package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.application.port.dto.SemanticLookupMatch;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public SemanticLookupResult findSimilar(Long policyId, String userQuestion, float[] queryEmbedding) {
        Duration ttl = Duration.ofHours(properties.cacheTtlHours());
        Optional<SimilarCachedAnswer> closestOpt = repository.findClosestByPolicyId(policyId, queryEmbedding, ttl);

        if (closestOpt.isEmpty()) {
            log.info("Q&A 의미 캐시 미스: missReason=NO_ROW, policyId={}, userQuestion=\"{}\"",
                    policyId, userQuestion);
            return SemanticLookupResult.miss();
        }

        SimilarCachedAnswer c = closestOpt.get();
        BigDecimal distance = BigDecimal.valueOf(c.distance()).setScale(5, RoundingMode.HALF_UP);
        BigDecimal similarity = BigDecimal.ONE
                .subtract(distance.divide(BigDecimal.valueOf(2), 5, RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO);
        SemanticLookupMatch match = new SemanticLookupMatch(c.id(), similarity, distance);

        if (c.distance() > properties.semanticDistanceThreshold()) {
            log.info("Q&A 의미 캐시 미스: missReason=DISTANCE_OVER_THRESHOLD, policyId={}, sourceHash={}, " +
                            "distance={}, similarity={}, userQuestion=\"{}\", cachedQuestion=\"{}\"",
                    policyId, c.sourceHash(), c.distance(), similarity, userQuestion, c.questionText());
            return SemanticLookupResult.belowThreshold(match);
        }

        log.info("Q&A 의미 캐시 히트: policyId={}, sourceHash={}, distance={}, similarity={}, " +
                        "userQuestion=\"{}\", cachedQuestion=\"{}\"",
                policyId, c.sourceHash(), c.distance(), similarity, userQuestion, c.questionText());

        return SemanticLookupResult.hit(match, toCachedAnswer(policyId, c));
    }

    private CachedAnswer toCachedAnswer(Long policyId, SimilarCachedAnswer c) {
        try {
            List<QnaSourceResult> sources = objectMapper.readValue(c.sourcesJson(), SOURCES_TYPE);
            return new CachedAnswer(c.answer(), sources, Instant.now());
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 sources 역직렬화 실패: policyId={}, error={}", policyId, e.toString());
            return new CachedAnswer(c.answer(), List.of(), Instant.now());
        }
    }

    @Override
    public void put(Long policyId, String question, String sourceHash, float[] embedding, CachedAnswer answer) {
        try {
            String sourcesJson = objectMapper.writeValueAsString(answer.sources());
            QnaQuestionCache entity = QnaQuestionCache.builder()
                    .policyId(policyId)
                    .sourceHash(sourceHash)
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
