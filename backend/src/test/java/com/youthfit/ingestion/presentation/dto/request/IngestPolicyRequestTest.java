package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.policy.domain.model.PolicyEnrichment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IngestPolicyRequest")
class IngestPolicyRequestTest {

    @Nested
    @DisplayName("EnrichmentPayload -> PolicyEnrichment 매핑")
    class EnrichmentPayloadMapping {

        @Test
        @DisplayName("enrichment_payload_의_cleanedText_가_PolicyEnrichment_로_매핑된다")
        void enrichmentPayload_cleanedText_maps_to_PolicyEnrichment() {
            // given
            String cleanedText = "외부 페이지 본문";
            IngestPolicyRequest request = buildIngestPolicyRequest(cleanedText);

            // when
            var command = request.toCommand();

            // then
            assertThat(command.enrichment()).isNotNull();
            assertThat(command.enrichment().cleanedText()).isEqualTo(cleanedText);
        }

        @Test
        @DisplayName("cleanedText_가_null_이면_PolicyEnrichment_cleanedText_도_null")
        void enrichmentPayload_null_cleanedText_maps_to_null() {
            // given
            IngestPolicyRequest request = buildIngestPolicyRequest(null);

            // when
            var command = request.toCommand();

            // then
            assertThat(command.enrichment()).isNotNull();
            assertThat(command.enrichment().cleanedText()).isNull();
        }

        // ── Helper Methods ──

        private IngestPolicyRequest buildIngestPolicyRequest(String cleanedText) {
            IngestPolicyRequest.SourceInfo sourceInfo = new IngestPolicyRequest.SourceInfo(
                    "https://example.com/policy",
                    "EXAMPLE_SOURCE",
                    LocalDateTime.of(2026, 5, 15, 10, 0)
            );

            IngestPolicyRequest.EnrichmentSectionsPayload sections =
                    new IngestPolicyRequest.EnrichmentSectionsPayload(
                            "지원 대상",
                            "지원 내용",
                            "신청 방법",
                            "필수 서류",
                            "마감 공지",
                            "정책 개요",
                            "적격 기준",
                            "운영 기관",
                            "연락처"
                    );

            IngestPolicyRequest.EnrichmentPayload enrichmentPayload =
                    new IngestPolicyRequest.EnrichmentPayload(
                            "https://example.com/policy/enriched",
                            LocalDateTime.of(2026, 5, 15, 10, 0),
                            "TestExtractor",
                            0.95,
                            "OK",
                            sections,
                            List.of(),
                            cleanedText
                    );

            IngestPolicyRequest.RawData rawData = new IngestPolicyRequest.RawData(
                    "EXT-001",
                    "정책 제목",
                    "정책 요약",
                    "정책 본문",
                    "일자리",
                    "서울",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 6, 30),
                    2026,
                    "연 1회",
                    "현금",
                    "국토교통부",
                    "1599-0001",
                    List.of("청년"),
                    List.of("주거"),
                    List.of("저소득"),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    null,
                    100,
                    false,
                    "https://example.com/apply",
                    null,
                    "source-hash-123",
                    enrichmentPayload
            );

            return new IngestPolicyRequest(sourceInfo, rawData, null);
        }
    }
}
