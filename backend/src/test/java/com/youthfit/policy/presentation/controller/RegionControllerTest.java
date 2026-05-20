package com.youthfit.policy.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.service.RegionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("RegionController")
@WebMvcTest(controllers = RegionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@WithMockUser
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionQueryService regionQueryService;

    @Test
    @DisplayName("GET /api/v1/regions/all - 시·도 + 시·군·구 목록을 반환한다")
    void findAllRegions_returns200WithBothLists() throws Exception {
        RegionListResult mockResult = new RegionListResult(
                List.of(new RegionListResult.Sido("11", "서울특별시")),
                List.of(new RegionListResult.Sigungu("11680", "11", "서울특별시", "강남구"))
        );
        given(regionQueryService.findAllRegions()).willReturn(mockResult);

        mockMvc.perform(get("/api/v1/regions/all"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(jsonPath("$.sidos[0].code").value("11"))
                .andExpect(jsonPath("$.sidos[0].name").value("서울특별시"))
                .andExpect(jsonPath("$.sigungus[0].code").value("11680"))
                .andExpect(jsonPath("$.sigungus[0].name").value("강남구"));
    }
}
