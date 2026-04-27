package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@DisplayName("PolicyQueryService")
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {

    @InjectMocks
    private PolicyQueryService policyQueryService;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicySourceRepository policySourceRepository;

    @Nested
    @DisplayName("findPolicyById")
    class FindPolicyById {

        @Test
        @DisplayName("존재하는 정책 ID로 조회하면 상세 결과를 반환한다")
        void exists_returnsDetail() {
            // given
            Policy policy = createMockPolicy();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policySourceRepository.findFirstByPolicyId(1L)).willReturn(Optional.<PolicySource>empty());

            // when
            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.title()).isEqualTo("청년 주거 지원");
            assertThat(result.category()).isEqualTo(Category.HOUSING);
        }

        @Test
        @DisplayName("source 가 있으면 sourceType/sourceLabel/sourceUrl 이 채워진다")
        void sourcePresent_returnsAllSourceFields() {
            Policy policy = createMockPolicy();
            PolicySource source = PolicySource.builder()
                    .policy(policy)
                    .sourceType(SourceType.BOKJIRO_CENTRAL)
                    .externalId("ext-1")
                    .sourceUrl("https://example.com/policy/1")
                    .rawJson("{}")
                    .sourceHash("hash")
                    .build();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policySourceRepository.findFirstByPolicyId(1L)).willReturn(Optional.of(source));

            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            assertThat(result.sourceType()).isEqualTo(SourceType.BOKJIRO_CENTRAL);
            assertThat(result.sourceLabel()).isEqualTo("복지로");
            assertThat(result.sourceUrl()).isEqualTo("https://example.com/policy/1");
        }

        @Test
        @DisplayName("존재하지 않는 정책 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        void notExists_throwsNotFoundException() {
            // given
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> policyQueryService.findPolicyById(999L))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("findPoliciesByFilters")
    class FindPoliciesByFilters {

        @Test
        @DisplayName("status=OPEN 필터로 정책 목록을 페이징 조회한다")
        void withOpenStatus_returnsFilteredPage() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.findAllByFilters(
                    eq("11"), eq(Category.HOUSING), eq(PolicyStatus.OPEN), any(Pageable.class)))
                    .willReturn(mockPage);
            given(policySourceRepository.findFirstByPolicyIds(anyList()))
                    .willReturn(Map.of());

            // when
            PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                    "11", Category.HOUSING, PolicyStatus.OPEN, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("필터가 모두 null이어도 정상 동작한다")
        void allNull_returnsPage() {
            // given
            Page<Policy> emptyPage = Page.empty();
            given(policyRepository.findAllByFilters(
                    isNull(), isNull(), isNull(), any(Pageable.class)))
                    .willReturn(emptyPage);
            given(policySourceRepository.findFirstByPolicyIds(anyList()))
                    .willReturn(Map.of());

            // when
            PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                    null, null, null, 0, 20);

            // then
            assertThat(result.policies()).isEmpty();
            assertThat(result.totalCount()).isZero();
        }

        @Test
        @DisplayName("페이지 결과의 각 항목에 source 가 매핑된다")
        void filterPage_mapsSourceToEachSummary() {
            Policy policy = createMockPolicy();
            Page<Policy> mockPage = new PageImpl<>(List.of(policy), Pageable.ofSize(20), 1);
            PolicySource source = PolicySource.builder()
                    .policy(policy)
                    .sourceType(SourceType.YOUTH_CENTER)
                    .externalId("ext-y")
                    .sourceUrl("https://example.com/y")
                    .rawJson("{}")
                    .sourceHash("hash")
                    .build();
            given(policyRepository.findAllByFilters(any(), any(), any(), any(Pageable.class)))
                    .willReturn(mockPage);
            given(policySourceRepository.findFirstByPolicyIds(List.of(1L)))
                    .willReturn(Map.of(1L, source));

            PolicyPageResult result = policyQueryService.findPoliciesByFilters(null, null, null, 0, 20);

            assertThat(result.policies()).hasSize(1);
            assertThat(result.policies().getFirst().sourceType()).isEqualTo(SourceType.YOUTH_CENTER);
            assertThat(result.policies().getFirst().sourceLabel()).isEqualTo("온통청년");

            then(policySourceRepository).should(times(1)).findFirstByPolicyIds(any());
        }
    }

    @Nested
    @DisplayName("searchPoliciesByKeyword")
    class SearchPoliciesByKeyword {

        @Test
        @DisplayName("키워드 + status=OPEN으로 검색하면 status를 그대로 전달한다")
        void withKeywordAndStatus_passesStatus() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.searchByKeyword(eq("주거"), eq(PolicyStatus.OPEN), any(Pageable.class)))
                    .willReturn(mockPage);
            given(policySourceRepository.findFirstByPolicyIds(anyList()))
                    .willReturn(Map.of());

            // when
            PolicyPageResult result = policyQueryService.searchPoliciesByKeyword("주거", PolicyStatus.OPEN, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
            assertThat(result.policies().getFirst().title()).contains("주거");
        }

        @Test
        @DisplayName("status가 null이어도 키워드 검색은 정상 동작한다")
        void nullStatus_returnsResults() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.searchByKeyword(eq("주거"), isNull(), any(Pageable.class)))
                    .willReturn(mockPage);
            given(policySourceRepository.findFirstByPolicyIds(anyList()))
                    .willReturn(Map.of());

            // when
            PolicyPageResult result = policyQueryService.searchPoliciesByKeyword("주거", null, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
        }
    }

    // ── 헬퍼 메서드 ──

    private Policy createMockPolicy() {
        Policy policy = Policy.builder()
                .title("청년 주거 지원")
                .summary("월세 지원 프로그램")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 1, 1))
                .applyEnd(LocalDate.of(2026, 12, 31))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
    }
}
