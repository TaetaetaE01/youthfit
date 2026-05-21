package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import org.springframework.stereotype.Component;

/**
 * Enrichment 결과를 사용자에게 그대로 노출할지, 관리자 검토(review)가 필요한지 판정하는 도메인 정책.
 *
 * <p>다음 중 하나라도 해당하면 검토 필요로 판정한다.
 * <ul>
 *   <li>enrichment 자체가 없음</li>
 *   <li>status가 OK가 아님 (FETCH_FAILED, LOW_CONFIDENCE 등 실패 케이스)</li>
 *   <li>confidence가 임계값(0.6) 미만</li>
 *   <li>detailLevel이 LITE — 본문 정보 자체가 부족</li>
 *   <li>핵심 섹션(supportTarget / supportContent / eligibilityCriteria) 중 2개 이상 누락</li>
 * </ul>
 */
@Component
public class EnrichmentReviewPolicy {

    public static final double CONFIDENCE_THRESHOLD = 0.6;
    public static final int MIN_MISSING_CORE_SECTIONS = 2;

    public boolean needsReview(Policy policy) {
        PolicyEnrichment e = policy.getEnrichment();
        if (e == null) {
            return true;
        }
        if (e.status() != EnrichmentStatus.OK) {
            return true;
        }
        if (e.confidence() == null || e.confidence() < CONFIDENCE_THRESHOLD) {
            return true;
        }
        if (policy.getDetailLevel() == DetailLevel.LITE) {
            return true;
        }
        if (missingCoreSections(e) >= MIN_MISSING_CORE_SECTIONS) {
            return true;
        }
        return false;
    }

    private int missingCoreSections(PolicyEnrichment e) {
        PolicyEnrichment.Sections s = e.sections();
        if (s == null) {
            return 3;
        }
        int missing = 0;
        if (isBlank(s.supportTarget())) {
            missing++;
        }
        if (isBlank(s.supportContent())) {
            missing++;
        }
        if (isBlank(s.eligibilityCriteria())) {
            missing++;
        }
        return missing;
    }

    private boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
