package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyAttachmentApplicationServiceTest {

    @Mock private PolicyAttachmentRepository repository;
    @InjectMocks private PolicyAttachmentApplicationService sut;

    private PolicyAttachment attachment;

    @BeforeEach
    void setUp() {
        attachment = PolicyAttachment.builder()
                .name("a.pdf")
                .url("http://x")
                .mediaType("application/pdf")
                .build();
    }

    @Test
    void markDownloading_은_도메인_메서드를_위임_호출한다() {
        when(repository.findById(1L)).thenReturn(Optional.of(attachment));
        sut.markDownloading(1L);
        assertThat(attachment.getExtractionStatus()).isEqualTo(AttachmentStatus.DOWNLOADING);
    }

    @Test
    void resetFailedToPending_은_FAILED_목록을_PENDING_으로_되돌리고_개수반환() {
        attachment.markDownloading();
        attachment.markFailed("err");
        when(repository.findFailedRetryable(20, 3)).thenReturn(List.of(attachment));
        int count = sut.resetFailedToPending(20, 3);
        assertThat(count).isEqualTo(1);
        assertThat(attachment.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
    }

    @Test
    void markPendingReextraction_은_정책의_모든_첨부를_PENDING_으로() {
        attachment.markDownloading();
        attachment.markDownloaded("k", "h");
        attachment.markExtracting();
        attachment.markExtracted("text");
        when(repository.findByPolicyId(99L)).thenReturn(List.of(attachment));
        sut.markPendingReextraction(99L);
        assertThat(attachment.getExtractionStatus()).isEqualTo(AttachmentStatus.PENDING);
    }
}
