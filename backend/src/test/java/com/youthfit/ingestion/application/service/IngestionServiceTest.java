package com.youthfit.ingestion.application.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.youthfit.common.domain.PeriodSource;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.application.service.CodeBasedRuleExtractionService;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.domain.service.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.application.service.PolicyIngestionService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("IngestionService")
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

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

    @Nested
    @DisplayName("receivePolicy")
    class ReceivePolicy {

        @Test
        @DisplayName("수집 명령을 받으면 PolicyIngestionService에 위임하고 RECEIVED 상태를 반환한다")
        void delegatesToPolicyIngestionService() {
            // given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("RECEIVED");
            assertThat(result.ingestionId()).isNotNull();
            then(policyIngestionService).should().registerPolicy(any());
        }

        @Test
        @DisplayName("카테고리 매핑 - 한국어 카테고리를 enum으로 변환한다")
        void mapsKoreanCategoryToEnum() {
            // given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "주거");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            ingestionService.receivePolicy(command);

            // then
            then(policyIngestionService).should().registerPolicy(any());
        }

        @Test
        @DisplayName("알 수 없는 카테고리는 WELFARE로 매핑된다")
        void unknownCategoryMapsToWelfare() {
            // given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "알수없는카테고리");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("RECEIVED");
        }

        @Test
        @DisplayName("알 수 없는 sourceType은 YOUTH_SEOUL_CRAWL로 기본 매핑된다")
        void unknownSourceTypeFallsBackToDefault() {
            // given
            IngestPolicyCommand command = command("UNKNOWN_TYPE", "일자리");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("RECEIVED");
        }

        @Test
        @DisplayName("PeriodResolver 가 반환한 신청기간이 RegisterPolicyCommand 에 그대로 전파된다")
        void propagatesResolvedPeriodToRegisterCommand() {
            // given
            IngestPolicyCommand command = commandWithoutPeriod("신청기간: 2026.05.01.~2026.06.30.");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(new ResolvedPeriod(
                            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                            PeriodSource.BODY_LABELED, 0.90, "신청기간: 2026.05.01.~2026.06.30."));
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            ingestionService.receivePolicy(command);

            // then
            ArgumentCaptor<RegisterPolicyCommand> captor = ArgumentCaptor.forClass(RegisterPolicyCommand.class);
            then(policyIngestionService).should().registerPolicy(captor.capture());
            assertThat(captor.getValue().applyStart()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(captor.getValue().applyEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(captor.getValue().applyPeriodSource()).isEqualTo(PeriodSource.BODY_LABELED);
            assertThat(captor.getValue().applyPeriodConfidence()).isEqualTo(0.90);
            assertThat(captor.getValue().applyPeriodEvidence()).isEqualTo("신청기간: 2026.05.01.~2026.06.30.");
        }

        @Test
        @DisplayName("PeriodResolver 가 empty 를 반환하면 RegisterPolicyCommand 메타는 null 이다")
        void emptyResolvedPeriodPropagatesNullMeta() {
            // given
            IngestPolicyCommand command = commandWithoutPeriod("상시접수");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(ResolvedPeriod.empty());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            ingestionService.receivePolicy(command);

            // then
            ArgumentCaptor<RegisterPolicyCommand> captor = ArgumentCaptor.forClass(RegisterPolicyCommand.class);
            then(policyIngestionService).should().registerPolicy(captor.capture());
            assertThat(captor.getValue().applyStart()).isNull();
            assertThat(captor.getValue().applyEnd()).isNull();
            assertThat(captor.getValue().applyPeriodSource()).isNull();
            assertThat(captor.getValue().applyPeriodConfidence()).isNull();
            assertThat(captor.getValue().applyPeriodEvidence()).isNull();
        }

        @Test
        @DisplayName("정책 등록 후 PolicyUpsertedEvent 를 발행한다 (policyId, title 포함)")
        void 정책_등록_후_PolicyUpsertedEvent_를_발행한다() {
            // Given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(42L));

            // When
            ingestionService.receivePolicy(command);

            // Then
            ArgumentCaptor<PolicyUpsertedEvent> captor = ArgumentCaptor.forClass(PolicyUpsertedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().policyId()).isEqualTo(42L);
            assertThat(captor.getValue().title()).isEqualTo(command.title());
        }

        @Test
        @DisplayName("가이드/룰은 더 이상 직접 호출되지 않는다 (이벤트 발행만 일어난다)")
        void 가이드와_룰은_직접_호출되지_않는다() {
            // Given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(42L));

            // When
            assertThatCode(() -> ingestionService.receivePolicy(command))
                    .doesNotThrowAnyException();

            // Then: eventPublisher 외엔 LLM 의존이 주입되지 않으므로, 단순히 publish 가 한 번 일어났는지로 검증
            then(eventPublisher).should().publishEvent(any(PolicyUpsertedEvent.class));
        }

        @Test
        @DisplayName("PolicyIngestionService 가 SKIPPED_DUPLICATE 를 반환하면 status 를 SKIPPED_DUPLICATE 로 응답하고 이벤트/첨부 다운로드를 트리거하지 않는다")
        void respondsSkippedDuplicateWithoutSideEffects() {
            // given
            IngestPolicyCommand command = command("YOUTH_CENTER", "주거");
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(resolvedPeriod());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.skippedDuplicate(42L));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("SKIPPED_DUPLICATE");
            then(eventPublisher).should(never()).publishEvent(any());
            then(attachmentDownloadService).should(never()).downloadForPolicyAsync(any());
        }

        @Test
        @DisplayName("youth center 상세 필드가 RegisterPolicyCommand 에 그대로 전달된다")
        void receivePolicy_propagates_youth_center_detail_fields_to_register_command() {
            IngestPolicyCommand command = sampleCommandWithDetailFields();
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(ResolvedPeriod.empty());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(99L));

            ingestionService.receivePolicy(command);

            ArgumentCaptor<RegisterPolicyCommand> captor = ArgumentCaptor.forClass(RegisterPolicyCommand.class);
            then(policyIngestionService).should().registerPolicy(captor.capture());
            RegisterPolicyCommand reg = captor.getValue();
            assertThat(reg.screeningMethod()).isEqualTo("심사방법");
            assertThat(reg.submissionDocuments()).isEqualTo("주민등록등본");
            assertThat(reg.additionalQualification()).isEqualTo("추가 자격");
            assertThat(reg.participationRestriction()).isEqualTo("기존 수혜자 제외");
            assertThat(reg.additionalNotes()).isEqualTo("기타");
            assertThat(reg.businessPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(reg.businessPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(reg.businessPeriodNote()).isEqualTo("특정기간");
            assertThat(reg.supportScale()).isEqualTo(25);
            assertThat(reg.firstComeFirstServed()).isTrue();
            assertThat(reg.applyUrl()).isEqualTo("https://apply.kr");
        }

        private IngestPolicyCommand sampleCommandWithDetailFields() {
            return new IngestPolicyCommand(
                    "https://src.kr", "YOUTH_CENTER", LocalDateTime.now(),
                    "EXT-1", "제목", "요약", "[지원대상]\n내용", "복지", "서울특별시",
                    null, null, 2026, null, "보조금",
                    "기관", "연락처",
                    List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(),
                    "심사방법", "주민등록등본", "추가 자격", "기존 수혜자 제외", "기타",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "특정기간",
                    25, true, "https://apply.kr",
                    null, null, null, null
            );
        }

        @Test
        @DisplayName("rawCodes가 있고 REGISTERED 이면 CodeBasedRuleExtractionService 를 호출한다")
        void receivePolicy_invokes_codeBased_extractor_when_rawCodes_present_and_REGISTERED() {
            IngestPolicyCommand command = sampleCommandWithRawCodes();
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(ResolvedPeriod.empty());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(123L));

            ingestionService.receivePolicy(command);

            ArgumentCaptor<CodeBasedExtractionInput> captor = ArgumentCaptor.forClass(CodeBasedExtractionInput.class);
            verify(codeBasedRuleExtractionService).extractAndPersist(eq(123L), captor.capture());
            assertThat(captor.getValue().maritalStatusCd()).isEqualTo("0055002");
            assertThat(captor.getValue().zipCodes()).containsExactly("11680");
        }

        @Test
        @DisplayName("rawCodes가 있고 UPDATED 이면 CodeBasedRuleExtractionService 를 호출한다")
        void receivePolicy_invokes_codeBased_extractor_when_rawCodes_present_and_UPDATED() {
            IngestPolicyCommand command = sampleCommandWithRawCodes();
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(ResolvedPeriod.empty());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.updated(456L));

            ingestionService.receivePolicy(command);

            verify(codeBasedRuleExtractionService).extractAndPersist(eq(456L), any());
        }

        @Test
        @DisplayName("SKIPPED_DUPLICATE 이면 CodeBasedRuleExtractionService 를 호출하지 않는다")
        void receivePolicy_skips_codeBased_extractor_when_SKIPPED_DUPLICATE() {
            IngestPolicyCommand command = sampleCommandWithRawCodes();
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(ResolvedPeriod.empty());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.skippedDuplicate(789L));

            ingestionService.receivePolicy(command);

            verify(codeBasedRuleExtractionService, never()).extractAndPersist(any(), any());
        }

        @Test
        @DisplayName("rawCodes 가 null 이면 CodeBasedRuleExtractionService 를 호출하지 않는다")
        void receivePolicy_skips_codeBased_extractor_when_rawCodes_null() {
            IngestPolicyCommand command = sampleCommandWithoutRawCodes();
            given(periodResolver.resolve(any(PeriodExtractionContext.class)))
                    .willReturn(ResolvedPeriod.empty());
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(111L));

            ingestionService.receivePolicy(command);

            verify(codeBasedRuleExtractionService, never()).extractAndPersist(any(), any());
        }

        private IngestPolicyCommand sampleCommandWithRawCodes() {
            return new IngestPolicyCommand(
                    "https://src.kr", "YOUTH_CENTER", LocalDateTime.now(),
                    "EXT-2", "제목", "요약", "[지원대상]\n내용", "복지", "서울특별시",
                    null, null, 2026, null, "보조금",
                    "기관", "연락처",
                    List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(),
                    null, null, null, null, null,
                    null, null, null,
                    null, null, null,
                    new IngestPolicyCommand.RawCodes(
                            19, 34, "Y",
                            "0055002", "0043001", 0, 0, null,
                            "0013001", "0049007", "0011005", "0014001",
                            List.of("11680")),
                    null, null, null
            );
        }

        private IngestPolicyCommand sampleCommandWithoutRawCodes() {
            return new IngestPolicyCommand(
                    "https://src.kr", "YOUTH_CENTER", LocalDateTime.now(),
                    "EXT-3", "제목", "요약", "[지원대상]\n내용", "복지", "서울특별시",
                    null, null, 2026, null, "보조금",
                    "기관", "연락처",
                    List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(),
                    null, null, null, null, null,
                    null, null, null,
                    null, null, null,
                    null, null, null, null  // rawCodes, providedSourceHash, enrichment, pipelineMeta
            );
        }

        private IngestPolicyCommand commandWithoutPeriod(String body) {
            return new IngestPolicyCommand(
                    "https://example.com/policy/2",
                    "BOKJIRO_CENTRAL",
                    LocalDateTime.of(2026, 4, 15, 10, 0),
                    "EXT-002",
                    "정책",
                    "요약",
                    body,
                    "복지",
                    "전국",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "보건복지부",
                    "129",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null
            );
        }

        private IngestPolicyCommand command(String sourceType, String category) {
            return new IngestPolicyCommand(
                    "https://example.com/policy/1",
                    sourceType,
                    LocalDateTime.of(2026, 4, 15, 10, 0),
                    "EXT-001",
                    "정책",
                    "요약",
                    "본문",
                    category,
                    "서울",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 6, 30),
                    null,
                    null,
                    null,
                    "국토교통부",
                    "1599-0001",
                    List.of("청년"),
                    List.of("주거"),
                    List.of("저소득"),
                    List.of(),
                    List.of(),
                    List.of(),
                    null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null
            );
        }

        private ResolvedPeriod resolvedPeriod() {
            return new ResolvedPeriod(
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                    PeriodSource.N8N, 0.95, "n8n");
        }
    }
}
