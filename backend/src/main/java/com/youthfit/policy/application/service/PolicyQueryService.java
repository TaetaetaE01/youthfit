package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;
import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.dto.result.PolicySummaryResult;
import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionCode;
import com.youthfit.policy.domain.model.RegionFilter;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;
    private final RegionCodeRegistry regionCodeRegistry;

    public PolicyPageResult findPoliciesByFilters(RegionFilter regionFilter, Category category,
                                                  PolicyStatus status,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Policy> policyPage = policyRepository.findAllByFilters(regionFilter, category, status, pageable);
        return toPageResult(policyPage);
    }

    private static final int NATIONWIDE_CODE_THRESHOLD = 100;
    private static final long CALENDAR_RANGE_MAX_DAYS = 92;
    private static final int CALENDAR_ALWAYS_OPEN_MAX_SIZE = 50;

    public PolicyDetailResult findPolicyById(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다: " + policyId));
        PolicySource source = policySourceRepository.findFirstByPolicyId(policyId).orElse(null);
        List<PolicyDetailResult.SubRegion> subRegions = isNationwide(policy)
                ? List.of()
                : regionCodeRegistry.findAll(policy.getRegionCodeList())
                        .stream()
                        .map(PolicyDetailResult.SubRegion::from)
                        .toList();
        PolicyDetailResult.Enrichment enrichmentResult = buildEnrichmentResult(policy.getEnrichment());
        return PolicyDetailResult.from(policy, source, subRegions, enrichmentResult);
    }

    private PolicyDetailResult.Enrichment buildEnrichmentResult(PolicyEnrichment e) {
        if (e == null || !e.isExposable()) {
            return null;
        }
        PolicyDetailResult.Enrichment.Sections sections = e.sections() == null ? null
                : new PolicyDetailResult.Enrichment.Sections(
                        e.sections().supportTarget(),
                        e.sections().supportContent(),
                        e.sections().applyMethod(),
                        e.sections().requiredDocuments(),
                        e.sections().deadlineNote(),
                        e.sections().policyOverview(),
                        e.sections().eligibilityCriteria(),
                        e.sections().operatingOrganization(),
                        e.sections().contactPhone()
                );
        List<PolicyDetailResult.Enrichment.ExtraAttachment> atts =
                e.extraAttachments() == null ? List.of()
                : e.extraAttachments().stream()
                        .map(a -> new PolicyDetailResult.Enrichment.ExtraAttachment(a.name(), a.url()))
                        .toList();
        return new PolicyDetailResult.Enrichment(e.sourceUrl(), e.fetchedAt(), sections, atts);
    }

    public List<PolicyCalendarResult> findByDateRange(LocalDate from, LocalDate to,
                                                       RegionFilter regionFilter,
                                                       Category category) {
        if (from == null || to == null) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "from, to 는 필수입니다");
        }
        if (from.isAfter(to)) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT,
                    "from 은 to 보다 이전이거나 같아야 합니다");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > CALENDAR_RANGE_MAX_DAYS) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT,
                    "조회 범위는 " + CALENDAR_RANGE_MAX_DAYS + "일을 초과할 수 없습니다");
        }

        return policyRepository.findByCalendarRange(from, to, regionFilter, category)
                .stream()
                .map(p -> PolicyCalendarResult.from(p, resolveRegionLabel(p)))
                .toList();
    }

    public PolicyCalendarPageResult findAlwaysOpen(RegionFilter regionFilter,
                                                    Category category,
                                                    int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), CALENDAR_ALWAYS_OPEN_MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Policy> policyPage = policyRepository.findAlwaysOpen(regionFilter, category, pageable);

        List<PolicyCalendarResult> items = policyPage.getContent().stream()
                .map(p -> PolicyCalendarResult.from(p, resolveRegionLabel(p)))
                .toList();

        return new PolicyCalendarPageResult(
                items,
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
    }

    private String resolveRegionLabel(Policy policy) {
        // "전국" 정책 또는 비정상적으로 많은 행정 코드를 묶은 정책은 그대로 "전국"으로 표기.
        if (isNationwide(policy)) return "전국";
        // 시·군·구 코드 리스트를 사람이 읽는 이름으로 변환. 다수면 첫 N개 + "외 K건".
        List<RegionCode> codes = regionCodeRegistry.findAll(policy.getRegionCodeList());
        if (codes.isEmpty()) return policy.getRegionCode();
        if (codes.size() == 1) return codes.get(0).name();
        return codes.get(0).name() + " 외 " + (codes.size() - 1) + "건";
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
                        .map(p -> PolicySummaryResult.from(p, sourceMap.get(p.getId()), summarizeSubRegions(p)))
                        .toList(),
                policyPage.getTotalElements(),
                policyPage.getNumber(),
                policyPage.getSize(),
                policyPage.getTotalPages(),
                policyPage.hasNext()
        );
    }

    private List<String> summarizeSubRegions(Policy policy) {
        if (isNationwide(policy)) return List.of();
        return regionCodeRegistry.findAll(policy.getRegionCodeList())
                .stream()
                .map(RegionCode::name)
                .distinct()
                .toList();
    }

    private boolean isNationwide(Policy policy) {
        if ("전국".equals(policy.getRegionCode())) return true;
        return policy.getRegionCodeList().size() >= NATIONWIDE_CODE_THRESHOLD;
    }
}
