package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEnrichmentTest {

    @Test
    void status_ok_and_confidence_above_threshold_is_exposable() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                0.8, EnrichmentStatus.OK, null, List.of());
        assertThat(e.isExposable()).isTrue();
    }

    @Test
    void status_ok_but_low_confidence_is_not_exposable() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                0.4, EnrichmentStatus.OK, null, List.of());
        assertThat(e.isExposable()).isFalse();
    }

    @Test
    void status_ok_but_null_confidence_is_not_exposable() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                null, EnrichmentStatus.OK, null, List.of());
        assertThat(e.isExposable()).isFalse();
    }

    @Test
    void non_ok_status_is_not_exposable_even_with_high_confidence() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                0.95, EnrichmentStatus.LOW_CONFIDENCE, null, List.of());
        assertThat(e.isExposable()).isFalse();
    }
}
