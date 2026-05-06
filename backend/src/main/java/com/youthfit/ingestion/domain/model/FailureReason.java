package com.youthfit.ingestion.domain.model;

public enum FailureReason {
    VALIDATION,
    PARSING,
    MAPPING,
    DEDUPLICATION_CONFLICT,
    OTHER;

    /**
     * 예외 타입을 분류 enum 으로 매핑.
     * receivePolicy 에서 catch 한 exception 을 분류한다.
     */
    public static FailureReason classify(Throwable t) {
        if (t == null) return OTHER;
        Class<?> c = t.getClass();
        String name = c.getName();
        if (name.contains("IllegalArgumentException")) return VALIDATION;
        if (name.contains("Validation") || name.contains("Constraint")) return VALIDATION;
        if (name.contains("Json") || name.contains("Parse")) return PARSING;
        if (name.contains("Mapping") || name.contains("Conversion")) return MAPPING;
        if (name.contains("Duplicate") || name.contains("UniqueConstraint")) return DEDUPLICATION_CONFLICT;
        // chain 조사 (cause)
        Throwable cause = t.getCause();
        if (cause != null && cause != t) return classify(cause);
        return OTHER;
    }
}
