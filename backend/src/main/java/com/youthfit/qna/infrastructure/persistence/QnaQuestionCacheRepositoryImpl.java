package com.youthfit.qna.infrastructure.persistence;

import com.youthfit.qna.domain.model.QnaQuestionCache;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@Repository
@RequiredArgsConstructor
public class QnaQuestionCacheRepositoryImpl implements QnaQuestionCacheRepository {

    private final QnaQuestionCacheJpaRepository jpaRepository;

    @Override
    public Optional<SimilarCachedAnswer> findClosestByPolicyId(Long policyId,
                                                               float[] queryEmbedding,
                                                               Duration ttl) {
        String vectorString = toVectorString(queryEmbedding);
        int ttlHours = (int) Math.max(1, ttl.toHours());
        List<Object[]> rows = jpaRepository.findClosestByPolicyId(policyId, vectorString, ttlHours);
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.get(0);
        return Optional.of(new SimilarCachedAnswer(
                ((Number) row[0]).longValue(),     // id
                (String) row[1],                    // questionText
                (String) row[2],                    // sourceHash
                (String) row[3],                    // answer
                (String) row[4],                    // sourcesJson
                ((Number) row[5]).doubleValue()    // distance
        ));
    }

    @Override
    public QnaQuestionCache save(QnaQuestionCache entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void deleteByPolicyId(Long policyId) {
        jpaRepository.deleteByPolicyId(policyId);
    }

    private String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : embedding) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }
}
