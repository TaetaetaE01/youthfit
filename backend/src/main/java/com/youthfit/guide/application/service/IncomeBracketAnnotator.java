package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IncomeBracketAnnotator {

    private static final Logger log = LoggerFactory.getLogger(IncomeBracketAnnotator.class);

    // group(1)=중위소득 percent, group(2)=차상위 마커, group(3)=도시근로자 percent
    private static final Pattern COMBINED_PATTERN = Pattern.compile(
            "(?:(?:기준\\s*)?중위소득(?:의)?\\s*(\\d+)\\s*%(?:\\s*이내|\\s*이하|\\s*까지)?)" +
            "|(?:(차상위)(?:계층)?(?:\\s*이하|\\s*이내)?)" +
            "|(?:(?:전년도\\s*|작년\\s*)?도시\\s*(?:근로자|직장인)\\s*가구\\s*(?:의|당)?\\s*" +
                    "(?:한\\s*달\\s*)?(?:평균|월평균|월\\s*평균)\\s*소득(?:의)?\\s*(\\d+)\\s*%" +
                    "(?:\\s*이내|\\s*이하|\\s*까지)?)");

    // 환산값이 이미 붙은 형태만 스킵 대상으로 인식.
    // 예: "월 약 256만원", "2026년 기준 1인 가구 월 약 128만원".
    private static final Pattern EXISTING_AMOUNT_PATTERN = Pattern.compile(
            "월\\s*약\\s*\\d+\\s*만원|\\d{4}\\s*년\\s*기준[^)]*만원");

    // 컨텍스트 키워드 → 가구원수 매핑.
    // 항목 텍스트 안에 다음 키워드가 있으면 해당 가구원수의 환산값을 표시한다.
    private static final List<KeywordRule> KEYWORD_RULES = List.of(
            new KeywordRule(List.of("청년", "단독"), List.of(HouseholdSize.ONE)),
            new KeywordRule(List.of("신혼부부", "신혼", "맞벌이", "부부"), List.of(HouseholdSize.TWO)),
            new KeywordRule(List.of("다자녀", "노부모"), List.of(HouseholdSize.THREE, HouseholdSize.FOUR)),
            new KeywordRule(List.of("3인"), List.of(HouseholdSize.THREE)),
            new KeywordRule(List.of("4인"), List.of(HouseholdSize.FOUR))
    );

    // 기본 가구원수 (키워드 매칭 없을 때).
    private static final List<HouseholdSize> DEFAULT_SIZES =
            List.of(HouseholdSize.ONE, HouseholdSize.TWO);

    private record KeywordRule(List<String> keywords, List<HouseholdSize> sizes) {}

    public GuideContent annotate(GuideContent content, IncomeBracketReference reference, Long policyId) {
        if (reference == null
                || (reference.medianIncome().isEmpty()
                    && reference.nearPoor().isEmpty()
                    && reference.urbanWorkerIncome().isEmpty())) {
            log.info("empty income bracket reference, skipping annotation: policyId={}, year={}",
                    policyId, reference == null ? "null" : reference.year());
            return content;
        }
        String oneLine = annotateText(content.oneLineSummary(), reference, policyId);
        List<GuideHighlight> highlights = content.highlights().stream()
                .map(h -> new GuideHighlight(annotateText(h.text(), reference, policyId), h.sourceField(), h.attachmentRef()))
                .toList();
        GuidePairedSection target = annotatePaired(content.target(), reference, policyId);
        GuidePairedSection criteria = annotatePaired(content.criteria(), reference, policyId);
        GuidePairedSection contentSection = annotatePaired(content.content(), reference, policyId);
        List<GuidePitfall> pitfalls = content.pitfalls().stream()
                .map(p -> new GuidePitfall(annotateText(p.text(), reference, policyId), p.sourceField(), p.attachmentRef()))
                .toList();
        return new GuideContent(oneLine, highlights, target, criteria, contentSection, pitfalls);
    }

    private GuidePairedSection annotatePaired(GuidePairedSection section,
                                              IncomeBracketReference reference,
                                              Long policyId) {
        if (section == null) return null;
        List<GuideGroup> newGroups = section.groups().stream()
                .map(g -> new GuideGroup(g.label(),
                        g.items().stream()
                                .map(item -> annotateText(item, reference, policyId))
                                .toList()))
                .toList();
        return new GuidePairedSection(newGroups);
    }

    private String annotateText(String text, IncomeBracketReference reference, Long policyId) {
        if (text == null || text.isEmpty()) return text;
        if (EXISTING_AMOUNT_PATTERN.matcher(text).find()) return text;

        // 항목 단위로 가구원수 결정 (청년/신혼/다자녀 등 키워드 기반).
        List<HouseholdSize> sizes = resolveSizesFromContext(text);

        // 같은 텍스트 안에서 같은 (종류, percent) 조합은 한 번만 환산값 부착.
        Set<Integer> processedMedianPercents = new HashSet<>();
        Set<Integer> processedUrbanPercents = new HashSet<>();
        boolean nearPoorProcessed = false;

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        Matcher m = COMBINED_PATTERN.matcher(text);
        while (m.find()) {
            result.append(text, lastEnd, m.end());
            String medianGroup = m.group(1);
            String nearPoorGroup = m.group(2);
            String urbanGroup = m.group(3);
            String suffix = null;
            if (medianGroup != null) {
                int percent = Integer.parseInt(medianGroup);
                if (processedMedianPercents.add(percent)) {
                    suffix = formatMedianSuffix(reference, percent, sizes);
                    if (suffix == null) {
                        log.warn("unmapped median income percent: percent={}, year={}, policyId={}, snippet={}",
                                percent, reference.year(), policyId, snippet(text, 60));
                    }
                }
            } else if (urbanGroup != null) {
                int percent = Integer.parseInt(urbanGroup);
                if (processedUrbanPercents.add(percent)) {
                    suffix = formatUrbanWorkerSuffix(reference, percent, sizes);
                    if (suffix == null) {
                        log.warn("unmapped urban-worker percent: percent={}, year={}, policyId={}, snippet={}",
                                percent, reference.year(), policyId, snippet(text, 60));
                    }
                }
            } else if (nearPoorGroup != null && !nearPoorProcessed) {
                nearPoorProcessed = true;
                suffix = formatNearPoorSuffix(reference);
            }
            if (suffix != null) result.append(suffix);
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    /**
     * 항목 텍스트의 키워드를 보고 환산값에 표시할 가구원수 목록을 결정한다.
     * 매칭되는 키워드가 없으면 기본값(1·2인) 사용.
     * 가구원수 순서 보장(LinkedHashSet → 정렬된 enum 순).
     */
    List<HouseholdSize> resolveSizesFromContext(String text) {
        Set<HouseholdSize> matched = new LinkedHashSet<>();
        for (KeywordRule rule : KEYWORD_RULES) {
            for (String keyword : rule.keywords) {
                if (text.contains(keyword)) {
                    matched.addAll(rule.sizes);
                    break;
                }
            }
        }
        if (matched.isEmpty()) return DEFAULT_SIZES;
        // enum 정의 순(ONE..EIGHT) 으로 정렬
        List<HouseholdSize> sorted = new ArrayList<>(matched);
        sorted.sort((a, b) -> Integer.compare(a.count(), b.count()));
        return sorted;
    }

    private String snippet(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String formatNearPoorSuffix(IncomeBracketReference reference) {
        Long one = reference.nearPoor().get(HouseholdSize.ONE);
        if (one == null) return null;
        return String.format(" (%d년 기준 1인 가구 월 약 %d만원 이하)",
                reference.year(), toManwon(one));
    }

    private String formatMedianSuffix(IncomeBracketReference reference, int percent,
                                      List<HouseholdSize> sizes) {
        return formatSuffix(reference, sizes,
                size -> reference.findAmount(size, percent));
    }

    private String formatUrbanWorkerSuffix(IncomeBracketReference reference, int percent,
                                           List<HouseholdSize> sizes) {
        return formatSuffix(reference, sizes,
                size -> reference.findUrbanWorkerAmount(size, percent));
    }

    private String formatSuffix(IncomeBracketReference reference,
                                List<HouseholdSize> sizes,
                                java.util.function.Function<HouseholdSize, Optional<Long>> amountFn) {
        List<String> parts = new ArrayList<>();
        for (HouseholdSize size : sizes) {
            Optional<Long> amt = amountFn.apply(size);
            amt.ifPresent(v -> parts.add(size.count() + "인 가구 월 약 " + toManwon(v) + "만원"));
        }
        if (parts.isEmpty()) return null;
        return " (" + reference.year() + "년 기준 " + String.join(", ", parts) + ")";
    }

    private long toManwon(long won) {
        return Math.round(won / 10000.0);
    }
}
