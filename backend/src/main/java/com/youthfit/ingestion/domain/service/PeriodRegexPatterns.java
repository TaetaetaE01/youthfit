package com.youthfit.ingestion.domain.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodRegexPatterns {

    public record Hit(LocalDate start, LocalDate end, PatternKind kind, String matchedText) {}

    public enum PatternKind { FULL_RANGE, YEAR_INHERIT, SAME_MONTH, DEADLINE_ONLY, START_ONLY }

    private static final String SEP = "(?:\\s*[.\\-/]\\s*|\\s*년\\s*|\\s*월\\s*)";
    private static final String Y4 = "(20\\d{2})";
    private static final String M2 = "(\\d{1,2})";
    private static final String D2 = "(\\d{1,2})";
    private static final String TAIL = "(?:\\s*일\\.?|\\s*\\([월화수목금토일]\\)|\\s*\\d{1,2}:\\d{2}|\\s*오[전후])*";
    private static final String ARROW = "\\s*[~〜∼\\-]\\s*";

    private static final Pattern FULL_RANGE = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + TAIL + ARROW + Y4 + SEP + M2 + SEP + D2 + TAIL);

    // 연도 상속: 종료에 4자리 연도가 없음
    private static final Pattern YEAR_INHERIT = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + TAIL + ARROW + "(?!20\\d{2})" + M2 + SEP + D2 + TAIL);

    // 동월 단축형: 종료가 D만
    private static final Pattern SAME_MONTH = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + TAIL + ARROW + "(?!20\\d{2})(?!\\d{1,2}\\s*[.\\-/월])" + D2 + "(?:\\s*일\\.?)?");

    // 단일 마감: (마감일: 접두) OR (~ 접두 + 후행 키워드) OR (날짜 + 후행 키워드)
    // ~ 접두 단독은 부족(범위 중간일 수 있음). 후행 키워드 단독은 단일 마감으로 해석.
    private static final Pattern DEADLINE_ONLY = Pattern.compile(
            "(?:마감(?:일)?\\s*[:：]?\\s*" + Y4 + SEP + M2 + SEP + D2 + "\\s*(?:까지|마감|이내)?"
            + "|[~〜∼\\-]\\s*" + Y4 + SEP + M2 + SEP + D2 + "\\s*(?:까지|마감|이내)"
            + "|" + Y4 + SEP + M2 + SEP + D2 + "\\s*(?:까지|마감|이내))");

    // 단일 시작: 날짜 + 부터
    private static final Pattern START_ONLY = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + "\\s*부터");

    public static List<Hit> findAll(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Hit> hits = new ArrayList<>();
        scanFullRange(text, hits);
        scanYearInherit(text, hits);
        scanSameMonth(text, hits);
        scanDeadlineOnly(text, hits);
        scanStartOnly(text, hits);
        return hits;
    }

    private static void scanFullRange(String text, List<Hit> out) {
        Matcher m = FULL_RANGE.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            LocalDate e = toDate(m.group(4), m.group(5), m.group(6));
            if (s != null && e != null && !e.isBefore(s)) {
                out.add(new Hit(s, e, PatternKind.FULL_RANGE, m.group()));
            }
        }
    }

    private static void scanYearInherit(String text, List<Hit> out) {
        Matcher m = YEAR_INHERIT.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            LocalDate e = toDate(m.group(1), m.group(4), m.group(5));
            if (s != null && e != null && !e.isBefore(s)) {
                out.add(new Hit(s, e, PatternKind.YEAR_INHERIT, m.group()));
            }
        }
    }

    private static void scanSameMonth(String text, List<Hit> out) {
        Matcher m = SAME_MONTH.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            LocalDate e = toDate(m.group(1), m.group(2), m.group(4));
            if (s != null && e != null && !e.isBefore(s)) {
                out.add(new Hit(s, e, PatternKind.SAME_MONTH, m.group()));
            }
        }
    }

    private static void scanDeadlineOnly(String text, List<Hit> out) {
        Matcher m = DEADLINE_ONLY.matcher(text);
        while (m.find()) {
            String y = firstNonNull(m.group(1), m.group(4), m.group(7));
            String mo = firstNonNull(m.group(2), m.group(5), m.group(8));
            String d = firstNonNull(m.group(3), m.group(6), m.group(9));
            LocalDate e = toDate(y, mo, d);
            if (e != null) out.add(new Hit(null, e, PatternKind.DEADLINE_ONLY, m.group()));
        }
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null) return v;
        return null;
    }

    private static void scanStartOnly(String text, List<Hit> out) {
        Matcher m = START_ONLY.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            if (s != null) out.add(new Hit(s, null, PatternKind.START_ONLY, m.group()));
        }
    }

    private static LocalDate toDate(String y, String mo, String d) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private PeriodRegexPatterns() {}
}
