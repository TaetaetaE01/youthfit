package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;

import java.time.Instant;
import java.util.List;

/**
 * 단일 정책의 enrichment 상세 뷰.
 */
public record PolicyEnrichmentDetailView(
        Long policyId,
        String title,
        EnrichmentView enrichment,
        DetailLevel detailLevel,
        List<UrlView> referenceSites,
        List<EnrichmentJobView> recentJobs,
        boolean needsReview
) {
    public record EnrichmentView(
            EnrichmentStatus status,
            Double confidence,
            Instant fetchedAt,
            String extractor,
            SectionsView sections
    ) {
        public static EnrichmentView of(PolicyEnrichment e) {
            if (e == null) return null;
            return new EnrichmentView(
                    e.status(),
                    e.confidence(),
                    e.fetchedAt(),
                    e.extractor(),
                    SectionsView.of(e.sections())
            );
        }
    }

    public record SectionsView(
            String supportTarget,
            String supportContent,
            String eligibilityCriteria,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote,
            String policyOverview,
            String operatingOrganization,
            String contactPhone
    ) {
        public static SectionsView of(PolicyEnrichment.Sections s) {
            if (s == null) return null;
            return new SectionsView(
                    s.supportTarget(),
                    s.supportContent(),
                    s.eligibilityCriteria(),
                    s.applyMethod(),
                    s.requiredDocuments(),
                    s.deadlineNote(),
                    s.policyOverview(),
                    s.operatingOrganization(),
                    s.contactPhone()
            );
        }
    }

    public static PolicyEnrichmentDetailView of(Policy policy, List<EnrichmentJob> recentJobs, boolean needsReview) {
        List<UrlView> sites = policy.getReferenceSites() == null
                ? List.of()
                : policy.getReferenceSites().stream().map(UrlView::of).toList();
        List<EnrichmentJobView> jobs = recentJobs == null
                ? List.of()
                : recentJobs.stream().map(EnrichmentJobView::of).toList();
        return new PolicyEnrichmentDetailView(
                policy.getId(),
                policy.getTitle(),
                EnrichmentView.of(policy.getEnrichment()),
                policy.getDetailLevel(),
                sites,
                jobs,
                needsReview
        );
    }
}
