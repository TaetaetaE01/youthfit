package com.youthfit.ingestion.application.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.application.port.PolicyPeriodLlmProvider;
import com.youthfit.ingestion.domain.model.PolicyPeriod;
import com.youthfit.ingestion.domain.service.PolicyPeriodExtractor;
import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.service.PolicyIngestionService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "\\[(개요|지원대상|선정기준|지원내용)]\\s*\\n([\\s\\S]*?)(?=\\n\\[(?:개요|지원대상|선정기준|지원내용)]|$)"
    );

    private final PolicyIngestionService policyIngestionService;
    private final ObjectMapper objectMapper;
    private final PolicyPeriodExtractor policyPeriodExtractor;
    private final PolicyPeriodLlmProvider policyPeriodLlmProvider;

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

        Sections sections = parseSections(command.body());
        PolicyPeriod period = resolvePeriod(command);

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
                period.start(),
                period.end(),
                toSet(command.lifeTags()),
                toSet(command.themeTags()),
                toSet(command.targetTags()),
                mapAttachments(command.attachments()),
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

    private PolicyPeriod resolvePeriod(IngestPolicyCommand command) {
        LocalDate applyStart = command.applyStart();
        LocalDate applyEnd = command.applyEnd();
        if (applyStart != null || applyEnd != null) {
            return PolicyPeriod.of(applyStart, applyEnd);
        }
        PolicyPeriod regexPeriod = policyPeriodExtractor.extract(command.body());
        if (!regexPeriod.isEmpty()) {
            return regexPeriod;
        }
        return policyPeriodLlmProvider.extractPeriod(command.title(), command.body());
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
}
