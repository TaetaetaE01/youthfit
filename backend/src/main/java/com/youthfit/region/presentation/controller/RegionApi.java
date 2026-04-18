package com.youthfit.region.presentation.controller;

import com.youthfit.region.domain.model.RegionLevel;
import com.youthfit.region.presentation.dto.response.RegionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "지역", description = "법정동코드 기반 시도/시군구 조회 API")
public interface RegionApi {

    @Operation(summary = "시도/시군구 조회",
            description = "level=SIDO로 시도 전체 조회, level=SIGUNGU&parentCode={시도코드}로 해당 시도의 시군구 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<com.youthfit.common.response.ApiResponse<List<RegionResponse>>> findRegions(
            @Parameter(description = "조회 레벨: SIDO | SIGUNGU", required = true) RegionLevel level,
            @Parameter(description = "상위 법정동코드 (SIGUNGU 조회 시 필수)") String parentCode
    );
}
