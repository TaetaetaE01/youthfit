package com.youthfit.guide.domain.model;

public record GuidePitfall(String text, GuideSourceField sourceField) {

    public GuidePitfall {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text는 비어있을 수 없습니다");
        }
        if (sourceField == null) {
            throw new IllegalArgumentException("sourceField는 null일 수 없습니다");
        }
    }
}
