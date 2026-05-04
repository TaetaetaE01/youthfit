package com.youthfit.user.domain.model;

public enum EmploymentKind {
    EMPLOYEE("직장인"),
    SELF_EMPLOYED("자영업"),
    UNEMPLOYED("미취업"),
    FREELANCER("프리랜서"),
    DAILY_WORKER("일용직"),
    ENTREPRENEUR("창업가"),
    PART_TIME("아르바이트"),
    FARMER("농업인"),
    OTHER("기타");

    private final String displayName;

    EmploymentKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
