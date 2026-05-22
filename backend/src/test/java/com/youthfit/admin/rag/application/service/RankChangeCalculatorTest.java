package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.result.RankChangeResult;
import com.youthfit.rag.application.dto.result.MergedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankChangeCalculator")
class RankChangeCalculatorTest {

    private final RankChangeCalculator calc = new RankChangeCalculator();

    private MergedChunk chunk(long id, int rank) {
        return new MergedChunk(id, rank - 1, 0.1, 0.0, rank, "preview");
    }

    @Test
    @DisplayName("동일 결과면 빈 리스트")
    void identical_returnsEmpty() {
        List<MergedChunk> same = List.of(chunk(1, 1), chunk(2, 2));
        assertThat(calc.compute(same, same)).isEmpty();
    }

    @Test
    @DisplayName("candidate 에만 있는 chunk 는 NEW (baselineRank null)")
    void candidateOnly_marksNew() {
        List<MergedChunk> baseline = List.of(chunk(1, 1));
        List<MergedChunk> candidate = List.of(chunk(1, 1), chunk(2, 2));
        List<RankChangeResult> changes = calc.compute(baseline, candidate);
        assertThat(changes)
                .extracting(RankChangeResult::chunkId, RankChangeResult::baselineRank,
                            RankChangeResult::candidateRank)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(2L, null, 2));
    }

    @Test
    @DisplayName("baseline 에만 있는 chunk 는 DROPPED (candidateRank null)")
    void baselineOnly_marksDropped() {
        List<MergedChunk> baseline = List.of(chunk(1, 1), chunk(2, 2));
        List<MergedChunk> candidate = List.of(chunk(1, 1));
        List<RankChangeResult> changes = calc.compute(baseline, candidate);
        assertThat(changes)
                .extracting(RankChangeResult::chunkId, RankChangeResult::candidateRank)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(2L, null));
    }

    @Test
    @DisplayName("양쪽에 있고 순위가 바뀌면 delta = candidate - baseline")
    void bothSidesDifferentRank_computesDelta() {
        List<MergedChunk> baseline = List.of(chunk(1, 1), chunk(2, 2));
        List<MergedChunk> candidate = List.of(chunk(2, 1), chunk(1, 2));
        List<RankChangeResult> changes = calc.compute(baseline, candidate);
        assertThat(changes)
                .extracting(RankChangeResult::chunkId, RankChangeResult::delta)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 1),
                        org.assertj.core.groups.Tuple.tuple(2L, -1));
    }

    @Test
    @DisplayName("양쪽 빈 결과 → 빈 리스트")
    void bothEmpty_returnsEmpty() {
        assertThat(calc.compute(List.of(), List.of())).isEmpty();
    }
}
