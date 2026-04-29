package com.youthfit.ingestion.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.config.CostGuardProperties;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentReindexServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyAttachmentRepository attachmentRepository;
    @Mock private RagIndexingService ragIndexingService;
    @Mock private GuideGenerationService guideGenerationService;
    @Spy private CostGuard costGuard = new CostGuard(new CostGuardProperties(""));
    @InjectMocks private AttachmentReindexService sut;

    @BeforeEach
    void setUp() {
        sut.setMaxContentKb(200);
    }

    @Test
    void reindex_정상_정책본문과_첨부텍스트를_합쳐_RagIndexing_호출() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("정책 본문");
        when(policy.getTitle()).thenReturn("title");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        PolicyAttachment a1 = pa(11L, "공고문.pdf", "내용 A");
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of(a1));
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 5, true));

        sut.reindex(1L);

        ArgumentCaptor<IndexPolicyDocumentCommand> captor = ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
        verify(ragIndexingService).indexPolicyDocument(captor.capture());
        String content = captor.getValue().content();
        assertThat(content).contains("정책 본문");
        assertThat(content).contains("=== 첨부 attachment-id=11 name=\"공고문.pdf\" ===");
        assertThat(content).contains("내용 A");

        verify(guideGenerationService).generateGuide(any());
    }

    @Test
    void mergeContent_첨부에_페이지_sentinel_있으면_LLM_친화적_마커로_변환() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("정책 본문 텍스트");

        PolicyAttachment att = pa(12L, "시행규칙.pdf",
                "\f<page=1>\n첫 페이지 텍스트\n\f<page=2>\n둘째 페이지 텍스트\n");

        String merged = sut.mergeContent(policy, List.of(att));

        assertThat(merged).contains("=== 정책 본문 ===");
        assertThat(merged).contains("정책 본문 텍스트");
        assertThat(merged).contains("=== 첨부 attachment-id=12 name=\"시행규칙.pdf\" ===");
        assertThat(merged).contains("--- page=1 ---");
        assertThat(merged).contains("첫 페이지 텍스트");
        assertThat(merged).contains("--- page=2 ---");
        assertThat(merged).contains("둘째 페이지 텍스트");
        // sentinel 원본은 잔존하지 않아야 함
        assertThat(merged).doesNotContain("\f<page=");
    }

    @Test
    void mergeContent_HWP등_단일페이지_sentinel_은_page_null_마커로_변환() {
        Policy policy = mock(Policy.class);
        when(policy.getBody()).thenReturn("본문");

        PolicyAttachment hwp = pa(13L, "안내문.hwp",
                "\f<page=null>\n전체 텍스트\n");

        String merged = sut.mergeContent(policy, List.of(hwp));

        assertThat(merged).contains("=== 첨부 attachment-id=13 name=\"안내문.hwp\" ===");
        assertThat(merged).contains("--- page=null ---");
        assertThat(merged).contains("전체 텍스트");
        assertThat(merged).doesNotContain("\f<page=");
    }

    @Test
    void reindex_updated_false_면_가이드_재생성_안함() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("body");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 0, false));

        sut.reindex(1L);

        verify(guideGenerationService, never()).generateGuide(any());
    }

    @Test
    void reindex_200KB_초과_시_초과분_첨부_생략() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(1L);
        when(policy.getBody()).thenReturn("본문");
        when(policy.getTitle()).thenReturn("t");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        PolicyAttachment big1 = pa(101L, "a.pdf", "X".repeat(150_000));
        PolicyAttachment big2 = pa(102L, "b.pdf", "Y".repeat(100_000));
        PolicyAttachment skipped = pa(103L, "c.pdf", "Z".repeat(50_000));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of(big1, big2, skipped));
        when(ragIndexingService.indexPolicyDocument(any())).thenReturn(new IndexingResult(1L, 1, true));

        sut.reindex(1L);

        ArgumentCaptor<IndexPolicyDocumentCommand> captor = ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
        verify(ragIndexingService).indexPolicyDocument(captor.capture());
        String content = captor.getValue().content();
        // 200KB = 200 * 1024 = 204800
        assertThat(content.length()).isLessThanOrEqualTo(204_800);
        assertThat(content).contains("a.pdf");
    }

    private PolicyAttachment pa(Long id, String name, String text) {
        PolicyAttachment a = mock(PolicyAttachment.class);
        when(a.getId()).thenReturn(id);
        when(a.getName()).thenReturn(name);
        when(a.getExtractedText()).thenReturn(text);
        return a;
    }
}
