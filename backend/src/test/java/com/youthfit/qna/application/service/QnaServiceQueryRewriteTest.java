package com.youthfit.qna.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.dto.command.PolicyMetadata;
import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("QnaService — query-rewrite 분기")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QnaServiceQueryRewriteTest {

    @InjectMocks
    private QnaService qnaService;

    @Mock private CostGuard costGuard;
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyAttachmentRepository policyAttachmentRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;
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
        policy = mockPolicy();
        given(qnaProperties.relevanceDistanceThreshold()).willReturn(0.4);
        given(qnaProperties.semanticDistanceThreshold()).willReturn(0.15);

        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        given(questionNormalizer.normalize(anyString())).willReturn("normalized");
        given(semanticQnaCache.findSimilar(anyLong(), anyString(), any())).willReturn(SemanticLookupResult.miss());
        given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS);
        given(policyDocumentRepository.findSourceHashByPolicyId(anyLong())).willReturn(Optional.of("hash-abc"));
    }

    @Test
    @DisplayName("enabled=false 면 rewriter 호출 없음, 원래 질문으로 임베딩·검색")
    void disabled_skipsRewriter() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(false);
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("작년 기준이야?")).willReturn(originalEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(originalEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "작년 기준이야?", 1L));
        Thread.sleep(200);

        verify(queryRewriter, never()).rewrite(anyString(), anyString());
        verify(embeddingProvider, times(1)).embed("작년 기준이야?");

        ArgumentCaptor<SearchChunksCommand> searchCmdCaptor =
                ArgumentCaptor.forClass(SearchChunksCommand.class);
        verify(ragSearchService).searchRelevantChunks(searchCmdCaptor.capture(), any());
        assertThat(searchCmdCaptor.getValue().query()).isEqualTo("작년 기준이야?");
    }

    @Test
    @DisplayName("enabled=true + rewriter 정상 → rewritten query 로 임베딩·검색")
    void enabled_usesRewrittenQuery() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        given(queryRewriter.rewrite(eq("청년내일저축계좌"), eq("작년 기준이야?")))
                .willReturn(Optional.of("청년내일저축계좌 최근 3개월 평균 근로사업소득"));
        float[] rewrittenEmbedding = new float[]{0.7f, 0.8f};
        given(embeddingProvider.embed("청년내일저축계좌 최근 3개월 평균 근로사업소득"))
                .willReturn(rewrittenEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(rewrittenEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "작년 기준이야?", 1L));
        Thread.sleep(200);

        // 원래 질문으로 1회(semantic 캐시 lookup용), rewritten 으로 1회 (RAG 용) — 총 2회
        verify(embeddingProvider).embed("작년 기준이야?");
        verify(embeddingProvider).embed("청년내일저축계좌 최근 3개월 평균 근로사업소득");

        // 답변 LLM 에는 원래 질문이 전달되어야 함
        ArgumentCaptor<String> questionCaptor = ArgumentCaptor.forClass(String.class);
        verify(qnaLlmProvider).generateAnswer(
                anyString(), any(PolicyMetadata.class), anyString(), questionCaptor.capture(), any());
        assertThat(questionCaptor.getValue()).isEqualTo("작년 기준이야?");

        ArgumentCaptor<SearchChunksCommand> searchCmdCaptor =
                ArgumentCaptor.forClass(SearchChunksCommand.class);
        verify(ragSearchService).searchRelevantChunks(searchCmdCaptor.capture(), eq(rewrittenEmbedding));
        assertThat(searchCmdCaptor.getValue().query())
                .isEqualTo("청년내일저축계좌 최근 3개월 평균 근로사업소득");
    }

    @Test
    @DisplayName("enabled=true + rewriter empty → 원래 질문으로 fallback")
    void enabled_emptyRewrite_fallsBackToOriginal() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        given(queryRewriter.rewrite(anyString(), anyString())).willReturn(Optional.empty());
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("질문")).willReturn(originalEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(originalEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(200);

        verify(queryRewriter).rewrite(anyString(), eq("질문"));
        verify(embeddingProvider, times(1)).embed("질문");
    }

    @Test
    @DisplayName("enabled=true + 정확 캐시 hit → rewriter 호출 없음")
    void exactCacheHit_skipsRewriter() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        CachedAnswer cached = new CachedAnswer(
                "이전 답변", List.of(), List.of(), java.time.Instant.now()
        );
        given(qnaAnswerCache.get(10L, "질문")).willReturn(Optional.of(cached));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(100);

        verify(queryRewriter, never()).rewrite(anyString(), anyString());
        verify(embeddingProvider, never()).embed(anyString());
    }

    @Test
    @DisplayName("enabled=true + 의미 캐시 hit → rewriter 호출 없음")
    void semanticCacheHit_skipsRewriter() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("질문")).willReturn(originalEmbedding);
        CachedAnswer cached = new CachedAnswer(
                "의미 일치 답변", List.of(), List.of(), java.time.Instant.now()
        );
        var match = new com.youthfit.qna.application.port.dto.SemanticLookupMatch(
                1L,
                java.math.BigDecimal.valueOf(0.92),
                java.math.BigDecimal.valueOf(0.08)
        );
        given(semanticQnaCache.findSimilar(anyLong(), anyString(), any()))
                .willReturn(SemanticLookupResult.hit(match, cached));
        given(lookupClassifier.classify(any())).willReturn(LookupResultType.HIT);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(100);

        verify(queryRewriter, never()).rewrite(anyString(), anyString());
        // semantic lookup 용 임베딩 1회만 — rewritten 임베딩 호출 없음
        verify(embeddingProvider, times(1)).embed(anyString());
    }

    @Test
    @DisplayName("enabled=true + rewriter 예외 → 원래 질문으로 fallback (예외 전파 안함)")
    void enabled_rewriterException_fallsBack() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        given(queryRewriter.rewrite(anyString(), anyString()))
                .willThrow(new RuntimeException("rewriter down"));
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("질문")).willReturn(originalEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(originalEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(200);

        verify(qnaLlmProvider, times(1)).generateAnswer(
                anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
    }

    private static PolicyDocumentChunkResult chunk(double distance) {
        return new PolicyDocumentChunkResult(
                1L, 10L, 0, "내용", distance, null, null, null
        );
    }

    private static Policy mockPolicy() {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getTitle()).willReturn("청년내일저축계좌");
        given(p.getCategory()).willReturn(Category.WELFARE);
        given(p.getSummary()).willReturn("저소득 청년 자산형성 지원");
        given(p.getSupportTarget()).willReturn("만 19~34세, 근로소득자");
        given(p.getSupportContent()).willReturn("월 30만원 매칭");
        given(p.getOrganization()).willReturn("보건복지부");
        given(p.getContact()).willReturn("02-123-4567");
        given(p.getApplyStart()).willReturn(LocalDate.of(2026, 5, 1));
        given(p.getApplyEnd()).willReturn(LocalDate.of(2026, 5, 31));
        given(p.getProvideType()).willReturn("현금");
        return p;
    }
}
