package com.youthfit.ingestion.domain.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.application.port.*;
import com.youthfit.ingestion.domain.model.*;
import com.youthfit.ingestion.domain.service.source.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PeriodResolver")
class PeriodResolverTest {

    private final LabeledRegexExtractor extractor = new LabeledRegexExtractor();
    private final PeriodLlmDirectExtractor llmDirect = mock(PeriodLlmDirectExtractor.class);
    private final PeriodLlmDisambiguator llmDisamb = mock(PeriodLlmDisambiguator.class);
    private final CostGuard costGuard = mock(CostGuard.class);

    private PeriodResolver newResolver() {
        return new PeriodResolver(
                List.of(
                        new N8nApplyFieldsSource(),
                        new BodyLabeledRegexSource(extractor),
                        new BodyGenericRegexSource(extractor),
                        new AttachmentLabeledRegexSource(extractor)),
                llmDirect, llmDisamb, costGuard);
    }

    @Test
    @DisplayName("BODY_LABELED 0.85 가 N8N 0.60 을 이긴다")
    void labeledBeatsN8n() {
        when(costGuard.enabled()).thenReturn(false);
        var ctx = new PeriodExtractionContext("t",
                "신청기간 2026.3.1 ~ 2026.4.30",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31),
                "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);

        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(r.source()).isEqualTo(PeriodSource.BODY_LABELED);
        verifyNoInteractions(llmDirect, llmDisamb);
    }

    @Test
    @DisplayName("후보 0개 + !CostGuard → LLM direct 호출")
    void noCandidatesCallsLlmDirect() {
        when(costGuard.enabled()).thenReturn(false);
        when(llmDirect.extract(any(), any())).thenReturn(Optional.of(new PeriodCandidate(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                PeriodSource.LLM_DIRECT, 0.60, "llm")));

        var ctx = new PeriodExtractionContext("t",
                "이번 분기 한 달간 모집", null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.source()).isEqualTo(PeriodSource.LLM_DIRECT);
        verify(llmDirect).extract(any(), any());
        verifyNoInteractions(llmDisamb);
    }

    @Test
    @DisplayName("후보 ≥ 2 + 최고점 < 0.70 → disambiguator 호출")
    void ambiguousCallsDisambiguator() {
        when(costGuard.enabled()).thenReturn(false);
        when(llmDisamb.choose(any(), any())).thenAnswer(inv -> {
            List<PeriodCandidate> cs = inv.getArgument(1);
            return Optional.of(new PeriodCandidate(
                    cs.get(0).start(), cs.get(0).end(),
                    PeriodSource.LLM_DISAMBIGUATED, 0.85, "chosen"));
        });

        // GENERIC 0.45 두 개 (마스킹 없음 — 라벨 없는 두 범위)
        var ctx = new PeriodExtractionContext("t",
                "2026.3.1 ~ 4.30 안내 / 추가 2026.5.1 ~ 5.31 가능",
                null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.source()).isEqualTo(PeriodSource.LLM_DISAMBIGUATED);
        verify(llmDisamb).choose(any(), any());
        verifyNoInteractions(llmDirect);
    }

    @Test
    @DisplayName("CostGuard 활성 → LLM 두 경로 모두 차단")
    void costGuardBlocksLlm() {
        when(costGuard.enabled()).thenReturn(true);
        var ctx = new PeriodExtractionContext("t",
                "이번 분기 한 달간 모집", null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.isEmpty()).isTrue();
        verifyNoInteractions(llmDirect, llmDisamb);
    }

    @Test
    @DisplayName("최종 confidence < 0.55 → empty 반환")
    void belowFloorReturnsEmpty() {
        when(costGuard.enabled()).thenReturn(false);
        var ctx = new PeriodExtractionContext("t",
                "공지 2026.3.1 ~ 2026.4.30", null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("동일 (start,end) 다중 소스 일치 시 보너스로 점수 상승")
    void multiSourceBonus() {
        when(costGuard.enabled()).thenReturn(false);
        // 본문 "신청기간 2026.3.1 ~ 2026.4.30" 은 다음 3 소스에서 동일 (start,end) 후보를 만든다:
        //   BODY_LABELED 0.85 (신청기간 라벨 윈도우 내 FULL_RANGE)
        //   BODY_GENERIC 0.45 (라벨-마스킹은 NEGATIVE 만 처리 — POSITIVE 윈도우는 그대로 남음)
        //   N8N         0.60 (applyStart/applyEnd 일치)
        // group.size = 3 → 보너스 = 0.05 * 2 = 0.10 → 0.85 + 0.10 = 0.95.
        var ctx = new PeriodExtractionContext("t",
                "신청기간 2026.3.1 ~ 2026.4.30",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30),
                "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.confidence()).isEqualTo(0.95);
    }
}
