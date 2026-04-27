package com.youthfit.guide.domain.model;

import java.util.List;

public record GuidePairedSection(List<GuideGroup> groups) {

    public GuidePairedSection {
        if (groups == null) {
            throw new IllegalArgumentException("groups는 null일 수 없습니다");
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("groups가 비어있을 수 없습니다");
        }
        groups = List.copyOf(groups);
    }
}
