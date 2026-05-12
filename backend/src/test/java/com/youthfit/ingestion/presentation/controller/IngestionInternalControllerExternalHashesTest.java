package com.youthfit.ingestion.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.ingestion.application.service.IngestionService;
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

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalApiKeyFilter 는 슬라이스 테스트 픽스처에서 excludeFilters 로 제외하므로,
 * 401 거부 케이스는 이 테스트에서 검증하지 않는다.
 * 필터 통합 테스트는 별도 통합 테스트(InternalApiKeyFilter 를 포함한 @SpringBootTest)에서 커버할 것.
 */
@WebMvcTest(controllers = IngestionInternalController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class IngestionInternalControllerExternalHashesTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AttachmentReindexService attachmentReindexService;

    @MockitoBean
    IngestionService ingestionService;

    @Test
    @DisplayName("GET /api/internal/ingestion/policies/external-hashes 가 external_id → hash 맵을 반환한다")
    void returns_external_id_hash_map() throws Exception {
        given(ingestionService.findExternalIdHashes("YOUTH_CENTER"))
                .willReturn(Map.of("PLY001", "h1", "PLY002", "h2"));

        mockMvc.perform(get("/api/internal/ingestion/policies/external-hashes")
                        .param("source", "YOUTH_CENTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PLY001").value("h1"))
                .andExpect(jsonPath("$.PLY002").value("h2"));
    }

    @Test
    @DisplayName("source 파라미터가 없으면 5xx 를 반환한다 (GlobalExceptionHandler 가 MissingServletRequestParameterException 을 별도 처리하지 않아 500으로 내려옴)")
    void returns_5xx_when_source_param_is_missing() throws Exception {
        // GlobalExceptionHandler 에 MissingServletRequestParameterException 핸들러가 없어
        // 현재 catch-all(Exception) 이 처리하므로 500이 반환된다.
        // 400 처리가 필요하면 GlobalExceptionHandler 에 핸들러를 추가해야 한다.
        mockMvc.perform(get("/api/internal/ingestion/policies/external-hashes"))
                .andExpect(status().is5xxServerError());
    }
}
