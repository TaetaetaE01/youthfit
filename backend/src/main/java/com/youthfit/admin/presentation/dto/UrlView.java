package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;

/**
 * 참조 사이트 평면 뷰.
 */
public record UrlView(
        String name,
        String url,
        PolicyReferenceSiteSource source
) {
    public static UrlView of(PolicyReferenceSite s) {
        if (s == null) return null;
        return new UrlView(s.name(), s.url(), s.source());
    }
}
