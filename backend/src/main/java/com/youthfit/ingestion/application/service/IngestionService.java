package com.youthfit.ingestion.application.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.application.service.CodeBasedRuleExtractionService;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.model.IngestionRunLog;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult.Outcome;
import com.youthfit.policy.application.service.PolicyIngestionService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "\\[(개요|지원대상|선정기준|지원내용)]\\s*\\n([\\s\\S]*?)(?=\\n\\[(?:개요|지원대상|선정기준|지원내용)]|$)"
    );

    private final PolicyIngestionService policyIngestionService;
    private final ObjectMapper objectMapper;
    private final PeriodResolver periodResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentDownloadService attachmentDownloadService;
    private final IngestionRunLogRepository ingestionRunLogRepository;
    private final IngestionItemFailureRepository ingestionItemFailureRepository;
    private final CodeBasedRuleExtractionService codeBasedRuleExtractionService;
    private final PolicySourceRepository policySourceRepository;

    public IngestPolicyResult receivePolicy(IngestPolicyCommand command) {
        Instant runStart = Instant.now();
        String sourceLabel = resolveSourceLabel(command);
        boolean failed = false;
        boolean duplicate = false;

        try {
            Category category = mapCategory(command.category());
            SourceType sourceType = resolveSourceType(command.sourceType());
            String rawJson = serialize(command);
            String sourceHash = command.providedSourceHash() != null && !command.providedSourceHash().isBlank()
                    ? command.providedSourceHash()
                    : sha256(rawJson);
            String externalId = command.externalId() != null && !command.externalId().isBlank()
                    ? command.externalId()
                    : command.sourceUrl();
            String summary = command.summary() != null && !command.summary().isBlank()
                    ? command.summary()
                    : command.body();

            Sections sections = parseSections(command.body());
            ResolvedPeriod period = resolvePeriod(command);

            List<String> regionCodes = command.rawCodes() == null
                    ? null
                    : command.rawCodes().zipCodes();
            RegisterPolicyCommand registerCommand = new RegisterPolicyCommand(
                    command.title(),
                    summary,
                    command.body(),
                    sections.supportTarget(),
                    sections.selectionCriteria(),
                    sections.supportContent(),
                    command.organization(),
                    command.contact(),
                    category,
                    command.region(),
                    regionCodes,
                    period.start(),
                    period.end(),
                    command.referenceYear(),
                    command.supportCycle(),
                    command.provideType(),
                    // ── 신규 11개 ──
                    command.screeningMethod(),
                    command.submissionDocuments(),
                    command.additionalQualification(),
                    command.participationRestriction(),
                    command.additionalNotes(),
                    command.businessPeriodStart(),
                    command.businessPeriodEnd(),
                    command.businessPeriodNote(),
                    command.supportScale(),
                    Boolean.TRUE.equals(command.firstComeFirstServed()),
                    command.applyUrl(),
                    toSet(command.lifeTags()),
                    toSet(command.themeTags()),
                    toSet(command.targetTags()),
                    mapAttachments(command.attachments()),
                    mapReferenceSites(command.referenceSites()),
                    mapApplyMethods(command.applyMethods()),
                    sourceType,
                    externalId,
                    command.sourceUrl(),
                    rawJson,
                    sourceHash,
                    command.enrichment(),
                    period.isEmpty() ? null : period.source(),
                    period.isEmpty() ? null : period.confidence(),
                    period.isEmpty() ? null : period.evidence()
            );

            PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
            duplicate = ingestionResult.outcome() != Outcome.REGISTERED;

            if (ingestionResult.outcome() == Outcome.SKIPPED_DUPLICATE) {
                return new IngestPolicyResult(UUID.randomUUID(), "SKIPPED_DUPLICATE");
            }

            if (command.rawCodes() != null) {
                CodeBasedExtractionInput extractionInput = new CodeBasedExtractionInput(
                        command.rawCodes().ageMin(),
                        command.rawCodes().ageMax(),
                        command.rawCodes().ageLimitYn(),
                        command.rawCodes().maritalStatusCd(),
                        command.rawCodes().earnConditionCd(),
                        command.rawCodes().earnMin(),
                        command.rawCodes().earnMax(),
                        command.rawCodes().earnEtcCn(),
                        command.rawCodes().employmentKindCd(),
                        command.rawCodes().educationCd(),
                        command.rawCodes().majorFieldCd(),
                        command.rawCodes().specializationCd(),
                        command.rawCodes().zipCodes() == null ? List.of() : command.rawCodes().zipCodes()
                );
                codeBasedRuleExtractionService.extractAndPersist(ingestionResult.policyId(), extractionInput);
            }

            eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
            triggerAttachmentDownload(ingestionResult.policyId());

            if (command.enrichment() != null) {
                log.info("enrichment received: externalId={}, status={}, confidence={}",
                        command.externalId(),
                        command.enrichment().status(),
                        command.enrichment().confidence());
            }

            return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
        } catch (RuntimeException e) {
            failed = true;
            recordFailure(command, sourceLabel, e);
            throw e;
        } finally {
            Instant runEnd = Instant.now();
            IngestionRunLog runLog = failed
                    ? IngestionRunLog.failure(sourceLabel, runStart, runEnd)
                    : IngestionRunLog.success(sourceLabel, runStart, runEnd, duplicate);
            try {
                ingestionRunLogRepository.save(runLog);
            } catch (Exception e) {
                log.warn("ingestion run log 적재 실패 (정상 흐름 진행): source={}", sourceLabel, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> findExternalIdHashes(String sourceType) {
        SourceType type;
        try {
            type = SourceType.valueOf(sourceType);
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
        return policySourceRepository.findExternalIdHashMap(type);
    }

    private Category mapCategory(String category) {
        return switch (category) {
            case "일자리" -> Category.JOBS;
            case "주거" -> Category.HOUSING;
            case "교육" -> Category.EDUCATION;
            case "복지" -> Category.WELFARE;
            case "금융" -> Category.FINANCE;
            case "문화" -> Category.CULTURE;
            case "참여" -> Category.PARTICIPATION;
            default -> Category.WELFARE;
        };
    }

    private SourceType resolveSourceType(String type) {
        try {
            return SourceType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return SourceType.YOUTH_SEOUL_CRAWL;
        }
    }

    private ResolvedPeriod resolvePeriod(IngestPolicyCommand command) {
        return periodResolver.resolve(PeriodExtractionContext.forIngest(command));
    }

    private Sections parseSections(String body) {
        if (body == null || body.isBlank()) return Sections.empty();
        String supportTarget = null;
        String selectionCriteria = null;
        String supportContent = null;
        Matcher matcher = SECTION_PATTERN.matcher(body);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            switch (key) {
                case "지원대상" -> supportTarget = value;
                case "선정기준" -> selectionCriteria = value;
                case "지원내용" -> supportContent = value;
                default -> { /* 개요는 summary에서 이미 다룸 */ }
            }
        }
        return new Sections(supportTarget, selectionCriteria, supportContent);
    }

    private Set<String> toSet(List<String> list) {
        if (list == null || list.isEmpty()) return Set.of();
        return new HashSet<>(list);
    }

    private List<RegisterPolicyCommand.Attachment> mapAttachments(List<IngestPolicyCommand.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        return attachments.stream()
                .map(a -> new RegisterPolicyCommand.Attachment(a.name(), a.url(), a.mediaType()))
                .toList();
    }

    private List<RegisterPolicyCommand.ReferenceSite> mapReferenceSites(List<IngestPolicyCommand.ReferenceSite> sites) {
        if (sites == null || sites.isEmpty()) return List.of();
        return sites.stream()
                .map(s -> new RegisterPolicyCommand.ReferenceSite(s.name(), s.url()))
                .toList();
    }

    private List<RegisterPolicyCommand.ApplyMethod> mapApplyMethods(List<IngestPolicyCommand.ApplyMethod> methods) {
        if (methods == null || methods.isEmpty()) return List.of();
        return methods.stream()
                .map(m -> new RegisterPolicyCommand.ApplyMethod(m.stageName(), m.description()))
                .toList();
    }

    private String serialize(IngestPolicyCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize ingest command", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record Sections(String supportTarget, String selectionCriteria, String supportContent) {
        static Sections empty() {
            return new Sections(null, null, null);
        }
    }

    private String resolveSourceLabel(IngestPolicyCommand command) {
        if (command == null) return "unknown";
        if (command.sourceType() != null && !command.sourceType().isBlank()) {
            return command.sourceType();
        }
        return "unknown";
    }

    private void recordFailure(IngestPolicyCommand command, String sourceLabel, Throwable t) {
        try {
            FailureReason reason = FailureReason.classify(t);
            String rawPayload = safeSerialize(command);
            String message = t.getMessage() == null ? t.getClass().getSimpleName()
                    : t.getClass().getSimpleName() + ": " + t.getMessage();
            if (message.length() > 4000) message = message.substring(0, 4000);
            String stack = stackTraceOf(t);
            IngestPolicyCommand.PipelineMeta meta = command == null ? null : command.pipelineMeta();
            ingestionItemFailureRepository.save(IngestionItemFailure.of(
                    null,                       // run_log_id 는 finally 시점에 저장돼서 모름 — null
                    sourceLabel,
                    command == null ? null : command.externalId(),
                    rawPayload,
                    reason,
                    message,
                    stack,
                    meta == null ? null : meta.n8nWorkflowName(),
                    meta == null ? null : meta.n8nExecutionId(),
                    meta == null ? null : meta.n8nNodeName()
            ));
        } catch (Exception inner) {
            log.warn("ingestion failure 적재 실패 (정상 흐름 진행)", inner);
        }
    }

    private static String stackTraceOf(Throwable t) {
        if (t == null) return null;
        java.io.StringWriter sw = new java.io.StringWriter(2048);
        try (java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
            t.printStackTrace(pw);
        }
        String s = sw.toString();
        return s.length() > 8000 ? s.substring(0, 8000) + "\n…(truncated)" : s;
    }

    private String safeSerialize(IngestPolicyCommand command) {
        if (command == null) return null;
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception e) {
            return "{\"_serializeError\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }

    private void triggerAttachmentDownload(Long policyId) {
        if (policyId == null) return;
        try {
            attachmentDownloadService.downloadForPolicyAsync(policyId);
        } catch (Exception e) {
            log.warn("첨부 다운로드 트리거 실패: policyId={}", policyId, e);
        }
    }
}
