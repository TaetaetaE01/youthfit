package com.youthfit.policy.domain.service;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TitleNormalizer {

    private static final Pattern STRIP = Pattern.compile("[^\\p{Alnum}가-힣]");

    private TitleNormalizer() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        return STRIP.matcher(title.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
