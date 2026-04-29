# 첨부 단위 출처 trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가이드 highlights·pitfalls 항목의 출처를 첨부 PDF 의 페이지 단위까지 trace 하고, 사용자가 라벨 클릭으로 새 탭 PDF 의 정확한 페이지로 점프하도록 만든다.

**Architecture:** (1) Tika SAX page handler 로 PDF 페이지 메타 보존, (2) `PolicyAttachment.extractedText` 에 페이지 sentinel 박음, (3) AttachmentReindexService mergedContent 마커 강화, (4) DocumentChunker 가 본문/첨부 boundary 강제 분할 + 페이지 추적, (5) `policy_document` 에 `attachment_id`/`page_start`/`page_end` 컬럼 추가 (JPA `ddl-auto=update`), (6) GuideSourceField enum 에 `ATTACHMENT` + `AttachmentRef` record 도입, (7) LLM 청크 라벨에 `[chunk-N source=... attachment-id=N pages=A-B]` 메타 박고 LLM 이 그대로 인용하도록 시스템 프롬프트 강화, (8) GuideValidator 검증 5 (sourceField=ATTACHMENT 유효성) 추가, (9) `PROMPT_VERSION v3 → v4` 로 sourceHash 자동 무효화, (10) `PolicyAttachmentController` 추가 (S3 presigned redirect / Local stream / 외부 URL fallback 분기), (11) 프론트 `AttachmentSourceLink` 컴포넌트로 새 탭 + `#page=N` deep link.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring Data JPA + Hibernate, PostgreSQL 17 + pgvector, Apache Tika, AWS SDK v2 (S3), JUnit 5, React 19, TypeScript 5, Vite 6, TanStack Query v5, Tailwind CSS v4, Vitest + Testing Library.

**PR 분할:** 4분할 권장 (Phase 1 / Phase 2 / Phase 3 / Phase 4). 각 PR 머지 후에도 사용자 노출은 깨지지 않으며, Phase 4 머지 시점에 처음 ATTACHMENT 라벨이 사용자에게 보인다.

**관련 spec:** `docs/superpowers/specs/2026-04-29-attachment-source-trace-design.md`

---

## File Structure

### 백엔드 — 신규 파일

| 경로 | 책임 |
|---|---|
| `backend/src/main/java/com/youthfit/ingestion/domain/model/PageText.java` | PDF 추출 결과의 페이지 단위 record (page, text) |
| `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/PageAwareContentHandler.java` | Tika SAX ContentHandler 커스텀, 페이지 마커 인지하고 `List<PageText>` 누적 |
| `backend/src/main/java/com/youthfit/guide/domain/model/AttachmentRef.java` | 가이드 항목의 첨부 출처 trace record (attachmentId, pageStart, pageEnd) |
| `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyAttachmentApi.java` | Swagger 인터페이스 |
| `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyAttachmentController.java` | 첨부 redirect endpoint |
| `backend/src/main/java/com/youthfit/policy/application/service/RedirectAttachmentService.java` | presign / stream / external 분기 비즈니스 로직 |
| `backend/src/main/java/com/youthfit/policy/application/dto/result/AttachmentRedirectResult.java` | 분기 결과 record (sealed) |
| `backend/src/main/java/com/youthfit/policy/domain/exception/AttachmentNotFoundException.java` | 도메인 전용 예외 |

### 백엔드 — 수정 파일

| 경로 | 변경 |
|---|---|
| `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractor.java` | `BodyContentHandler` → `PageAwareContentHandler` + sentinel 포함 텍스트 반환 |
| `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java` | `mergeContent()` 에서 sentinel 인지 → 강화된 마커 (`=== 첨부 attachment-id=N name=... ===`, `--- page=M ---`) 출력 |
| `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` | 본문/첨부 boundary 강제 분할 + 페이지 boundary 추적, 청크별 `(attachmentId, pageStart, pageEnd)` 메타 채움 |
| `backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java` | `attachmentId`, `pageStart`, `pageEnd` 컬럼 추가 + `@Index` |
| `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java` | chunker 결과의 메타 그대로 PolicyDocument 에 인덱싱 |
| `backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java` | `ATTACHMENT` enum 추가 |
| `backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java` | `attachmentRef` 옵셔널 필드 추가 |
| `backend/src/main/java/com/youthfit/guide/domain/model/GuidePitfall.java` | `attachmentRef` 옵셔널 필드 추가 |
| `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` | `combinedSourceText()` 의 청크 라벨에 메타 박기 |
| `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` | SYSTEM_PROMPT 에 원칙 N (출처 라벨 정확성) 추가 + few-shot 1개 + `buildResponseFormat()` schema 갱신 (attachmentRef 추가, ATTACHMENT enum) |
| `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java` | 검증 5 (sourceField=ATTACHMENT 유효성) 추가 |
| `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` | `PROMPT_VERSION` `v3 → v4`, validator 호출에 `policy.attachments` 전달 |
| `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentStorage.java` | `Optional<String> presign(String key, Duration ttl)` default method 추가 |
| `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorage.java` | `presign()` override (AWS SDK `S3Presigner`) |
| `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyAttachmentResponse.java` | `id` 필드 추가 (현재 응답에 없음) |
| `docs/ENTITIES.md` | PolicyDocument / Guide 섹션의 변경 사항 반영 |

### 프론트엔드 — 신규 파일

| 경로 | 책임 |
|---|---|
| `frontend/src/components/policy/AttachmentSourceLink.tsx` | ATTACHMENT 항목 라벨 + 새 탭 링크 (`#page=N`) |
| `frontend/src/components/policy/__tests__/AttachmentSourceLink.test.tsx` | 단위 테스트 |

### 프론트엔드 — 수정 파일

| 경로 | 변경 |
|---|---|
| `frontend/src/types/policy.ts` | `GuideSourceField` 에 `'ATTACHMENT'`, `AttachmentRef` 인터페이스, `GuideHighlight`/`GuidePitfall` 에 `attachmentRef`, `PolicyAttachment` 에 `id` 필드 추가 |
| `frontend/src/components/policy/SourceLinkedListCard.tsx` | 내부 `AttachmentRef` → `AttachmentSummary` rename, 항목별 `sourceField=ATTACHMENT` 분기 → `<AttachmentSourceLink />`, 외 4종은 기존 `scrollAndHighlight` |

---

## Phase 1: Tika 페이지 추출 + extractedText sentinel (PR1 / 3h)

### Task 1.1: PageText record + PageAwareContentHandler — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/model/PageText.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/PageAwareContentHandler.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/PageAwareContentHandlerTest.java`

- [ ] **Step 1: PageText record 생성**

```java
package com.youthfit.ingestion.domain.model;

import java.util.Objects;

public record PageText(Integer page, String text) {
    public PageText {
        Objects.requireNonNull(text, "text must not be null");
        // page == null 허용 (HWP / 페이지 마커 없는 추출 결과)
    }
}
```

- [ ] **Step 2: PageAwareContentHandler 실패 테스트 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.domain.model.PageText;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageAwareContentHandlerTest {

    @Test
    void givenSamplePdf_whenParse_thenSplitsByPage() throws Exception {
        // given
        InputStream pdf = getClass().getResourceAsStream("/extractor/sample-3-pages.pdf");
        assertThat(pdf).isNotNull();

        PageAwareContentHandler handler = new PageAwareContentHandler();

        // when
        new AutoDetectParser().parse(pdf, handler, new Metadata(), new ParseContext());
        List<PageText> pages = handler.getPages();

        // then
        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).page()).isEqualTo(1);
        assertThat(pages.get(0).text()).isNotBlank();
        assertThat(pages.get(2).page()).isEqualTo(3);
    }

    @Test
    void givenPlainText_whenParse_thenSinglePageNullPage() throws Exception {
        InputStream txt = new ByteArrayInputStream("hello world".getBytes());

        PageAwareContentHandler handler = new PageAwareContentHandler();
        new AutoDetectParser().parse(txt, handler, new Metadata(), new ParseContext());
        List<PageText> pages = handler.getPages();

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).page()).isNull();
        assertThat(pages.get(0).text()).contains("hello world");
    }
}
```

- [ ] **Step 3: 테스트 실행 → 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.infrastructure.external.PageAwareContentHandlerTest`
Expected: 컴파일 실패 (`PageAwareContentHandler` 클래스 없음).

- [ ] **Step 4: 3페이지 sample PDF fixture 준비**

```bash
# 우리는 이미 sample.pdf 가 backend/src/test/resources/extractor/ 에 있다 (PR #46).
# 3페이지 PDF 가 없으면 LibreOffice/online tool 로 만들어 backend/src/test/resources/extractor/sample-3-pages.pdf 로 저장.
# 또는 기존 sample.pdf 가 1페이지일 가능성 → 다중 페이지 fixture 필요.
ls backend/src/test/resources/extractor/
```

만약 다중 페이지 fixture 가 없으면 다음으로 생성:

```bash
# Python 으로 3페이지 PDF 생성 (사용 가능 시)
python3 -c "
from reportlab.pdfgen import canvas
c = canvas.Canvas('backend/src/test/resources/extractor/sample-3-pages.pdf')
for i in [1, 2, 3]:
    c.drawString(100, 750, f'Page {i} content')
    c.showPage()
c.save()
"
# 또는 macOS 의 cupsfilter, pandoc 등 활용
```

만약 fixture 생성 환경이 없으면 사용자에게 sample-3-pages.pdf 제공 요청. (테스트 fixture 부재 시 Task 1.1 의 첫 테스트는 `@Disabled` 로 표기하고 두 번째 plain-text 테스트만 활성, 후속 PR 에서 fixture 추가 — 하지만 이건 fallback 이므로 가능하면 Step 4 에서 fixture 확보)

- [ ] **Step 5: PageAwareContentHandler 최소 구현**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.domain.model.PageText;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

/**
 * Tika SAX 핸들러. PDFParser 가 발생시키는 페이지 마커 (<div class="page">) 를 인지해
 * 페이지별 텍스트를 누적한다.
 *
 * - 페이지 마커가 발생하면 새 페이지 시작 (현재 누적분을 PageText 로 flush)
 * - 페이지 마커가 한 번도 발생 안 하면 (e.g. plain text) 단일 PageText(page=null, text=...) 로 종료
 */
public class PageAwareContentHandler extends ContentHandlerDecorator {

    private final List<PageText> pages = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private int pageNumber = 0;
    private boolean sawAnyPageMarker = false;

    public PageAwareContentHandler() {
        // Tika 의 BodyContentHandler(-1) 을 내부 위임자로 사용해
        // 본문 영역 텍스트만 수집 (HEAD/META 등 무시)
        super(new BodyContentHandler(-1));
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        super.startElement(uri, localName, qName, atts);
        if ("div".equalsIgnoreCase(localName)) {
            String cls = atts.getValue("class");
            if ("page".equals(cls)) {
                if (sawAnyPageMarker) {
                    // 직전 페이지 flush
                    flushPage();
                }
                sawAnyPageMarker = true;
                pageNumber++;
                current.setLength(0);
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        if (sawAnyPageMarker) {
            current.append(ch, start, length);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        if (sawAnyPageMarker) {
            flushPage();
        } else {
            // 페이지 마커 없는 경우 (HWP / plain text 등) — 단일 페이지 (page=null) 로 종료
            String body = ((BodyContentHandler) getContentHandler()).toString();
            pages.add(new PageText(null, body));
        }
    }

    private void flushPage() {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            pages.add(new PageText(pageNumber, text));
        }
    }

    public List<PageText> getPages() {
        return List.copyOf(pages);
    }
}
```

- [ ] **Step 6: 테스트 재실행 → PASS 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.infrastructure.external.PageAwareContentHandlerTest`
Expected: PASS (2 tests).

만약 `sample-3-pages.pdf` 부재로 첫 테스트 FAIL → fixture 확보 후 재실행. 그래도 안 되면 Tika 의 PDFParser 가 `<div class="page">` 가 아니라 다른 마커를 사용할 가능성 → 디버그 로그로 actual SAX events 확인하고 핸들러 보정.

디버깅용 라인 (임시):
```java
@Override
public void startElement(...) {
    System.out.println("startElement: " + localName + " class=" + atts.getValue("class"));
    ...
}
```

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/model/PageText.java \
        backend/src/main/java/com/youthfit/ingestion/infrastructure/external/PageAwareContentHandler.java \
        backend/src/test/java/com/youthfit/ingestion/infrastructure/external/PageAwareContentHandlerTest.java \
        backend/src/test/resources/extractor/sample-3-pages.pdf
git commit -m "$(cat <<'EOF'
feat(be): PageText record + PageAwareContentHandler 추가

Tika SAX 핸들러 커스텀해 PDFParser 의 페이지 마커 (<div class="page">)
인지하고 페이지별 텍스트 누적. 페이지 마커 없는 입력 (HWP/plain) 은
단일 PageText(page=null) 로 fallback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.2: TikaAttachmentExtractor 페이지 sentinel 박기 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractor.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractorTest.java`

- [ ] **Step 1: TikaAttachmentExtractor 페이지 sentinel 검증 테스트 추가**

테스트 파일이 이미 있으면 메서드 추가, 없으면 생성:

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TikaAttachmentExtractorTest {

    private final TikaAttachmentExtractor extractor = new TikaAttachmentExtractor();

    @Test
    void given3PagePdf_whenExtract_thenSentinelPerPage() throws Exception {
        InputStream pdf = getClass().getResourceAsStream("/extractor/sample-3-pages.pdf");
        long size = pdf.available();

        ExtractionResult result = extractor.extract(pdf, size);

        assertThat(result).isInstanceOf(ExtractionResult.Success.class);
        String text = ((ExtractionResult.Success) result).text();

        // sentinel 형식: \f<page=N>\n
        assertThat(text).contains("\f<page=1>");
        assertThat(text).contains("\f<page=2>");
        assertThat(text).contains("\f<page=3>");
    }

    @Test
    void givenPlainText_whenExtract_thenSinglePageNullSentinel() throws Exception {
        InputStream txt = new java.io.ByteArrayInputStream("hello".getBytes());
        ExtractionResult result = extractor.extract(txt, 5);

        assertThat(result).isInstanceOf(ExtractionResult.Success.class);
        String text = ((ExtractionResult.Success) result).text();
        assertThat(text).contains("\f<page=null>");
        assertThat(text).contains("hello");
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.infrastructure.external.TikaAttachmentExtractorTest`
Expected: FAIL — 현재 `extract()` 가 sentinel 안 박음.

- [ ] **Step 3: TikaAttachmentExtractor 변경**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.ingestion.domain.model.PageText;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@Order(20)
public class TikaAttachmentExtractor implements AttachmentExtractor {

    private static final List<String> SUPPORTED = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/html",
            "text/plain"
    );

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        return SUPPORTED.stream().anyMatch(s -> mediaType.toLowerCase().startsWith(s));
    }

    @Override
    public ExtractionResult extract(InputStream stream, long sizeBytes) {
        try {
            PageAwareContentHandler handler = new PageAwareContentHandler();
            new AutoDetectParser().parse(stream, handler, new Metadata(), new ParseContext());
            String sentineled = toSentineledText(handler.getPages());
            return new ExtractionResult.Success(sentineled);
        } catch (Exception e) {
            return new ExtractionResult.Failed(e.getMessage());
        }
    }

    /**
     * 페이지 단위 텍스트를 form-feed + page tag sentinel 로 직렬화한다.
     * 형식: \f<page=N>\n{text}\n\f<page=N+1>\n{text}...
     * 페이지가 알 수 없으면 (HWP/plain) page=null 로 박힘.
     */
    private String toSentineledText(List<PageText> pages) {
        StringBuilder sb = new StringBuilder();
        for (PageText p : pages) {
            sb.append('\f');
            sb.append("<page=").append(p.page() == null ? "null" : p.page()).append(">\n");
            sb.append(p.text()).append('\n');
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.infrastructure.external.TikaAttachmentExtractorTest`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractor.java \
        backend/src/test/java/com/youthfit/ingestion/infrastructure/external/TikaAttachmentExtractorTest.java
git commit -m "$(cat <<'EOF'
feat(be): TikaAttachmentExtractor 페이지 sentinel 박기

PageAwareContentHandler 로 페이지 분리한 결과를 form-feed + <page=N>
sentinel 로 직렬화해 ExtractionResult.Success.text() 에 담는다.
AttachmentReindexService 가 이 sentinel 인지하고 mergedContent 마커로 변환.

페이지 마커 없는 입력은 <page=null> 로 박혀 후속 단계에서 첨부 단위
fallback (page 정보 없음) 으로 자연스럽게 흐른다.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.3: Phase 0 sample 검증 (수동)

**Files:** (변경 없음)

- [ ] **Step 1: 로컬 환경에서 정책 7번 첨부 추출 재실행**

```bash
docker compose up -d --build backend
sleep 20

# 정책 7번 첨부의 extraction_status 를 PENDING 으로 되돌려 재추출 트리거
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "UPDATE policy_attachment SET extraction_status='PENDING', extraction_retry_count=0 WHERE policy_id=7;"

# 1~2분 대기 후 결과 조회
sleep 90
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "SELECT id, name, extraction_status, length(extracted_text) FROM policy_attachment WHERE policy_id=7;"
```

Expected: 정책 7번 첨부의 `extraction_status='EXTRACTED'`, `length(extracted_text)` 가 sentinel 포함되어 기존보다 약간 길어짐 (sentinel 자체 길이 + 페이지 수 × ~12자).

- [ ] **Step 2: extracted_text 의 sentinel 직접 확인**

```bash
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "SELECT substring(extracted_text from 1 for 200) FROM policy_attachment WHERE policy_id=7 LIMIT 1;" | cat -A
```

Expected: 출력 첫 부분에 `^L<page=1>` (또는 `\f<page=1>`) 마커가 보임.

- [ ] **Step 3: 결과 정리**

문제 발견 시 (예: 페이지 마커 안 박힘 → Tika 의 PDFParser 가 `<div class="page">` 가 아닌 다른 element 사용 → handler 디버그 로그 추가, fixture 와 실제 정책 PDF 의 SAX event 차이 분석).

문제 없으면 Phase 1 종료. PR 1 권장:

```
[BE] feat: 첨부 PDF 페이지 단위 추출 (Tika SAX page-aware handler)
```

PR 분기:

```bash
git checkout -b feat/be/attachment-page-extraction
git push -u origin feat/be/attachment-page-extraction
gh pr create --title "[BE] feat: 첨부 PDF 페이지 단위 추출" --body "$(cat <<'EOF'
## Summary
- TikaAttachmentExtractor 가 PageAwareContentHandler 로 PDF 페이지 마커 인지
- extractedText 에 form-feed + <page=N> sentinel 박음
- 페이지 마커 없는 입력 (HWP/plain) 은 <page=null> fallback

## Test plan
- [ ] PageAwareContentHandlerTest (2 tests) 통과
- [ ] TikaAttachmentExtractorTest (2 tests) 통과
- [ ] 정책 7번 첨부 재추출 후 sentinel 직접 확인
- [ ] 다음 단계 (Phase 2) 가 sentinel 인지하도록 보장

## 후속 (Phase 2)
- AttachmentReindexService 가 sentinel 인지하고 mergedContent 마커로 변환
- DocumentChunker 가 페이지 boundary 추적

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 2: 청크 boundary + 페이지 메타 + PolicyDocument 컬럼 (PR2 / 4h)

### Task 2.1: PolicyDocument 엔티티 컬럼 추가 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentTest.java`

- [ ] **Step 1: PolicyDocument 빌더 attachmentId/pageStart/pageEnd 테스트**

```java
package com.youthfit.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDocumentTest {

    @Test
    void givenAttachmentChunk_whenBuild_thenAttachmentMetaSet() {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(7L)
                .chunkIndex(2)
                .content("청크 텍스트")
                .sourceHash("abc")
                .attachmentId(12L)
                .pageStart(35)
                .pageEnd(37)
                .build();

        assertThat(doc.getAttachmentId()).isEqualTo(12L);
        assertThat(doc.getPageStart()).isEqualTo(35);
        assertThat(doc.getPageEnd()).isEqualTo(37);
    }

    @Test
    void givenBodyChunk_whenBuild_thenAttachmentMetaNull() {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(7L)
                .chunkIndex(0)
                .content("본문 청크")
                .sourceHash("abc")
                .build();

        assertThat(doc.getAttachmentId()).isNull();
        assertThat(doc.getPageStart()).isNull();
        assertThat(doc.getPageEnd()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.rag.domain.model.PolicyDocumentTest`
Expected: 컴파일 실패 (`attachmentId`, `pageStart`, `pageEnd` 빌더 메서드 없음).

- [ ] **Step 3: PolicyDocument 엔티티에 필드 추가**

기존 파일 상단에 `@Index` 추가, 필드 추가, getter 추가:

```java
package com.youthfit.rag.domain.model;

import com.youthfit.common.domain.model.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "policy_document",
    indexes = {
        @Index(name = "idx_policy_document_attachment", columnList = "attachment_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    // ↓ NEW
    @Column(name = "attachment_id")
    private Long attachmentId;        // null = 정책 본문 청크

    @Column(name = "page_start")
    private Integer pageStart;        // null = HWP / 본문 청크 / 페이지 메타 없음

    @Column(name = "page_end")
    private Integer pageEnd;
    // ↑ NEW

    @Builder
    private PolicyDocument(
            Long policyId,
            int chunkIndex,
            String content,
            String sourceHash,
            Long attachmentId,
            Integer pageStart,
            Integer pageEnd) {
        this.policyId = policyId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.sourceHash = sourceHash;
        this.attachmentId = attachmentId;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public boolean hasEmbedding() {
        return embedding != null;
    }
}
```

- [ ] **Step 4: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.rag.domain.model.PolicyDocumentTest`
Expected: PASS (2 tests).

- [ ] **Step 5: 로컬 DB 컬럼 자동 ALTER 확인**

```bash
docker compose up -d --build backend
sleep 30

docker compose exec postgres psql -U youthfit -d youthfit \
  -c "\d policy_document"
```

Expected: 출력에 `attachment_id bigint`, `page_start integer`, `page_end integer` 와 `idx_policy_document_attachment` 인덱스가 보임. `ddl-auto=update` 가 자동 ALTER TABLE 적용.

- [ ] **Step 6: prod 마이그레이션 SQL 메모**

prod 배포 전에 실행할 SQL 을 spec 의 §9 절차에 맞춰 따로 메모 (PR description 또는 운영 채널). 본 plan 에선 SQL 만 명시:

```sql
ALTER TABLE policy_document
  ADD COLUMN attachment_id BIGINT NULL,
  ADD COLUMN page_start INTEGER NULL,
  ADD COLUMN page_end INTEGER NULL;
CREATE INDEX idx_policy_document_attachment ON policy_document(attachment_id);
```

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java \
        backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentTest.java
git commit -m "$(cat <<'EOF'
feat(rag): PolicyDocument 에 attachment_id/page_start/page_end 컬럼 추가

가이드 highlights/pitfalls 의 첨부 단위 출처 trace 를 위해 청크 메타에
첨부 ID + 페이지 범위 보유. 본문 청크 (정책 body 발췌) 는 NULL 유지.
local 환경은 ddl-auto=update 가 자동 ALTER, prod 는 별도 SQL 절차.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.2: AttachmentReindexService.mergeContent 마커 강화 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java`

- [ ] **Step 1: mergeContent 테스트 추가**

기존 테스트 파일에 메서드 추가 (없으면 생성):

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentReindexServiceTest {

    @Test
    void givenAttachmentWithSentinels_whenMerge_thenMarkersWithIdAndPages() throws Exception {
        // given
        Policy policy = mockPolicyWithBody("정책 본문 텍스트");
        PolicyAttachment att = mockAttachment(12L, "시행규칙.pdf",
                "\f<page=1>\n첫 페이지 텍스트\n\f<page=2>\n둘째 페이지 텍스트\n");

        AttachmentReindexService service = new AttachmentReindexService(
                null, null, null, null, 200);

        // when
        Method m = AttachmentReindexService.class
                .getDeclaredMethod("mergeContent", Policy.class, List.class);
        m.setAccessible(true);
        String merged = (String) m.invoke(service, policy, List.of(att));

        // then
        assertThat(merged).contains("=== 정책 본문 ===");
        assertThat(merged).contains("정책 본문 텍스트");
        assertThat(merged).contains("=== 첨부 attachment-id=12 name=\"시행규칙.pdf\" ===");
        assertThat(merged).contains("--- page=1 ---");
        assertThat(merged).contains("첫 페이지 텍스트");
        assertThat(merged).contains("--- page=2 ---");
        assertThat(merged).contains("둘째 페이지 텍스트");
    }

    @Test
    void givenHwpAttachmentWithoutPages_whenMerge_thenSinglePageNullMarker() throws Exception {
        Policy policy = mockPolicyWithBody("본문");
        PolicyAttachment hwp = mockAttachment(13L, "안내문.hwp",
                "\f<page=null>\n전체 텍스트\n");

        AttachmentReindexService service = new AttachmentReindexService(
                null, null, null, null, 200);

        Method m = AttachmentReindexService.class
                .getDeclaredMethod("mergeContent", Policy.class, List.class);
        m.setAccessible(true);
        String merged = (String) m.invoke(service, policy, List.of(hwp));

        assertThat(merged).contains("=== 첨부 attachment-id=13 name=\"안내문.hwp\" ===");
        assertThat(merged).contains("--- page=null ---");
        assertThat(merged).contains("전체 텍스트");
    }

    private Policy mockPolicyWithBody(String body) {
        // 주석: 실제 Policy 도메인 객체 생성 — 도메인 빌더 사용 또는 reflection.
        // 본 plan 은 Policy 의 정확한 빌더 시그니처 모름 → 간단히 reflection 또는
        // PolicyTestFixture 가 있다면 그걸 사용. 없으면 Mockito mock 으로 대체.
        Policy p = org.mockito.Mockito.mock(Policy.class);
        org.mockito.Mockito.when(p.getBody()).thenReturn(body);
        return p;
    }

    private PolicyAttachment mockAttachment(Long id, String name, String text) {
        PolicyAttachment a = org.mockito.Mockito.mock(PolicyAttachment.class);
        org.mockito.Mockito.when(a.getId()).thenReturn(id);
        org.mockito.Mockito.when(a.getName()).thenReturn(name);
        org.mockito.Mockito.when(a.getExtractedText()).thenReturn(text);
        return a;
    }
}
```

> **참고**: `AttachmentReindexService` 의 정확한 생성자 시그니처는 기존 코드 그대로 유지. 위 테스트의 `null, null, null, null, 200` 인자 부분은 실제 시그니처 맞춰 변경 (예: `policyAttachmentRepository`, `documentChunker`, `ragIndexingService`, `guideGenerationService`, maxContentKb).

- [ ] **Step 2: 테스트 실행 → FAIL**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.application.service.AttachmentReindexServiceTest`
Expected: FAIL — 현재 출력엔 `attachment-id=` 가 없음 (`=== 첨부: name ===` 형식만).

- [ ] **Step 3: AttachmentReindexService.mergeContent 변경**

기존 mergeContent 본문 (line 65-102 주변) 의 첨부 헤더 + 본문 처리 부분을 다음으로 교체:

```java
private static final java.util.regex.Pattern PAGE_SENTINEL =
        java.util.regex.Pattern.compile("\\f<page=([^>]+)>\\n");

String mergeContent(Policy policy, List<PolicyAttachment> attachments) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== 정책 본문 ===\n").append(safe(policy.getBody()));

    int budget = maxContentKb * 1024;
    int used = sb.length();

    for (PolicyAttachment att : attachments) {
        String extracted = att.getExtractedText();
        if (extracted == null || extracted.isBlank()) continue;

        String header = "\n\n=== 첨부 attachment-id=" + att.getId()
                      + " name=\"" + att.getName() + "\" ===\n";
        if (used + header.length() > budget) break;
        sb.append(header);
        used += header.length();

        // sentinel → 마커 변환
        String body = sentinelToMarkers(extracted);
        if (used + body.length() > budget) {
            int remaining = budget - used;
            sb.append(body, 0, Math.max(0, remaining));
            used = budget;
            break;
        }
        sb.append(body);
        used += body.length();
    }

    return sb.toString();
}

/**
 * extractedText 안의 \f<page=N>\n sentinel 을 LLM 친화적 마커로 변환.
 *  \f<page=1>\n... → \n--- page=1 ---\n...
 *  \f<page=null>\n... → \n--- page=null ---\n...
 */
private String sentinelToMarkers(String extractedText) {
    return PAGE_SENTINEL.matcher(extractedText)
            .replaceAll(matchResult -> "\n--- page=" + matchResult.group(1) + " ---\n");
}

private static String safe(String s) { return s == null ? "" : s; }
```

> **주의**: 기존 maxContentKb 변수 이름과 사용 방식이 다를 수 있음. 기존 코드의 `byte` vs `length` 구분, KB→byte 환산 확인. 본 plan 은 `String.length()` (chars) 기준 근사로 사용.

- [ ] **Step 4: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.application.service.AttachmentReindexServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java
git commit -m "$(cat <<'EOF'
feat(ingestion): mergedContent 마커 강화 (attachment-id + 페이지)

첨부 헤더에 attachment-id 박고, extractedText 의 \\f<page=N>\\n sentinel
을 \\n--- page=N ---\\n 마커로 변환. DocumentChunker 와 LLM 청크 라벨이
이 마커 인지하고 청크별 출처 메타 채움.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.3: DocumentChunker boundary 분할 + 페이지 추적 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: DocumentChunker 분할 테스트 추가**

```java
package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
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

        // 본문 청크 1개 + 첨부 청크 1개 (또는 그 이상)
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
    void givenMultiplePages_whenChunkSpansPages_thenPageRangeTracked() {
        // 한 청크에 page 1~2 가 걸치도록 짧은 페이지 텍스트 + 충분한 chunkSize
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

        // 모든 청크는 attachment_id=12, pageStart/pageEnd 가 청크가 걸친 페이지 범위
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.getAttachmentId()).isEqualTo(12L);
            assertThat(c.getPageStart()).isNotNull();
            assertThat(c.getPageEnd()).isNotNull();
            assertThat(c.getPageStart()).isLessThanOrEqualTo(c.getPageEnd());
        });
        // 최소 1개 청크는 page range 가 1-3 또는 일부 (청크 사이즈에 따라)
    }

    @Test
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
```

- [ ] **Step 2: 테스트 실행 → FAIL**

Run: `cd backend && ./gradlew test --tests com.youthfit.rag.domain.service.DocumentChunkerTest`
Expected: FAIL — 현재 chunker 가 boundary 인지 안 함.

- [ ] **Step 3: DocumentChunker 변경**

기존 `chunk()` 메서드를 다음으로 교체. 핵심 알고리즘: (1) 본문/첨부 boundary 마커로 입력을 segment 들로 분할, (2) 각 segment 안에서 페이지 마커 추적하며 character 길이 기반 청크화.

```java
package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.PolicyDocument;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentChunker {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;

    // === 첨부 attachment-id=12 name="..." ===
    private static final Pattern ATTACHMENT_HEADER = Pattern.compile(
            "===\\s*첨부\\s+attachment-id=(\\d+)\\s+name=\"([^\"]*)\"\\s*===");
    // === 정책 본문 ===
    private static final String BODY_HEADER = "=== 정책 본문 ===";
    // --- page=N --- (N 은 정수 또는 "null")
    private static final Pattern PAGE_MARKER = Pattern.compile("---\\s*page=([^\\s]+)\\s*---");

    private final int maxChunkSize;

    public DocumentChunker() { this(DEFAULT_MAX_CHUNK_SIZE); }

    public DocumentChunker(int maxChunkSize) { this.maxChunkSize = maxChunkSize; }

    public List<PolicyDocument> chunk(Long policyId, String content) {
        List<Segment> segments = splitToSegments(content);
        List<PolicyDocument> result = new ArrayList<>();
        int globalIndex = 0;

        for (Segment seg : segments) {
            for (Chunk c : chunkSegment(seg)) {
                result.add(PolicyDocument.builder()
                        .policyId(policyId)
                        .chunkIndex(globalIndex++)
                        .content(c.text())
                        .sourceHash(computeHash(c.text()))
                        .attachmentId(seg.attachmentId())
                        .pageStart(c.pageStart())
                        .pageEnd(c.pageEnd())
                        .build());
            }
        }
        return result;
    }

    /** 본문/첨부 boundary 로 입력을 segment 들로 분할 */
    private List<Segment> splitToSegments(String content) {
        List<Segment> segments = new ArrayList<>();

        // 본문 처리 — content 가 BODY_HEADER 로 시작한다고 가정
        int bodyStart = content.indexOf(BODY_HEADER);
        Matcher matcher = ATTACHMENT_HEADER.matcher(content);
        int firstAtt = matcher.find() ? matcher.start() : content.length();
        matcher.reset();

        if (bodyStart != -1) {
            String bodyText = content.substring(bodyStart + BODY_HEADER.length(), firstAtt).trim();
            if (!bodyText.isBlank()) {
                segments.add(new Segment(null, bodyText));
            }
        } else if (firstAtt > 0) {
            // 본문 헤더가 없으면 첫 첨부 전 모두를 body 로 간주
            String pre = content.substring(0, firstAtt).trim();
            if (!pre.isBlank()) segments.add(new Segment(null, pre));
        }

        // 첨부 segment 추출
        while (matcher.find()) {
            long attachmentId = Long.parseLong(matcher.group(1));
            int segStart = matcher.end();

            int segEnd;
            if (matcher.find()) {
                segEnd = matcher.start();
                matcher.region(matcher.start(), content.length()); // 다음 iteration 에서 다시 매치되도록
                matcher.reset();
                // 다시 처음부터 매치를 시도하지 않도록 별도 strategy 필요 — 단순화 위해 재정렬:
            } else {
                segEnd = content.length();
            }
            // 위 로직은 복잡해질 수 있음. 단순화:
            // 모든 ATTACHMENT_HEADER 매치 위치 미리 수집해 페어링.
            break;
        }
        // ↓ 위의 단순화된 단일-pass 방식 대신 명시적 2-pass 로 재작성
        return splitToSegmentsClean(content);
    }

    /** ATTACHMENT_HEADER 위치를 미리 수집해 segment 분할 */
    private List<Segment> splitToSegmentsClean(String content) {
        List<int[]> attHeaders = new ArrayList<>(); // [start, end, attachmentId]
        Matcher m = ATTACHMENT_HEADER.matcher(content);
        while (m.find()) {
            int s = m.start();
            int e = m.end();
            long attId = Long.parseLong(m.group(1));
            attHeaders.add(new int[]{s, e, (int) attId}); // attId 가 int 범위 가정 — long 필요시 다른 자료구조
        }

        List<Segment> segments = new ArrayList<>();

        int firstHeaderStart = attHeaders.isEmpty() ? content.length() : attHeaders.get(0)[0];

        // 본문 segment
        int bodyHeaderIdx = content.indexOf(BODY_HEADER);
        int bodyStart = bodyHeaderIdx == -1 ? 0 : bodyHeaderIdx + BODY_HEADER.length();
        if (bodyStart < firstHeaderStart) {
            String bodyText = content.substring(bodyStart, firstHeaderStart).trim();
            if (!bodyText.isBlank()) segments.add(new Segment(null, bodyText));
        }

        // 첨부 segments
        for (int i = 0; i < attHeaders.size(); i++) {
            int[] h = attHeaders.get(i);
            int segStart = h[1];
            int segEnd = (i + 1 < attHeaders.size()) ? attHeaders.get(i + 1)[0] : content.length();
            String segText = content.substring(segStart, segEnd).trim();
            if (!segText.isBlank()) {
                segments.add(new Segment((long) h[2], segText));
            }
        }

        return segments;
    }

    /** Segment 내 페이지 마커 추적하며 character 길이 기반 청크 분할 */
    private List<Chunk> chunkSegment(Segment seg) {
        List<Chunk> chunks = new ArrayList<>();
        String text = seg.text();

        // 페이지 마커 위치 + 값 수집
        record PageMark(int offset, Integer page) {}
        List<PageMark> marks = new ArrayList<>();
        Matcher pm = PAGE_MARKER.matcher(text);
        while (pm.find()) {
            String v = pm.group(1);
            Integer page = "null".equals(v) ? null : Integer.parseInt(v);
            marks.add(new PageMark(pm.start(), page));
        }

        // character 길이 단위로 단순 분할 (단락/문장 분할 로직은 단순화)
        int len = text.length();
        int cursor = 0;
        while (cursor < len) {
            int end = Math.min(cursor + maxChunkSize, len);
            String chunkText = text.substring(cursor, end);

            // 청크가 걸친 페이지 범위 산출
            Integer pageStart = null;
            Integer pageEnd = null;
            for (PageMark mk : marks) {
                if (mk.offset() >= cursor && mk.offset() < end) {
                    if (pageStart == null) pageStart = mk.page();
                    pageEnd = mk.page();
                }
            }
            // 청크 시작 직전 페이지 마커가 있다면 그 페이지가 시작
            if (pageStart == null) {
                for (int i = marks.size() - 1; i >= 0; i--) {
                    if (marks.get(i).offset() < cursor) {
                        pageStart = marks.get(i).page();
                        pageEnd = pageStart;
                        break;
                    }
                }
            }

            chunks.add(new Chunk(chunkText.trim(), pageStart, pageEnd));
            cursor = end;
        }
        return chunks;
    }

    public String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record Segment(Long attachmentId, String text) {}
    private record Chunk(String text, Integer pageStart, Integer pageEnd) {}
}
```

> **단순화 노트**: 위 구현은 character 단위 분할로 단순화. 기존 단락(`\n\n`)/문장(`. `) 분할 로직을 그대로 유지하려면 `chunkSegment` 안에서 단락 우선 → 문장 → 고정 크기 3계층으로 다시 작성. 본 plan 의 1차 구현은 character 분할로 통과하고, 단락/문장 분할은 후속 PR 또는 본 PR 의 추가 step 으로 보강.

- [ ] **Step 4: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.rag.domain.service.DocumentChunkerTest`
Expected: PASS (3 tests).

만약 단락/문장 분할 로직을 보존하고 싶고 기존 테스트가 있다면, 그 테스트도 같이 통과하도록 `chunkSegment` 안에서 단락 우선 분할을 추가:

```java
// chunkSegment() 안에서, 단락(\n\n) 단위 우선 분할 후 길이 초과 시 2차 분할
String[] paragraphs = text.split("\n\n");
StringBuilder buf = new StringBuilder();
int bufStartOffset = 0;
int absOffset = 0;

for (String para : paragraphs) {
    if (buf.length() + para.length() + 2 > maxChunkSize && buf.length() > 0) {
        emit(chunks, buf.toString(), marks, bufStartOffset, bufStartOffset + buf.length());
        buf.setLength(0);
        bufStartOffset = absOffset;
    }
    if (buf.length() > 0) buf.append("\n\n");
    buf.append(para);
    absOffset += para.length() + 2;
}
if (buf.length() > 0) {
    emit(chunks, buf.toString(), marks, bufStartOffset, bufStartOffset + buf.length());
}
```

→ 단락/문장 우선 분할까지 보강하는 step 을 추가하려면 별도 sub-task 분리. 현재 plan 은 character 분할만 구현. 후속 PR 에서 확장.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java \
        backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "$(cat <<'EOF'
feat(rag): DocumentChunker 본문/첨부 boundary 분할 + 페이지 메타 추적

mergedContent 의 정책 본문 / 첨부 boundary 에서 청크 강제 분할 (한 청크
= 단일 출처 보장). 첨부 segment 안의 페이지 마커 (--- page=N ---) 로
청크가 걸친 페이지 범위 (pageStart/pageEnd) 추적해 PolicyDocument 메타
채움. HWP 등 페이지 메타 없는 첨부는 pageStart/pageEnd = null.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.4: ENTITIES.md 갱신 + Phase 2 빌드 검증

**Files:**
- Modify: `docs/ENTITIES.md`

- [ ] **Step 1: ENTITIES.md 의 PolicyDocument 섹션 갱신**

`docs/ENTITIES.md` 에서 PolicyDocument 항목을 찾아 다음 컬럼 추가 명시:

```markdown
### 4.1 PolicyDocument — `policy_document`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| ... 기존 ... | | |
| attachment_id | BIGINT NULL | 첨부 청크인 경우 PolicyAttachment.id, 본문 청크는 NULL |
| page_start | INTEGER NULL | 청크가 걸친 페이지 시작 (PDF). HWP/본문은 NULL |
| page_end | INTEGER NULL | 청크가 걸친 페이지 끝 (PDF). HWP/본문은 NULL |

인덱스: `idx_policy_document_attachment` on `attachment_id` (가이드 검증 시 정책별 청크의 첨부 매핑 빠른 조회).
```

- [ ] **Step 2: 백엔드 전체 빌드**

```bash
cd backend && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL. 단위 테스트 + 슬라이스 테스트 모두 통과.

빌드 실패 시 → 로그 분석. 가장 가능성 큰 원인:
- 기존 `RagIndexingService` 가 PolicyDocument 빌더 호출하는데 새 메타 필드 없는 경우 (Lombok 빌더는 forward compatible 하므로 컴파일 통과 가능)
- 기존 `AttachmentReindexService` 의 mergeContent 가 `private` 이 아니라 `package-private` 이라 reflection 없이 호출됨 → 테스트 수정

- [ ] **Step 3: 통합 검증 (수동)**

```bash
docker compose up -d --build backend
sleep 30

# 정책 7번 첨부 재추출 + 가이드 재인덱싱 트리거
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "UPDATE policy_attachment SET extraction_status='PENDING' WHERE policy_id=7;"

sleep 90

# 결과 확인
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "SELECT chunk_index, attachment_id, page_start, page_end, length(content) FROM policy_document WHERE policy_id=7 ORDER BY chunk_index;"
```

Expected: 본문 청크 1개 (attachment_id NULL), 첨부 청크 N개 (attachment_id 채워짐, page_start/page_end 채워짐 if PDF).

- [ ] **Step 4: 커밋 + Phase 2 마무리**

```bash
git add docs/ENTITIES.md
git commit -m "$(cat <<'EOF'
docs(entities): PolicyDocument 에 attachment_id/page_start/page_end 명시

첨부 단위 출처 trace 작업 (Phase 2) 의 컬럼 추가 결과 반영.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

PR 2 권장:

```
[BE] feat: 청크 메타에 첨부/페이지 trace (Phase 2/4)
```

---

## Phase 3: Guide 도메인 + LLM 입력/응답 + 검증 5 (PR3 / 4h)

### Task 3.1: AttachmentRef record 신규 + GuideSourceField ATTACHMENT 추가 — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/AttachmentRef.java`
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java`
- Test: `backend/src/test/java/com/youthfit/guide/domain/model/AttachmentRefTest.java`

- [ ] **Step 1: AttachmentRef 불변식 테스트**

```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentRefTest {

    @Test
    void givenValidPageRange_whenConstruct_thenOk() {
        AttachmentRef ref = new AttachmentRef(12L, 35, 37);
        assertThat(ref.attachmentId()).isEqualTo(12L);
        assertThat(ref.pageStart()).isEqualTo(35);
        assertThat(ref.pageEnd()).isEqualTo(37);
    }

    @Test
    void givenSinglePage_whenStartEqualsEnd_thenOk() {
        AttachmentRef ref = new AttachmentRef(12L, 35, 35);
        assertThat(ref.pageStart()).isEqualTo(ref.pageEnd());
    }

    @Test
    void givenHwpFallback_whenPagesNull_thenOk() {
        AttachmentRef ref = new AttachmentRef(13L, null, null);
        assertThat(ref.pageStart()).isNull();
        assertThat(ref.pageEnd()).isNull();
    }

    @Test
    void givenAttachmentIdNull_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new AttachmentRef(null, 35, 37))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenOnlyPageStart_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new AttachmentRef(12L, 35, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("함께 존재");
    }

    @Test
    void givenStartGreaterThanEnd_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new AttachmentRef(12L, 37, 35))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageStart");
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패**

Run: `cd backend && ./gradlew test --tests com.youthfit.guide.domain.model.AttachmentRefTest`
Expected: 컴파일 실패 (`AttachmentRef` 없음).

- [ ] **Step 3: AttachmentRef record 생성**

```java
package com.youthfit.guide.domain.model;

import java.util.Objects;

/**
 * 가이드 highlights/pitfalls 항목의 첨부 출처 trace.
 *
 * - sourceField=ATTACHMENT 일 때 not-null
 * - HWP/페이지 메타 없는 첨부는 pageStart/pageEnd null (둘 다 null 또는 둘 다 not-null)
 * - 페이지 범위는 pageStart ≤ pageEnd
 */
public record AttachmentRef(
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd) {

    public AttachmentRef {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        if ((pageStart == null) != (pageEnd == null)) {
            throw new IllegalArgumentException(
                    "pageStart 와 pageEnd 는 함께 존재해야 함");
        }
        if (pageStart != null && pageStart > pageEnd) {
            throw new IllegalArgumentException(
                    "pageStart 는 pageEnd 이하여야 함");
        }
    }
}
```

- [ ] **Step 4: GuideSourceField 에 ATTACHMENT 추가**

```java
package com.youthfit.guide.domain.model;

public enum GuideSourceField {
    SUPPORT_TARGET,
    SELECTION_CRITERIA,
    SUPPORT_CONTENT,
    BODY,
    ATTACHMENT          // ← NEW: 첨부에서 가져온 정보
}
```

- [ ] **Step 5: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.guide.domain.model.AttachmentRefTest`
Expected: PASS (6 tests).

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/AttachmentRef.java \
        backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java \
        backend/src/test/java/com/youthfit/guide/domain/model/AttachmentRefTest.java
git commit -m "$(cat <<'EOF'
feat(guide): AttachmentRef record + GuideSourceField.ATTACHMENT

가이드 항목의 첨부 출처 trace 도메인 모델. 페이지 range + HWP fallback
(pages null) 지원. 불변식: attachmentId not-null, pageStart/pageEnd 동시
존재 또는 동시 null, pageStart ≤ pageEnd.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.2: GuideHighlight / GuidePitfall 에 attachmentRef 추가 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java`
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/GuidePitfall.java`
- Test: 기존 테스트 또는 새 테스트 (단순 record 변경이라 별도 테스트 단순)

- [ ] **Step 1: GuideHighlight.attachmentRef 필드 추가**

```java
package com.youthfit.guide.domain.model;

public record GuideHighlight(
        String text,
        GuideSourceField sourceField,
        AttachmentRef attachmentRef    // null when sourceField != ATTACHMENT
) {
    public GuideHighlight(String text, GuideSourceField sourceField) {
        this(text, sourceField, null);
    }
}
```

> **호환성**: 보조 생성자로 기존 `new GuideHighlight(text, sourceField)` 호출처 그대로 유지 (자동 attachmentRef=null).

- [ ] **Step 2: GuidePitfall 동일 패턴**

```java
package com.youthfit.guide.domain.model;

public record GuidePitfall(
        String text,
        GuideSourceField sourceField,
        AttachmentRef attachmentRef
) {
    public GuidePitfall(String text, GuideSourceField sourceField) {
        this(text, sourceField, null);
    }
}
```

- [ ] **Step 3: 빌드 → 컴파일 PASS**

Run: `cd backend && ./gradlew compileJava`
Expected: PASS. 호환성 보조 생성자 덕에 기존 코드 깨지지 않음.

- [ ] **Step 4: 단위 테스트 실행** (기존 GuideValidatorTest, OpenAiChatClient 관련 테스트 등)

Run: `cd backend && ./gradlew test`
Expected: PASS (모든 테스트). attachmentRef 가 옵셔널이라 기존 테스트에 영향 없음.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java \
        backend/src/main/java/com/youthfit/guide/domain/model/GuidePitfall.java
git commit -m "$(cat <<'EOF'
feat(guide): GuideHighlight/GuidePitfall 에 attachmentRef 옵셔널 필드

sourceField=ATTACHMENT 일 때만 not-null. 기존 호출처 호환을 위해
보조 생성자 (attachmentRef=null 자동) 추가.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.3: GuideGenerationInput.combinedSourceText 청크 라벨 메타 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java`

기존 `chunkContents` 는 `List<String>` 이었으나, attachmentId/page 메타가 필요하므로 청크별 메타를 함께 전달해야 함. 두 가지 옵션:

- **Option A**: `chunkContents` 를 `List<ChunkInput>` 로 교체 (텍스트 + attachmentId? + pageStart? + pageEnd?)
- **Option B**: 별도 `chunkMetas: List<ChunkMeta>` 병렬 전달

**Option A 채택** (응집도 ↑, GuideGenerationService 호출처에서 PolicyDocument 직접 변환).

- [ ] **Step 1: ChunkInput record + combinedSourceText 라벨 변경 테스트**

```java
package com.youthfit.guide.application.dto.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationInputTest {

    @Test
    void givenBodyAndAttachmentChunks_whenCombinedSourceText_thenLabelsHaveMeta() {
        GuideGenerationInput input = new GuideGenerationInput(
                7L, "제목", 2026,
                "summary", "body", "supportTarget", "selectionCriteria", "supportContent",
                "contact", "organization",
                List.of(
                        new ChunkInput("본문 텍스트", null, null, null),
                        new ChunkInput("첨부 1페이지~3페이지 텍스트", 12L, 1, 3),
                        new ChunkInput("HWP 텍스트", 13L, null, null)
                ),
                null);

        String txt = input.combinedSourceText();

        assertThat(txt).contains("[chunk-0 source=BODY]");
        assertThat(txt).contains("본문 텍스트");
        assertThat(txt).contains("[chunk-1 source=ATTACHMENT attachment-id=12 pages=1-3]");
        assertThat(txt).contains("첨부 1페이지~3페이지 텍스트");
        assertThat(txt).contains("[chunk-2 source=ATTACHMENT attachment-id=13]");
        assertThat(txt).doesNotContain("[chunk-2 source=ATTACHMENT attachment-id=13 pages=");
    }
}
```

- [ ] **Step 2: ChunkInput record 신규**

```java
// backend/src/main/java/com/youthfit/guide/application/dto/command/ChunkInput.java
package com.youthfit.guide.application.dto.command;

public record ChunkInput(
        String content,
        Long attachmentId,    // null = 정책 본문 청크
        Integer pageStart,    // null = HWP / 본문
        Integer pageEnd
) {}
```

- [ ] **Step 3: GuideGenerationInput 변경**

```java
package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.application.IncomeBracketReference;

import java.util.List;

public record GuideGenerationInput(
        Long policyId,
        String title,
        Integer referenceYear,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String contact,
        String organization,
        List<ChunkInput> chunks,        // ← was List<String> chunkContents
        IncomeBracketReference referenceData) {

    public String combinedSourceText() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "summary", summary);
        appendSection(sb, "body", body);
        appendSection(sb, "supportTarget", supportTarget);
        appendSection(sb, "selectionCriteria", selectionCriteria);
        appendSection(sb, "supportContent", supportContent);
        if (referenceYear != null) {
            sb.append("[referenceYear]\n").append(referenceYear).append("\n\n");
        }
        for (int i = 0; i < chunks.size(); i++) {
            ChunkInput c = chunks.get(i);
            sb.append('[').append("chunk-").append(i);
            if (c.attachmentId() == null) {
                sb.append(" source=BODY]\n");
            } else {
                sb.append(" source=ATTACHMENT attachment-id=").append(c.attachmentId());
                if (c.pageStart() != null) {
                    if (c.pageStart().equals(c.pageEnd())) {
                        sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
                    } else {
                        sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
                    }
                }
                sb.append("]\n");
            }
            sb.append(c.content()).append("\n\n");
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String name, String value) {
        if (value != null && !value.isBlank()) {
            sb.append('[').append(name).append("]\n").append(value).append("\n\n");
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.guide.application.dto.command.GuideGenerationInputTest`
Expected: PASS.

- [ ] **Step 5: GuideGenerationService 호출처 변경**

`GuideGenerationService` 가 `GuideGenerationInput` 만들 때 `chunks` 에 `ChunkInput` 으로 PolicyDocument 메타 매핑:

```java
// GuideGenerationService.java 안 input 만드는 부분 (line 70~80 부근 추정)
List<ChunkInput> chunkInputs = chunks.stream()
        .map(d -> new ChunkInput(
                d.getContent(),
                d.getAttachmentId(),
                d.getPageStart(),
                d.getPageEnd()))
        .toList();
GuideGenerationInput input = new GuideGenerationInput(
        ..., chunkInputs, reference);
```

- [ ] **Step 6: 컴파일 + 전체 테스트 PASS 확인**

Run: `cd backend && ./gradlew test`
Expected: 기존 GuideGenerationServiceTest 등 통과. 만약 input 만들 때 List<String> 으로 보내던 곳 있으면 ChunkInput 으로 변환.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/command/ChunkInput.java \
        backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java
git commit -m "$(cat <<'EOF'
feat(guide): 청크 라벨에 첨부/페이지 메타 박기

GuideGenerationInput.chunks 를 List<ChunkInput> 으로 교체해 청크별
attachmentId / pageStart / pageEnd 보유. combinedSourceText() 가
[chunk-N source=ATTACHMENT attachment-id=12 pages=1-3] 형식으로 라벨링.
LLM 이 응답에 그대로 인용해 attachmentRef 채움.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.4: OpenAiChatClient 시스템 프롬프트 + few-shot + structured output schema

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- Test: `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java` (있으면 보강, 없으면 schema unit test 신규)

- [ ] **Step 1: SYSTEM_PROMPT 에 원칙 N 추가**

기존 `SYSTEM_PROMPT` 상수 (line:33-145) 의 끝부분에 다음 원칙 추가:

```java
// SYSTEM_PROMPT 의 마지막 원칙 뒤에 다음 텍스트 append
"""

## 원칙 N. 출처 라벨 정확성 (첨부 trace)

- highlights / pitfalls 의 sourceField 는 정보가 발견된 청크 라벨의 source 값을 그대로 쓴다.
- source=ATTACHMENT 인 청크에서 가져온 정보:
  - attachmentRef.attachmentId = 청크 라벨의 attachment-id 그대로
  - attachmentRef.pageStart / pageEnd = 청크 라벨의 pages= 범위 그대로
  - 청크 라벨에 pages 가 없으면 pageStart / pageEnd = null (HWP 등)
  - 라벨에 없는 페이지를 추측해서 박지 말 것
- sourceField != ATTACHMENT 일 때 attachmentRef = null
- 여러 청크에 걸친 정보면 가장 핵심 정보가 있는 청크 1개를 선택해 그 라벨 메타를 박는다

### 예시
입력: `[chunk-1 source=ATTACHMENT attachment-id=12 pages=35-35]\n배우자 명의 자가 주택이 있는 경우도 본 사업의 중복 수혜 제한 대상에 포함된다.`
출력 일부: `{"pitfalls": [{"text": "배우자 명의 자가 주택이 있어도 신청 제외", "sourceField": "ATTACHMENT", "attachmentRef": {"attachmentId": 12, "pageStart": 35, "pageEnd": 35}}]}`
"""
```

> 정확한 변경: `OpenAiChatClient.java` 안에서 `SYSTEM_PROMPT` 가 multi-line text block 으로 정의되어 있다고 가정. 끝의 `"""` 직전에 위 텍스트 삽입.

- [ ] **Step 2: buildResponseFormat() 에 attachmentRef + ATTACHMENT enum 추가**

기존 `buildResponseFormat()` (line:291-349 부근) 에서 `highlights` 와 `pitfalls` items schema 를 다음으로 교체:

```java
// highlights items schema
Map<String, Object> highlightItem = Map.of(
    "type", "object",
    "properties", Map.of(
        "text", Map.of("type", "string"),
        "sourceField", Map.of("enum", List.of(
            "SUPPORT_TARGET", "SELECTION_CRITERIA", "SUPPORT_CONTENT", "BODY", "ATTACHMENT")),
        "attachmentRef", Map.of(
            "type", List.of("object", "null"),
            "properties", Map.of(
                "attachmentId", Map.of("type", "integer"),
                "pageStart", Map.of("type", List.of("integer", "null")),
                "pageEnd",   Map.of("type", List.of("integer", "null"))
            ),
            "required", List.of("attachmentId", "pageStart", "pageEnd"),
            "additionalProperties", false
        )
    ),
    "required", List.of("text", "sourceField", "attachmentRef"),
    "additionalProperties", false
);

// pitfalls 도 동일 구조
Map<String, Object> pitfallItem = highlightItem;  // 동일

// schema 안의 highlights, pitfalls 항목에 위 schema 매핑
```

> **참고**: 정확한 schema 빌더 코드는 기존 `buildResponseFormat()` 의 Map 구조를 따라간다. enum 값에 `ATTACHMENT` 추가 + 각 item 의 properties 에 attachmentRef object 추가.

- [ ] **Step 3: 응답 JSON → GuideContent 매핑 검증 (parseResponse)**

`OpenAiChatClient.parseResponse()` (line:218-232 부근) 가 Jackson 등으로 GuideContent 역직렬화한다고 가정. record 가 attachmentRef 필드 추가만 했으므로 자동 매핑.

다만 attachmentRef 가 null 인 경우 record 생성 호환 여부 확인 — Jackson 은 record 의 nullable 필드를 잘 처리. 추가 작업 없음.

- [ ] **Step 4: schema 단위 테스트 (가능하면)**

```java
@Test
void buildResponseFormat_includesAttachmentEnum() {
    OpenAiChatClient client = new OpenAiChatClient(...);
    Map<String, Object> format = invokePrivate(client, "buildResponseFormat");
    String json = new ObjectMapper().writeValueAsString(format);
    assertThat(json).contains("ATTACHMENT");
    assertThat(json).contains("attachmentRef");
}
```

- [ ] **Step 5: 빌드 검증**

Run: `cd backend && ./gradlew test --tests com.youthfit.guide.infrastructure.external.OpenAiChatClientTest`
Expected: 기존 테스트 PASS, 신규 schema 테스트 PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java
git commit -m "$(cat <<'EOF'
feat(guide): LLM 시스템 프롬프트 + structured output 에 attachmentRef 도입

원칙 N (출처 라벨 정확성) 추가 — 청크 라벨의 attachment-id/pages 를
그대로 인용하도록 강제, 추측 페이지 금지. few-shot 1개 추가.
buildResponseFormat() 에 ATTACHMENT enum + attachmentRef 객체 schema 추가.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.5: GuideValidator 검증 5 추가 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java`

- [ ] **Step 1: 검증 5 테스트 케이스 추가**

```java
@Test
void givenAttachmentSourceFieldWithNullRef_whenValidate_thenInvalid() {
    GuideContent content = guideContentWith(
            List.of(new GuideHighlight("text", GuideSourceField.ATTACHMENT, null)),
            List.of());

    var report = validator.validate(content, Set.of(12L));
    assertThat(report.hasInvalidAttachmentRef()).isTrue();
    assertThat(report.hasRetryTrigger()).isTrue();
}

@Test
void givenAttachmentRefToUnknownId_whenValidate_thenInvalid() {
    GuideContent content = guideContentWith(
            List.of(new GuideHighlight("text", GuideSourceField.ATTACHMENT,
                    new AttachmentRef(99L, 1, 1))),
            List.of());

    var report = validator.validate(content, Set.of(12L, 13L));
    assertThat(report.hasInvalidAttachmentRef()).isTrue();
}

@Test
void givenNonAttachmentWithRef_whenValidate_thenInvalid() {
    GuideContent content = guideContentWith(
            List.of(new GuideHighlight("text", GuideSourceField.SUPPORT_TARGET,
                    new AttachmentRef(12L, 1, 1))),
            List.of());

    var report = validator.validate(content, Set.of(12L));
    assertThat(report.hasInvalidAttachmentRef()).isTrue();
}

@Test
void givenValidAttachmentRef_whenValidate_thenOk() {
    GuideContent content = guideContentWith(
            List.of(
                new GuideHighlight("hi-1", GuideSourceField.SUPPORT_TARGET, null),
                new GuideHighlight("hi-2", GuideSourceField.ATTACHMENT,
                        new AttachmentRef(12L, 35, 37)),
                new GuideHighlight("hi-3", GuideSourceField.BODY, null)
            ),
            List.of());

    var report = validator.validate(content, Set.of(12L));
    assertThat(report.hasInvalidAttachmentRef()).isFalse();
}

private GuideContent guideContentWith(List<GuideHighlight> hi, List<GuidePitfall> pi) {
    return new GuideContent(
            "summary", hi,
            new GuidePairedSection(List.of()),
            new GuidePairedSection(List.of()),
            new GuidePairedSection(List.of()),
            pi);
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 또는 FAIL**

Run: `cd backend && ./gradlew test --tests com.youthfit.guide.application.service.GuideValidatorTest`
Expected: 컴파일 실패 (`hasInvalidAttachmentRef` 없음, validate 시그니처 변경).

- [ ] **Step 3: ValidationReport 에 새 필드 추가**

기존 `ValidationReport` (record):

```java
public record ValidationReport(
        boolean hasGroupMixViolation,
        boolean hasInsufficientHighlights,
        boolean hasInvalidAttachmentRef,    // ← NEW
        java.util.List<String> feedbackMessages) {

    public boolean hasRetryTrigger() {
        return hasGroupMixViolation || hasInsufficientHighlights || hasInvalidAttachmentRef;
    }
}
```

- [ ] **Step 4: GuideValidator.validate() 시그니처 + 검증 로직 변경**

```java
public ValidationReport validate(GuideContent content, java.util.Set<Long> validAttachmentIds) {
    boolean groupMix = checkGroupMix(content);
    boolean insufficientHighlights = content.highlights().size() < 3;
    boolean invalidAttachmentRef = checkInvalidAttachmentRef(content, validAttachmentIds);

    java.util.List<String> feedback = new java.util.ArrayList<>();
    if (groupMix) feedback.add("...");                    // 기존 메시지
    if (insufficientHighlights) feedback.add("...");
    if (invalidAttachmentRef) {
        feedback.add(
            "highlights/pitfalls 항목의 sourceField 가 ATTACHMENT 인 경우 attachmentRef 가 정확해야 합니다. "
          + "정책의 첨부 ID 목록에 없는 attachmentId 를 박거나, sourceField 가 ATTACHMENT 가 아닌데 "
          + "attachmentRef 를 박지 마세요.");
    }
    return new ValidationReport(groupMix, insufficientHighlights, invalidAttachmentRef, feedback);
}

private boolean checkInvalidAttachmentRef(GuideContent content, java.util.Set<Long> validIds) {
    for (GuideHighlight h : content.highlights()) {
        if (isInvalidRef(h.sourceField(), h.attachmentRef(), validIds)) return true;
    }
    for (GuidePitfall p : content.pitfalls()) {
        if (isInvalidRef(p.sourceField(), p.attachmentRef(), validIds)) return true;
    }
    return false;
}

private boolean isInvalidRef(GuideSourceField sf, AttachmentRef ref, java.util.Set<Long> validIds) {
    if (sf == GuideSourceField.ATTACHMENT) {
        if (ref == null) return true;
        if (!validIds.contains(ref.attachmentId())) return true;
        // pageStart/pageEnd 의 양립성은 record 생성자가 이미 보장 (생성 시 throw)
        // 다만 retrieved JSON 에서 record 변환 후 통과하므로 여기 도달했다는 건 양립성 OK
        return false;
    } else {
        return ref != null;   // 4종 enum 인데 ref 있으면 invalid
    }
}
```

- [ ] **Step 5: GuideGenerationService 호출처 변경**

```java
// GuideGenerationService.java 의 validator 호출 부분
java.util.Set<Long> validAttachmentIds = policy.getAttachments().stream()
        .map(com.youthfit.policy.domain.model.PolicyAttachment::getId)
        .collect(java.util.stream.Collectors.toSet());

ValidationReport firstReport = guideValidator.validate(firstResponse, validAttachmentIds);
// 후속 retry 도 동일 시그니처 사용
```

- [ ] **Step 6: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.guide.application.service.GuideValidatorTest`
Expected: PASS (4 신규 + 기존 테스트).

- [ ] **Step 7: 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: 전체 PASS. validate(content) 1-arg 호출처가 있으면 컴파일 에러 → 모두 validate(content, validAttachmentIds) 로 교체.

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java
git commit -m "$(cat <<'EOF'
feat(guide): GuideValidator 검증 5 (sourceField=ATTACHMENT 유효성) 추가

- ATTACHMENT 인데 attachmentRef=null → invalid
- attachmentId 가 정책 첨부 ID 목록에 없음 → invalid
- non-ATTACHMENT 인데 attachmentRef 박혀있음 → invalid
- 위반 시 retry 트리거 (기존 검증과 동일 패턴)

GuideValidator.validate() 가 정책의 validAttachmentIds 인자를 받아
출처 무결성 검증.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.6: PROMPT_VERSION v3 → v4 + Phase 3 빌드 검증

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`

- [ ] **Step 1: PROMPT_VERSION 증분**

기존:
```java
static final String PROMPT_VERSION = "v3";
```

변경:
```java
static final String PROMPT_VERSION = "v4";  // 첨부 trace (attachmentRef) 도입으로 증분
```

- [ ] **Step 2: 빌드 + 전체 테스트**

```bash
cd backend && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 통합 검증 (수동)**

```bash
docker compose up -d --build backend
sleep 30

# 정책 7번 가이드 재생성 (sourceHash 가 prompt v4 포함이라 자동 무효화)
curl -X POST http://localhost:8080/api/internal/guides/generate \
  -H "X-Internal-Api-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"policyId": 7, "policyTitle": "x", "documentContent": "x"}'

# 결과 조회
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "SELECT policy_id, source_hash, jsonb_pretty(content::jsonb) FROM guide WHERE policy_id=7;"
```

Expected: 가이드 JSON 의 highlights / pitfalls 에서 일부 항목이 `sourceField=ATTACHMENT` + `attachmentRef={attachmentId, pageStart, pageEnd}` 박혀있음.

만약 모든 항목이 `sourceField=BODY` 또는 4종이면 LLM 이 청크 라벨을 인용 안 한 것 → 시스템 프롬프트 보강, 또는 첨부 청크가 충분한 디테일을 보유하는지 확인 (정책 7번의 첨부 콘텐츠가 짧으면 LLM 이 ATTACHMENT 항목을 만들 동기 없음).

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java
git commit -m "$(cat <<'EOF'
chore(guide): PROMPT_VERSION v3 → v4 (첨부 trace 도입)

sourceHash 자동 무효화로 CostGuard allowlist 정책 (7·30) 의 가이드가
다음 호출 시 자동 재생성되어 새 schema (attachmentRef) 채움.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

PR 3 권장:

```
[BE] feat: 가이드 첨부 출처 trace (Phase 3/4)
```

---

## Phase 4: 백엔드 redirect endpoint + 프론트 ATTACHMENT 라벨 (PR4 / 4h)

### Task 4.1: AttachmentStorage.presign + S3 override — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentStorage.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorage.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorageTest.java`

- [ ] **Step 1: AttachmentStorage 인터페이스에 presign default 추가**

```java
package com.youthfit.ingestion.application.port;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface AttachmentStorage {
    StorageReference put(InputStream content, String key, String mediaType);

    InputStream get(String key);

    boolean exists(String key);

    /**
     * 외부 노출 가능한 presigned URL 발급. S3 등 cloud storage 만 override.
     * Local 등 default 는 Optional.empty() — controller 가 stream 응답으로 fallback.
     */
    default Optional<String> presign(String key, Duration ttl) {
        return Optional.empty();
    }
}
```

- [ ] **Step 2: S3AttachmentStorage presign 테스트**

```java
package com.youthfit.ingestion.infrastructure.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class S3AttachmentStorageTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "S3_ACCESS_KEY_ID", matches = ".+")
    void givenStorageKey_whenPresign_thenReturnsHttpsUrl() {
        S3AttachmentStorage storage = createForTest();
        Optional<String> url = storage.presign("attachments/test.pdf", Duration.ofMinutes(5));
        assertThat(url).isPresent();
        assertThat(url.get()).startsWith("https://");
        assertThat(url.get()).contains("X-Amz-Signature=");
    }

    private S3AttachmentStorage createForTest() {
        // 실제 AWS 연결을 요구하므로 환경변수 있을 때만 활성. 단위 테스트 보다는 통합 테스트.
        return new S3AttachmentStorage(/* ... 실제 빈 주입 ... */);
    }
}
```

> 환경변수 부재 시 `@EnabledIfEnvironmentVariable` 으로 skip. 단위 테스트로는 어려운 영역이라 통합 검증은 운영 환경에서.

- [ ] **Step 3: S3AttachmentStorage 에 presign 구현**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.StorageReference;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "attachment.storage.type", havingValue = "s3")
@RequiredArgsConstructor
public class S3AttachmentStorage implements AttachmentStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    // ... 기존 put/get/exists 구현 그대로 ...

    @Override
    public Optional<String> presign(String key, Duration ttl) {
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getReq)
                    .build();
            URL url = presigner.presignGetObject(presignReq).url();
            return Optional.of(url.toString());
        } catch (Exception e) {
            // presign 실패 시 controller 가 stream fallback
            return Optional.empty();
        }
    }
}
```

> **참고**: 기존 `S3AttachmentStorage` 의 정확한 빈 주입 시그니처 (S3Client / S3Presigner / bucket / region 등) 는 PR #46 에서 도입. 본 plan 의 코드는 일반 패턴으로, 실제 빈 구조 맞춰 보정.

- [ ] **Step 4: 빌드**

Run: `cd backend && ./gradlew compileJava`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentStorage.java \
        backend/src/main/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorage.java \
        backend/src/test/java/com/youthfit/ingestion/infrastructure/external/S3AttachmentStorageTest.java
git commit -m "$(cat <<'EOF'
feat(ingestion): AttachmentStorage.presign 추가 (S3 override, Local default empty)

Local 등 default 는 Optional.empty() 반환 → controller 가 stream 응답으로
fallback. S3AttachmentStorage 는 S3Presigner 로 presigned URL 발급.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.2: PolicyAttachment 응답 DTO 에 id 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyAttachmentResponse.java`
- Modify: 응답 매핑 위치 (PolicyController 또는 Mapper 클래스)

- [ ] **Step 1: PolicyAttachmentResponse record 에 id 추가**

```java
package com.youthfit.policy.presentation.dto.response;

public record PolicyAttachmentResponse(
        Long id,             // ← NEW
        String name,
        String url,
        String mediaType
) {
    public static PolicyAttachmentResponse from(
            com.youthfit.policy.domain.model.PolicyAttachment a) {
        return new PolicyAttachmentResponse(
                a.getId(), a.getName(), a.getUrl(), a.getMediaType());
    }
}
```

> **참고**: 정확한 DTO 클래스명 / 매핑 위치는 기존 코드 그대로. `from()` factory 패턴이 다르면 기존 패턴 따름.

- [ ] **Step 2: 빌드**

Run: `cd backend && ./gradlew build`
Expected: PASS.

- [ ] **Step 3: API 응답 직접 검증**

```bash
curl -s http://localhost:8080/api/policies/7 | jq '.data.attachments'
```

Expected: 각 attachment 객체에 `id` 필드 포함.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/presentation/dto/response/PolicyAttachmentResponse.java
git commit -m "$(cat <<'EOF'
feat(policy): PolicyAttachmentResponse 에 id 필드 추가

가이드 highlights/pitfalls 의 attachmentRef.attachmentId 를 프론트에서
정책 첨부와 매칭하기 위해 id 노출.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.3: AttachmentRedirectResult sealed + RedirectAttachmentService — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/application/dto/result/AttachmentRedirectResult.java`
- Create: `backend/src/main/java/com/youthfit/policy/application/service/RedirectAttachmentService.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/exception/AttachmentNotFoundException.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/service/RedirectAttachmentServiceTest.java`

- [ ] **Step 1: AttachmentRedirectResult sealed interface**

```java
package com.youthfit.policy.application.dto.result;

import java.io.InputStream;

public sealed interface AttachmentRedirectResult {
    /** S3 등 presigned URL 로 302 redirect. */
    record PresignRedirect(String url) implements AttachmentRedirectResult {}

    /** 외부 원본 URL 로 302 redirect (S3 캐시 없거나 presign 실패). */
    record ExternalRedirect(String url) implements AttachmentRedirectResult {}

    /** Local 등 storage 가 직접 stream 으로 응답. */
    record StreamResponse(
            InputStream stream,
            String mediaType,
            String filename) implements AttachmentRedirectResult {}
}
```

- [ ] **Step 2: AttachmentNotFoundException**

```java
package com.youthfit.policy.domain.exception;

public class AttachmentNotFoundException extends RuntimeException {
    private final Long attachmentId;

    public AttachmentNotFoundException(Long attachmentId) {
        super("PolicyAttachment not found: " + attachmentId);
        this.attachmentId = attachmentId;
    }

    public Long getAttachmentId() { return attachmentId; }
}
```

> 전역 예외 핸들러에서 404 매핑. 기존 핸들러 위치 (예: `common/presentation/exception/GlobalExceptionHandler.java`) 에 매핑 추가:
>
> ```java
> @ExceptionHandler(AttachmentNotFoundException.class)
> public ResponseEntity<ApiResponse<Void>> handle(AttachmentNotFoundException e) {
>     return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(...));
> }
> ```

- [ ] **Step 3: RedirectAttachmentService 분기 테스트**

```java
package com.youthfit.policy.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedirectAttachmentServiceTest {

    private final PolicyAttachmentRepository repo = Mockito.mock(PolicyAttachmentRepository.class);
    private final AttachmentStorage storage = Mockito.mock(AttachmentStorage.class);
    private final RedirectAttachmentService service = new RedirectAttachmentService(repo, storage);

    @Test
    void givenStorageKeyAndPresignAvailable_whenResolve_thenPresignRedirect() {
        PolicyAttachment att = mockAttachment(12L, "key-12", "https://orig.example/x.pdf",
                "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));
        Mockito.when(storage.presign(Mockito.eq("key-12"), Mockito.any(Duration.class)))
                .thenReturn(Optional.of("https://s3.aws/presigned"));

        var result = service.resolve(12L);

        assertThat(result).isInstanceOf(AttachmentRedirectResult.PresignRedirect.class);
        assertThat(((AttachmentRedirectResult.PresignRedirect) result).url())
                .isEqualTo("https://s3.aws/presigned");
    }

    @Test
    void givenStorageKeyButPresignEmpty_whenResolve_thenStreamResponse() {
        PolicyAttachment att = mockAttachment(12L, "key-12", "https://orig.example/x.pdf",
                "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));
        Mockito.when(storage.presign(Mockito.eq("key-12"), Mockito.any(Duration.class)))
                .thenReturn(Optional.empty());
        Mockito.when(storage.get("key-12"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        var result = service.resolve(12L);

        assertThat(result).isInstanceOf(AttachmentRedirectResult.StreamResponse.class);
        var stream = (AttachmentRedirectResult.StreamResponse) result;
        assertThat(stream.mediaType()).isEqualTo("application/pdf");
        assertThat(stream.filename()).contains("x.pdf");
    }

    @Test
    void givenNoStorageKey_whenResolve_thenExternalRedirect() {
        PolicyAttachment att = mockAttachment(12L, null, "https://orig.example/x.pdf",
                "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));

        var result = service.resolve(12L);

        assertThat(result).isInstanceOf(AttachmentRedirectResult.ExternalRedirect.class);
        assertThat(((AttachmentRedirectResult.ExternalRedirect) result).url())
                .isEqualTo("https://orig.example/x.pdf");
    }

    @Test
    void givenNotFound_whenResolve_thenThrows() {
        Mockito.when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(99L))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void givenNoStorageKeyAndNoExternalUrl_whenResolve_thenThrows() {
        PolicyAttachment att = mockAttachment(12L, null, null, "application/pdf");
        Mockito.when(repo.findById(12L)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.resolve(12L))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    private PolicyAttachment mockAttachment(Long id, String storageKey, String url, String mediaType) {
        PolicyAttachment a = Mockito.mock(PolicyAttachment.class);
        Mockito.when(a.getId()).thenReturn(id);
        Mockito.when(a.getStorageKey()).thenReturn(storageKey);
        Mockito.when(a.getUrl()).thenReturn(url);
        Mockito.when(a.getMediaType()).thenReturn(mediaType);
        Mockito.when(a.getName()).thenReturn(url == null ? "unknown" : url.substring(url.lastIndexOf('/') + 1));
        return a;
    }
}
```

- [ ] **Step 4: 테스트 실행 → 컴파일 실패**

Run: `cd backend && ./gradlew test --tests com.youthfit.policy.application.service.RedirectAttachmentServiceTest`
Expected: 컴파일 실패.

- [ ] **Step 5: RedirectAttachmentService 구현**

```java
package com.youthfit.policy.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 첨부 redirect 분기 비즈니스 로직.
 * S3 presign 가능 → PresignRedirect (302)
 * Local / presign 실패 → StreamResponse (200 stream)
 * storageKey 없음 + 외부 URL 있음 → ExternalRedirect (302)
 * 셋 다 없음 → AttachmentNotFoundException (404)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectAttachmentService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final PolicyAttachmentRepository repository;
    private final AttachmentStorage storage;

    public AttachmentRedirectResult resolve(Long attachmentId) {
        PolicyAttachment attachment = repository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        // 1. storageKey 가 있으면 presign 시도 → 실패 시 stream
        if (attachment.getStorageKey() != null) {
            Optional<String> presigned = storage.presign(attachment.getStorageKey(), PRESIGN_TTL);
            if (presigned.isPresent()) {
                log.info("attachment-redirect presign id={} key={}",
                        attachmentId, attachment.getStorageKey());
                return new AttachmentRedirectResult.PresignRedirect(presigned.get());
            }
            // Local 등은 stream
            log.info("attachment-redirect stream id={} key={}",
                    attachmentId, attachment.getStorageKey());
            return new AttachmentRedirectResult.StreamResponse(
                    storage.get(attachment.getStorageKey()),
                    attachment.getMediaType() == null ? "application/octet-stream" : attachment.getMediaType(),
                    attachment.getName());
        }

        // 2. storageKey 없음 → 외부 URL fallback
        if (attachment.getUrl() != null && !attachment.getUrl().isBlank()) {
            log.info("attachment-redirect external id={} url={}",
                    attachmentId, attachment.getUrl());
            return new AttachmentRedirectResult.ExternalRedirect(attachment.getUrl());
        }

        // 3. 셋 다 없음
        throw new AttachmentNotFoundException(attachmentId);
    }
}
```

- [ ] **Step 6: 테스트 재실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.policy.application.service.RedirectAttachmentServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/result/AttachmentRedirectResult.java \
        backend/src/main/java/com/youthfit/policy/application/service/RedirectAttachmentService.java \
        backend/src/main/java/com/youthfit/policy/domain/exception/AttachmentNotFoundException.java \
        backend/src/test/java/com/youthfit/policy/application/service/RedirectAttachmentServiceTest.java
git commit -m "$(cat <<'EOF'
feat(policy): RedirectAttachmentService 분기 (presign / stream / external)

- S3 presign 가능 → PresignRedirect
- Local / presign 실패 → StreamResponse (백엔드가 직접 stream)
- storageKey 없음 + 외부 URL 있음 → ExternalRedirect
- 셋 다 없음 → AttachmentNotFoundException (404)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.4: PolicyAttachmentApi + Controller — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyAttachmentApi.java`
- Create: `backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyAttachmentController.java`
- Test: `backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyAttachmentControllerTest.java` (MockMvc 슬라이스)

- [ ] **Step 1: PolicyAttachmentApi 인터페이스 (Swagger)**

```java
package com.youthfit.policy.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Policy Attachment", description = "정책 첨부 파일 redirect")
public interface PolicyAttachmentApi {

    @Operation(summary = "첨부 파일 redirect / stream",
            description = "S3 캐시가 있으면 presigned URL 로 302, 없으면 외부 원본 URL 로 302, "
                       + "Local storage 면 직접 stream. fragment(#page=N) 는 클라이언트 side 에서 자동 보존.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "presigned 또는 외부 URL 로 redirect"),
            @ApiResponse(responseCode = "200", description = "Local storage stream 응답"),
            @ApiResponse(responseCode = "404", description = "첨부 없음 또는 storage/외부 URL 모두 부재")
    })
    ResponseEntity<?> redirectFile(
            @Parameter(description = "첨부 ID") @PathVariable Long attachmentId);
}
```

- [ ] **Step 2: PolicyAttachmentController 슬라이스 테스트**

```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.application.service.RedirectAttachmentService;
import com.youthfit.policy.domain.exception.AttachmentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PolicyAttachmentController.class)
class PolicyAttachmentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RedirectAttachmentService service;

    @Test
    void givenPresignResult_whenGet_then302WithLocation() throws Exception {
        when(service.resolve(12L))
                .thenReturn(new AttachmentRedirectResult.PresignRedirect("https://s3.aws/presigned"));

        mockMvc.perform(get("/api/policies/attachments/12/file"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://s3.aws/presigned"));
    }

    @Test
    void givenStreamResult_whenGet_then200WithBody() throws Exception {
        when(service.resolve(12L))
                .thenReturn(new AttachmentRedirectResult.StreamResponse(
                        new ByteArrayInputStream("hello".getBytes()),
                        "application/pdf",
                        "x.pdf"));

        mockMvc.perform(get("/api/policies/attachments/12/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("hello".getBytes()));
    }

    @Test
    void givenExternalResult_whenGet_then302External() throws Exception {
        when(service.resolve(12L))
                .thenReturn(new AttachmentRedirectResult.ExternalRedirect("https://orig/x.pdf"));

        mockMvc.perform(get("/api/policies/attachments/12/file"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://orig/x.pdf"));
    }

    @Test
    void givenNotFound_whenGet_then404() throws Exception {
        when(service.resolve(99L)).thenThrow(new AttachmentNotFoundException(99L));

        mockMvc.perform(get("/api/policies/attachments/99/file"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: PolicyAttachmentController 구현**

```java
package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.application.service.RedirectAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/policies/attachments")
@RequiredArgsConstructor
public class PolicyAttachmentController implements PolicyAttachmentApi {

    private final RedirectAttachmentService redirectAttachmentService;

    @Override
    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<?> redirectFile(@PathVariable Long attachmentId) {
        AttachmentRedirectResult result = redirectAttachmentService.resolve(attachmentId);

        return switch (result) {
            case AttachmentRedirectResult.PresignRedirect r ->
                    ResponseEntity.status(302).location(URI.create(r.url())).build();
            case AttachmentRedirectResult.ExternalRedirect r ->
                    ResponseEntity.status(302).location(URI.create(r.url())).build();
            case AttachmentRedirectResult.StreamResponse r -> {
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.filename() + "\"");
                yield ResponseEntity.ok()
                        .headers(headers)
                        .contentType(MediaType.parseMediaType(r.mediaType()))
                        .body(new InputStreamResource(r.stream()));
            }
        };
    }
}
```

- [ ] **Step 4: 테스트 실행 → PASS**

Run: `cd backend && ./gradlew test --tests com.youthfit.policy.presentation.controller.PolicyAttachmentControllerTest`
Expected: PASS (4 tests).

- [ ] **Step 5: 통합 검증 (수동)**

```bash
docker compose up -d --build backend
sleep 15

# 정책 7번의 첫 첨부 ID 조회 (locally)
ATT_ID=$(docker compose exec -T postgres psql -U youthfit -d youthfit -tAc \
  "SELECT id FROM policy_attachment WHERE policy_id=7 LIMIT 1;")
echo "Attachment ID: $ATT_ID"

# Endpoint 호출 (-L 으로 redirect 따라감)
curl -v -L "http://localhost:8080/api/policies/attachments/$ATT_ID/file#page=2" -o /tmp/x.pdf
file /tmp/x.pdf
```

Expected: PDF 다운로드 성공. local profile 이라 stream 응답 (200 OK + Content-Type: application/pdf).

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyAttachmentApi.java \
        backend/src/main/java/com/youthfit/policy/presentation/controller/PolicyAttachmentController.java \
        backend/src/test/java/com/youthfit/policy/presentation/controller/PolicyAttachmentControllerTest.java
git commit -m "$(cat <<'EOF'
feat(policy): GET /api/policies/attachments/{id}/file endpoint 추가

PresignRedirect / StreamResponse / ExternalRedirect 분기. fragment
(#page=N) 는 클라이언트가 final URL 에 자동 보존 (RFC 7231).
Swagger 인터페이스 PolicyAttachmentApi 분리.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.5: 프론트 타입 갱신 + AttachmentRef → AttachmentSummary rename

**Files:**
- Modify: `frontend/src/types/policy.ts`
- Modify: `frontend/src/components/policy/SourceLinkedListCard.tsx` (내부 타입 rename)

- [ ] **Step 1: types/policy.ts 갱신**

기존 파일 (line:36-117 부근) 에 다음 변경:

```typescript
// PolicyAttachment 에 id 필드 추가
export interface PolicyAttachment {
  id: number;            // ← NEW
  name: string;
  url: string;
  mediaType: string | null;
}

// GuideSourceField 에 ATTACHMENT 추가
export type GuideSourceField =
  | 'SUPPORT_TARGET'
  | 'SELECTION_CRITERIA'
  | 'SUPPORT_CONTENT'
  | 'BODY'
  | 'ATTACHMENT';        // ← NEW

// AttachmentRef 신규 (가이드 항목의 첨부 출처 trace)
export interface AttachmentRef {
  attachmentId: number;
  pageStart: number | null;
  pageEnd: number | null;
}

// GuideHighlight, GuidePitfall 에 attachmentRef 옵셔널
export interface GuideHighlight {
  text: string;
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}

export interface GuidePitfall {
  text: string;
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}
```

- [ ] **Step 2: SourceLinkedListCard 의 내부 AttachmentRef → AttachmentSummary rename**

`frontend/src/components/policy/SourceLinkedListCard.tsx:36-40` 의 기존 타입을:

```typescript
// 변경 전
export interface AttachmentRef {
  id?: number;
  name: string;
  url: string;
}

// 변경 후
export interface AttachmentSummary {
  id?: number;
  name: string;
  url: string;
}
```

해당 파일 안의 `attachments: AttachmentRef[]` → `attachments: AttachmentSummary[]` 도 동시 변경.

- [ ] **Step 3: 다른 파일에서 SourceLinkedListCard 의 AttachmentRef import 사용 위치 grep + 갱신**

```bash
cd frontend && grep -rn "AttachmentRef" src/ | grep -v node_modules | grep -v dist
```

Expected: SourceLinkedListCard 내부 사용 외에 외부 import 가 있을 수 있음 (예: PolicyDetailPage, useGuide). 모두 `AttachmentSummary` 로 일괄 교체.

```bash
# macOS sed
LC_ALL=C find frontend/src -name "*.tsx" -o -name "*.ts" \
  | xargs sed -i '' 's/import type { \(.*\)AttachmentRef\(.*\) } from .@\/components\/policy\/SourceLinkedListCard./import type { \1AttachmentSummary\2 } from "@\/components\/policy\/SourceLinkedListCard"/g'
```

수동 점검 후 `AttachmentRef` 가 types/policy.ts 의 신규 타입을 가리키는지, SourceLinkedListCard 의 옛 타입 (이제 AttachmentSummary) 을 가리키는지 케이스별 분리. 다음 grep 으로 마지막 점검:

```bash
grep -rn "from '@/components/policy/SourceLinkedListCard'" frontend/src/
grep -rn "AttachmentRef" frontend/src/
```

- [ ] **Step 4: 빌드 + 타입체크**

```bash
cd frontend && npm run build
```

Expected: 빌드 성공. 타입 에러 0.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/types/policy.ts \
        frontend/src/components/policy/SourceLinkedListCard.tsx \
        frontend/src/  # 자동 갱신된 다른 파일들
git commit -m "$(cat <<'EOF'
feat(fe): AttachmentRef 도입 + 기존 컴포넌트 내부 타입 AttachmentSummary 로 rename

types/policy.ts 에 가이드 항목 출처 trace 용 AttachmentRef 추가
({ attachmentId, pageStart, pageEnd }). 기존 SourceLinkedListCard 내부의
{ id?, name, url } 형태 타입은 의미가 다르므로 AttachmentSummary 로 rename
해 충돌 회피. PolicyAttachment 에 id 필드 추가 (백엔드 응답에 포함됨).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.6: AttachmentSourceLink 컴포넌트 — TDD

**Files:**
- Create: `frontend/src/components/policy/AttachmentSourceLink.tsx`
- Create: `frontend/src/components/policy/__tests__/AttachmentSourceLink.test.tsx`

- [ ] **Step 1: 라벨 / 클릭 핸들러 검증 테스트**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { AttachmentSourceLink } from '../AttachmentSourceLink';
import type { AttachmentRef, PolicyAttachment } from '@/types/policy';

describe('AttachmentSourceLink', () => {
  const attachments: PolicyAttachment[] = [
    { id: 12, name: '시행규칙.pdf', url: 'https://orig/12.pdf', mediaType: 'application/pdf' },
    { id: 13, name: '안내문.hwp', url: 'https://orig/13.hwp', mediaType: 'application/x-hwp' },
  ];

  it('renders single page label', () => {
    const ref: AttachmentRef = { attachmentId: 12, pageStart: 35, pageEnd: 35 };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(screen.getByText(/시행규칙\.pdf/)).toBeInTheDocument();
    expect(screen.getByText(/35페이지/)).toBeInTheDocument();
  });

  it('renders page range label', () => {
    const ref: AttachmentRef = { attachmentId: 12, pageStart: 35, pageEnd: 37 };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(screen.getByText(/35-37페이지/)).toBeInTheDocument();
  });

  it('renders without page when pageStart is null (HWP)', () => {
    const ref: AttachmentRef = { attachmentId: 13, pageStart: null, pageEnd: null };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(screen.getByText(/안내문\.hwp/)).toBeInTheDocument();
    expect(screen.queryByText(/페이지/)).not.toBeInTheDocument();
  });

  it('returns null when attachment id not found', () => {
    const ref: AttachmentRef = { attachmentId: 999, pageStart: 1, pageEnd: 1 };
    const { container } = render(
      <AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);
    expect(container.firstChild).toBeNull();
  });

  it('opens new tab with #page=N on click', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const ref: AttachmentRef = { attachmentId: 12, pageStart: 35, pageEnd: 37 };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);

    await userEvent.click(screen.getByRole('button'));

    expect(openSpy).toHaveBeenCalledWith(
      '/api/policies/attachments/12/file#page=35',
      '_blank',
      'noopener,noreferrer');
    openSpy.mockRestore();
  });

  it('opens new tab without fragment when pageStart is null', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const ref: AttachmentRef = { attachmentId: 13, pageStart: null, pageEnd: null };
    render(<AttachmentSourceLink attachmentRef={ref} attachments={attachments} />);

    await userEvent.click(screen.getByRole('button'));

    expect(openSpy).toHaveBeenCalledWith(
      '/api/policies/attachments/13/file',
      '_blank',
      'noopener,noreferrer');
    openSpy.mockRestore();
  });
});
```

- [ ] **Step 2: 테스트 실행 → FAIL**

Run: `cd frontend && npm test -- AttachmentSourceLink`
Expected: FAIL — 컴포넌트 없음.

- [ ] **Step 3: AttachmentSourceLink 구현**

```tsx
// frontend/src/components/policy/AttachmentSourceLink.tsx
import type { AttachmentRef, PolicyAttachment } from '@/types/policy';

interface Props {
  attachmentRef: AttachmentRef;
  attachments: PolicyAttachment[];
  className?: string;
}

export function AttachmentSourceLink({ attachmentRef, attachments, className }: Props) {
  const target = attachments.find((a) => a.id === attachmentRef.attachmentId);
  if (!target) return null;

  const pageLabel =
    attachmentRef.pageStart === null
      ? ''
      : attachmentRef.pageStart === attachmentRef.pageEnd
        ? ` · ${attachmentRef.pageStart}페이지`
        : ` · ${attachmentRef.pageStart}-${attachmentRef.pageEnd}페이지`;

  const href =
    `/api/policies/attachments/${attachmentRef.attachmentId}/file` +
    (attachmentRef.pageStart !== null ? `#page=${attachmentRef.pageStart}` : '');

  return (
    <button
      type="button"
      onClick={() => window.open(href, '_blank', 'noopener,noreferrer')}
      className={
        className ??
        'inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ' +
          'border-indigo-300 text-indigo-800 hover:bg-indigo-100'
      }
    >
      📎 첨부: {target.name}{pageLabel} ↗
    </button>
  );
}
```

- [ ] **Step 4: 테스트 재실행 → PASS**

Run: `cd frontend && npm test -- AttachmentSourceLink`
Expected: PASS (6 tests).

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/AttachmentSourceLink.tsx \
        frontend/src/components/policy/__tests__/AttachmentSourceLink.test.tsx
git commit -m "$(cat <<'EOF'
feat(fe): AttachmentSourceLink 컴포넌트 추가

가이드 highlights/pitfalls 의 ATTACHMENT 항목을 클릭 시
window.open('/api/policies/attachments/{id}/file#page=N', '_blank') 로
새 탭에서 PDF 의 해당 페이지 deep link. HWP/페이지 메타 없는 첨부는
fragment 생략, 첫 페이지로 열림.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.7: SourceLinkedListCard sourceField=ATTACHMENT 분기

**Files:**
- Modify: `frontend/src/components/policy/SourceLinkedListCard.tsx`

- [ ] **Step 1: 항목 렌더에서 ATTACHMENT 분기 추가**

기존 `items.map` 부분 (line:88-100 부근) 을 다음으로 교체:

```tsx
import { AttachmentSourceLink } from './AttachmentSourceLink';
import type { PolicyAttachment } from '@/types/policy';

// Item 타입을 attachmentRef 포함하도록 보강
interface Item {
  text: string;
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;   // ← NEW
}

interface Props {
  title: string;
  emoji: string;
  tone: keyof typeof TONE_CLASSES;
  items: Item[];
  attachments: AttachmentSummary[];          // 헤더 첨부 트리거용 (기존)
  policyAttachments: PolicyAttachment[];     // ← NEW: 항목별 ATTACHMENT 라벨용 (id 필요)
}

export function SourceLinkedListCard({
  title, emoji, tone, items, attachments, policyAttachments,
}: Props) {
  if (!items.length) return null;
  const t = TONE_CLASSES[tone];

  // ... 기존 renderAttachmentTrigger 그대로 ...

  return (
    <section className={...}>
      <div className="mb-4 flex items-center justify-between">
        <h2 className={...}>...{title}</h2>
        {renderAttachmentTrigger()}
      </div>
      <ul className="space-y-3">
        {items.map((it, i) => (
          <li key={i} className="text-sm text-neutral-800">
            <p className="mb-1">• {it.text}</p>
            {it.sourceField === 'ATTACHMENT' && it.attachmentRef ? (
              <AttachmentSourceLink
                attachmentRef={it.attachmentRef}
                attachments={policyAttachments}
                className={`ml-3 inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
              />
            ) : (
              <button
                type="button"
                onClick={() => scrollAndHighlight(SCROLL_TARGETS[it.sourceField as Exclude<GuideSourceField, 'ATTACHMENT'>])}
                className={`ml-3 inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
              >
                {SOURCE_LABELS[it.sourceField as Exclude<GuideSourceField, 'ATTACHMENT'>]} ↗
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
```

> SOURCE_LABELS / SCROLL_TARGETS 는 4종 enum 만 다루므로 type assertion (`as Exclude<...>`) 으로 좁힘. 또는 두 매핑에 'ATTACHMENT' 키를 placeholder 로 추가하고 절대 사용 안 함.

- [ ] **Step 2: 호출처 (PolicyDetailPage 또는 Guide 렌더 컴포넌트) 갱신**

```bash
grep -rn "SourceLinkedListCard" frontend/src/
```

기존 호출에 `policyAttachments={policy.attachments}` prop 추가. policy.attachments 는 PolicyDetail 에서 이미 로드되어 있음.

- [ ] **Step 3: 빌드 + 테스트**

```bash
cd frontend && npm run build && npm test
```

Expected: 빌드 성공, 모든 테스트 PASS.

- [ ] **Step 4: 통합 검증 (수동)**

```bash
cd frontend && npm run dev
```

브라우저로 `http://localhost:5173/policies/7` 접속:
- highlights/pitfalls 카드에 ATTACHMENT sourceField 항목이 보이면 라벨 `📎 첨부: 시행규칙.pdf · 35-37페이지 ↗` 형태
- 클릭 시 새 탭으로 PDF 가 열리고 35페이지로 이동 (브라우저가 fragment 보존)

만약 가이드에 ATTACHMENT 항목이 없으면 LLM 이 첨부에서 정보 안 뽑은 것 → 백엔드 가이드 재생성 트리거 또는 프롬프트 보강.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/SourceLinkedListCard.tsx \
        frontend/src/  # PolicyDetailPage 등 호출처
git commit -m "$(cat <<'EOF'
feat(fe): SourceLinkedListCard 가 ATTACHMENT 항목을 새 탭 PDF 로 분기

sourceField === 'ATTACHMENT' 인 항목은 AttachmentSourceLink 로 렌더해
window.open #page=N deep link. 그 외 4종은 기존 scrollAndHighlight 라벨.
호출처에서 policyAttachments prop 추가로 첨부 ID ↔ 이름 매칭.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.8: Phase 4 통합 검증 + PR 생성

**Files:** (변경 없음)

- [ ] **Step 1: 백엔드 + 프론트 빌드**

```bash
cd backend && ./gradlew clean build
cd ../frontend && npm run build
```

Expected: 양쪽 BUILD SUCCESSFUL.

- [ ] **Step 2: e2e 검증 시나리오**

```bash
docker compose up -d --build backend
sleep 30

# 정책 7번 가이드 재생성 (PROMPT_VERSION v4 로 자동 무효화)
curl -X POST http://localhost:8080/api/internal/guides/generate \
  -H "X-Internal-Api-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"policyId": 7, "policyTitle": "x", "documentContent": "x"}' | jq .

# 가이드 응답에 ATTACHMENT 항목 존재 확인
curl -s http://localhost:8080/api/v1/guides/7 | jq '.data.highlights[] | select(.sourceField == "ATTACHMENT")'
```

Expected: 최소 1개 이상 ATTACHMENT 항목이 있고, attachmentRef.attachmentId 가 정책 7번 첨부 중 하나의 id, pageStart/pageEnd 가 채워짐.

- [ ] **Step 3: 프론트 e2e (수동)**

```bash
cd frontend && npm run dev
```

- 브라우저: `http://localhost:5173/policies/7`
- highlights / pitfalls 카드에서 ATTACHMENT 라벨 확인
- 라벨 클릭 → 새 탭에서 PDF 가 열리고 페이지 N 으로 이동

- [ ] **Step 4: PR 생성 (Phase 4)**

```bash
git push origin feat/phase-4-attachment-source-link
gh pr create --title "[FE/BE] feat: 첨부 단위 출처 trace UI 노출 (Phase 4/4)" --body "$(cat <<'EOF'
## Summary
- 백엔드 GET /api/policies/attachments/{id}/file (presign 302 / stream / external 분기)
- 프론트 AttachmentSourceLink 컴포넌트 + SourceLinkedListCard 의 ATTACHMENT 분기
- 새 탭 PDF + #page=N deep link 로 사용자 검증 동선

## Test plan
- [ ] AttachmentSourceLink unit test (6 cases) PASS
- [ ] PolicyAttachmentControllerTest (4 cases) PASS
- [ ] RedirectAttachmentServiceTest (5 cases) PASS
- [ ] e2e: 정책 7번 가이드 재생성 → ATTACHMENT 항목 노출 → 클릭 시 PDF 페이지 점프

## 후속
- iOS Safari 의 #page=N 동작 모니터링
- 사용자 abuse 발견 시 endpoint 인증 검토 (operations memo)
- 청크 단위 vs 페이지 단위 chunker (옵션 D) 는 v0.x 검토

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 자가 점검 (Plan Self-Review)

### Spec coverage

- §3 영향 모듈 → File Structure / Phase 1-4 모듈별 구성 ✓
- §4 도메인 모델 → Task 1.1 (PageText), 2.1 (PolicyDocument), 3.1 (AttachmentRef + GuideSourceField), 3.2 (Highlight/Pitfall) ✓
- §5 데이터 흐름 → Task 1.1 (Tika SAX), 1.2 (sentinel), 2.2 (mergedContent 마커), 2.3 (chunker boundary), 3.3 (LLM 청크 라벨), 3.4 (LLM schema), 3.5 (검증), 4.6 (프론트 렌더) ✓
- §6 LLM 입력/응답 + 검증 5 → Task 3.4, 3.5 ✓
- §7 백엔드 API → Task 4.3, 4.4 ✓
- §8 프론트엔드 → Task 4.5, 4.6, 4.7 ✓
- §9 마이그레이션 → Task 2.1 (ddl-auto=update 자동, prod SQL Step 6), Task 3.6 (PROMPT_VERSION v4) ✓
- §10 비범위 → Phase 1-4 안에서 자연 처리 (HWP page=null fallback, 페이지 단위 chunker 미적용 명시) ✓
- §11 위험 / 모니터링 → Task 1.3 (Phase 0 sample 검증), Task 4.8 (e2e 검증) ✓
- §12 권장 PR 분할 → Phase 1-4 각 마지막 step 의 PR 생성 명령 ✓
- §13 결정 로그 → spec 자체에 있음 ✓
- §14 미결 / 후속 → Phase 4 PR description 의 "후속" ✓

### Placeholder scan

- TBD/TODO 잔존 없음 ✓
- "implement later" / "fill in details" 잔존 없음 ✓
- "Add appropriate error handling" 같은 모호한 지시 없음 — 구체적 예외 (`AttachmentNotFoundException`) 정의 ✓
- 일부 step (예: Task 2.3 의 chunker 구현) 에 "단순화 노트" 가 있으나 단순화 자체가 의도이며, 단락/문장 분할 보강은 후속 step 명시 ✓

### Type consistency

- `AttachmentRef` (백엔드 record) 시그니처 = `(Long attachmentId, Integer pageStart, Integer pageEnd)` — Task 3.1 ↔ Task 3.2 ↔ Task 3.5 일관 ✓
- `AttachmentRef` (프론트 interface) = `{ attachmentId: number, pageStart: number | null, pageEnd: number | null }` — Task 4.5 ↔ Task 4.6 일관 ✓
- `ChunkInput` (record) = `(String content, Long attachmentId, Integer pageStart, Integer pageEnd)` — Task 3.3 ↔ Task 3.6 일관 ✓
- `ValidationReport` 가 Task 3.5 에서 `hasInvalidAttachmentRef` 추가, 호출처 모두 갱신 (GuideGenerationService) ✓
- `GuideValidator.validate()` 시그니처 (1-arg → 2-arg) — Task 3.5 에서 모든 호출처 갱신 명시 ✓
- `AttachmentStorage.presign(String, Duration) → Optional<String>` — Task 4.1 ↔ Task 4.3 (RedirectAttachmentService) 일관 ✓
- `AttachmentRedirectResult` sealed (PresignRedirect / StreamResponse / ExternalRedirect) — Task 4.3 ↔ Task 4.4 일관 ✓
- 프론트 `AttachmentSummary` (rename 후) — Task 4.5 (rename) ↔ Task 4.7 (`SourceLinkedListCard.attachments`) 일관 ✓

### Self-review 결론

수정 필요 없음. 다만 **Task 2.3 의 DocumentChunker 구현이 단락/문장 분할 로직을 단순화** 했는데, 기존 chunker 의 단락 우선 분할이 RAG 검색 품질에 영향이 있다면 후속 PR 에서 보강해야 함. 본 PR 의 scope 에서는 character 분할 + 본문/첨부 boundary 강제 분할만 보장.

---

## 실행 옵션

**Plan complete and saved to `docs/superpowers/plans/2026-04-29-attachment-source-trace.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — 매 task 마다 fresh subagent dispatch, task 사이 review, 빠른 iteration

**2. Inline Execution** — 본 세션에서 executing-plans 스킬로 batch 실행, checkpoint 마다 review

**Which approach?**



