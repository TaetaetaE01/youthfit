package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminQnaCacheService;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDetailResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupKpiResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupSummaryResponse;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.qna.domain.model.LookupResultType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminQnaCacheController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminQnaCacheControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminQnaCacheService service;

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
    void kpi_200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.kpi()).thenReturn(new QnaCacheLookupKpiResponse(
                BigDecimal.valueOf(0.65), BigDecimal.valueOf(0.62),
                BigDecimal.valueOf(0.88), BigDecimal.valueOf(0.0150),
                10L, 20L));

        mvc.perform(get("/api/v1/admin/qna-cache/kpi").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayHitRate").value(0.65));
    }

    @Test
    void admin_권한_없으면_403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get("/api/v1/admin/qna-cache/kpi").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void daily_stats_200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.dailyStats(14)).thenReturn(List.of(
                new QnaCacheLookupDailyStatsResponse(LocalDate.now(), 5L, 2L, 3L, BigDecimal.valueOf(0.85))));

        mvc.perform(get("/api/v1/admin/qna-cache/daily-stats")
                        .param("days", "14")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hitCount").value(5));
    }

    @Test
    void list_200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(
                new QnaCacheLookupSummaryResponse(1L, LocalDateTime.now(),
                        LookupResultType.HIT, 1L, "질문…", BigDecimal.valueOf(0.92), 10L))));

        mvc.perform(get("/api/v1/admin/qna-cache")
                        .param("size", "20")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    void detail_200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.detail(1L)).thenReturn(new QnaCacheLookupDetailResponse(
                1L, LocalDateTime.now(), LookupResultType.HIT, 1L, "질문", "정규화",
                10L, "캐시질문", "답변", BigDecimal.valueOf(0.92), BigDecimal.valueOf(0.20), false));

        mvc.perform(get("/api/v1/admin/qna-cache/1").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void export_csv_헤더_검증() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mvc.perform(get("/api/v1/admin/qna-cache/export").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"qna-cache-lookup.csv\""));
    }
}
