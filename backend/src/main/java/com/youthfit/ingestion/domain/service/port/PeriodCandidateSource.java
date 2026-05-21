package com.youthfit.ingestion.domain.service.port;

import com.youthfit.ingestion.domain.model.PeriodCandidate;

import java.util.List;

public interface PeriodCandidateSource {
    List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx);
}
