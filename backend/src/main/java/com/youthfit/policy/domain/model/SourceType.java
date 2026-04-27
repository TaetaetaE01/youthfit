package com.youthfit.policy.domain.model;

public enum SourceType {
    YOUTH_SEOUL_CRAWL("청년 서울"),
    BOKJIRO_CENTRAL("복지로"),
    YOUTH_CENTER("온통청년");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
