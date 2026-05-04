package com.youthfit.user.domain.model;

public enum EmploymentKind {
    EMPLOYEE("재직자"),
    SELF_EMPLOYED("자영업자"),
    UNEMPLOYED("미취업자"),
    FREELANCER("프리랜서"),
    DAILY_WORKER("일용근로자"),
    ENTREPRENEUR("(예비)창업자"),
    PART_TIME("단기근로자"),
    FARMER("영농종사자"),
    OTHER("기타");

    private final String displayName;

    EmploymentKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
