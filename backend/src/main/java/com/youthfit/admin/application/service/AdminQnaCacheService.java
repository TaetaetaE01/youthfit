package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.request.QnaCacheLookupListQuery;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDetailResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupKpiResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupSummaryResponse;
import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.qna.infrastructure.persistence.QnaQuestionCacheJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQnaCacheService {

    private final QnaCacheLookupLogRepository repository;
    private final QnaQuestionCacheJpaRepository questionCacheRepository;
    private final QnaProperties qnaProperties;

    public QnaCacheLookupKpiResponse kpi() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);
        LocalDateTime sevenDaysAgo = today.minusDays(7);

        Object[] r = repository.aggregateKpi(today, yesterday, sevenDaysAgo);
        long todayTotal    = ((Number) r[0]).longValue();
        long todayHits     = ((Number) r[1]).longValue();
        long yestTotal     = ((Number) r[2]).longValue();
        long yestHits      = ((Number) r[3]).longValue();
        long sevenHits     = ((Number) r[4]).longValue();
        double sevenAvgSim = ((Number) r[5]).doubleValue();

        BigDecimal todayHitRate     = ratio(todayHits, todayTotal);
        BigDecimal yesterdayHitRate = ratio(yestHits, yestTotal);
        BigDecimal estSavings       = qnaProperties.cache().estimatedSavingsPerHitUsd()
                .multiply(BigDecimal.valueOf(sevenHits))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal avgSim           = BigDecimal.valueOf(sevenAvgSim).setScale(4, RoundingMode.HALF_UP);
        long sevenTotal             = todayTotal + yestTotal;

        return new QnaCacheLookupKpiResponse(
                todayHitRate, yesterdayHitRate, avgSim, estSavings,
                sevenHits, sevenTotal
        );
    }

    private BigDecimal ratio(long num, long denom) {
        if (denom == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(num)
                .divide(BigDecimal.valueOf(denom), 4, RoundingMode.HALF_UP);
    }

    public List<QnaCacheLookupDailyStatsResponse> dailyStats(int days) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);
        return repository.aggregateDaily(from, to).stream()
                .map(row -> new QnaCacheLookupDailyStatsResponse(
                        LocalDate.parse((String) row[0]),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        BigDecimal.valueOf(((Number) row[4]).doubleValue()).setScale(4, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    public Page<QnaCacheLookupSummaryResponse> list(QnaCacheLookupListQuery q) {
        return repository.search(
                q.result(), q.policyId(), q.from(), q.to(),
                PageRequest.of(q.page(), q.size())
        ).map(this::toSummary);
    }

    public QnaCacheLookupDetailResponse detail(Long id) {
        QnaCacheLookupLog lookupLog = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lookup log not found: " + id));

        String matchedQ = null;
        String matchedAnswerExcerpt = null;
        if (lookupLog.getMatchedCachedId() != null) {
            var cachedOpt = questionCacheRepository.findById(lookupLog.getMatchedCachedId());
            if (cachedOpt.isPresent()) {
                var cached = cachedOpt.get();
                matchedQ = cached.getQuestionText();
                String ans = cached.getAnswer();
                matchedAnswerExcerpt = ans == null ? null
                        : (ans.length() > 200 ? ans.substring(0, 200) + "…" : ans);
            }
        }

        return new QnaCacheLookupDetailResponse(
                lookupLog.getId(), lookupLog.getLookedUpAt(), lookupLog.getResult(), lookupLog.getPolicyId(),
                lookupLog.getQuestionText(), lookupLog.getNormalizedText(),
                lookupLog.getMatchedCachedId(), matchedQ, matchedAnswerExcerpt,
                lookupLog.getSimilarityScore(), lookupLog.getThresholdApplied(), lookupLog.isLlmCallMade()
        );
    }

    public void exportCsv(QnaCacheLookupListQuery q, Writer out) {
        List<QnaCacheLookupLog> rows = repository.exportFiltered(
                q.result(), q.policyId(), q.from(), q.to(),
                PageRequest.of(0, 5000)
        );
        PrintWriter pw = new PrintWriter(out);
        pw.println("id,looked_up_at,result,policy_id,question_text,similarity_score,matched_cached_id,llm_call_made");
        for (QnaCacheLookupLog r : rows) {
            pw.printf("%d,%s,%s,%d,%s,%s,%s,%s%n",
                    r.getId(),
                    r.getLookedUpAt(),
                    r.getResult(),
                    r.getPolicyId(),
                    csvEscape(r.getQuestionText()),
                    r.getSimilarityScore() == null ? "" : r.getSimilarityScore(),
                    r.getMatchedCachedId() == null ? "" : r.getMatchedCachedId(),
                    r.isLlmCallMade()
            );
        }
        pw.flush();
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private QnaCacheLookupSummaryResponse toSummary(QnaCacheLookupLog lookupLog) {
        String excerpt = lookupLog.getQuestionText().length() > 50
                ? lookupLog.getQuestionText().substring(0, 50) + "…"
                : lookupLog.getQuestionText();
        return new QnaCacheLookupSummaryResponse(
                lookupLog.getId(), lookupLog.getLookedUpAt(), lookupLog.getResult(), lookupLog.getPolicyId(),
                excerpt, lookupLog.getSimilarityScore(), lookupLog.getMatchedCachedId()
        );
    }
}
