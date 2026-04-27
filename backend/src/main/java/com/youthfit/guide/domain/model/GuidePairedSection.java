package com.youthfit.guide.domain.model;

import java.util.List;

public record GuidePairedSection(List<String> items) {

    public GuidePairedSection {
        if (items == null) {
            throw new IllegalArgumentException("items는 null일 수 없습니다");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items가 비어있을 수 없습니다");
        }
        items = List.copyOf(items);
    }
}
