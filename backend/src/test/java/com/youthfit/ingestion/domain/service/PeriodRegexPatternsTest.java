package com.youthfit.ingestion.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PeriodRegexPatterns")
class PeriodRegexPatternsTest {

    @Test
    @DisplayName("FULL_RANGE: 2026.03.01 ~ 2026.04.30")
    void fullRange() {
        var hits = PeriodRegexPatterns.findAll("신청기간 2026.03.01 ~ 2026.04.30");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("YEAR_INHERIT: 2026.3.1 ~ 4.30 (종료 연도 상속)")
    void yearInherit() {
        var hits = PeriodRegexPatterns.findAll("2026.3.1 ~ 4.30");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("SAME_MONTH: 2026.3.1 ~ 31 (종료 연/월 상속)")
    void sameMonth() {
        var hits = PeriodRegexPatterns.findAll("2026.3.1 ~ 31");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("DEADLINE_ONLY: 2026.6.30 까지 (start=null)")
    void deadlineOnly() {
        var hits = PeriodRegexPatterns.findAll("신청 마감 2026.6.30 까지");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isNull();
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("요일/시간이 끼어든 FULL_RANGE")
    void fullRangeWithDayOfWeek() {
        var hits = PeriodRegexPatterns.findAll("2026.03.01(월) 09:00 ~ 2026.04.30(금) 18:00");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("연도 없는 자연어는 매치하지 않는다")
    void noMatchOnNaturalLanguage() {
        assertThat(PeriodRegexPatterns.findAll("매년 3월~4월")).isEmpty();
    }

    @Test
    @DisplayName("종료가 시작보다 빠르면 제외")
    void rejectReversed() {
        assertThat(PeriodRegexPatterns.findAll("2026.04.30 ~ 2026.03.01")).isEmpty();
    }
}
