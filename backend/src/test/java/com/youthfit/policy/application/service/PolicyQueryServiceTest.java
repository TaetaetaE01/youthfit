package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@DisplayName("PolicyQueryService")
@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {

    @InjectMocks
    private PolicyQueryService policyQueryService;

    @Mock
    private PolicyRepository policyRepository;

    @Nested
    @DisplayName("findPolicyById")
    class FindPolicyById {

        @Test
        @DisplayName("존재하는 정책 ID로 조회하면 상세 결과를 반환한다")
        void exists_returnsDetail() {
            // given
            Policy policy = createMockPolicy();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));

            // when
            PolicyDetailResult result = policyQueryService.findPolicyById(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.title()).isEqualTo("청년 주거 지원");
            assertThat(result.category()).isEqualTo(Category.HOUSING);
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
        @DisplayName("필터 조건으로 정책 목록을 페이징 조회한다")
        void withFilters_returnsFilteredPage() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.findAllByFilters(
                    eq("11"), eq(Category.HOUSING), isNull(), any(Pageable.class)))
                    .willReturn(mockPage);

            // when
            PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                    "11", Category.HOUSING, null, "createdAt", false, 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("결과가 없으면 빈 목록을 반환한다")
        void noResults_returnsEmptyPage() {
            // given
            Page<Policy> emptyPage = Page.empty();
            given(policyRepository.findAllByFilters(
                    any(), any(), any(), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                    null, null, null, "createdAt", false, 0, 20);

            // then
            assertThat(result.policies()).isEmpty();
            assertThat(result.totalCount()).isZero();
        }
    }

    @Nested
    @DisplayName("searchPoliciesByKeyword")
    class SearchPoliciesByKeyword {

        @Test
        @DisplayName("키워드로 정책을 검색하면 매칭된 결과를 반환한다")
        void withKeyword_returnsMatchingResults() {
            // given
            Page<Policy> mockPage = new PageImpl<>(
                    List.of(createMockPolicy()),
                    Pageable.ofSize(20), 1);
            given(policyRepository.searchByKeyword(eq("주거"), any(Pageable.class)))
                    .willReturn(mockPage);

            // when
            PolicyPageResult result = policyQueryService.searchPoliciesByKeyword("주거", 0, 20);

            // then
            assertThat(result.policies()).hasSize(1);
            assertThat(result.policies().getFirst().title()).contains("주거");
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
