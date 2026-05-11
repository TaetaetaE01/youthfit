package com.youthfit.rag.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeywordExtractor {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[가-힣A-Za-z0-9]{2,}");

    private final Set<String> stopwords;
    private final int maxKeywords;

    public KeywordExtractor(Set<String> stopwords, int maxKeywords) {
        this.stopwords = stopwords == null ? Set.of() : Set.copyOf(stopwords);
        this.maxKeywords = maxKeywords > 0 ? maxKeywords : 5;
    }

    public List<String> extract(String query) {
        if (query == null || query.isBlank()) return List.of();

        Matcher matcher = TOKEN_PATTERN.matcher(query);
        Set<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (stopwords.contains(token)) continue;
            seen.add(token);
            if (seen.size() >= maxKeywords) break;
        }
        return new ArrayList<>(seen);
    }
}
