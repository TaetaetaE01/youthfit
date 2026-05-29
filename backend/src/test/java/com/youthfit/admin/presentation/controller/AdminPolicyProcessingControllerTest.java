package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.dto.AttachmentSummaryResult;
import com.youthfit.admin.application.dto.PolicyProcessingItemResult;
import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import com.youthfit.admin.application.dto.PolicyProcessingStatsResult;
import com.youthfit.admin.application.dto.ReferenceSummaryResult;
import com.youthfit.admin.application.dto.ReprocessResult;
import com.youthfit.admin.application.service.AdminPolicyProcessingService;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminPolicyProcessingController")
@WebMvcTest(AdminPolicyProcessingController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminPolicyProcessingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminPolicyProcessingService service;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    InternalApiKeyFilter internalApiKeyFilter;

    @BeforeEach
    void stubFiltersAsPassThrough() throws Exception {
        doAnswer(this::invokeChain).when(jwtAuthenticationFilter)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        doAnswer(this::invokeChain).when(internalApiKeyFilter)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    private Object invokeChain(org.mockito.invocation.InvocationOnMock invocation) throws Exception {
        ServletRequest req = invocation.getArgument(0);
        ServletResponse res = invocation.getArgument(1);
        FilterChain chain = invocation.getArgument(2);
        chain.doFilter(req, res);
        return null;
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@youthfit.test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                "u@youthfit.test", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/policies/processing - 200 + 목록 행을 반환한다")
    void getPolicies_returns200WithList() throws Exception {
        PolicyProcessingItemResult item = new PolicyProcessingItemResult(
                100L,
                "월세 지원",
                "서울",
                PolicyProcessingCompleteness.COMPLETE,
                Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS),
                new AttachmentSummaryResult(0, 0, 0),
                ReferenceSummaryResult.placeholder(),
                LocalDateTime.now()
        );
        when(service.findProcessingPolicies(any())).thenReturn(
                new PolicyProcessingListResult(1L, 0, 50, List.of(item))
        );

        mockMvc.perform(get("/api/v1/admin/policies/processing")
                        .param("filter", "ALL")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.items[0].policyId").value(100))
                .andExpect(jsonPath("$.items[0].title").value("월세 지원"))
                .andExpect(jsonPath("$.items[0].completeness").value("COMPLETE"))
                .andExpect(jsonPath("$.items[0].stepStatuses.RAG_INDEXING").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/policies/processing/stats - 200 + KPI 카운트를 반환한다")
    void getStats_returns200WithCounts() throws Exception {
        when(service.findProcessingStats()).thenReturn(
                new PolicyProcessingStatsResult(247, 182, 51, 14, 8)
        );

        mockMvc.perform(get("/api/v1/admin/policies/processing/stats")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(247))
                .andExpect(jsonPath("$.completeCount").value(182))
                .andExpect(jsonPath("$.partialCount").value(51))
                .andExpect(jsonPath("$.incompleteCount").value(14))
                .andExpect(jsonPath("$.recent24hCount").value(8));
    }

    @Test
    @DisplayName("GET /api/v1/admin/policies/processing/{id} - 정책이 없으면 404")
    void getDetail_returns404WhenPolicyNotFound() throws Exception {
        when(service.findProcessingDetail(eq(999L)))
                .thenThrow(new YouthFitException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/policies/processing/999")
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/admin/policies/processing - 인증 없으면 401")
    void getPolicies_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/policies/processing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/admin/policies/processing - ADMIN 아니면 403")
    void getPolicies_returns403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/policies/processing")
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden());
    }

    // ---- Task 10: retryStep ----

    @Test
    @DisplayName("POST /steps/{step}/retry - 200 + 큐잉된 stepId 를 반환한다")
    void retryStep_returns200WithStepIds() throws Exception {
        when(service.retryStep(eq(100L), eq(com.youthfit.policy.domain.model.ProcessingStep.RAG_INDEXING)))
                .thenReturn(new ReprocessResult(true, List.of(77L), "재실행 큐잉됨"));

        mockMvc.perform(post("/api/v1/admin/policies/processing/100/steps/RAG_INDEXING/retry")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(true))
                .andExpect(jsonPath("$.stepIds[0]").value(77));
    }

    @Test
    @DisplayName("POST /steps/INGESTION/retry - 400 (INGESTION 거부)")
    void retryStep_returns400_forIngestionStep() throws Exception {
        when(service.retryStep(eq(100L), eq(com.youthfit.policy.domain.model.ProcessingStep.INGESTION)))
                .thenThrow(new YouthFitException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(post("/api/v1/admin/policies/processing/100/steps/INGESTION/retry")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /steps/{step}/retry - 알 수 없는 step 은 400")
    void retryStep_returns400_forUnknownStep() throws Exception {
        mockMvc.perform(post("/api/v1/admin/policies/processing/100/steps/UNKNOWN/retry")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }
}
