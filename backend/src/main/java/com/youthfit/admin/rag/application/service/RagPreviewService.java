package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.command.HybridOverrideCommand;
import com.youthfit.admin.rag.application.dto.command.RagPreviewCommand;
import com.youthfit.admin.rag.application.dto.result.PreviewSideResult;
import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;
import com.youthfit.admin.rag.application.dto.result.RankChangeResult;
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagPreviewService {

    private static final Logger log = LoggerFactory.getLogger(RagPreviewService.class);

    private final RagSearchService ragSearchService;
    private final EmbeddingProvider embeddingProvider;
    private final RankChangeCalculator rankChangeCalculator;
    private final RagPreviewRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public RagPreviewResult preview(RagPreviewCommand cmd) {
        if (!rateLimiter.tryAcquire(cmd.userId())) {
            throw new RagPreviewRateLimitException();
        }

        SearchChunksCommand searchCmd = new SearchChunksCommand(cmd.policyId(), cmd.query());
        float[] embedding = embeddingProvider.embed(cmd.query());

        RagSearchTrace baseline = ragSearchService.searchRelevantChunksWithTrace(
                searchCmd, embedding, null);
        HybridSearchOverrides overrides = toOverrides(cmd.candidate());
        RagSearchTrace candidate = ragSearchService.searchRelevantChunksWithTrace(
                searchCmd, embedding, overrides);

        List<RankChangeResult> changes =
                rankChangeCalculator.compute(baseline.merged(), candidate.merged());

        logAudit(cmd, baseline, candidate, changes);

        return new RagPreviewResult(
                cmd.policyId(),
                cmd.query(),
                baseline.usedKeywords(),
                new PreviewSideResult(baseline),
                new PreviewSideResult(candidate),
                changes);
    }

    private HybridSearchOverrides toOverrides(HybridOverrideCommand c) {
        if (c == null) return null;
        return new HybridSearchOverrides(
                c.hybridEnabled(),
                c.topNPerSearch(),
                c.rrfK(),
                c.trigramThreshold(),
                c.keywordBoostEnabled(),
                c.maxKeywords());
    }

    private void logAudit(RagPreviewCommand cmd, RagSearchTrace baseline,
                          RagSearchTrace candidate, List<RankChangeResult> changes) {
        String q = cmd.query() == null ? "" : cmd.query();
        if (q.length() > 200) q = q.substring(0, 200);
        log.info("admin.rag.preview userId={} policyId={} query=\"{}\" "
                        + "baseline={} candidate={} baselineMs={} candidateMs={} "
                        + "baselineHits={} candidateHits={} rankChanges={}",
                cmd.userId(), cmd.policyId(), q,
                baseline.effective(), candidate.effective(),
                baseline.tookMs(), candidate.tookMs(),
                baseline.merged().size(), candidate.merged().size(),
                changes.size());
    }
}
