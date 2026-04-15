package com.youthfit.qna.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.domain.model.QnaHistory;
import com.youthfit.qna.domain.repository.QnaHistoryRepository;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class QnaService {

    private static final Logger log = LoggerFactory.getLogger(QnaService.class);
    private static final long SSE_TIMEOUT = 120_000L;

    private final PolicyRepository policyRepository;
    private final RagSearchService ragSearchService;
    private final QnaLlmProvider qnaLlmProvider;
    private final QnaHistoryRepository qnaHistoryRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SseEmitter askQuestion(AskQuestionCommand command) {
        Policy policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        executor.execute(() -> {
            try {
                processQuestion(emitter, command, policy);
            } catch (Exception e) {
                log.error("Q&A 스트리밍 처리 중 오류: policyId={}", command.policyId(), e);
                sendErrorEvent(emitter, "답변 생성 중 오류가 발생했습니다");
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void processQuestion(SseEmitter emitter, AskQuestionCommand command, Policy policy) throws IOException {
        List<PolicyDocumentChunkResult> chunks = ragSearchService.searchRelevantChunks(
                new SearchChunksCommand(command.policyId(), command.question()));

        if (chunks.isEmpty()) {
            sendChunkEvent(emitter, "해당 정책의 인덱싱된 문서가 없어 답변을 생성할 수 없습니다. 정책 상세 페이지를 확인해 주세요.");
            sendDoneEvent(emitter, List.of());
            emitter.complete();
            return;
        }

        String context = buildContext(chunks);
        List<QnaSourceResult> sources = buildSources(command.policyId(), chunks);

        String fullAnswer = qnaLlmProvider.generateAnswer(
                policy.getTitle(), context, command.question(),
                chunk -> sendChunkEvent(emitter, chunk)
        );

        sendSourcesEvent(emitter, sources);
        sendDoneEvent(emitter, sources);
        emitter.complete();

        saveHistory(command, fullAnswer, sources);
    }

    @Transactional
    protected void saveHistory(AskQuestionCommand command, String fullAnswer, List<QnaSourceResult> sources) {
        try {
            QnaHistory history = QnaHistory.builder()
                    .userId(command.userId())
                    .policyId(command.policyId())
                    .question(command.question())
                    .build();

            String sourcesJson = objectMapper.writeValueAsString(sources);
            history.completeAnswer(fullAnswer, sourcesJson);
            qnaHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Q&A 히스토리 저장 실패: userId={}, policyId={}", command.userId(), command.policyId(), e);
        }
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
                        "청크 " + chunk.chunkIndex(),
                        truncateExcerpt(chunk.content())
                ))
                .toList();
    }

    private String truncateExcerpt(String content) {
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    private void sendChunkEvent(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event()
                    .data(Map.of("type", "CHUNK", "content", content)));
        } catch (IOException e) {
            log.warn("SSE CHUNK 이벤트 전송 실패", e);
        }
    }

    private void sendSourcesEvent(SseEmitter emitter, List<QnaSourceResult> sources) {
        try {
            emitter.send(SseEmitter.event()
                    .data(Map.of("type", "SOURCES", "sources", sources)));
        } catch (IOException e) {
            log.warn("SSE SOURCES 이벤트 전송 실패", e);
        }
    }

    private void sendDoneEvent(SseEmitter emitter, List<QnaSourceResult> sources) {
        try {
            emitter.send(SseEmitter.event()
                    .data(Map.of("type", "DONE")));
        } catch (IOException e) {
            log.warn("SSE DONE 이벤트 전송 실패", e);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .data(Map.of("type", "ERROR", "content", message)));
        } catch (IOException e) {
            log.warn("SSE ERROR 이벤트 전송 실패", e);
        }
    }
}
