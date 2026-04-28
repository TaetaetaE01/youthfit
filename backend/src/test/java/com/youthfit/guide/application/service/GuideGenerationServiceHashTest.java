package com.youthfit.guide.application.service;

import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.rag.domain.model.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationServiceHashTest {

    @Test
    void hash_referenceData_version이_바뀌면_달라진다() {
        Policy policy = somePolicy();
        List<PolicyDocument> chunks = List.of();
        IncomeBracketReference v1 = ref(2025, 1);
        IncomeBracketReference v2 = ref(2025, 2);

        String h1 = GuideGenerationService.computeHashForTest(policy, chunks, v1);
        String h2 = GuideGenerationService.computeHashForTest(policy, chunks, v2);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hash_referenceData_연도가_바뀌면_달라진다() {
        Policy policy = somePolicy();
        IncomeBracketReference y2025 = ref(2025, 1);
        IncomeBracketReference y2026 = ref(2026, 1);

        String h1 = GuideGenerationService.computeHashForTest(policy, List.of(), y2025);
        String h2 = GuideGenerationService.computeHashForTest(policy, List.of(), y2026);

        assertThat(h1).isNotEqualTo(h2);
    }

    private IncomeBracketReference ref(int year, int version) {
        return new IncomeBracketReference(year, version,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_400_000L)),
                Map.of(HouseholdSize.ONE, 1_100_000L));
    }

    private Policy somePolicy() {
        return Policy.builder()
                .title("X")
                .referenceYear(2025)
                .build();
    }
}
