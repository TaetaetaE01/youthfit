package com.youthfit.ingestion.domain.model;

import java.util.Objects;

/**
 * 첨부 추출 결과의 페이지 단위 record.
 *
 * @param page 페이지 번호 (1-based). HWP 등 페이지 메타가 없는 추출 결과는 null.
 * @param text 페이지 텍스트 (not null, 빈 문자열은 허용).
 */
public record PageText(Integer page, String text) {
    public PageText {
        Objects.requireNonNull(text, "text must not be null");
    }
}
