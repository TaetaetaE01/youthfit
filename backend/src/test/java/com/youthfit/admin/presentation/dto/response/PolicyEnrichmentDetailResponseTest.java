package com.youthfit.admin.presentation.dto.response;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyEnrichmentDetailResponseTest {

    @Test
    void of_는_첨부의_상태와_실패사유를_매핑한다() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(20L);
        when(policy.getTitle()).thenReturn("청년정책네트워크");

        PolicyAttachment extracted = PolicyAttachment.builder()
                .name("안내.pdf").url("http://x/a.pdf").mediaType("application/pdf").build();
        extracted.markDownloading();
        extracted.markDownloaded("key", "hash");
        extracted.markExtracting();
        extracted.markExtracted("본문 텍스트");

        PolicyAttachment failed = PolicyAttachment.builder()
                .name("공고문.hwpx").url("http://x/b").mediaType("application/x-hwp").build();
        failed.markDownloading();
        failed.markFailed("DownloadException: download failed <- InvalidMediaTypeException: does not contain '/'");

        PolicyAttachment skipped = PolicyAttachment.builder()
                .name("스캔본.pdf").url("http://x/c.pdf").mediaType("application/pdf").build();
        skipped.markSkipped(SkipReason.SCANNED_PDF);

        var resp = PolicyEnrichmentDetailResponse.of(policy, List.of(), false, List.of(extracted, failed, skipped));

        assertThat(resp.attachments()).hasSize(3);

        var ok = resp.attachments().get(0);
        assertThat(ok.name()).isEqualTo("안내.pdf");
        assertThat(ok.status()).isEqualTo(AttachmentStatus.EXTRACTED);
        assertThat(ok.hasText()).isTrue();

        var f = resp.attachments().get(1);
        assertThat(f.name()).isEqualTo("공고문.hwpx");
        assertThat(f.status()).isEqualTo(AttachmentStatus.FAILED);
        assertThat(f.error()).contains("InvalidMediaTypeException");

        var s = resp.attachments().get(2);
        assertThat(s.status()).isEqualTo(AttachmentStatus.SKIPPED);
        assertThat(s.skipReason()).isEqualTo(SkipReason.SCANNED_PDF);
    }

    @Test
    void of_는_첨부가_null_이면_빈_리스트() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getTitle()).thenReturn("제목");

        var resp = PolicyEnrichmentDetailResponse.of(policy, List.of(), false, null);

        assertThat(resp.attachments()).isEmpty();
    }
}
