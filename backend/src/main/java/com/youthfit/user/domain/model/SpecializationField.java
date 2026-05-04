package com.youthfit.user.domain.model;

public enum SpecializationField {
    SME("중소기업"),
    WOMAN("여성"),
    BASIC_LIVELIHOOD("기초생활수급자"),
    SINGLE_PARENT("한부모가정"),
    DISABLED("장애인"),
    FARMER("농업인"),
    MILITARY("군 복무"),
    LOCAL_TALENT("지역인재"),
    OTHER("기타");

    private final String displayName;

    SpecializationField(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
