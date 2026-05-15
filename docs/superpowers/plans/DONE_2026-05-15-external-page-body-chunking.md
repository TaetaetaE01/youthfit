# External Page Body Chunking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년 enrichment 파이프라인이 정제한 외부 페이지 본문(cleanedText, 8000자 cap)을 별도 `PolicyDocument` 청크(`ENRICHMENT_BODY` source)로 보관해 RAG·가이드 입력에 원문 어휘가 흐르도록 한다.

**Architecture:** ingestion 이 n8n payload 의 `enrichment.cleanedText` 를 `PolicyEnrichment` 에 보존 → `DocumentChunker` 가 기존 BODY/ATTACHMENT 청크에 더해 `cleanedText` 를 `ENRICHMENT_BODY` source 청크로 분할 → 동일 임베딩 모델로 인덱싱 → `RagSearchService` 는 source 무관하게 거리 검색, 가이드는 청크 라벨에 `source=ENRICHMENT_BODY` 노출하고 LLM 이 `sourceField=ENRICHMENT` 로 매핑한다.

**Tech Stack:** Java 21, Spring Boot 4.0.5, PostgreSQL 17 + pgvector, Flyway (`backend/src/main/resources/sql/`), JUnit 5, n8n.

**Spec:** [`docs/superpowers/specs/2026-05-15-external-page-body-chunking-design.md`](../specs/2026-05-15-external-page-body-chunking-design.md)

**전제:** PR #100 (`feat/guide-enrichment-bokjiro`) 머지 완료 (2026-05-14).

**Spec 대비 보정:** spec §8.2·§10.2·§16-4 가 `PROMPT_VERSION v5 → v6` 으로 적었으나 실제 코드는 `static final String PROMPT_VERSION = "v4"` (`backend/.../GuideGenerationService.java:42`). 본 plan 은 **v4 → v5** 로 증분한다.

---

## 작업 단위 개요

| # | Task | 영향 모듈 | TDD 가능 |
|---|---|---|---|
| 1 | `PolicyDocumentSource` enum 신설 | rag | O |
| 2 | `policy_document.source` 컬럼 + 마이그레이션 + `PolicyDocument.source` 필드 | rag, DB | O |
| 3 | `PolicyEnrichment.cleanedText` 필드 | policy | O |
| 4 | `IngestPolicyRequest`/`IngestPolicyCommand` 에 `cleanedText` 매핑 | ingestion | O |
| 5 | `DocumentChunker.computeHash` 에 `enrichment.cleanedText` 포함 | rag | O |
| 6 | `DocumentChunker` 가 기존 청크에 `source` 부여 | rag | O |
| 7 | `DocumentChunker.chunkWithEnrichment` 가 `cleanedText` 도 `ENRICHMENT_BODY` 청크로 분할 | rag | O |
| 8 | `RagIndexingService` hash 시그니처 정합 + 관찰 가능성 로깅 | rag | O |
| 9 | `ChunkInput.source` 필드 + `GuideGenerationInput.combinedSourceText` 라벨 확장 + `computeHash` 갱신 | guide | O |
| 10 | `OpenAiChatClient.SYSTEM_PROMPT` 에 `ENRICHMENT_BODY → ENRICHMENT` 매핑 규칙 추가 + `PROMPT_VERSION` v4 → v5 | guide | O |
| 11 | n8n `youth-center-seoul.json` 워크플로우에 `cleanedText` 부착 | n8n | 수동 |
| 12 | 통합 시나리오 E2E 검증 (수동 + 로그 확인) | 전체 | 수동 |

각 task 가 자체적으로 빌드·테스트·커밋 가능한 단위. 의존성: 1 → 2 → (3 ↔ 4) → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12. 3 과 4 는 병렬 가능.

---

## Task 1: `PolicyDocumentSource` enum 신설

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocumentSource.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentSourceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentSourceTest.java`:

```java
package com.youthfit.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDocumentSourceTest {

    @Test
    void enum_은_3개_값을_가진다() {
        assertThat(PolicyDocumentSource.values())
                .containsExactly(
                        PolicyDocumentSource.BODY,
                        PolicyDocumentSource.ATTACHMENT,
                        PolicyDocumentSource.ENRICHMENT_BODY);
    }

    @Test
    void name_은_DB_저장_문자열과_일치한다() {
        assertThat(PolicyDocumentSource.BODY.name()).isEqualTo("BODY");
        assertThat(PolicyDocumentSource.ATTACHMENT.name()).isEqualTo("ATTACHMENT");
        assertThat(PolicyDocumentSource.ENRICHMENT_BODY.name()).isEqualTo("ENRICHMENT_BODY");
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

```bash
cd backend && ./gradlew compileTestJava
```
Expected: FAIL with `cannot find symbol PolicyDocumentSource`

- [ ] **Step 3: enum 작성**

`backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocumentSource.java`:

```java
package com.youthfit.rag.domain.model;

/**
 * PolicyDocument 청크의 출처 구분.
 *
 * <ul>
 *     <li>{@link #BODY} — 정책 본문 컬럼(body / supportTarget / supportContent 등)</li>
 *     <li>{@link #ATTACHMENT} — 첨부 파일에서 추출된 텍스트</li>
 *     <li>{@link #ENRICHMENT_BODY} — 외부 정책 안내 페이지 본문(enrichment.cleanedText)</li>
 * </ul>
 */
public enum PolicyDocumentSource {
    BODY,
    ATTACHMENT,
    ENRICHMENT_BODY
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.domain.model.PolicyDocumentSourceTest"
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocumentSource.java \
        backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentSourceTest.java
git commit -m "feat(rag): PolicyDocumentSource enum 신설 (BODY/ATTACHMENT/ENRICHMENT_BODY)"
```

---

## Task 2: `policy_document.source` 컬럼 + `PolicyDocument.source` 필드

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-15-policy-document-source.sql`
- Modify: `backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentTest.java` (기존 파일 확장)

- [ ] **Step 1: Flyway 마이그레이션 작성**

`backend/src/main/resources/sql/2026-05-15-policy-document-source.sql`:

```sql
ALTER TABLE policy_document
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'BODY';

UPDATE policy_document
   SET source = 'ATTACHMENT'
 WHERE attachment_id IS NOT NULL;

-- DEFAULT 는 마이그레이션 적용 후 운영자가 별도 단계에서 제거한다.
-- (롤백 안전성을 위해 spec §11 의 5단계 롤백 경로 참조)
```

- [ ] **Step 2: `PolicyDocument` 엔티티에 `source` 필드 추가 — 실패 테스트**

`backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentTest.java` 에 추가:

```java
@Test
void builder_는_source_를_요구한다() {
    PolicyDocument doc = PolicyDocument.builder()
            .policyId(1L)
            .chunkIndex(0)
            .content("샘플 청크")
            .sourceHash("hash")
            .source(PolicyDocumentSource.BODY)
            .build();

    assertThat(doc.getSource()).isEqualTo(PolicyDocumentSource.BODY);
}

@Test
void builder_는_ENRICHMENT_BODY_source_를_지원한다() {
    PolicyDocument doc = PolicyDocument.builder()
            .policyId(1L)
            .chunkIndex(0)
            .content("외부 페이지 본문 발췌")
            .sourceHash("hash")
            .source(PolicyDocumentSource.ENRICHMENT_BODY)
            .build();

    assertThat(doc.getSource()).isEqualTo(PolicyDocumentSource.ENRICHMENT_BODY);
    assertThat(doc.getAttachmentId()).isNull();
}
```

- [ ] **Step 3: 컴파일 실패 확인**

```bash
cd backend && ./gradlew compileTestJava
```
Expected: FAIL with `cannot find symbol method source / getSource`

- [ ] **Step 4: `PolicyDocument` 수정 — source 필드 + builder + getter**

`backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java` 의 필드와 빌더를 다음과 같이 갱신:

```java
@Enumerated(EnumType.STRING)
@Column(name = "source", nullable = false, length = 32)
private PolicyDocumentSource source;
```

그리고 `@Builder` 생성자 시그니처와 본문에 `source` 인자 추가:

```java
@Builder
private PolicyDocument(Long policyId,
                       int chunkIndex,
                       String content,
                       String sourceHash,
                       PolicyDocumentSource source,
                       Long attachmentId,
                       Integer pageStart,
                       Integer pageEnd) {
    this.policyId = policyId;
    this.chunkIndex = chunkIndex;
    this.content = content;
    this.sourceHash = sourceHash;
    this.source = source;
    this.attachmentId = attachmentId;
    this.pageStart = pageStart;
    this.pageEnd = pageEnd;
}
```

`@Getter` 가 적용되어 있으므로 `getSource()` 는 자동 생성.

- [ ] **Step 5: 컴파일 후 기존 호출부 컴파일 에러 잡기**

```bash
cd backend && ./gradlew compileJava
```
Expected: FAIL with builder 호출부 (`DocumentChunker`, 테스트 등) source 미지정.

각 호출부에서 `source(PolicyDocumentSource.BODY)` 또는 적절한 값을 임시로 추가하라. (실제 source 매핑은 Task 6 에서 정리)

호출부 후보 (필요 시 grep 으로 모두 식별):
- `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` (2곳: chunk + chunkWithEnrichment)
- 기존 `PolicyDocumentTest` 등 테스트들

임시 매핑 규칙:
- `chunk()` 메서드: `attachmentId == null` 이면 `BODY`, 아니면 `ATTACHMENT`
- `chunkWithEnrichment()` 의 enrichment 섹션 청크: `BODY` (현재 기준 — Task 6 에서 별도 분류)

- [ ] **Step 6: 테스트 + 빌드 통과 확인**

```bash
cd backend && ./gradlew test
```
Expected: PASS (전체 테스트)

Flyway 마이그레이션은 통합 테스트 컨텍스트가 띄울 때 자동 적용. 만약 통합 테스트가 H2 사용한다면 PostgreSQL 전용 구문 호환성 확인 필요 (현 spec 은 PostgreSQL 전용 운영).

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-15-policy-document-source.sql \
        backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java \
        backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java \
        backend/src/test/java/com/youthfit/rag/domain/model/PolicyDocumentTest.java
git commit -m "feat(rag): PolicyDocument.source 컬럼 추가 + Flyway 마이그레이션"
```

---

## Task 3: `PolicyEnrichment.cleanedText` 필드

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyEnrichmentTest.java` (없으면 생성)

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/policy/domain/model/PolicyEnrichmentTest.java`:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEnrichmentTest {

    @Test
    void cleanedText_는_nullable_이다() {
        PolicyEnrichment enrichment = new PolicyEnrichment(
                "https://example.com",
                Instant.now(),
                "extractor",
                0.9,
                EnrichmentStatus.OK,
                null,
                List.of(),
                null
        );
        assertThat(enrichment.cleanedText()).isNull();
    }

    @Test
    void cleanedText_는_값을_그대로_보관한다() {
        String text = "외부 페이지에서 정제된 본문";
        PolicyEnrichment enrichment = new PolicyEnrichment(
                "https://example.com",
                Instant.now(),
                "extractor",
                0.9,
                EnrichmentStatus.OK,
                null,
                List.of(),
                text
        );
        assertThat(enrichment.cleanedText()).isEqualTo(text);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

```bash
cd backend && ./gradlew compileTestJava
```
Expected: FAIL — record 생성자 인자 수 불일치.

- [ ] **Step 3: record 에 필드 추가**

`backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java` 의 record 정의 갱신:

```java
public record PolicyEnrichment(
        String sourceUrl,
        Instant fetchedAt,
        String extractor,
        Double confidence,
        EnrichmentStatus status,
        Sections sections,
        List<ExtraAttachment> extraAttachments,
        String cleanedText
) {
    // ... 기존 메서드 그대로 ...
}
```

`Sections` / `ExtraAttachment` nested record 와 `isExposable()` 메서드는 그대로 유지. cleanedText 는 isExposable 판정에 관여하지 않는다 (status·confidence 기준 유지).

- [ ] **Step 4: 기존 PolicyEnrichment 생성 호출부 컴파일 에러 잡기**

```bash
cd backend && ./gradlew compileJava
```
Expected: FAIL — `IngestPolicyRequest.mapEnrichment()` 등 7-인자 생성자 호출부.

기존 호출부에 `null` 또는 적절한 값을 마지막 인자로 추가하라. Task 4 에서 ingestion 흐름에서 실제 매핑 (cleanedText 가 payload 로부터 들어옴).

확인 대상 (필요 시 grep `new PolicyEnrichment\\(`):
- `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java:106-113` → 임시로 `, null` 추가
- 테스트들

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test
```
Expected: PASS (전체).

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicyEnrichment.java \
        backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java \
        backend/src/test/java/com/youthfit/policy/domain/model/PolicyEnrichmentTest.java
git commit -m "feat(policy): PolicyEnrichment.cleanedText 필드 추가 (nullable)"
```

---

## Task 4: `IngestPolicyRequest` → `IngestPolicyCommand` 에 `cleanedText` 매핑

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java` (구조 확인 후 enrichment 필드가 cleanedText 를 함께 전달하도록 흐름 유지)
- Test: `backend/src/test/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequestTest.java` (없으면 생성)

> **Note**: `IngestPolicyCommand` 의 enrichment 필드 타입이 무엇인지 먼저 확인하라. 만약 `PolicyEnrichment` 도메인 객체를 그대로 받는다면 Task 3 에서 추가한 cleanedText 가 자동으로 흐른다. 다른 DTO 라면 그 DTO 에도 cleanedText 를 추가해야 한다.

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequestTest.java` (혹은 기존 테스트 확장):

```java
package com.youthfit.ingestion.presentation.dto.request;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestPolicyRequestTest {

    @Test
    void enrichment_payload_의_cleanedText_가_PolicyEnrichment_로_매핑된다() {
        IngestPolicyRequest.EnrichmentPayload payload = new IngestPolicyRequest.EnrichmentPayload(
                "https://example.com/policy/123",
                LocalDateTime.now(),
                "n8n-cheerio-v1",
                0.85,
                "OK",
                null,
                List.of(),
                "외부 페이지 본문 (boilerplate 제거 후)"
        );
        IngestPolicyRequest request = sampleRequestWith(payload);

        IngestPolicyCommand command = request.toCommand();

        assertThat(command.enrichment()).isNotNull();
        assertThat(command.enrichment().cleanedText())
                .isEqualTo("외부 페이지 본문 (boilerplate 제거 후)");
    }

    @Test
    void cleanedText_가_null_이면_PolicyEnrichment_cleanedText_도_null() {
        IngestPolicyRequest.EnrichmentPayload payload = new IngestPolicyRequest.EnrichmentPayload(
                "https://example.com/policy/123",
                LocalDateTime.now(),
                "n8n-cheerio-v1",
                0.85,
                "OK",
                null,
                List.of(),
                null
        );
        IngestPolicyRequest request = sampleRequestWith(payload);

        IngestPolicyCommand command = request.toCommand();

        assertThat(command.enrichment().cleanedText()).isNull();
    }

    private IngestPolicyRequest sampleRequestWith(IngestPolicyRequest.EnrichmentPayload enrichment) {
        // 최소 필수값으로 채우기 — 도메인 객체 구조에 맞춰 RawData/SourceInfo 생성
        // 본 plan 실행 시 IngestPolicyRequest 의 다른 필수 필드를 채워서 빌드
        throw new UnsupportedOperationException("실제 테스트에서 RawData/SourceInfo 구성");
    }
}
```

> **Implementation note**: `sampleRequestWith` 헬퍼는 실제 작성 시 IngestPolicyRequest 의 모든 필수 필드를 채워야 한다. 기존 다른 IngestPolicyRequest 테스트가 있으면 그 패턴을 재사용.

- [ ] **Step 2: 컴파일 실패 확인**

```bash
cd backend && ./gradlew compileTestJava
```
Expected: FAIL — `EnrichmentPayload` 생성자 인자 수 불일치.

- [ ] **Step 3: `EnrichmentPayload` 에 cleanedText 추가 + mapEnrichment 매핑**

`backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java` 의 `EnrichmentPayload` 갱신:

```java
public record EnrichmentPayload(
        String sourceUrl,                   // NO_LINK 시 null 허용
        @NotNull LocalDateTime fetchedAt,
        @NotBlank String extractor,
        Double confidence,
        @NotBlank String status,
        @Valid EnrichmentSectionsPayload sections,
        List<@Valid ExtraAttachmentPayload> extraAttachments,
        String cleanedText                  // NEW — nullable, n8n 에서 cap 후 전달
) {}
```

그리고 `mapEnrichment()` 의 `return new PolicyEnrichment(...)` 마지막 인자에 `p.cleanedText()` 매핑:

```java
return new PolicyEnrichment(
        p.sourceUrl(),
        p.fetchedAt().toInstant(ZoneOffset.UTC),
        p.extractor(),
        p.confidence(),
        status,
        sections,
        atts,
        p.cleanedText()
);
```

- [ ] **Step 4: `IngestPolicyCommand` 의 enrichment 타입 확인 + 필요 시 갱신**

`backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java` 를 열고 enrichment 필드 타입 확인:

- 만약 `PolicyEnrichment` 도메인 객체 그대로 → 추가 변경 불요 (Task 3 의 record 확장이 자동 전파)
- 만약 별도 nested record 면 cleanedText 필드 + 매핑 추가

마찬가지로 `IngestionService.receivePolicy()` 가 `command.enrichment()` 를 `RegisterPolicyCommand` 까지 전달하는 경로를 따라가, `policy.enrichment` jsonb 컬럼에 cleanedText 가 직렬화돼 들어가는지 확인.

> `PolicyEnrichment` 가 jsonb 컬럼에 매핑되어 있고 jackson 기본 직렬화 사용이면 record 필드 추가만으로 자동 직렬화/역직렬화된다. 별도 컬럼·테이블 마이그레이션 불요.

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.*"
cd backend && ./gradlew test
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java \
        backend/src/main/java/com/youthfit/ingestion/application/dto/command/IngestPolicyCommand.java \
        backend/src/test/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequestTest.java
git commit -m "feat(ingestion): enrichment.cleanedText 를 PolicyEnrichment 까지 전달"
```

---

## Task 5: `DocumentChunker.computeHash` 에 `enrichment.cleanedText` 포함

**배경**: 현재 `computeHash(content)` 는 `content` 만으로 hash 를 계산한다. enrichment 만 변경되면 hash 가 그대로라 `RagIndexingService` 가 재인덱싱을 스킵한다 (line 42 `existingHash.equals(newHash)`). cleanedText 추가로 인한 청크 변화를 트리거하려면 hash 입력에 cleanedText 가 들어가야 한다.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java` (호출부)
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java` (없으면 생성)

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java` 에 추가:

```java
@Test
void computeHash_는_enrichment_cleanedText_가_바뀌면_달라진다() {
    DocumentChunker chunker = new DocumentChunker();
    PolicyEnrichment a = new PolicyEnrichment(
            "url", Instant.now(), "ex", 0.9, EnrichmentStatus.OK, null, List.of(), "텍스트 A");
    PolicyEnrichment b = new PolicyEnrichment(
            "url", Instant.now(), "ex", 0.9, EnrichmentStatus.OK, null, List.of(), "텍스트 B");

    String hashA = chunker.computeHash("동일 본문", a);
    String hashB = chunker.computeHash("동일 본문", b);

    assertThat(hashA).isNotEqualTo(hashB);
}

@Test
void computeHash_는_enrichment_null_이면_content_만의_hash_와_동일() {
    DocumentChunker chunker = new DocumentChunker();

    String oldHash = chunker.computeHash("본문 X");        // 기존 1-인자
    String newHash = chunker.computeHash("본문 X", null);  // null enrichment

    assertThat(oldHash).isEqualTo(newHash);
}
```

- [ ] **Step 2: 컴파일 실패 확인**

```bash
cd backend && ./gradlew compileTestJava
```
Expected: FAIL — 2-인자 computeHash 메서드 없음.

- [ ] **Step 3: `DocumentChunker.computeHash` 오버로드 추가**

`backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java` 에 새 메서드 추가 (기존 1-인자는 유지 — 다른 호출부 호환):

```java
public String computeHash(String content, PolicyEnrichment enrichment) {
    String enrichmentKey = enrichment == null || enrichment.cleanedText() == null
            ? ""
            : enrichment.cleanedText();
    return computeHash(content + "ENRICHMENT_BODY" + enrichmentKey);
}
```

> `` 은 ASCII SOH 제어 문자. 본문에 등장할 가능성 없는 구분자로 사용해 (content="AENRICHMENT_BODYB", enrichment=null) 같은 충돌 회피.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"
```
Expected: PASS

- [ ] **Step 5: `RagIndexingService` 가 새 시그니처 사용**

`backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java:37`:

```java
String newHash = documentChunker.computeHash(command.content(), command.enrichment());
```

`existing.get(0).getSourceHash()` 와의 비교는 그대로. `DocumentChunker.chunk()` 내부의 `computeHash(content)` 도 동일하게 갱신해야 한다 (Task 6 의 source 정합과 함께). 본 task 에서는 `RagIndexingService` 한 곳만 갱신하고, Task 6 에서 chunker 내부 hash 도 통일.

- [ ] **Step 6: 전체 테스트 통과 확인**

```bash
cd backend && ./gradlew test
```
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java \
        backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java \
        backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "feat(rag): DocumentChunker.computeHash 가 enrichment.cleanedText 를 포함해 재인덱싱 트리거 보장"
```

---

## Task 6: `DocumentChunker` 가 기존 청크에 `source` 부여

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`DocumentChunkerTest` 에 추가:

```java
@Test
void chunk_는_attachmentId_없으면_source_BODY_를_부여한다() {
    DocumentChunker chunker = new DocumentChunker();
    String content = "=== 정책 본문 ===\n청년 정책 안내. 자격 요건은 만 19~34세.\n";

    List<PolicyDocument> chunks = chunker.chunk(1L, content);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allSatisfy(c -> {
        assertThat(c.getSource()).isEqualTo(PolicyDocumentSource.BODY);
        assertThat(c.getAttachmentId()).isNull();
    });
}

@Test
void chunk_는_첨부_segment_의_청크에_source_ATTACHMENT_를_부여한다() {
    DocumentChunker chunker = new DocumentChunker();
    String content = """
            === 정책 본문 ===
            본문 내용.
            === 첨부 attachment-id=42 name="공고문.pdf" ===
            첨부 내용.
            """;

    List<PolicyDocument> chunks = chunker.chunk(1L, content);

    assertThat(chunks).anySatisfy(c -> {
        assertThat(c.getSource()).isEqualTo(PolicyDocumentSource.BODY);
        assertThat(c.getAttachmentId()).isNull();
    });
    assertThat(chunks).anySatisfy(c -> {
        assertThat(c.getSource()).isEqualTo(PolicyDocumentSource.ATTACHMENT);
        assertThat(c.getAttachmentId()).isEqualTo(42L);
    });
}
```

- [ ] **Step 2: 실행 시 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"
```
Expected: FAIL — Task 2 의 임시 매핑이 builder 에 들어가 있으나 `getSource()` 결과 검증 안 거침. 명시적 단언이 실패해야 정상.

(만약 Task 2 에서 이미 BODY/ATTACHMENT 매핑을 임시 적용했으면 이 단계는 그대로 PASS 할 가능성 — 그 경우 다음 step 의 변경 후 회귀가 없는지 확인)

- [ ] **Step 3: `chunk()` 가 source 를 명시 세팅**

`DocumentChunker.chunk()` 의 `PolicyDocument.builder()` 호출에 다음 줄 추가:

```java
.source(seg.attachmentId() == null
        ? PolicyDocumentSource.BODY
        : PolicyDocumentSource.ATTACHMENT)
```

전체 빌더는 아래와 같이 변한다:

```java
documents.add(PolicyDocument.builder()
        .policyId(policyId)
        .chunkIndex(globalIndex++)
        .content(c.text())
        .sourceHash(sourceHash)
        .source(seg.attachmentId() == null
                ? PolicyDocumentSource.BODY
                : PolicyDocumentSource.ATTACHMENT)
        .attachmentId(seg.attachmentId())
        .pageStart(c.pageStart())
        .pageEnd(c.pageEnd())
        .build());
```

또한 `chunkWithEnrichment()` 의 enrichment 섹션 청크는 정책 본문 LLM 추출 (BODY 와 동일한 의미)이므로 `.source(PolicyDocumentSource.BODY)` 를 추가:

```java
result.add(PolicyDocument.builder()
        .policyId(policyId)
        .chunkIndex(globalIndex++)
        .content(enrichedContent)
        .sourceHash(sourceHash)
        .source(PolicyDocumentSource.BODY)
        .build());
```

> 결정 근거: spec §16-1 결정에 따라 ENRICHMENT_BODY 는 cleanedText 본문 발췌만 가리킨다. enrichment.sections (LLM 추출) 는 기존 정책 본문 의미 (BODY) 와 같은 카테고리로 둔다 (현재 `[자동수집-...]` prefix 가 출처 식별을 텍스트로 제공).

- [ ] **Step 4: 내부 computeHash 호출 정합**

`chunk()` line 58 의 `computeHash(content)` 와 `chunkWithEnrichment()` line 130 의 `computeHash(content)` 를 그대로 둔다 (기존 1-인자 메서드는 유지). `RagIndexingService` 가 2-인자 hash 를 쓰므로 청크 저장용 sourceHash 와 인덱싱 hash 가 다를 수 있는데, 청크의 sourceHash 는 단지 정책별 묶음 식별자이므로 기존 동작 유지가 안전. (현 코드는 sourceHash 를 동등성 비교에만 쓰지 검색 키로 쓰지 않음)

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java \
        backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "feat(rag): DocumentChunker 가 BODY/ATTACHMENT source 를 명시 부여"
```

---

## Task 7: `chunkWithEnrichment` 가 `cleanedText` 도 `ENRICHMENT_BODY` 청크로 분할

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`
- Test: `backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`DocumentChunkerTest` 에 추가:

```java
@Test
void chunkWithEnrichment_는_cleanedText_를_ENRICHMENT_BODY_청크로_분할한다() {
    DocumentChunker chunker = new DocumentChunker();
    PolicyEnrichment enrichment = new PolicyEnrichment(
            "https://ext.example.com/policy/1",
            Instant.now(),
            "n8n-cheerio-v1",
            0.85,
            EnrichmentStatus.OK,
            null,
            List.of(),
            "외부 페이지 본문. 신청서 양식은 별도 공지로 안내. 마감일은 매월 말일."
    );

    List<PolicyDocument> chunks = chunker.chunkWithEnrichment(1L, "정책 본문", enrichment);

    assertThat(chunks).anySatisfy(c -> {
        assertThat(c.getSource()).isEqualTo(PolicyDocumentSource.ENRICHMENT_BODY);
        assertThat(c.getAttachmentId()).isNull();
        assertThat(c.getContent()).contains("외부 페이지 본문");
    });
}

@Test
void chunkWithEnrichment_는_isExposable_false_면_ENRICHMENT_BODY_청크를_만들지_않는다() {
    DocumentChunker chunker = new DocumentChunker();
    PolicyEnrichment enrichment = new PolicyEnrichment(
            "https://ext.example.com/policy/1",
            Instant.now(),
            "n8n-cheerio-v1",
            0.3,                    // < EXPOSURE_CONFIDENCE_THRESHOLD (0.6)
            EnrichmentStatus.OK,
            null,
            List.of(),
            "외부 페이지 본문"
    );

    List<PolicyDocument> chunks = chunker.chunkWithEnrichment(1L, "정책 본문", enrichment);

    assertThat(chunks).noneMatch(c -> c.getSource() == PolicyDocumentSource.ENRICHMENT_BODY);
}

@Test
void chunkWithEnrichment_는_cleanedText_null_또는_blank_면_ENRICHMENT_BODY_청크를_만들지_않는다() {
    DocumentChunker chunker = new DocumentChunker();
    PolicyEnrichment enrichment = new PolicyEnrichment(
            "url", Instant.now(), "ex", 0.9, EnrichmentStatus.OK, null, List.of(), null);

    List<PolicyDocument> chunks = chunker.chunkWithEnrichment(1L, "정책 본문", enrichment);

    assertThat(chunks).noneMatch(c -> c.getSource() == PolicyDocumentSource.ENRICHMENT_BODY);
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"
```
Expected: FAIL — ENRICHMENT_BODY 청크가 생성되지 않음.

- [ ] **Step 3: `chunkWithEnrichment` 확장**

`DocumentChunker.chunkWithEnrichment()` 메서드 끝부분(sectionEntries 처리 후) 에 cleanedText 처리 블록 추가:

```java
public List<PolicyDocument> chunkWithEnrichment(Long policyId, String content,
                                                 PolicyEnrichment enrichment) {
    List<PolicyDocument> base = chunk(policyId, content);

    if (enrichment == null || !enrichment.isExposable()) {
        return base;
    }

    List<PolicyDocument> result = new ArrayList<>(base);
    int globalIndex = base.size();
    String sourceHash = computeHash(content);

    // 기존: enrichment.sections 청크 (BODY source)
    if (enrichment.sections() != null) {
        Map<String, String> sectionEntries = buildSectionEntries(enrichment.sections());
        for (Map.Entry<String, String> entry : sectionEntries.entrySet()) {
            String enrichedContent = "[자동수집-" + entry.getKey() + "] " + entry.getValue();
            result.add(PolicyDocument.builder()
                    .policyId(policyId)
                    .chunkIndex(globalIndex++)
                    .content(enrichedContent)
                    .sourceHash(sourceHash)
                    .source(PolicyDocumentSource.BODY)
                    .build());
        }
    }

    // 신규: cleanedText 를 BODY splitter 로 분할해 ENRICHMENT_BODY 청크 생성
    String cleaned = enrichment.cleanedText();
    if (cleaned != null && !cleaned.isBlank()) {
        List<PolicyDocument> cleanedChunks = chunk(policyId, cleaned);
        for (PolicyDocument cc : cleanedChunks) {
            result.add(PolicyDocument.builder()
                    .policyId(policyId)
                    .chunkIndex(globalIndex++)
                    .content(cc.getContent())
                    .sourceHash(sourceHash)
                    .source(PolicyDocumentSource.ENRICHMENT_BODY)
                    .build());
        }
    }

    return result;
}

private Map<String, String> buildSectionEntries(PolicyEnrichment.Sections sections) {
    Map<String, String> entries = new java.util.LinkedHashMap<>();
    if (sections.supportTarget() != null && !sections.supportTarget().isBlank()) {
        entries.put("지원대상", sections.supportTarget());
    }
    if (sections.supportContent() != null && !sections.supportContent().isBlank()) {
        entries.put("지원내용", sections.supportContent());
    }
    if (sections.applyMethod() != null && !sections.applyMethod().isBlank()) {
        entries.put("신청방법", sections.applyMethod());
    }
    if (sections.requiredDocuments() != null && !sections.requiredDocuments().isBlank()) {
        entries.put("제출서류", sections.requiredDocuments());
    }
    if (sections.deadlineNote() != null && !sections.deadlineNote().isBlank()) {
        entries.put("마감안내", sections.deadlineNote());
    }
    return entries;
}
```

> **주의 (재인덱싱 트리거)**: spec §16-2 결정에 따라 isExposable=false 면 청크화 안 함 — 위 코드의 `if (... !isExposable()) return base;` 가 처리. cleanedText 도 동일하게 영향받음.

> **chunkIndex 충돌 회피**: cleanedText 청크는 base + sections 다음에 이어 붙여 globalIndex 연속 유지.

> **재사용**: cleanedText 분할은 `chunk(policyId, cleaned)` 를 재호출해 BODY splitter (table-aware + 줄 보존 + overlap) 를 그대로 적용. 단 이 결과의 `source` 는 모두 BODY 로 세팅돼 있으니, 결과를 다시 build 해 ENRICHMENT_BODY 로 source 만 교체.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.DocumentChunkerTest"
```
Expected: PASS

- [ ] **Step 5: 전체 테스트**

```bash
cd backend && ./gradlew test
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java \
        backend/src/test/java/com/youthfit/rag/domain/service/DocumentChunkerTest.java
git commit -m "feat(rag): chunkWithEnrichment 가 cleanedText 를 ENRICHMENT_BODY 청크로 분할"
```

---

## Task 8: `RagIndexingService` 관찰 가능성 로깅 + 통합 흐름 정합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java`
- Test: `backend/src/test/java/com/youthfit/rag/application/service/RagIndexingServiceTest.java` (없으면 생성)

- [ ] **Step 1: 실패 테스트 작성**

`RagIndexingServiceTest`:

```java
@Test
void indexPolicyDocument_는_source_별_청크_수를_로그로_남긴다() {
    // captor 로 LogEvent 검증, 또는 mock SLF4J. 간단히는 ListAppender 사용.
    Logger ragLogger = (Logger) LoggerFactory.getLogger(RagIndexingService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ragLogger.addAppender(appender);

    // ... given: cost guard allows, command with enrichment having cleanedText ...
    sut.indexPolicyDocument(commandWithCleanedText);

    assertThat(appender.list)
            .anyMatch(e -> e.getFormattedMessage().contains("body_chunks_count")
                        && e.getFormattedMessage().contains("attachment_chunks_count")
                        && e.getFormattedMessage().contains("enrichment_body_chunks_count"));
}
```

> **Implementation note**: 기존 RagIndexingService 테스트 구조에 맞춰 mock policyDocumentRepository / documentChunker / embeddingProvider / costGuard / qnaCacheInvalidator 를 구성. 본 plan 의 코드 스니펫은 핵심만 보여줌.

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagIndexingServiceTest"
```
Expected: FAIL — 해당 로그 메시지 없음.

- [ ] **Step 3: 로그 추가**

`RagIndexingService.indexPolicyDocument()` 의 `return new IndexingResult(...)` 직전에 source 별 카운트 로그 추가:

```java
long bodyCount = chunks.stream()
        .filter(c -> c.getSource() == PolicyDocumentSource.BODY).count();
long attachmentCount = chunks.stream()
        .filter(c -> c.getSource() == PolicyDocumentSource.ATTACHMENT).count();
long enrichmentBodyCount = chunks.stream()
        .filter(c -> c.getSource() == PolicyDocumentSource.ENRICHMENT_BODY).count();
log.info("정책 인덱싱 완료: policyId={}, body_chunks_count={}, attachment_chunks_count={}, enrichment_body_chunks_count={}",
        command.policyId(), bodyCount, attachmentCount, enrichmentBodyCount);

return new IndexingResult(command.policyId(), chunks.size(), true);
```

import 추가: `import com.youthfit.rag.domain.model.PolicyDocumentSource;`

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagIndexingServiceTest"
cd backend && ./gradlew test
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java \
        backend/src/test/java/com/youthfit/rag/application/service/RagIndexingServiceTest.java
git commit -m "feat(rag): RagIndexingService 가 source 별 청크 수를 로그로 남김"
```

---

## Task 9: `ChunkInput.source` + `GuideGenerationInput.combinedSourceText` 라벨 + `computeHash` 정합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/ChunkInput.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java` (없으면 생성)

- [ ] **Step 1: 실패 테스트 작성**

`GuideGenerationInputTest`:

```java
package com.youthfit.guide.application.dto.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationInputTest {

    @Test
    void combinedSourceText_는_BODY_청크에_source_BODY_라벨을_붙인다() {
        GuideGenerationInput input = inputWithChunks(List.of(
                new ChunkInput("본문 청크 텍스트", null, null, null, "BODY")
        ));

        String text = input.combinedSourceText();

        assertThat(text).contains("[chunk-0 source=BODY]");
    }

    @Test
    void combinedSourceText_는_ATTACHMENT_청크에_attachment_id_와_pages_를_노출한다() {
        GuideGenerationInput input = inputWithChunks(List.of(
                new ChunkInput("첨부 청크", 42L, 3, 5, "ATTACHMENT")
        ));

        String text = input.combinedSourceText();

        assertThat(text).contains("[chunk-0 source=ATTACHMENT attachment-id=42 pages=3-5]");
    }

    @Test
    void combinedSourceText_는_ENRICHMENT_BODY_청크에_별도_source_라벨을_붙인다() {
        GuideGenerationInput input = inputWithChunks(List.of(
                new ChunkInput("외부 페이지 발췌", null, null, null, "ENRICHMENT_BODY")
        ));

        String text = input.combinedSourceText();

        assertThat(text).contains("[chunk-0 source=ENRICHMENT_BODY]");
    }

    private GuideGenerationInput inputWithChunks(List<ChunkInput> chunks) {
        return new GuideGenerationInput(
                1L, "정책 제목", 2026, "summary", "body",
                null, null, null, null, null,
                chunks, null);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

```bash
cd backend && ./gradlew compileTestJava
```
Expected: FAIL — `ChunkInput` 5-인자 생성자 없음.

- [ ] **Step 3: `ChunkInput` 에 source 필드 추가**

`backend/src/main/java/com/youthfit/guide/application/dto/command/ChunkInput.java`:

```java
package com.youthfit.guide.application.dto.command;

/**
 * 가이드 생성 입력 청크 단위.
 *
 * @param content      청크 본문
 * @param attachmentId 첨부 ID. null 인 경우 BODY/ENRICHMENT_BODY 청크
 * @param pageStart    첨부 페이지 시작. null 인 경우 페이지 정보 없음
 * @param pageEnd      첨부 페이지 끝
 * @param source       청크 출처 라벨 — "BODY" / "ATTACHMENT" / "ENRICHMENT_BODY"
 *                     rag.domain.model.PolicyDocumentSource enum 의 .name() 결과를 그대로 사용한다.
 *                     guide 모듈이 rag enum 을 직접 import 하지 않도록 String 으로 매핑 (모듈 결합도 회피).
 */
public record ChunkInput(
        String content,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd,
        String source
) {}
```

- [ ] **Step 4: `GuideGenerationInput.of()` 매핑 갱신 + `combinedSourceText` 라벨 확장**

`backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`:

`of()` 메서드의 `new ChunkInput(...)` 5번째 인자에 source 매핑 추가:

```java
.map(d -> new ChunkInput(
        d.getContent(),
        d.getAttachmentId(),
        d.getPageStart(),
        d.getPageEnd(),
        d.getSource() == null ? "BODY" : d.getSource().name()))
```

(null fallback: 마이그레이션 중 기존 row 호환)

`combinedSourceText()` 의 라벨 부분 갱신:

```java
for (int i = 0; i < chunks.size(); i++) {
    ChunkInput c = chunks.get(i);
    sb.append('[').append("chunk-").append(i);
    sb.append(" source=").append(c.source());
    if (c.attachmentId() != null) {
        sb.append(" attachment-id=").append(c.attachmentId());
        if (c.pageStart() != null) {
            sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
        }
    }
    sb.append("]\n");
    sb.append(c.content()).append("\n\n");
}
```

- [ ] **Step 5: `GuideGenerationService.computeHash` 의 chunks 직렬화에 source 포함**

`backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` 의 `computeHash` (lines 201-216) 의 chunks 처리 부분에서 chunk 의 source 도 hash 입력에 포함:

```java
for (PolicyDocument chunk : chunks) {
    sb.append(chunk.getChunkIndex()).append('|');
    sb.append(chunk.getSource() == null ? "BODY" : chunk.getSource().name()).append('|');
    sb.append(chunk.getContent()).append('|');
    sb.append(chunk.getAttachmentId() == null ? "" : chunk.getAttachmentId()).append('\n');
}
```

(현재 라인 구조는 파일 직접 확인 후 정확히 갱신. 핵심은 hash 입력에 `chunk.getSource()` 가 들어가는 것)

- [ ] **Step 6: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.guide.*"
cd backend && ./gradlew test
```
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/command/ChunkInput.java \
        backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java
git commit -m "feat(guide): ChunkInput.source + combinedSourceText 가 ENRICHMENT_BODY 라벨 노출"
```

---

## Task 10: `SYSTEM_PROMPT` 매핑 규칙 + `PROMPT_VERSION` v4 → v5

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Test: `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java` (없으면 생성, SYSTEM_PROMPT 상수 노출 패턴 확인)

- [ ] **Step 1: 실패 테스트 작성**

`OpenAiChatClient` 의 `SYSTEM_PROMPT` 가 private 이면 package-private 으로 노출하거나 별도 verification 패턴 사용. 단순화하면 reflection 또는 `String SYSTEM_PROMPT = OpenAiChatClient.SYSTEM_PROMPT` 형식의 접근 (테스트 동일 패키지에 두면 접근 가능).

```java
package com.youthfit.guide.infrastructure.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatClientTest {

    @Test
    void SYSTEM_PROMPT_는_ENRICHMENT_BODY_청크_매핑_규칙을_포함한다() {
        assertThat(OpenAiChatClient.SYSTEM_PROMPT)
                .contains("source=ENRICHMENT_BODY")
                .contains("sourceField=ENRICHMENT");
    }
}
```

`GuideGenerationServiceTest`:

```java
@Test
void PROMPT_VERSION_은_v5_이다() {
    assertThat(GuideGenerationService.PROMPT_VERSION).isEqualTo("v5");
}
```

(기존 GuideGenerationServiceTest 에 추가)

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.guide.*"
```
Expected: FAIL — SYSTEM_PROMPT 에 매핑 규칙 부재, PROMPT_VERSION 은 v4.

- [ ] **Step 3: `SYSTEM_PROMPT` 갱신**

`backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` 의 `SYSTEM_PROMPT` 상수 본문 (현재 ENRICHMENT 라벨 규칙이 있는 부분) 에 다음 줄 추가:

```
- [chunk-N source=ENRICHMENT_BODY] 청크에서 가져온 정보는 sourceField=ENRICHMENT 로 라벨링한다.
  이 청크는 외부 정책 안내 페이지 본문 발췌이며 attachmentId 는 없다. attachmentRef 도 비워둔다.
```

기존 ATTACHMENT 라벨 규칙과 ENRICHMENT 라벨 규칙(line 68-82 근처) 사이에 위 두 줄을 자연스럽게 삽입.

(정확한 삽입 위치는 파일을 읽고 prompt 의 "출처 라벨" 섹션 다음에 둔다)

- [ ] **Step 4: `PROMPT_VERSION` 증분**

`GuideGenerationService.java:42`:

```java
static final String PROMPT_VERSION = "v5";  // 프롬프트 / 스키마 변경 시 증분
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.guide.*"
cd backend && ./gradlew test
```
Expected: PASS

> **운영 영향**: PROMPT_VERSION 변경으로 GuideGenerationService.computeHash 결과가 모든 정책에서 바뀜 → CostGuard allowlist 정책의 가이드가 다음 인덱싱·재생성 사이클에서 일회성 재생성. spec §10.2 참조.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java
git commit -m "feat(guide): SYSTEM_PROMPT 에 ENRICHMENT_BODY 매핑 규칙 추가, PROMPT_VERSION v4 → v5"
```

---

## Task 11: n8n `youth-center-seoul.json` 워크플로우에 `cleanedText` 부착

**Files:**
- Modify: `n8n/youth-center-seoul.json` (또는 운영 n8n 인스턴스에서 직접 export 후 갱신)

> **수동 작업**: n8n 워크플로우는 UI 로 편집한다. 본 task 는 JSON export 파일을 직접 수정하는 흐름.

- [ ] **Step 1: 현재 워크플로우 구조 파악**

`n8n/youth-center-seoul.json` 을 열어 enrichment 처리 노드 흐름 확인. 보통 다음 순서:

1. HTTP fetch (정책 안내 페이지)
2. cheerio (boilerplate 제거 → cleaned text)
3. text cap (8000자)
4. OpenAI gpt-4o-mini (sections 추출)
5. assemble IngestPolicyRequest payload
6. POST → backend `/api/admin/ingestion/policies`

- [ ] **Step 2: payload assemble 노드에 cleanedText 부착**

5번 노드의 `enrichment` 객체 구성에 다음 필드 추가:

```json
"enrichment": {
  "sourceUrl": "{{ $('fetch').item.json.url }}",
  "fetchedAt": "{{ $now.toISO() }}",
  "extractor": "n8n-cheerio-v1",
  "confidence": {{ $('llm-confidence').item.json.value }},
  "status": "{{ $('status').item.json.value }}",
  "sections": { /* 기존 */ },
  "extraAttachments": [ /* 기존 */ ],
  "cleanedText": "{{ $('cheerio-cap-8000').item.json.text }}"
}
```

> 노드 이름은 실제 워크플로우에 맞춰 수정. 핵심은 `cleanedText` 가 cheerio cap 후 텍스트 그대로 부착되는 것.

- [ ] **Step 3: n8n 워크플로우 dry-run**

n8n UI 에서 단일 정책(예: 한 청년 정책 url) 으로 워크플로우를 실행하고 마지막 payload 단계에서 `enrichment.cleanedText` 가 비어있지 않은 문자열로 들어가는지 시각 확인.

- [ ] **Step 4: 운영 활성화 전 backend 가 cleanedText 수신해도 throw 하지 않는지 확인**

backend 가 staging 에 배포된 상태에서 `/api/admin/ingestion/policies` 로 cleanedText 포함 payload 를 한 건 POST 해 200 + ingestion log 정상 확인. 실패 시 IngestPolicyRequest validation 또는 jackson 역직렬화 점검.

- [ ] **Step 5: 커밋**

```bash
git add n8n/youth-center-seoul.json
git commit -m "feat(n8n): youth-center-seoul 워크플로우가 enrichment.cleanedText 를 ingestion payload 에 부착"
```

---

## Task 12: 통합 시나리오 E2E 검증

**Files:** 없음 — 수동 시나리오 + 로그·DB 확인

> **목적**: backend·n8n 머지 후 실제 한 정책이 ENRICHMENT_BODY 청크로 인덱싱되고 RAG·가이드에 흐르는지 검증.

- [ ] **Step 1: 정책 1건 트리거**

n8n `youth-center-seoul` 워크플로우를 단일 정책(테스트용 청년 정책 한 건) 으로 실행. backend 의 `IngestionService` 가 receivePolicy → RagIndexingService.indexPolicyDocument 까지 이어지는지 ingestion log 확인.

- [ ] **Step 2: `policy_document` 테이블 확인**

```sql
SELECT chunk_index, source, attachment_id, LEFT(content, 80) AS preview
FROM policy_document
WHERE policy_id = <테스트 정책 ID>
ORDER BY chunk_index;
```

기대:
- `source = 'BODY'` 청크 N건
- `source = 'ATTACHMENT'` 청크 M건 (첨부가 있으면)
- `source = 'ENRICHMENT_BODY'` 청크 K건 (외부 페이지가 있으면 K ≥ 1)
- ENRICHMENT_BODY 청크의 `attachment_id` 는 모두 NULL

- [ ] **Step 3: RagIndexingService 로그 확인**

```
정책 인덱싱 완료: policyId=<...>, body_chunks_count=N, attachment_chunks_count=M, enrichment_body_chunks_count=K
```

위 형식의 로그가 한 줄 출력되었는지 확인.

- [ ] **Step 4: Q&A 흐름 검증**

해당 정책에 대해 Q&A 요청을 보내, RAG 검색 결과에 ENRICHMENT_BODY 청크가 top-k 안에 포함될 수 있는 질문을 던진다 (외부 페이지에만 존재하는 정보 인용 질문). 답변에 외부 페이지 발췌 인용이 자연스럽게 들어가는지 확인.

- [ ] **Step 5: 가이드 재생성 확인**

해당 정책이 CostGuard allowlist 에 있다면, `GuideGenerationService.generateGuide()` 호출 시 PROMPT_VERSION 변경으로 hash 가 달라져 재생성된다. 재생성된 GuideContent 의 `sourceField` 에 `ENRICHMENT` 라벨이 적절히 매핑되는지 확인.

- [ ] **Step 6: 운영 로그 모니터링 (24시간)**

n8n 정기 실행(보통 04:00) 후 24시간 내 ENRICHMENT_BODY 청크 통계와 Q&A 답변 품질을 모니터링. 이상 시 spec §11 의 롤백 경로(n8n 단독 롤백 → backend 단독 롤백) 적용.

- [ ] **Step 7: spec / plan DONE 마커**

검증 완료 후:

```bash
git mv docs/superpowers/specs/2026-05-15-external-page-body-chunking-design.md \
       docs/superpowers/specs/DONE_2026-05-15-external-page-body-chunking-design.md
git mv docs/superpowers/plans/2026-05-15-external-page-body-chunking.md \
       docs/superpowers/plans/DONE_2026-05-15-external-page-body-chunking.md
git commit -m "docs: external-page-body-chunking 사이클 완료 DONE 마커"
```

---

## 점진 롤아웃 (spec §11 재확인)

1. **Task 1 ~ 10 머지 (backend 사이클)** — `policy_document.source` 마이그레이션 + ENRICHMENT_BODY 청크 생성 코드 + PROMPT_VERSION v5. 단 n8n 이 cleanedText 를 안 보내고 있으므로 ENRICHMENT_BODY 청크 실제 생성은 0건.
2. **Task 11 머지 (n8n)** — cleanedText 부착 활성화. 다음 04:00 스케줄에서 자동 청크 생성 시작.
3. **Task 12 검증** — 운영 흐름 확인 후 DONE 마커.

롤백 안전성:
- backend 단독 롤백 시: `source` 컬럼 NOT NULL DEFAULT 'BODY' 가 유지되면 구버전 backend 의 INSERT 가 NOT NULL 위반 없이 동작. DEFAULT 제거 단계는 본 사이클에 포함하지 않음 (운영자가 다음 안정기 후 별도 수행).
- n8n 단독 롤백 시: cleanedText null → DocumentChunker 가 ENRICHMENT_BODY 청크 미생성 (안전).

---

## 알려진 한계 / 후속 사이클

- **E 사이클**: `enrichment.extraAttachments` 자동 다운로드 + 첨부 청킹 (본 plan 비범위).
- **외부 페이지 단독 변경 감지**: 현재 `fetchedAt` 변경 시에만 재인덱싱. 외부 페이지만 바뀌고 fetchedAt 이 그대로면 미반영 가능. 다음 사이클에서 cleanedText hash 별도 추적 후보.
- **신뢰도 가중치**: ENRICHMENT_BODY 청크에 BODY 보다 낮은 cosine distance weight 부여 — 현재 단순 거리 검색으로 충분, 운영 데이터 누적 후 검토.

---

## 의존성 다이어그램

```
Task 1 (enum)
   ↓
Task 2 (DDL + entity source)
   ↓
Task 3 (PolicyEnrichment.cleanedText)
   ↓
Task 4 (ingestion mapping)
   ↓
Task 5 (DocumentChunker.computeHash overload)
   ↓
Task 6 (DocumentChunker source 부여)
   ↓
Task 7 (chunkWithEnrichment cleanedText 분할)
   ↓
Task 8 (RagIndexingService observability)
   ↓
Task 9 (ChunkInput / combinedSourceText / computeHash)
   ↓
Task 10 (SYSTEM_PROMPT + PROMPT_VERSION)
   ↓
Task 11 (n8n cleanedText 부착)
   ↓
Task 12 (E2E 검증)
```
