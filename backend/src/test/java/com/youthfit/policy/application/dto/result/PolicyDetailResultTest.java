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

        PolicySource source = PolicySource.builder()
                .policy(policy)
                .sourceType(SourceType.BOKJIRO_CENTRAL)
                .externalId("ext-1")
                .sourceUrl("https://example.com/policy/1")
                .rawJson("{}")
                .sourceHash("hash")
                .build();

        // when
        PolicyDetailResult result = PolicyDetailResult.from(policy, source);

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

    @Test
    @DisplayName("source 가 있을 때 sourceType/sourceLabel/sourceUrl 모두 채워진다")
    void sourcePresent_populatesAllSourceFields() {
        Policy policy = createPolicyWithIdAndTitle(1L, "테스트 정책");
        PolicySource source = PolicySource.builder()
                .policy(policy)
                .sourceType(SourceType.BOKJIRO_CENTRAL)
                .externalId("ext-1")
                .sourceUrl("https://example.com/policy/1")
                .rawJson("{}")
                .sourceHash("hash")
                .build();

        PolicyDetailResult result = PolicyDetailResult.from(policy, source);

        assertThat(result.sourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
        assertThat(result.sourceLabel()).isEqualTo("복지로");
        assertThat(result.sourceUrl()).isEqualTo("https://example.com/policy/1");
    }

    @Test
    @DisplayName("source 가 null 일 때 sourceType/sourceLabel/sourceUrl 모두 null")
    void sourceNull_allSourceFieldsNull() {
        Policy policy = createPolicyWithIdAndTitle(1L, "테스트 정책");

        PolicyDetailResult result = PolicyDetailResult.from(policy, null);

        assertThat(result.sourceType()).isNull();
        assertThat(result.sourceLabel()).isNull();
        assertThat(result.sourceUrl()).isNull();
    }

    // ── 헬퍼 메서드 ──

    private Policy createPolicyWithIdAndTitle(Long id, String title) {
        Policy policy = Policy.builder()
                .title(title)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
