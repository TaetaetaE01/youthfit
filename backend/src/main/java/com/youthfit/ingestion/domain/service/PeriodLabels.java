package com.youthfit.ingestion.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodLabels {

    public static final List<String> POSITIVE = List.of(
            "신청기간", "신청 기간", "접수기간", "접수 기간",
            "모집기간", "모집 기간", "공모기간", "공모 기간",
            "사업신청기간", "사업 신청 기간", "신청일정", "신청 일정",
            "신청마감", "신청 마감", "접수마감", "접수 마감",
            "모집마감", "모집 마감", "공모마감", "공모 마감"
    );

    public static final List<String> NEGATIVE = List.of(
            "사업기간", "사업 기간", "운영기간", "운영 기간",
            "수행기간", "수행 기간", "교육기간", "교육 기간",
            "지원기간", "지원 기간"
    );

    private static final Pattern POSITIVE_PATTERN = buildPattern(POSITIVE);
    private static final Pattern NEGATIVE_PATTERN = buildPattern(NEGATIVE);

    private static Pattern buildPattern(List<String> labels) {
        String alt = labels.stream()
                .map(s -> s.replace(" ", "\\s*"))
                .reduce((a, b) -> a + "|" + b).orElseThrow();
        return Pattern.compile("(" + alt + ")\\s*[:：]?", Pattern.UNICODE_CHARACTER_CLASS);
    }

    public record LabelMatch(String label, int start, int end, boolean negative) {}

    public static List<LabelMatch> matchAll(String body) {
        if (body == null || body.isBlank()) return List.of();
        List<LabelMatch> out = new ArrayList<>();
        scan(body, POSITIVE_PATTERN, false, out);
        scan(body, NEGATIVE_PATTERN, true, out);
        out.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return out;
    }

    private static void scan(String body, Pattern p, boolean negative, List<LabelMatch> out) {
        Matcher m = p.matcher(body);
        while (m.find()) {
            out.add(new LabelMatch(m.group(1), m.start(), m.end(), negative));
        }
    }

    private PeriodLabels() {}
}
