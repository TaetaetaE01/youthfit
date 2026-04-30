package com.youthfit.qna.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.domain.model.QnaFailedReason;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.service.RagSearchService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class QnaService {

    private static final Logger log = LoggerFactory.getLogger(QnaService.class);
    private static final long SSE_TIMEOUT = 120_000L;
    private static final String NO_INDEXED_MESSAGE =
            "이 정책은 아직 본문 인덱싱이 되어 있지 않아 답변을 만들 수 없습니다. 정책 상세 페이지에서 원문을 확인해 주세요.";
    private static final String NO_RELEVANT_MESSAGE =
            "해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다.";
    private static final String COST_GUARD_BLOCKED_MESSAGE =
            "현재 환경에서 이 정책은 Q&A를 지원하지 않습니다.";
    private static final String LLM_ERROR_MESSAGE =
            "답변 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    private final CostGuard costGuard;
    private final PolicyRepository policyRepository;
    private final RagSearchService ragSearchService;
    private final QnaLlmProvider qnaLlmProvider;
    private final QnaAnswerCache qnaAnswerCache;
    private final QnaHistoryWriter historyWriter;
    private final QnaProperties qnaProperties;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = new DelegatingSecurityContextExecutorService(
            Executors.newVirtualThreadPerTaskExecutor());

    public SseEmitter askQuestion(AskQuestionCommand command) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        if (!costGuard.allows(command.policyId())) {
            costGuard.logSkip("qna.askQuestion", command.policyId());
            sendErrorEvent(emitter, COST_GUARD_BLOCKED_MESSAGE);
            emitter.complete();
            return emitter;
        }

        Policy policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        Long historyId = historyWriter.startInProgress(command.userId(), command.policyId(), command.question());

        executor.execute(() -> {
            try {
                processQuestion(emitter, command, policy, historyId);
            } catch (Exception e) {
                log.error("Q&A 스트리밍 처리 중 예상치 못한 오류: policyId={}", command.policyId(), e);
                sendErrorEvent(emitter, LLM_ERROR_MESSAGE);
                historyWriter.markFailed(historyId, QnaFailedReason.INTERNAL_ERROR);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void processQuestion(SseEmitter emitter, AskQuestionCommand command, Policy policy, Long historyId)
            throws IOException {
        Optional<CachedAnswer> cached;
        try {
            cached = qnaAnswerCache.get(command.policyId(), command.question());
        } catch (Exception e) {
            log.warn("Q&A 캐시 get 실패 (정상 흐름 진행): policyId={}", command.policyId(), e);
            cached = Optional.empty();
        }
        if (cached.isPresent()) {
            sendCachedAnswer(emitter, cached.get(), historyId);
            return;
        }

        List<PolicyDocumentChunkResult> chunks = ragSearchService.searchRelevantChunks(
                new SearchChunksCommand(command.policyId(), command.question()));

        if (chunks.isEmpty()) {
            rejectAndComplete(emitter, historyId, NO_INDEXED_MESSAGE, QnaFailedReason.NO_INDEXED_DOCUMENT);
            return;
        }

        double threshold = qnaProperties.relevanceDistanceThreshold();
        List<PolicyDocumentChunkResult> passing = chunks.stream()
                .filter(c -> c.distance() <= threshold)
                .toList();

        if (passing.isEmpty()) {
            rejectAndComplete(emitter, historyId, NO_RELEVANT_MESSAGE, QnaFailedReason.NO_RELEVANT_CHUNK);
            return;
        }

        String context = buildContext(passing);
        List<QnaSourceResult> sources = buildSources(command.policyId(), passing);

        String fullAnswer;
        try {
            fullAnswer = qnaLlmProvider.generateAnswer(
                    policy.getTitle(), context, command.question(),
                    chunk -> sendChunkEvent(emitter, chunk)
            );
        } catch (Exception e) {
            log.error("LLM 호출 실패: policyId={}", command.policyId(), e);
            sendErrorEvent(emitter, LLM_ERROR_MESSAGE);
            historyWriter.markFailed(historyId, QnaFailedReason.LLM_ERROR);
            emitter.completeWithError(e);
            return;
        }

        sendSourcesEvent(emitter, sources);
        sendDoneEvent(emitter);
        emitter.complete();

        try {
            qnaAnswerCache.put(command.policyId(), command.question(),
                    new CachedAnswer(fullAnswer, sources, Instant.now()));
        } catch (Exception e) {
            log.warn("Q&A 캐시 put 실패: policyId={}", command.policyId(), e);
        }

        try {
            String sourcesJson = objectMapper.writeValueAsString(sources);
            historyWriter.markCompleted(historyId, fullAnswer, sourcesJson);
        } catch (Exception e) {
            log.error("Q&A 히스토리 markCompleted 실패: historyId={}", historyId, e);
        }
    }

    private void sendCachedAnswer(SseEmitter emitter, CachedAnswer cached, Long historyId) {
        sendChunkEvent(emitter, cached.answer());
        sendSourcesEvent(emitter, cached.sources());
        sendDoneEvent(emitter);
        emitter.complete();
        try {
            String sourcesJson = objectMapper.writeValueAsString(cached.sources());
            historyWriter.markCompleted(historyId, cached.answer(), sourcesJson);
        } catch (Exception e) {
            log.error("Q&A 캐시 히트 history markCompleted 실패: historyId={}", historyId, e);
        }
    }

    private void rejectAndComplete(SseEmitter emitter, Long historyId, String message, QnaFailedReason reason) {
        sendChunkEvent(emitter, message);
        sendSourcesEvent(emitter, List.of());
        sendDoneEvent(emitter);
        emitter.complete();
        historyWriter.markFailed(historyId, reason);
    }

    private String buildContext(List<PolicyDocumentChunkResult> chunks) {
        StringBuilder sb = new StringBuilder();
        for (PolicyDocumentChunkResult chunk : chunks) {
            sb.append("[청크 ").append(chunk.chunkIndex()).append("]\n");
            sb.append(chunk.content()).append("\n\n");
        }
        return sb.toString();
    }

    private List<QnaSourceResult> buildSources(Long policyId, List<PolicyDocumentChunkResult> chunks) {
        return chunks.stream()
                .map(chunk -> new QnaSourceResult(
                        policyId,
                        chunk.attachmentId(),
                        chunk.attachmentId() == null ? null : "첨부 #" + chunk.attachmentId(),
                        chunk.pageStart(),
                        chunk.pageEnd(),
                        truncateExcerpt(chunk.content())
                ))
                .toList();
    }

    private String truncateExcerpt(String content) {
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    private void sendChunkEvent(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "CHUNK", "content", content)));
        } catch (IOException e) {
            log.warn("SSE CHUNK 이벤트 전송 실패", e);
        }
    }

    private void sendSourcesEvent(SseEmitter emitter, List<QnaSourceResult> sources) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "SOURCES", "sources", sources)));
        } catch (IOException e) {
            log.warn("SSE SOURCES 이벤트 전송 실패", e);
        }
    }

    private void sendDoneEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "DONE")));
        } catch (IOException e) {
            log.warn("SSE DONE 이벤트 전송 실패", e);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "ERROR", "content", message)));
        } catch (IOException e) {
            log.warn("SSE ERROR 이벤트 전송 실패", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
