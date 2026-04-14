package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.repository.GuideRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuideGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationService.class);

    private final GuideRepository guideRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final GuideLlmProvider guideLlmProvider;

    @Transactional(readOnly = true)
    public Optional<GuideResult> findGuideByPolicyId(Long policyId) {
        return guideRepository.findByPolicyId(policyId)
                .map(GuideResult::from);
    }

    @Transactional
    public GuideGenerationResult generateGuide(GenerateGuideCommand command) {
        List<PolicyDocument> chunks = policyDocumentRepository
                .findByPolicyIdOrderByChunkIndex(command.policyId());

        if (chunks.isEmpty()) {
            return new GuideGenerationResult(command.policyId(), false,
                    "인덱싱된 문서가 없습니다");
        }

        String combinedContent = chunks.stream()
                .map(PolicyDocument::getContent)
                .collect(Collectors.joining("\n\n"));

        String contentHash = computeHash(combinedContent);

        Optional<Guide> existing = guideRepository.findByPolicyId(command.policyId());
        if (existing.isPresent() && !existing.get().hasChanged(contentHash)) {
            log.info("가이드 변경 없음, 재생성 스킵: policyId={}", command.policyId());
            return new GuideGenerationResult(command.policyId(), false,
                    "문서 변경 없음");
        }

        String summaryHtml = guideLlmProvider.generateGuideSummary(
                command.policyTitle(), combinedContent);

        if (existing.isPresent()) {
            existing.get().regenerate(summaryHtml, contentHash);
            guideRepository.save(existing.get());
            log.info("가이드 재생성 완료: policyId={}", command.policyId());
        } else {
            Guide guide = Guide.builder()
                    .policyId(command.policyId())
                    .summaryHtml(summaryHtml)
                    .sourceHash(contentHash)
                    .build();
            guideRepository.save(guide);
            log.info("가이드 신규 생성 완료: policyId={}", command.policyId());
        }

        return new GuideGenerationResult(command.policyId(), true, "생성 완료");
    }

    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
