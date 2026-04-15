package com.youthfit.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class DateTimeUtil {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private DateTimeUtil() {
    }

    public static LocalDateTime nowKst() {
        return LocalDateTime.now(KST);
    }

    public static LocalDate todayKst() {
        return LocalDate.now(KST);
    }
}
