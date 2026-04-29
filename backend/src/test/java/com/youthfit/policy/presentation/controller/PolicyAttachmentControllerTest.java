package com.youthfit.policy.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.application.service.RedirectAttachmentService;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PolicyAttachmentController")
@WebMvcTest(controllers = PolicyAttachmentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class PolicyAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RedirectAttachmentService redirectAttachmentService;

    @Test
    @DisplayName("PresignRedirect 결과 → 302 + Location header")
    void givenPresignResult_whenGet_then302WithLocation() throws Exception {
        when(redirectAttachmentService.resolve(12L))
                .thenReturn(new AttachmentRedirectResult.PresignRedirect("https://s3.aws/presigned"));

        mockMvc.perform(get("/api/policies/attachments/12/file"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://s3.aws/presigned"));
    }

    @Test
    @DisplayName("StreamResponse 결과 → 200 + Content-Type + body")
    void givenStreamResult_whenGet_then200WithBody() throws Exception {
        when(redirectAttachmentService.resolve(12L))
                .thenReturn(new AttachmentRedirectResult.StreamResponse(
                        new ByteArrayInputStream("hello".getBytes()),
                        "application/pdf",
                        "x.pdf"));

        mockMvc.perform(get("/api/policies/attachments/12/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("hello".getBytes()));
    }

    @Test
    @DisplayName("ExternalRedirect 결과 → 302 + 외부 Location")
    void givenExternalResult_whenGet_then302External() throws Exception {
        when(redirectAttachmentService.resolve(12L))
                .thenReturn(new AttachmentRedirectResult.ExternalRedirect("https://orig/x.pdf"));

        mockMvc.perform(get("/api/policies/attachments/12/file"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://orig/x.pdf"));
    }

    @Test
    @DisplayName("AttachmentNotFoundException → 404")
    void givenNotFound_whenGet_then404() throws Exception {
        when(redirectAttachmentService.resolve(99L)).thenThrow(new AttachmentNotFoundException(99L));

        mockMvc.perform(get("/api/policies/attachments/99/file"))
                .andExpect(status().isNotFound());
    }
}
