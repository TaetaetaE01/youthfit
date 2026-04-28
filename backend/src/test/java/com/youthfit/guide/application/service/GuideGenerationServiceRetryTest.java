package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuideSourceField;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.application.port.IncomeBracketReferenceLoader;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideGenerationServiceRetryTest {

    @Test
    void 일차_위반시_재시도_호출_이차_통과시_이차_저장() {
        GuideRepository guideRepo = mock(GuideRepository.class);
        PolicyRepository policyRepo = mock(PolicyRepository.class);
        PolicyDocumentRepository docRepo = mock(PolicyDocumentRepository.class);
        GuideLlmProvider llm = mock(GuideLlmProvider.class);
        IncomeBracketReferenceLoader refLoader = mock(IncomeBracketReferenceLoader.class);
        IncomeBracketAnnotator annotator = mock(IncomeBracketAnnotator.class);
        when(annotator.annotate(any(GuideContent.class), any(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0));

        Policy policy = Policy.builder()
                .title("X")
                .referenceYear(2025)
                .selectionCriteria("비어있지 않은 텍스트")
                .body("정책 본문")
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        when(policyRepo.findById(1L)).thenReturn(Optional.of(policy));
        when(docRepo.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(refLoader.findByYear(2025)).thenReturn(Optional.of(refOf()));
        when(guideRepo.findByPolicyId(1L)).thenReturn(Optional.empty());

        // 1차 응답: highlights 1개 (부족) + 정상
        GuideContent firstResponse = contentWithHighlights(1);
        // 2차 응답: highlights 3개 (통과)
        GuideContent secondResponse = contentWithHighlights(3);

        when(llm.generateGuide(any())).thenReturn(firstResponse);
        when(llm.regenerateWithFeedback(any(), any())).thenReturn(secondResponse);

        GuideGenerationService service = new GuideGenerationService(
                guideRepo, policyRepo, docRepo, llm, new GuideValidator(), refLoader, annotator);

        service.generateGuide(new GenerateGuideCommand(1L, "X", "x"));

        verify(llm).generateGuide(any());
        verify(llm).regenerateWithFeedback(any(), any());
        ArgumentCaptor<Guide> savedCaptor = ArgumentCaptor.forClass(Guide.class);
        verify(guideRepo).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getContent().highlights()).hasSize(3);
    }

    @Test
    void 이차도_위반시_위반_적은_쪽_저장() {
        GuideRepository guideRepo = mock(GuideRepository.class);
        PolicyRepository policyRepo = mock(PolicyRepository.class);
        PolicyDocumentRepository docRepo = mock(PolicyDocumentRepository.class);
        GuideLlmProvider llm = mock(GuideLlmProvider.class);
        IncomeBracketReferenceLoader refLoader = mock(IncomeBracketReferenceLoader.class);
        IncomeBracketAnnotator annotator = mock(IncomeBracketAnnotator.class);
        when(annotator.annotate(any(GuideContent.class), any(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0));

        Policy policy = Policy.builder()
                .title("X")
                .referenceYear(2025)
                .body("정책 본문")
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        when(policyRepo.findById(1L)).thenReturn(Optional.of(policy));
        when(docRepo.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(refLoader.findByYear(2025)).thenReturn(Optional.of(refOf()));
        when(guideRepo.findByPolicyId(1L)).thenReturn(Optional.empty());

        GuideContent first = contentWithHighlights(1);   // 위반 1
        GuideContent second = contentWithHighlights(0);  // 위반 1 (highlights 0)

        when(llm.generateGuide(any())).thenReturn(first);
        when(llm.regenerateWithFeedback(any(), any())).thenReturn(second);

        new GuideGenerationService(guideRepo, policyRepo, docRepo, llm, new GuideValidator(), refLoader, annotator)
                .generateGuide(new GenerateGuideCommand(1L, "X", "x"));

        ArgumentCaptor<Guide> savedCaptor = ArgumentCaptor.forClass(Guide.class);
        verify(guideRepo).save(savedCaptor.capture());
        // 동률이거나 1차가 적으면 1차 우선 — first(highlights 1) 가 second(0) 보다 violation 적음
        assertThat(savedCaptor.getValue().getContent().highlights()).hasSize(1);
    }

    @Test
    void retry_후_finalResponse에_annotate를_1회_호출한다() {
        GuideRepository guideRepo = mock(GuideRepository.class);
        PolicyRepository policyRepo = mock(PolicyRepository.class);
        PolicyDocumentRepository docRepo = mock(PolicyDocumentRepository.class);
        GuideLlmProvider llm = mock(GuideLlmProvider.class);
        IncomeBracketReferenceLoader refLoader = mock(IncomeBracketReferenceLoader.class);
        IncomeBracketAnnotator annotator = mock(IncomeBracketAnnotator.class);
        when(annotator.annotate(any(GuideContent.class), any(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(0));

        Policy policy = Policy.builder()
                .title("X")
                .referenceYear(2025)
                .body("정책 본문")
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        when(policyRepo.findById(1L)).thenReturn(Optional.of(policy));
        when(docRepo.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(refLoader.findByYear(2025)).thenReturn(Optional.of(refOf()));
        when(guideRepo.findByPolicyId(1L)).thenReturn(Optional.empty());

        // 1차 위반(highlights 1) → 2차 통과(highlights 3) 시나리오
        GuideContent first = contentWithHighlights(1);
        GuideContent second = contentWithHighlights(3);
        when(llm.generateGuide(any())).thenReturn(first);
        when(llm.regenerateWithFeedback(any(), any())).thenReturn(second);

        new GuideGenerationService(guideRepo, policyRepo, docRepo, llm, new GuideValidator(), refLoader, annotator)
                .generateGuide(new GenerateGuideCommand(1L, "X", "x"));

        // retry 발생해도 annotate 는 finalResponse 에 대해 단 1회 호출되어야 함
        verify(annotator, times(1))
                .annotate(any(GuideContent.class), any(IncomeBracketReference.class), anyLong());
    }

    private IncomeBracketReference refOf() {
        return new IncomeBracketReference(2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_208L)),
                Map.of(HouseholdSize.ONE, 1_196_007L));
    }

    private GuideContent contentWithHighlights(int n) {
        List<GuideHighlight> hs = new ArrayList<>();
        for (int i = 0; i < n; i++) hs.add(new GuideHighlight("h" + i, GuideSourceField.BODY));
        return new GuideContent("요약", hs, null, null, null, List.of());
    }
}
