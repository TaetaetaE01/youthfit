package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.SourceType;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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

        PolicySource source = PolicySource.builder()
                .policy(policy)
                .sourceType(SourceType.YOUTH_CENTER)
                .externalId("ext-2")
                .sourceUrl("https://example.com/policy/2")
                .rawJson("{}")
                .sourceHash("hash2")
                .build();

        // when
        PolicySummaryResult result = PolicySummaryResult.from(policy, source);

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

    @Test
    @DisplayName("source 가 있을 때 sourceType/sourceLabel 모두 채워진다")
    void sourcePresent_populatesSourceFields() {
        Policy policy = Policy.builder()
                .title("테스트 정책")
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);

        PolicySource source = PolicySource.builder()
                .policy(policy)
                .sourceType(SourceType.BOKJIRO_CENTRAL)
                .externalId("ext-1")
                .sourceUrl("https://example.com/policy/1")
                .rawJson("{}")
                .sourceHash("hash")
                .build();

        PolicySummaryResult result = PolicySummaryResult.from(policy, source);

        assertThat(result.sourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
        assertThat(result.sourceLabel()).isEqualTo("복지로");
    }

    @Test
    @DisplayName("source 가 null 일 때 sourceType/sourceLabel 모두 null")
    void sourceNull_sourceFieldsNull() {
        Policy policy = Policy.builder()
                .title("테스트 정책")
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);

        PolicySummaryResult result = PolicySummaryResult.from(policy, null);

        assertThat(result.sourceType()).isNull();
        assertThat(result.sourceLabel()).isNull();
    }
}
