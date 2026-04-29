package com.youthfit.ingestion.domain.model;

import java.util.Objects;

public record PageText(Integer page, String text) {
    public PageText {
        Objects.requireNonNull(text, "text must not be null");
        // page == null 허용 (HWP / 페이지 마커 없는 추출 결과)
    }
}
