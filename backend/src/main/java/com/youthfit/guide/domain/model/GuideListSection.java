package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideListSection(
        List<String> items,
        GuideSourceField sourceField,
        AttachmentRef attachmentRef
) {
    public GuideListSection {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items는 비어있을 수 없습니다");
        }
        if (sourceField == null) {
            throw new IllegalArgumentException("sourceField는 null일 수 없습니다");
        }
        items = List.copyOf(items);
    }

    public GuideListSection(List<String> items, GuideSourceField sourceField) {
        this(items, sourceField, null);
    }
}
