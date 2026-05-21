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
public class AttachmentLabeledRegexSource implements PeriodCandidateSource {
    private final LabeledRegexExtractor extractor;

    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        if (ctx.attachmentTexts() == null || ctx.attachmentTexts().isEmpty()) return List.of();
        return ctx.attachmentTexts().stream()
                .flatMap(t -> extractor.candidatesInLabeledWindows(t, PeriodSource.ATTACHMENT_LABELED).stream())
                .map(c -> new PeriodCandidate(
                        c.start(), c.end(), c.source(),
                        c.confidence() - 0.10,
                        c.evidence()))
                .toList();
    }
}
