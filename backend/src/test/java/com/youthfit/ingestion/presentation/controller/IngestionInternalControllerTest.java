package com.youthfit.ingestion.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestionInternalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class IngestionInternalControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AttachmentReindexService attachmentReindexService;

    @Test
    @DisplayName("POST /api/internal/ingestion/reindex/{policyId} 가 AttachmentReindexService.reindex 를 호출한다")
    void reindex_invokesService() throws Exception {
        mockMvc.perform(post("/api/internal/ingestion/reindex/7").with(csrf()))
                .andExpect(status().isNoContent());
        verify(attachmentReindexService).reindex(7L);
    }
}
