package com.youthfit.rag.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.config.CostGuardProperties;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.DocumentChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("RagIndexingService")
@ExtendWith(MockitoExtension.class)
class RagIndexingServiceTest {

    @InjectMocks
    private RagIndexingService ragIndexingService;

    @Mock
    private PolicyDocumentRepository policyDocumentRepository;

    @Mock
    private DocumentChunker documentChunker;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Spy
    private CostGuard costGuard = new CostGuard(new CostGuardProperties(""));

    @Mock
    private com.youthfit.qna.application.port.QnaCacheInvalidator qnaCacheInvalidator;

    @Nested
    @DisplayName("indexPolicyDocument - 정책 문서 인덱싱")
    class IndexPolicyDocument {

        @Test
        @DisplayName("신규 문서를 청크 분할하고 임베딩을 생성하여 저장한다")
        void newDocument_chunksAndSaves() {
            // given
            IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "정책 내용");
            String hash = "abc123";
            List<PolicyDocument> chunks = List.of(
                    createChunk(1L, 0, "청크1", hash),
                    createChunk(1L, 1, "청크2", hash)
            );

            given(documentChunker.computeHash("정책 내용")).willReturn(hash);
            given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of());
            given(documentChunker.chunk(1L, "정책 내용")).willReturn(chunks);
            given(embeddingProvider.embedBatch(List.of("청크1", "청크2")))
                    .willReturn(List.of(new float[]{0.1f}, new float[]{0.2f}));
            given(policyDocumentRepository.saveAll(anyList())).willReturn(chunks);

            // when
            IndexingResult result = ragIndexingService.indexPolicyDocument(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.chunkCount()).isEqualTo(2);
            assertThat(result.updated()).isTrue();
            verify(policyDocumentRepository).saveAll(chunks);
        }

        @Test
        @DisplayName("동일한 해시의 문서가 이미 있으면 인덱싱을 스킵한다")
        void sameHash_skipsIndexing() {
            // given
            IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "정책 내용");
            String hash = "same-hash";
            PolicyDocument existing = createChunk(1L, 0, "기존 청크", hash);

            given(documentChunker.computeHash("정책 내용")).willReturn(hash);
            given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of(existing));

            // when
            IndexingResult result = ragIndexingService.indexPolicyDocument(command);

            // then
            assertThat(result.policyId()).isEqualTo(1L);
            assertThat(result.chunkCount()).isEqualTo(1);
            assertThat(result.updated()).isFalse();
            verify(policyDocumentRepository, never()).saveAll(any());
            verify(embeddingProvider, never()).embedBatch(any());
        }

        @Test
        @DisplayName("해시가 변경되면 기존 문서를 삭제하고 새로 인덱싱한다")
        void differentHash_deletesAndReindexes() {
            // given
            IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "변경된 내용");
            String oldHash = "old-hash";
            String newHash = "new-hash";
            PolicyDocument existing = createChunk(1L, 0, "기존 청크", oldHash);
            List<PolicyDocument> newChunks = List.of(
                    createChunk(1L, 0, "새 청크", newHash)
            );

            given(documentChunker.computeHash("변경된 내용")).willReturn(newHash);
            given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of(existing));
            given(documentChunker.chunk(1L, "변경된 내용")).willReturn(newChunks);
            given(embeddingProvider.embedBatch(List.of("새 청크")))
                    .willReturn(List.of(new float[]{0.5f}));
            given(policyDocumentRepository.saveAll(anyList())).willReturn(newChunks);

            // when
            IndexingResult result = ragIndexingService.indexPolicyDocument(command);

            // then
            assertThat(result.updated()).isTrue();
            assertThat(result.chunkCount()).isEqualTo(1);
            verify(policyDocumentRepository).deleteByPolicyId(1L);
            verify(policyDocumentRepository).saveAll(newChunks);
        }

        @Test
        @DisplayName("해시가 변경되면 의미 캐시도 같은 트랜잭션 안에서 비운다")
        void differentHash_invalidatesSemanticCache() {
            IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "변경된 내용");
            PolicyDocument existing = createChunk(1L, 0, "기존 청크", "old-hash");

            given(documentChunker.computeHash("변경된 내용")).willReturn("new-hash");
            given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of(existing));
            given(documentChunker.chunk(1L, "변경된 내용"))
                    .willReturn(List.of(createChunk(1L, 0, "새 청크", "new-hash")));
            given(embeddingProvider.embedBatch(List.of("새 청크")))
                    .willReturn(List.of(new float[]{0.5f}));

            ragIndexingService.indexPolicyDocument(command);

            verify(qnaCacheInvalidator).invalidatePolicy(1L);
        }

        @Test
        @DisplayName("해시가 동일하면 의미 캐시 invalidate 를 호출하지 않는다")
        void sameHash_doesNotInvalidate() {
            IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "정책 내용");
            PolicyDocument existing = createChunk(1L, 0, "기존 청크", "same-hash");
            given(documentChunker.computeHash("정책 내용")).willReturn("same-hash");
            given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of(existing));

            ragIndexingService.indexPolicyDocument(command);

            verify(qnaCacheInvalidator, never()).invalidatePolicy(anyLong());
        }

        @Test
        @DisplayName("신규 정책(기존 인덱스 없음)은 invalidate 호출 없이 그대로 인덱싱한다")
        void newDocument_doesNotInvalidate() {
            IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "정책 내용");
            given(documentChunker.computeHash("정책 내용")).willReturn("hash");
            given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of());
            given(documentChunker.chunk(1L, "정책 내용"))
                    .willReturn(List.of(createChunk(1L, 0, "청크", "hash")));
            given(embeddingProvider.embedBatch(any())).willReturn(List.of(new float[]{0.1f}));

            ragIndexingService.indexPolicyDocument(command);

            verify(qnaCacheInvalidator, never()).invalidatePolicy(anyLong());
        }
    }

    // ── 헬퍼 메서드 ──

    private PolicyDocument createChunk(Long policyId, int index, String content, String hash) {
        return PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(index)
                .content(content)
                .sourceHash(hash)
                .build();
    }
}
