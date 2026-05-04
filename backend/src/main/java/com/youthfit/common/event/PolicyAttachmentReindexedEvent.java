package com.youthfit.common.event;

/**
 * 첨부 재인덱싱(AttachmentReindexService.reindex) 결과로 정책 문서가 실제 갱신되었을 때만
 * 발행되는 이벤트. result.updated() == false 인 경우에는 발행되지 않는다.
 *
 * 구독자: GuideGenerationEventListener, EligibilityRuleGenerationEventListener
 *
 * 발행 위치: AttachmentReindexService.reindex(...)
 */
public record PolicyAttachmentReindexedEvent(Long policyId) {
}
