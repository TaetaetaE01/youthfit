package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dto.AttachmentDetailResult;
import com.youthfit.admin.application.dto.AttachmentSummaryResult;
import com.youthfit.admin.application.dto.PolicyProcessingDetailResult;
import com.youthfit.admin.application.dto.PolicyProcessingFilter;
import com.youthfit.admin.application.dto.PolicyProcessingItemResult;
import com.youthfit.admin.application.dto.PolicyProcessingListCommand;
import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import com.youthfit.admin.application.dto.PolicyProcessingSort;
import com.youthfit.admin.application.dto.PolicyProcessingStatsResult;
import com.youthfit.admin.application.dto.ReferenceDetailResult;
import com.youthfit.admin.application.dto.ReferenceSummaryResult;
import com.youthfit.admin.application.dto.ReprocessResult;
import com.youthfit.admin.application.dto.StepDetailResult;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.common.event.PolicyReprocessRequestedEvent;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.service.EligibilityRuleGenerationService;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.policy.application.service.PolicyProcessingStepService;
import com.youthfit.policy.domain.model.AttachmentExtractionCounts;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final PolicyProcessingStepService stepService;
    private final RagIndexingService ragIndexingService;
    private final AttachmentReindexService attachmentReindexService;
    private final GuideGenerationService guideGenerationService;
    private final EligibilityRuleGenerationService eligibilityRuleGenerationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 어드민 정책 처리 현황 페이지 조회.
     *
     * <p>1) Policy 페이지를 조회하고 → 2) policy id 들로 step / attachment / embedding 을 일괄 조회하여
     * 3) 각 정책마다 종합 완성도를 계산한 다음 4) 메모리 상에서 filter 를 적용한다.</p>
     *
     * <p><b>페이징 + in-memory 필터 의미 한계:</b>
     * {@code totalCount} 는 검색·지역 SQL 필터까지 적용된 모집단 크기다.
     * {@code filteredItemCount} 는 in-memory 빠른필터까지 적용한 페이지 결과 수다.
     * computed 필터({@code RAG_FAILED}, {@code ATTACHMENT_EMBEDDING_MISSING},
     * {@code GUIDE_RULE_FAILED}, {@code REFERENCE_FETCH_FAILED}) 는 SQL 사전 거름이 불가능해
     * 페이지 후 후처리되므로 같은 totalCount 안에서도 페이지마다 filteredItemCount 가 0 ~ {@code size}
     * 사이로 변동될 수 있다. 정확한 글로벌 필터 카운트가 필요해지면 SQL group-by + projection 으로
     * 마이그레이션해야 한다.</p>
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
                filtered.size(),
                filtered
        );
    }

    /**
     * 어드민 정책 처리 현황 KPI 집계.
     *
     * <p>전체 정책을 한 번에 로드한 뒤 step/attachment/embedding 일괄 조회 결과와 함께
     * 정책마다 {@link #computeCompleteness} 를 호출해 4 종 카운트를 합산한다.
     * recent24hCount 는 {@code policy.created_at} 기준으로 in-memory 비교한다.
     *
     * <p>MVP 운영 데이터 기준 정책 수가 수백~수천 수준이라는 전제에서 안전하다.
     * 정책 수가 그 이상으로 늘어나면 SQL group-by 집계로 마이그레이션해야 한다.
     */
    public PolicyProcessingStatsResult findProcessingStats() {
        List<Policy> all = policyRepository.findAllForStats();
        List<Long> ids = all.stream().map(Policy::getId).toList();

        Map<Long, Map<ProcessingStep, ProcessingStatus>> stepMap =
                stepRepository.findLatestStatusMapByPolicyIds(ids);
        Map<Long, AttachmentExtractionCounts> attachMap =
                attachmentRepository.aggregateExtractionByPolicyIds(ids);
        Map<Long, Long> embedMap =
                documentRepository.countAttachmentEmbeddingsByPolicyIds(ids);

        long complete = 0;
        long partial = 0;
        long incomplete = 0;
        long recent = 0;
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        for (Policy p : all) {
            PolicyProcessingCompleteness c = computeCompleteness(
                    stepMap.getOrDefault(p.getId(), Map.of()),
                    attachMap.getOrDefault(p.getId(), AttachmentExtractionCounts.empty()),
                    embedMap.getOrDefault(p.getId(), 0L)
            );
            switch (c) {
                case COMPLETE -> complete++;
                case PARTIAL -> partial++;
                case INCOMPLETE -> incomplete++;
            }
            if (p.getCreatedAt() != null && p.getCreatedAt().isAfter(twentyFourHoursAgo)) {
                recent++;
            }
        }

        return new PolicyProcessingStatsResult(all.size(), complete, partial, incomplete, recent);
    }

    /**
     * 정책 1건의 펼침 영역 상세 조회.
     *
     * <p>1) policy_processing_step 의 step 별 최신 attempt 행 → 2) policy_attachment 전체 행 +
     * 임베딩 적재 attachmentId set → 3) ENRICHMENT detail_json 의 참조 사이트 fetch 결과를
     * 한 응답으로 조립한다.</p>
     *
     * <p>repository 의 {@code findLatestRowsByPolicyId} 는 JPQL {@code order by pps.step} 로
     * 정렬하지만 {@link ProcessingStep} 가 {@code @Enumerated(EnumType.STRING)} 으로 저장되어
     * 알파벳 순 (ENRICHMENT, GUIDE, INGESTION, RAG_INDEXING, RULE) 로 내려온다.
     * 파이프라인 순서대로 보여주기 위해 호출 측에서 {@link ProcessingStep#ordinal()} 로
     * 다시 정렬한다.</p>
     *
     * @throws YouthFitException {@link ErrorCode#NOT_FOUND} 정책이 존재하지 않을 때
     */
    public PolicyProcessingDetailResult findProcessingDetail(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));

        List<PolicyProcessingStep> stepRows = stepRepository.findLatestRowsByPolicyId(policyId).stream()
                .sorted(Comparator.comparingInt(s -> s.getStep().ordinal()))
                .toList();

        List<StepDetailResult> stepResults = stepRows.stream()
                .map(AdminPolicyProcessingService::toStepDetail)
                .toList();

        List<PolicyAttachment> attachments = attachmentRepository.findByPolicyId(policyId);
        Set<Long> embeddedIds = documentRepository.findEmbeddedAttachmentIds(policyId);

        List<AttachmentDetailResult> attachmentResults = attachments.stream()
                .map(a -> new AttachmentDetailResult(
                        a.getId(),
                        a.getName(),
                        a.getExtractionStatus(),
                        embeddedIds.contains(a.getId())
                ))
                .toList();

        List<ReferenceDetailResult> referenceResults = parseReferenceResults(stepRows);

        Map<ProcessingStep, ProcessingStatus> stepStatusMap = stepRows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PolicyProcessingStep::getStep,
                        PolicyProcessingStep::getStatus,
                        (a, b) -> b
                ));
        AttachmentExtractionCounts attachCounts = attachmentRepository
                .aggregateExtractionByPolicyIds(List.of(policyId))
                .getOrDefault(policyId, AttachmentExtractionCounts.empty());
        long embeddedCount = embeddedIds.size();

        PolicyProcessingCompleteness completeness =
                computeCompleteness(stepStatusMap, attachCounts, embeddedCount);

        return new PolicyProcessingDetailResult(
                policy.getId(),
                policy.getTitle(),
                completeness,
                stepResults,
                attachmentResults,
                referenceResults
        );
    }

    /**
     * 단계 1건 재실행.
     *
     * <p>{@link ProcessingStep#INGESTION} 은 n8n 재크롤이 필요하므로 어드민에서 재실행할 수 없다 (400).
     * 그 외 단계는 다음 동작을 수행한다:</p>
     * <ul>
     *   <li>{@code RAG_INDEXING} → {@link RagIndexingService#indexPolicyDocument(IndexPolicyDocumentCommand)}</li>
     *   <li>{@code GUIDE} → {@link GuideGenerationService#generateGuide(GenerateGuideCommand)}</li>
     *   <li>{@code RULE} → {@link EligibilityRuleGenerationService#generateRules(GenerateEligibilityRulesCommand)}</li>
     *   <li>{@code ENRICHMENT} → SKIPPED 처리 (MVP: admin trigger 미연결, n8n 재실행 필요)</li>
     * </ul>
     *
     * <p>모든 경로에서 {@link PolicyProcessingStepService#markStarted} 로 step 행을 만들고,
     * 성공 시 {@code SUCCESS}, 실패 시 {@code FAILED} 로 {@link PolicyProcessingStepService#markFinished} 호출.</p>
     *
     * @throws YouthFitException {@link ErrorCode#INVALID_INPUT} INGESTION 재실행 시도, {@link ErrorCode#NOT_FOUND} 정책 없음
     */
    @Transactional
    public ReprocessResult retryStep(Long policyId, ProcessingStep step) {
        if (step == ProcessingStep.INGESTION) {
            throw new YouthFitException(
                    ErrorCode.INVALID_INPUT,
                    "INGESTION 단계는 n8n 재크롤이 필요해 어드민에서 재실행할 수 없습니다."
            );
        }
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));

        Long stepRowId = stepService.markStarted(policyId, step);
        try {
            switch (step) {
                case RAG_INDEXING -> ragIndexingService.indexPolicyDocument(
                        new IndexPolicyDocumentCommand(policyId, policy.getBody(), policy.getEnrichment())
                );
                case GUIDE -> guideGenerationService.generateGuide(
                        new GenerateGuideCommand(policyId, policy.getTitle(), null)
                );
                case RULE -> eligibilityRuleGenerationService.generateRules(
                        new GenerateEligibilityRulesCommand(policyId)
                );
                case ENRICHMENT -> {
                    // MVP: ENRICHMENT 는 n8n 파이프라인 외 진입점이 없어 admin 에서 직접 트리거하지 않는다.
                    // TODO: 별도 enrichment 재실행 서비스가 생기면 연결한다.
                    stepService.markFinished(stepRowId, ProcessingStatus.SKIPPED,
                            "MVP: ENRICHMENT manual trigger 미연결", null);
                    return new ReprocessResult(true, List.of(stepRowId), "ENRICHMENT 재실행 미지원 (SKIPPED)");
                }
                default -> throw new YouthFitException(ErrorCode.INVALID_INPUT);
            }
            stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, null, null);
        } catch (YouthFitException e) {
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
            throw e;
        } catch (Exception e) {
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
            throw e;
        }
        return new ReprocessResult(true, List.of(stepRowId), "재실행 큐잉됨");
    }

    /**
     * 첨부 1건 임베딩 재실행.
     *
     * <p>{@link AttachmentReindexService#reindex(Long)} 는 정책 단위 동작이라 1건만 재인덱싱하는
     * API 가 없다. MVP 에선 1건 트리거 시에도 정책 단위 재인덱싱이 이루어지며, 펼침 영역 UX
     * 에 해당 동작을 안내한다.</p>
     *
     * @throws YouthFitException {@link ErrorCode#NOT_FOUND} 첨부가 없거나 정책 소속이 다를 때
     */
    @Transactional
    public ReprocessResult reindexAttachment(Long policyId, Long attachmentId) {
        PolicyAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
        if (attachment.getPolicy() == null || !attachment.getPolicy().getId().equals(policyId)) {
            throw new YouthFitException(ErrorCode.NOT_FOUND);
        }
        Long stepRowId = stepService.markStarted(policyId, ProcessingStep.RAG_INDEXING);
        try {
            attachmentReindexService.reindex(policyId);
            stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS,
                    "첨부 1건 재실행 트리거 (정책 단위 reindex)", null);
        } catch (Exception e) {
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
            throw e;
        }
        return new ReprocessResult(true, List.of(stepRowId), "첨부 재인덱싱 큐잉됨");
    }

    /**
     * 정책의 모든 첨부 임베딩 일괄 재실행.
     *
     * <p>{@link AttachmentReindexService#reindex(Long)} 는 정책 단위 동작이라 그대로 호출한다.
     * RAG_INDEXING step 행에 SUCCESS/FAILED 기록.</p>
     *
     * @throws YouthFitException {@link ErrorCode#NOT_FOUND} 정책 없음
     */
    @Transactional
    public ReprocessResult reindexAllAttachments(Long policyId) {
        policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
        Long stepRowId = stepService.markStarted(policyId, ProcessingStep.RAG_INDEXING);
        try {
            attachmentReindexService.reindex(policyId);
            stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS,
                    "전체 첨부 재인덱싱 트리거", null);
        } catch (Exception e) {
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
            throw e;
        }
        return new ReprocessResult(true, List.of(stepRowId), "전체 첨부 재인덱싱 큐잉됨");
    }

    /**
     * 정책 본문 RAG 재인덱싱.
     *
     * <p>{@link RagIndexingService#indexPolicyDocument} 를 정책 본문 + enrichment 만 가지고 호출한다.
     * 첨부 머지는 포함하지 않으므로 본문/요약/조건/지원내용 등 정책 자체의 변경만 다시 임베딩한다.</p>
     *
     * @throws YouthFitException {@link ErrorCode#NOT_FOUND} 정책 없음
     */
    @Transactional
    public ReprocessResult reindexRag(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
        Long stepRowId = stepService.markStarted(policyId, ProcessingStep.RAG_INDEXING);
        try {
            ragIndexingService.indexPolicyDocument(
                    new IndexPolicyDocumentCommand(policyId, policy.getBody(), policy.getEnrichment())
            );
            stepService.markFinished(stepRowId, ProcessingStatus.SUCCESS, null, null);
        } catch (Exception e) {
            stepService.markFinished(stepRowId, ProcessingStatus.FAILED, e.getMessage(), null);
            throw e;
        }
        return new ReprocessResult(true, List.of(stepRowId), "RAG 본문 재인덱싱 완료");
    }

    /**
     * 정책 전체 재처리 (ENRICHMENT/GUIDE/RULE/RAG_INDEXING 4단계 일괄 큐잉).
     *
     * <p>4 단계에 대해 {@link PolicyProcessingStepService#markStarted} 로 IN_PROGRESS step 행을 만들고,
     * {@link PolicyReprocessRequestedEvent} 를 발행한다. 향후 각 모듈 listener 가 본 이벤트를 받아
     * 실제 작업을 수행할 예정이다. MVP 단계에서는 listener 연결이 없어 step 행만 IN_PROGRESS 로 남고,
     * 대시보드에서 어드민이 진행 상태를 모니터링할 수 있다.</p>
     *
     * <p>운영 추적을 위해 {@code reason} 파라미터를 그대로 이벤트와 안내 메시지에 포함한다.</p>
     *
     * @throws YouthFitException {@link ErrorCode#NOT_FOUND} 정책 없음
     */
    @Transactional
    public ReprocessResult reprocess(Long policyId, String reason) {
        policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
        List<Long> stepIds = new ArrayList<>();
        for (ProcessingStep step : List.of(
                ProcessingStep.ENRICHMENT,
                ProcessingStep.GUIDE,
                ProcessingStep.RULE,
                ProcessingStep.RAG_INDEXING
        )) {
            stepIds.add(stepService.markStarted(policyId, step));
        }
        eventPublisher.publishEvent(new PolicyReprocessRequestedEvent(policyId, reason, stepIds));
        return new ReprocessResult(true, stepIds, "전체 재처리 큐잉됨 (사유: " + reason + ")");
    }

    /**
     * Phase D 이전에는 참조 사이트 fetch 결과를 채우지 않는다.
     *
     * <p>Phase D 에서 ENRICHMENT step 의 {@code detail_json.skippedUrls} 파싱이 추가되면
     * 이 메서드가 url / status / chunkCount 를 채워 리스트로 반환한다. 현재는 항상 빈 리스트.</p>
     */
    private List<ReferenceDetailResult> parseReferenceResults(List<PolicyProcessingStep> stepRows) {
        return List.of();
    }

    private static StepDetailResult toStepDetail(PolicyProcessingStep s) {
        Instant started = s.getStartedAt();
        Instant finished = s.getFinishedAt();
        Long durationMs = (started != null && finished != null)
                ? Duration.between(started, finished).toMillis()
                : null;
        return new StepDetailResult(
                s.getStep(),
                s.getStatus(),
                durationMs,
                s.getAttempt(),
                s.getReason(),
                toLocalDateTime(started),
                toLocalDateTime(finished)
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
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
