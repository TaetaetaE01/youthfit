/**
 * Metrics 모듈 — LLM 호출/비용 측정 도메인.
 * <p>
 * 다른 도메인(qna/guide/rag/ingestion/eligibility)이 OpenAI 호출 직후 ApplicationEvent 를 발행하면,
 * 본 모듈의 listener 가 1시간 단위 bucket 으로 upsert 적재한다.
 */
package com.youthfit.metrics;
