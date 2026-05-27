package com.youthfit.policy.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;
import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PolicyController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class PolicyCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyQueryService policyQueryService;

    @Test
    @DisplayName("GET /api/v1/policies/calendar — 200 + items 반환")
    void calendar_ok() throws Exception {
        PolicyCalendarResult r = new PolicyCalendarResult(
                1L, "청년월세", Category.HOUSING,
                LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 31), "전국", null);
        when(policyQueryService.findByDateRange(any(), any(), any(), eq(null)))
                .thenReturn(List.of(r));

        mockMvc.perform(get("/api/v1/policies/calendar")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].title").value("청년월세"))
                .andExpect(jsonPath("$.items[0].applyStart").value("2026-03-14"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/calendar — from > to 면 400 (YouthFitException YF-001)")
    void calendar_invalidRange() throws Exception {
        when(policyQueryService.findByDateRange(any(), any(), any(), any()))
                .thenThrow(new YouthFitException(ErrorCode.INVALID_INPUT,
                        "from 은 to 보다 이전이거나 같아야 합니다"));

        mockMvc.perform(get("/api/v1/policies/calendar")
                        .param("from", "2026-03-31")
                        .param("to", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("YF-001"));
    }

    @Test
    @DisplayName("GET /api/v1/policies/calendar/always-open — 200 + 페이지 응답")
    void alwaysOpen_ok() throws Exception {
        PolicyCalendarResult r = new PolicyCalendarResult(
                2L, "상시멘토링", Category.EDUCATION, null, null, "전국", null);
        PolicyCalendarPageResult page = new PolicyCalendarPageResult(
                List.of(r), 1L, 0, 20, 1, false);
        when(policyQueryService.findAlwaysOpen(any(), eq(null), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/policies/calendar/always-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.totalCount").value(1))
                // 진짜 상시 정책(applyStart=null, applyEnd=null)은 deadlineYear=null
                .andExpect(jsonPath("$.content[0].deadlineYear").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/policies/calendar/always-open — 사실상 상시 정책은 deadlineYear=2026 반환")
    void alwaysOpen_effectivelyAlwaysOpen_hasDeadlineYear() throws Exception {
        // isEffectivelyAlwaysOpen()=true 인 정책: applyEnd 가 2026-12-31 → deadlineYear=2026
        PolicyCalendarResult r = new PolicyCalendarResult(
                3L, "사실상상시지원", Category.JOBS,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "전국", 2026);
        PolicyCalendarPageResult page = new PolicyCalendarPageResult(
                List.of(r), 1L, 0, 20, 1, false);
        when(policyQueryService.findAlwaysOpen(any(), eq(null), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/policies/calendar/always-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].deadlineYear").value(2026));
    }
}
