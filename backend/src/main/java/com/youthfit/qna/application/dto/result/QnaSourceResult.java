package com.youthfit.qna.application.dto.result;

public record QnaSourceResult(
        Long policyId,
        Long attachmentId,
        String attachmentLabel,
        Integer pageStart,
        Integer pageEnd,
        String excerpt
) {
}
