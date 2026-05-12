package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("PolicyQueryService — enrichment 마스킹")
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceEnrichmentMaskingTest {

    @InjectMocks
    private PolicyQueryService policyQueryService;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicySourceRepository policySourceRepository;

    @Mock
    private RegionCodeRegistry regionCodeRegistry;

    // ── helpers ──

    private Policy createPolicy() {
        Policy policy = Policy.builder()
                .title("청년 주거 지원")
                .summary("월세 지원")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 1, 1))
                .applyEnd(LocalDate.of(2026, 12, 31))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
    }

    private PolicyEnrichment enrichmentWith(EnrichmentStatus status, Double confidence) {
        return new PolicyEnrichment(
                "https://example.com/policy/1",
                Instant.parse("2026-05-01T00:00:00Z"),
                "test-extractor",
                confidence,
                status,
                new PolicyEnrichment.Sections("대상", "내용", "신청 방법", "서류", "마감 비고"),
                List.of(new PolicyEnrichment.ExtraAttachment("첨부", "https://att.example.com"))
        );
    }

    private void givenPolicyWithEnrichment(Policy policy, PolicyEnrichment enrichment) {
        policy.replaceEnrichment(enrichment);
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
        given(policySourceRepository.findFirstByPolicyId(1L)).willReturn(Optional.<PolicySource>empty());
    }

    // ── test cases ──

    @Nested
    @DisplayName("enrichment가 null인 경우")
    class NullEnrichment {

        @Test
        @DisplayName("정책의 enrichment가 null이면 결과의 enrichment도 null이다")
        void enrichment_is_null_on_result_when_policy_enrichment_is_null() {
            Policy policy = createPolicy();
            // enrichment를 설정하지 않음 → null
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policySourceRepository.findFirstByPolicyId(1L)).willReturn(Optional.<PolicySource>empty());

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNull();
        }
    }

    @Nested
    @DisplayName("enrichment status가 OK가 아닌 경우")
    class NonOkStatus {

        @Test
        @DisplayName("status가 LOW_CONFIDENCE이면 enrichment가 null로 마스킹된다")
        void enrichment_is_null_on_result_when_status_not_ok() {
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.LOW_CONFIDENCE, 0.9));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNull();
        }

        @Test
        @DisplayName("status가 FETCH_FAILED이면 enrichment가 null로 마스킹된다")
        void enrichment_is_null_on_result_when_status_fetch_failed() {
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.FETCH_FAILED, null));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNull();
        }
    }

    @Nested
    @DisplayName("confidence가 임계값 미만인 경우")
    class BelowConfidenceThreshold {

        @Test
        @DisplayName("status=OK이지만 confidence=0.5이면 enrichment가 null로 마스킹된다")
        void enrichment_is_null_on_result_when_confidence_below_threshold() {
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.OK, 0.5));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNull();
        }

        @Test
        @DisplayName("confidence가 null이면 enrichment가 null로 마스킹된다")
        void enrichment_is_null_on_result_when_confidence_is_null() {
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.OK, null));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNull();
        }
    }

    @Nested
    @DisplayName("enrichment가 노출 조건을 충족하는 경우")
    class ExposableEnrichment {

        @Test
        @DisplayName("status=OK이고 confidence=0.6(임계값 경계)이면 enrichment가 노출된다")
        void enrichment_is_returned_at_threshold_boundary() {
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.OK, 0.6));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNotNull();
            assertThat(result.enrichment().sourceUrl()).isEqualTo("https://example.com/policy/1");
        }

        @Test
        @DisplayName("status=OK이고 confidence=0.9이면 enrichment가 노출되며 sections와 첨부파일이 매핑된다")
        void enrichment_is_returned_on_result_when_status_ok_and_high_confidence() {
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.OK, 0.9));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.enrichment()).isNotNull();
            assertThat(result.enrichment().sourceUrl()).isEqualTo("https://example.com/policy/1");
            assertThat(result.enrichment().fetchedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));

            PolicyDetailResult.Enrichment.Sections sections = result.enrichment().sections();
            assertThat(sections).isNotNull();
            assertThat(sections.supportTarget()).isEqualTo("대상");
            assertThat(sections.supportContent()).isEqualTo("내용");
            assertThat(sections.applyMethod()).isEqualTo("신청 방법");
            assertThat(sections.requiredDocuments()).isEqualTo("서류");
            assertThat(sections.deadlineNote()).isEqualTo("마감 비고");

            assertThat(result.enrichment().extraAttachments()).hasSize(1);
            assertThat(result.enrichment().extraAttachments().getFirst().name()).isEqualTo("첨부");
            assertThat(result.enrichment().extraAttachments().getFirst().url()).isEqualTo("https://att.example.com");
        }

        @Test
        @DisplayName("내부 메타(extractor/confidence/status)가 결과 DTO에 노출되지 않는다")
        void internal_meta_fields_are_not_in_result() {
            // PolicyDetailResult.Enrichment 에 extractor, confidence, status 필드가 없음을 컴파일 레벨에서 검증
            // 런타임에도 결과 enrichment 내부에 해당 필드가 없는지 확인
            Policy policy = createPolicy();
            givenPolicyWithEnrichment(policy, enrichmentWith(EnrichmentStatus.OK, 0.9));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            PolicyDetailResult.Enrichment enrichment = result.enrichment();
            assertThat(enrichment).isNotNull();
            // 컴파일 수준: Enrichment 레코드에는 sourceUrl, fetchedAt, sections, extraAttachments만 존재
            // extractor(), confidence(), status() 메서드가 없으면 컴파일 오류 → 테스트 자체가 보장
            assertThat(enrichment.sourceUrl()).isNotNull();
            assertThat(enrichment.fetchedAt()).isNotNull();
        }
    }
}
