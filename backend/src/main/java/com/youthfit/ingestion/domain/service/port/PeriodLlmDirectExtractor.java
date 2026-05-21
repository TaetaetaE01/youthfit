package com.youthfit.ingestion.domain.service.port;

import com.youthfit.ingestion.domain.model.PeriodCandidate;

import java.util.Optional;

public interface PeriodLlmDirectExtractor {
    Optional<PeriodCandidate> extract(String title, String body);
}
