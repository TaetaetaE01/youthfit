package com.youthfit.policy.domain.exception;

/**
 * 첨부 파일을 찾을 수 없거나, storageKey/외부 URL 모두 부재한 경우의 도메인 전용 예외.
 * GlobalExceptionHandler 에서 404 로 매핑된다.
 */
public class AttachmentNotFoundException extends RuntimeException {

    private final Long attachmentId;

    public AttachmentNotFoundException(Long attachmentId) {
        super("PolicyAttachment not found: " + attachmentId);
        this.attachmentId = attachmentId;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }
}
