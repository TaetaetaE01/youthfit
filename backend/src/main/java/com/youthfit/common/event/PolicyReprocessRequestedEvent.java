package com.youthfit.common.event;

import java.util.List;

/**
 * 어드민이 정책 전체 재처리를 요청했을 때 발행되는 이벤트.
 *
 * <p>{@code AdminPolicyProcessingService.reprocess(policyId, reason)} 가 ENRICHMENT/GUIDE/RULE/RAG_INDEXING
 * 네 단계에 대해 {@link com.youthfit.policy.application.service.PolicyProcessingStepService#markStarted}
 * 로 step 행을 만든 다음 본 이벤트를 발행한다.</p>
 *
 * <p>구독자: 향후 enrichment/guide/rule/rag 각 모듈의 listener 가 본 이벤트를 받아 실제 재실행을 수행할 예정이다.
 * MVP 단계에서는 listener 가 아직 연결되지 않아 step 행만 IN_PROGRESS 로 남는다. 운영자는 대시보드에서
 * 진행 여부를 모니터링하고 listener 연결은 별도 작업으로 진행한다.</p>
 *
 * @param policyId 재처리 대상 정책 id
 * @param reason 운영자가 입력한 재처리 사유 (감사/추적용)
 * @param stepIds 큐잉된 PolicyProcessingStep 행 id 목록 (순서: ENRICHMENT, GUIDE, RULE, RAG_INDEXING)
 */
public record PolicyReprocessRequestedEvent(Long policyId, String reason, List<Long> stepIds) {
}
