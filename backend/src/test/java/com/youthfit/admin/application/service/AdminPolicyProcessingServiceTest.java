package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dto.PolicyProcessingDetailResult;
import com.youthfit.admin.application.dto.PolicyProcessingFilter;
import com.youthfit.admin.application.dto.PolicyProcessingListCommand;
import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import com.youthfit.admin.application.dto.PolicyProcessingSort;
import com.youthfit.admin.application.dto.PolicyProcessingStatsResult;
import com.youthfit.admin.application.dto.ReprocessResult;
import com.youthfit.admin.application.dto.StepDetailResult;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.AttachmentExtractionCounts;
import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminPolicyProcessingService")
@ExtendWith(MockitoExtension.class)
class AdminPolicyProcessingServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyProcessingStepRepository stepRepository;
    @Mock private PolicyAttachmentRepository attachmentRepository;
    @Mock private PolicyDocumentRepository documentRepository;
    @Mock private PolicyProcessingStepService stepService;
    @Mock private RagIndexingService ragIndexingService;
    @Mock private AttachmentReindexService attachmentReindexService;
    @Mock private GuideGenerationService guideGenerationService;
    @Mock private EligibilityRuleGenerationService eligibilityRuleGenerationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AdminPolicyProcessingService service;

    private static final PolicyProcessingListCommand DEFAULT_COMMAND = new PolicyProcessingListCommand(
            null, null, PolicyProcessingFilter.ALL, PolicyProcessingSort.UPDATED_DESC, 0, 50);

    @Test
    @DisplayName("RAG SUCCESS + 첨부 0건이면 완성도는 COMPLETE 다")
    void completenessIsCompleteWhenRagSuccessAndNoAttachments() {
        givenSinglePolicyPage(100L, "월세 지원", "11");
        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(
                Map.of(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS))
        );
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(
                Map.of(100L, new AttachmentExtractionCounts(0, 0, 0))
        );
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of()
        );

        PolicyProcessingListResult result = service.findProcessingPolicies(DEFAULT_COMMAND);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness())
                .isEqualTo(PolicyProcessingCompleteness.COMPLETE);
    }

    @Test
    @DisplayName("RAG SUCCESS 지만 첨부 일부만 임베딩됐다면 완성도는 PARTIAL 이다")
    void completenessIsPartialWhenAttachmentsExtractedButEmbeddingMissing() {
        givenSinglePolicyPage(100L, "도전지원금", "11");
        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(
                Map.of(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS))
        );
        // total=5, downloaded=5, extracted=4. 일부만 추출, 임베딩 2건
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(
                Map.of(100L, new AttachmentExtractionCounts(5, 5, 4))
        );
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of(100L, 2L)
        );

        PolicyProcessingListResult result = service.findProcessingPolicies(DEFAULT_COMMAND);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness())
                .isEqualTo(PolicyProcessingCompleteness.PARTIAL);
    }

    @Test
    @DisplayName("RAG FAILED 이면 완성도는 INCOMPLETE 다")
    void completenessIsIncompleteWhenRagFailed() {
        givenSinglePolicyPage(100L, "월세대출", "11");
        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(
                Map.of(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED))
        );
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(
                Map.of(100L, AttachmentExtractionCounts.empty())
        );
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of()
        );

        PolicyProcessingListResult result = service.findProcessingPolicies(DEFAULT_COMMAND);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness())
                .isEqualTo(PolicyProcessingCompleteness.INCOMPLETE);
    }

    @Test
    @DisplayName("findProcessingStats 는 전체/완성도/24h 버킷을 모두 집계한다")
    void findProcessingStats_aggregatesAllCompletenessBuckets() {
        // 5개 정책 mock: 3 COMPLETE, 1 PARTIAL, 1 INCOMPLETE, recent24h 는 2건
        LocalDateTime now = LocalDateTime.now();
        Policy p1 = policyMock(1L, now.minusHours(2));   // COMPLETE, recent
        Policy p2 = policyMock(2L, now.minusDays(2));    // COMPLETE
        Policy p3 = policyMock(3L, now.minusHours(10));  // COMPLETE, recent
        Policy p4 = policyMock(4L, now.minusDays(5));    // PARTIAL
        Policy p5 = policyMock(5L, now.minusDays(7));    // INCOMPLETE
        given(policyRepository.findAllForStats()).willReturn(List.of(p1, p2, p3, p4, p5));

        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(Map.of(
                1L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                2L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                3L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                4L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                5L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED)
        ));
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(Map.of(
                1L, AttachmentExtractionCounts.empty(),
                2L, AttachmentExtractionCounts.empty(),
                3L, AttachmentExtractionCounts.empty(),
                4L, new AttachmentExtractionCounts(3, 3, 1), // PARTIAL: 추출 일부 + 임베딩 1
                5L, AttachmentExtractionCounts.empty()
        ));
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of(4L, 1L)
        );

        PolicyProcessingStatsResult stats = service.findProcessingStats();

        assertThat(stats.totalCount()).isEqualTo(5);
        assertThat(stats.completeCount()).isEqualTo(3);
        assertThat(stats.partialCount()).isEqualTo(1);
        assertThat(stats.incompleteCount()).isEqualTo(1);
        assertThat(stats.recent24hCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("findProcessingDetail 은 정책이 없으면 NOT_FOUND 를 던진다")
    void findProcessingDetail_throwsNotFoundWhenPolicyMissing() {
        given(policyRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findProcessingDetail(999L))
                .isInstanceOf(YouthFitException.class)
                .extracting(e -> ((YouthFitException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("findProcessingDetail 은 단계 행을 ProcessingStep ordinal 순으로 재정렬한다")
    void findProcessingDetail_resortsStepsByPipelineOrder() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        lenient().when(policy.getTitle()).thenReturn("월세 지원");
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));

        // repository 는 알파벳 순 (ENRICHMENT, GUIDE, INGESTION, RAG_INDEXING, RULE) 로 내려준다.
        // 서비스는 이를 INGESTION → ENRICHMENT → GUIDE → RULE → RAG_INDEXING 순으로 재정렬해야 한다.
        List<PolicyProcessingStep> alphabeticalRows = List.of(
                stepMock(ProcessingStep.ENRICHMENT, ProcessingStatus.SUCCESS),
                stepMock(ProcessingStep.GUIDE, ProcessingStatus.SUCCESS),
                stepMock(ProcessingStep.INGESTION, ProcessingStatus.SUCCESS),
                stepMock(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                stepMock(ProcessingStep.RULE, ProcessingStatus.SUCCESS)
        );
        given(stepRepository.findLatestRowsByPolicyId(100L)).willReturn(alphabeticalRows);
        given(attachmentRepository.findByPolicyId(100L)).willReturn(List.of());
        given(documentRepository.findEmbeddedAttachmentIds(100L)).willReturn(Set.of());
        given(attachmentRepository.aggregateExtractionByPolicyIds(List.of(100L)))
                .willReturn(Map.of());

        PolicyProcessingDetailResult result = service.findProcessingDetail(100L);

        assertThat(result.steps()).extracting(StepDetailResult::step).containsExactly(
                ProcessingStep.INGESTION,
                ProcessingStep.ENRICHMENT,
                ProcessingStep.GUIDE,
                ProcessingStep.RULE,
                ProcessingStep.RAG_INDEXING
        );
        // RAG SUCCESS + 첨부 0 → COMPLETE
        assertThat(result.completeness()).isEqualTo(PolicyProcessingCompleteness.COMPLETE);
        // Phase D 이전 references 는 빈 리스트
        assertThat(result.references()).isEmpty();
    }

    @Test
    @DisplayName("findProcessingDetail 은 첨부 임베딩 적재 여부를 attachment id set 으로 판정한다")
    void findProcessingDetail_marksAttachmentEmbeddedWhenIdInSet() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(200L);
        lenient().when(policy.getTitle()).thenReturn("도전지원금");
        given(policyRepository.findById(200L)).willReturn(Optional.of(policy));

        given(stepRepository.findLatestRowsByPolicyId(200L)).willReturn(List.of());

        PolicyAttachment a1 = attachmentMock(11L, "guide.pdf", AttachmentStatus.EXTRACTED);
        PolicyAttachment a2 = attachmentMock(12L, "form.pdf", AttachmentStatus.FAILED);
        given(attachmentRepository.findByPolicyId(200L)).willReturn(List.of(a1, a2));
        given(documentRepository.findEmbeddedAttachmentIds(200L)).willReturn(Set.of(11L));
        given(attachmentRepository.aggregateExtractionByPolicyIds(List.of(200L)))
                .willReturn(Map.of(200L, new AttachmentExtractionCounts(2, 2, 1)));

        PolicyProcessingDetailResult result = service.findProcessingDetail(200L);

        assertThat(result.attachments()).hasSize(2);
        assertThat(result.attachments().get(0).attachmentId()).isEqualTo(11L);
        assertThat(result.attachments().get(0).embedded()).isTrue();
        assertThat(result.attachments().get(0).extractionStatus()).isEqualTo(AttachmentStatus.EXTRACTED);
        assertThat(result.attachments().get(1).attachmentId()).isEqualTo(12L);
        assertThat(result.attachments().get(1).embedded()).isFalse();
    }

    // ---- Task 10: retryStep ----

    @Test
    @DisplayName("retryStep — INGESTION 단계는 INVALID_INPUT 으로 거부한다")
    void retryStep_rejectsIngestion() {
        assertThatThrownBy(() -> service.retryStep(100L, ProcessingStep.INGESTION))
                .isInstanceOf(YouthFitException.class)
                .extracting(e -> ((YouthFitException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("retryStep — 정책이 없으면 NOT_FOUND 를 던진다")
    void retryStep_throwsNotFoundWhenPolicyMissing() {
        given(policyRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryStep(999L, ProcessingStep.RAG_INDEXING))
                .isInstanceOf(YouthFitException.class)
                .extracting(e -> ((YouthFitException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("retryStep — RAG_INDEXING 은 RagIndexingService 를 호출하고 SUCCESS 로 마감한다")
    void retryStep_invokesRagIndexingForRagStep() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        lenient().when(policy.getBody()).thenReturn("본문");
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(stepService.markStarted(100L, ProcessingStep.RAG_INDEXING)).willReturn(77L);
        given(ragIndexingService.indexPolicyDocument(any(IndexPolicyDocumentCommand.class)))
                .willReturn(new IndexingResult(100L, 3, true));

        ReprocessResult result = service.retryStep(100L, ProcessingStep.RAG_INDEXING);

        verify(stepService).markStarted(100L, ProcessingStep.RAG_INDEXING);
        verify(ragIndexingService).indexPolicyDocument(any(IndexPolicyDocumentCommand.class));
        verify(stepService).markFinished(eq(77L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
        assertThat(result.queued()).isTrue();
        assertThat(result.stepIds()).containsExactly(77L);
    }

    @Test
    @DisplayName("retryStep — GUIDE 는 GuideGenerationService 를 호출한다")
    void retryStep_invokesGuideGenerationForGuideStep() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        lenient().when(policy.getTitle()).thenReturn("월세 지원");
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(stepService.markStarted(100L, ProcessingStep.GUIDE)).willReturn(78L);

        ReprocessResult result = service.retryStep(100L, ProcessingStep.GUIDE);

        verify(guideGenerationService).generateGuide(any(GenerateGuideCommand.class));
        verify(stepService).markFinished(eq(78L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
        assertThat(result.queued()).isTrue();
    }

    @Test
    @DisplayName("retryStep — RULE 은 EligibilityRuleGenerationService 를 호출한다")
    void retryStep_invokesRuleGenerationForRuleStep() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(stepService.markStarted(100L, ProcessingStep.RULE)).willReturn(79L);

        ReprocessResult result = service.retryStep(100L, ProcessingStep.RULE);

        verify(eligibilityRuleGenerationService).generateRules(any(GenerateEligibilityRulesCommand.class));
        verify(stepService).markFinished(eq(79L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
        assertThat(result.queued()).isTrue();
    }

    @Test
    @DisplayName("retryStep — ENRICHMENT 는 MVP 미연결로 SKIPPED 처리한다")
    void retryStep_marksEnrichmentSkippedAsMvpStub() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(stepService.markStarted(100L, ProcessingStep.ENRICHMENT)).willReturn(80L);

        ReprocessResult result = service.retryStep(100L, ProcessingStep.ENRICHMENT);

        verify(stepService).markFinished(eq(80L), eq(ProcessingStatus.SKIPPED), anyString(), isNull());
        assertThat(result.queued()).isTrue();
        assertThat(result.message()).contains("SKIPPED");
    }

    // ---- Task 11: reindexAttachment ----

    @Test
    @DisplayName("reindexAttachment — 첨부가 없으면 NOT_FOUND")
    void reindexAttachment_throws404WhenAttachmentMissing() {
        given(attachmentRepository.findById(401L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reindexAttachment(100L, 401L))
                .isInstanceOf(YouthFitException.class)
                .extracting(e -> ((YouthFitException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("reindexAttachment — 첨부가 다른 정책 소속이면 NOT_FOUND")
    void reindexAttachment_throws404WhenNotBelongToPolicy() {
        Policy otherPolicy = mock(Policy.class);
        lenient().when(otherPolicy.getId()).thenReturn(999L);
        PolicyAttachment attachment = attachmentMockWithPolicy(401L, "안내문.pdf",
                AttachmentStatus.EXTRACTED, otherPolicy);
        given(attachmentRepository.findById(401L)).willReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.reindexAttachment(100L, 401L))
                .isInstanceOf(YouthFitException.class)
                .extracting(e -> ((YouthFitException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("reindexAttachment — 정책 단위 reindex 를 호출하고 SUCCESS 로 마감한다")
    void reindexAttachment_invokesAttachmentReindex() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        PolicyAttachment attachment = attachmentMockWithPolicy(401L, "안내문.pdf",
                AttachmentStatus.EXTRACTED, policy);
        given(attachmentRepository.findById(401L)).willReturn(Optional.of(attachment));
        given(stepService.markStarted(100L, ProcessingStep.RAG_INDEXING)).willReturn(91L);

        ReprocessResult result = service.reindexAttachment(100L, 401L);

        verify(attachmentReindexService).reindex(100L);
        verify(stepService).markFinished(eq(91L), eq(ProcessingStatus.SUCCESS), anyString(), isNull());
        assertThat(result.queued()).isTrue();
        assertThat(result.stepIds()).containsExactly(91L);
    }

    @Test
    @DisplayName("retryStep — RAG 가 실패하면 FAILED 로 마감하고 예외를 다시 던진다")
    void retryStep_marksFailedAndRethrowsOnRagException() {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(100L);
        lenient().when(policy.getBody()).thenReturn("본문");
        given(policyRepository.findById(100L)).willReturn(Optional.of(policy));
        given(stepService.markStarted(100L, ProcessingStep.RAG_INDEXING)).willReturn(81L);
        given(ragIndexingService.indexPolicyDocument(any(IndexPolicyDocumentCommand.class)))
                .willThrow(new RuntimeException("embedding down"));

        assertThatThrownBy(() -> service.retryStep(100L, ProcessingStep.RAG_INDEXING))
                .isInstanceOf(RuntimeException.class);

        verify(stepService).markFinished(eq(81L), eq(ProcessingStatus.FAILED), anyString(), isNull());
    }

    private PolicyProcessingStep stepMock(ProcessingStep step, ProcessingStatus status) {
        PolicyProcessingStep s = mock(PolicyProcessingStep.class);
        Instant now = Instant.now();
        lenient().when(s.getStep()).thenReturn(step);
        lenient().when(s.getStatus()).thenReturn(status);
        lenient().when(s.getAttempt()).thenReturn(1);
        lenient().when(s.getStartedAt()).thenReturn(now.minusSeconds(10));
        lenient().when(s.getFinishedAt()).thenReturn(now);
        return s;
    }

    private PolicyAttachment attachmentMock(Long id, String name, AttachmentStatus status) {
        PolicyAttachment a = mock(PolicyAttachment.class);
        lenient().when(a.getId()).thenReturn(id);
        lenient().when(a.getName()).thenReturn(name);
        lenient().when(a.getExtractionStatus()).thenReturn(status);
        return a;
    }

    private PolicyAttachment attachmentMockWithPolicy(Long id, String name, AttachmentStatus status, Policy policy) {
        PolicyAttachment a = attachmentMock(id, name, status);
        lenient().when(a.getPolicy()).thenReturn(policy);
        return a;
    }

    private Policy policyMock(Long id, LocalDateTime createdAt) {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(id);
        lenient().when(policy.getCreatedAt()).thenReturn(createdAt);
        return policy;
    }

    private void givenSinglePolicyPage(Long id, String title, String regionCode) {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(id);
        lenient().when(policy.getTitle()).thenReturn(title);
        lenient().when(policy.getRegionCode()).thenReturn(regionCode);
        Page<Policy> page = new PageImpl<>(List.of(policy));
        given(policyRepository.findForAdminProcessing(
                isNull(), isNull(), any(Sort.class), any(Pageable.class)))
                .willReturn(page);
    }
}
