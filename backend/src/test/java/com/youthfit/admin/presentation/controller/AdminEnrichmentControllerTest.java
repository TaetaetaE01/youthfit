package com.youthfit.admin.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.admin.presentation.dto.ReferenceSiteRequest;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import com.youthfit.policy.application.service.EnrichmentJobConflictException;
import com.youthfit.policy.application.service.EnrichmentJobRateLimitException;
import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.service.PolicyReferenceSiteMerger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEnrichmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminEnrichmentControllerTest {

    @Autowired MockMvc mvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean AdminEnrichmentQueryService queryService;
    @MockitoBean EnrichmentJobService jobService;
    @MockitoBean PolicyRepository policyRepo;
    @MockitoBean EnrichmentJobRepository jobRepo;
    @MockitoBean PolicyReferenceSiteMerger merger;

    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean InternalApiKeyFilter internalApiKeyFilter;

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
    void 인증되지않은_요청은_401() throws Exception {
        mvc.perform(get("/api/v1/admin/enrichment/candidates"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void ADMIN이아니면_403() throws Exception {
        mvc.perform(get("/api/v1/admin/enrichment/candidates").with(authentication(userAuth())))
           .andExpect(status().isForbidden());
    }

    @Test
    void POST_jobs_는_202와_jobId를_돌려준다() throws Exception {
        EnrichmentJob job = mock(EnrichmentJob.class);
        when(job.getId()).thenReturn(100L);
        when(job.getStatus()).thenReturn(EnrichmentJobStatus.PENDING);
        when(jobService.create(eq(42L), any(), any())).thenReturn(job);

        mvc.perform(post("/api/v1/admin/enrichment/policies/42/jobs")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.jobId").value(100))
           .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void POST_jobs_는_진행중_잡_있으면_409() throws Exception {
        when(jobService.create(eq(42L), any(), any()))
                .thenThrow(new EnrichmentJobConflictException(42L));

        mvc.perform(post("/api/v1/admin/enrichment/policies/42/jobs")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
           .andExpect(status().isConflict());
    }

    @Test
    void POST_jobs_는_레이트리밋이면_429() throws Exception {
        when(jobService.create(eq(42L), any(), any()))
                .thenThrow(new EnrichmentJobRateLimitException(42L, 5));

        mvc.perform(post("/api/v1/admin/enrichment/policies/42/jobs")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
           .andExpect(status().isTooManyRequests());
    }

    @Test
    void PUT_reference_sites_저장() throws Exception {
        var body = List.of(new ReferenceSiteRequest(
                "새URL", "https://new.example.com", PolicyReferenceSiteSource.ADMIN));
        Policy policy = mock(Policy.class);
        when(policy.getReferenceSites()).thenReturn(List.of());
        when(policyRepo.findById(42L)).thenReturn(Optional.of(policy));
        when(merger.merge(any(), any())).thenReturn(List.of());
        when(policyRepo.save(any())).thenReturn(policy);

        mvc.perform(put("/api/v1/admin/enrichment/policies/42/reference-sites")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
           .andExpect(status().isOk());
    }

    @Test
    void GET_candidates_summary_200() throws Exception {
        when(queryService.findReviewSummary()).thenReturn(
                new com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult(
                        10L, 3L, java.util.Map.of(), java.util.Map.of()));

        mvc.perform(get("/api/v1/admin/enrichment/candidates/summary")
                        .with(authentication(adminAuth())))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(10))
           .andExpect(jsonPath("$.needsReview").value(3));
    }

    @Test
    void GET_candidates_는_페이지를_반환한다() throws Exception {
        Page<Policy> empty = new PageImpl<>(List.of());
        when(queryService.findReviewCandidates(any(), any())).thenReturn(empty);

        mvc.perform(get("/api/v1/admin/enrichment/candidates")
                        .with(authentication(adminAuth())))
           .andExpect(status().isOk());
    }
}
