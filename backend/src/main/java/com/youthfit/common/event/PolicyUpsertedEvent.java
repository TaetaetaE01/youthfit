package com.youthfit.common.event;

/**
 * 정책 ingest(신규/갱신) 트랜잭션이 정상 commit 된 직후 발행되는 이벤트.
 *
 * 구독자: GuideGenerationEventListener, EligibilityRuleGenerationEventListener, RagIndexingEventListener
 *
 * 발행 위치: IngestionService.receivePolicy(...)
 */
public record PolicyUpsertedEvent(Long policyId, String title) {
}
