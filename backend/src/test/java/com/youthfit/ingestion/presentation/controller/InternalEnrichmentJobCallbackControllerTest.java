package com.youthfit.ingestion.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalEnrichmentJobCallbackController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class InternalEnrichmentJobCallbackControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EnrichmentJobService jobService;

    @Test
    void RUNNING_콜백은_markRunning을_호출한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RUNNING"}
                                """))
                .andExpect(status().isNoContent());

        verify(jobService).markRunning(100L);
    }

    @Test
    void SUCCESS_콜백은_complete를_호출한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUCCESS"}
                                """))
                .andExpect(status().isNoContent());

        verify(jobService).complete(100L, EnrichmentJobStatus.SUCCESS, null);
    }

    @Test
    void FAILED_콜백은_에러메시지와_함께_complete를_호출한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FAILED","error":"fetch_timeout"}
                                """))
                .andExpect(status().isNoContent());

        verify(jobService).complete(100L, EnrichmentJobStatus.FAILED, "fetch_timeout");
    }

    @Test
    void status_누락_시_400을_반환한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"error":"some_error"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
