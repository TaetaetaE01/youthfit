package com.youthfit.admin.rag.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.admin.rag.application.dto.result.PreviewSideResult;
import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;
import com.youthfit.admin.rag.application.service.RagPreviewRateLimitException;
import com.youthfit.admin.rag.application.service.RagPreviewService;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminRagPreviewController")
@WebMvcTest(AdminRagPreviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminRagPreviewControllerTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    @MockitoBean
    RagPreviewService previewService;

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
                "42", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                "42", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private RagPreviewResult okResult() {
        RagSearchTrace trace = new RagSearchTrace(
                new EffectiveConfig(true, 20, 60, 0.10, true, 5),
                List.of(), List.of(), List.of(), List.of("주거"), 100L);
        return new RagPreviewResult(1L, "주거",
                List.of("주거"),
                new PreviewSideResult(trace),
                new PreviewSideResult(trace),
                List.of());
    }

    @Test
    @DisplayName("admin 정상 요청 → 200")
    void admin_ok() throws Exception {
        given(previewService.preview(any())).willReturn(okResult());

        String body = om.writeValueAsString(Map.of(
                "policyId", 1,
                "query", "주거",
                "candidate", Map.of("rrfK", 30)
        ));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.baseline").exists())
                .andExpect(jsonPath("$.candidate").exists())
                .andExpect(jsonPath("$.diff.rankChanges").isArray());
    }

    @Test
    @DisplayName("비-admin → 403")
    void nonAdmin_forbidden() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거", "candidate", Map.of()));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없음 → 401")
    void unauthenticated_unauthorized() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거", "candidate", Map.of()));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("query blank → 400")
    void blankQuery_badRequest() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "", "candidate", Map.of()));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rrfK = 0 → 400")
    void invalidRrfK_badRequest() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거",
                "candidate", Map.of("rrfK", 0)));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("topNPerSearch = 1000 → 400 (상한 100)")
    void topNTooLarge_badRequest() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거",
                "candidate", Map.of("topNPerSearch", 1000)));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rate limit 초과 → 429 + Retry-After")
    void rateLimitExceeded_429() throws Exception {
        given(previewService.preview(any())).willThrow(new RagPreviewRateLimitException());

        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거", "candidate", Map.of()));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }
}
