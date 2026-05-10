package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PgVectorSemanticQnaCache.findSimilar")
class PgVectorSemanticQnaCacheTest {

    @Mock
    private QnaQuestionCacheRepository repository;

    private QnaProperties properties;
    private PgVectorSemanticQnaCache cache;

    private static final Long POLICY_ID = 1L;
    private static final float[] EMBEDDING = {0.1f, 0.2f, 0.3f};
    private static final double THRESHOLD = 0.20;

    @BeforeEach
    void setUp() {
        // cacheTtlHours=24, relevanceDistanceThreshold=0.4, semanticDistanceThreshold=0.20
        properties = new QnaProperties(24L, 0.4, THRESHOLD, null);
        cache = new PgVectorSemanticQnaCache(repository, properties, new ObjectMapper());
    }

    @Nested
    @DisplayName("저장소에 매칭 결과 없음")
    class NoRow {

        @Test
        @DisplayName("findSimilar_매칭_없음_miss_회신")
        void findSimilar_매칭_없음_miss_회신() {
            when(repository.findClosestByPolicyId(eq(POLICY_ID), eq(EMBEDDING), any(Duration.class)))
                    .thenReturn(Optional.empty());

            SemanticLookupResult result = cache.findSimilar(POLICY_ID, "질문", EMBEDDING);

            assertThat(result.closest()).isEmpty();
            assertThat(result.cachedAnswer()).isEmpty();
        }
    }

    @Nested
    @DisplayName("가장 가까운 후보 존재")
    class RowExists {

        @Test
        @DisplayName("findSimilar_가까운_후보_있고_임계값_초과_belowThreshold — distance 0.30 > threshold 0.20")
        void findSimilar_가까운_후보_있고_임계값_초과_belowThreshold() {
            SimilarCachedAnswer candidate = new SimilarCachedAnswer(
                    42L, "캐시된 질문", "hash-abc", "캐시된 답변", "[]", null, 0.30
            );
            when(repository.findClosestByPolicyId(eq(POLICY_ID), eq(EMBEDDING), any(Duration.class)))
                    .thenReturn(Optional.of(candidate));

            SemanticLookupResult result = cache.findSimilar(POLICY_ID, "질문", EMBEDDING);

            assertThat(result.closest()).isPresent();
            assertThat(result.closest().get().cachedId()).isEqualTo(42L);
            assertThat(result.cachedAnswer()).isEmpty();
        }

        @Test
        @DisplayName("findSimilar_가까운_후보_있고_임계값_이하_hit — distance 0.15 <= threshold 0.20")
        void findSimilar_가까운_후보_있고_임계값_이하_hit() {
            SimilarCachedAnswer candidate = new SimilarCachedAnswer(
                    99L, "캐시된 질문", "hash-xyz", "캐시된 답변 내용", "[]", null, 0.15
            );
            when(repository.findClosestByPolicyId(eq(POLICY_ID), eq(EMBEDDING), any(Duration.class)))
                    .thenReturn(Optional.of(candidate));

            SemanticLookupResult result = cache.findSimilar(POLICY_ID, "질문", EMBEDDING);

            assertThat(result.closest()).isPresent();
            assertThat(result.closest().get().cachedId()).isEqualTo(99L);
            assertThat(result.cachedAnswer()).isPresent();
            assertThat(result.cachedAnswer().get().answer()).isEqualTo("캐시된 답변 내용");
        }
    }
}
