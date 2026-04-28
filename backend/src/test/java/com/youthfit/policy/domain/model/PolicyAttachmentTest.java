package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyAttachmentTest {

    private PolicyAttachment newAttachment() {
        return PolicyAttachment.builder()
                .name("공고문.pdf")
                .url("https://example.com/file.pdf")
                .mediaType("application/pdf")
                .build();
    }

    @Test
    void 신규_첨부는_PENDING_상태로_생성된다() {
        PolicyAttachment a = newAttachment();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isZero();
        assertThat(a.getExtractedText()).isNull();
        assertThat(a.getSkipReason()).isNull();
    }

    @Test
    void markDownloading_은_PENDING에서만_허용된다() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.DOWNLOADING);
    }

    @Test
    void markDownloading_을_DOWNLOADING_상태에서_다시_호출하면_예외() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        assertThatThrownBy(a::markDownloading)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid transition");
    }

    @Test
    void markDownloaded_는_DOWNLOADING에서만_허용되며_storageKey와_fileHash를_세팅() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("attachments/2026/04/abc.pdf", "abc123hash");
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.DOWNLOADED);
        assertThat(a.getStorageKey()).isEqualTo("attachments/2026/04/abc.pdf");
        assertThat(a.getFileHash()).isEqualTo("abc123hash");
    }

    @Test
    void markExtracting_은_DOWNLOADED에서만_허용() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.EXTRACTING);
    }

    @Test
    void markExtracted_는_EXTRACTING에서만_허용하며_텍스트_저장() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markExtracted("추출된 텍스트");
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.EXTRACTED);
        assertThat(a.getExtractedText()).isEqualTo("추출된 텍스트");
    }

    @Test
    void markFailed_는_재시도카운트_증가_및_에러메시지_저장() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markFailed("network timeout");
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.FAILED);
        assertThat(a.getExtractionRetryCount()).isEqualTo(1);
        assertThat(a.getExtractionError()).isEqualTo("network timeout");
    }

    @Test
    void markFailed_에러메시지_500자_초과시_truncate() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        String longError = "x".repeat(600);
        a.markFailed(longError);
        assertThat(a.getExtractionError()).hasSize(500);
    }

    @Test
    void markSkipped_는_사유_저장() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markSkipped(SkipReason.SCANNED_PDF);
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.SKIPPED);
        assertThat(a.getSkipReason()).isEqualTo(SkipReason.SCANNED_PDF);
    }

    @Test
    void markSkipped_는_종료상태_EXTRACTED_에서_호출하면_예외() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markExtracted("text");
        assertThatThrownBy(() -> a.markSkipped(SkipReason.SCANNED_PDF))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markPendingReextraction_은_종료상태에서_PENDING_으로_복귀_및_필드_초기화() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markDownloaded("k", "h");
        a.markExtracting();
        a.markExtracted("text");
        a.markPendingReextraction();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isZero();
        assertThat(a.getExtractionError()).isNull();
        assertThat(a.getSkipReason()).isNull();
    }

    @Test
    void markPendingReextraction_은_FAILED에서도_허용() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markFailed("err");
        a.markPendingReextraction();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isZero();
    }

    @Test
    void markPendingReextraction_은_PENDING_상태에서_호출하면_예외() {
        PolicyAttachment a = newAttachment();
        assertThatThrownBy(a::markPendingReextraction)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetFailedToPending_은_FAILED에서_PENDING으로_복귀하지만_retryCount는_유지() {
        PolicyAttachment a = newAttachment();
        a.markDownloading();
        a.markFailed("err1");
        a.resetFailedToPending();
        assertThat(a.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(a.getExtractionRetryCount()).isEqualTo(1);
    }
}
