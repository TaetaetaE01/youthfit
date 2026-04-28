package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentDownloader;
import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentDownloadServiceTest {

    @Mock private PolicyAttachmentRepository repository;
    @Mock private PolicyAttachmentApplicationService stateService;
    @Mock private AttachmentStorage storage;
    @Mock private AttachmentDownloader downloader;
    @InjectMocks private AttachmentDownloadService sut;

    private PolicyAttachment attachment;

    @BeforeEach
    void setUp() {
        attachment = PolicyAttachment.builder()
                .name("a.pdf").url("http://x/a.pdf").mediaType("application/pdf")
                .build();
        sut.setMaxSizeMb(50);
        sut.setMimeWhitelist(Set.of("application/pdf"));
    }

    @Test
    void downloadOne_정상흐름_markDownloading_저장_markDownloaded() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(attachment));
        when(downloader.download(eq("http://x/a.pdf"), anyLong()))
                .thenReturn(new AttachmentDownloader.DownloadedFile(
                        new ByteArrayInputStream("data".getBytes()), 4, "application/pdf"));
        when(storage.put(any(), anyString(), eq("application/pdf")))
                .thenReturn(new StorageReference("attachments/1.pdf", 4, "deadbeef"));

        sut.downloadOne(1L);

        verify(stateService).markDownloading(1L);
        verify(stateService).markDownloaded(1L, "attachments/1.pdf", "deadbeef");
        verify(stateService, never()).markFailed(anyLong(), anyString());
        verify(stateService, never()).markSkipped(anyLong(), any());
    }

    @Test
    void downloadOne_화이트리스트_외_mediaType_은_SKIPPED() {
        attachment = PolicyAttachment.builder()
                .name("a.png").url("http://x/a.png").mediaType("image/png").build();
        when(repository.findById(2L)).thenReturn(Optional.of(attachment));

        sut.downloadOne(2L);

        verify(stateService).markDownloading(2L);
        verify(stateService).markSkipped(2L, SkipReason.UNSUPPORTED_MIME);
        verifyNoInteractions(downloader, storage);
    }

    @Test
    void downloadOne_OVERSIZED_시_SKIPPED() {
        when(repository.findById(3L)).thenReturn(Optional.of(attachment));
        when(downloader.download(anyString(), anyLong()))
                .thenThrow(new AttachmentDownloader.OversizedException("too big"));

        sut.downloadOne(3L);

        verify(stateService).markSkipped(3L, SkipReason.OVERSIZED);
    }

    @Test
    void downloadOne_HTTP_실패_시_FAILED() {
        when(repository.findById(4L)).thenReturn(Optional.of(attachment));
        when(downloader.download(anyString(), anyLong()))
                .thenThrow(new AttachmentDownloader.DownloadException("timeout", new RuntimeException()));

        sut.downloadOne(4L);

        verify(stateService).markFailed(eq(4L), contains("timeout"));
    }
}
