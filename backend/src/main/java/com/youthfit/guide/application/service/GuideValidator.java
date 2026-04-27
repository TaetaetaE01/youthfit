package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GuideValidator {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "(\\d+\\s*(?:만원|원|개월|년|세|%|명))"
    );

    private static final Pattern FRIENDLY_TONE = Pattern.compile(
            "(?:해요|예요|에요|드려요|이에요|어요|아요)(?:[.!?\\s]|$)"
    );

    public List<String> findMissingNumericTokens(String originalText, GuideContent content) {
        if (originalText == null || originalText.isBlank()) return List.of();

        Set<String> originalTokens = extractTokens(originalText);
        String renderedText = renderContentText(content);
        Set<String> renderedTokens = extractTokens(renderedText);

        List<String> missing = new ArrayList<>();
        for (String token : originalTokens) {
            if (!renderedTokens.contains(token)) {
                missing.add(token);
            }
        }
        return missing;
    }

    public boolean containsFriendlyTone(GuideContent content) {
        return FRIENDLY_TONE.matcher(renderContentText(content)).find();
    }

    private Set<String> extractTokens(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher m = NUMERIC_TOKEN.matcher(text);
        while (m.find()) {
            tokens.add(m.group(1).replaceAll("\\s+", ""));
        }
        return tokens;
    }

    private String renderContentText(GuideContent content) {
        StringBuilder sb = new StringBuilder();
        sb.append(content.oneLineSummary()).append(" ");
        appendSection(sb, content.target());
        appendSection(sb, content.criteria());
        appendSection(sb, content.content());
        content.pitfalls().forEach(p -> sb.append(p.text()).append(" "));
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, GuidePairedSection section) {
        if (section == null) return;
        section.items().forEach(item -> sb.append(item).append(" "));
    }
}
