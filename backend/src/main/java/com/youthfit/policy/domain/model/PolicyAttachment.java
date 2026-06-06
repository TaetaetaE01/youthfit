package com.youthfit.policy.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "policy_attachment",
        indexes = {
                @Index(name = "idx_policy_attachment_status_updated", columnList = "extraction_status,updated_at")
        }
)
public class PolicyAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "media_type", length = 100)
    private String mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private AttachmentStatus extractionStatus = AttachmentStatus.PENDING;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "extraction_retry_count", nullable = false)
    private int extractionRetryCount = 0;

    @Column(name = "extraction_error", length = 500)
    private String extractionError;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 30)
    private SkipReason skipReason;

    @Builder
    private PolicyAttachment(String name, String url, String mediaType) {
        this.name = name;
        this.url = url;
        this.mediaType = mediaType;
        this.extractionStatus = AttachmentStatus.PENDING;
        this.extractionRetryCount = 0;
    }

    void assignTo(Policy policy) {
        this.policy = policy;
    }

    private static final int ERROR_MAX_LENGTH = 500;

    public void markDownloading() {
        require(extractionStatus == AttachmentStatus.PENDING, AttachmentStatus.DOWNLOADING);
        this.extractionStatus = AttachmentStatus.DOWNLOADING;
    }

    public void markDownloaded(String storageKey, String fileHash) {
        require(extractionStatus == AttachmentStatus.DOWNLOADING, AttachmentStatus.DOWNLOADED);
        this.extractionStatus = AttachmentStatus.DOWNLOADED;
        this.storageKey = storageKey;
        this.fileHash = fileHash;
    }

    public void markExtracting() {
        require(extractionStatus == AttachmentStatus.DOWNLOADED, AttachmentStatus.EXTRACTING);
        this.extractionStatus = AttachmentStatus.EXTRACTING;
    }

    public void markExtracted(String text) {
        require(extractionStatus == AttachmentStatus.EXTRACTING, AttachmentStatus.EXTRACTED);
        this.extractionStatus = AttachmentStatus.EXTRACTED;
        this.extractedText = text;
        this.extractionError = null;
    }

    public void markSkipped(SkipReason reason) {
        require(!extractionStatus.isTerminal(), AttachmentStatus.SKIPPED);
        this.extractionStatus = AttachmentStatus.SKIPPED;
        this.skipReason = reason;
    }

    public void markFailed(String error) {
        require(!extractionStatus.isTerminal(), AttachmentStatus.FAILED);
        this.extractionStatus = AttachmentStatus.FAILED;
        this.extractionRetryCount += 1;
        this.extractionError = truncate(error);
    }

    public void markPendingReextraction() {
        // 재적재 시 정책의 모든 첨부에 일괄 호출되는 best-effort 동작이다.
        // 이미 완료된(EXTRACTED/SKIPPED/FAILED) 첨부만 재추출 대기로 되돌리고,
        // 대기/진행 중(PENDING/DOWNLOADING/DOWNLOADED/EXTRACTING)인 첨부는 멱등 no-op 으로
        // 두어 진행 흐름을 깨거나 이미 받은 다운로드(storageKey/fileHash)를 헛되게 만들지 않는다.
        boolean reextractable = extractionStatus == AttachmentStatus.EXTRACTED
                || extractionStatus == AttachmentStatus.SKIPPED
                || extractionStatus == AttachmentStatus.FAILED;
        if (!reextractable) {
            return;
        }
        this.extractionStatus = AttachmentStatus.PENDING;
        this.extractionRetryCount = 0;
        this.extractionError = null;
        this.skipReason = null;
    }

    public void resetFailedToPending() {
        require(extractionStatus == AttachmentStatus.FAILED, AttachmentStatus.PENDING);
        this.extractionStatus = AttachmentStatus.PENDING;
    }

    private void require(boolean condition, AttachmentStatus target) {
        if (!condition) throw invalidTransition(target);
    }

    private IllegalStateException invalidTransition(AttachmentStatus target) {
        return new IllegalStateException(
                "invalid transition: " + extractionStatus + " → " + target);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_MAX_LENGTH ? s : s.substring(0, ERROR_MAX_LENGTH);
    }
}
