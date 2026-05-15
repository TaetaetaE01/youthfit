package com.youthfit.guide.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.config.CostGuardProperties;
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.application.port.IncomeBracketReferenceLoader;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuideGenerationServiceTest {

    @Mock GuideRepository guideRepository;
    @Mock PolicyRepository policyRepository;
    @Mock PolicyDocumentRepository policyDocumentRepository;
    @Mock GuideLlmProvider guideLlmProvider;
    @Mock GuideValidator guideValidator;
    @Mock IncomeBracketReferenceLoader referenceLoader;
    @Mock IncomeBracketAnnotator incomeBracketAnnotator;
    @Spy CostGuard costGuard = new CostGuard(new CostGuardProperties(""));

    @InjectMocks GuideGenerationService service;

    private IncomeBracketReference sampleReference() {
        return new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_000L)),
                Map.of(HouseholdSize.ONE, 1_196_000L));
    }

    private Policy samplePolicy() {
        Policy policy = Policy.builder()
                .title("청년 월세 지원")
                .summary("월세 부담 완화")
                .body("만 19세 이상 34세 이하 …")
                .supportTarget("만 19세 이상 34세 이하의 무주택 세대주")
                .selectionCriteria(null)
                .supportContent("매월 최대 20만원, 최대 12개월")
                .category(Category.HOUSING)
                .regionCode("11000")
                .referenceYear(2025)
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
    }

    private GuideContent sampleContent() {
        GuideGroup group = new GuideGroup(null, List.of("만 19~34세"));
        return new GuideContent(
                "청년 월세 지원",
                List.of(),
                new GuidePairedSection(List.of(group)),
                null, null,
                null, null, null, null,
                List.of());
    }

    @Test
    void PROMPT_VERSION_은_v5_이다() {
        assertThat(GuideGenerationService.PROMPT_VERSION).isEqualTo("v5");
    }

    @Test
    void Policy_없으면_NOT_FOUND_결과() {
        when(policyRepository.findById(99L)).thenReturn(Optional.empty());

        GuideGenerationResult result = service.generateGuide(new GenerateGuideCommand(99L, "x", "x"));

        assertThat(result.regenerated()).isFalse();
        assertThat(result.message()).contains("정책");
        verify(guideLlmProvider, never()).generateGuide(any());
    }

    @Test
    void 청크가_비어있어도_가이드_생성() {
        Policy policy = samplePolicy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(referenceLoader.findByYear(2025)).thenReturn(Optional.of(sampleReference()));
        when(guideRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(guideLlmProvider.generateGuide(any())).thenReturn(sampleContent());
        when(guideValidator.validate(any(), any()))
                .thenReturn(new GuideValidator.ValidationReport(false, false, false, List.of()));
        when(guideValidator.filterInvalidSourceFields(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(guideValidator.findMissingNumericTokens(any(), any())).thenReturn(List.of());
        when(guideValidator.containsFriendlyTone(any())).thenReturn(false);
        when(incomeBracketAnnotator.annotate(any(GuideContent.class), any(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0));

        GuideGenerationResult result = service.generateGuide(new GenerateGuideCommand(1L, "청년 월세 지원", "x"));

        assertThat(result.regenerated()).isTrue();
        verify(guideRepository, times(1)).save(any(Guide.class));
    }

    @Test
    void generateGuide_가_finalResponse에_annotate를_1회_호출한다() {
        Policy policy = samplePolicy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(referenceLoader.findByYear(2025)).thenReturn(Optional.of(sampleReference()));
        when(guideRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(guideLlmProvider.generateGuide(any())).thenReturn(sampleContent());
        when(guideValidator.validate(any(), any()))
                .thenReturn(new GuideValidator.ValidationReport(false, false, false, List.of()));
        when(guideValidator.filterInvalidSourceFields(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(guideValidator.findMissingNumericTokens(any(), any())).thenReturn(List.of());
        when(guideValidator.containsFriendlyTone(any())).thenReturn(false);
        when(incomeBracketAnnotator.annotate(any(GuideContent.class), any(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.generateGuide(new GenerateGuideCommand(1L, "청년 월세 지원", "x"));

        verify(incomeBracketAnnotator, times(1))
                .annotate(any(GuideContent.class), any(IncomeBracketReference.class), eq(1L));
    }

    @Test
    void sourceHash_동일하면_재생성_스킵() {
        Policy policy = samplePolicy();
        IncomeBracketReference reference = sampleReference();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(referenceLoader.findByYear(2025)).thenReturn(Optional.of(reference));

        // 첫 호출에서 같은 hash가 이미 존재한다고 가정
        Guide existing = Guide.builder()
                .policyId(1L)
                .content(sampleContent())
                .sourceHash(GuideGenerationService.computeHashForTest(policy, List.of(), reference))
                .build();
        when(guideRepository.findByPolicyId(1L)).thenReturn(Optional.of(existing));

        GuideGenerationResult result = service.generateGuide(new GenerateGuideCommand(1L, "청년 월세 지원", "x"));

        assertThat(result.regenerated()).isFalse();
        verify(guideLlmProvider, never()).generateGuide(any());
    }

    @Test
    void enrichment_fetchedAt이_다른_정책은_hash가_다르다() {
        Policy p1 = policyWithEnrichment(Instant.parse("2026-05-12T04:00:00Z"));
        Policy p2 = policyWithEnrichment(Instant.parse("2026-05-13T04:00:00Z"));

        String h1 = GuideGenerationService.computeHashForTest(p1, List.of(), null);
        String h2 = GuideGenerationService.computeHashForTest(p2, List.of(), null);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void enrichment이_같으면_hash가_같다() {
        Policy p1 = policyWithEnrichment(Instant.parse("2026-05-12T04:00:00Z"));
        Policy p2 = policyWithEnrichment(Instant.parse("2026-05-12T04:00:00Z"));

        String h1 = GuideGenerationService.computeHashForTest(p1, List.of(), null);
        String h2 = GuideGenerationService.computeHashForTest(p2, List.of(), null);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void enrichment_isExposable이_false면_hash에_enrichment_미포함() {
        Policy p1 = policyWithLowConfidenceEnrichment(Instant.parse("2026-05-12T04:00:00Z"));
        Policy p2 = policyWithoutEnrichment();

        String h1 = GuideGenerationService.computeHashForTest(p1, List.of(), null);
        String h2 = GuideGenerationService.computeHashForTest(p2, List.of(), null);

        assertThat(h1).isEqualTo(h2);
    }

    private Policy policyWithEnrichment(Instant fetchedAt) {
        Policy policy = samplePolicy();
        policy.replaceEnrichment(new PolicyEnrichment(
                "https://example.com/policy/1",
                fetchedAt,
                "test-extractor",
                0.85,
                EnrichmentStatus.OK,
                new PolicyEnrichment.Sections("대상", "내용", "신청 방법", "서류", "마감 비고", null, null, null, null),
                List.of(),
                null
        ));
        return policy;
    }

    private Policy policyWithLowConfidenceEnrichment(Instant fetchedAt) {
        Policy policy = samplePolicy();
        policy.replaceEnrichment(new PolicyEnrichment(
                "https://example.com/policy/1",
                fetchedAt,
                "test-extractor",
                0.4,
                EnrichmentStatus.LOW_CONFIDENCE,
                new PolicyEnrichment.Sections("대상", "내용", "신청 방법", "서류", "마감 비고", null, null, null, null),
                List.of(),
                null
        ));
        return policy;
    }

    private Policy policyWithoutEnrichment() {
        return samplePolicy();
    }
}
