package com.youthfit.ingestion.domain.service.source;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.application.port.PeriodCandidateSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BodyLabeledRegexSource implements PeriodCandidateSource {
    private final LabeledRegexExtractor extractor;

    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        return extractor.candidatesInLabeledWindows(ctx.body(), PeriodSource.BODY_LABELED);
    }
}
