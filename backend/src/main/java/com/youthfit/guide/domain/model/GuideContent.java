package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideContent(
        String oneLineSummary,
        List<GuideHighlight> highlights,
        GuidePairedSection target,
        GuidePairedSection criteria,
        GuidePairedSection content,
        GuideListSection applyMethod,
        GuideListSection deadlineNote,
        GuideListSection requiredDocuments,
        GuideListSection contact,
        List<GuidePitfall> pitfalls) {

    public GuideContent {
        if (oneLineSummary == null || oneLineSummary.isBlank()) {
            throw new IllegalArgumentException("oneLineSummary는 비어있을 수 없습니다");
        }
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
        // 4개 NEW 섹션은 nullable — 정책마다 정보 부재 가능
    }
}
