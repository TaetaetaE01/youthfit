package com.youthfit.ingestion.application.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.youthfit.eligibility.application.service.CodeBasedRuleExtractionService;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.ingestion.domain.service.port.PeriodExtractionContext;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.application.service.PolicyIngestionService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("IngestionService PolicyProcessingStep 기록")
@ExtendWith(MockitoExtension.class)
class IngestionServicePolicyProcessingStepTest {

    @InjectMocks
    private IngestionService ingestionService;

    @Mock
    private PolicyIngestionService policyIngestionService;

    @Mock
    private PeriodResolver periodResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AttachmentDownloadService attachmentDownloadService;

    @Mock
    private IngestionRunLogRepository ingestionRunLogRepository;

    @Mock
    private IngestionItemFailureRepository ingestionItemFailureRepository;

    @Mock
    private CodeBasedRuleExtractionService codeBasedRuleExtractionService;

    @Mock
    private PolicyProcessingStepService stepService;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("INGESTION step 을 markStarted, markFinished(SUCCESS) 로 기록한다")
    void receivePolicy_records_INGESTION_step_on_success() {
        // given
        IngestPolicyCommand command = TestFixtures.minimalCommand();
        given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                .willReturn(ResolvedPeriod.empty());
        given(policyIngestionService.registerPolicy(any()))
                .willReturn(PolicyIngestionResult.registered(42L));
        given(stepService.markStarted(42L, ProcessingStep.INGESTION)).willReturn(100L);

        // when
        ingestionService.receivePolicy(command);

        // then
        verify(stepService).markStarted(42L, ProcessingStep.INGESTION);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SUCCESS), isNull(), isNull());
    }

    @Test
    @DisplayName("dedup 으로 SKIPPED_DUPLICATE 이면 INGESTION step 을 SKIPPED 로 기록한다")
    void receivePolicy_records_INGESTION_step_as_SKIPPED_when_duplicate() {
        // given
        IngestPolicyCommand command = TestFixtures.minimalCommand();
        given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                .willReturn(ResolvedPeriod.empty());
        given(policyIngestionService.registerPolicy(any()))
                .willReturn(PolicyIngestionResult.skippedDuplicate(42L));
        given(stepService.markStarted(42L, ProcessingStep.INGESTION)).willReturn(100L);

        // when
        ingestionService.receivePolicy(command);

        // then
        verify(stepService).markStarted(42L, ProcessingStep.INGESTION);
        verify(stepService).markFinished(eq(100L), eq(ProcessingStatus.SKIPPED), eq("DUPLICATE"), isNull());
    }

    @Test
    @DisplayName("enrichment payload status=OK 이면 ENRICHMENT step 을 SUCCESS 로 기록한다")
    void receivePolicy_records_ENRICHMENT_step_when_enrichment_status_OK() {
        // given
        IngestPolicyCommand command = TestFixtures.commandWithEnrichmentStatus(EnrichmentStatus.OK);
        given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                .willReturn(ResolvedPeriod.empty());
        given(policyIngestionService.registerPolicy(any()))
                .willReturn(PolicyIngestionResult.registered(42L));
        given(stepService.markStarted(42L, ProcessingStep.INGESTION)).willReturn(100L);
        given(stepService.markStarted(42L, ProcessingStep.ENRICHMENT)).willReturn(101L);

        // when
        ingestionService.receivePolicy(command);

        // then
        verify(stepService).markStarted(42L, ProcessingStep.ENRICHMENT);
        verify(stepService).markFinished(eq(101L), eq(ProcessingStatus.SUCCESS), eq("OK"), any());
    }

    @Test
    @DisplayName("enrichment payload status=NO_LINK 이면 ENRICHMENT step 을 SKIPPED 로 그대로 복사한다")
    void receivePolicy_copies_NO_LINK_to_SKIPPED() {
        // given
        IngestPolicyCommand command = TestFixtures.commandWithEnrichmentStatus(EnrichmentStatus.NO_LINK);
        given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                .willReturn(ResolvedPeriod.empty());
        given(policyIngestionService.registerPolicy(any()))
                .willReturn(PolicyIngestionResult.registered(42L));
        given(stepService.markStarted(42L, ProcessingStep.INGESTION)).willReturn(100L);
        given(stepService.markStarted(42L, ProcessingStep.ENRICHMENT)).willReturn(101L);

        // when
        ingestionService.receivePolicy(command);

        // then
        verify(stepService).markStarted(42L, ProcessingStep.ENRICHMENT);
        verify(stepService).markFinished(eq(101L), eq(ProcessingStatus.SKIPPED), eq("NO_LINK"), any());
    }

    @Test
    @DisplayName("enrichment payload 가 없으면 ENRICHMENT step 을 기록하지 않는다")
    void receivePolicy_skips_ENRICHMENT_step_when_payload_null() {
        // given
        IngestPolicyCommand command = TestFixtures.minimalCommand();
        given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                .willReturn(ResolvedPeriod.empty());
        given(policyIngestionService.registerPolicy(any()))
                .willReturn(PolicyIngestionResult.registered(42L));
        given(stepService.markStarted(42L, ProcessingStep.INGESTION)).willReturn(100L);

        // when
        ingestionService.receivePolicy(command);

        // then
        verify(stepService, never()).markStarted(anyLong(), eq(ProcessingStep.ENRICHMENT));
    }
}
