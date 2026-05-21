package com.youthfit.ingestion.domain.service.source;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.ingestion.domain.service.port.PeriodCandidateSource;
import com.youthfit.ingestion.domain.service.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class N8nApplyFieldsSource implements PeriodCandidateSource {
    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        if (ctx.n8nApplyStart() == null && ctx.n8nApplyEnd() == null) return List.of();
        boolean both = ctx.n8nApplyStart() != null && ctx.n8nApplyEnd() != null;
        return List.of(new PeriodCandidate(
                ctx.n8nApplyStart(), ctx.n8nApplyEnd(),
                PeriodSource.N8N,
                both ? 0.60 : 0.40,
                "n8n applyStart/applyEnd"
        ));
    }
}
