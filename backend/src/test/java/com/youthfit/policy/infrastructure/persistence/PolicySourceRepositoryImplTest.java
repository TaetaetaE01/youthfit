package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;

@DisplayName("PolicySourceRepositoryImpl.findFirstByPolicyIds")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicySourceRepositoryImplTest {

    @Mock
    private PolicySourceJpaRepository jpaRepository;

    @InjectMocks
    private PolicySourceRepositoryImpl sut;

    @Test
    @DisplayName("정책 N개 각각 source 1개 → Map 사이즈 N")
    void multiplePolicies_eachWithOneSource_returnsAllMapped() {
        Policy p1 = policyWithId(1L);
        Policy p2 = policyWithId(2L);
        PolicySource s1 = sourceWithPolicy(10L, p1, SourceType.BOKJIRO_CENTRAL);
        PolicySource s2 = sourceWithPolicy(20L, p2, SourceType.YOUTH_CENTER);

        given(jpaRepository.findAllByPolicyIdInOrderByIdAsc(List.of(1L, 2L)))
                .willReturn(List.of(s1, s2));

        Map<Long, PolicySource> result = sut.findFirstByPolicyIds(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(1L).getSourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
        assertThat(result.get(2L).getSourceType()).isEqualTo(SourceType.YOUTH_CENTER);
    }

    @Test
    @DisplayName("한 정책에 source 2개 → Map 에는 첫 번째(id 오름차순)만")
    void singlePolicyWithTwoSources_returnsFirstOnly() {
        Policy p1 = policyWithId(1L);
        PolicySource first = sourceWithPolicy(10L, p1, SourceType.BOKJIRO_CENTRAL);
        PolicySource second = sourceWithPolicy(20L, p1, SourceType.YOUTH_CENTER);

        given(jpaRepository.findAllByPolicyIdInOrderByIdAsc(List.of(1L)))
                .willReturn(List.of(first, second));

        Map<Long, PolicySource> result = sut.findFirstByPolicyIds(List.of(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(1L).getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("source 없는 정책 ID는 Map 에서 누락")
    void policyWithoutSource_isMissingFromMap() {
        Policy p1 = policyWithId(1L);
        PolicySource s1 = sourceWithPolicy(10L, p1, SourceType.BOKJIRO_CENTRAL);

        given(jpaRepository.findAllByPolicyIdInOrderByIdAsc(List.of(1L, 2L)))
                .willReturn(List.of(s1));

        Map<Long, PolicySource> result = sut.findFirstByPolicyIds(List.of(1L, 2L));

        assertThat(result).hasSize(1);
        assertThat(result).containsKey(1L);
        assertThat(result).doesNotContainKey(2L);
    }

    @Test
    @DisplayName("빈 입력 리스트 → 빈 Map (DB 호출 회피)")
    void emptyInput_returnsEmptyMap() {
        Map<Long, PolicySource> result = sut.findFirstByPolicyIds(List.of());

        assertThat(result).isEmpty();
        then(jpaRepository).should(never()).findAllByPolicyIdInOrderByIdAsc(any());
    }

    // ── 테스트 헬퍼 ──

    private Policy policyWithId(Long id) {
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(id);
        return policy;
    }

    private PolicySource sourceWithPolicy(Long sourceId, Policy policy, SourceType sourceType) {
        PolicySource source = mock(PolicySource.class);
        given(source.getId()).willReturn(sourceId);
        given(source.getPolicy()).willReturn(policy);
        given(source.getSourceType()).willReturn(sourceType);
        return source;
    }
}
