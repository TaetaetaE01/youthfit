package com.youthfit.admin.application.dto;

import com.youthfit.policy.domain.model.SourceType;

/**
 * 정책 출처 태그 (application Result).
 * code 는 {@link SourceType#name()}, label 은 한글 표시명.
 */
public record SourceTagResult(String code, String label) {
    public static SourceTagResult from(SourceType sourceType) {
        return new SourceTagResult(sourceType.name(), sourceType.getLabel());
    }
}
