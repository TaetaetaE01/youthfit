package com.youthfit.ingestion.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PeriodLabels")
class PeriodLabelsTest {

    @Test
    @DisplayName("양성 라벨로 신청기간/접수기간/모집기간/공모기간/사업신청기간을 인식한다")
    void recognizesPositiveLabels() {
        assertThat(PeriodLabels.matchAll("신청기간: 2026.3.1~4.30"))
                .extracting(PeriodLabels.LabelMatch::label, PeriodLabels.LabelMatch::negative)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("신청기간", false));
    }

    @Test
    @DisplayName("네거티브 라벨로 사업기간/운영기간을 인식한다")
    void recognizesNegativeLabels() {
        List<PeriodLabels.LabelMatch> ms = PeriodLabels.matchAll("[사업기간] 2025-01-01 ~ 2025-12-31");
        assertThat(ms).hasSize(1);
        assertThat(ms.get(0).negative()).isTrue();
    }

    @Test
    @DisplayName("양성과 네거티브가 같이 있으면 둘 다 인식한다 (위치 순)")
    void recognizesBothInOrder() {
        String body = "사업기간 2025.1.1~12.31\n신청기간 2026.3.1~4.30";
        List<PeriodLabels.LabelMatch> ms = PeriodLabels.matchAll(body);
        assertThat(ms).hasSize(2);
        assertThat(ms.get(0).negative()).isTrue();   // 사업기간
        assertThat(ms.get(1).negative()).isFalse();  // 신청기간
    }
}
