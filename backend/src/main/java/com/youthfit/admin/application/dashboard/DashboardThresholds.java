package com.youthfit.admin.application.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 어드민 대시보드 신호 평가에 사용되는 임계치 묶음.
 *
 * <p>{@code application.yml} 의 {@code admin.dashboard.*} 하위 값과 1:1 매핑된다.
 * 각 신호 구현체는 자신이 사용하는 nested 그룹만 주입받아 사용한다.</p>
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties("admin.dashboard")
public class DashboardThresholds {

    private final Llm llm;
    private final Ingestion ingestion;
    private final Enrichment enrichment;
    private final Email email;
    private final QnaCache qnaCache;
    private final PolicyIntake policyIntake;
    private final ScheduledTasks scheduledTasks;

    @Getter
    @RequiredArgsConstructor
    public static class Llm {
        private final BigDecimal weeklyBudgetKrw;
        private final BigDecimal dailySpikeMultiplier;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Ingestion {
        private final int staleDays;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Enrichment {
        private final int backlogWarn;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Email {
        private final BigDecimal failureRateThreshold;
        private final int failureCountThreshold;
    }

    @Getter
    @RequiredArgsConstructor
    public static class QnaCache {
        private final BigDecimal hitDropThresholdPp;
    }

    @Getter
    @RequiredArgsConstructor
    public static class PolicyIntake {
        private final BigDecimal stallRatio;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ScheduledTasks {
        private final int staleHours;
    }
}
