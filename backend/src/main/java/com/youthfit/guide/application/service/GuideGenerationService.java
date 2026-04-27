package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.repository.GuideRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuideGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationService.class);

    private final GuideRepository guideRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final PolicyRepository policyRepository;
    private final GuideLlmProvider guideLlmProvider;

    @Transactional(readOnly = true)
    public Optional<GuideResult> findGuideByPolicyId(Long policyId) {
        return guideRepository.findByPolicyId(policyId)
                .map(GuideResult::from);
    }

    @Transactional
    public GuideGenerationResult generateGuide(GenerateGuideCommand command) {
        var policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new IllegalArgumentException("정책을 찾을 수 없습니다"));

        List<PolicyDocument> chunks = policyDocumentRepository
                .findByPolicyIdOrderByChunkIndex(command.policyId());

        if (chunks.isEmpty()) {
            return new GuideGenerationResult(command.policyId(), false,
                    "인덱싱된 문서가 없습니다");
        }

        String contentHash = computeHash(chunks.stream()
                .map(PolicyDocument::getContent)
                .collect(Collectors.joining("\n\n")));

        Optional<Guide> existing = guideRepository.findByPolicyId(command.policyId());
        if (existing.isPresent() && !existing.get().hasChanged(contentHash)) {
            log.info("가이드 변경 없음, 재생성 스킵: policyId={}", command.policyId());
            return new GuideGenerationResult(command.policyId(), false,
                    "문서 변경 없음");
        }

        GuideGenerationInput input = GuideGenerationInput.of(policy, chunks);
        GuideContent content = guideLlmProvider.generateGuide(input);

        if (existing.isPresent()) {
            existing.get().regenerate(content, contentHash);
            guideRepository.save(existing.get());
            log.info("가이드 재생성 완료: policyId={}", command.policyId());
        } else {
            Guide guide = Guide.builder()
                    .policyId(command.policyId())
                    .content(content)
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
