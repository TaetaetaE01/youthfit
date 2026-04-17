package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;

    public PolicyPageResult findPoliciesByFilters(String regionCode, Category category,
                                                  PolicyStatus status, String sortBy,
                                                  boolean ascending, int page, int size) {
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Policy> policyPage = policyRepository.findAllByFilters(regionCode, category, status, pageable);

        return new PolicyPageResult(
                policyPage.getContent().stream().map(PolicySummaryResult::from).toList(),
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
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
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Policy> policyPage = policyRepository.searchByKeyword(keyword, pageable);

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
