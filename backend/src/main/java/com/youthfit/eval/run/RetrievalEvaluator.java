package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.SnippetMatcher;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 평가 케이스 1건을 시나리오 설정으로 실행해 판정한다.
 * 스니펫 매칭은 MergedChunk.preview(500자 truncate)가 아니라
 * vectorTopN/trigramTopN 의 전체 content 로 수행한다.
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
public class RetrievalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluator.class);

    private final RagSearchService ragSearchService;
    private final EmbeddingProvider embeddingProvider;
    private final QueryRewriter queryRewriter;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;

    public CaseResult evaluate(EvalCase c, EvalScenario scenario, QueryEmbeddingFileCache cache) {
        Optional<Policy> policy = policyRepository.findById(c.policyId());
        if (policy.isEmpty()
                || !SnippetMatcher.normalize(policy.get().getTitle())
                        .equals(SnippetMatcher.normalize(c.policyTitle()))) {
            log.warn("STALE 케이스: id={}, 기대 title=\"{}\", 실제={}",
                    c.id(), c.policyTitle(), policy.map(Policy::getTitle).orElse("(정책 없음)"));
            return new CaseResult(c, CaseStatus.STALE, List.of(), null, 0L, c.question(), null);
        }

        if (policyDocumentRepository.findByPolicyIdOrderByChunkIndex(c.policyId()).isEmpty()) {
            return new CaseResult(c, CaseStatus.NO_CHUNKS, List.of(), null, 0L, c.question(), null);
        }

        try {
            String question = scenario.queryRewrite()
                    ? queryRewriter.rewrite(policy.get().getTitle(), c.question()).orElse(c.question())
                    : c.question();

            float[] embedding = cache.getOrCompute(question, embeddingProvider::embed);
            RagSearchTrace trace = ragSearchService.searchRelevantChunksWithTrace(
                    new SearchChunksCommand(c.policyId(), question), embedding, scenario.overrides());

            Map<Long, String> contentById = new HashMap<>();
            for (SimilarChunk chunk : trace.vectorTopN()) {
                contentById.put(chunk.id(), chunk.content());
            }
            for (SimilarChunk chunk : trace.trigramTopN()) {
                contentById.putIfAbsent(chunk.id(), chunk.content());
            }

            List<RankedChunk> ranked = new ArrayList<>();
            Integer firstRelevantRank = null;
            for (MergedChunk merged : trace.merged()) {
                String content = contentById.getOrDefault(merged.chunkId(), merged.preview());
                boolean relevant = c.expectedSnippets().stream()
                        .anyMatch(snippet -> SnippetMatcher.containsSnippet(content, snippet));
                ranked.add(new RankedChunk(merged.chunkId(), merged.rank(), merged.distance(), relevant));
                if (relevant && firstRelevantRank == null) {
                    firstRelevantRank = merged.rank();
                }
            }

            return new CaseResult(c, CaseStatus.OK, ranked, firstRelevantRank,
                    trace.tookMs(), question, trace.effective());
        } catch (Exception e) {
            log.warn("케이스 실행 실패 SKIPPED: id={}, error={}", c.id(), e.toString());
            return new CaseResult(c, CaseStatus.SKIPPED, List.of(), null, 0L, c.question(), null);
        }
    }
}
