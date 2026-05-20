package com.youthfit.policy.application.dto.result;

import java.util.List;

public record RegionListResult(
        List<Sido> sidos,
        List<Sigungu> sigungus
) {
    public record Sido(String code, String name) {}
    public record Sigungu(String code, String sidoCode, String sidoName, String name) {}
}
