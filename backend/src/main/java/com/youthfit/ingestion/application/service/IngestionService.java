package com.youthfit.ingestion.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.service.PolicyIngestionService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final PolicyIngestionService policyIngestionService;
    private final ObjectMapper objectMapper;

    public IngestPolicyResult receivePolicy(IngestPolicyCommand command) {
        Category category = mapCategory(command.category());
        SourceType sourceType = resolveSourceType(command.sourceType());
        String rawJson = serialize(command);
        String sourceHash = sha256(rawJson);
        String externalId = command.externalId() != null && !command.externalId().isBlank()
                ? command.externalId()
                : command.sourceUrl();
        String summary = command.summary() != null && !command.summary().isBlank()
                ? command.summary()
                : command.body();

        RegisterPolicyCommand registerCommand = new RegisterPolicyCommand(
                command.title(),
                summary,
                category,
                command.region(),
                command.applyStart(),
                command.applyEnd(),
                sourceType,
                externalId,
                command.sourceUrl(),
                rawJson,
                sourceHash
        );

        policyIngestionService.registerPolicy(registerCommand);

        return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
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

    private String serialize(IngestPolicyCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException e) {
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
}
