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
