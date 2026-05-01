package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.domain.model.QnaQuestionCache;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PgVectorSemanticQnaCache implements SemanticQnaCache {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSemanticQnaCache.class);
    private static final TypeReference<List<QnaSourceResult>> SOURCES_TYPE = new TypeReference<>() {};

    private final QnaQuestionCacheRepository repository;
    private final QnaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<CachedAnswer> findSimilar(Long policyId, float[] queryEmbedding) {
        Duration ttl = Duration.ofHours(properties.cacheTtlHours());
        Optional<SimilarCachedAnswer> closest = repository.findClosestByPolicyId(policyId, queryEmbedding, ttl);
        if (closest.isEmpty()) {
            return Optional.empty();
        }
        SimilarCachedAnswer hit = closest.get();
        if (hit.distance() > properties.semanticDistanceThreshold()) {
            if (log.isDebugEnabled()) {
                log.debug("Q&A 의미 캐시 미스: policyId={}, closestDistance={}", policyId, hit.distance());
            }
            return Optional.empty();
        }
        log.info("Q&A 의미 캐시 히트: policyId={}, distance={}", policyId, hit.distance());
        try {
            List<QnaSourceResult> sources = objectMapper.readValue(hit.sourcesJson(), SOURCES_TYPE);
            return Optional.of(new CachedAnswer(hit.answer(), sources, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 sources 역직렬화 실패: policyId={}, error={}", policyId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long policyId, String question, float[] embedding, CachedAnswer answer) {
        try {
            String sourcesJson = objectMapper.writeValueAsString(answer.sources());
            QnaQuestionCache entity = QnaQuestionCache.builder()
                    .policyId(policyId)
                    .questionText(question)
                    .embedding(embedding)
                    .answer(answer.answer())
                    .sourcesJson(sourcesJson)
                    .build();
            repository.save(entity);
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 write 실패 (사용자 응답엔 영향 없음): policyId={}, error={}",
                    policyId, e.toString());
        }
    }
}
