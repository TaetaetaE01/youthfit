package com.youthfit.user.domain.model;

public enum Education {
    UNDER_HIGH("고졸 미만"),
    HIGH_SCHOOL_IN("고등학교 재학"),
    HIGH_SCHOOL_EXPECTED("고등학교 졸업 예정"),
    HIGH_SCHOOL_GRAD("고등학교 졸업"),
    COLLEGE_IN("대학 재학"),
    COLLEGE_EXPECTED("대학 졸업 예정"),
    COLLEGE_GRAD("대학 졸업"),
    GRADUATE("대학원 이상"),
    OTHER("기타");

    private final String displayName;

    Education(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
