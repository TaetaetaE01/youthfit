package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.RegionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service("policyRegionQueryService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionQueryService {

    private final RegionCodeRegistry regionCodeRegistry;

    public RegionListResult findAllRegions() {
        List<RegionCode> all = regionCodeRegistry.findAll();
        Map<String, String> sidoOrder = new LinkedHashMap<>();
        List<RegionListResult.Sigungu> sigungus = new java.util.ArrayList<>();
        for (RegionCode rc : all) {
            sidoOrder.putIfAbsent(rc.sidoCode(), rc.sidoName());
            sigungus.add(new RegionListResult.Sigungu(rc.code(), rc.sidoCode(), rc.sidoName(), rc.name()));
        }
        List<RegionListResult.Sido> sidos = sidoOrder.entrySet().stream()
                .map(e -> new RegionListResult.Sido(e.getKey(), e.getValue()))
                .toList();
        return new RegionListResult(sidos, sigungus);
    }
}
