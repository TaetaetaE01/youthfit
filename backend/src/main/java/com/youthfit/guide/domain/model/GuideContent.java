package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideContent(
        String oneLineSummary,
        GuidePairedSection target,
        GuidePairedSection criteria,
        GuidePairedSection content,
        List<GuidePitfall> pitfalls) {

    public GuideContent {
        if (oneLineSummary == null || oneLineSummary.isBlank()) {
            throw new IllegalArgumentException("oneLineSummary는 비어있을 수 없습니다");
        }
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
    }
}
