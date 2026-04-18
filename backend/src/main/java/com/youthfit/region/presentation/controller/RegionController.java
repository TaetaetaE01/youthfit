package com.youthfit.region.presentation.controller;

import com.youthfit.region.application.service.RegionQueryService;
import com.youthfit.region.domain.model.RegionLevel;
import com.youthfit.region.presentation.dto.response.RegionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController implements RegionApi {

    private final RegionQueryService regionQueryService;

    @GetMapping
    @Override
    public ResponseEntity<List<RegionResponse>> findRegions(
            @RequestParam RegionLevel level,
            @RequestParam(required = false) String parentCode) {

        List<RegionResponse> body = regionQueryService.findRegions(level, parentCode).stream()
                .map(RegionResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}
