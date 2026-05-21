package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;

/**
 * 참조 사이트 평면 응답 DTO.
 */
public record UrlResponse(
        String name,
        String url,
        PolicyReferenceSiteSource source
) {
    public static UrlResponse of(PolicyReferenceSite s) {
        if (s == null) return null;
        return new UrlResponse(s.name(), s.url(), s.source());
    }
}
