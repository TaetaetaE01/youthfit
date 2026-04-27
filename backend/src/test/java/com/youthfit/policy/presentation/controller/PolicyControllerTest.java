package com.youthfit.policy.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PolicyController")
@WebMvcTest(controllers = PolicyController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyQueryService policyQueryService;

    @Test
    @DisplayName("GET /api/v1/policies - 정책 목록을 조회한다")
    void findPolicies_returns200WithPage() throws Exception {
        // given
        PolicySummaryResult summary = new PolicySummaryResult(
                1L, "청년 취업 지원", "요약", Category.JOBS, "11",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                2026, PolicyStatus.OPEN, DetailLevel.LITE, "서울시");
        PolicyPageResult pageResult = new PolicyPageResult(
                List.of(summary), 1L, 0, 20, 1, false);

        given(policyQueryService.findPoliciesByFilters(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("청년 취업 지원"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/policies - 필터 파라미터(status 포함)를 전달할 수 있다")
    void findPolicies_withFilters_returns200() throws Exception {
        // given
        PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
        given(policyQueryService.findPoliciesByFilters(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies")
                        .param("regionCode", "11")
                        .param("category", "JOBS")
                        .param("status", "OPEN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        then(policyQueryService).should()
                .findPoliciesByFilters(eq("11"), eq(Category.JOBS), eq(PolicyStatus.OPEN), eq(0), eq(10));
    }

    @Test
    @DisplayName("GET /api/v1/policies - sortType 파라미터는 더 이상 사용되지 않으며 무시된다")
    void findPolicies_legacySortTypeParam_isIgnored() throws Exception {
        // given
        PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
        given(policyQueryService.findPoliciesByFilters(any(), any(), any(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then — sortType 파라미터가 있어도 200 응답
        mockMvc.perform(get("/api/v1/policies").param("sortType", "DEADLINE"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/policies/{policyId} - 정책 상세를 조회한다")
    void getPolicyDetail_returns200WithDetail() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 4, 15, 10, 0);
        PolicyDetailResult detail = new PolicyDetailResult(
                1L, "청년 취업 지원", "요약", null, null, null, null, null, null,
                Category.JOBS, "11",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30),
                null, null, null,
                PolicyStatus.OPEN, DetailLevel.LITE,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "https://youth.seoul.go.kr/policy/1",
                now, now);

        given(policyQueryService.findPolicyById(1L)).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/v1/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("청년 취업 지원"))
                .andExpect(jsonPath("$.category").value("JOBS"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/search - 키워드만 전달하면 status는 null로 위임된다")
    void searchPolicies_keywordOnly_passesNullStatus() throws Exception {
        // given
        PolicySummaryResult summary = new PolicySummaryResult(
                1L, "청년 취업 지원", "요약", Category.JOBS, "11",
                null, null, 2026, PolicyStatus.OPEN, DetailLevel.LITE, "서울시");
        PolicyPageResult pageResult = new PolicyPageResult(
                List.of(summary), 1L, 0, 20, 1, false);

        given(policyQueryService.searchPoliciesByKeyword(eq("취업"), isNull(), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies/search").param("keyword", "취업"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("청년 취업 지원"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/search - status를 함께 전달하면 그대로 서비스에 위임된다")
    void searchPolicies_keywordWithStatus_passesStatus() throws Exception {
        // given
        PolicyPageResult pageResult = new PolicyPageResult(List.of(), 0L, 0, 20, 0, false);
        given(policyQueryService.searchPoliciesByKeyword(eq("취업"), eq(PolicyStatus.OPEN), anyInt(), anyInt()))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/v1/policies/search")
                        .param("keyword", "취업")
                        .param("status", "OPEN"))
                .andExpect(status().isOk());
    }
}
