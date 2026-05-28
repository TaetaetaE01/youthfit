package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyProcessingStepService {

    private static final Logger log = LoggerFactory.getLogger(PolicyProcessingStepService.class);

    private final PolicyProcessingStepRepository repository;

    /**
     * 단계 시작 기록. attempt 는 기존 행 수 + 1 로 자동 계산.
     *
     * <p>호출 가정: 같은 (policyId, step) 에 대한 동시 호출은 없다고 가정한다.
     * 본 Phase 의 listener 들은 서로 다른 step 이라 사실상 동시 진입이 없으며,
     * 향후 admin 재실행 (Phase E) 단계에서 동시성이 필요해지면 unique constraint
     * 충돌 retry 로직을 도입한다.
     *
     * @return 저장된 step row id (markFinished 호출 시 사용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long markStarted(Long policyId, ProcessingStep step) {
        int attempt = repository.countByPolicyIdAndStep(policyId, step) + 1;
        PolicyProcessingStep row = PolicyProcessingStep.start(policyId, step, attempt);
        PolicyProcessingStep saved = repository.save(row);
        return saved.getId();
    }

    /**
     * 단계 종료 기록. 없는 id 면 경고 후 종료 (호출자 코드 안전성 우선).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinished(Long stepRowId, ProcessingStatus status, String reason, String detailJson) {
        repository.findById(stepRowId).ifPresentOrElse(
                row -> {
                    row.finish(status, reason, detailJson);
                    repository.save(row);
                },
                () -> log.warn("processing step row 없음, finish 무시: rowId={}, status={}", stepRowId, status)
        );
    }
}
