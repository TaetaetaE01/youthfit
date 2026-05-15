# RAG 청킹 의미 단위 보존 강화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `DocumentChunker` 가 PDF 평문 표·번호 리스트·긴 단락의 의미 단위를 보존하도록 강화해서, RAG retrieval 이 답이 있는 청크를 놓치는 케이스를 줄인다.

**Architecture:** 단일 도메인 서비스(`DocumentChunker`) 의 청킹 알고리즘에 3가지 변경(줄 보존·표 인식·overlap)을 누적하고, `computeHash` 입력에 chunker version 을 섞어 의미 캐시를 자연 만료시킨다. 운영 1회 reindex 를 위해 InternalApiKey 보호된 endpoint 를 신규 추가한다. DDD 의존 방향(Domain ← Application ← Presentation) 유지.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JUnit 5, AssertJ, PostgreSQL + pgvector, OpenAI text-embedding-3-small.

**Spec:** [`docs/superpowers/specs/DONE_2026-05-11-rag-table-aware-chunking-design.md`](../specs/DONE_2026-05-11-rag-table-aware-chunking-design.md)

---

## File Structure

### 변경
| 파일 | 책임 | 변경 내용 |
|---|---|---|
| `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` | 청킹 본체 | (1) `splitBySize` → `splitByLines` 다운그레이드. (2) `identifyTableBlocks` + 헤더 prepend 신규. (3) `applyOverlap` 신규. (4) `CHUNKER_VERSION` 상수 추가, `computeHash` 입력에 prefix |
| `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java` | 청커 단위 테스트 | 시나리오 5개 추가 (줄 보존·표 인식·overlap·version hash·표 분할 시 헤더) |

### 신규
| 파일 | 책임 |
|---|---|
| `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java` | `POST /api/internal/ingestion/reindex/{policyId}` — `AttachmentReindexService.reindex` 호출 |
| `backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerTest.java` | 엔드포인트 슬라이스 테스트 (`@WebMvcTest`) |

### 무변경 (참조만)
- `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java` — chunker 호출만 하므로 자동 적용
- `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java` — 컨트롤러가 호출
- `backend/src/main/java/com/youthfit/common/config/SecurityConfig.java` — `/api/internal/**` 가 이미 `InternalApiKeyFilter` 통과

---

## Task 1: `splitBySize` → `splitByLines` 다운그레이드

목적: 줄(`\n`) 자체는 절대 자르지 않는 누적 분할 로직으로 교체. 표·리스트의 행 boundary 가 자동 보존됨.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java:216-223`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`DocumentChunkerTest.java` 의 `Chunk` 중첩 클래스에 새 테스트 추가:

```java
@Test
@DisplayName("긴 단일 단락은 줄 boundary 에서 분할되어 행이 중간에 잘리지 않는다")
void longSingleParagraph_splitsAtLineBoundary_notMidLine() {
    // given — \n\n 없이 한 단락. 각 줄은 약 14자, 총 4줄이 한 단락에 묶임
    DocumentChunker smallChunker = new DocumentChunker(30);
    String content = "사업번호1 사업A 기관A\n사업번호2 사업B 기관B\n사업번호3 사업C 기관C\n사업번호4 사업D 기관D";

    // when
    List<PolicyDocument> result = smallChunker.chunk(1L, content);

    // then — 각 청크의 모든 행은 완전한 "사업번호N 사업X 기관X" 패턴이어야 함
    assertThat(result).isNotEmpty();
    assertThat(result).allSatisfy(chunk -> {
        for (String line : chunk.getContent().split("\n")) {
            assertThat(line).matches("사업번호\\d+ 사업[A-Z] 기관[A-Z]");
        }
    });
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest.Chunk.longSingleParagraph_splitsAtLineBoundary_notMidLine"`
Expected: FAIL — 현재 `splitBySize` 가 글자 단위 cut 이라 "사업번호3 사업C" + "기관C\n사업번호4..." 형태로 행이 깨짐

- [ ] **Step 3: `splitBySize` → `splitByLines` 교체 + 호출자 시그니처 변경**

(a) `DocumentChunker.java:216-223` 의 `splitBySize` 메서드를 통째로 삭제하고 다음 `splitByLines` 를 같은 위치에 추가:

```java
/**
 * 단락(\n\n) 내부가 maxChunkSize 보다 긴 경우 줄(\n) 단위로 누적 분할.
 * 단일 줄 자체가 maxChunkSize 를 넘는 극단 케이스만 글자 단위 cut (last-resort).
 * 결과는 원문 text 의 [start, end) offset 페어 리스트로 반환한다.
 */
private void splitByLines(String text, int start, int end, List<int[]> ranges) {
    int cursor = start;
    int chunkStart = -1;
    int chunkEnd = -1;

    while (cursor < end) {
        int lineEnd = text.indexOf('\n', cursor);
        int lineFinish = (lineEnd == -1 || lineEnd >= end) ? end : lineEnd;
        int lineLen = lineFinish - cursor;

        if (lineLen > maxChunkSize) {
            if (chunkStart != -1) {
                ranges.add(new int[]{chunkStart, chunkEnd});
                chunkStart = -1;
                chunkEnd = -1;
            }
            int c = cursor;
            while (c < lineFinish) {
                int next = Math.min(c + maxChunkSize, lineFinish);
                ranges.add(new int[]{c, next});
                c = next;
            }
        } else if (chunkStart == -1) {
            chunkStart = cursor;
            chunkEnd = lineFinish;
        } else if ((chunkEnd - chunkStart) + 1 + lineLen > maxChunkSize) {
            ranges.add(new int[]{chunkStart, chunkEnd});
            chunkStart = cursor;
            chunkEnd = lineFinish;
        } else {
            chunkEnd = lineFinish;
        }

        if (lineEnd == -1 || lineEnd >= end) break;
        cursor = lineEnd + 1;
    }

    if (chunkStart != -1) {
        ranges.add(new int[]{chunkStart, chunkEnd});
    }
}
```

(b) `paragraphAwareSplit` 내부의 호출(현재 라인 191):
```java
splitBySize(trimStart, trimEnd, ranges);
```
다음으로 교체:
```java
splitByLines(text, trimStart, trimEnd, ranges);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"`
Expected: PASS — 새 테스트 + 기존 테스트 11개 모두 통과

기존 테스트 중 `chunkSize_doesNotExceedMax` 가 깨지지 않는지 특히 주목 (줄 단위 누적도 maxChunkSize 약속을 지켜야 함).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "$(cat <<'EOF'
refactor(rag): splitBySize 를 splitByLines 로 다운그레이드

긴 단락 내부 분할 시 줄(\n) boundary 보존. 단일 줄이 maxChunkSize
를 넘는 극단 케이스만 글자 단위 cut (last-resort fallback).

표·번호 리스트의 행이 청크 중간에 잘리지 않도록 하는 기초 변경.
표 인식과 헤더 prepend 는 후속 task 에서 추가.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 표 인식 + 헤더 prepend

목적: 연속 번호 행 3+ 패턴을 표로 식별하고, 분할 시 헤더를 각 청크에 prepend.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: 실패 테스트 작성 — 표 미분할 케이스**

`DocumentChunkerTest.java` 에 `TableAware` 중첩 클래스 신규 추가:

```java
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
}
```

- [ ] **Step 2: 실패 테스트 추가 — 표 분할 시 헤더 prepend**

같은 `TableAware` 클래스에 추가:

```java
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
```

- [ ] **Step 3: 실패 테스트 추가 — 헤더 후보 평문 종결 시 헤더 없음**

같은 `TableAware` 클래스에 추가:

```java
    @Test
    @DisplayName("직전 행이 평문(20자 초과 + 마침표 종결)이면 헤더 후보로 잡지 않는다")
    void prevLineIsPlainSentence_noHeaderTreatedAsTableHeader() {
        DocumentChunker chunker = new DocumentChunker(60);
        // 직전 행이 25자 + 마침표 종결 — 헤더가 아닌 본문 문장
        String content = "다음은 중복 참여 불가 사업의 전체 목록입니다.\n"
                + "1 기초생활보장 복지부\n"
                + "2 희망키움통장 복지부\n"
                + "3 디딤씨앗통장 복지부\n"
                + "4 청년저축계좌 복지부\n"
                + "5 청년내일채움 고용부\n"
                + "6 청년재직자공제 고용부";

        List<PolicyDocument> result = chunker.chunk(1L, content);

        // 표는 인식되지만 헤더 prepend 는 X — 분할 시 첫 청크 외에는 평문 문장으로 시작 안 함
        assertThat(result).hasSizeGreaterThan(1);
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).getContent()).doesNotStartWith("다음은 중복 참여");
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest.TableAware"`
Expected: FAIL 3건 — 현재 코드는 표 인식 로직이 없음

- [ ] **Step 5: 표 인식 + 헤더 prepend 구현**

`DocumentChunker.java` 의 `chunkSegment` 메서드(라인 121-139)를 다음으로 교체:

```java
private List<Chunk> chunkSegment(Segment seg) {
    String text = seg.text();
    List<PageMark> marks = collectPageMarks(text);

    List<TableBlock> tableBlocks = identifyTableBlocks(text);
    List<int[]> rawChunks = chunkWithTableBlocks(text, tableBlocks);

    List<Chunk> chunks = new ArrayList<>(rawChunks.size());
    for (int[] range : rawChunks) {
        int start = range[0];
        int end = range[1];
        String chunkText = text.substring(start, end).trim();
        if (chunkText.isBlank()) {
            continue;
        }
        // 표 청크에는 헤더 prepend 가 필요할 수 있음 — chunkWithTableBlocks 가 [start,end] 결정 시 이미 헤더 영역 포함
        // 단 분할 청크(2번째 이후)는 별도 prepend 처리
        String finalText = applyHeaderIfNeeded(chunkText, tableBlocks, start, end);
        PageRange pr = computePageRange(marks, start, end);
        chunks.add(new Chunk(finalText, pr.start(), pr.end()));
    }
    return chunks;
}
```

이어서 `DocumentChunker.java` 클래스 안에 다음 메서드들 신규 추가 (`paragraphAwareSplit` 위에):

```java
private static final Pattern NUMBER_ROW = Pattern.compile(
        "^(\\d+\\s+|\\d+\\.\\s+|[①②③④⑤⑥⑦⑧⑨⑩])");
private static final int MIN_TABLE_ROWS = 3;
private static final int HEADER_PLAINTEXT_MIN_LEN = 20;
private static final Pattern PLAINTEXT_END = Pattern.compile(".*[.?!]\\s*$");

private List<TableBlock> identifyTableBlocks(String text) {
    List<TableBlock> blocks = new ArrayList<>();
    int len = text.length();
    int cursor = 0;
    Integer prevLineStart = null;
    Integer prevLineEnd = null;
    int runStart = -1;
    int runCount = 0;

    while (cursor <= len) {
        int lineEnd = text.indexOf('\n', cursor);
        int finish = (lineEnd == -1) ? len : lineEnd;
        String line = text.substring(cursor, finish);

        if (NUMBER_ROW.matcher(line).find()) {
            if (runStart == -1) {
                runStart = cursor;
            }
            runCount++;
        } else {
            if (runCount >= MIN_TABLE_ROWS) {
                String header = pickHeader(text, prevLineStart, prevLineEnd);
                blocks.add(new TableBlock(runStart, finish, header));
            }
            runStart = -1;
            runCount = 0;
            prevLineStart = cursor;
            prevLineEnd = finish;
        }

        if (lineEnd == -1) {
            if (runCount >= MIN_TABLE_ROWS) {
                String header = pickHeader(text, prevLineStart, prevLineEnd);
                blocks.add(new TableBlock(runStart, finish, header));
            }
            break;
        }
        cursor = lineEnd + 1;
    }
    return blocks;
}

private String pickHeader(String text, Integer prevStart, Integer prevEnd) {
    if (prevStart == null) return null;
    String candidate = text.substring(prevStart, prevEnd).trim();
    if (candidate.isEmpty()) return null;
    if (candidate.length() > HEADER_PLAINTEXT_MIN_LEN
            && PLAINTEXT_END.matcher(candidate).matches()) {
        return null; // 평문 종결 — 헤더 아님
    }
    return candidate;
}

private List<int[]> chunkWithTableBlocks(String text, List<TableBlock> blocks) {
    if (blocks.isEmpty()) {
        return paragraphAwareSplit(text);
    }

    List<int[]> ranges = new ArrayList<>();
    int cursor = 0;
    for (TableBlock b : blocks) {
        if (cursor < b.start) {
            ranges.addAll(paragraphAwareSplit(text.substring(cursor, b.start))
                    .stream()
                    .map(r -> new int[]{r[0] + cursor, r[1] + cursor})
                    .toList());
        }
        // 표 block 자체를 한 단위로 줄 단위 분할
        List<int[]> tableRanges = new ArrayList<>();
        splitByLines(text, b.start, b.end, tableRanges);
        ranges.addAll(tableRanges);
        cursor = b.end;
    }
    if (cursor < text.length()) {
        ranges.addAll(paragraphAwareSplit(text.substring(cursor))
                .stream()
                .map(r -> new int[]{r[0] + cursor, r[1] + cursor})
                .toList());
    }
    return ranges;
}

private String applyHeaderIfNeeded(String chunkText, List<TableBlock> blocks, int start, int end) {
    for (TableBlock b : blocks) {
        if (start >= b.start && end <= b.end && b.header != null) {
            // 청크가 이미 헤더로 시작하면 중복 prepend 회피
            if (chunkText.startsWith(b.header)) {
                return chunkText;
            }
            return b.header + "\n" + chunkText;
        }
    }
    return chunkText;
}

private record TableBlock(int start, int end, String header) {}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"`
Expected: PASS — 신규 3개 + 기존 모두 통과

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "$(cat <<'EOF'
feat(rag): 표 인식 + 헤더 prepend 청킹

연속 번호 행 3+ 패턴(^\\d+\\s+ / ^\\d+\\.\\s+ / ^[①-⑩])을 표 block 으로
식별. 직전 한 줄을 헤더 후보로 추정하되, 평문 종결(20자 초과 +
마침표/물음표/느낌표) 행은 헤더 아님으로 처리.

표 block 은 한 단위로 줄 단위 분할되, 각 청크에 헤더 prepend.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 청크 간 overlap (~80자)

목적: 일반 평문 청크 사이에 ~80자 overlap 을 두어 boundary 깨짐 시 인접 청크가 일부 정보를 회수.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`DocumentChunkerTest.java` 에 `Overlap` 중첩 클래스 신규 추가:

```java
@Nested
@DisplayName("청크 간 overlap")
class Overlap {

    @Test
    @DisplayName("일반 평문 청크 사이에 ~80자 overlap 이 들어간다")
    void normalChunks_haveOverlapPrefix() {
        DocumentChunker chunker = new DocumentChunker(200);
        // 표 패턴 X. 충분히 긴 평문 (250자+) — 분할 강제
        String content = "지원 대상은 만 19세부터 34세까지의 청년이며 소득은 중위소득 100% 이하 가구입니다. "
                + "이는 청년이 자립할 수 있는 기반을 마련하기 위한 정책으로 정부가 매칭 지원금을 제공합니다. "
                + "신청은 복지로 또는 주민센터에서 가능하며 신청 후 약 4주 이내에 결과가 통보됩니다. "
                + "선정된 신청자는 매월 본인 저축액에 비례한 정부 지원금을 받게 됩니다.";

        List<PolicyDocument> result = chunker.chunk(1L, content);

        assertThat(result.size()).isGreaterThanOrEqualTo(2);
        String firstChunk = result.get(0).getContent();
        String secondChunk = result.get(1).getContent();

        // 두 번째 청크 시작 부분에 첫 번째 청크 끝 일부가 포함되어야 함
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

        // 모든 청크는 헤더로 시작 (overlap 으로 인한 추가 prefix 없음)
        assertThat(result).allSatisfy(chunk ->
                assertThat(chunk.getContent()).startsWith(header + "\n"));
    }
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest.Overlap"`
Expected: FAIL — overlap 로직 미구현

- [ ] **Step 3: overlap 구현**

`DocumentChunker.java` 의 `chunk` 메서드(라인 37-64)에서 `chunks` 를 만든 직후 overlap 을 적용한다. 메서드 본문을 다음으로 교체:

```java
public List<PolicyDocument> chunk(Long policyId, String content) {
    if (content == null || content.isBlank()) {
        return List.of();
    }

    String sourceHash = computeHash(content);
    List<Segment> segments = splitToSegments(content.trim());

    List<PolicyDocument> documents = new ArrayList<>();
    int globalIndex = 0;
    for (Segment seg : segments) {
        List<Chunk> segChunks = chunkSegment(seg);
        List<Chunk> withOverlap = applyOverlap(segChunks);
        for (Chunk c : withOverlap) {
            if (c.text().isBlank()) {
                continue;
            }
            documents.add(PolicyDocument.builder()
                    .policyId(policyId)
                    .chunkIndex(globalIndex++)
                    .content(c.text())
                    .sourceHash(sourceHash)
                    .attachmentId(seg.attachmentId())
                    .pageStart(c.pageStart())
                    .pageEnd(c.pageEnd())
                    .build());
        }
    }
    return documents;
}
```

이어서 같은 클래스에 `applyOverlap` 메서드 신규 추가 (`chunkSegment` 아래):

```java
private static final int OVERLAP_CHARS = 80;
private static final int OVERLAP_BACKTRACK_LIMIT = 20;

/**
 * 일반 평문 청크에 ~80자 overlap 을 prepend. 표 청크(헤더로 시작)는 스킵.
 */
private List<Chunk> applyOverlap(List<Chunk> chunks) {
    if (chunks.size() < 2) return chunks;

    List<Chunk> result = new ArrayList<>(chunks.size());
    result.add(chunks.get(0));
    for (int i = 1; i < chunks.size(); i++) {
        Chunk prev = chunks.get(i - 1);
        Chunk cur = chunks.get(i);

        // 현재 청크가 표 청크(헤더가 prepend된 형태)인지 추정 — 첫 줄이 NUMBER_ROW 가 아니고
        // 다음 줄이 NUMBER_ROW 면 표 청크로 간주하여 overlap 스킵
        if (looksLikeTableChunk(cur.text())) {
            result.add(cur);
            continue;
        }

        String overlap = computeOverlapPrefix(prev.text());
        if (overlap.isEmpty()) {
            result.add(cur);
        } else {
            result.add(new Chunk(overlap + " " + cur.text(), cur.pageStart(), cur.pageEnd()));
        }
    }
    return result;
}

private boolean looksLikeTableChunk(String text) {
    String[] lines = text.split("\n", 3);
    if (lines.length < 2) return false;
    // 첫 줄은 헤더(NUMBER_ROW 미매칭), 두 번째 줄은 NUMBER_ROW
    return !NUMBER_ROW.matcher(lines[0]).find()
            && NUMBER_ROW.matcher(lines[1]).find();
}

private String computeOverlapPrefix(String prevText) {
    if (prevText.length() <= OVERLAP_CHARS) return prevText;
    int start = prevText.length() - OVERLAP_CHARS;
    // 단어 중간이면 직전 공백/문장부호까지 backtrack (최대 OVERLAP_BACKTRACK_LIMIT 자)
    int backtrack = 0;
    while (start < prevText.length() && backtrack < OVERLAP_BACKTRACK_LIMIT) {
        char ch = prevText.charAt(start);
        if (Character.isWhitespace(ch) || ".,!?;:".indexOf(ch) >= 0) {
            start++;
            break;
        }
        start++;
        backtrack++;
    }
    if (start >= prevText.length()) return "";
    return prevText.substring(start);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"`
Expected: PASS — 신규 Overlap 2개 + 기존 모두 통과

특히 `chunkSize_doesNotExceedMax` 가 깨질 가능성에 주목 — overlap 으로 길이가 늘어남. 만약 깨지면 `DocumentChunkerTest.java` 의 해당 테스트(라인 103-117) 를 다음으로 교체:

```java
@Test
@DisplayName("각 청크의 크기가 maxChunkSize + overlap 한도를 초과하지 않는다 (soft limit)")
void chunkSize_doesNotExceedMaxPlusOverlap() {
    // given
    int maxSize = 100;
    int overlapAllowance = 100; // overlap 80자 + 헤더/공백 여유
    DocumentChunker smallChunker = new DocumentChunker(maxSize);
    String content = "A".repeat(50) + "\n\n" + "B".repeat(50) + "\n\n" + "C".repeat(50);

    // when
    List<PolicyDocument> result = smallChunker.chunk(1L, content);

    // then — overlap·헤더 prepend 로 한도 살짝 초과 허용 (spec §9 soft limit 룰)
    assertThat(result).allSatisfy(chunk ->
            assertThat(chunk.getContent().length()).isLessThanOrEqualTo(maxSize + overlapAllowance));
}
```

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "$(cat <<'EOF'
feat(rag): 청크 간 ~80자 overlap 적용

일반 평문 청크 시작에 직전 청크 끝 ~80자 prepend. 단어 중간 끊김
회피를 위해 직전 공백/문장부호까지 최대 20자 backtrack.

표 청크는 헤더 prepend 가 같은 역할이라 overlap 스킵.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `CHUNKER_VERSION` 적용 (의미 캐시 자연 만료)

목적: chunker 알고리즘 변경을 source_hash 에 반영해 의미 캐시(`qna_question_cache`) 가 자연 만료되도록.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`DocumentChunkerTest.java` 의 `ComputeHash` 중첩 클래스에 추가:

```java
    @Test
    @DisplayName("computeHash 는 chunker version 을 입력에 섞어 raw SHA-256 과 달라야 한다")
    void computeHash_includesChunkerVersion() throws Exception {
        DocumentChunker chunker = new DocumentChunker();
        String content = "테스트 내용";

        // raw SHA-256
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] rawBytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
        String rawHash = HexFormat.of().formatHex(rawBytes);

        String chunkerHash = chunker.computeHash(content);

        assertThat(chunkerHash).isNotEqualTo(rawHash);
    }
```

테스트 파일 상단에 import 추가 (아직 없으면):
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest.ComputeHash.computeHash_includesChunkerVersion"`
Expected: FAIL — 현재 `computeHash` 는 content 만으로 SHA-256 계산하므로 raw hash 와 동일

- [ ] **Step 3: `CHUNKER_VERSION` 추가 + `computeHash` 변경**

`DocumentChunker.java` 의 상수 영역에 추가:

```java
private static final String CHUNKER_VERSION = "v2";
```

`computeHash` 메서드(라인 66-74)를 다음으로 교체:

```java
public String computeHash(String content) {
    try {
        String input = CHUNKER_VERSION + ":" + content;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"`
Expected: PASS — 신규 테스트 + 기존 ComputeHash 의 다른 테스트들도 통과 (sameContent/differentContent/hash-format 테스트는 version 영향 받지 않음)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "$(cat <<'EOF'
feat(rag): computeHash 입력에 CHUNKER_VERSION prefix

chunker 알고리즘 변경 시 모든 청크의 source_hash 가 자동 변경되어
의미 캐시(qna_question_cache) 가 자연 만료된다.

미래 chunker 변경 시도 version 만 bump 하면 자동 처리.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `IngestionInternalController` reindex endpoint

목적: 운영 1회 reindex 호출을 위해 InternalApiKey 보호된 신규 endpoint 추가.

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

새 파일 `backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerTest.java` 생성:

```java
package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionInternalController.class)
class IngestionInternalControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AttachmentReindexService attachmentReindexService;

    @Test
    @DisplayName("POST /api/internal/ingestion/reindex/{policyId} 가 AttachmentReindexService.reindex 를 호출한다")
    @WithMockUser
    void reindex_invokesService() throws Exception {
        mockMvc.perform(post("/api/internal/ingestion/reindex/7").with(csrf()))
                .andExpect(status().isNoContent());
        verify(attachmentReindexService).reindex(7L);
    }
}
```

(주의: 실제 보안 통합 테스트는 별도. 여기는 슬라이스 테스트로 controller 동작만 검증)

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.presentation.controller.IngestionInternalControllerTest"`
Expected: FAIL — `IngestionInternalController` 클래스 미존재

- [ ] **Step 3: 컨트롤러 구현**

새 파일 `backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java` 생성:

```java
package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 운영용 ingestion 엔드포인트. InternalApiKeyFilter 보호.
 * 외부 사용자가 호출할 수 없고, 운영 스크립트/n8n 에서만 사용.
 */
@RestController
@RequestMapping("/api/internal/ingestion")
@RequiredArgsConstructor
public class IngestionInternalController {

    private final AttachmentReindexService attachmentReindexService;

    @PostMapping("/reindex/{policyId:\\d+}")
    public ResponseEntity<Void> reindex(@PathVariable Long policyId) {
        attachmentReindexService.reindex(policyId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.presentation.controller.IngestionInternalControllerTest"`
Expected: PASS

- [ ] **Step 5: 빌드 전체 테스트 + 컴파일 확인**

Run: `cd backend && ./gradlew build -x integrationTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/presentation/controller/IngestionInternalController.java backend/src/test/java/com/youthfit/ingestion/presentation/controller/IngestionInternalControllerTest.java
git commit -m "$(cat <<'EOF'
feat(ingestion): 운영용 reindex internal endpoint 추가

POST /api/internal/ingestion/reindex/{policyId} — AttachmentReindexService.reindex
호출. InternalApiKeyFilter 보호되어 X-Internal-Api-Key 헤더 필수.

청킹 알고리즘 변경 후 정책별 1회 reindex 트리거 용도.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: PR 생성 + 운영 검증 + 전체 reindex

목적: PR 머지 후 spec §7 의 단계 rollout 진행.

**Files:** 없음 (운영 단계)

- [ ] **Step 1: 변경 사항 푸시 + PR 생성**

권장 브랜치명: `feat/rag-table-aware-chunking` (작업 시작 시 main 에서 분기).

```bash
git push -u origin feat/rag-table-aware-chunking
gh pr create --title "feat(rag): RAG 청킹 의미 단위 보존 강화 — 표 인식 + 줄 보존 + overlap" --body "$(cat <<'EOF'
## Summary

- 표 인식 (연속 번호 행 3+ 패턴) + 분할 시 헤더 prepend
- splitBySize → splitByLines 다운그레이드 — 줄 boundary 보존
- 청크 간 ~80자 overlap (일반 평문 청크에만)
- CHUNKER_VERSION 을 source_hash 입력에 섞어 의미 캐시 자연 만료
- 운영 reindex endpoint 신규 추가 — `POST /api/internal/ingestion/reindex/{policyId}`

## Test plan

머지 후 단계 rollout — spec §7 참고:

1. 정책 7번 (청년내일저축계좌) reindex 1회
2. 검증 query 모음 (spec §8) 으로 Before/After 답변 비교
3. 표 query 5개 중 ≥4개 정답 + 자연어 query 5개 회귀 없음 → 전체 정책 reindex 진행

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: PR 머지 후 정책 7번 reindex**

```bash
curl -X POST http://localhost:8080/api/internal/ingestion/reindex/7 \
  -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}"
```

응답: `204 No Content`

서버 로그에서 다음 확인:
```
reindex policyId=7 chunks=N updated=true
attachment reindex event published: policyId=7
```

- [ ] **Step 3: 표 query 5개 답변 확인 (수동)**

QnA UI 또는 다음 curl 로 정책 7번에 다음 5개 query 를 던지고 답변 캡처:

| Query | 기대 |
|---|---|
| "중복수혜 안되는 통장 리스트 알려줘" | 중복 불가 표 내용 (기초생활보장/희망키움통장 등) |
| "어떤 통장이 중복수혜인지 리스트 알려줘" | 같은 표 회수 |
| "디딤씨앗통장 중복 가능한가요?" | "가능" |
| "꿈나래통장 중복 가능한가요?" | "가능" |
| "안되는 통장 리스트" | 중복 불가 표 회수 |

판정: 5개 중 ≥4개 정답이면 Step 4 로 진행. 미달이면 휴리스틱 파라미터(MIN_TABLE_ROWS, HEADER_PLAINTEXT_MIN_LEN)를 조정한 후속 PR 검토.

- [ ] **Step 4: 자연어 query 5개 회귀 확인 (수동)**

| Query | 회귀 기준 |
|---|---|
| "신청 자격이 뭐야?" | 기존 답변과 정보량 동등 |
| "지원 금액은 얼마야?" | 기존 답변과 수치 동일 |
| "신청 기간은 언제야?" | 기존 답변과 기간 동일 |
| "어디서 신청해?" | 기존 답변과 채널 동일 |
| "지원 대상은?" | 기존 답변과 대상 동일 |

판정: 5개 모두 회귀 없음 → Step 5 진행. 회귀 발생 시 spec §7.3 롤백 절차 (코드 revert + 재배포 + 전체 reindex).

- [ ] **Step 5: 전체 정책 reindex**

대상 정책 id 리스트 확보:
```bash
psql "$DATABASE_URL" -t -c "SELECT id FROM policy ORDER BY id;" > /tmp/policy_ids.txt
```

순차 reindex (병렬 시 OpenAI rate limit 위험):
```bash
while read -r id; do
    if [ -z "$id" ]; then continue; fi
    curl -X POST "http://localhost:8080/api/internal/ingestion/reindex/${id}" \
        -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}" \
        --silent --show-error
    echo "reindexed: $id"
    sleep 1
done < /tmp/policy_ids.txt
```

- [ ] **Step 6: 전체 reindex 완료 후 sanity check**

```bash
psql "$DATABASE_URL" -c "SELECT COUNT(DISTINCT policy_id) AS policies, COUNT(*) AS chunks FROM policy_document;"
```

기대: policies 가 정책 총수와 일치, chunks 수가 reindex 전과 비교했을 때 의미 있는 차이 (표 헤더 prepend 로 약간 증가 예상).

서버 로그에서 reindex 실패 entry 없는지:
```bash
grep "reindex failed" /var/log/youthfit-backend.log | tail
```

---

## 후속 / 미결 항목 (이번 PR 범위 밖)

- 자동화 retrieval 벤치마크 (ground-truth chunk 매핑) — v0 범위 X
- 섹션 헤더 prepend (■ ▶ Ⅰ 등 명시적 헤더 인식) — 후속 사이클
- Tika 가 표를 공백 다중으로 떨궈주는 PDF 추가 패턴 인식 — 운영 데이터 보고 결정
- 의미 캐시 v1 (intent 기반) — `docs/superpowers/specs/TODO_v1-semantic-cache-intent-based.md` 참고

## 검증 명령 요약

| 단계 | 명령 |
|---|---|
| 전체 단위 테스트 | `cd backend && ./gradlew test --tests "com.youthfit.rag.*"` |
| 컨트롤러 슬라이스 테스트 | `cd backend && ./gradlew test --tests "com.youthfit.ingestion.presentation.*"` |
| 전체 빌드 | `cd backend && ./gradlew build -x integrationTest` |
| 정책 7번 reindex | `curl -X POST .../api/internal/ingestion/reindex/7 -H "X-Internal-Api-Key: $KEY"` |
| 청크 분포 확인 | `psql -c "SELECT page_start, page_end, LENGTH(content) FROM policy_document WHERE policy_id=7 ORDER BY chunk_index;"` |
