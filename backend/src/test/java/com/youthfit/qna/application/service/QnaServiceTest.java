package com.youthfit.qna.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.dto.command.PolicyMetadata;
import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.domain.model.QnaFailedReason;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("QnaService")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QnaServiceTest {

    @InjectMocks
    private QnaService qnaService;

    @Mock private CostGuard costGuard;
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyAttachmentRepository policyAttachmentRepository;
    @Mock private com.youthfit.rag.domain.repository.PolicyDocumentRepository policyDocumentRepository;
    @Mock private RagSearchService ragSearchService;
    @Mock private QnaLlmProvider qnaLlmProvider;
    @Mock private QnaAnswerCache qnaAnswerCache;
    @Mock private SemanticQnaCache semanticQnaCache;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private QnaHistoryWriter historyWriter;
    @Mock private QnaProperties qnaProperties;
    @Mock private ObjectMapper objectMapper;

    private Policy policy;

    @BeforeEach
    void setUp() {
        policy = mockPolicy(10L, "테스트 정책");
        given(qnaProperties.relevanceDistanceThreshold()).willReturn(0.4);
        given(qnaProperties.semanticDistanceThreshold()).willReturn(0.15);
    }

    @Nested
    @DisplayName("진입점 가드")
    class Entry {

        @Test
        @DisplayName("CostGuard 가 차단하면 LLM/RAG 호출 없이 ERROR 이벤트만 보낸다")
        void costGuardBlocked_emitsErrorOnly() throws Exception {
            given(costGuard.allows(10L)).willReturn(false);

            AskQuestionCommand command = new AskQuestionCommand(10L, "재학생도 가능?", 1L);
            SseEmitter emitter = qnaService.askQuestion(command);

            // SseEmitter 비동기 처리 대기 (간단히 sleep — 더 결정적인 방식은 ExecutorService 주입으로 동기화)
            Thread.sleep(100);

            verify(costGuard).allows(10L);
            verify(policyRepository, never()).findById(anyLong());
            verify(ragSearchService, never()).searchRelevantChunks(any());
            verify(ragSearchService, never()).searchRelevantChunks(any(), any());
            verify(embeddingProvider, never()).embed(anyString());
            verify(qnaLlmProvider, never()).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(historyWriter, never()).startInProgress(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("정책이 없으면 NOT_FOUND 예외, history 미저장")
        void policyNotFound_throws() {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.empty());

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);

            assertThatThrownBy(() -> qnaService.askQuestion(command))
                    .isInstanceOf(YouthFitException.class);
            verify(historyWriter, never()).startInProgress(anyLong(), anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("캐시 히트")
    class CacheHit {

        @Test
        @DisplayName("캐시 히트 시 LLM·RAG 호출 없이 캐시된 답변을 그대로 반환")
        void cacheHit_skipsRagAndLlm() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(1L, 10L, "재학생도 가능?")).willReturn(99L);
            CachedAnswer cached = new CachedAnswer(
                    "이전 답변",
                    List.of(new QnaSourceResult(10L, null, null, null, null, "발췌")),
                    Instant.now()
            );
            given(qnaAnswerCache.get(10L, "재학생도 가능?")).willReturn(Optional.of(cached));
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            AskQuestionCommand command = new AskQuestionCommand(10L, "재학생도 가능?", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(100);

            verify(ragSearchService, never()).searchRelevantChunks(any());
            verify(qnaLlmProvider, never()).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(embeddingProvider, never()).embed(anyString());
            verify(semanticQnaCache, never()).findSimilar(anyLong(), anyString(), any());
            verify(historyWriter).markCompleted(eq(99L), eq("이전 답변"), anyString());
            verify(qnaAnswerCache, never()).put(anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("거절 흐름")
    class Reject {

        @Test
        @DisplayName("정책에 인덱싱된 청크가 0건이면 NO_INDEXED_DOCUMENT 거절")
        void noIndexedChunks_failsWithNoIndexedDocument() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of());

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(100);

            verify(qnaLlmProvider, never()).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(historyWriter).markFailed(99L, QnaFailedReason.NO_INDEXED_DOCUMENT);
        }

        @Test
        @DisplayName("모든 청크 distance 가 임계값을 초과하면 NO_RELEVANT_CHUNK 거절")
        void allChunksOverThreshold_failsWithNoRelevantChunk() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    chunk(0.7),
                    chunk(0.9)
            ));

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(100);

            verify(qnaLlmProvider, never()).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(historyWriter).markFailed(99L, QnaFailedReason.NO_RELEVANT_CHUNK);
        }
    }

    @Nested
    @DisplayName("정상 경로")
    class Happy {

        @Test
        @DisplayName("임계값 통과 청크가 있으면 LLM 1회 호출 + 캐시 put + history COMPLETED")
        void threshold_passesAndCallsLlm() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    chunk(0.2),
                    chunk(0.6)  // 임계값 0.4 초과 — 컨텍스트에서 제외
            ));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("답변 ");
                        consumer.accept("일부.");
                        return "답변 일부.";
                    });
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(qnaLlmProvider, times(1)).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(embeddingProvider, times(1)).embed("질문");
            verify(qnaAnswerCache).put(eq(10L), eq("질문"), any(CachedAnswer.class));
            verify(semanticQnaCache).put(eq(10L), eq("질문"), eq("hash-abc"), any(), any(CachedAnswer.class));
            verify(historyWriter).markCompleted(eq(99L), eq("답변 일부."), anyString());
        }

        @Test
        @DisplayName("LLM 호출 시 PolicyMetadata 9필드가 매핑되어 전달된다")
        void llmReceivesMappedPolicyMetadata() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("LLM 답변");
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            AskQuestionCommand command = new AskQuestionCommand(10L, "이 정책 뭐야?", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            ArgumentCaptor<PolicyMetadata> captor = ArgumentCaptor.forClass(PolicyMetadata.class);
            verify(qnaLlmProvider).generateAnswer(
                    anyString(), captor.capture(), anyString(), anyString(), any());

            PolicyMetadata captured = captor.getValue();
            assertThat(captured.category()).isEqualTo("WELFARE");
            assertThat(captured.summary()).isEqualTo("저소득 청년 자산형성 지원");
            assertThat(captured.supportTarget()).isEqualTo("만 19~34세, 근로소득자");
            assertThat(captured.supportContent()).isEqualTo("월 30만원 매칭");
            assertThat(captured.organization()).isEqualTo("보건복지부");
            assertThat(captured.contact()).isEqualTo("02-123-4567");
            assertThat(captured.applyStart()).isEqualTo(java.time.LocalDate.of(2026, 5, 1));
            assertThat(captured.applyEnd()).isEqualTo(java.time.LocalDate.of(2026, 5, 31));
            assertThat(captured.provideType()).isEqualTo("현금");
        }
    }

    @Nested
    @DisplayName("LLM 에러")
    class LlmError {

        @Test
        @DisplayName("LLM 호출이 예외를 던지면 history FAILED·LLM_ERROR")
        void llmThrows_marksFailed() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willThrow(new RuntimeException("OpenAI 5xx"));

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(historyWriter).markFailed(99L, QnaFailedReason.LLM_ERROR);
            verify(qnaAnswerCache, never()).put(anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("의미 캐시")
    class SemanticCache {

        @Test
        @DisplayName("정확 캐시 미스 → 의미 캐시 히트 시 임베딩 1회 호출, RAG/LLM 호출 0회")
        void semanticHit_skipsRagAndLlm() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
            float[] embedding = new float[]{0.1f, 0.2f};
            given(embeddingProvider.embed("재학생도 가능?")).willReturn(embedding);
            CachedAnswer cached = new CachedAnswer(
                    "이전 답변(의미 일치)",
                    List.of(new QnaSourceResult(10L, null, null, null, null, "발췌")),
                    Instant.now()
            );
            given(semanticQnaCache.findSimilar(eq(10L), eq("재학생도 가능?"), eq(embedding))).willReturn(Optional.of(cached));
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            AskQuestionCommand command = new AskQuestionCommand(10L, "재학생도 가능?", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(100);

            verify(embeddingProvider, times(1)).embed("재학생도 가능?");
            verify(ragSearchService, never()).searchRelevantChunks(any());
            verify(ragSearchService, never()).searchRelevantChunks(any(), any());
            verify(qnaLlmProvider, never()).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(qnaAnswerCache, never()).put(anyLong(), anyString(), any());
            verify(semanticQnaCache, never()).put(anyLong(), anyString(), anyString(), any(), any());
            verify(historyWriter).markCompleted(eq(99L), eq("이전 답변(의미 일치)"), anyString());
        }

        @Test
        @DisplayName("의미 캐시 미스 → RAG에 동일한 임베딩이 전달되고 LLM 1회 호출 + 두 캐시 모두 put")
        void semanticMiss_passesSameEmbeddingToRag() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
            float[] embedding = new float[]{0.3f, 0.4f};
            given(embeddingProvider.embed("질문")).willReturn(embedding);
            given(semanticQnaCache.findSimilar(eq(10L), eq("질문"), eq(embedding))).willReturn(Optional.empty());
            given(ragSearchService.searchRelevantChunks(any(), eq(embedding))).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("LLM 답변");
            given(objectMapper.writeValueAsString(any())).willReturn("[]");
            given(policyDocumentRepository.findSourceHashByPolicyId(anyLong())).willReturn(Optional.of("hash-abc"));

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(embeddingProvider, times(1)).embed("질문");
            verify(ragSearchService, times(1)).searchRelevantChunks(any(), eq(embedding));
            verify(qnaLlmProvider, times(1)).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(qnaAnswerCache).put(eq(10L), eq("질문"), any(CachedAnswer.class));
            verify(semanticQnaCache).put(eq(10L), eq("질문"), eq("hash-abc"), eq(embedding), any(CachedAnswer.class));
        }

        @Test
        @DisplayName("의미 캐시 findSimilar 가 예외를 던지면 RAG 흐름으로 폴백")
        void semanticCacheError_fallsBackToRag() throws Exception {
            given(costGuard.allows(10L)).willReturn(true);
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
            given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
            float[] embedding = new float[]{0.5f};
            given(embeddingProvider.embed("질문")).willReturn(embedding);
            given(semanticQnaCache.findSimilar(anyLong(), anyString(), any()))
                    .willThrow(new RuntimeException("DB 장애"));
            given(ragSearchService.searchRelevantChunks(any(), eq(embedding))).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("LLM 답변");
            given(objectMapper.writeValueAsString(any())).willReturn("[]");
            given(policyDocumentRepository.findSourceHashByPolicyId(anyLong())).willReturn(Optional.of("hash-abc"));

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(qnaLlmProvider, times(1)).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
        }
    }

    private void cacheMissDefaults() {
        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
        given(semanticQnaCache.findSimilar(anyLong(), anyString(), any())).willReturn(Optional.empty());
        given(policyDocumentRepository.findSourceHashByPolicyId(anyLong())).willReturn(Optional.of("hash-abc"));
    }

    private static PolicyDocumentChunkResult chunk(double distance) {
        return new PolicyDocumentChunkResult(
                1L, 10L, 0, "내용", distance, null, null, null
        );
    }

    private static Policy mockPolicy(Long id, String title) {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getTitle()).willReturn(title);
        given(p.getCategory()).willReturn(com.youthfit.policy.domain.model.Category.WELFARE);
        given(p.getSummary()).willReturn("저소득 청년 자산형성 지원");
        given(p.getSupportTarget()).willReturn("만 19~34세, 근로소득자");
        given(p.getSupportContent()).willReturn("월 30만원 매칭");
        given(p.getOrganization()).willReturn("보건복지부");
        given(p.getContact()).willReturn("02-123-4567");
        given(p.getApplyStart()).willReturn(java.time.LocalDate.of(2026, 5, 1));
        given(p.getApplyEnd()).willReturn(java.time.LocalDate.of(2026, 5, 31));
        given(p.getProvideType()).willReturn("현금");
        return p;
    }
}
