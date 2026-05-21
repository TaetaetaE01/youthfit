package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.service.RegionQueryService;
import com.youthfit.policy.presentation.dto.response.RegionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController("policyRegionController")
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController implements RegionApi {

    private final RegionQueryService regionQueryService;

    @GetMapping("/all")
    @Override
    public ResponseEntity<RegionListResponse> findAllRegions() {
        RegionListResult result = regionQueryService.findAllRegions();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS).cachePublic())
                .body(RegionListResponse.from(result));
    }
}
