package com.youthfit.eligibility.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.eligibility.application.dto.command.EligibilityRuleExtractionInput;
import com.youthfit.eligibility.application.dto.command.GenerateEligibilityRulesCommand;
import com.youthfit.eligibility.application.dto.command.RuleExtractionChunk;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;
import com.youthfit.eligibility.application.dto.result.RuleGenerationResult;
import com.youthfit.eligibility.application.port.EligibilityRuleLlmProvider;
import com.youthfit.eligibility.application.service.EligibilityRuleValidator.ValidationReport;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.model.RuleConfidence;
import com.youthfit.eligibility.domain.model.RuleOperator;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EligibilityRuleGenerationService {

    public static final String PROMPT_VERSION = "v1";

    private static final Logger log = LoggerFactory.getLogger(EligibilityRuleGenerationService.class);

    private final CostGuard costGuard;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final EligibilityRuleRepository ruleRepository;
    private final EligibilityRuleLlmProvider llmProvider;
    private final EligibilityRuleValidator validator;

    @Transactional
    public RuleGenerationResult generateRules(GenerateEligibilityRulesCommand command) {
        Long policyId = command.policyId();

        if (!costGuard.allows(policyId)) {
            costGuard.logSkip("generateRules", policyId);
            return new RuleGenerationResult(policyId, false, "cost-guard: allowlist 외 정책");
        }

        Optional<Policy> policyOpt = policyRepository.findById(policyId);
        if (policyOpt.isEmpty()) {
            log.warn("정책 없음 - 룰 추출 스킵: policyId={}", policyId);
            return new RuleGenerationResult(policyId, false, "정책을 찾을 수 없습니다");
        }
        Policy policy = policyOpt.get();

        List<PolicyDocument> chunks = policyDocumentRepository.findByPolicyIdOrderByChunkIndex(policyId);

        EligibilityRuleExtractionInput input = buildInput(policy, chunks);
        String hash = computeHash(input);

        List<EligibilityRule> existing = ruleRepository.findAllByPolicyId(policyId);
        if (!existing.isEmpty()
                && hash.equals(existing.get(0).getSourceHash())
                && PROMPT_VERSION.equals(existing.get(0).getExtractionVersion())) {
            log.info("룰 변경 없음, 재추출 스킵: policyId={}", policyId);
            return new RuleGenerationResult(policyId, false, "변경 없음");
        }

        List<RawExtractedRule> finalRules;
        try {
            List<RawExtractedRule> first = llmProvider.extractRules(input);
            ValidationReport firstReport = validator.validate(first);

            if (firstReport.shouldRetry()) {
                log.info("룰 추출 검증 위반, 재시도: policyId={}, violations={}",
                        policyId, firstReport.feedbackMessages());
                List<RawExtractedRule> second = llmProvider.regenerateWithFeedback(
                        input, firstReport.feedbackMessages());
                ValidationReport secondReport = validator.validate(second);

                if (secondReport.feedbackMessages().size() < firstReport.feedbackMessages().size()) {
                    finalRules = secondReport.acceptedRules();
                } else {
                    log.warn("재시도가 개선되지 않음, 1차 응답 사용: policyId={}", policyId);
                    finalRules = firstReport.acceptedRules();
                }
            } else {
                finalRules = firstReport.acceptedRules();
            }
        } catch (Exception e) {
            log.error("룰 추출 실패 - 기존 룰 유지: policyId={}, message={}", policyId, e.getMessage());
            return new RuleGenerationResult(policyId, false, "LLM 호출 실패: " + e.getMessage());
        }

        ruleRepository.deleteAllByPolicyId(policyId);
        List<EligibilityRule> entities = finalRules.stream()
                .map(r -> toEntity(r, policyId, hash))
                .toList();
        ruleRepository.saveAll(entities);

        log.info("룰 추출 완료: policyId={}, ruleCount={}", policyId, entities.size());
        return new RuleGenerationResult(policyId, true, "생성 완료 (" + entities.size() + "개)");
    }

    private EligibilityRuleExtractionInput buildInput(Policy policy, List<PolicyDocument> chunks) {
        List<RuleExtractionChunk> attachmentChunks = chunks.stream()
                .filter(c -> c.getAttachmentId() != null)
                .map(c -> new RuleExtractionChunk(
                        c.getContent(), c.getAttachmentId(), c.getPageStart(), c.getPageEnd()))
                .toList();
        return new EligibilityRuleExtractionInput(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getBody(),
                attachmentChunks
        );
    }

    private EligibilityRule toEntity(RawExtractedRule raw, Long policyId, String hash) {
        return EligibilityRule.builder()
                .policyId(policyId)
                .field(raw.field())
                .operator(RuleOperator.valueOf(raw.operator()))
                .value(raw.value())
                .label(raw.label())
                .sourceReference(raw.sourceReference())
                .confidence(RuleConfidence.valueOf(raw.confidence()))
                .sourceHash(hash)
                .extractionVersion(PROMPT_VERSION)
                .build();
    }

    private String computeHash(EligibilityRuleExtractionInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(input.title()));
        sb.append(safe(input.summary()));
        sb.append(safe(input.supportTarget()));
        sb.append(safe(input.selectionCriteria()));
        sb.append(safe(input.supportContent()));
        sb.append(safe(input.body()));
        input.attachmentChunks().forEach(c -> sb.append(safe(c.content())));
        sb.append("|prompt:").append(PROMPT_VERSION);
        return sha256(sb.toString());
    }

    private String safe(String s) {
        return s == null ? "" : s;
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
}
