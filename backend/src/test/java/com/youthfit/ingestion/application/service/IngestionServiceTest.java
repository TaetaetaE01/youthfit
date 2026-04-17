package com.youthfit.ingestion.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.application.service.PolicyIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Nested
    @DisplayName("receivePolicy")
    class ReceivePolicy {

        @Test
        @DisplayName("수집 명령을 받으면 PolicyIngestionService에 위임하고 RECEIVED 상태를 반환한다")
        void delegatesToPolicyIngestionService() {
            // given
            IngestPolicyCommand command = command("YOUTH_SEOUL_CRAWL", "일자리");
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(new PolicyIngestionResult(1L, true));

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
                    .willReturn(new PolicyIngestionResult(1L, true));

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
                    .willReturn(new PolicyIngestionResult(1L, true));

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
                    .willReturn(new PolicyIngestionResult(1L, true));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("RECEIVED");
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
                    "국토교통부",
                    "1599-0001",
                    List.of("청년"),
                    List.of("주거"),
                    List.of("저소득"),
                    List.of()
            );
        }
    }
}
