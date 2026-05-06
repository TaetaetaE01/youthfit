package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminLlmCostService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LlmCostBucketRepository repository;

    @Value("${youthfit.metrics.llm-cost.usd-to-krw:1350}")
    private BigDecimal usdToKrwRate;

    @Transactional(readOnly = true)
    public LlmCostKpiResponse getKpi() {
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        LocalDate todayKst = nowKst.toLocalDate();
        Instant todayStart = todayKst.atStartOfDay(KST).toInstant();
        Instant tomorrowStart = todayKst.plusDays(1).atStartOfDay(KST).toInstant();

        DayOfWeek dow = todayKst.getDayOfWeek();
        LocalDate weekStart = todayKst.minusDays(dow.getValue() - 1L); // Mon=1
        Instant weekStartInst = weekStart.atStartOfDay(KST).toInstant();

        LocalDate monthStart = todayKst.withDayOfMonth(1);
        Instant monthStartInst = monthStart.atStartOfDay(KST).toInstant();

        LocalDate lastMonthStart = monthStart.minusMonths(1);
        Instant lastMonthStartInst = lastMonthStart.atStartOfDay(KST).toInstant();

        BigDecimal today = costSum(todayStart, tomorrowStart);
        BigDecimal week = costSum(weekStartInst, tomorrowStart);
        BigDecimal month = costSum(monthStartInst, tomorrowStart);
        BigDecimal lastMonth = costSum(lastMonthStartInst, monthStartInst);
        long monthCalls = callsSum(monthStartInst, tomorrowStart);

        return new LlmCostKpiResponse(today, week, month, monthCalls, usdToKrwRate, lastMonth);
    }

    @Transactional(readOnly = true)
    public LlmCostSeriesResponse getSeries(String range) {
        Range r = parseRange(range);
        List<Object[]> rows = repository.hourlySeries(r.from(), r.to());

        // bucket_hour → moduleMap
        Map<Instant, Map<LlmModule, BigDecimal>> grouped = new TreeMap<>();
        for (Object[] row : rows) {
            Instant at = ((Timestamp) row[0]).toInstant();
            LlmModule m = LlmModule.valueOf((String) row[1]);
            BigDecimal cost = (BigDecimal) row[2];
            grouped.computeIfAbsent(at, k -> new EnumMap<>(LlmModule.class)).put(m, cost);
        }

        List<LlmCostSeriesResponse.Point> points = new ArrayList<>();
        for (Map.Entry<Instant, Map<LlmModule, BigDecimal>> e : grouped.entrySet()) {
            points.add(new LlmCostSeriesResponse.Point(e.getKey(), e.getValue()));
        }
        return new LlmCostSeriesResponse(range, points);
    }

    @Transactional(readOnly = true)
    public List<LlmCostModuleDailyResponse> getDailyByModule(String range) {
        Range r = parseRange(range);
        List<Object[]> rows = repository.dailyByModule(r.from(), r.to());
        List<LlmCostModuleDailyResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = ((Date) row[0]).toLocalDate();
            LlmModule module = LlmModule.valueOf((String) row[1]);
            BigDecimal cost = (BigDecimal) row[2];
            long calls = ((Number) row[3]).longValue();
            result.add(new LlmCostModuleDailyResponse(date, module, cost, calls));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LlmCostModelSummaryResponse> getModelSummary(String range) {
        Range r = parseRange(range);
        List<Object[]> rows = repository.modelSummary(r.from(), r.to());

        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : rows) {
            total = total.add((BigDecimal) row[5]);
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) total = BigDecimal.ONE;

        List<LlmCostModelSummaryResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String model = (String) row[0];
            long calls = ((Number) row[1]).longValue();
            long prompt = ((Number) row[2]).longValue();
            long completion = ((Number) row[3]).longValue();
            long totalT = ((Number) row[4]).longValue();
            BigDecimal cost = (BigDecimal) row[5];
            BigDecimal share = cost.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            result.add(new LlmCostModelSummaryResponse(model, calls, prompt, completion, totalT, cost, share));
        }
        return result;
    }

    private BigDecimal costSum(Instant from, Instant to) {
        Map<String, Object> row = repository.sumBetween(from, to);
        Object cost = row.get("cost");
        if (cost == null) return BigDecimal.ZERO;
        return (BigDecimal) cost;
    }

    private long callsSum(Instant from, Instant to) {
        Map<String, Object> row = repository.sumBetween(from, to);
        Object calls = row.get("calls");
        if (calls == null) return 0;
        return ((Number) calls).longValue();
    }

    private Range parseRange(String range) {
        Instant now = Instant.now();
        Instant from = switch (range == null ? "7d" : range.toLowerCase(Locale.ROOT)) {
            case "24h" -> now.minus(Duration.ofHours(24));
            case "7d" -> now.minus(Duration.ofDays(7));
            case "30d" -> now.minus(Duration.ofDays(30));
            default -> now.minus(Duration.ofDays(7));
        };
        return new Range(from, now);
    }

    private record Range(Instant from, Instant to) {}
}
