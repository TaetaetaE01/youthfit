package com.youthfit.ingestion.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.config.CostGuardProperties;
import com.youthfit.ingestion.application.port.AttachmentEmbeddingJudge;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult.AttachmentDecision;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttachmentReindexService 임베딩 게이트")
class AttachmentReindexServiceGateTest {

    @Mock PolicyRepository policyRepository;
    @Mock PolicyAttachmentRepository attachmentRepository;
    @Mock RagIndexingService ragIndexingService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AttachmentEmbeddingJudge embeddingJudge;

    CostGuard costGuard = new CostGuard(new CostGuardProperties(""));
    AttachmentReindexService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentReindexService(
                policyRepository, attachmentRepository, ragIndexingService,
                eventPublisher, costGuard, embeddingJudge);
        ReflectionTestUtils.setField(service, "maxContentKb", 200);
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy()));
        given(ragIndexingService.indexPolicyDocument(any()))
                .willReturn(new IndexingResult(1L, 0, false));
    }

    private Policy policy() {
        Policy p = Policy.builder().title("청년월세").body("본문").build();
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    private PolicyAttachment extracted(long id, String name, String text) {
        PolicyAttachment a = PolicyAttachment.builder()
                .name(name).url("http://x/" + id).mediaType("application/x-hwp").build();
        a.markDownloading();
        a.markDownloaded("k" + id, "h" + id);
        a.markExtracting();
        a.markExtracted(text);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Nested
    @DisplayName("첨부 개수 분기")
    class CountBranch {

        @Test
        @DisplayName("첨부가 1개면 게이트를 호출하지 않는다")
        void singleAttachmentSkipsGate() {
            given(attachmentRepository.findExtractedByPolicyId(1L))
                    .willReturn(List.of(extracted(10L, "안내.hwp", "내용")));

            service.reindex(1L);

            verify(embeddingJudge, never()).judge(any());
        }

        @Test
        @DisplayName("첨부가 2개 이상이고 미판정이면 게이트를 호출한다")
        void multipleUndecidedCallsGate() {
            given(attachmentRepository.findExtractedByPolicyId(1L))
                    .willReturn(List.of(
                            extracted(10L, "사업안내.hwp", "지원대상 금액 일정"),
                            extracted(11L, "동의서.hwp", "개인정보 수집 동의")));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"),
                    new AttachmentDecision(11L, false, "단순 동의서"))));

            service.reindex(1L);

            verify(embeddingJudge).judge(any());
        }
    }

    @Nested
    @DisplayName("선별·머지")
    class SelectAndMerge {

        @Test
        @DisplayName("제외 판정된 첨부는 머지 content 에서 빠진다")
        void excludedAttachmentNotMerged() {
            given(attachmentRepository.findExtractedByPolicyId(1L))
                    .willReturn(List.of(
                            extracted(10L, "사업안내.hwp", "지원대상-금액-일정-본문"),
                            extracted(11L, "동의서.hwp", "개인정보수집동의-양식-본문")));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"),
                    new AttachmentDecision(11L, false, "단순 동의서"))));

            service.reindex(1L);

            ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                    ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
            verify(ragIndexingService).indexPolicyDocument(captor.capture());
            String content = captor.getValue().content();
            assertThat(content).contains("attachment-id=10");
            assertThat(content).doesNotContain("attachment-id=11");
        }

        @Test
        @DisplayName("판정 결과가 첨부에 영속화된다")
        void persistsDecision() {
            PolicyAttachment a10 = extracted(10L, "사업안내.hwp", "지원대상");
            PolicyAttachment a11 = extracted(11L, "동의서.hwp", "동의");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"),
                    new AttachmentDecision(11L, false, "단순 동의서"))));

            service.reindex(1L);

            assertThat(a10.getEmbeddingIncluded()).isTrue();
            assertThat(a11.getEmbeddingIncluded()).isFalse();
            verify(attachmentRepository).save(a10);
            verify(attachmentRepository).save(a11);
        }
    }

    @Nested
    @DisplayName("캐시")
    class Cache {

        @Test
        @DisplayName("이미 판정된 첨부만 있으면 게이트를 재호출하지 않는다")
        void allDecidedSkipsGate() {
            PolicyAttachment a10 = extracted(10L, "a.hwp", "내용1");
            PolicyAttachment a11 = extracted(11L, "b.hwp", "내용2");
            a10.decideEmbedding(true, "이전 판정");
            a11.decideEmbedding(false, "이전 판정");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));

            service.reindex(1L);

            verify(embeddingJudge, never()).judge(any());
        }
    }

    @Nested
    @DisplayName("fail-open")
    class FailOpen {

        @Test
        @DisplayName("게이트 예외 시 미판정 첨부를 모두 포함으로 저장한다")
        void gateFailureIncludesAll() {
            PolicyAttachment a10 = extracted(10L, "a.hwp", "내용1");
            PolicyAttachment a11 = extracted(11L, "b.hwp", "내용2");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));
            given(embeddingJudge.judge(any())).willThrow(new RuntimeException("timeout"));

            service.reindex(1L);

            assertThat(a10.getEmbeddingIncluded()).isTrue();
            assertThat(a11.getEmbeddingIncluded()).isTrue();
            ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                    ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
            verify(ragIndexingService).indexPolicyDocument(captor.capture());
            assertThat(captor.getValue().content())
                    .contains("attachment-id=10").contains("attachment-id=11");
        }
    }

    @Nested
    @DisplayName("부분 캐시")
    class PartialCache {

        @Test
        @DisplayName("하나는 이미 false 판정, 다른 하나는 미판정 — 게이트는 호출되고 미판정 1개만 전달되며 false 판정 첨부는 머지에서 제외된다")
        void partiallyDecidedCallsGateForUndecidedOnlyAndExcludesPreDecidedFalse() {
            PolicyAttachment a10 = extracted(10L, "사업안내.hwp", "지원대상-금액-일정");
            PolicyAttachment a11 = extracted(11L, "동의서.hwp", "개인정보수집동의");
            a11.decideEmbedding(false, "이전 판정");  // a11 은 이미 false 판정
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"))));

            service.reindex(1L);

            // 게이트는 반드시 호출된다 (a10 이 미판정이므로)
            verify(embeddingJudge).judge(any());
            // a11(pre-decided false)은 머지에서 제외, a10(embed=true)은 포함
            ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                    ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
            verify(ragIndexingService).indexPolicyDocument(captor.capture());
            String content = captor.getValue().content();
            assertThat(content).contains("attachment-id=10");
            assertThat(content).doesNotContain("attachment-id=11");
        }
    }

    @Nested
    @DisplayName("gate-no-decision")
    class GateNoDecision {

        @Test
        @DisplayName("LLM 이 일부 id 를 누락 반환하면 누락 첨부는 보수적 포함(gate-no-decision)으로 저장되고 머지에 포함된다")
        void missingDecisionFallsBackToConservativeInclude() {
            PolicyAttachment a10 = extracted(10L, "사업안내.hwp", "지원대상-금액-일정");
            PolicyAttachment a11 = extracted(11L, "동의서.hwp", "개인정보수집동의");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));
            // 게이트가 a10 만 반환하고 a11 은 누락
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"))));

            service.reindex(1L);

            // a11 은 gate-no-decision 으로 보수적 포함 저장
            assertThat(a11.getEmbeddingIncluded()).isTrue();
            verify(attachmentRepository).save(a11);
            // 머지 content 에 a10·a11 모두 포함
            ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                    ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
            verify(ragIndexingService).indexPolicyDocument(captor.capture());
            String content = captor.getValue().content();
            assertThat(content).contains("attachment-id=10").contains("attachment-id=11");
        }
    }
}
