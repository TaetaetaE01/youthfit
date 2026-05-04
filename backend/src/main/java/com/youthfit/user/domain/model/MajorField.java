package com.youthfit.user.domain.model;

public enum MajorField implements LabeledEnum {
    HUMANITIES("인문계열"),
    SOCIAL("사회계열"),
    ECONOMICS("상경계열"),
    NATURAL("이학계열"),
    ENGINEERING("공학계열"),
    ARTS("예체능계열"),
    AGRICULTURE("농산업계열"),
    OTHER("기타");

    private final String displayName;

    MajorField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
