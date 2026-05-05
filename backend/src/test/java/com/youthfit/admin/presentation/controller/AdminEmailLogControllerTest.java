package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.EmailAttemptSummaryResponse;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.user.application.email.EmailDispatcher;
import com.youthfit.user.application.email.EmailSendAttemptQueryService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEmailLogController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminEmailLogControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EmailSendAttemptQueryService queryService;

    @MockitoBean
    EmailDispatcher dispatcher;

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
    void list_비인증_401() throws Exception {
        mvc.perform(get("/api/v1/admin/email-attempts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_USER_403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get("/api/v1/admin/email-attempts").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_ADMIN_OK() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(queryService.list(any())).thenReturn(new PageImpl<>(List.of(
                new EmailAttemptSummaryResponse(1L, "u@ex.com", "DEADLINE", "SENT",
                        "subj", LocalDateTime.now(), LocalDateTime.now(), "msg-1"))));
        mvc.perform(get("/api/v1/admin/email-attempts")
                        .with(authentication(auth))
                        .param("from", "2026-05-01").param("to", "2026-05-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    void list_status_csv_파싱() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(queryService.list(any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/admin/email-attempts")
                        .with(authentication(auth))
                        .param("statuses", "FAILED,BOUNCED"))
                .andExpect(status().isOk());
    }

    @Test
    void redispatch_FAILED_201() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(dispatcher.redispatch(50L)).thenReturn(99L);
        mvc.perform(post("/api/v1/admin/email-attempts/50/redispatch")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.newAttemptId").value(99));
    }

    @Test
    void redispatch_SENT_400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(dispatcher.redispatch(50L))
                .thenThrow(new IllegalStateException("FAILED 상태만 재발송 가능"));
        mvc.perform(post("/api/v1/admin/email-attempts/50/redispatch")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
