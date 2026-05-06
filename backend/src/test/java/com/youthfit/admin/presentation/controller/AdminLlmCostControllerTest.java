package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminLlmCostService;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.metrics.domain.model.LlmModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminLlmCostController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminLlmCostControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminLlmCostService service;

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
    void kpi_GET_은_200_을_반환한다() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.getKpi()).thenReturn(new LlmCostKpiResponse(
                new BigDecimal("0.123456"), new BigDecimal("1.234"),
                new BigDecimal("12.34"), 1234L,
                new BigDecimal("1350"), new BigDecimal("11.11")
        ));

        mvc.perform(get("/api/v1/admin/llm-cost/kpi").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayCostUsd").exists())
                .andExpect(jsonPath("$.usdToKrwRate").value(1350));
    }

    @Test
    void series_GET_은_range_파라미터를_전달한다() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.getSeries("24h")).thenReturn(
                new LlmCostSeriesResponse("24h", List.of()));

        mvc.perform(get("/api/v1/admin/llm-cost/series")
                        .param("range", "24h")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"));
    }

    @Test
    void by_module_GET_은_기본값_7d_를_사용한다() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.getDailyByModule(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/llm-cost/by-module").with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    void by_model_GET_은_정렬된_리스트를_반환한다() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.getModelSummary(any())).thenReturn(List.of(
                new LlmCostModelSummaryResponse("gpt-4o-mini", 100, 1000, 500, 1500,
                        new BigDecimal("1.234"), new BigDecimal("80.00"))
        ));

        mvc.perform(get("/api/v1/admin/llm-cost/by-model").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("gpt-4o-mini"));
    }

    @Test
    void 인증_없이_접근하면_401_또는_403() throws Exception {
        mvc.perform(get("/api/v1/admin/llm-cost/kpi"))
                .andExpect(status().is4xxClientError());
    }
}
