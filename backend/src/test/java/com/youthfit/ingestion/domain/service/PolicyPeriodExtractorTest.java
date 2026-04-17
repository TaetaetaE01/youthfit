package com.youthfit.ingestion.domain.service;

import com.youthfit.ingestion.domain.model.PolicyPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyPeriodExtractor")
class PolicyPeriodExtractorTest {

    private final PolicyPeriodExtractor extractor = new PolicyPeriodExtractor();

    @Test
    @DisplayName("점 구분 연월일 범위를 추출한다")
    void extractsDotSeparatedRange() {
        PolicyPeriod period = extractor.extract("신청기간: 2026.03.01.~2026.04.30.");

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("하이픈 구분 연월일 범위를 추출한다")
    void extractsHyphenSeparatedRange() {
        PolicyPeriod period = extractor.extract("접수 2026-03-01 ~ 2026-04-30 까지");

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("한국어 연월일 범위를 추출한다")
    void extractsKoreanYearMonthDayRange() {
        PolicyPeriod period = extractor.extract("2026년 3월 1일 ~ 2026년 4월 30일");

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("유효하지 않은 날짜는 건너뛰고 다음 매치를 사용한다")
    void skipsInvalidDateAndUsesNextMatch() {
        PolicyPeriod period = extractor.extract(
                "잘못된 값 2026.02.30.~2026.03.01. 실제 기간 2026.05.01.~2026.05.31."
        );

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("연도가 없는 기간은 빈 값을 반환한다")
    void returnsEmptyForYearlessRange() {
        PolicyPeriod period = extractor.extract("매년 3월~4월 접수");

        assertThat(period.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("상시접수 문구는 빈 값을 반환한다")
    void returnsEmptyForAlwaysOpen() {
        PolicyPeriod period = extractor.extract("상시접수");

        assertThat(period.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("종료일이 시작일보다 빠르면 건너뛴다")
    void skipsReversedRange() {
        PolicyPeriod period = extractor.extract("2026.04.30.~2026.03.01.");

        assertThat(period.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("빈 입력은 빈 값을 반환한다")
    void returnsEmptyForBlankInput() {
        assertThat(extractor.extract(null).isEmpty()).isTrue();
        assertThat(extractor.extract("").isEmpty()).isTrue();
        assertThat(extractor.extract("   ").isEmpty()).isTrue();
    }
}
