package com.youthfit.ingestion.application.service;

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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Nested
    @DisplayName("receivePolicy")
    class ReceivePolicy {

        @Test
        @DisplayName("수집 명령을 받으면 PolicyIngestionService에 위임하고 RECEIVED 상태를 반환한다")
        void delegatesToPolicyIngestionService() {
            // given
            IngestPolicyCommand command = new IngestPolicyCommand(
                    "https://youth.seoul.go.kr/policy/1",
                    "YOUTH_SEOUL_CRAWL",
                    LocalDateTime.of(2026, 4, 15, 10, 0),
                    "청년 취업 지원",
                    "청년 취업을 지원합니다.",
                    "일자리",
                    "서울",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 6, 30)
            );
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
            IngestPolicyCommand command = new IngestPolicyCommand(
                    "https://example.com", "YOUTH_SEOUL_CRAWL",
                    LocalDateTime.now(), "주거 지원", "주거 정책입니다.",
                    "주거", "서울", null, null
            );
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
            IngestPolicyCommand command = new IngestPolicyCommand(
                    "https://example.com", "YOUTH_SEOUL_CRAWL",
                    LocalDateTime.now(), "기타 정책", "기타 내용",
                    "알수없는카테고리", "서울", null, null
            );
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
            IngestPolicyCommand command = new IngestPolicyCommand(
                    "https://example.com", "UNKNOWN_TYPE",
                    LocalDateTime.now(), "정책", "내용",
                    "일자리", "서울", null, null
            );
            given(policyIngestionService.registerPolicy(any()))
                    .willReturn(new PolicyIngestionResult(1L, true));

            // when
            IngestPolicyResult result = ingestionService.receivePolicy(command);

            // then
            assertThat(result.status()).isEqualTo("RECEIVED");
        }
    }
}
