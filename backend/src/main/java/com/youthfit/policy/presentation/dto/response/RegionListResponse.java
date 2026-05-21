package com.youthfit.policy.presentation.dto.response;

import com.youthfit.policy.application.dto.result.RegionListResult;

import java.util.List;

public record RegionListResponse(List<Sido> sidos, List<Sigungu> sigungus) {

    public record Sido(String code, String name) {}
    public record Sigungu(String code, String sidoCode, String sidoName, String name) {}

    public static RegionListResponse from(RegionListResult result) {
        return new RegionListResponse(
                result.sidos().stream().map(s -> new Sido(s.code(), s.name())).toList(),
                result.sigungus().stream()
                        .map(g -> new Sigungu(g.code(), g.sidoCode(), g.sidoName(), g.name())).toList()
        );
    }
}
