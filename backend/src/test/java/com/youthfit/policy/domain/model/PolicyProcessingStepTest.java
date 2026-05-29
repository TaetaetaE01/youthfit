package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyProcessingStepTest {

    @Test
    void start_는_IN_PROGRESS_status_와_started_at_을_세팅한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.INGESTION, 1);

        assertThat(step.getPolicyId()).isEqualTo(1L);
        assertThat(step.getStep()).isEqualTo(ProcessingStep.INGESTION);
        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.IN_PROGRESS);
        assertThat(step.getAttempt()).isEqualTo(1);
        assertThat(step.getStartedAt()).isNotNull();
        assertThat(step.getFinishedAt()).isNull();
        assertThat(step.getDurationMs()).isNull();
    }

    @Test
    void finish_는_상태_종료시각_소요시간을_세팅한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.GUIDE, 1);
        Instant start = step.getStartedAt();

        step.finish(ProcessingStatus.SUCCESS, null, null);

        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(step.getFinishedAt()).isAfterOrEqualTo(start);
        assertThat(step.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(step.getReason()).isNull();
    }

    @Test
    void finish_FAILED_는_reason_을_500자로_truncate_한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.RAG_INDEXING, 1);
        String longReason = "x".repeat(800);

        step.finish(ProcessingStatus.FAILED, longReason, null);

        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(step.getReason()).hasSize(500);
    }

    @Test
    void finish_SKIPPED_는_detail_json_을_보관한다() {
        PolicyProcessingStep step = PolicyProcessingStep.start(1L, ProcessingStep.ENRICHMENT, 1);
        String detail = "{\"skippedUrls\":[{\"url\":\"https://x.com\",\"reason\":\"SPA_DETECTED\"}]}";

        step.finish(ProcessingStatus.SKIPPED, "ALL_URLS_SKIPPED", detail);

        assertThat(step.getStatus()).isEqualTo(ProcessingStatus.SKIPPED);
        assertThat(step.getReason()).isEqualTo("ALL_URLS_SKIPPED");
        assertThat(step.getDetailJson()).isEqualTo(detail);
    }
}
