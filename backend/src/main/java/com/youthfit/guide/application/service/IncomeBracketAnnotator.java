package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IncomeBracketAnnotator {

    private static final Logger log = LoggerFactory.getLogger(IncomeBracketAnnotator.class);

    private static final Pattern COMBINED_PATTERN = Pattern.compile(
            "(?:(?:기준\\s*)?중위소득(?:의)?\\s*(\\d+)\\s*%(?:\\s*이내|\\s*이하|\\s*까지)?)" +
            "|(?:차상위(?:계층)?(?:\\s*이하|\\s*이내)?)");

    private static final Pattern EXISTING_AMOUNT_PATTERN = Pattern.compile("\\d+\\s*만원");

    public GuideContent annotate(GuideContent content, IncomeBracketReference reference, Long policyId) {
        GuidePairedSection criteria = annotatePaired(content.criteria(), reference, policyId);
        return new GuideContent(
                content.oneLineSummary(),
                content.highlights(),
                content.target(),
                criteria,
                content.content(),
                content.pitfalls()
        );
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
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        Matcher m = COMBINED_PATTERN.matcher(text);
        while (m.find()) {
            result.append(text, lastEnd, m.end());
            String percentGroup = m.group(1);
            String suffix;
            if (percentGroup != null) {
                int percent = Integer.parseInt(percentGroup);
                suffix = formatMedianSuffix(reference, percent);
                if (suffix == null) {
                    log.warn("unmapped median income percent: percent={}, year={}, policyId={}, snippet={}",
                            percent, reference.year(), policyId, snippet(text, 60));
                }
            } else {
                suffix = formatNearPoorSuffix(reference);
            }
            if (suffix != null) result.append(suffix);
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
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

    private String formatMedianSuffix(IncomeBracketReference reference, int percent) {
        Optional<Long> one = reference.findAmount(HouseholdSize.ONE, percent);
        Optional<Long> two = reference.findAmount(HouseholdSize.TWO, percent);
        if (one.isEmpty() && two.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(" (").append(reference.year()).append("년 기준 ");
        if (one.isPresent()) sb.append("1인 가구 월 약 ").append(toManwon(one.get())).append("만원");
        if (two.isPresent()) {
            if (one.isPresent()) sb.append(", ");
            sb.append("2인 가구 월 약 ").append(toManwon(two.get())).append("만원");
        }
        sb.append(")");
        return sb.toString();
    }

    private long toManwon(long won) {
        return Math.round(won / 10000.0);
    }
}
