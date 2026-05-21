package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnrichmentReviewPolicy - needsReview 판정 규칙")
class EnrichmentReviewPolicyTest {

    private final EnrichmentReviewPolicy policy = new EnrichmentReviewPolicy();

    @Test
    @DisplayName("enrichment이 null이면 검토 필요")
    void enrichment_이_null_이면_검토필요() {
        // given
        Policy p = policyWithLevel(DetailLevel.MEDIUM);

        // when & then
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    @DisplayName("status가 FETCH_FAILED이면 confidence가 높아도 검토 필요")
    void status_fetchFailed_이면_검토필요() {
        // given
        Policy p = policyWithLevel(DetailLevel.MEDIUM);
        p.replaceEnrichment(enrichment(EnrichmentStatus.FETCH_FAILED, 0.9, fullSections()));

        // when & then
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    @DisplayName("status가 OK이지만 confidence가 0.6 미만이면 검토 필요")
    void status_ok_이고_confidence_0_59_이면_검토필요() {
        // given
        Policy p = policyWithLevel(DetailLevel.MEDIUM);
        p.replaceEnrichment(enrichment(EnrichmentStatus.OK, 0.59, fullSections()));

        // when & then
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    @DisplayName("OK + 높은 confidence라도 detailLevel이 LITE이면 검토 필요")
    void detailLevel_LITE_이면_검토필요() {
        // given
        Policy p = policyWithLevel(DetailLevel.LITE);
        p.replaceEnrichment(enrichment(EnrichmentStatus.OK, 0.9, fullSections()));

        // when & then
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    @DisplayName("OK + 높은 confidence + MEDIUM이어도 핵심 섹션 2개 이상 누락이면 검토 필요")
    void core_sections_2개_누락이면_검토필요() {
        // given - supportTarget만 있고 supportContent / eligibilityCriteria는 누락
        Policy p = policyWithLevel(DetailLevel.MEDIUM);
        PolicyEnrichment.Sections sections = new PolicyEnrichment.Sections(
                "청년",  // supportTarget present
                null,     // supportContent missing
                null, null, null, null,
                "   ",   // eligibilityCriteria blank
                null, null
        );
        p.replaceEnrichment(enrichment(EnrichmentStatus.OK, 0.9, sections));

        // when & then
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    @DisplayName("OK + 높은 confidence + MEDIUM + 핵심 섹션 3개 모두 채워져 있으면 검토 불필요")
    void 핵심_섹션_모두_있으면_검토불필요() {
        // given
        Policy p = policyWithLevel(DetailLevel.MEDIUM);
        p.replaceEnrichment(enrichment(EnrichmentStatus.OK, 0.9, fullSections()));

        // when & then
        assertThat(policy.needsReview(p)).isFalse();
    }

    // ── helpers ──

    private static Policy policyWithLevel(DetailLevel level) {
        Policy p = Policy.builder()
                .title("샘플 정책")
                .category(Category.JOBS)
                .build();
        if (level != DetailLevel.LITE) {
            p.upgradeDetailLevel(level);
        }
        return p;
    }

    private static PolicyEnrichment enrichment(EnrichmentStatus status, double confidence,
                                               PolicyEnrichment.Sections sections) {
        return new PolicyEnrichment(
                "https://example.com",
                Instant.parse("2026-05-12T15:30:00Z"),
                "openai:gpt-4o-mini",
                confidence,
                status,
                sections,
                List.of(),
                null
        );
    }

    private static PolicyEnrichment.Sections fullSections() {
        return new PolicyEnrichment.Sections(
                "만 19~34세 청년",          // supportTarget
                "월 20만원 지원",            // supportContent
                "온라인 신청",               // applyMethod (non-core)
                "주민등록등본",              // requiredDocuments (non-core)
                "상시 모집",                 // deadlineNote (non-core)
                "정책 개요",                 // policyOverview (non-core)
                "소득 중위 150% 이하",       // eligibilityCriteria
                "서울특별시청",              // operatingOrganization (non-core)
                "02-000-0000"               // contactPhone (non-core)
        );
    }
}
