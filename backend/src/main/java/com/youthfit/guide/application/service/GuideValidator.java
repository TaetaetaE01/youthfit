package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuideSourceField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class GuideValidator {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "(\\d+\\s*(?:만원|원|개월|년|세|%|명))"
    );

    private static final Pattern FRIENDLY_TONE = Pattern.compile(
            "(?:해요|예요|에요|드려요|이에요|어요|아요)(?:[.!?\\s]|$)"
    );

    private static final List<String> CATEGORY_KEYWORDS = List.of(
            "차상위", "일반공급", "특별공급", "신혼부부",
            "생애최초", "맞벌이", "다자녀", "기혼", "미혼"
    );

    public record ValidationReport(
            boolean hasGroupMixViolation,
            boolean hasInsufficientHighlights,
            List<String> feedbackMessages
    ) {

        public boolean hasRetryTrigger() {
            return hasGroupMixViolation || hasInsufficientHighlights;
        }

        public int violationCount() {
            int n = 0;
            if (hasGroupMixViolation) n++;
            if (hasInsufficientHighlights) n++;
            return n;
        }
    }

    public ValidationReport validate(GuideContent content) {
        boolean groupMix = checkGroupMix(content);
        boolean insufficientHighlights = content.highlights().size() < 3;

        List<String> feedback = new ArrayList<>();
        if (groupMix) {
            feedback.add("일부 group의 items에 분류 키워드(차상위/일반공급/특별공급/신혼부부 등)가 2종 이상 섞여 있다. group을 분리하고 label에 분류명을 명시할 것.");
        }
        if (insufficientHighlights) {
            feedback.add("highlights가 " + content.highlights().size() + "개. 최소 3개 이상 작성할 것 (긍정·중립·차별점).");
        }

        return new ValidationReport(groupMix, insufficientHighlights, feedback);
    }

    public <T> List<T> filterInvalidSourceFields(
            List<T> items,
            Set<GuideSourceField> nonEmptyFields,
            Function<T, GuideSourceField> sourceFieldExtractor) {
        return items.stream()
                .filter(it -> nonEmptyFields.contains(sourceFieldExtractor.apply(it)))
                .toList();
    }

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

    private boolean checkGroupMix(GuideContent content) {
        return Stream.of(content.target(), content.criteria(), content.content())
                .filter(s -> s != null)
                .flatMap(s -> s.groups().stream())
                .anyMatch(g -> {
                    String joined = String.join(" ", g.items());
                    long count = CATEGORY_KEYWORDS.stream().filter(joined::contains).count();
                    return count >= 2;
                });
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
        section.groups().forEach(group -> {
            if (group.label() != null) {
                sb.append(group.label()).append(" ");
            }
            group.items().forEach(item -> sb.append(item).append(" "));
        });
    }
}
