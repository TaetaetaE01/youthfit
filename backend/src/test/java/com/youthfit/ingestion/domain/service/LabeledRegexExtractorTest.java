package com.youthfit.ingestion.domain.service;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LabeledRegexExtractor")
class LabeledRegexExtractorTest {

    private final LabeledRegexExtractor extractor = new LabeledRegexExtractor();

    @Test
    @DisplayName("양성 라벨 윈도우 안의 완전 범위를 BODY_LABELED 후보로 만든다 (confidence 0.85)")
    void labeledFullRange() {
        List<PeriodCandidate> cs = extractor.candidatesInLabeledWindows(
                "신청기간: 2026.03.01 ~ 2026.04.30", PeriodSource.BODY_LABELED);

        assertThat(cs).hasSize(1);
        assertThat(cs.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(cs.get(0).confidence()).isEqualTo(0.85);
        assertThat(cs.get(0).source()).isEqualTo(PeriodSource.BODY_LABELED);
    }

    @Test
    @DisplayName("네거티브 라벨 윈도우의 매치는 라벨 후보에서 제외된다")
    void negativeLabelWindowExcluded() {
        List<PeriodCandidate> cs = extractor.candidatesInLabeledWindows(
                "[사업기간] 2025.1.1 ~ 2025.12.31", PeriodSource.BODY_LABELED);

        assertThat(cs).isEmpty();
    }

    @Test
    @DisplayName("네거티브 윈도우를 마스킹한 본문으로 GENERIC 스캔을 한다")
    void genericScanWithNegativeMasking() {
        List<PeriodCandidate> cs = extractor.candidatesInBodyMasked(
                "사업기간 2025.1.1~12.31\n공지 2026.3.1~4.30",
                PeriodSource.BODY_GENERIC);

        assertThat(cs).hasSize(1);
        assertThat(cs.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(cs.get(0).confidence()).isEqualTo(0.45);
    }

    @Test
    @DisplayName("단일 마감일(DEADLINE_ONLY)은 confidence 0.65 (라벨) / 0.35 (제네릭)")
    void deadlineOnlyConfidence() {
        var labeled = extractor.candidatesInLabeledWindows(
                "신청 마감 2026.6.30 까지", PeriodSource.BODY_LABELED);
        assertThat(labeled).singleElement().satisfies(c -> {
            assertThat(c.start()).isNull();
            assertThat(c.end()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(c.confidence()).isEqualTo(0.65);
        });

        var generic = extractor.candidatesInBodyMasked(
                "공지 2026.6.30 까지", PeriodSource.BODY_GENERIC);
        assertThat(generic).singleElement().satisfies(c -> {
            assertThat(c.confidence()).isEqualTo(0.35);
        });
    }
}
