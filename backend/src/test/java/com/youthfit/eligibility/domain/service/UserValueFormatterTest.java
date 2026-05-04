package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.view.UserValueView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserValueFormatter")
class UserValueFormatterTest {

    private final UserValueFormatter formatter = new UserValueFormatter();

    @Test
    @DisplayName("age 필드 → \"만 N세\"")
    void age() {
        UserValueView view = formatter.format("age", 29);

        assertThat(view.raw()).isEqualTo("29");
        assertThat(view.displayText()).isEqualTo("만 29세");
    }

    @Test
    @DisplayName("employmentKind 필드 (이미 enum.name() 문자열) → 한국어 라벨")
    void employment() {
        UserValueView view = formatter.format("employmentKind", "UNEMPLOYED");

        assertThat(view.raw()).isEqualTo("UNEMPLOYED");
        assertThat(view.displayText()).isEqualTo("미취업자");
    }

    @Test
    @DisplayName("annualIncome 필드 → \"N만원\"")
    void income() {
        UserValueView view = formatter.format("annualIncome", 30000000L);

        assertThat(view.raw()).isEqualTo("30000000");
        assertThat(view.displayText()).isEqualTo("3,000만원");
    }

    @Test
    @DisplayName("region 필드 (코드 그대로) → 코드 그대로")
    void region() {
        UserValueView view = formatter.format("region", "1100000000");

        assertThat(view.raw()).isEqualTo("1100000000");
        assertThat(view.displayText()).isEqualTo("1100000000");
    }

    @Test
    @DisplayName("null 값 → null 반환")
    void nullValue() {
        UserValueView view = formatter.format("age", null);

        assertThat(view).isNull();
    }
}
