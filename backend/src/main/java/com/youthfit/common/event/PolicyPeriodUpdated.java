package com.youthfit.common.event;

/**
 * 보강(PeriodBackfillService)으로 정책의 신청기간이 갱신되었을 때 발행.
 * 구독자: (현재 없음 — 향후 캐시 무효화, 알림 등 확장)
 */
public record PolicyPeriodUpdated(Long policyId) {}
