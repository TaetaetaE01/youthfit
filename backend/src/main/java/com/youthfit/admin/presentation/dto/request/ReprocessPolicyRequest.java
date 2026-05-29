package com.youthfit.admin.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 정책 전체 재처리 요청 DTO.
 *
 * <p>운영 추적을 위해 재처리 사유 입력이 필수다.</p>
 */
@Schema(description = "정책 전체 재처리 요청")
public record ReprocessPolicyRequest(
        @Schema(description = "재처리 사유 (운영 로그용)", example = "ENRICHMENT 결과 오탐")
        @NotBlank String reason
) { }
