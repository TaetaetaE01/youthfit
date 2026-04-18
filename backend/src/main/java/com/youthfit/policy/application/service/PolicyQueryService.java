package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySortType;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;

    public PolicyPageResult findPoliciesByFilters(String regionCode, Category category,
                                                  PolicyStatus status, PolicySortType sortType,
                                                  int page, int size) {
        PolicySortType effective = sortType == null ? PolicySortType.DEADLINE : sortType;
        Pageable pageable = PageRequest.of(page, size);

        Page<Policy> policyPage = policyRepository.findAllByFilters(regionCode, category, status, effective, pageable);

        return toPageResult(policyPage);
    }

    public PolicyDetailResult findPolicyById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다: " + policyId));
        String sourceUrl = policySourceRepository.findFirstByPolicyId(policyId)
                .map(src -> src.getSourceUrl())
                .orElse(null);
        return PolicyDetailResult.from(policy, sourceUrl);
    }

    public PolicyPageResult searchPoliciesByKeyword(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Policy> policyPage = policyRepository.searchByKeyword(keyword, PolicySortType.DEADLINE, pageable);

        return toPageResult(policyPage);
    }

    private PolicyPageResult toPageResult(Page<Policy> policyPage) {
        return new PolicyPageResult(
                policyPage.getContent().stream().map(PolicySummaryResult::from).toList(),
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
    }
}
