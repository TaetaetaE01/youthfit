package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminIngestionService;
import com.youthfit.admin.presentation.dto.response.IngestionFailureSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionKpiResponse;
import com.youthfit.admin.presentation.dto.response.IngestionRetryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionStaleSourceResponse;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminIngestionController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminIngestionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminIngestionService service;

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
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void kpi_GET_은_200_을_반환한다() throws Exception {
        when(service.getKpi()).thenReturn(new IngestionKpiResponse(
                10, 1, new BigDecimal("12.50"), new BigDecimal("0.10")));

        mvc.perform(get("/api/v1/admin/ingestion/kpi").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yesterdayReceived").value(10));
    }

    @Test
    void daily_stats_GET_은_days_파라미터를_전달한다() throws Exception {
        when(service.getDailyStats(7)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/ingestion/daily-stats")
                        .param("days", "7")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void stale_sources_GET_은_리스트를_반환한다() throws Exception {
        when(service.getStaleSources()).thenReturn(List.of(
                new IngestionStaleSourceResponse("ZZZ", Instant.now().minusSeconds(3600 * 25), 25L)
        ));

        mvc.perform(get("/api/v1/admin/ingestion/stale-sources").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("ZZZ"));
    }

    @Test
    void failures_GET_은_페이지네이션을_지원한다() throws Exception {
        Page<IngestionFailureSummaryResponse> page = new PageImpl<>(List.of());
        when(service.searchFailures(any(), any(), any(), any(), eq(0), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/admin/ingestion/failures").with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void retry_POST_는_재처리_결과를_반환한다() throws Exception {
        when(service.retryFailure(1L)).thenReturn(IngestionRetryResponse.from(RetryResult.success()));

        mvc.perform(post("/api/v1/admin/ingestion/failures/1/retry")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void failure_id_가_숫자가_아니면_컨트롤러_미매핑() throws Exception {
        // {id:\d+} 정규식으로 비숫자 경로는 컨트롤러에 매핑되지 않음 — GlobalExceptionHandler 가 500 반환
        mvc.perform(get("/api/v1/admin/ingestion/failures/abc").with(authentication(adminAuth())))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void 인증_없이_접근하면_4xx() throws Exception {
        mvc.perform(get("/api/v1/admin/ingestion/kpi"))
                .andExpect(status().is4xxClientError());
    }
}
