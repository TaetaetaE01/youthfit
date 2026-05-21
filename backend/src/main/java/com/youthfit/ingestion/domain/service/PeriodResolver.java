package com.youthfit.ingestion.domain.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.ingestion.application.port.*;
import com.youthfit.ingestion.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeriodResolver {

    private static final Logger log = LoggerFactory.getLogger(PeriodResolver.class);

    private static final double AMBIGUOUS_BELOW = 0.70;
    private static final double FINAL_FLOOR = 0.55;
    private static final double GROUP_BONUS_PER_EXTRA = 0.05;
    private static final double GROUP_BONUS_MAX = 0.15;

    private final List<PeriodCandidateSource> sources;
    private final PeriodLlmDirectExtractor llmDirect;
    private final PeriodLlmDisambiguator llmDisambiguator;
    private final CostGuard costGuard;

    public ResolvedPeriod resolve(PeriodExtractionContext ctx) {
        List<PeriodCandidate> candidates = collect(ctx);

        if (candidates.isEmpty() && !costGuard.enabled()) {
            Optional<PeriodCandidate> direct = llmDirect.extract(ctx.title(), ctx.body());
            direct.ifPresent(candidates::add);
        }

        if (candidates.isEmpty()) {
            log.info("period-resolve externalId={} result=empty (no candidates)", ctx.externalId());
            return ResolvedPeriod.empty();
        }

        List<PeriodCandidate> grouped = mergeByRange(candidates);
        PeriodCandidate best = grouped.stream()
                .max(Comparator.comparingDouble(PeriodCandidate::confidence))
                .orElseThrow();

        if (best.confidence() < AMBIGUOUS_BELOW && grouped.size() >= 2 && !costGuard.enabled()) {
            String snippet = buildSnippet(grouped);
            Optional<PeriodCandidate> chosen = llmDisambiguator.choose(snippet, grouped);
            if (chosen.isPresent()) best = chosen.get();
        }

        if (best.confidence() < FINAL_FLOOR) {
            log.info("period-resolve externalId={} result=empty (below floor) candidates={} best_conf={}",
                    ctx.externalId(), grouped.size(), best.confidence());
            return ResolvedPeriod.empty();
        }

        log.info("period-resolve externalId={} source={} confidence={} start={} end={} evidence=\"{}\"",
                ctx.externalId(), best.source(), best.confidence(), best.start(), best.end(), best.evidence());
        return ResolvedPeriod.from(best);
    }

    private List<PeriodCandidate> collect(PeriodExtractionContext ctx) {
        return sources.stream()
                .flatMap(s -> s.findCandidates(ctx).stream())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<PeriodCandidate> mergeByRange(List<PeriodCandidate> cs) {
        Map<String, List<PeriodCandidate>> grouped = cs.stream()
                .collect(Collectors.groupingBy(c -> String.valueOf(c.start()) + "_" + c.end()));
        List<PeriodCandidate> out = new ArrayList<>();
        for (List<PeriodCandidate> group : grouped.values()) {
            PeriodCandidate max = group.stream()
                    .max(Comparator.comparingDouble(PeriodCandidate::confidence))
                    .orElseThrow();
            double bonus = Math.min(GROUP_BONUS_MAX, GROUP_BONUS_PER_EXTRA * (group.size() - 1));
            out.add(new PeriodCandidate(
                    max.start(), max.end(), max.source(),
                    Math.min(1.0, max.confidence() + bonus),
                    max.evidence()
            ));
        }
        return out;
    }

    private String buildSnippet(List<PeriodCandidate> cs) {
        StringBuilder sb = new StringBuilder();
        for (PeriodCandidate c : cs) {
            sb.append("[").append(c.source()).append("] ").append(c.evidence()).append("\n");
        }
        return sb.toString();
    }
}
