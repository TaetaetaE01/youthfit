package com.youthfit.guide.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.application.port.IncomeBracketReferenceLoader;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuideGenerationService {

    static final String PROMPT_VERSION = "v3";  // 프롬프트 / 스키마 변경 시 증분
    static final String ANNOTATOR_VERSION = "v1";  // 환산값 후처리기 변경 시 증분

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationService.class);

    private final GuideRepository guideRepository;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final GuideLlmProvider guideLlmProvider;
    private final GuideValidator guideValidator;
    private final IncomeBracketReferenceLoader referenceLoader;
    private final IncomeBracketAnnotator incomeBracketAnnotator;
    private final CostGuard costGuard;

    @Transactional(readOnly = true)
    public Optional<GuideResult> findGuideByPolicyId(Long policyId) {
        return guideRepository.findByPolicyId(policyId).map(GuideResult::from);
    }

    @Transactional
    public GuideGenerationResult generateGuide(GenerateGuideCommand command) {
        if (!costGuard.allows(command.policyId())) {
            costGuard.logSkip("generateGuide", command.policyId());
            return new GuideGenerationResult(command.policyId(), false, "cost-guard: allowlist 외 정책");
        }
        Optional<Policy> policyOpt = policyRepository.findById(command.policyId());
        if (policyOpt.isEmpty()) {
            return new GuideGenerationResult(command.policyId(), false, "정책을 찾을 수 없습니다");
        }
        Policy policy = policyOpt.get();
        List<PolicyDocument> chunks = policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId());

        IncomeBracketReference reference = resolveReference(policy.getReferenceYear());

        String hash = computeHash(policy, chunks, reference);
        Optional<Guide> existing = guideRepository.findByPolicyId(command.policyId());
        if (existing.isPresent() && !existing.get().hasChanged(hash)) {
            log.info("가이드 변경 없음, 재생성 스킵: policyId={}", command.policyId());
            return new GuideGenerationResult(command.policyId(), false, "변경 없음");
        }

        GuideGenerationInput input = GuideGenerationInput.of(policy, chunks, reference);
        Set<Long> validAttachmentIds = policy.getAttachments().stream()
                .map(PolicyAttachment::getId)
                .collect(Collectors.toSet());
        GuideContent firstResponse = guideLlmProvider.generateGuide(input);
        GuideValidator.ValidationReport firstReport = guideValidator.validate(firstResponse, validAttachmentIds);

        GuideContent finalResponse;
        if (firstReport.hasRetryTrigger()) {
            log.info("가이드 검증 위반으로 재시도: policyId={}, violations={}",
                    command.policyId(), firstReport.feedbackMessages());
            GuideContent secondResponse = guideLlmProvider.regenerateWithFeedback(input, firstReport.feedbackMessages());
            GuideValidator.ValidationReport secondReport = guideValidator.validate(secondResponse, validAttachmentIds);

            if (secondReport.violationCount() < firstReport.violationCount()) {
                finalResponse = secondResponse;
                if (secondReport.hasRetryTrigger()) {
                    log.warn("재시도 후에도 검증 위반 (감소): policyId={}, violations={}",
                            command.policyId(), secondReport.feedbackMessages());
                }
            } else {
                finalResponse = firstResponse;
                log.warn("재시도가 개선되지 않음, 1차 응답 저장: policyId={}", command.policyId());
            }
        } else {
            finalResponse = firstResponse;
        }

        // 결정성 후처리: 중위소득/차상위 비율을 만원 기준 환산값으로 보강
        finalResponse = incomeBracketAnnotator.annotate(finalResponse, reference, command.policyId());

        // 검증 4: sourceField 유효성 (해당 항목 폐기)
        finalResponse = filterInvalidSourceFields(finalResponse, policy);

        // 후처리 검증 (로그 위주)
        List<String> missing = guideValidator.findMissingNumericTokens(input.combinedSourceText(), finalResponse);
        if (!missing.isEmpty()) {
            log.warn("가이드 풀이에 원문 숫자 토큰 누락: policyId={}, missing={}", command.policyId(), missing);
        }
        if (guideValidator.containsFriendlyTone(finalResponse)) {
            log.warn("가이드 풀이에 친근체 출현: policyId={}", command.policyId());
        }

        if (existing.isPresent()) {
            existing.get().regenerate(finalResponse, hash);
            guideRepository.save(existing.get());
        } else {
            guideRepository.save(Guide.builder()
                    .policyId(command.policyId())
                    .content(finalResponse)
                    .sourceHash(hash)
                    .build());
        }
        log.info("가이드 생성 완료: policyId={}", command.policyId());
        return new GuideGenerationResult(command.policyId(), true, "생성 완료");
    }

    private GuideContent filterInvalidSourceFields(GuideContent c, Policy p) {
        Set<GuideSourceField> nonEmpty = new HashSet<>();
        if (notBlank(p.getSupportTarget())) nonEmpty.add(GuideSourceField.SUPPORT_TARGET);
        if (notBlank(p.getSelectionCriteria())) nonEmpty.add(GuideSourceField.SELECTION_CRITERIA);
        if (notBlank(p.getSupportContent())) nonEmpty.add(GuideSourceField.SUPPORT_CONTENT);
        if (notBlank(p.getBody())) nonEmpty.add(GuideSourceField.BODY);

        List<GuideHighlight> hs = guideValidator.filterInvalidSourceFields(
                c.highlights(), nonEmpty, GuideHighlight::sourceField);
        List<GuidePitfall> ps = guideValidator.filterInvalidSourceFields(
                c.pitfalls(), nonEmpty, GuidePitfall::sourceField);

        return new GuideContent(c.oneLineSummary(), hs, c.target(), c.criteria(), c.content(), ps);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private IncomeBracketReference resolveReference(Integer policyYear) {
        if (policyYear != null) {
            Optional<IncomeBracketReference> byYear = referenceLoader.findByYear(policyYear);
            if (byYear.isPresent()) return byYear.get();
            log.warn("referenceYear={} 에 매칭되는 yaml 없음 → findLatest() 사용", policyYear);
        }
        return referenceLoader.findLatest();
    }

    /** 테스트 노출용. computeHash는 주입된 의존성을 사용하지 않으므로 null 인자로 인스턴스 생성 안전. */
    static String computeHashForTest(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
        return new GuideGenerationService(null, null, null, null, null, null, null, null)
                .computeHash(policy, chunks, reference);
    }

    private String computeHash(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(policy.getTitle()));
        sb.append(safe(policy.getSummary()));
        sb.append(safe(policy.getBody()));
        sb.append(safe(policy.getSupportTarget()));
        sb.append(safe(policy.getSelectionCriteria()));
        sb.append(safe(policy.getSupportContent()));
        sb.append(policy.getReferenceYear());
        chunks.forEach(c -> sb.append(c.getContent()));
        if (reference != null) {
            sb.append("|ref:").append(reference.year()).append(":").append(reference.version());
        }
        sb.append("|prompt:").append(PROMPT_VERSION);
        sb.append("|annotator:").append(ANNOTATOR_VERSION);
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
