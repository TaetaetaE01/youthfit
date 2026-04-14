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

@DisplayName("PolicySummaryResult DTO 변환")
class PolicySummaryResultTest {

    @Test
    @DisplayName("Policy Entity로부터 요약 필드를 올바르게 변환한다")
    void from_mapsAllFields() {
        // given
        Policy policy = Policy.builder()
                .title("청년 취업 지원")
                .summary("취업 역량 강화")
                .category(Category.JOBS)
                .regionCode("26")
                .applyStart(LocalDate.of(2026, 3, 1))
                .applyEnd(LocalDate.of(2026, 6, 30))
                .build();
        ReflectionTestUtils.setField(policy, "id", 2L);

        // when
        PolicySummaryResult result = PolicySummaryResult.from(policy);

        // then
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result.id()).isEqualTo(2L);
            softly.assertThat(result.title()).isEqualTo("청년 취업 지원");
            softly.assertThat(result.summary()).isEqualTo("취업 역량 강화");
            softly.assertThat(result.category()).isEqualTo(Category.JOBS);
            softly.assertThat(result.regionCode()).isEqualTo("26");
            softly.assertThat(result.status()).isEqualTo(PolicyStatus.UPCOMING);
            softly.assertThat(result.detailLevel()).isEqualTo(DetailLevel.LITE);
        });
    }
}
