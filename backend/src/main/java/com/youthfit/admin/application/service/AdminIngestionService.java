package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.response.IngestionDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.IngestionFailureDetailResponse;
import com.youthfit.admin.presentation.dto.response.IngestionFailureSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionKpiResponse;
import com.youthfit.admin.presentation.dto.response.IngestionRetryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionSourceSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionStaleSourceResponse;
import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.application.service.RetryFailedIngestionItemUseCase;
import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminIngestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IngestionRunLogRepository runLogRepo;
    private final IngestionItemFailureRepository failureRepo;
    private final RetryFailedIngestionItemUseCase retryUseCase;

    @Value("${youthfit.ingestion.health.stale-threshold-hours:24}")
    private int staleHours;

    @Transactional(readOnly = true)
    public IngestionKpiResponse getKpi() {
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        Instant yesterdayStart = nowKst.toLocalDate().minusDays(1).atStartOfDay(KST).toInstant();
        Instant todayStart = nowKst.toLocalDate().atStartOfDay(KST).toInstant();
        Instant sevenDaysAgo = nowKst.toLocalDate().minusDays(7).atStartOfDay(KST).toInstant();

        Map<String, Object> yesterday = runLogRepo.sumBetween(yesterdayStart, todayStart);
        Map<String, Object> week = runLogRepo.sumBetween(sevenDaysAgo, nowKst.toInstant());

        long yReceived = ((Number) yesterday.getOrDefault("received", 0)).longValue();
        long yFailure = ((Number) yesterday.getOrDefault("failure", 0)).longValue();
        long wReceived = ((Number) week.getOrDefault("received", 0)).longValue();
        long wFailure = ((Number) week.getOrDefault("failure", 0)).longValue();

        BigDecimal avg = wReceived == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(wReceived).divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
        BigDecimal failureRate = wReceived == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(wFailure)
                        .divide(BigDecimal.valueOf(wReceived), 4, RoundingMode.HALF_UP);

        return new IngestionKpiResponse(yReceived, yFailure, avg, failureRate);
    }

    @Transactional(readOnly = true)
    public List<IngestionDailyStatsResponse> getDailyStats(int days) {
        Instant now = Instant.now();
        Instant from = ZonedDateTime.now(KST).toLocalDate().minusDays(days - 1L)
                .atStartOfDay(KST).toInstant();
        List<Object[]> rows = runLogRepo.dailyStats(from, now);
        List<IngestionDailyStatsResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = ((Date) row[0]).toLocalDate();
            String source = (String) row[1];
            long success = ((Number) row[2]).longValue();
            long failure = ((Number) row[3]).longValue();
            long duplicate = ((Number) row[4]).longValue();
            result.add(new IngestionDailyStatsResponse(date, source, success, failure, duplicate));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<IngestionSourceSummaryResponse> getSourceSummaries() {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        List<Object[]> rows = runLogRepo.sourceSummaries(sevenDaysAgo);
        Instant staleThreshold = Instant.now().minus(Duration.ofHours(staleHours));
        List<IngestionSourceSummaryResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String source = (String) row[0];
            Instant lastReceived = ((Timestamp) row[1]).toInstant();
            long weekReceived = ((Number) row[2]).longValue();
            BigDecimal failureRate = (BigDecimal) row[3];
            boolean stale = lastReceived.isBefore(staleThreshold);
            result.add(new IngestionSourceSummaryResponse(
                    source, lastReceived, weekReceived, failureRate, stale));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<IngestionStaleSourceResponse> getStaleSources() {
        Instant threshold = Instant.now().minus(Duration.ofHours(staleHours));
        List<Object[]> rows = runLogRepo.staleSources(threshold);
        Instant now = Instant.now();
        List<IngestionStaleSourceResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String source = (String) row[0];
            Instant lastReceived = ((Timestamp) row[1]).toInstant();
            long hours = Duration.between(lastReceived, now).toHours();
            result.add(new IngestionStaleSourceResponse(source, lastReceived, hours));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<IngestionFailureSummaryResponse> searchFailures(
            String source, FailureReason reason, Instant from, Instant to,
            int page, int size) {
        Page<IngestionItemFailure> p = failureRepo.search(
                source, reason, from, to, PageRequest.of(page, size));
        return p.map(f -> new IngestionFailureSummaryResponse(
                f.getId(), f.getSource(), f.getFailureReason(), f.getSourceItemId(),
                excerpt(f.getErrorMessage(), 120),
                f.getRetryCount(), f.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public IngestionFailureDetailResponse getFailureDetail(Long id) {
        IngestionItemFailure f = failureRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("실패 항목을 찾을 수 없습니다: " + id));
        return new IngestionFailureDetailResponse(
                f.getId(), f.getSource(), f.getSourceItemId(),
                f.getFailureReason(), f.getErrorMessage(),
                f.getRawPayload(), f.getRawPayloadHash(), f.isPayloadAvailable(),
                f.getRetryCount(), f.getLastRetriedAt(), f.getCreatedAt()
        );
    }

    @Transactional
    public IngestionRetryResponse retryFailure(Long failureId) {
        RetryResult result = retryUseCase.retry(failureId);
        return IngestionRetryResponse.from(result);
    }

    private String excerpt(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
