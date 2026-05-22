package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.service.AdminDashboardOverviewService;
import com.youthfit.admin.presentation.dto.response.DashboardActionItemResponse;
import com.youthfit.admin.presentation.dto.response.DashboardAreaStatusResponse;
import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminDashboardController")
@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminDashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminDashboardOverviewService service;

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

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/overview - ADMIN 권한이면 200 과 payload 를 반환한다")
    void overview_returns200_forAdmin() throws Exception {
        Instant generatedAt = Instant.parse("2026-05-22T03:00:00Z");
        Instant detectedAt = Instant.parse("2026-05-22T02:55:00Z");
        var actionItem = new DashboardActionItemResponse(
                "INGESTION_FAILURE",
                DashboardSeverity.HIGH,
                "수집 실패율 상승",
                "최근 1시간 실패율 12%",
                "/admin/ingestion",
                detectedAt
        );
        var area = new DashboardAreaStatusResponse(
                "ingestion",
                "Ingestion",
                "CRITICAL",
                "확인 필요",
                List.of(),
                "/admin/ingestion"
        );
        when(service.findOverview()).thenReturn(
                new DashboardOverviewResponse(generatedAt, List.of(actionItem), List.of(area)));

        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mvc.perform(get("/api/v1/admin/dashboard/overview").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generatedAt").exists())
                .andExpect(jsonPath("$.data.actionItems[0].code").value("INGESTION_FAILURE"))
                .andExpect(jsonPath("$.data.actionItems[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data.actionItems[0].deeplink").value("/admin/ingestion"))
                .andExpect(jsonPath("$.data.areas[0].key").value("ingestion"))
                .andExpect(jsonPath("$.data.areas[0].status").value("CRITICAL"))
                .andExpect(jsonPath("$.data.areas[0].summary").value("확인 필요"))
                .andExpect(jsonPath("$.data.areas[0].sparkline").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/overview - 인증 없으면 401")
    void overview_returns401_whenUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/overview - ADMIN 이 아니면 403")
    void overview_returns403_whenNotAdmin() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mvc.perform(get("/api/v1/admin/dashboard/overview").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }
}
