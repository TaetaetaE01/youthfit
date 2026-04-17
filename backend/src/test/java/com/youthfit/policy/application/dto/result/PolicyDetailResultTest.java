package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

@DisplayName("PolicyDetailResult DTO 변환")
class PolicyDetailResultTest {

    @Test
    @DisplayName("Policy Entity로부터 모든 필드를 올바르게 변환한다")
    void from_mapsAllFields() {
        // given
        Policy policy = Policy.builder()
                .title("청년 주거 지원")
                .summary("월세 지원 프로그램")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 1, 1))
                .applyEnd(LocalDate.of(2026, 12, 31))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);

        // when
        PolicyDetailResult result = PolicyDetailResult.from(policy, "https://example.com/policy/1");

        // then
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.id()).isEqualTo(1L);
            softly.assertThat(result.title()).isEqualTo("청년 주거 지원");
            softly.assertThat(result.summary()).isEqualTo("월세 지원 프로그램");
            softly.assertThat(result.category()).isEqualTo(Category.HOUSING);
            softly.assertThat(result.regionCode()).isEqualTo("11");
            softly.assertThat(result.applyStart()).isEqualTo(LocalDate.of(2026, 1, 1));
            softly.assertThat(result.applyEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
            softly.assertThat(result.status()).isEqualTo(PolicyStatus.UPCOMING);
            softly.assertThat(result.detailLevel()).isEqualTo(DetailLevel.LITE);
            softly.assertThat(result.sourceUrl()).isEqualTo("https://example.com/policy/1");
        });
    }
}
