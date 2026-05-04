package com.youthfit.user.domain.model;

public enum Education implements LabeledEnum {
    UNDER_HIGH("고졸 미만"),
    HIGH_SCHOOL_IN("고교 재학"),
    HIGH_SCHOOL_EXPECTED("고졸 예정"),
    HIGH_SCHOOL_GRAD("고교 졸업"),
    COLLEGE_IN("대학 재학"),
    COLLEGE_EXPECTED("대졸 예정"),
    COLLEGE_GRAD("대학 졸업"),
    GRADUATE("석·박사"),
    OTHER("기타");

    private final String displayName;

    Education(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
