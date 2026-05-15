package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.SimilarChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ReciprocalRankFusion")
class ReciprocalRankFusionTest {

    private final ReciprocalRankFusion rrf = new ReciprocalRankFusion();

    private SimilarChunk chunk(Long id, double distance) {
        return new SimilarChunk(
                id, 1L, id.intValue(), "content-" + id,
                null, null, null, distance
        );
    }

    @Test
    @DisplayName("양쪽 모두에 같은 청크가 있으면 두 점수가 합산되어 더 높은 순위가 된다")
    void mergesSameChunkScores() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.2), chunk(2L, 0.3));
        List<SimilarChunk> tri = List.of(chunk(2L, 0.0), chunk(1L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SimilarChunk::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("한쪽에만 있는 청크는 한쪽 점수만 받는다")
    void singleSideChunk() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.2));
        List<SimilarChunk> tri = List.of(chunk(2L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        assertThat(result).extracting(SimilarChunk::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("양쪽 매칭 청크가 단일 매칭보다 상위에 정렬된다")
    void bothSidesRanksHigher() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.2), chunk(2L, 0.3));
        List<SimilarChunk> tri = List.of(chunk(1L, 0.0), chunk(3L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("topK 컷으로 결과 수가 제한된다")
    void topKLimits() {
        List<SimilarChunk> vec = List.of(
                chunk(1L, 0.1), chunk(2L, 0.2), chunk(3L, 0.3), chunk(4L, 0.4), chunk(5L, 0.5)
        );
        List<SimilarChunk> tri = List.of();

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("양쪽 모두 비어 있으면 빈 결과를 반환한다")
    void bothEmpty() {
        List<SimilarChunk> result = rrf.merge(List.of(), List.of(), 60, 10);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("양쪽 매칭 시 vector 의 distance 가 결과 distance 로 유지된다")
    void preservesVectorDistance() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.25));
        List<SimilarChunk> tri = List.of(chunk(1L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distance()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("trigram 단독 청크는 distance 가 1.0-similarity 로 채워진다")
    void trigramOnlyChunkUsesConvertedDistance() {
        SimilarChunk trigramOnly = new SimilarChunk(
                1L, 1L, 0, "c", null, null, null, 0.8
        );
        List<SimilarChunk> result = rrf.merge(List.of(), List.of(trigramOnly), 60, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distance()).isCloseTo(0.2, within(1e-9));
    }
}
