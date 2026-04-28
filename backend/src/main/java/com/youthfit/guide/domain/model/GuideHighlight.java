package com.youthfit.guide.domain.model;

public record GuideHighlight(String text, GuideSourceField sourceField) {

    public GuideHighlight {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text는 비어있을 수 없습니다");
        }
        if (sourceField == null) {
            throw new IllegalArgumentException("sourceField는 null일 수 없습니다");
        }
    }
}
