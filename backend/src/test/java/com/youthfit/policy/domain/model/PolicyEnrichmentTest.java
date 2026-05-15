package com.youthfit.policy.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEnrichmentTest {

    private static ObjectMapper jacksonWithJavaTime() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        return m;
    }

    @Test
    void serialization_does_not_leak_isExposable_as_property() throws Exception {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.parse("2026-05-12T15:30:00Z"),
                "openai:gpt-4o-mini", 0.85, EnrichmentStatus.OK, null, List.of(), null);
        String json = jacksonWithJavaTime().writeValueAsString(e);
        assertThat(json).doesNotContain("\"exposable\"");
    }

    @Test
    void deserialization_succeeds_for_jsonb_roundtrip() throws Exception {
        var original = new PolicyEnrichment(
                "https://example.com", Instant.parse("2026-05-12T15:30:00Z"),
                "openai:gpt-4o-mini", 0.85, EnrichmentStatus.OK,
                new PolicyEnrichment.Sections("청년", "월 20만원", "온라인", null, null, null, null, null, null),
                List.of(new PolicyEnrichment.ExtraAttachment("apply.hwp", "https://example.com/x.hwp")), null);
        ObjectMapper m = jacksonWithJavaTime();
        String json = m.writeValueAsString(original);
        PolicyEnrichment roundtripped = m.readValue(json, PolicyEnrichment.class);
        assertThat(roundtripped).isEqualTo(original);
    }


    @Test
    void status_ok_and_confidence_above_threshold_is_exposable() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                0.8, EnrichmentStatus.OK, null, List.of(), null);
        assertThat(e.isExposable()).isTrue();
    }

    @Test
    void status_ok_but_low_confidence_is_not_exposable() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                0.4, EnrichmentStatus.OK, null, List.of(), null);
        assertThat(e.isExposable()).isFalse();
    }

    @Test
    void status_ok_but_null_confidence_is_not_exposable() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                null, EnrichmentStatus.OK, null, List.of(), null);
        assertThat(e.isExposable()).isFalse();
    }

    @Test
    void non_ok_status_is_not_exposable_even_with_high_confidence() {
        var e = new PolicyEnrichment(
                "https://example.com", Instant.now(), "openai:gpt-4o-mini",
                0.95, EnrichmentStatus.LOW_CONFIDENCE, null, List.of(), null);
        assertThat(e.isExposable()).isFalse();
    }

    @Test
    void cleanedText_는_nullable_이다() {
        PolicyEnrichment enrichment = new PolicyEnrichment(
                "https://example.com",
                Instant.now(),
                "extractor",
                0.9,
                EnrichmentStatus.OK,
                null,
                List.of(),
                null
        );
        assertThat(enrichment.cleanedText()).isNull();
    }

    @Test
    void cleanedText_는_값을_그대로_보관한다() {
        String text = "외부 페이지에서 정제된 본문";
        PolicyEnrichment enrichment = new PolicyEnrichment(
                "https://example.com",
                Instant.now(),
                "extractor",
                0.9,
                EnrichmentStatus.OK,
                null,
                List.of(),
                text
        );
        assertThat(enrichment.cleanedText()).isEqualTo(text);
    }
}
