package com.youthfit.ingestion.domain.service;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LabeledRegexExtractor {

    private static final int WINDOW_MAX_CHARS = 200;
    private static final int EVIDENCE_MAX = 200;

    public List<PeriodCandidate> candidatesInLabeledWindows(String body, PeriodSource source) {
        if (body == null || body.isBlank()) return List.of();
        List<PeriodLabels.LabelMatch> labels = PeriodLabels.matchAll(body);

        // 라벨이 전혀 없으면 본문 전체를 라벨 윈도우로 간주한다.
        // (DEADLINE_ONLY 같은 패턴은 `마감` 키워드를 자체 접두로 가지므로 라벨 없이도 자기 식별이 가능하다.)
        if (labels.isEmpty()) {
            List<PeriodCandidate> out = new ArrayList<>();
            for (PeriodRegexPatterns.Hit hit : PeriodRegexPatterns.findAll(body)) {
                double conf = labeledConfidence(hit.kind());
                out.add(new PeriodCandidate(
                        hit.start(), hit.end(), source, conf,
                        clipEvidence(hit.matchedText())));
            }
            return out;
        }

        List<PeriodCandidate> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            PeriodLabels.LabelMatch lm = labels.get(i);
            if (lm.negative()) continue;

            int wStart = lm.end();
            int wEnd = computeWindowEnd(body, wStart,
                    i + 1 < labels.size() ? labels.get(i + 1).start() : body.length());
            String window = body.substring(wStart, wEnd);

            for (PeriodRegexPatterns.Hit hit : PeriodRegexPatterns.findAll(window)) {
                double conf = labeledConfidence(hit.kind());
                out.add(new PeriodCandidate(
                        hit.start(), hit.end(), source, conf,
                        clipEvidence(lm.label() + ": " + hit.matchedText())));
            }
        }
        return out;
    }

    public List<PeriodCandidate> candidatesInBodyMasked(String body, PeriodSource source) {
        if (body == null || body.isBlank()) return List.of();
        String masked = maskNegativeWindows(body);
        List<PeriodCandidate> out = new ArrayList<>();
        for (PeriodRegexPatterns.Hit hit : PeriodRegexPatterns.findAll(masked)) {
            double conf = genericConfidence(hit.kind());
            out.add(new PeriodCandidate(
                    hit.start(), hit.end(), source, conf,
                    clipEvidence(hit.matchedText())));
        }
        return out;
    }

    private String maskNegativeWindows(String body) {
        List<PeriodLabels.LabelMatch> labels = PeriodLabels.matchAll(body);
        StringBuilder sb = new StringBuilder(body);
        for (int i = 0; i < labels.size(); i++) {
            PeriodLabels.LabelMatch lm = labels.get(i);
            if (!lm.negative()) continue;
            int wStart = lm.end();
            int wEnd = computeWindowEnd(body, wStart,
                    i + 1 < labels.size() ? labels.get(i + 1).start() : body.length());
            for (int p = wStart; p < wEnd; p++) {
                sb.setCharAt(p, ' ');
            }
        }
        return sb.toString();
    }

    private int computeWindowEnd(String body, int start, int nextLabelStart) {
        int lineEnd = findLineEnd(body, start);
        int hard = Math.min(body.length(), start + WINDOW_MAX_CHARS);
        return Math.min(Math.min(nextLabelStart, lineEnd), hard);
    }

    private int findLineEnd(String body, int from) {
        int i = body.indexOf('\n', from);
        return i == -1 ? body.length() : i;
    }

    private double labeledConfidence(PeriodRegexPatterns.PatternKind k) {
        return switch (k) {
            case FULL_RANGE -> 0.85;
            case YEAR_INHERIT, SAME_MONTH -> 0.80;
            case DEADLINE_ONLY, START_ONLY -> 0.65;
        };
    }

    private double genericConfidence(PeriodRegexPatterns.PatternKind k) {
        return switch (k) {
            case FULL_RANGE, YEAR_INHERIT, SAME_MONTH -> 0.45;
            case DEADLINE_ONLY, START_ONLY -> 0.35;
        };
    }

    private String clipEvidence(String s) {
        if (s == null) return null;
        String trimmed = s.replaceAll("\\s+", " ").trim();
        return trimmed.length() > EVIDENCE_MAX ? trimmed.substring(0, EVIDENCE_MAX) : trimmed;
    }
}
