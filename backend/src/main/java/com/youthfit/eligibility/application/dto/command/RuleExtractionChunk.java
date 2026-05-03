package com.youthfit.eligibility.application.dto.command;

public record RuleExtractionChunk(
        String content,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd
) {

    public boolean isAttachment() {
        return attachmentId != null;
    }
}
