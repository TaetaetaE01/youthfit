package com.youthfit.ingestion.domain.service.port;

import com.youthfit.ingestion.domain.model.PeriodCandidate;

import java.util.List;
import java.util.Optional;

public interface PeriodLlmDisambiguator {
    Optional<PeriodCandidate> choose(String bodySnippet, List<PeriodCandidate> candidates);
}
