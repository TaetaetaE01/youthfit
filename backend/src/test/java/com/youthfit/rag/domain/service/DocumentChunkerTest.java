package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.PolicyDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        @DisplayName("각 청크의 크기가 maxChunkSize + overlap 한도를 초과하지 않는다 (soft limit)")
        void chunkSize_doesNotExceedMaxPlusOverlap() {
            int maxSize = 100;
            // OVERLAP_CHARS(80) + "\n" 구분자 1자 = 81 자 가 실제 상한.
            int overlapAllowance = 81;
            DocumentChunker smallChunker = new DocumentChunker(maxSize);
            String content = "A".repeat(50) + "\n\n" + "B".repeat(50) + "\n\n" + "C".repeat(50);

            List<PolicyDocument> result = smallChunker.chunk(1L, content);

            assertThat(result).allSatisfy(chunk ->
                    assertThat(chunk.getContent().length()).isLessThanOrEqualTo(maxSize + overlapAllowance));
        }

        @Test
        @DisplayName("긴 단일 단락은 줄 boundary 에서 분할되어 행이 중간에 잘리지 않는다")
        void longSingleParagraph_splitsAtLineBoundary_notMidLine() {
            DocumentChunker smallChunker = new DocumentChunker(30);
            String content = "사업번호1 사업A 기관A\n사업번호2 사업B 기관B\n사업번호3 사업C 기관C\n사업번호4 사업D 기관D";

            List<PolicyDocument> result = smallChunker.chunk(1L, content);

            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(chunk -> {
                for (String line : chunk.getContent().split("\n")) {
                    assertThat(line).matches("사업번호\\d+ 사업[A-Z] 기관[A-Z]");
                }
            });
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

        @Test
        @DisplayName("computeHash 는 chunker version 을 입력에 섞어 raw SHA-256 과 달라야 한다")
        void computeHash_includesChunkerVersion() throws Exception {
            DocumentChunker chunker = new DocumentChunker();
            String content = "테스트 내용";

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] rawBytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
            String rawHash = HexFormat.of().formatHex(rawBytes);

            String chunkerHash = chunker.computeHash(content);

            assertThat(chunkerHash).isNotEqualTo(rawHash);
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
    @DisplayName("표 인식 + 헤더 prepend")
    class TableAware {

        @Test
        @DisplayName("연속 번호 행 3+ 이 maxChunkSize 안에 들어가면 통째로 한 청크")
        void shortTable_fitsInOneChunk() {
            DocumentChunker chunker = new DocumentChunker(500);
            String content = "사업번호 사업구분 시행기관\n"
                    + "1 기초생활보장 복지부\n"
                    + "2 희망키움통장 복지부\n"
                    + "3 디딤씨앗통장 복지부\n"
                    + "4 청년저축계좌 복지부";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).contains("사업번호 사업구분 시행기관");
            assertThat(result.get(0).getContent()).contains("4 청년저축계좌");
        }

        @Test
        @DisplayName("표가 maxChunkSize 를 넘으면 행 boundary 에서 분할되, 각 청크에 헤더가 prepend 된다")
        void longTable_splitsAtRowBoundary_withHeaderPrepended() {
            DocumentChunker chunker = new DocumentChunker(60);
            String header = "사업번호 사업구분 시행기관";
            String content = header + "\n"
                    + "1 기초생활보장 복지부\n"
                    + "2 희망키움통장 복지부\n"
                    + "3 디딤씨앗통장 복지부\n"
                    + "4 청년저축계좌 복지부\n"
                    + "5 청년내일채움 고용부\n"
                    + "6 청년재직자공제 고용부";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            assertThat(result).hasSizeGreaterThan(1);
            assertThat(result).allSatisfy(chunk ->
                    assertThat(chunk.getContent()).startsWith(header));
        }

        @Test
        @DisplayName("직전 행이 평문(20자 초과 + 마침표 종결)이면 헤더 후보로 잡지 않는다")
        void prevLineIsPlainSentence_noHeaderTreatedAsTableHeader() {
            DocumentChunker chunker = new DocumentChunker(60);
            String content = "다음은 중복 참여 불가 사업의 전체 목록입니다.\n"
                    + "1 기초생활보장 복지부\n"
                    + "2 희망키움통장 복지부\n"
                    + "3 디딤씨앗통장 복지부\n"
                    + "4 청년저축계좌 복지부\n"
                    + "5 청년내일채움 고용부\n"
                    + "6 청년재직자공제 고용부";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            assertThat(result).hasSizeGreaterThan(1);
            for (int i = 1; i < result.size(); i++) {
                assertThat(result.get(i).getContent()).doesNotStartWith("다음은 중복 참여");
            }
        }

        @Test
        @DisplayName("표 block 끝에 plain text 가 이어질 때 표 끝 줄이 다음 청크로 새지 않는다")
        void tableFollowedByPlainText_blockEndIncludesTrailingNewline() {
            DocumentChunker chunker = new DocumentChunker(500);
            String content = "사업번호 사업구분 시행기관\n"
                    + "1 기초생활보장 복지부\n"
                    + "2 희망키움통장 복지부\n"
                    + "3 디딤씨앗통장 복지부\n"
                    + "여기는 표가 끝나고 시작하는 평문 단락입니다.";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            // 표 청크와 평문 청크가 분리되어야 함
            // 표 청크: "사업번호 ... 3 디딤씨앗통장 복지부" — 마지막 줄까지
            // 평문 청크: "여기는 표가 끝나고 시작하는 평문 단락입니다."
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).contains("3 디딤씨앗통장");
            assertThat(result.get(0).getContent()).doesNotContain("여기는 표가 끝나고");
            assertThat(result.get(1).getContent()).contains("여기는 표가 끝나고");
            assertThat(result.get(1).getContent()).doesNotContain("3 디딤씨앗통장");
        }

        @Test
        @DisplayName("직전 줄이 NUMBER_ROW 패턴이면 헤더 후보로 잡지 않는다 (방어 케이스)")
        void prevLineIsNumberRow_notTreatedAsHeader() {
            DocumentChunker chunker = new DocumentChunker(500);
            // 직전 줄이 그 자체로 number row 형태
            String content = "1. 첫 번째 항목 설명\n"
                    + "1 기초생활보장 복지부\n"
                    + "2 희망키움통장 복지부\n"
                    + "3 디딤씨앗통장 복지부";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            // "1. 첫 번째 항목 설명" 이 헤더로 잘못 잡혀서 prepend 되면 안 됨
            // 그리고 자체적으로 이게 NUMBER_ROW 매칭이라 표 인식 시 함께 묶일 수도 있으니
            // 핵심은: 결과 청크에 "1. 첫 번째 항목 설명" 이 헤더처럼 두 번 prepend 되지 않을 것
            long countOfPrefix = result.stream()
                    .filter(c -> c.getContent().contains("1. 첫 번째 항목 설명"))
                    .count();
            assertThat(countOfPrefix).isLessThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("청크 간 overlap")
    class Overlap {

        @Test
        @DisplayName("일반 평문 청크 사이에 ~80자 overlap 이 들어간다")
        void normalChunks_haveOverlapPrefix() {
            DocumentChunker chunker = new DocumentChunker(100);
            String content = "지원 대상은 만 19세부터 34세까지의 청년이며 소득은 중위소득 100% 이하 가구입니다. "
                    + "이는 청년이 자립할 수 있는 기반을 마련하기 위한 정책으로 정부가 매칭 지원금을 제공합니다. "
                    + "신청은 복지로 또는 주민센터에서 가능하며 신청 후 약 4주 이내에 결과가 통보됩니다. "
                    + "선정된 신청자는 매월 본인 저축액에 비례한 정부 지원금을 받게 됩니다.";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            assertThat(result.size()).isGreaterThanOrEqualTo(2);
            String firstChunk = result.get(0).getContent();
            String secondChunk = result.get(1).getContent();

            int prefixLen = Math.min(80, secondChunk.length());
            String secondPrefix = secondChunk.substring(0, prefixLen);
            boolean foundOverlap = false;
            for (int len = prefixLen; len >= 10; len--) {
                if (firstChunk.contains(secondPrefix.substring(0, len))) {
                    foundOverlap = true;
                    break;
                }
            }
            assertThat(foundOverlap).as("두 번째 청크 시작이 첫 번째 청크 끝 일부와 겹쳐야 함").isTrue();
        }

        @Test
        @DisplayName("표 청크에는 overlap 이 적용되지 않는다 (헤더 prepend 가 그 역할)")
        void tableChunks_skipOverlap() {
            DocumentChunker chunker = new DocumentChunker(60);
            String header = "사업번호 사업구분 시행기관";
            String content = header + "\n"
                    + "1 기초생활보장 복지부\n"
                    + "2 희망키움통장 복지부\n"
                    + "3 디딤씨앗통장 복지부\n"
                    + "4 청년저축계좌 복지부\n"
                    + "5 청년내일채움 고용부\n"
                    + "6 청년재직자공제 고용부";

            List<PolicyDocument> result = chunker.chunk(1L, content);

            assertThat(result).allSatisfy(chunk ->
                    assertThat(chunk.getContent()).startsWith(header + "\n"));
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
