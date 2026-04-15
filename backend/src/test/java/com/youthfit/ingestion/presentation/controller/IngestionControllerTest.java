package com.youthfit.ingestion.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.application.service.IngestionService;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("IngestionController")
@WebMvcTest(controllers = IngestionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    @DisplayName("POST /api/internal/ingestion/policies - 정책을 수집하면 202를 반환한다")
    void receivePolicy_returns202() throws Exception {
        // given
        UUID ingestionId = UUID.randomUUID();
        given(ingestionService.receivePolicy(any()))
                .willReturn(new IngestPolicyResult(ingestionId, "RECEIVED"));

        // when & then
        mockMvc.perform(post("/api/internal/ingestion/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {
                                    "url": "https://youth.seoul.go.kr/policy/1",
                                    "type": "YOUTH_SEOUL_CRAWL",
                                    "fetchedAt": "2026-04-15T10:00:00"
                                  },
                                  "rawData": {
                                    "title": "청년 취업 지원",
                                    "body": "청년 취업을 지원합니다.",
                                    "category": "일자리",
                                    "region": "서울"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));
    }

    @Test
    @DisplayName("POST /api/internal/ingestion/policies - 필수 필드 누락 시 400을 반환한다")
    void receivePolicy_missingRequiredFields_returns400() throws Exception {
        // when & then
        mockMvc.perform(post("/api/internal/ingestion/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": null,
                                  "rawData": null
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
