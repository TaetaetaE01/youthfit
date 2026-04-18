package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.domain.model.*;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("PolicyIngestionService")
@ExtendWith(MockitoExtension.class)
class PolicyIngestionServiceTest {

    @InjectMocks
    private PolicyIngestionService policyIngestionService;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicySourceRepository policySourceRepository;

    @Nested
    @DisplayName("registerPolicy - 신규 정책")
    class NewPolicy {

        @Test
        @DisplayName("기존 소스가 없으면 새 Policy와 PolicySource를 생성한다")
        void noExistingSource_createsNewPolicyAndSource() {
            // given
            RegisterPolicyCommand command = createCommand();
            given(policySourceRepository.findBySourceTypeAndExternalId(any(), any()))
                    .willReturn(Optional.empty());

            Policy savedPolicy = createPolicy(1L);
            given(policyRepository.save(any())).willReturn(savedPolicy);
            given(policySourceRepository.save(any())).willReturn(null);

            // when
            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.isNew()).isTrue();
            then(policyRepository).should().save(any());
            then(policySourceRepository).should().save(any());
        }
    }

    @Nested
    @DisplayName("registerPolicy - 기존 정책 (중복)")
    class ExistingPolicy {

        @Test
        @DisplayName("소스가 존재하고 해시가 동일하면 업데이트하지 않는다")
        void sameHash_doesNotUpdate() {
            // given
            RegisterPolicyCommand command = createCommand();
            Policy existingPolicy = createPolicy(1L);
            PolicySource existingSource = createPolicySource(existingPolicy, command.sourceHash());

            given(policySourceRepository.findBySourceTypeAndExternalId(any(), any()))
                    .willReturn(Optional.of(existingSource));

            // when
            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.isNew()).isFalse();
            then(policyRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("소스가 존재하고 해시가 다르면 정책과 소스를 업데이트한다")
        void differentHash_updatesExisting() {
            // given
            RegisterPolicyCommand command = createCommand();
            Policy existingPolicy = createPolicy(1L);
            PolicySource existingSource = createPolicySource(existingPolicy, "old-hash");

            given(policySourceRepository.findBySourceTypeAndExternalId(any(), any()))
                    .willReturn(Optional.of(existingSource));

            // when
            PolicyIngestionResult result = policyIngestionService.registerPolicy(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.isNew()).isFalse();
            assertThat(existingPolicy.getTitle()).isEqualTo(command.title());
        }
    }

    // ── 헬퍼 메서드 ──

    private RegisterPolicyCommand createCommand() {
        return new RegisterPolicyCommand(
                "청년 취업 지원",
                "청년 취업을 지원합니다.",
                null,
                null,
                null,
                null,
                null,
                null,
                Category.JOBS,
                "11",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 30),
                null,
                null,
                null,
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                SourceType.YOUTH_SEOUL_CRAWL,
                "https://youth.seoul.go.kr/policy/1",
                "https://youth.seoul.go.kr/policy/1",
                "{\"title\":\"청년 취업 지원\"}",
                "abc123hash"
        );
    }

    private Policy createPolicy(Long id) {
        Policy policy = Policy.builder()
                .title("기존 정책")
                .summary("기존 요약")
                .category(Category.JOBS)
                .regionCode("11")
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private PolicySource createPolicySource(Policy policy, String sourceHash) {
        return PolicySource.builder()
                .policy(policy)
                .sourceType(SourceType.YOUTH_SEOUL_CRAWL)
                .externalId("https://youth.seoul.go.kr/policy/1")
                .sourceUrl("https://youth.seoul.go.kr/policy/1")
                .rawJson("{}")
                .sourceHash(sourceHash)
                .build();
    }
}
