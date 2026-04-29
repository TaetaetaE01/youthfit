package com.youthfit.guide.domain.model;

import java.util.Objects;

/**
 * 가이드 highlights/pitfalls 항목의 첨부 출처 trace.
 *
 * - sourceField=ATTACHMENT 일 때 not-null
 * - HWP/페이지 메타 없는 첨부는 pageStart/pageEnd null (둘 다 null 또는 둘 다 not-null)
 * - 페이지 범위는 pageStart ≤ pageEnd
 */
public record AttachmentRef(
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd) {

    public AttachmentRef {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        if ((pageStart == null) != (pageEnd == null)) {
            throw new IllegalArgumentException(
                    "pageStart 와 pageEnd 는 함께 존재해야 함");
        }
        if (pageStart != null && pageStart > pageEnd) {
            throw new IllegalArgumentException(
                    "pageStart 는 pageEnd 이하여야 함");
        }
    }
}
