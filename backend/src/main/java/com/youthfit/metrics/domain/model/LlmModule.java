package com.youthfit.metrics.domain.model;

/**
 * LLM 호출 비용 집계 모듈 구분.
 *
 * <p>⚠️ 값 추가 시: llm_cost_bucket.module 의 CHECK 제약은 Hibernate 가 "테이블 생성 시점의"
 * enum 값으로 만든 것이라 자동 갱신되지 않는다. 기존 DB 에는 반드시 ALTER 마이그레이션을
 * 함께 추가할 것 (전례: sql/2026-07-04-llm-cost-bucket-module-check-attachment-gate.sql, #177).
 */
public enum LlmModule {
    QNA,
    GUIDE,
    EMBEDDING,
    INGESTION,
    ELIGIBILITY,
    QUERY_REWRITE,
    ATTACHMENT_GATE
}
