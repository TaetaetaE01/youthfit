package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
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

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentExtractionSchedulerTest {

    @Mock private PolicyAttachmentRepository repository;
    @Mock private PolicyAttachmentApplicationService stateService;
    @Mock private AttachmentStorage storage;
    @Mock private ExtractionDispatcher dispatcher;
    @Mock private AttachmentDownloadService downloadService;
    @Mock private AttachmentReindexService reindexService;
    @InjectMocks private AttachmentExtractionScheduler sut;

    @BeforeEach
    void setUp() {
        sut.setBatchSize(20);
        sut.setRetryLimit(3);
        sut.setMinTextChars(100);
    }

    @Test
    void runCycle_4_1_resetFailedToPending_먼저() {
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of());

        sut.runCycle();

        verify(stateService).resetFailedToPending(20, 3);
    }

    @Test
    void runCycle_4_2_PENDING_은_downloadOne_으로_위임() {
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(1L);
        when(repository.findPendingForDownload(20)).thenReturn(List.of(p));
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of());

        sut.runCycle();

        verify(downloadService).downloadOne(1L);
    }

    @Test
    void runCycle_4_3_DOWNLOADED_은_dispatcher_호출_후_markExtracted() throws Exception {
        com.youthfit.policy.domain.model.Policy policy = mockPolicy(99L);
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(2L);
        when(p.getStorageKey()).thenReturn("k");
        when(p.getMediaType()).thenReturn("application/pdf");
        when(p.getPolicy()).thenReturn(policy);
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of(p));
        when(storage.get("k")).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(dispatcher.dispatch(any(), anyLong(), eq("application/pdf")))
                .thenReturn(ExtractionResult.success("X".repeat(150)));
        when(repository.isAllTerminalForPolicy(99L)).thenReturn(true);

        sut.runCycle();

        verify(stateService).markExtracting(2L);
        verify(stateService).markExtracted(2L, "X".repeat(150));
        verify(reindexService).reindex(99L);
    }

    @Test
    void runCycle_추출_텍스트_100자_미만이면_SCANNED_PDF_skip() throws Exception {
        com.youthfit.policy.domain.model.Policy policy = mockPolicy(99L);
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(3L);
        when(p.getStorageKey()).thenReturn("k");
        when(p.getMediaType()).thenReturn("application/pdf");
        when(p.getPolicy()).thenReturn(policy);
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of(p));
        when(storage.get("k")).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(dispatcher.dispatch(any(), anyLong(), eq("application/pdf")))
                .thenReturn(ExtractionResult.success("짧다"));
        when(repository.isAllTerminalForPolicy(99L)).thenReturn(true);

        sut.runCycle();

        verify(stateService).markSkipped(3L, SkipReason.SCANNED_PDF);
        verify(stateService, never()).markExtracted(anyLong(), anyString());
    }

    @Test
    void runCycle_정책의_모든_첨부가_종료되지_않으면_reindex_안함() throws Exception {
        com.youthfit.policy.domain.model.Policy policy = mockPolicy(99L);
        PolicyAttachment p = mock(PolicyAttachment.class);
        when(p.getId()).thenReturn(4L);
        when(p.getStorageKey()).thenReturn("k");
        when(p.getMediaType()).thenReturn("application/pdf");
        when(p.getPolicy()).thenReturn(policy);
        when(repository.findPendingForDownload(20)).thenReturn(List.of());
        when(repository.findDownloadedForExtraction(20)).thenReturn(List.of(p));
        when(storage.get("k")).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(dispatcher.dispatch(any(), anyLong(), eq("application/pdf")))
                .thenReturn(ExtractionResult.success("X".repeat(200)));
        when(repository.isAllTerminalForPolicy(99L)).thenReturn(false);

        sut.runCycle();

        verify(reindexService, never()).reindex(anyLong());
    }

    private com.youthfit.policy.domain.model.Policy mockPolicy(long id) {
        com.youthfit.policy.domain.model.Policy policy = mock(com.youthfit.policy.domain.model.Policy.class);
        when(policy.getId()).thenReturn(id);
        return policy;
    }
}
