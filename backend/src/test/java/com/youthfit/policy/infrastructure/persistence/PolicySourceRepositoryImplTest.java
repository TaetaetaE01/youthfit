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
class PolicySourceRepositoryImplTest {

    @Mock
    private PolicySourceJpaRepository jpaRepository;

    @InjectMocks
    private PolicySourceRepositoryImpl sut;

    @Test
    @DisplayName("정책 N개 각각 source 1개 → Map 사이즈 N")
    void multiplePolicies_eachWithOneSource_returnsAllMapped() {
        Policy p1 = mock(Policy.class);
        Policy p2 = mock(Policy.class);
        given(p1.getId()).willReturn(1L);
        given(p2.getId()).willReturn(2L);

        PolicySource s1 = mock(PolicySource.class);
        PolicySource s2 = mock(PolicySource.class);
        given(s1.getPolicy()).willReturn(p1);
        given(s1.getSourceType()).willReturn(SourceType.BOKJIRO_CENTRAL);
        given(s2.getPolicy()).willReturn(p2);
        given(s2.getSourceType()).willReturn(SourceType.YOUTH_CENTER);

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
        Policy p1 = mock(Policy.class);
        given(p1.getId()).willReturn(1L);

        PolicySource first = mock(PolicySource.class);
        PolicySource second = mock(PolicySource.class);
        given(first.getId()).willReturn(10L);
        given(first.getPolicy()).willReturn(p1);
        given(second.getPolicy()).willReturn(p1);

        given(jpaRepository.findAllByPolicyIdInOrderByIdAsc(List.of(1L)))
                .willReturn(List.of(first, second));

        Map<Long, PolicySource> result = sut.findFirstByPolicyIds(List.of(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(1L).getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("source 없는 정책 ID는 Map 에서 누락")
    void policyWithoutSource_isMissingFromMap() {
        Policy p1 = mock(Policy.class);
        given(p1.getId()).willReturn(1L);

        PolicySource s1 = mock(PolicySource.class);
        given(s1.getPolicy()).willReturn(p1);

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

}
