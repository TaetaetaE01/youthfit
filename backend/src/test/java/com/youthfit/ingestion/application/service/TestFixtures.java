package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.PolicyEnrichment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IngestionServicePolicyProcessingStepTest 전용 픽스처.
 * `IngestPolicyCommand` 생성자 인자 순서는 main 의 record 와 동기화돼야 한다.
 */
final class TestFixtures {

    private TestFixtures() {
    }

    static IngestPolicyCommand minimalCommand() {
        return baseCommand(null);
    }

    static IngestPolicyCommand commandWithEnrichmentStatus(EnrichmentStatus status) {
        PolicyEnrichment enrichment = new PolicyEnrichment(
                "https://ref.example.com",
                Instant.now(),
                "test-extractor",
                0.85,
                status,
                null,
                List.of(),
                "cleaned"
        );
        return baseCommand(enrichment);
    }

    private static IngestPolicyCommand baseCommand(PolicyEnrichment enrichment) {
        return new IngestPolicyCommand(
                "https://example.com/policy/1",
                "YOUTH_SEOUL_CRAWL",
                LocalDateTime.now(),
                "ext-1",
                "테스트 정책",
                "요약",
                "본문",
                "복지",
                "서울",
                null, null, null, null, null,
                "기관", "연락처",
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                null,
                null,
                enrichment,
                null
        );
    }
}
