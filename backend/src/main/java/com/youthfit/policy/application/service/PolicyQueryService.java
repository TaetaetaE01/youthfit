package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;

    public PolicyPageResult findPoliciesByFilters(String regionCode, Category category,
                                                  PolicyStatus status,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Policy> policyPage = policyRepository.findAllByFilters(regionCode, category, status, pageable);
        return toPageResult(policyPage);
    }

    public PolicyDetailResult findPolicyById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다: " + policyId));
        PolicySource source = policySourceRepository.findFirstByPolicyId(policyId).orElse(null);
        return PolicyDetailResult.from(policy, source);
    }

    public PolicyPageResult searchPoliciesByKeyword(String keyword, PolicyStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Policy> policyPage = policyRepository.searchByKeyword(keyword, status, pageable);
        return toPageResult(policyPage);
    }

    private PolicyPageResult toPageResult(Page<Policy> policyPage) {
        List<Long> ids = policyPage.getContent().stream().map(Policy::getId).toList();
        Map<Long, PolicySource> sourceMap = policySourceRepository.findFirstByPolicyIds(ids);
        return new PolicyPageResult(
                policyPage.getContent().stream()
                        .map(p -> PolicySummaryResult.from(p, sourceMap.get(p.getId())))
                        .toList(),
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
    }
}
