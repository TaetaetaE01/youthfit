# 가이드에 enrichment 흡수 + 복지로식 섹션 구조 도입 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온통청년 enrichment 의 9개 sections 를 guide 생성의 1급 입력으로 끌어올리고, GuideContent 에 신청방법/신청기한/제출서류/문의처 4개 섹션을 추가해 복지로 정책 상세 화면 구조와 부합하는 가이드를 만든다.

**Architecture:** 백엔드 `guide` 모듈의 도메인/입력/프롬프트/검증 4축을 확장하고, 신규 `GuideListSection` record + `GuideSourceField.ENRICHMENT` 라벨을 도입한다. `GuideGenerationInput` 에 `EnrichmentInput` 절을 추가해 LLM 에게 본문과 enrichment 를 모두 노출. `PROMPT_VERSION` v4→v5 + hash 입력에 enrichment fetchedAt 포함으로 enrichment 변경만으로도 자동 재생성된다. Frontend 는 `GuideListSectionCard` 신규 컴포넌트 + 기존 `AiSourceChip` 재활용 + `PolicyDetailPage` 의 가이드 카드 묶음에 4개 NEW 카드 삽입 + 순서 재배치.

**Tech Stack:** Java 21 / Spring Boot 4.0.5 (백엔드), React 19 + TypeScript 5 + Vite 6 + Tailwind v4 (프론트), JUnit 5 / Vitest, OpenAI gpt-4o-mini (LLM).

**Spec:** `docs/superpowers/specs/2026-05-14-guide-enrichment-bokjiro-design.md`

---

## File Structure

### 백엔드 신규
- `backend/src/main/java/com/youthfit/guide/domain/model/GuideListSection.java`
- `backend/src/test/java/com/youthfit/guide/domain/model/GuideListSectionTest.java`

### 백엔드 수정
- `backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java` — `ENRICHMENT` 추가
- `backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java` — 4개 nullable 필드 추가
- `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` — `EnrichmentInput` 추가, `combinedSourceText` 직렬화 확장
- `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` — `PROMPT_VERSION` v5, `computeHash` 변경, 후처리 4섹션 처리
- `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java` — 검증 6/7 + ValidationReport 확장
- `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` — `SYSTEM_PROMPT` v5, JSON 스키마 4개 필드, `parseResponse` 4개 필드
- `backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java` — 4개 NEW 필드 + `ListSectionDto`

### 백엔드 테스트
- `backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java`
- `backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java`
- `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java`
- `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java`

### 프론트엔드 신규
- `frontend/src/components/policy/GuideListSectionCard.tsx`
- `frontend/src/components/policy/GuideListSectionCard.test.tsx`

### 프론트엔드 수정
- `frontend/src/types/policy.ts` — `GuideSourceField` 에 `ENRICHMENT`, `GuideListSection`, `Guide` 4개 NEW 필드
- `frontend/src/pages/PolicyDetailPage.tsx` — 4개 카드 추가 + 순서 재배치

---

## Task 1: `GuideSourceField` 에 `ENRICHMENT` 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java`

- [ ] **Step 1: enum 항목 추가**

```java
package com.youthfit.guide.domain.model;

public enum GuideSourceField {
    SUPPORT_TARGET,
    SELECTION_CRITERIA,
    SUPPORT_CONTENT,
    BODY,
    ATTACHMENT,
    ENRICHMENT
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java
git commit -m "feat(guide): add ENRICHMENT source field to GuideSourceField"
```

---

## Task 2: `GuideListSection` record 신설

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/GuideListSection.java`
- Create: `backend/src/test/java/com/youthfit/guide/domain/model/GuideListSectionTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideListSectionTest {

    @Test
    @DisplayName("items 가 비어있으면 생성 실패")
    void items_empty_then_fail() {
        assertThatThrownBy(() -> new GuideListSection(List.of(), GuideSourceField.ENRICHMENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    @DisplayName("items null 이면 생성 실패")
    void items_null_then_fail() {
        assertThatThrownBy(() -> new GuideListSection(null, GuideSourceField.ENRICHMENT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sourceField null 이면 생성 실패")
    void sourceField_null_then_fail() {
        assertThatThrownBy(() -> new GuideListSection(List.of("item"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceField");
    }

    @Test
    @DisplayName("items 1개 + attachmentRef null 도 정상 생성")
    void single_item_no_attachment_then_ok() {
        GuideListSection s = new GuideListSection(
                List.of("2026-03-01 ~ 2026-05-31"),
                GuideSourceField.ENRICHMENT
        );
        assertThat(s.items()).containsExactly("2026-03-01 ~ 2026-05-31");
        assertThat(s.attachmentRef()).isNull();
    }

    @Test
    @DisplayName("items 는 불변 사본")
    void items_defensive_copy() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>();
        mutable.add("a");
        GuideListSection s = new GuideListSection(mutable, GuideSourceField.ENRICHMENT, null);
        mutable.add("b");
        assertThat(s.items()).containsExactly("a");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.domain.model.GuideListSectionTest' -i`
Expected: FAIL (`GuideListSection` 클래스 없음)

- [ ] **Step 3: record 구현**

```java
package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideListSection(
        List<String> items,
        GuideSourceField sourceField,
        AttachmentRef attachmentRef
) {
    public GuideListSection {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items는 비어있을 수 없습니다");
        }
        if (sourceField == null) {
            throw new IllegalArgumentException("sourceField는 null일 수 없습니다");
        }
        items = List.copyOf(items);
    }

    public GuideListSection(List<String> items, GuideSourceField sourceField) {
        this(items, sourceField, null);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.domain.model.GuideListSectionTest'`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideListSection.java backend/src/test/java/com/youthfit/guide/domain/model/GuideListSectionTest.java
git commit -m "feat(guide): add GuideListSection record for 신청방법/신청기한/제출서류/문의처"
```

---

## Task 3: `GuideContent` 4개 nullable 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java`
- Create: `backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideContentTest {

    @Test
    @DisplayName("4개 NEW 섹션 모두 null 로 생성 가능")
    void new_sections_all_nullable() {
        GuideContent c = new GuideContent(
                "한 줄 요약",
                List.of(highlight("h1")),
                paired("g1", "i1"),
                paired("g2", "i2"),
                paired("g3", "i3"),
                null,  // applyMethod
                null,  // deadlineNote
                null,  // requiredDocuments
                null,  // contact
                List.of(pitfall("p1"))
        );
        assertThat(c.applyMethod()).isNull();
        assertThat(c.deadlineNote()).isNull();
        assertThat(c.requiredDocuments()).isNull();
        assertThat(c.contact()).isNull();
    }

    @Test
    @DisplayName("4개 NEW 섹션 모두 채워서 생성")
    void new_sections_all_present() {
        GuideListSection apply = new GuideListSection(List.of("회원가입", "신청서 작성"), GuideSourceField.ENRICHMENT);
        GuideListSection deadline = new GuideListSection(List.of("2026-03-01 ~ 2026-05-31"), GuideSourceField.ENRICHMENT);
        GuideListSection docs = new GuideListSection(List.of("등본"), GuideSourceField.ENRICHMENT);
        GuideListSection contact = new GuideListSection(List.of("02-2133-6586"), GuideSourceField.SUPPORT_CONTENT);

        GuideContent c = new GuideContent(
                "한 줄 요약",
                List.of(highlight("h1")),
                paired("g1", "i1"),
                paired("g2", "i2"),
                paired("g3", "i3"),
                apply, deadline, docs, contact,
                List.of(pitfall("p1"))
        );
        assertThat(c.applyMethod().items()).containsExactly("회원가입", "신청서 작성");
        assertThat(c.deadlineNote().items()).containsExactly("2026-03-01 ~ 2026-05-31");
        assertThat(c.requiredDocuments().items()).containsExactly("등본");
        assertThat(c.contact().sourceField()).isEqualTo(GuideSourceField.SUPPORT_CONTENT);
    }

    private GuideHighlight highlight(String text) {
        return new GuideHighlight(text, GuideSourceField.BODY, null);
    }

    private GuidePitfall pitfall(String text) {
        return new GuidePitfall(text, GuideSourceField.BODY, null);
    }

    private GuidePairedSection paired(String label, String item) {
        return new GuidePairedSection(List.of(new GuideGroup(label, List.of(item))));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.domain.model.GuideContentTest' -i`
Expected: FAIL (`GuideContent` 생성자 시그니처 불일치)

- [ ] **Step 3: GuideContent 확장**

```java
package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideContent(
        String oneLineSummary,
        List<GuideHighlight> highlights,
        GuidePairedSection target,
        GuidePairedSection criteria,
        GuidePairedSection content,
        GuideListSection applyMethod,
        GuideListSection deadlineNote,
        GuideListSection requiredDocuments,
        GuideListSection contact,
        List<GuidePitfall> pitfalls) {

    public GuideContent {
        if (oneLineSummary == null || oneLineSummary.isBlank()) {
            throw new IllegalArgumentException("oneLineSummary는 비어있을 수 없습니다");
        }
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
    }
}
```

- [ ] **Step 4: 컴파일 에러 부수 수정**

`GuideContent` 시그니처 변경으로 다음 호출 위치를 모두 새 시그니처에 맞게 수정. 4개 NEW 섹션은 일단 `null` 로.

검색 명령: `cd backend && grep -rn 'new GuideContent(' src/main src/test`

수정 위치 (예상):
- `GuideGenerationService.enforceAttachmentSourceField` — 기존 6-arg `new GuideContent(c.oneLineSummary(), hs, c.target(), c.criteria(), c.content(), ps)` → 10-arg `new GuideContent(c.oneLineSummary(), hs, c.target(), c.criteria(), c.content(), c.applyMethod(), c.deadlineNote(), c.requiredDocuments(), c.contact(), ps)`
- `GuideGenerationService.filterInvalidSourceFields` — 동일 패턴 수정
- `OpenAiChatClient.parseResponse` — `return new GuideContent(oneLine, highlights, target, criteria, content, pitfalls);` → 4개 NEW 섹션은 `null` 자리 (Task 7 에서 실제 파싱 추가)
- 기존 테스트 코드 (`*Test.java`) 중 `new GuideContent(` 호출하는 곳들 — 4개 NEW 섹션을 모두 `null` 로 추가

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.domain.model.GuideContentTest'`
Expected: PASS (2 tests)

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.*'`
Expected: 기존 guide 테스트도 전부 통과 (4개 NEW 섹션은 null 자리로 새 시그니처 호환)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java
# + 기존 테스트 시그니처 수정 파일들
git commit -m "feat(guide): extend GuideContent with 4 bokjiro-style sections (applyMethod/deadlineNote/requiredDocuments/contact)"
```

---

## Task 4: `GuideGenerationInput.EnrichmentInput` 추가 + `combinedSourceText` 직렬화

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`
- Create: `backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.domain.model.PolicyEnrichment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationInputTest {

    @Test
    @DisplayName("enrichment 없으면 combinedSourceText 에 [enrichment.*] 절 미포함")
    void enrichment_null_no_section() {
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "title", 2026, "summary", "body",
                "supportTarget", "criteria", "content", "contact", "org",
                null,                    // enrichment
                List.of(), null
        );
        String text = input.combinedSourceText();
        assertThat(text).doesNotContain("[enrichment");
    }

    @Test
    @DisplayName("enrichment 있으면 9개 절 모두 직렬화")
    void enrichment_present_all_sections_serialized() {
        GuideGenerationInput.EnrichmentInput enrichment = new GuideGenerationInput.EnrichmentInput(
                "AI 추출 지원대상",
                "AI 추출 지원내용",
                "1. 회원가입 2. 신청서 작성",
                "주민등록등본, 임대차계약서",
                "2026-03-01 ~ 2026-05-31",
                "정책 개요",
                "선정기준 풀이",
                "서울특별시 청년정책담당관",
                "02-2133-6586",
                Instant.parse("2026-05-12T04:12:33Z"),
                "https://www.youthcenter.go.kr/...",
                0.82
        );
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "title", 2026, "summary", "body",
                null, null, null, null, null,
                enrichment,
                List.of(), null
        );
        String text = input.combinedSourceText();
        assertThat(text)
                .contains("[enrichment.meta]")
                .contains("[enrichment.policyOverview]")
                .contains("[enrichment.supportTarget]")
                .contains("[enrichment.eligibilityCriteria]")
                .contains("[enrichment.supportContent]")
                .contains("[enrichment.applyMethod]")
                .contains("[enrichment.requiredDocuments]")
                .contains("[enrichment.deadlineNote]")
                .contains("[enrichment.operatingOrganization]")
                .contains("[enrichment.contactPhone]")
                .contains("0.82")
                .contains("2026-05-12T04:12:33Z");
    }

    @Test
    @DisplayName("enrichment 일부 sections 가 null 이면 해당 절 미포함")
    void enrichment_partial_sections() {
        GuideGenerationInput.EnrichmentInput enrichment = new GuideGenerationInput.EnrichmentInput(
                null,           // supportTarget null
                "지원내용",
                null,           // applyMethod null
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-12T04:12:33Z"),
                "https://...",
                0.7
        );
        GuideGenerationInput input = new GuideGenerationInput(
                1L, "t", null, null, null, null, null, null, null, null,
                enrichment, List.of(), null
        );
        String text = input.combinedSourceText();
        assertThat(text).contains("[enrichment.supportContent]");
        assertThat(text).doesNotContain("[enrichment.supportTarget]");
        assertThat(text).doesNotContain("[enrichment.applyMethod]");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.application.dto.command.GuideGenerationInputTest' -i`
Expected: FAIL (`EnrichmentInput` 미존재)

- [ ] **Step 3: GuideGenerationInput 확장**

```java
package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.rag.domain.model.PolicyDocument;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

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
        EnrichmentInput enrichment,
        List<ChunkInput> chunks,
        IncomeBracketReference referenceData
) {

    public GuideGenerationInput {
        if (policyId == null) {
            throw new IllegalArgumentException("policyId는 null일 수 없습니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어있을 수 없습니다");
        }
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public record EnrichmentInput(
            String supportTarget,
            String supportContent,
            String applyMethod,
            String requiredDocuments,
            String deadlineNote,
            String policyOverview,
            String eligibilityCriteria,
            String operatingOrganization,
            String contactPhone,
            Instant fetchedAt,
            String sourceUrl,
            Double confidence
    ) {}

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference referenceData) {
        EnrichmentInput enrichmentInput = null;
        if (policy.getEnrichment() != null && policy.getEnrichment().isExposable()) {
            PolicyEnrichment e = policy.getEnrichment();
            PolicyEnrichment.Sections s = e.sections();
            enrichmentInput = new EnrichmentInput(
                    s.supportTarget(),
                    s.supportContent(),
                    s.applyMethod(),
                    s.requiredDocuments(),
                    s.deadlineNote(),
                    s.policyOverview(),
                    s.eligibilityCriteria(),
                    s.operatingOrganization(),
                    s.contactPhone(),
                    e.fetchedAt(),
                    e.sourceUrl(),
                    e.confidence()
            );
        }

        List<ChunkInput> chunkInputs = chunks == null
                ? List.of()
                : chunks.stream()
                        .map(d -> new ChunkInput(
                                d.getContent(),
                                d.getAttachmentId(),
                                d.getPageStart(),
                                d.getPageEnd()))
                        .collect(Collectors.toList());

        return new GuideGenerationInput(
                policy.getId(),
                policy.getTitle(),
                policy.getReferenceYear(),
                policy.getSummary(),
                policy.getBody(),
                policy.getSupportTarget(),
                policy.getSelectionCriteria(),
                policy.getSupportContent(),
                policy.getContact(),
                policy.getOrganization(),
                enrichmentInput,
                chunkInputs,
                referenceData
        );
    }

    public String combinedSourceText() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "summary", summary);
        appendSection(sb, "body", body);
        appendSection(sb, "supportTarget", supportTarget);
        appendSection(sb, "selectionCriteria", selectionCriteria);
        appendSection(sb, "supportContent", supportContent);

        if (enrichment != null) {
            sb.append("[enrichment.meta]\n");
            if (enrichment.sourceUrl() != null) sb.append("sourceUrl=").append(enrichment.sourceUrl()).append("\n");
            if (enrichment.fetchedAt() != null) sb.append("fetchedAt=").append(enrichment.fetchedAt()).append("\n");
            if (enrichment.confidence() != null) sb.append("confidence=").append(enrichment.confidence()).append("\n");
            sb.append("(LLM 은 이 메타 절을 출력 생성에 직접 인용하지 않는다. 출처 라벨 참고 정보로만 활용)\n\n");

            appendSection(sb, "enrichment.policyOverview", enrichment.policyOverview());
            appendSection(sb, "enrichment.supportTarget", enrichment.supportTarget());
            appendSection(sb, "enrichment.eligibilityCriteria", enrichment.eligibilityCriteria());
            appendSection(sb, "enrichment.supportContent", enrichment.supportContent());
            appendSection(sb, "enrichment.applyMethod", enrichment.applyMethod());
            appendSection(sb, "enrichment.requiredDocuments", enrichment.requiredDocuments());
            appendSection(sb, "enrichment.deadlineNote", enrichment.deadlineNote());
            appendSection(sb, "enrichment.operatingOrganization", enrichment.operatingOrganization());
            appendSection(sb, "enrichment.contactPhone", enrichment.contactPhone());
        }

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
                    sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
                }
                sb.append("]\n");
            }
            sb.append(c.content()).append("\n\n");
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("[").append(key).append("]\n").append(value).append("\n\n");
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.application.dto.command.GuideGenerationInputTest'`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java
git commit -m "feat(guide): inject enrichment.sections into GuideGenerationInput as first-class input"
```

---

## Task 5: `OpenAiChatClient.buildResponseFormat` JSON 스키마 확장 + `parseResponse` 4개 필드 파싱

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`

- [ ] **Step 1: JSON 스키마 확장**

`buildResponseFormat()` 메서드를 다음으로 교체:

```java
private Map<String, Object> buildResponseFormat() {
    Map<String, Object> groupSchema = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("label", "items"),
            "properties", Map.of(
                    "label", Map.of("anyOf", List.of(
                            Map.of("type", "string"),
                            Map.of("type", "null")
                    )),
                    "items", Map.of("type", "array", "items", Map.of("type", "string"))
            )
    );

    Map<String, Object> pairedSchema = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("groups"),
            "properties", Map.of(
                    "groups", Map.of("type", "array", "items", groupSchema)
            )
    );

    Map<String, Object> attachmentRefSchema = Map.of(
            "anyOf", List.of(
                    Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of("attachmentId", "pageStart", "pageEnd"),
                            "properties", Map.of(
                                    "attachmentId", Map.of("type", "integer"),
                                    "pageStart", Map.of("anyOf", List.of(
                                            Map.of("type", "integer"),
                                            Map.of("type", "null")
                                    )),
                                    "pageEnd", Map.of("anyOf", List.of(
                                            Map.of("type", "integer"),
                                            Map.of("type", "null")
                                    ))
                            )
                    ),
                    Map.of("type", "null")
            )
    );

    List<String> sourceFieldEnum = List.of(
            "SUPPORT_TARGET", "SELECTION_CRITERIA", "SUPPORT_CONTENT",
            "BODY", "ATTACHMENT", "ENRICHMENT"
    );

    Map<String, Object> pitfallSchema = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("text", "sourceField", "attachmentRef"),
            "properties", Map.of(
                    "text", Map.of("type", "string"),
                    "sourceField", Map.of("type", "string", "enum", sourceFieldEnum),
                    "attachmentRef", attachmentRefSchema
            )
    );

    Map<String, Object> listSectionSchema = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("items", "sourceField", "attachmentRef"),
            "properties", Map.of(
                    "items", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1),
                    "sourceField", Map.of("type", "string", "enum", sourceFieldEnum),
                    "attachmentRef", attachmentRefSchema
            )
    );

    Map<String, Object> nullableListSection = Map.of(
            "anyOf", List.of(listSectionSchema, Map.of("type", "null"))
    );

    Map<String, Object> schema = new java.util.LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("required", List.of(
            "oneLineSummary", "highlights", "target", "criteria", "content",
            "applyMethod", "deadlineNote", "requiredDocuments", "contact", "pitfalls"
    ));
    java.util.LinkedHashMap<String, Object> properties = new java.util.LinkedHashMap<>();
    properties.put("oneLineSummary", Map.of("type", "string"));
    properties.put("highlights", Map.of("type", "array", "items", pitfallSchema));
    properties.put("target", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))));
    properties.put("criteria", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))));
    properties.put("content", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))));
    properties.put("applyMethod", nullableListSection);
    properties.put("deadlineNote", nullableListSection);
    properties.put("requiredDocuments", nullableListSection);
    properties.put("contact", nullableListSection);
    properties.put("pitfalls", Map.of("type", "array", "items", pitfallSchema));
    schema.put("properties", properties);

    return Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                    "name", "guide_content",
                    "strict", true,
                    "schema", schema
            )
    );
}
```

`Map.of` 가 10 entry 한도에 막히므로 `LinkedHashMap` 으로 교체. 동작 의도는 동일하되 OpenAI strict 모드에서 키 순서 보존.

- [ ] **Step 2: `parseResponse` 에 4개 NEW 필드 파싱 추가**

`parseResponse` 와 신규 헬퍼 메서드를 추가:

```java
GuideContent parseResponse(String json) {
    try {
        JsonNode node = objectMapper.readTree(json);
        String oneLine = node.get("oneLineSummary").asText();
        List<GuideHighlight> highlights = parseHighlights(node.get("highlights"));
        GuidePairedSection target = parsePaired(node.get("target"));
        GuidePairedSection criteria = parsePaired(node.get("criteria"));
        GuidePairedSection content = parsePaired(node.get("content"));
        GuideListSection applyMethod = parseListSection(node.get("applyMethod"));
        GuideListSection deadlineNote = parseListSection(node.get("deadlineNote"));
        GuideListSection requiredDocuments = parseListSection(node.get("requiredDocuments"));
        GuideListSection contact = parseListSection(node.get("contact"));
        List<GuidePitfall> pitfalls = parsePitfalls(node.get("pitfalls"));
        return new GuideContent(
                oneLine, highlights, target, criteria, content,
                applyMethod, deadlineNote, requiredDocuments, contact,
                pitfalls
        );
    } catch (Exception e) {
        log.error("가이드 응답 JSON 파싱 실패: {}", json, e);
        throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 응답 파싱 실패");
    }
}

private GuideListSection parseListSection(JsonNode node) {
    if (node == null || node.isNull()) return null;
    JsonNode itemsNode = node.get("items");
    if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) return null;
    List<String> items = new ArrayList<>();
    itemsNode.forEach(n -> items.add(n.asText()));

    JsonNode sourceFieldNode = node.get("sourceField");
    if (sourceFieldNode == null || sourceFieldNode.isNull()) return null;
    GuideSourceField sourceField;
    try {
        sourceField = GuideSourceField.valueOf(sourceFieldNode.asText());
    } catch (IllegalArgumentException e) {
        log.warn("invalid sourceField in list section: {}", sourceFieldNode.asText());
        return null;
    }

    com.youthfit.guide.domain.model.AttachmentRef ref = parseAttachmentRef(node.get("attachmentRef"));
    try {
        return new GuideListSection(items, sourceField, ref);
    } catch (IllegalArgumentException e) {
        log.warn("invalid GuideListSection from LLM: items={} sourceField={} ref={}, msg={}",
                items, sourceField, ref, e.getMessage());
        return null;
    }
}
```

상단 import 추가: `import com.youthfit.guide.domain.model.GuideListSection;`

- [ ] **Step 3: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java
git commit -m "feat(guide): extend OpenAI JSON schema/parser with 4 new sections and ENRICHMENT enum"
```

---

## Task 6: `OpenAiChatClient.SYSTEM_PROMPT` v5 갱신

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`

- [ ] **Step 1: 시스템 프롬프트의 `[출력 단위 — JSON]` 블록과 그 이후 변환 예시 블록 갱신**

기존 `[출력 단위 — JSON]` 블록을 다음으로 교체 (들여쓰기 그대로 유지):

```
[출력 단위 — JSON]
- oneLineSummary: 정책 정체를 누가/무엇을/얼마나 받는지 1~2문장.
- highlights: 사용자가 PDF를 보지 않고도 정책의 핵심 특징을 파악할 수 있는 항목 3~6개.
  혜택의 강도, 차별점, 신청 시점/방법의 특이사항, 우대조건, 중복 수혜 가능 여부 등 긍정·중립 정보.
  각 항목 sourceField 라벨 필수 (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY / ATTACHMENT / ENRICHMENT).
- target / criteria / content: 각각 supportTarget / selectionCriteria / supportContent 풀이 (groups 배열).
  입력이 비어있으면 null. groups 구조는 아래 [변환 예시] 참조.
- applyMethod / deadlineNote / requiredDocuments / contact: 복지로식 정형 섹션.
  각각 신청방법 / 신청기한 / 제출서류 / 문의처. 각 항목은 { "items": [...], "sourceField": "...", "attachmentRef": null|... } 형태.
  본문과 enrichment 양쪽에 정보가 없으면 null. items 가 비어있는 형태로 만들지 말 것.
  contact 는 operatingOrganization + contactPhone 을 같은 items 리스트에 줄 단위로 배치.
  deadlineNote.items 는 가능하면 "YYYY-MM-DD ~ YYYY-MM-DD" 또는 사람이 읽는 형태("상시", "예산 소진 시까지").
- pitfalls: 부정·함정·예외·제외 조건만 (자격 미달 트리거, 중복 수혜 제한, 사후 의무, 신청기한 외).
  긍정·중립 정보는 highlights로 보낸다. 각 항목 sourceField 라벨 필수.

[ENRICHMENT 라벨 사용 규칙]
- [enrichment.*] 절은 외부 정책 안내 페이지에서 AI 가 자동 추출한 보조 정보다.
- 본문 절([body], [supportTarget], [selectionCriteria], [supportContent]) 에 같거나 유사한 정보가 있으면
  본문의 sourceField (BODY / SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT) 를 **우선** 사용한다.
- 본문에 없고 [enrichment.*] 절에만 있는 정보만 sourceField=ENRICHMENT 로 라벨링한다.
- [enrichment.eligibilityCriteria] 는 본문의 [selectionCriteria] 와 동의어로 취급. criteria 섹션 입력으로 활용.
- [enrichment.policyOverview] 는 별도 출력 섹션을 만들지 말고, oneLineSummary / highlights 작성 시 보조 입력으로만 활용.
- [enrichment.meta] 는 신뢰도/출처 참고 정보다. 본문 인용에 직접 사용하지 않는다.
```

기존 `[변환 예시 6] 첨부 trace:` 다음에 두 예시를 추가:

```
[변환 예시 7] enrichment 흡수 (온통청년 정책, 본문 빈약):
입력:
  [supportTarget]
  (빈 값 또는 짧은 요약)

  [enrichment.supportTarget]
  서울특별시 거주 만 19~34세 청년 무주택자

  [enrichment.applyMethod]
  1) 온통청년 홈페이지 회원가입
  2) 신청서 작성 후 첨부서류 업로드
  3) 자치구 청년정책팀 심사 후 결과 통보

  [enrichment.requiredDocuments]
  주민등록등본, 최근 3개월 건강보험 자격득실확인서, 임대차계약서 사본

  [enrichment.deadlineNote]
  2026-03-01 ~ 2026-05-31

  [enrichment.operatingOrganization]
  서울특별시 청년정책담당관

  [enrichment.contactPhone]
  02-2133-6586

→ 출력 일부:
  "target": {
    "groups": [
      { "label": null,
        "items": [
          "서울특별시 거주 중인 만 19~34세 청년",
          "본인 명의의 집을 소유하지 않은 무주택자"
        ] }
    ]
  },
  "applyMethod": {
    "items": [
      "온통청년 홈페이지에서 회원가입한다",
      "신청서를 작성하고 첨부서류를 업로드한다",
      "자치구 청년정책팀 심사 후 결과 통보를 받는다"
    ],
    "sourceField": "ENRICHMENT",
    "attachmentRef": null
  },
  "deadlineNote": {
    "items": ["2026-03-01 ~ 2026-05-31"],
    "sourceField": "ENRICHMENT",
    "attachmentRef": null
  },
  "requiredDocuments": {
    "items": ["주민등록등본", "최근 3개월 건강보험 자격득실확인서", "임대차계약서 사본"],
    "sourceField": "ENRICHMENT",
    "attachmentRef": null
  },
  "contact": {
    "items": ["서울특별시 청년정책담당관", "02-2133-6586"],
    "sourceField": "ENRICHMENT",
    "attachmentRef": null
  }

[변환 예시 8] 본문 우선, enrichment 무시:
입력:
  [contact]
  서울특별시 청년정책담당관 02-2133-6586 youth@seoul.go.kr

  [enrichment.operatingOrganization]
  서울특별시 청년정책담당관

  [enrichment.contactPhone]
  02-2133-6586

→ 출력:
  "contact": {
    "items": ["서울특별시 청년정책담당관 02-2133-6586", "youth@seoul.go.kr"],
    "sourceField": "SUPPORT_CONTENT",
    "attachmentRef": null
  }
(본문에 동일 정보가 있으면 enrichment 가 아닌 본문 라벨 사용)
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java
git commit -m "feat(guide): update SYSTEM_PROMPT to v5 with 4 new sections and ENRICHMENT rules"
```

---

## Task 7: `GuideGenerationService` PROMPT_VERSION v5 + computeHash + 후처리 4섹션

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Create: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성 (computeHash 동작 검증)**

```java
package com.youthfit.guide.application.service;

import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationServiceTest {

    @Test
    @DisplayName("enrichment fetchedAt 이 다른 정책은 hash 가 다르다")
    void hash_differs_with_enrichment_fetchedAt() {
        Policy p1 = policyWithEnrichment(Instant.parse("2026-05-12T04:00:00Z"));
        Policy p2 = policyWithEnrichment(Instant.parse("2026-05-13T04:00:00Z"));

        String h1 = GuideGenerationService.computeHashForTest(p1, List.of(), null);
        String h2 = GuideGenerationService.computeHashForTest(p2, List.of(), null);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("enrichment 가 같으면 hash 가 같다")
    void hash_same_with_same_enrichment() {
        Policy p1 = policyWithEnrichment(Instant.parse("2026-05-12T04:00:00Z"));
        Policy p2 = policyWithEnrichment(Instant.parse("2026-05-12T04:00:00Z"));

        String h1 = GuideGenerationService.computeHashForTest(p1, List.of(), null);
        String h2 = GuideGenerationService.computeHashForTest(p2, List.of(), null);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("enrichment isExposable 가 false 면 hash 에 enrichment 미포함")
    void hash_no_enrichment_if_not_exposable() {
        // confidence < 0.6
        Policy p1 = Policy.builder()
                .title("t")
                .summary("s")
                .body("b")
                .enrichment(new PolicyEnrichment(
                        "https://...",
                        Instant.parse("2026-05-12T04:00:00Z"),
                        "openai:gpt-4o-mini",
                        0.3,
                        EnrichmentStatus.LOW_CONFIDENCE,
                        null, List.of()))
                .build();
        Policy p2 = Policy.builder()
                .title("t")
                .summary("s")
                .body("b")
                .enrichment(null)
                .build();

        String h1 = GuideGenerationService.computeHashForTest(p1, List.of(), null);
        String h2 = GuideGenerationService.computeHashForTest(p2, List.of(), null);

        assertThat(h1).isEqualTo(h2);
    }

    private Policy policyWithEnrichment(Instant fetchedAt) {
        return Policy.builder()
                .title("t")
                .summary("s")
                .body("b")
                .enrichment(new PolicyEnrichment(
                        "https://...",
                        fetchedAt,
                        "openai:gpt-4o-mini",
                        0.82,
                        EnrichmentStatus.OK,
                        new PolicyEnrichment.Sections(
                                "AI 지원대상", null, null, null, null, null, null, null, null),
                        List.of()))
                .build();
    }
}
```

> Note: `Policy.builder()` 사용 가능 여부와 정확한 빌더 시그니처는 `Policy.java` 에서 먼저 확인. 빌더가 없거나 다르면 테스트 헬퍼에서 적절히 인스턴스 생성 방식 변경.

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.application.service.GuideGenerationServiceTest' -i`
Expected: FAIL (enrichment 가 hash 에 포함되지 않음)

- [ ] **Step 3: GuideGenerationService 변경 — PROMPT_VERSION v5, computeHash, 후처리**

`PROMPT_VERSION` 상수와 `computeHash` 갱신:

```java
static final String PROMPT_VERSION = "v5";  // 프롬프트 / 스키마 변경 시 증분
static final String ANNOTATOR_VERSION = "v5";

private String computeHash(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
    StringBuilder sb = new StringBuilder();
    sb.append(safe(policy.getTitle()));
    sb.append(safe(policy.getSummary()));
    sb.append(safe(policy.getBody()));
    sb.append(safe(policy.getSupportTarget()));
    sb.append(safe(policy.getSelectionCriteria()));
    sb.append(safe(policy.getSupportContent()));
    sb.append(policy.getReferenceYear());
    chunks.forEach(c -> sb.append(c.getContent()));
    if (reference != null) {
        sb.append("|ref:").append(reference.year()).append(":").append(reference.version());
    }
    // confidence 는 hash 에 넣지 않는다 — 동일 enrichment 재추출에서 미세한 confidence 변동만으로
    // 매번 재생성되는 것을 막기 위함. enrichment 재추출 시 fetchedAt 이 항상 갱신되므로
    // 변경 감지엔 fetchedAt 만으로 충분.
    if (policy.getEnrichment() != null && policy.getEnrichment().isExposable()) {
        sb.append("|enrich:")
          .append(policy.getEnrichment().fetchedAt() == null
                  ? ""
                  : policy.getEnrichment().fetchedAt().toString());
    }
    sb.append("|prompt:").append(PROMPT_VERSION);
    sb.append("|annotator:").append(ANNOTATOR_VERSION);
    return sha256(sb.toString());
}
```

`enforceAttachmentSourceField` 에 4개 NEW 섹션 처리 추가:

```java
private GuideContent enforceAttachmentSourceField(GuideContent c) {
    List<GuideHighlight> hs = c.highlights().stream()
            .map(h -> h.attachmentRef() != null && h.sourceField() != GuideSourceField.ATTACHMENT
                    ? new GuideHighlight(h.text(), GuideSourceField.ATTACHMENT, h.attachmentRef())
                    : h)
            .toList();
    List<GuidePitfall> ps = c.pitfalls().stream()
            .map(p -> p.attachmentRef() != null && p.sourceField() != GuideSourceField.ATTACHMENT
                    ? new GuidePitfall(p.text(), GuideSourceField.ATTACHMENT, p.attachmentRef())
                    : p)
            .toList();
    return new GuideContent(
            c.oneLineSummary(), hs, c.target(), c.criteria(), c.content(),
            enforceListSectionAttachment(c.applyMethod()),
            enforceListSectionAttachment(c.deadlineNote()),
            enforceListSectionAttachment(c.requiredDocuments()),
            enforceListSectionAttachment(c.contact()),
            ps
    );
}

private GuideListSection enforceListSectionAttachment(GuideListSection s) {
    if (s == null) return null;
    if (s.attachmentRef() != null && s.sourceField() != GuideSourceField.ATTACHMENT) {
        return new GuideListSection(s.items(), GuideSourceField.ATTACHMENT, s.attachmentRef());
    }
    return s;
}
```

`filterInvalidSourceFields` 에 ENRICHMENT 허용 + 4개 섹션 처리:

```java
private GuideContent filterInvalidSourceFields(GuideContent c, Policy p) {
    Set<GuideSourceField> nonEmpty = new HashSet<>();
    if (notBlank(p.getSupportTarget())) nonEmpty.add(GuideSourceField.SUPPORT_TARGET);
    if (notBlank(p.getSelectionCriteria())) nonEmpty.add(GuideSourceField.SELECTION_CRITERIA);
    if (notBlank(p.getSupportContent())) nonEmpty.add(GuideSourceField.SUPPORT_CONTENT);
    if (notBlank(p.getBody())) nonEmpty.add(GuideSourceField.BODY);
    if (p.getAttachments() != null && !p.getAttachments().isEmpty()) {
        nonEmpty.add(GuideSourceField.ATTACHMENT);
    }
    if (p.getEnrichment() != null && p.getEnrichment().isExposable()) {
        nonEmpty.add(GuideSourceField.ENRICHMENT);
    }

    List<GuideHighlight> hs = guideValidator.filterInvalidSourceFields(
            c.highlights(), nonEmpty, GuideHighlight::sourceField);
    List<GuidePitfall> ps = guideValidator.filterInvalidSourceFields(
            c.pitfalls(), nonEmpty, GuidePitfall::sourceField);

    return new GuideContent(
            c.oneLineSummary(), hs, c.target(), c.criteria(), c.content(),
            filterListSection(c.applyMethod(), nonEmpty),
            filterListSection(c.deadlineNote(), nonEmpty),
            filterListSection(c.requiredDocuments(), nonEmpty),
            filterListSection(c.contact(), nonEmpty),
            ps
    );
}

private GuideListSection filterListSection(GuideListSection s, Set<GuideSourceField> nonEmpty) {
    if (s == null) return null;
    return nonEmpty.contains(s.sourceField()) ? s : null;
}
```

상단 import 추가: `import com.youthfit.guide.domain.model.GuideListSection;`

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.application.service.GuideGenerationServiceTest'`
Expected: PASS (3 tests)

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.*'`
Expected: 기존 guide 테스트도 모두 통과

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java
git commit -m "feat(guide): bump PROMPT_VERSION to v5 + enrichment fetchedAt in hash + 4 sections post-processing"
```

---

## Task 8: `GuideValidator` 검증 6/7 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java`
- Create: `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java`

> 검증 6: 4개 NEW 섹션의 sourceField 가 입력 절에 존재하지 않으면 폐기 (재시도 트리거 X).
> 검증 7: 4개 NEW 섹션의 items 빈 리스트 검사는 record 가 이미 거부하므로 추가 가드 (parser 가 빈 리스트면 null 반환). 따라서 `GuideValidator` 단에선 추가 코드 불요. parser 에서 이미 처리됨 (Task 5 의 `parseListSection`).

> 검증 6 의 실제 적용은 `GuideGenerationService.filterInvalidSourceFields` 에서 이미 4개 섹션 처리를 했으므로 (Task 7) — `GuideValidator` 자체에는 별도 보고 항목 없음. 다만 `ValidationReport` 의 hash 변경 영향이 없음을 회귀 테스트로 확인.

- [ ] **Step 1: GuideValidator 회귀 테스트 작성**

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.AttachmentRef;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuideListSection;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GuideValidatorTest {

    private final GuideValidator validator = new GuideValidator();

    @Test
    @DisplayName("4개 NEW 섹션이 있어도 기존 검증 1~3 동작은 동일")
    void existing_validations_unchanged_with_new_sections() {
        GuideContent content = new GuideContent(
                "한 줄 요약",
                List.of(
                        new GuideHighlight("h1", GuideSourceField.BODY, null),
                        new GuideHighlight("h2", GuideSourceField.BODY, null),
                        new GuideHighlight("h3", GuideSourceField.BODY, null)
                ),
                paired("g1", "i1"),
                paired("g2", "i2"),
                paired("g3", "i3"),
                new GuideListSection(List.of("step1"), GuideSourceField.ENRICHMENT),
                new GuideListSection(List.of("상시"), GuideSourceField.ENRICHMENT),
                null,
                null,
                List.of(new GuidePitfall("p1", GuideSourceField.BODY, null))
        );

        GuideValidator.ValidationReport report = validator.validate(content, Set.of());

        assertThat(report.hasRetryTrigger()).isFalse();
        assertThat(report.violationCount()).isZero();
    }

    @Test
    @DisplayName("filterInvalidSourceFields 는 ENRICHMENT 라벨도 정상 통과시킨다")
    void filter_allows_enrichment_when_in_whitelist() {
        List<GuideHighlight> input = List.of(
                new GuideHighlight("h1", GuideSourceField.ENRICHMENT, null),
                new GuideHighlight("h2", GuideSourceField.BODY, null)
        );

        Set<GuideSourceField> whitelist = Set.of(GuideSourceField.BODY, GuideSourceField.ENRICHMENT);
        List<GuideHighlight> filtered = validator.filterInvalidSourceFields(
                input, whitelist, GuideHighlight::sourceField);

        assertThat(filtered).hasSize(2);
    }

    @Test
    @DisplayName("filterInvalidSourceFields 는 화이트리스트 외 ENRICHMENT 를 폐기한다")
    void filter_drops_enrichment_when_not_in_whitelist() {
        List<GuideHighlight> input = List.of(
                new GuideHighlight("h1", GuideSourceField.ENRICHMENT, null),
                new GuideHighlight("h2", GuideSourceField.BODY, null)
        );

        Set<GuideSourceField> whitelist = Set.of(GuideSourceField.BODY);
        List<GuideHighlight> filtered = validator.filterInvalidSourceFields(
                input, whitelist, GuideHighlight::sourceField);

        assertThat(filtered).extracting(GuideHighlight::sourceField).containsExactly(GuideSourceField.BODY);
    }

    private GuidePairedSection paired(String label, String item) {
        return new GuidePairedSection(List.of(new GuideGroup(label, List.of(item))));
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.application.service.GuideValidatorTest'`
Expected: PASS (3 tests) — GuideValidator 자체는 변경 없이 기존 동작 그대로

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java
git commit -m "test(guide): verify GuideValidator handles 4 new sections and ENRICHMENT label"
```

---

## Task 9: `GuideResponse` DTO 4개 NEW 필드 직렬화

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java`

- [ ] **Step 1: GuideResponse 확장**

```java
package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideListSection;
import com.youthfit.guide.domain.model.GuidePairedSection;

import java.time.LocalDateTime;
import java.util.List;

public record GuideResponse(
        Long policyId,
        String oneLineSummary,
        List<HighlightDto> highlights,
        PairedDto target,
        PairedDto criteria,
        PairedDto content,
        ListSectionDto applyMethod,
        ListSectionDto deadlineNote,
        ListSectionDto requiredDocuments,
        ListSectionDto contact,
        List<PitfallDto> pitfalls,
        LocalDateTime updatedAt
) {

    public record PairedDto(List<GroupDto> groups) {}

    public record GroupDto(String label, List<String> items) {}

    public record AttachmentRefDto(Long attachmentId, Integer pageStart, Integer pageEnd) {
        public static AttachmentRefDto from(com.youthfit.guide.domain.model.AttachmentRef r) {
            return r == null ? null
                    : new AttachmentRefDto(r.attachmentId(), r.pageStart(), r.pageEnd());
        }
    }

    public record PitfallDto(String text, String sourceField, AttachmentRefDto attachmentRef) {}

    public record HighlightDto(String text, String sourceField, AttachmentRefDto attachmentRef) {}

    public record ListSectionDto(
            List<String> items,
            String sourceField,
            AttachmentRefDto attachmentRef
    ) {}

    public static GuideResponse from(GuideResult result) {
        GuideContent c = result.content();
        return new GuideResponse(
                result.policyId(),
                c.oneLineSummary(),
                c.highlights().stream()
                        .map(h -> new HighlightDto(h.text(), h.sourceField().name(),
                                AttachmentRefDto.from(h.attachmentRef())))
                        .toList(),
                toPairedDto(c.target()),
                toPairedDto(c.criteria()),
                toPairedDto(c.content()),
                toListSectionDto(c.applyMethod()),
                toListSectionDto(c.deadlineNote()),
                toListSectionDto(c.requiredDocuments()),
                toListSectionDto(c.contact()),
                c.pitfalls().stream()
                        .map(p -> new PitfallDto(p.text(), p.sourceField().name(),
                                AttachmentRefDto.from(p.attachmentRef())))
                        .toList(),
                result.updatedAt()
        );
    }

    private static PairedDto toPairedDto(GuidePairedSection section) {
        if (section == null) return null;
        List<GroupDto> groups = section.groups().stream()
                .map(g -> new GroupDto(g.label(), g.items()))
                .toList();
        return new PairedDto(groups);
    }

    private static ListSectionDto toListSectionDto(GuideListSection section) {
        if (section == null) return null;
        return new ListSectionDto(
                section.items(),
                section.sourceField().name(),
                AttachmentRefDto.from(section.attachmentRef())
        );
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.guide.*'`
Expected: 전체 guide 테스트 통과

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java
git commit -m "feat(guide): expose 4 new sections in GuideResponse DTO"
```

---

## Task 10: 백엔드 전체 빌드 + 회귀 테스트

**Files:**
- (변경 없음)

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 모든 테스트 통과

문제가 있으면 해당 task 로 돌아가 수정 후 다시 실행.

- [ ] **Step 2: 통합 동작 확인 (옵션, 로컬 실행 가능 시)**

Run: `cd backend && ./gradlew bootRun &`

샘플 정책 1건 (enrichment 가 있는 온통청년 정책) 의 가이드 재생성을 admin 도구나 SQL 로 트리거. JSON 응답에서 4개 NEW 섹션이 들어오는지 확인.

(이 단계는 로컬 환경 구성이 필요하므로 build 만 통과해도 다음 task 진행 가능)

- [ ] **Step 3: 백엔드 머지 commit (선택, 별도 PR 분리 시)**

이미 task 별로 commit 했으므로 별도 squash 없이 진행. 

---

## Task 11: 프론트엔드 `types/policy.ts` 확장

**Files:**
- Modify: `frontend/src/types/policy.ts`

- [ ] **Step 1: `GuideSourceField` 에 ENRICHMENT 추가, `GuideListSection` 추가, `Guide` 에 4개 NEW 필드 추가**

기존 `GuideSourceField` 정의 (133~138 line) 를 다음으로 교체:

```typescript
export type GuideSourceField =
  | 'SUPPORT_TARGET'
  | 'SELECTION_CRITERIA'
  | 'SUPPORT_CONTENT'
  | 'BODY'
  | 'ATTACHMENT'
  | 'ENRICHMENT';
```

`GuidePairedSection` 정의 다음에 `GuideListSection` 추가:

```typescript
export interface GuideListSection {
  items: string[];
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}
```

`Guide` 인터페이스 (167~176 line) 를 다음으로 교체:

```typescript
export interface Guide {
  policyId: number;
  oneLineSummary: string;
  highlights: GuideHighlight[];
  target: GuidePairedSection | null;
  criteria: GuidePairedSection | null;
  content: GuidePairedSection | null;
  applyMethod: GuideListSection | null;
  deadlineNote: GuideListSection | null;
  requiredDocuments: GuideListSection | null;
  contact: GuideListSection | null;
  pitfalls: GuidePitfall[];
  updatedAt: string;
}
```

- [ ] **Step 2: 타입체크 확인**

Run: `cd frontend && npm run build`
Expected: 빌드 성공 (4개 NEW 필드를 쓰지 않는 컴포넌트들은 영향 없음)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/policy.ts
git commit -m "feat(fe): extend Guide types with 4 new sections and ENRICHMENT source field"
```

---

## Task 12: `GuideListSectionCard` 컴포넌트 신설

**Files:**
- Create: `frontend/src/components/policy/GuideListSectionCard.tsx`
- Create: `frontend/src/components/policy/GuideListSectionCard.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GuideListSectionCard } from './GuideListSectionCard';
import type { GuideListSection } from '@/types/policy';

describe('GuideListSectionCard', () => {
  it('section 이 null 이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(
      <GuideListSectionCard title="신청방법" emoji="📝" section={null} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('items 가 여러 개면 불릿 리스트로 렌더', () => {
    const section: GuideListSection = {
      items: ['1단계', '2단계', '3단계'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="신청방법" emoji="📝" section={section} />);
    expect(screen.getByText('신청방법')).toBeInTheDocument();
    expect(screen.getByText('1단계')).toBeInTheDocument();
    expect(screen.getByText('2단계')).toBeInTheDocument();
    expect(screen.getByText('3단계')).toBeInTheDocument();
  });

  it('items 가 1개면 불릿 없이 한 줄로 렌더', () => {
    const section: GuideListSection = {
      items: ['2026-03-01 ~ 2026-05-31'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="신청기한" emoji="📅" section={section} />);
    expect(screen.getByText('2026-03-01 ~ 2026-05-31')).toBeInTheDocument();
  });

  it('sourceField=ENRICHMENT 면 AI 자동 수집 배지를 헤더에 노출', () => {
    const section: GuideListSection = {
      items: ['주민등록등본'],
      sourceField: 'ENRICHMENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="제출서류" emoji="📂" section={section} />);
    expect(screen.getByText('AI 자동 수집')).toBeInTheDocument();
  });

  it('sourceField=BODY/SUPPORT_* 면 AI 자동 수집 배지 없음', () => {
    const section: GuideListSection = {
      items: ['연락처'],
      sourceField: 'SUPPORT_CONTENT',
      attachmentRef: null,
    };
    render(<GuideListSectionCard title="문의처" emoji="☎" section={section} />);
    expect(screen.queryByText('AI 자동 수집')).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd frontend && npm test -- GuideListSectionCard.test.tsx`
Expected: FAIL (컴포넌트 미존재)

- [ ] **Step 3: 컴포넌트 구현**

```tsx
import type { GuideListSection } from '@/types/policy';
import { AiSourceChip } from './AiSourceChip';

interface Props {
  title: string;
  emoji: string;
  section: GuideListSection | null;
}

export function GuideListSectionCard({ title, emoji, section }: Props) {
  if (!section) return null;

  const isEnrichment = section.sourceField === 'ENRICHMENT';
  const isSingle = section.items.length === 1;

  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-base font-semibold text-neutral-900">
          <span aria-hidden>{emoji}</span>
          {title}
        </h2>
        {isEnrichment && <AiSourceChip />}
      </div>
      {isSingle ? (
        <p className="text-sm text-neutral-800">{section.items[0]}</p>
      ) : (
        <ul className="space-y-2">
          {section.items.map((item, i) => (
            <li key={i} className="text-sm text-neutral-800">
              • {item}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npm test -- GuideListSectionCard.test.tsx`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/policy/GuideListSectionCard.tsx frontend/src/components/policy/GuideListSectionCard.test.tsx
git commit -m "feat(fe): add GuideListSectionCard for 신청방법/신청기한/제출서류/문의처"
```

---

## Task 13: `PolicyDetailPage` 4개 NEW 카드 + 순서 재배치

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: import 추가 및 카드 삽입**

`PolicyDetailPage.tsx` 의 import 영역에 다음 추가:

```tsx
import { GuideListSectionCard } from '@/components/policy/GuideListSectionCard';
```

기존 가이드 카드 렌더 위치 (line 548~748 부근) 를 다음 순서로 재배치:

```tsx
{guide && <OneLineSummaryCard oneLineSummary={guide.oneLineSummary} />}
{guide && guide.highlights.length > 0 && (
  <HighlightsCard
    /* 기존 props 그대로 */
  />
)}
{guide?.target && (
  <PairedSection
    /* 기존 props — target */
  />
)}
{guide?.criteria && (
  <PairedSection
    /* 기존 props — criteria */
  />
)}
{guide?.content && (
  <PairedSection
    /* 기존 props — content */
  />
)}
{guide?.applyMethod && (
  <GuideListSectionCard title="신청방법" emoji="📝" section={guide.applyMethod} />
)}
{guide?.deadlineNote && (
  <GuideListSectionCard title="신청기한" emoji="📅" section={guide.deadlineNote} />
)}
{guide?.requiredDocuments && (
  <GuideListSectionCard title="제출서류" emoji="📂" section={guide.requiredDocuments} />
)}
{guide?.contact && (
  <GuideListSectionCard title="문의처" emoji="☎" section={guide.contact} />
)}
{guide && guide.pitfalls.length > 0 && (
  <PitfallsCard
    /* 기존 props 그대로 */
  />
)}
```

> 기존 `HighlightsCard` / `PairedSection` / `PitfallsCard` 의 props 시그니처는 그대로 유지. 위치만 재배치하고 4개 NEW 카드를 PairedSection content 뒤·PitfallsCard 앞에 삽입.

- [ ] **Step 2: 타입체크 + 빌드**

Run: `cd frontend && npm run build`
Expected: 빌드 성공

- [ ] **Step 3: 개발 서버 + 시각 확인**

Run: `cd frontend && npm run dev`

브라우저에서 enrichment 가 있는 정책(서울 청년 정책 중 1건) 의 상세 페이지를 열어 다음 확인:
- 4개 NEW 카드가 PairedSection content 카드와 PitfallsCard 사이에 노출됨
- ENRICHMENT 출처인 카드 헤더에 "AI 자동 수집" 배지가 보임
- enrichment 없는 정책에서 4개 카드가 미렌더 (잘못 노출 X)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(fe): add 4 bokjiro-style cards (신청방법/신청기한/제출서류/문의처) to PolicyDetailPage"
```

---

## Task 14: 통합 시각 검증 + 마무리

**Files:**
- (변경 없음)

- [ ] **Step 1: 백엔드 + 프론트 동시 기동**

Run: `cd backend && ./gradlew bootRun &` (포트 8080)
Run: `cd frontend && npm run dev` (포트 5173)

- [ ] **Step 2: 가이드 재생성 트리거**

allowlist 통과 정책 1건의 가이드를 강제 재생성 (admin API 또는 SQL 로 `guide.source_hash` 초기화).

- [ ] **Step 3: 시각 검증 체크리스트**

다음 6가지 케이스를 정책별로 확인:
- [ ] enrichment 풍부한 온통청년 정책: 4개 NEW 카드 모두 ENRICHMENT 배지
- [ ] 본문 풍부한 복지로 정책: 4개 NEW 카드 일부만 노출, 배지 없음 (BODY/SUPPORT_* 출처)
- [ ] enrichment 없는 정책: 4개 NEW 카드 자체 미렌더
- [ ] confidence 미달 정책: 4개 NEW 카드 자체 미렌더 (`isExposable() == false`)
- [ ] 카드 순서: 한 줄 요약 → 특징 → 지원대상 → 선정기준 → 지원내용 → 신청방법 → 신청기한 → 제출서류 → 문의처 → 놓치기 쉬운 점
- [ ] AI 자동 수집 배지 hover/tap 시 툴팁 표시

- [ ] **Step 4: admin LLM 비용 대시보드 확인 (선택)**

`/admin/llm-cost` 대시보드에서 머지 직후 LLM 호출 spike 가 예상 범위 내인지 확인.

- [ ] **Step 5: 최종 commit / PR**

create-pr 스킬로 PR 생성. PR 본문에 spec 링크 + 시각 검증 결과 첨부.

---

## Self-Review Notes

### 스펙 커버리지 매핑

| spec 섹션 | 구현 task |
|---|---|
| §5.1 GuideListSection record | Task 2 |
| §5.2 GuideContent 4개 nullable | Task 3 |
| §5.3 GuideSourceField.ENRICHMENT | Task 1 |
| §5.4 매핑 표 (LLM 프롬프트 안내) | Task 6 |
| §6.1 GuideGenerationInput.EnrichmentInput | Task 4 |
| §6.2 combinedSourceText 직렬화 | Task 4 |
| §7.1 SYSTEM_PROMPT v5 | Task 6 |
| §7.2 JSON 스키마 4개 필드 + ENRICHMENT enum | Task 5 |
| §8.1 검증 6 (sourceField 유효성) | Task 7 (`filterInvalidSourceFields` 확장) |
| §8.2 검증 7 (빈 items 방지) | Task 5 (`parseListSection` 에서 처리) |
| §8.3 후처리 (enforceAttachmentSourceField) | Task 7 |
| §9.1 hash 입력 변경 | Task 7 |
| §9.2 PolicyUpsertedEvent 트리거 | 변경 없음 (이미 작동) |
| §10.1 가이드 카드 레이아웃 | Task 13 |
| §10.2 컴포넌트 구조 | Task 12, 13 |
| §10.3 출처 배지 (AiSourceChip 재활용) | Task 12 |
| §10.4 응답 타입 | Task 11 |
| §10.5 빈/부분 출력 처리 | Task 12 |
| §11 비용 / 운영 | Task 14 (LLM 비용 대시보드 확인) |
| §12 점진 롤아웃 | 백엔드(Task 1~10) → 프론트(Task 11~13) → 검증(Task 14) |
| §13 테스트 전략 | Task 2/3/4/7/8/12 |
| §14 관찰 가능성 | Task 7 (로그 추가는 별도 task 없이 GuideGenerationService 변경 시 inline) |

### 빠진 spec 요구사항 점검

- §14 의 `enrichment_used` / `new_sections_present` / `enrichment_source_count` 로그는 Task 7 작업 중 `GuideGenerationService.generateGuide` 메서드 끝 부분에 한 줄로 추가. 별도 task 분리 없이 진행.

### Type 일관성 점검

- 백엔드 `GuideListSection` 와 프론트엔드 `GuideListSection` 필드명 일치: `items`, `sourceField`, `attachmentRef` ✓
- `GuideResponse.ListSectionDto` 의 직렬화 필드명 일치 ✓
- `Guide` 인터페이스의 4개 NEW 필드명이 백엔드 `GuideContent` 필드명과 일치: `applyMethod`, `deadlineNote`, `requiredDocuments`, `contact` ✓
- `GuideSourceField.ENRICHMENT` 가 백엔드 enum 과 프론트 union type 모두에 추가 ✓
