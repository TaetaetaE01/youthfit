package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dto.AttachmentSummaryResult;
import com.youthfit.admin.application.dto.PolicyProcessingFilter;
import com.youthfit.admin.application.dto.PolicyProcessingItemResult;
import com.youthfit.admin.application.dto.PolicyProcessingListCommand;
import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import com.youthfit.admin.application.dto.PolicyProcessingSort;
import com.youthfit.admin.application.dto.ReferenceSummaryResult;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.policy.domain.model.AttachmentExtractionCounts;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 어드민 정책 처리 현황 대시보드 유스케이스 서비스.
 *
 * <p>{@code policy_processing_step} + {@code policy_attachment} + {@code policy_document}
 * 세 테이블의 일괄 조회를 결합해 정책별 처리 단계 status, 첨부 추출/임베딩 카운트, 종합 완성도를 산출한다.
 *
 * <p>Presentation 컨트롤러는 본 서비스의 Result 만 의존하고, 도메인 리포지토리에 직접 접근하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPolicyProcessingService {

    private final PolicyRepository policyRepository;
    private final PolicyProcessingStepRepository stepRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final PolicyDocumentRepository documentRepository;

    /**
     * 어드민 정책 처리 현황 페이지 조회.
     *
     * <p>1) Policy 페이지를 조회하고 → 2) policy id 들로 step / attachment / embedding 을 일괄 조회하여
     * 3) 각 정책마다 종합 완성도를 계산한 다음 4) 메모리 상에서 filter 를 적용한다.
     */
    public PolicyProcessingListResult findProcessingPolicies(PolicyProcessingListCommand command) {
        Page<Policy> policyPage = policyRepository.findForAdminProcessing(
                command.query(),
                command.region(),
                toSpringSort(command.sort()),
                PageRequest.of(command.page(), command.size())
        );

        // 정책 페이지가 비어 있더라도 batch 리포지토리에는 항상 non-null 빈 리스트를 넘긴다.
        List<Long> policyIds = policyPage.getContent().stream().map(Policy::getId).toList();

        Map<Long, Map<ProcessingStep, ProcessingStatus>> stepMap =
                stepRepository.findLatestStatusMapByPolicyIds(policyIds);
        Map<Long, AttachmentExtractionCounts> attachMap =
                attachmentRepository.aggregateExtractionByPolicyIds(policyIds);
        Map<Long, Long> embedMap =
                documentRepository.countAttachmentEmbeddingsByPolicyIds(policyIds);

        List<PolicyProcessingItemResult> items = policyPage.getContent().stream().map(p -> {
            Map<ProcessingStep, ProcessingStatus> stepStatuses =
                    stepMap.getOrDefault(p.getId(), Map.of());
            AttachmentExtractionCounts attachCounts =
                    attachMap.getOrDefault(p.getId(), AttachmentExtractionCounts.empty());
            long embeddedCount = embedMap.getOrDefault(p.getId(), 0L);

            return new PolicyProcessingItemResult(
                    p.getId(),
                    p.getTitle(),
                    p.getRegionCode(),
                    computeCompleteness(stepStatuses, attachCounts, embeddedCount),
                    stepStatuses,
                    new AttachmentSummaryResult(attachCounts.total(), attachCounts.extracted(), embeddedCount),
                    // 참고 사이트 결과는 Phase D 의 ENRICHMENT detail_json 채택 이후 채워진다.
                    ReferenceSummaryResult.placeholder(),
                    p.getUpdatedAt()
            );
        }).toList();

        List<PolicyProcessingItemResult> filtered = applyFilter(items, command.filter());

        return new PolicyProcessingListResult(
                policyPage.getTotalElements(),
                command.page(),
                command.size(),
                filtered
        );
    }

    /**
     * 종합 완성도 계산.
     * <ul>
     *   <li>RAG_INDEXING 이 SUCCESS 가 아니면 INCOMPLETE</li>
     *   <li>RAG_INDEXING SUCCESS + 첨부가 0건이면 COMPLETE</li>
     *   <li>RAG_INDEXING SUCCESS + 모든 첨부가 EXTRACTED 이고 distinct 임베딩 attachment 수가 total 과 같으면 COMPLETE</li>
     *   <li>그 외(RAG SUCCESS 이지만 첨부 처리/임베딩 누락)는 PARTIAL</li>
     * </ul>
     */
    PolicyProcessingCompleteness computeCompleteness(
            Map<ProcessingStep, ProcessingStatus> stepStatuses,
            AttachmentExtractionCounts attachCounts,
            long embeddedCount
    ) {
        ProcessingStatus ragStatus = stepStatuses.get(ProcessingStep.RAG_INDEXING);
        if (ragStatus != ProcessingStatus.SUCCESS) {
            return PolicyProcessingCompleteness.INCOMPLETE;
        }
        if (attachCounts.total() == 0) {
            return PolicyProcessingCompleteness.COMPLETE;
        }
        boolean allExtracted = attachCounts.extracted() == attachCounts.total();
        boolean allEmbedded = embeddedCount >= attachCounts.total();
        if (allExtracted && allEmbedded) {
            return PolicyProcessingCompleteness.COMPLETE;
        }
        return PolicyProcessingCompleteness.PARTIAL;
    }

    private List<PolicyProcessingItemResult> applyFilter(
            List<PolicyProcessingItemResult> items, PolicyProcessingFilter filter
    ) {
        return switch (filter) {
            case ALL -> items;
            case INCOMPLETE -> items.stream()
                    .filter(i -> i.completeness() == PolicyProcessingCompleteness.INCOMPLETE)
                    .toList();
            case PARTIAL -> items.stream()
                    .filter(i -> i.completeness() == PolicyProcessingCompleteness.PARTIAL)
                    .toList();
            case RAG_FAILED -> items.stream()
                    .filter(i -> i.stepStatuses().get(ProcessingStep.RAG_INDEXING) == ProcessingStatus.FAILED)
                    .toList();
            case ATTACHMENT_EMBEDDING_MISSING -> items.stream()
                    .filter(i -> i.attachments().total() > 0
                            && i.attachments().embedded() < i.attachments().total())
                    .toList();
            case GUIDE_RULE_FAILED -> items.stream()
                    .filter(i -> i.stepStatuses().get(ProcessingStep.GUIDE) == ProcessingStatus.FAILED
                            || i.stepStatuses().get(ProcessingStep.RULE) == ProcessingStatus.FAILED)
                    .toList();
            // 참고 사이트 fetch 결과는 Phase D 에서 채워진다. 그 전엔 결과 없음.
            case REFERENCE_FETCH_FAILED -> List.of();
            // RECENT_24H 은 SQL 단계 created_at 필터로 처리하므로 in-memory 에서는 추가 작업 없음.
            case RECENT_24H -> items;
        };
    }

    private Sort toSpringSort(PolicyProcessingSort sort) {
        return switch (sort) {
            case UPDATED_DESC -> Sort.by(Sort.Direction.DESC, "updatedAt");
            // COMPLETENESS_ASC 는 정책 페이지 단계에서 정렬할 수 없으므로 id 정렬로 일단 가져와
            // 서비스 후처리에서 다시 정렬한다. (Task 5 단계에서는 in-memory 후처리 생략)
            case COMPLETENESS_ASC -> Sort.by(Sort.Direction.ASC, "id");
            case ID_ASC -> Sort.by(Sort.Direction.ASC, "id");
        };
    }
}
