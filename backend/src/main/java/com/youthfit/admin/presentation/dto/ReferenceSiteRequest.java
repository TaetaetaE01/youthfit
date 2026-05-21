package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 관리자 입력 참조 사이트 요청 DTO.
 * source 가 null 이면 ADMIN 으로 강제된다.
 */
public record ReferenceSiteRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^https?://.+") String url,
        PolicyReferenceSiteSource source
) { }
