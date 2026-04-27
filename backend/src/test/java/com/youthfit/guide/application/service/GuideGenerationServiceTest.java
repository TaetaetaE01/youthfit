package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("GuideGenerationService")
@ExtendWith(MockitoExtension.class)
class GuideGenerationServiceTest {

    @InjectMocks
    private GuideGenerationService guideGenerationService;

    @Mock
    private GuideRepository guideRepository;

    @Mock
    private PolicyDocumentRepository policyDocumentRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private GuideLlmProvider guideLlmProvider;

    @Nested
    @DisplayName("findGuideByPolicyId - 가이드 조회")
    class FindGuideByPolicyId {

        @Test
        @DisplayName("존재하는 가이드를 조회하면 결과를 반환한다")
        void exists_returnsGuideResult() {
            // given
            GuideContent content = new GuideContent("요약", null, null, null, List.of());
            Guide guide = createMockGuide(1L, 1L, content, "hash-v1");
            given(guideRepository.findByPolicyId(1L)).willReturn(Optional.of(guide));

            // when
            Optional<GuideResult> result = guideGenerationService.findGuideByPolicyId(1L);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().policyId()).isEqualTo(1L);
            assertThat(result.get().content()).isEqualTo(content);
        }

        @Test
        @DisplayName("존재하지 않는 가이드를 조회하면 빈 값을 반환한다")
        void notExists_returnsEmpty() {
            // given
            given(guideRepository.findByPolicyId(999L)).willReturn(Optional.empty());

            // when
            Optional<GuideResult> result = guideGenerationService.findGuideByPolicyId(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("generateGuide - 가이드 생성")
    class GenerateGuide {

        @Test
        @DisplayName("인덱싱된 문서가 없으면 생성을 스킵한다")
        void noChunks_skipsGeneration() {
            // given
            GenerateGuideCommand command = new GenerateGuideCommand(1L, "정책 제목", "내용");
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                    .willReturn(List.of());

            // when
            GuideGenerationResult result = guideGenerationService.generateGuide(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.generated()).isFalse();
            assertThat(result.reason()).contains("인덱싱된 문서가 없습니다");
            verify(guideLlmProvider, never()).generateGuide(any(GuideGenerationInput.class));
        }

        @Test
        @DisplayName("기존 가이드와 소스 해시가 동일하면 재생성을 스킵한다")
        void sameHash_skipsRegeneration() {
            // given
            GenerateGuideCommand command = new GenerateGuideCommand(1L, "정책 제목", "내용");
            PolicyDocument chunk = createChunk(1L, 0, "청크 내용");

            // 실제 SHA-256 해시 계산 (GuideGenerationService.computeHash와 동일한 결과)
            String contentHash = computeSha256("청크 내용");
            GuideContent content = new GuideContent("요약", null, null, null, List.of());
            Guide existing = createMockGuide(1L, 1L, content, contentHash);

            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                    .willReturn(List.of(chunk));
            given(guideRepository.findByPolicyId(1L)).willReturn(Optional.of(existing));

            // when
            GuideGenerationResult result = guideGenerationService.generateGuide(command);

            // then
            assertThat(result.generated()).isFalse();
            assertThat(result.reason()).contains("변경 없음");
            verify(guideLlmProvider, never()).generateGuide(any(GuideGenerationInput.class));
        }

        @Test
        @DisplayName("신규 가이드를 생성하여 저장한다")
        void newGuide_generatesAndSaves() {
            // given
            GenerateGuideCommand command = new GenerateGuideCommand(1L, "청년 주거 지원", "내용");
            PolicyDocument chunk = createChunk(1L, 0, "주거 지원 상세 내용");
            GuideContent content = new GuideContent("청년 주거 지원", null, null, null, List.of());

            Policy policy = createMockPolicy(1L, "청년 주거 지원");

            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                    .willReturn(List.of(chunk));
            given(guideRepository.findByPolicyId(1L)).willReturn(Optional.empty());
            given(guideLlmProvider.generateGuide(any(GuideGenerationInput.class)))
                    .willReturn(content);
            given(guideRepository.save(any(Guide.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            GuideGenerationResult result = guideGenerationService.generateGuide(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.generated()).isTrue();
            assertThat(result.reason()).contains("생성 완료");
            verify(guideRepository).save(any(Guide.class));
        }

        @Test
        @DisplayName("소스가 변경되면 기존 가이드를 재생성한다")
        void changedSource_regeneratesGuide() {
            // given
            GenerateGuideCommand command = new GenerateGuideCommand(1L, "청년 주거 지원", "내용");
            PolicyDocument chunk = createChunk(1L, 0, "변경된 내용");
            GuideContent oldContent = new GuideContent("기존", null, null, null, List.of());
            Guide existing = createMockGuide(1L, 1L, oldContent, "old-hash");
            GuideContent newContent = new GuideContent("새로운 가이드", null, null, null, List.of());

            Policy policy = createMockPolicy(1L, "청년 주거 지원");

            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                    .willReturn(List.of(chunk));
            given(guideRepository.findByPolicyId(1L)).willReturn(Optional.of(existing));
            given(guideLlmProvider.generateGuide(any(GuideGenerationInput.class)))
                    .willReturn(newContent);
            given(guideRepository.save(any(Guide.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            GuideGenerationResult result = guideGenerationService.generateGuide(command);

            // then
            assertThat(result.generated()).isTrue();
            assertThat(existing.getContent()).isEqualTo(newContent);
            verify(guideRepository).save(existing);
        }

        @Test
        @DisplayName("여러 청크의 내용을 결합하여 LLM에 전달한다")
        void multipleChunks_combinesContent() {
            // given
            GenerateGuideCommand command = new GenerateGuideCommand(1L, "정책", "내용");
            List<PolicyDocument> chunks = List.of(
                    createChunk(1L, 0, "첫 번째 청크"),
                    createChunk(1L, 1, "두 번째 청크")
            );
            GuideContent content = new GuideContent("결합된 가이드", null, null, null, List.of());

            Policy policy = createMockPolicy(1L, "정책");

            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(chunks);
            given(guideRepository.findByPolicyId(1L)).willReturn(Optional.empty());
            given(guideLlmProvider.generateGuide(any(GuideGenerationInput.class)))
                    .willReturn(content);
            given(guideRepository.save(any(Guide.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            GuideGenerationResult result = guideGenerationService.generateGuide(command);

            // then
            assertThat(result.generated()).isTrue();
            verify(guideLlmProvider).generateGuide(any(GuideGenerationInput.class));
        }
    }

    // ── 헬퍼 메서드 ──

    private Guide createMockGuide(Long id, Long policyId, GuideContent content, String sourceHash) {
        Guide guide = Guide.builder()
                .policyId(policyId)
                .content(content)
                .sourceHash(sourceHash)
                .build();
        ReflectionTestUtils.setField(guide, "id", id);
        return guide;
    }

    private Policy createMockPolicy(Long id, String title) {
        Policy policy = Policy.builder()
                .title(title)
                .referenceYear(2025)
                .body(title)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private PolicyDocument createChunk(Long policyId, int index, String content) {
        return PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(index)
                .content(content)
                .sourceHash("test-hash")
                .build();
    }

    private String computeSha256(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
