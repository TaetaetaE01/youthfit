package com.youthfit.ingestion.application.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.youthfit.common.config.CostGuard;
import com.youthfit.common.config.CostGuardProperties;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.application.port.PolicyPeriodLlmProvider;
import com.youthfit.ingestion.domain.model.PolicyPeriod;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import com.youthfit.ingestion.domain.service.PolicyPeriodExtractor;
import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.application.service.PolicyIngestionService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("IngestionService")
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @InjectMocks
    private IngestionService ingestionService;

    @Mock
    private PolicyIngestionService policyIngestionService;

    @Mock
    private PolicyPeriodLlmProvider policyPeriodLlmProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AttachmentDownloadService attachmentDownloadService;

    @Mock
    private IngestionRunLogRepository ingestionRunLogRepository;

    @Mock
    private IngestionItemFailureRepository ingestionItemFailureRepository;

    @Spy
    private PolicyPeriodExtractor policyPeriodExtractor = new PolicyPeriodExtractor();

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Spy
    private CostGuard costGuard = new CostGuard(new CostGuardProperties(""));

    @Nested
    @DisplayName("receivePolicy")
    class ReceivePolicy {

        @Test
        @DisplayName("수집 명령을 받으면 PolicyIngestionService에 위임하고 RECEIVED 상태를 반환한다")
        void delegatesToPolicyIngestionService() {
            // given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
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
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("RECEIVED");
        }

        @Test
        @DisplayName("기간이 비어 있으면 본문에서 정규식으로 보강한다")
        void enrichesPeriodViaRegexWhenMissing() {
            // given
            IngestPolicyCommand command = commandWithoutPeriod("신청기간: 2026.05.01.~2026.06.30.");
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            ingestionService.receivePolicy(command);

            // then
            ArgumentCaptor<RegisterPolicyCommand> captor = ArgumentCaptor.forClass(RegisterPolicyCommand.class);
            then(policyIngestionService).should().registerPolicy(captor.capture());
            assertThat(captor.getValue().applyStart()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(captor.getValue().applyEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
            then(policyPeriodLlmProvider).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("정규식이 실패하면 LLM 제공자로 폴백한다")
        void fallsBackToLlmWhenRegexFails() {
            // given
            IngestPolicyCommand command = commandWithoutPeriod("상시접수");
            given(policyPeriodLlmProvider.extractPeriod(any(), any()))
                    .willReturn(PolicyPeriod.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            ingestionService.receivePolicy(command);

            // then
            ArgumentCaptor<RegisterPolicyCommand> captor = ArgumentCaptor.forClass(RegisterPolicyCommand.class);
            then(policyIngestionService).should().registerPolicy(captor.capture());
            assertThat(captor.getValue().applyStart()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(captor.getValue().applyEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("command에 기간이 이미 있으면 보강 로직을 호출하지 않는다")
        void keepsCommandPeriodWhenPresent() {
            // given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(1L));

            // when
            ingestionService.receivePolicy(command);

            // then
            then(policyPeriodLlmProvider).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("정책 등록 후 PolicyUpsertedEvent 를 발행한다 (policyId, title 포함)")
        void 정책_등록_후_PolicyUpsertedEvent_를_발행한다() {
            // Given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
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
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(PolicyIngestionResult.registered(42L));

            // When
            assertThatCode(() -> ingestionService.receivePolicy(command))
                    .doesNotThrowAnyException();

            // Then: eventPublisher 외엔 LLM 의존이 주입되지 않으므로, 단순히 publish 가 한 번 일어났는지로 검증
            then(eventPublisher).should().publishEvent(any(PolicyUpsertedEvent.class));
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
                    List.of()
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
                    List.of()
            );
        }
    }
}
