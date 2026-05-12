package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyIngestionServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicySourceRepository policySourceRepository;
    @Mock private PolicyAttachmentApplicationService policyAttachmentApplicationService;

    @InjectMocks private PolicyIngestionService policyIngestionService;

    @Nested
    @DisplayName("YOUTH_CENTER 우선 스킵 분기")
    class YouthCenterSkipBranch {

        @Test
        @DisplayName("동일 정규화 제목의 BOKJIRO 정책이 존재하면 SKIPPED_DUPLICATE 를 반환하고 저장하지 않는다")
        void skipsWhenBokjiroExists() {
            RegisterPolicyCommand command = command(SourceType.YOUTH_CENTER, "WLF99999",
                    "청년월세 지원사업");
            Policy bokjiro = mockPolicy(42L);
            given(policyRepository.findByNormalizedTitleWithBokjiroSource("청년월세지원사업"))
                    .willReturn(Optional.of(bokjiro));

            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            assertThat(result.outcome()).isEqualTo(PolicyIngestionResult.Outcome.SKIPPED_DUPLICATE);
            assertThat(result.policyId()).isEqualTo(42L);
            verify(policySourceRepository, never()).save(any());
            verify(policyRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 정규화 제목의 BOKJIRO 정책이 없으면 정상 등록한다")
        void registersWhenBokjiroAbsent() {
            RegisterPolicyCommand command = command(SourceType.YOUTH_CENTER, "WLF99999",
                    "신규 정책");
            given(policyRepository.findByNormalizedTitleWithBokjiroSource("신규정책"))
                    .willReturn(Optional.empty());
            given(policySourceRepository.findBySourceTypeAndExternalId(SourceType.YOUTH_CENTER, "WLF99999"))
                    .willReturn(Optional.empty());
            Policy saved = mockPolicy(99L);
            given(policyRepository.save(any())).willReturn(saved);

            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            assertThat(result.outcome()).isEqualTo(PolicyIngestionResult.Outcome.REGISTERED);
            assertThat(result.isNew()).isTrue();
        }
    }

    @Nested
    @DisplayName("BOKJIRO 흐름 회귀")
    class BokjiroRegression {

        @Test
        @DisplayName("BOKJIRO_CENTRAL 등록은 정규화 제목 조회를 호출하지 않는다")
        void bokjiroDoesNotCheckNormalizedTitle() {
            RegisterPolicyCommand command = command(SourceType.BOKJIRO_CENTRAL, "WLF00001",
                    "복지로 정책");
            given(policySourceRepository.findBySourceTypeAndExternalId(SourceType.BOKJIRO_CENTRAL, "WLF00001"))
                    .willReturn(Optional.empty());
            Policy saved = mockPolicy(7L);
            given(policyRepository.save(any())).willReturn(saved);

            policyIngestionService.registerPolicy(command);

            verify(policyRepository, never()).findByNormalizedTitleWithBokjiroSource(any());
        }
    }

    @Nested
    @DisplayName("기존 source 재수신 — UPDATED vs SKIPPED_DUPLICATE 분리")
    class ExistingSourceOutcome {

        @Test
        @DisplayName("기존 source + hash 변경 → UPDATED")
        void existingChanged() {
            RegisterPolicyCommand command = command(SourceType.BOKJIRO_CENTRAL, "WLF111", "정책 A");
            PolicySource existing = mockPolicySource(11L, "old-hash");
            given(policySourceRepository.findBySourceTypeAndExternalId(SourceType.BOKJIRO_CENTRAL, "WLF111"))
                    .willReturn(Optional.of(existing));

            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            assertThat(result.outcome()).isEqualTo(PolicyIngestionResult.Outcome.UPDATED);
            assertThat(result.policyId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("기존 source + hash 동일 → SKIPPED_DUPLICATE (이벤트/저장 없음)")
        void existingUnchanged() {
            RegisterPolicyCommand command = command(SourceType.BOKJIRO_CENTRAL, "WLF222", "정책 B");
            PolicySource existing = mockPolicySource(22L, "hash");

            // command 의 sourceHash 와 existing 의 sourceHash 가 같다고 mock
            given(existing.hasChanged(command.sourceHash())).willReturn(false);
            given(policySourceRepository.findBySourceTypeAndExternalId(SourceType.BOKJIRO_CENTRAL, "WLF222"))
                    .willReturn(Optional.of(existing));

            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            assertThat(result.outcome()).isEqualTo(PolicyIngestionResult.Outcome.SKIPPED_DUPLICATE);
            assertThat(result.policyId()).isEqualTo(22L);
        }
    }

    // ── 헬퍼 ──

    private RegisterPolicyCommand command(SourceType sourceType, String externalId, String title) {
        return new RegisterPolicyCommand(
                title, "summary", "[개요]\nbody", null, null, null,
                "org", "contact", Category.WELFARE, "전국", List.of(),
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, false, null,
                Set.of(), Set.of(), Set.of(),
                List.of(), List.of(), List.of(),
                sourceType, externalId, "https://example.com",
                "{\"json\":\"raw\"}", "hash",
                null
        );
    }

    private Policy mockPolicy(Long id) {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getId()).willReturn(id);
        return p;
    }

    private PolicySource mockPolicySource(Long policyId, String hash) {
        PolicySource s = org.mockito.Mockito.mock(PolicySource.class);
        Policy policy = mockPolicy(policyId);
        given(s.getPolicy()).willReturn(policy);
        given(s.hasChanged(any())).willReturn(true); // default — overridden in test
        return s;
    }

    @Test
    void registerPolicy_propagates_youth_center_detail_fields_to_new_policy() {
        given(policyRepository.findByNormalizedTitleWithBokjiroSource(any()))
                .willReturn(Optional.empty());
        given(policySourceRepository.findBySourceTypeAndExternalId(any(), any()))
                .willReturn(Optional.empty());
        given(policyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegisterPolicyCommand command = sampleRegisterCommandWithDetailFields();
        policyIngestionService.registerPolicy(command);

        ArgumentCaptor<Policy> policyCaptor = ArgumentCaptor.forClass(Policy.class);
        verify(policyRepository).save(policyCaptor.capture());
        Policy saved = policyCaptor.getValue();
        assertThat(saved.getScreeningMethod()).isEqualTo("심사방법");
        assertThat(saved.getSubmissionDocuments()).isEqualTo("주민등록등본");
        assertThat(saved.getAdditionalQualification()).isEqualTo("추가 자격");
        assertThat(saved.getParticipationRestriction()).isEqualTo("기존 수혜자 제외");
        assertThat(saved.getAdditionalNotes()).isEqualTo("기타");
        assertThat(saved.getBusinessPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saved.getBusinessPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(saved.getBusinessPeriodNote()).isEqualTo("특정기간");
        assertThat(saved.getSupportScale()).isEqualTo(25);
        assertThat(saved.isFirstComeFirstServed()).isTrue();
        assertThat(saved.getApplyUrl()).isEqualTo("https://apply.kr");
    }

    private RegisterPolicyCommand sampleRegisterCommandWithDetailFields() {
        return new RegisterPolicyCommand(
                "샘플", "요약", "본문",
                "대상", "기준", "내용",
                "기관", "연락처",
                Category.JOBS, "서울특별시", List.of(),
                null, null, 2026, "연 1회", "보조금",
                "심사방법", "주민등록등본", "추가 자격", "기존 수혜자 제외", "기타",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "특정기간",
                25, true, "https://apply.kr",
                Set.of(), Set.of(), Set.of(),
                List.of(), List.of(), List.of(),
                SourceType.YOUTH_CENTER, "EXT-001", "https://src.kr",
                "{}", "abc123",
                null
        );
    }
}
