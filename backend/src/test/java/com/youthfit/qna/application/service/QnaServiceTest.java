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
import com.youthfit.qna.application.event.QnaCacheLookupEvent;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.domain.model.QnaFailedReason;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
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
import org.springframework.context.ApplicationEventPublisher;
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
    @Mock private QnaCacheLookupClassifier lookupClassifier;
    @Mock private QuestionNormalizer questionNormalizer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private QueryRewriter queryRewriter;
    @Mock private QueryRewriteProperties queryRewriteProperties;

    private Policy policy;

    @BeforeEach
    void setUp() {
        policy = mockPolicy(10L, "테스트 정책");
        given(qnaProperties.relevanceDistanceThreshold()).willReturn(0.4);
        given(qnaProperties.semanticDistanceThreshold()).willReturn(0.15);
        given(queryRewriteProperties.enabled()).willReturn(false);
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
                    List.of(),
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
            ArgumentCaptor<String> answerCaptor = ArgumentCaptor.forClass(String.class);
            verify(historyWriter).markCompleted(eq(99L), answerCaptor.capture(), anyString());
            assertThat(answerCaptor.getValue())
                    .startsWith("답변 일부.")
                    .endsWith("📞 문의: 보건복지부 · 02-123-4567");
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

        @Test
        @DisplayName("청크 통과율 0건 + 메타 답변일 때 sources 에 정책 기본 정보 entry 1개 포함")
        void emptyPassingChunks_addsMetaSourceEntry() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
                    chunk(0.85),
                    chunk(0.9)
            ));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("청년내일저축계좌는 만 19~34세 청년을 대상으로...");

            given(objectMapper.writeValueAsString(any())).willAnswer(inv -> {
                Object arg = inv.getArgument(0);
                if (arg instanceof java.util.List<?> list && !list.isEmpty()) {
                    QnaSourceResult first = (QnaSourceResult) list.get(0);
                    return "[{\"label\":\"" + first.attachmentLabel() + "\"}]";
                }
                return "[]";
            });

            AskQuestionCommand command = new AskQuestionCommand(10L, "이 정책 뭐야?", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(qnaLlmProvider, times(1)).generateAnswer(
                    anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            // sources 가 캐시 put 시 전달되는지 확인
            ArgumentCaptor<CachedAnswer> answerCaptor = ArgumentCaptor.forClass(CachedAnswer.class);
            verify(qnaAnswerCache).put(eq(10L), eq("이 정책 뭐야?"), answerCaptor.capture());
            java.util.List<QnaSourceResult> capturedSources = answerCaptor.getValue().sources();
            assertThat(capturedSources).hasSize(1);
            assertThat(capturedSources.get(0).attachmentLabel()).isEqualTo("정책 기본 정보");
            assertThat(capturedSources.get(0).policyId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("LLM 이 fallback 메시지 반환 시 sources 비우기 (출처 모순 방지)")
        void fallbackAnswer_emptiesSources() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.6)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다.");
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            AskQuestionCommand command = new AskQuestionCommand(10L, "오늘 점심 뭐 먹지?", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            ArgumentCaptor<CachedAnswer> answerCaptor = ArgumentCaptor.forClass(CachedAnswer.class);
            verify(qnaAnswerCache).put(eq(10L), eq("오늘 점심 뭐 먹지?"), answerCaptor.capture());
            assertThat(answerCaptor.getValue().sources()).isEmpty();
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
            given(questionNormalizer.normalize(anyString())).willReturn("재학생도 가능");
            CachedAnswer cached = new CachedAnswer(
                    "이전 답변(의미 일치)",
                    List.of(new QnaSourceResult(10L, null, null, null, null, "발췌")),
                    List.of(),
                    Instant.now()
            );
            SemanticLookupResult hitResult = SemanticLookupResult.hit(
                    new com.youthfit.qna.application.port.dto.SemanticLookupMatch(
                            1L,
                            java.math.BigDecimal.valueOf(0.925),
                            java.math.BigDecimal.valueOf(0.075)
                    ), cached);
            given(semanticQnaCache.findSimilar(eq(10L), eq("재학생도 가능?"), eq(embedding)))
                    .willReturn(hitResult);
            given(lookupClassifier.classify(hitResult)).willReturn(LookupResultType.HIT);
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
            verify(eventPublisher).publishEvent(any(QnaCacheLookupEvent.class));
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
            given(questionNormalizer.normalize(anyString())).willReturn("질문");
            given(semanticQnaCache.findSimilar(eq(10L), eq("질문"), eq(embedding))).willReturn(SemanticLookupResult.miss());
            given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS);
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
            verify(eventPublisher).publishEvent(any(QnaCacheLookupEvent.class));
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
            given(questionNormalizer.normalize(anyString())).willReturn("질문");
            given(semanticQnaCache.findSimilar(anyLong(), anyString(), any()))
                    .willThrow(new RuntimeException("DB 장애"));
            // when exception occurs, lookupResult = SemanticLookupResult.miss()
            given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS);
            given(ragSearchService.searchRelevantChunks(any(), eq(embedding))).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("LLM 답변");
            given(objectMapper.writeValueAsString(any())).willReturn("[]");
            given(policyDocumentRepository.findSourceHashByPolicyId(anyLong())).willReturn(Optional.of("hash-abc"));

            AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
            qnaService.askQuestion(command);
            Thread.sleep(200);

            verify(qnaLlmProvider, times(1)).generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
            verify(eventPublisher).publishEvent(any(QnaCacheLookupEvent.class));
        }
    }

    @Nested
    @DisplayName("푸터 첨부")
    class ContactFooter {

        @Test
        @DisplayName("정상 답변 + organization/contact 있음 → 캐시에 푸터 포함")
        void footer_appended_when_metadata_present() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("정상 답변");
                        return "정상 답변";
                    });
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            ArgumentCaptor<CachedAnswer> cacheCaptor = ArgumentCaptor.forClass(CachedAnswer.class);

            qnaService.askQuestion(new AskQuestionCommand(10L, "신청 자격?", 1L));
            Thread.sleep(200);

            verify(qnaAnswerCache).put(eq(10L), anyString(), cacheCaptor.capture());
            assertThat(cacheCaptor.getValue().answer()).contains("📞 문의: ");
        }
    }

    /**
     * 주의: 이 테스트들은 follow-up LLM 호출 결과가 캐시에 들어가는지만 검증한다.
     * SUGGESTIONS SSE 이벤트가 실제로 emitter 로 송출되는지는 SseEmitter 가
     * QnaService.askQuestion 내부에서 생성되어 주입 불가능한 구조라
     * 이 슬라이스 테스트로는 검증 못한다. 수동 e2e 또는 Spring web layer 테스트 필요.
     */
    @Nested
    @DisplayName("후속 추천질문")
    class FollowUps {

        @Test
        @DisplayName("정상 답변 → follow-up LLM 호출 + 캐시에 저장")
        void followUps_generated_for_normal_answer() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("정상 답변");
                        return "정상 답변";
                    });
            given(qnaLlmProvider.generateFollowUpQuestions(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(List.of("후속A", "후속B"));
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            ArgumentCaptor<CachedAnswer> cacheCaptor = ArgumentCaptor.forClass(CachedAnswer.class);

            qnaService.askQuestion(new AskQuestionCommand(10L, "신청 자격?", 1L));
            Thread.sleep(200);

            verify(qnaLlmProvider).generateFollowUpQuestions(anyString(), eq("신청 자격?"), anyString(), anyString());
            verify(qnaAnswerCache).put(eq(10L), anyString(), cacheCaptor.capture());
            assertThat(cacheCaptor.getValue().followUpQuestions()).containsExactly("후속A", "후속B");
        }

        @Test
        @DisplayName("follow-up 호출 시 답변 LLM 에 전달한 본문 컨텍스트가 동일하게 전달된다 (grounding)")
        void followUps_receiveSameContextAsAnswerLlm() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willReturn("정상 답변");
            given(qnaLlmProvider.generateFollowUpQuestions(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(List.of("후속A"));
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            qnaService.askQuestion(new AskQuestionCommand(10L, "질문?", 1L));
            Thread.sleep(200);

            ArgumentCaptor<String> answerContextCaptor = ArgumentCaptor.forClass(String.class);
            verify(qnaLlmProvider).generateAnswer(
                    anyString(), any(PolicyMetadata.class), answerContextCaptor.capture(), anyString(), any());

            ArgumentCaptor<String> followUpContextCaptor = ArgumentCaptor.forClass(String.class);
            verify(qnaLlmProvider).generateFollowUpQuestions(
                    anyString(), anyString(), anyString(), followUpContextCaptor.capture());

            assertThat(followUpContextCaptor.getValue()).isEqualTo(answerContextCaptor.getValue());
        }

        @Test
        @DisplayName("fallback 답변 → follow-up 호출 스킵")
        void followUps_skipped_for_fallback() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            String fallback = "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다.";
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept(fallback);
                        return fallback;
                    });
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            qnaService.askQuestion(new AskQuestionCommand(10L, "내가 받을 수 있나요?", 1L));
            Thread.sleep(200);

            verify(qnaLlmProvider, never()).generateFollowUpQuestions(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("follow-up LLM 빈 리스트 반환 → 본문 답변/캐시 정상, follow-up 만 빈 리스트")
        void followUps_empty_graceful() throws Exception {
            cacheMissDefaults();
            given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
            given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                    .willAnswer(inv -> {
                        Consumer<String> consumer = inv.getArgument(4);
                        consumer.accept("정상 답변");
                        return "정상 답변";
                    });
            given(qnaLlmProvider.generateFollowUpQuestions(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(List.of());
            given(objectMapper.writeValueAsString(any())).willReturn("[]");

            ArgumentCaptor<CachedAnswer> cacheCaptor = ArgumentCaptor.forClass(CachedAnswer.class);

            qnaService.askQuestion(new AskQuestionCommand(10L, "질문?", 1L));
            Thread.sleep(200);

            verify(qnaAnswerCache).put(eq(10L), anyString(), cacheCaptor.capture());
            assertThat(cacheCaptor.getValue().answer()).contains("정상 답변");
            assertThat(cacheCaptor.getValue().followUpQuestions()).isEmpty();
        }
    }

    private void cacheMissDefaults() {
        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
        given(semanticQnaCache.findSimilar(anyLong(), anyString(), any())).willReturn(SemanticLookupResult.miss());
        given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS);
        given(questionNormalizer.normalize(anyString())).willReturn("normalized");
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
