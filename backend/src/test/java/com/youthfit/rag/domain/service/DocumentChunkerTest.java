package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.PolicyDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DocumentChunker")
class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Nested
    @DisplayName("chunk - 문서 청크 분할")
    class Chunk {

        @Test
        @DisplayName("짧은 문서는 하나의 청크로 반환한다")
        void shortContent_returnsSingleChunk() {
            // given
            String content = "청년 주거 지원 정책입니다.";

            // when
            List<PolicyDocument> result = chunker.chunk(1L, content);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPolicyId()).isEqualTo(1L);
            assertThat(result.get(0).getChunkIndex()).isZero();
            assertThat(result.get(0).getContent()).isEqualTo(content);
        }

        @Test
        @DisplayName("빈 문자열이면 빈 리스트를 반환한다")
        void emptyContent_returnsEmptyList() {
            // when
            List<PolicyDocument> result = chunker.chunk(1L, "");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null이면 빈 리스트를 반환한다")
        void nullContent_returnsEmptyList() {
            // when
            List<PolicyDocument> result = chunker.chunk(1L, null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("공백만 있으면 빈 리스트를 반환한다")
        void blankContent_returnsEmptyList() {
            // when
            List<PolicyDocument> result = chunker.chunk(1L, "   \n\n  ");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("문단 구분자 기준으로 청크를 분할한다")
        void multipleParagraphs_splitsIntoChunks() {
            // given
            DocumentChunker smallChunker = new DocumentChunker(50);
            String content = "첫 번째 문단입니다. 꽤 긴 내용이 포함되어 있습니다.\n\n두 번째 문단입니다. 역시 긴 내용입니다.";

            // when
            List<PolicyDocument> result = smallChunker.chunk(1L, content);

            // then
            assertThat(result).hasSizeGreaterThan(1);
            for (int i = 0; i < result.size(); i++) {
                assertThat(result.get(i).getChunkIndex()).isEqualTo(i);
                assertThat(result.get(i).getPolicyId()).isEqualTo(1L);
            }
        }

        @Test
        @DisplayName("모든 청크에 동일한 sourceHash가 설정된다")
        void allChunks_haveSameSourceHash() {
            // given
            DocumentChunker smallChunker = new DocumentChunker(20);
            String content = "첫 번째 문단입니다. 내용이 길어요.\n\n두 번째 문단입니다. 역시 길어요.\n\n세 번째 문단입니다. 꽤 길어요.";

            // when
            List<PolicyDocument> result = smallChunker.chunk(1L, content);

            // then
            assertThat(result).hasSizeGreaterThan(1);
            String expectedHash = result.get(0).getSourceHash();
            assertThat(result).allSatisfy(chunk ->
                    assertThat(chunk.getSourceHash()).isEqualTo(expectedHash));
        }

        @Test
        @DisplayName("각 청크의 크기가 maxChunkSize를 초과하지 않는다")
        void chunkSize_doesNotExceedMax() {
            // given
            int maxSize = 100;
            DocumentChunker smallChunker = new DocumentChunker(maxSize);
            String content = "A".repeat(50) + "\n\n" + "B".repeat(50) + "\n\n" + "C".repeat(50);

            // when
            List<PolicyDocument> result = smallChunker.chunk(1L, content);

            // then
            assertThat(result).allSatisfy(chunk ->
                    assertThat(chunk.getContent().length()).isLessThanOrEqualTo(maxSize));
        }
    }

    @Nested
    @DisplayName("computeHash - 해시 계산")
    class ComputeHash {

        @Test
        @DisplayName("동일한 내용이면 같은 해시를 반환한다")
        void sameContent_returnsSameHash() {
            // given
            String content = "테스트 내용";

            // when
            String hash1 = chunker.computeHash(content);
            String hash2 = chunker.computeHash(content);

            // then
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("다른 내용이면 다른 해시를 반환한다")
        void differentContent_returnsDifferentHash() {
            // when
            String hash1 = chunker.computeHash("내용1");
            String hash2 = chunker.computeHash("내용2");

            // then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("SHA-256 해시는 64자 hex 문자열이다")
        void hash_isSha256HexString() {
            // when
            String hash = chunker.computeHash("test");

            // then
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("생성자 유효성 검증")
    class Constructor {

        @Test
        @DisplayName("maxChunkSize가 0 이하이면 예외가 발생한다")
        void invalidMaxChunkSize_throwsException() {
            assertThatThrownBy(() -> new DocumentChunker(0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new DocumentChunker(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("attachment boundary 분할 + 페이지 추적")
    class AttachmentBoundary {

        @Test
        @DisplayName("정책 본문/첨부 boundary 에서 청크가 강제 분할된다")
        void givenBodyAndAttachmentBoundary_whenChunk_thenSplitsAtBoundary() {
            String content = """
                    === 정책 본문 ===
                    정책 본문 짧은 텍스트입니다.

                    === 첨부 attachment-id=12 name="시행규칙.pdf" ===
                    --- page=1 ---
                    첨부 1페이지 텍스트.
                    --- page=2 ---
                    첨부 2페이지 텍스트.
                    """;

            List<PolicyDocument> chunks = chunker.chunk(7L, content);

            assertThat(chunks).isNotEmpty();

            PolicyDocument bodyChunk = chunks.stream()
                    .filter(c -> c.getAttachmentId() == null)
                    .findFirst().orElseThrow();
            assertThat(bodyChunk.getContent()).contains("정책 본문 짧은 텍스트");
            assertThat(bodyChunk.getPageStart()).isNull();
            assertThat(bodyChunk.getPageEnd()).isNull();

            PolicyDocument attChunk = chunks.stream()
                    .filter(c -> c.getAttachmentId() != null && c.getAttachmentId() == 12L)
                    .findFirst().orElseThrow();
            assertThat(attChunk.getContent()).contains("첨부 1페이지 텍스트");
            assertThat(attChunk.getPageStart()).isEqualTo(1);
            assertThat(attChunk.getPageEnd()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("여러 페이지에 걸친 청크는 pageStart/pageEnd 가 추적된다")
        void givenMultiplePages_whenChunkSpansPages_thenPageRangeTracked() {
            String content = """
                    === 첨부 attachment-id=12 name="x.pdf" ===
                    --- page=1 ---
                    짧은 1페이지.
                    --- page=2 ---
                    짧은 2페이지.
                    --- page=3 ---
                    짧은 3페이지.
                    """;

            List<PolicyDocument> chunks = chunker.chunk(7L, content);

            assertThat(chunks).allSatisfy(c -> {
                assertThat(c.getAttachmentId()).isEqualTo(12L);
                assertThat(c.getPageStart()).isNotNull();
                assertThat(c.getPageEnd()).isNotNull();
                assertThat(c.getPageStart()).isLessThanOrEqualTo(c.getPageEnd());
            });
        }

        @Test
        @DisplayName("HWP 등 페이지 메타 없는 첨부는 attachmentId 만 설정되고 page 는 null 이다")
        void givenHwpWithoutPageMeta_whenChunk_thenAttachmentIdSetPagesNull() {
            String content = """
                    === 첨부 attachment-id=13 name="안내문.hwp" ===
                    --- page=null ---
                    HWP 전체 텍스트입니다. 페이지 메타 없음.
                    """;

            List<PolicyDocument> chunks = chunker.chunk(7L, content);

            assertThat(chunks).allSatisfy(c -> {
                assertThat(c.getAttachmentId()).isEqualTo(13L);
                assertThat(c.getPageStart()).isNull();
                assertThat(c.getPageEnd()).isNull();
            });
        }
    }
}
