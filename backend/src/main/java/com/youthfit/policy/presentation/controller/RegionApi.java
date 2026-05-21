package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.presentation.dto.response.RegionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "지역", description = "행정코드 마스터 데이터 조회 API")
public interface RegionApi {

    @Operation(summary = "전체 지역 마스터 조회",
            description = "시·도(2자리) + 시·군·구(5자리) 행정코드 전체 목록을 반환한다. "
                    + "응답은 24시간 캐시 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<RegionListResponse> findAllRegions();
}
