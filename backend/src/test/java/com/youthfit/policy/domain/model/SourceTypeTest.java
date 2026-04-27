package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceType")
class SourceTypeTest {

    @ParameterizedTest(name = "{0} → 라벨 {1}")
    @CsvSource({
            "BOKJIRO_CENTRAL,복지로",
            "YOUTH_CENTER,온통청년",
            "YOUTH_SEOUL_CRAWL,청년 서울"
    })
    @DisplayName("getLabel 은 한글 라벨을 반환한다")
    void getLabel_returnsKoreanLabel(SourceType type, String expectedLabel) {
        assertThat(type.getLabel()).isEqualTo(expectedLabel);
    }
}
