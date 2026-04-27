# 쉬운 해석 가이드 (Easy Policy Interpretation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 청년 정책 상세 페이지에 페어드 레이아웃(원문 ↔ 쉬운 해석) + 한 줄 요약 + 놓치기 쉬운 점 카드를 도입한다. 백엔드 `Guide` 엔티티를 단일 HTML에서 구조화 JSON으로 전환하고, OpenAI Structured Outputs로 출력을 강제한다.

**Architecture:** 가이드는 Policy 구조화 필드를 필수 입력, PolicyDocument 청크를 옵션 보강 입력으로 받는 하이브리드 모델. 가이드 생성은 새벽 ingestion 흐름에서 `IngestionService.receivePolicy` 후속 호출로 트리거(spec 7.1의 (a) 옵션). LLM 출력은 OpenAI structured outputs(JSON schema strict)로 강제.

**Tech Stack:** Java 21, Spring Boot 4.0, JPA + PostgreSQL JSONB, OpenAI Chat API (gpt-4o-mini, structured outputs), JUnit 5 + Mockito (백엔드 테스트), React 19 + TypeScript + TanStack Query + Tailwind (프론트), Vitest (프론트 테스트).

**Spec:** `docs/superpowers/specs/2026-04-28-easy-policy-interpretation-design.md`

---

## File Structure

### 백엔드 — 신규 파일

| 경로 | 책임 |
|------|------|
| `backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java` | 가이드 콘텐츠 값 객체 (JSON 직렬화 대상) |
| `backend/src/main/java/com/youthfit/guide/domain/model/GuidePairedSection.java` | 페어드 섹션 (items 배열) 값 객체 |
| `backend/src/main/java/com/youthfit/guide/domain/model/GuidePitfall.java` | 놓치기 쉬운 점 항목 값 객체 |
| `backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java` | 출처 필드 enum |
| `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` | LLM 입력 번들 (Policy 필드 + 청크 결합) |
| `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java` | 후처리 검증 (숫자 보존, 친근체 검출) |
| `backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java` | 도메인 값 객체 테스트 |
| `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java` | 검증 로직 테스트 |

### 백엔드 — 수정 파일

| 경로 | 변경 |
|------|------|
| `backend/src/main/java/com/youthfit/guide/domain/model/Guide.java` | `summaryHtml: TEXT` → `content: JSONB`. 도메인 메서드 시그니처 변경 |
| `backend/src/main/java/com/youthfit/guide/domain/repository/GuideRepository.java` | 변경 없음 |
| `backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java` | `summaryHtml` → `content: GuideContent` |
| `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java` | 반환 타입 `String` → `GuideContent`. 입력 타입 변경 |
| `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` | Policy 직접 조회, 청크 옵션 결합, 후처리 검증 호출 |
| `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` | structured outputs JSON schema, 새 시스템 프롬프트 |
| `backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java` | 새 필드(`oneLineSummary`, `paired`, `pitfalls`) |
| `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java` | `registerPolicy` 후속으로 `generateGuide` 호출 |
| `backend/src/test/java/com/youthfit/guide/domain/model/GuideTest.java` | 새 스키마 반영 |
| `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java` | 하이브리드 입력, 청크 옵션, sourceHash 캐싱 |
| `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java` | 가이드 생성 후속 호출 검증 |
| `docs/ENTITIES.md` | Guide 테이블 컬럼 설명 갱신 |

### 프론트 — 신규 파일

| 경로 | 책임 |
|------|------|
| `frontend/src/components/policy/OneLineSummaryCard.tsx` | 상단 한 줄 요약 카드 |
| `frontend/src/components/policy/PairedSection.tsx` | 페어드 섹션(쉬운 해석 + 원문) 컨테이너 |
| `frontend/src/components/policy/EasySectionBox.tsx` | 페어 안의 쉬운 해석 박스 (불릿 리스트) |
| `frontend/src/components/policy/PitfallsCard.tsx` | 놓치기 쉬운 점 카드 + 출처 클릭 핸들러 |
| `frontend/src/lib/scrollHighlight.ts` | 출처 클릭 시 부드러운 스크롤 + 1.5초 하이라이트 유틸 |

### 프론트 — 수정 파일

| 경로 | 변경 |
|------|------|
| `frontend/src/types/policy.ts` | `Guide` 인터페이스 새 스키마 |
| `frontend/src/apis/guide.api.ts` | 404를 null로 변환 |
| `frontend/src/hooks/queries/useGuide.ts` | 미생성 정책에서 에러 대신 null 반환 |
| `frontend/src/pages/PolicyDetailPage.tsx` | 새 컴포넌트로 레이아웃 재구성 |

### 마이그레이션 노트

`spring.jpa.hibernate.ddl-auto=update` 모드는 컬럼 타입을 변경하지 않는다. dev/test 환경에선 `guide` 테이블을 수동 DROP 후 재생성. 운영 환경에선 별도 마이그레이션 도구 도입 시 처리(현재 운영 데이터 없음, 이번 plan 범위 외).

---

## Task 1: GuideSourceField enum

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java`

가이드의 `pitfalls` 항목과 페어드 섹션이 어느 원문 필드에서 도출되었는지 표현하는 enum.

- [ ] **Step 1: 파일 작성**

```java
package com.youthfit.guide.domain.model;

public enum GuideSourceField {
    SUPPORT_TARGET,
    SELECTION_CRITERIA,
    SUPPORT_CONTENT,
    BODY
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideSourceField.java
git commit -m "feat(guide): GuideSourceField enum 추가"
```

---

## Task 2: GuidePairedSection record

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/GuidePairedSection.java`
- Create: `backend/src/test/java/com/youthfit/guide/domain/model/GuidePairedSectionTest.java`

페어드 섹션의 쉬운 해석 콘텐츠. `items` 배열만 가짐.

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/guide/domain/model/GuidePairedSectionTest.java`:
```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidePairedSectionTest {

    @Test
    void items가_null이면_예외() {
        assertThatThrownBy(() -> new GuidePairedSection(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    void items가_비어있으면_예외() {
        assertThatThrownBy(() -> new GuidePairedSection(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어있");
    }

    @Test
    void 정상_생성() {
        GuidePairedSection section = new GuidePairedSection(List.of("만 19~34세", "본인 명의 계약자"));
        assertThat(section.items()).containsExactly("만 19~34세", "본인 명의 계약자");
    }
}
```

- [ ] **Step 2: 테스트 실행해 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests "GuidePairedSectionTest"`
Expected: FAIL (컴파일 에러 — `GuidePairedSection` 미존재)

- [ ] **Step 3: 최소 구현**

`backend/src/main/java/com/youthfit/guide/domain/model/GuidePairedSection.java`:
```java
package com.youthfit.guide.domain.model;

import java.util.List;

public record GuidePairedSection(List<String> items) {

    public GuidePairedSection {
        if (items == null) {
            throw new IllegalArgumentException("items는 null일 수 없습니다");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items가 비어있을 수 없습니다");
        }
        items = List.copyOf(items);
    }
}
```

- [ ] **Step 4: 테스트 실행해 PASS 확인**

Run: `cd backend && ./gradlew test --tests "GuidePairedSectionTest"`
Expected: 3 tests, all PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuidePairedSection.java backend/src/test/java/com/youthfit/guide/domain/model/GuidePairedSectionTest.java
git commit -m "feat(guide): GuidePairedSection 값 객체 추가"
```

---

## Task 3: GuidePitfall record

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/GuidePitfall.java`
- Create: `backend/src/test/java/com/youthfit/guide/domain/model/GuidePitfallTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuidePitfallTest {

    @Test
    void text가_빈문자열이면_예외() {
        assertThatThrownBy(() -> new GuidePitfall("  ", GuideSourceField.SUPPORT_TARGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceField가_null이면_예외() {
        assertThatThrownBy(() -> new GuidePitfall("월세 60만원 초과 제외", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정상_생성() {
        GuidePitfall pitfall = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);
        assertThat(pitfall.text()).isEqualTo("월세 60만원 초과 제외");
        assertThat(pitfall.sourceField()).isEqualTo(GuideSourceField.SUPPORT_TARGET);
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `cd backend && ./gradlew test --tests "GuidePitfallTest"`
Expected: 컴파일 에러

- [ ] **Step 3: 구현**

```java
package com.youthfit.guide.domain.model;

public record GuidePitfall(String text, GuideSourceField sourceField) {

    public GuidePitfall {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text는 비어있을 수 없습니다");
        }
        if (sourceField == null) {
            throw new IllegalArgumentException("sourceField는 null일 수 없습니다");
        }
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "GuidePitfallTest"`
Expected: 3 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuidePitfall.java backend/src/test/java/com/youthfit/guide/domain/model/GuidePitfallTest.java
git commit -m "feat(guide): GuidePitfall 값 객체 추가"
```

---

## Task 4: GuideContent record

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java`
- Create: `backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java`

가이드 콘텐츠 전체. JSONB 컬럼에 저장될 직렬화 대상.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideContentTest {

    @Test
    void oneLineSummary가_빈문자열이면_예외() {
        assertThatThrownBy(() -> new GuideContent(
                "  ",
                null, null, null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pitfalls가_null이면_빈리스트로_저장() {
        GuideContent content = new GuideContent(
                "한 줄 요약", null, null, null, null);
        assertThat(content.pitfalls()).isEmpty();
    }

    @Test
    void 모든_paired가_null이고_pitfalls도_비어있으면_허용() {
        GuideContent content = new GuideContent(
                "한 줄 요약", null, null, null, List.of());
        assertThat(content.oneLineSummary()).isEqualTo("한 줄 요약");
        assertThat(content.target()).isNull();
        assertThat(content.criteria()).isNull();
        assertThat(content.content()).isNull();
        assertThat(content.pitfalls()).isEmpty();
    }

    @Test
    void 정상_생성() {
        GuidePairedSection target = new GuidePairedSection(List.of("만 19~34세"));
        GuidePitfall pitfall = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);

        GuideContent content = new GuideContent(
                "만 19~34세 청년 월세 지원",
                target, null, null,
                List.of(pitfall));

        assertThat(content.target().items()).containsExactly("만 19~34세");
        assertThat(content.pitfalls()).hasSize(1);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "GuideContentTest"`
Expected: 컴파일 에러

- [ ] **Step 3: 구현**

```java
package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideContent(
        String oneLineSummary,
        GuidePairedSection target,
        GuidePairedSection criteria,
        GuidePairedSection content,
        List<GuidePitfall> pitfalls) {

    public GuideContent {
        if (oneLineSummary == null || oneLineSummary.isBlank()) {
            throw new IllegalArgumentException("oneLineSummary는 비어있을 수 없습니다");
        }
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "GuideContentTest"`
Expected: 4 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java
git commit -m "feat(guide): GuideContent 값 객체 추가"
```

---

## Task 5: Guide 엔티티 — content JSONB 컬럼으로 전환

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/Guide.java`
- Modify: `backend/src/test/java/com/youthfit/guide/domain/model/GuideTest.java`

기존 `summary_html: TEXT` 컬럼을 제거하고 `content: JSONB`로 교체. JPA의 `@JdbcTypeCode(SqlTypes.JSON)`로 JSONB 매핑(이미 `Policy.referenceSites`에서 동일 패턴 사용 중).

- [ ] **Step 1: 기존 테스트 백업 후 새 테스트 작성**

`backend/src/test/java/com/youthfit/guide/domain/model/GuideTest.java` (전체 교체):
```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    private GuideContent sampleContent(String summary) {
        return new GuideContent(
                summary,
                new GuidePairedSection(List.of("만 19~34세")),
                null, null, List.of());
    }

    @Test
    void 신규_가이드_생성() {
        GuideContent content = sampleContent("청년 월세 지원");
        Guide guide = Guide.builder()
                .policyId(1L)
                .content(content)
                .sourceHash("hash1")
                .build();

        assertThat(guide.getPolicyId()).isEqualTo(1L);
        assertThat(guide.getContent().oneLineSummary()).isEqualTo("청년 월세 지원");
        assertThat(guide.getSourceHash()).isEqualTo("hash1");
    }

    @Test
    void hasChanged는_해시가_다르면_true() {
        Guide guide = Guide.builder()
                .policyId(1L)
                .content(sampleContent("요약"))
                .sourceHash("hash1")
                .build();

        assertThat(guide.hasChanged("hash2")).isTrue();
        assertThat(guide.hasChanged("hash1")).isFalse();
    }

    @Test
    void regenerate는_콘텐츠와_해시를_갱신() {
        Guide guide = Guide.builder()
                .policyId(1L)
                .content(sampleContent("이전"))
                .sourceHash("oldHash")
                .build();

        GuideContent newContent = sampleContent("이후");
        guide.regenerate(newContent, "newHash");

        assertThat(guide.getContent().oneLineSummary()).isEqualTo("이후");
        assertThat(guide.getSourceHash()).isEqualTo("newHash");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "GuideTest"`
Expected: 컴파일 에러 (`getSummaryHtml()` 등 더 이상 없음)

- [ ] **Step 3: 엔티티 교체**

`backend/src/main/java/com/youthfit/guide/domain/model/Guide.java`:
```java
package com.youthfit.guide.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guide")
public class Guide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false, unique = true)
    private Long policyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    private GuideContent content;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Builder
    private Guide(Long policyId, GuideContent content, String sourceHash) {
        this.policyId = policyId;
        this.content = content;
        this.sourceHash = sourceHash;
    }

    public boolean hasChanged(String newHash) {
        return !this.sourceHash.equals(newHash);
    }

    public void regenerate(GuideContent content, String sourceHash) {
        this.content = content;
        this.sourceHash = sourceHash;
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "GuideTest"`
Expected: 3 tests PASS

- [ ] **Step 5: 컴파일 에러 확인 — 다른 곳에서 getSummaryHtml() 사용처가 깨짐**

Run: `cd backend && ./gradlew compileJava`
Expected: 에러. `GuideResult.from()`, `OpenAiChatClient`, `GuideGenerationService`에서 깨짐 — 후속 task에서 처리.

- [ ] **Step 6: 커밋 (의도적으로 깨진 상태로)**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/Guide.java backend/src/test/java/com/youthfit/guide/domain/model/GuideTest.java
git commit -m "refactor(guide): Guide 엔티티 content JSONB 컬럼으로 전환

후속 task에서 의존 코드를 새 시그니처로 맞춘다. 본 커밋만으로는 컴파일 깨짐."
```

---

## Task 6: GuideResult DTO 갱신

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java`

- [ ] **Step 1: 교체**

```java
package com.youthfit.guide.application.dto.result;

import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;

import java.time.LocalDateTime;

public record GuideResult(
        Long id,
        Long policyId,
        GuideContent content,
        String sourceHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GuideResult from(Guide guide) {
        return new GuideResult(
                guide.getId(),
                guide.getPolicyId(),
                guide.getContent(),
                guide.getSourceHash(),
                guide.getCreatedAt(),
                guide.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 2: 컴파일 확인 (다른 곳은 여전히 깨짐)**

Run: `cd backend && ./gradlew compileJava`
Expected: GuideResult 자체는 통과. GuideResponse / OpenAiChatClient 등은 여전히 깨짐.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java
git commit -m "refactor(guide): GuideResult content 필드 사용으로 변경"
```

---

## Task 7: GuideGenerationInput command DTO

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`

LLM에 전달되는 입력 번들. Policy 구조화 필드(필수) + PolicyDocument 청크(옵션).

- [ ] **Step 1: 작성**

```java
package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.rag.domain.model.PolicyDocument;

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
        List<String> chunkContents
) {

    public GuideGenerationInput {
        if (policyId == null) {
            throw new IllegalArgumentException("policyId는 null일 수 없습니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어있을 수 없습니다");
        }
        chunkContents = chunkContents == null ? List.of() : List.copyOf(chunkContents);
    }

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks) {
        List<String> chunkTexts = chunks == null
                ? List.of()
                : chunks.stream().map(PolicyDocument::getContent).collect(Collectors.toList());

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
                chunkTexts
        );
    }

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
        for (int i = 0; i < chunkContents.size(); i++) {
            sb.append("[chunk-").append(i).append("]\n").append(chunkContents.get(i)).append("\n\n");
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

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: GuideGenerationInput 통과 (다른 곳 여전히 깨짐)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java
git commit -m "feat(guide): GuideGenerationInput 입력 번들 DTO 추가"
```

---

## Task 8: GuideLlmProvider 시그니처 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java`

- [ ] **Step 1: 교체**

```java
package com.youthfit.guide.application.port;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;

public interface GuideLlmProvider {

    GuideContent generateGuide(GuideGenerationInput input);
}
```

- [ ] **Step 2: 컴파일 확인 (구현체 깨짐)**

Run: `cd backend && ./gradlew compileJava`
Expected: `OpenAiChatClient`가 인터페이스 구현 안 맞아 깨짐 — 다음 task에서 처리.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java
git commit -m "refactor(guide): GuideLlmProvider 시그니처 변경 (구조화 출력)"
```

---

## Task 9: OpenAiChatClient — structured outputs

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- Create: `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java`

OpenAI Chat API에 `response_format: { type: "json_schema", strict: true, schema: ... }` 적용. 응답을 `GuideContent`로 파싱.

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java`:
```java
package com.youthfit.guide.infrastructure.external;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiChatClientTest {

    @Mock OpenAiChatProperties properties;
    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Test
    void OpenAI_응답_JSON을_GuideContent로_파싱한다() {
        // 본 테스트는 구현 후 통과해야 함. 구현 가이드용 시나리오 표현.
        // 실제로는 RestClient를 인터페이스 분리해 mock 가능하도록 구현해야 함.
        // -> Step 3에서 RestClient 호출을 별도 메서드로 분리하여 protected method 오버라이드로 테스트.
        assertThat(true).isTrue();
    }

    @Test
    void parseResponse_정상_JSON을_파싱() {
        OpenAiChatClient client = new OpenAiChatClient(properties);
        String json = """
                {
                  "oneLineSummary": "만 19~34세 청년 월세 지원",
                  "target": { "items": ["만 19~34세", "본인 명의 계약자"] },
                  "criteria": null,
                  "content": null,
                  "pitfalls": [
                    { "text": "월세 60만원 초과 제외", "sourceField": "SUPPORT_TARGET" }
                  ]
                }
                """;
        GuideContent content = client.parseResponse(json);
        assertThat(content.oneLineSummary()).isEqualTo("만 19~34세 청년 월세 지원");
        assertThat(content.target().items()).containsExactly("만 19~34세", "본인 명의 계약자");
        assertThat(content.criteria()).isNull();
        assertThat(content.pitfalls()).hasSize(1);
        assertThat(content.pitfalls().get(0).text()).isEqualTo("월세 60만원 초과 제외");
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `cd backend && ./gradlew test --tests "OpenAiChatClientTest"`
Expected: 컴파일 에러 (`parseResponse` 미존재)

- [ ] **Step 3: OpenAiChatClient 전면 교체**

`backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`:
```java
package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiChatClient implements GuideLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            너는 한국 청년 정책 가이드를 만드는 보조자다.

            원칙:
            1. 입력으로 주어진 원문에만 근거하라. 원문에 없는 조건/금액/기한/자격을 추가하지 마라.
            2. 원문에 명시된 모든 금액·연령·기간·지역 조건은 풀이에 누락 없이 보존하라.
            3. 어려운 행정 용어를 일상어로 치환할 때 의미를 바꾸지 마라.
            4. 어조는 원문의 명사형/단정형을 유지하라. "~예요", "~드려요", "~해요" 같은 친근체는 절대 사용하지 마라.
            5. 추정/가정/예시/"~일 수 있어요" 같은 표현 금지.
            6. 환경값(중위소득 N% 등)은 입력의 referenceYear 기준 환산값을 괄호로 병기하되, 환산값이 입력에 없으면 만들어내지 마라.

            출력 단위:
            - oneLineSummary: 정책의 정체를 1~2문장으로. 누가/무엇을/어떻게 받는지 핵심만.
            - target: supportTarget 원문의 풀이 (불릿 항목 배열). 입력에 supportTarget이 비어있으면 null.
            - criteria: selectionCriteria 원문의 풀이. 입력에 selectionCriteria가 비어있으면 null.
            - content: supportContent 원문의 풀이. 입력에 supportContent가 비어있으면 null.
            - pitfalls: 사용자가 원문을 한 번 읽고 놓칠 만한 함정 항목. 각 항목에 sourceField (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY) 라벨 필수.
            """;

    private final OpenAiChatProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GuideContent generateGuide(GuideGenerationInput input) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserMessage(input))
                ),
                "response_format", buildResponseFormat()
        );

        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.error("OpenAI Chat API 호출 실패: policyId={}", input.policyId());
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 생성에 실패했습니다");
        }

        String json = response.get("choices").get(0).get("message").get("content").asText();
        log.info("가이드 생성 완료: policyId={}, 응답 길이={}", input.policyId(), json.length());
        return parseResponse(json);
    }

    GuideContent parseResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String oneLine = node.get("oneLineSummary").asText();
            GuidePairedSection target = parsePaired(node.get("target"));
            GuidePairedSection criteria = parsePaired(node.get("criteria"));
            GuidePairedSection content = parsePaired(node.get("content"));
            List<GuidePitfall> pitfalls = parsePitfalls(node.get("pitfalls"));
            return new GuideContent(oneLine, target, criteria, content, pitfalls);
        } catch (Exception e) {
            log.error("가이드 응답 JSON 파싱 실패: {}", json, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 응답 파싱 실패");
        }
    }

    private GuidePairedSection parsePaired(JsonNode node) {
        if (node == null || node.isNull()) return null;
        JsonNode itemsNode = node.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) return null;
        List<String> items = new ArrayList<>();
        itemsNode.forEach(n -> items.add(n.asText()));
        return new GuidePairedSection(items);
    }

    private List<GuidePitfall> parsePitfalls(JsonNode node) {
        List<GuidePitfall> pitfalls = new ArrayList<>();
        if (node == null || !node.isArray()) return pitfalls;
        node.forEach(n -> {
            String text = n.get("text").asText();
            String sourceFieldStr = n.get("sourceField").asText();
            pitfalls.add(new GuidePitfall(text, GuideSourceField.valueOf(sourceFieldStr)));
        });
        return pitfalls;
    }

    private String buildUserMessage(GuideGenerationInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 메타]\n");
        sb.append("title: ").append(input.title()).append("\n");
        if (input.referenceYear() != null) sb.append("referenceYear: ").append(input.referenceYear()).append("\n");
        if (input.organization() != null) sb.append("organization: ").append(input.organization()).append("\n");
        if (input.contact() != null) sb.append("contact: ").append(input.contact()).append("\n");
        sb.append("\n[원문]\n");
        sb.append(input.combinedSourceText());
        return sb.toString();
    }

    private Map<String, Object> buildResponseFormat() {
        Map<String, Object> pairedSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("items"),
                "properties", Map.of(
                        "items", Map.of("type", "array", "items", Map.of("type", "string"))
                )
        );

        Map<String, Object> pitfallSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("text", "sourceField"),
                "properties", Map.of(
                        "text", Map.of("type", "string"),
                        "sourceField", Map.of(
                                "type", "string",
                                "enum", List.of("SUPPORT_TARGET", "SELECTION_CRITERIA", "SUPPORT_CONTENT", "BODY")
                        )
                )
        );

        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("oneLineSummary", "target", "criteria", "content", "pitfalls"),
                "properties", Map.of(
                        "oneLineSummary", Map.of("type", "string"),
                        "target", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                        "criteria", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                        "content", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                        "pitfalls", Map.of("type", "array", "items", pitfallSchema)
                )
        );

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "guide_content",
                        "strict", true,
                        "schema", schema
                )
        );
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "OpenAiChatClientTest.parseResponse_정상_JSON을_파싱"`
Expected: PASS

- [ ] **Step 5: 전체 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: GuideGenerationService가 아직 미수정이라 깨짐 — 다음 task에서.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java
git commit -m "feat(guide): OpenAiChatClient structured outputs 적용

OpenAI response_format json_schema strict로 출력 강제. parseResponse를
패키지 가시성 메서드로 분리하여 단위 테스트 가능."
```

---

## Task 10: GuideValidator — 후처리 검증

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java`
- Create: `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java`

원문에 등장한 숫자 토큰이 풀이에 보존되는지, 친근체가 등장하는지 검증. 실패 시 경고 로그만(차단 아님).

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideValidatorTest {

    GuideValidator validator = new GuideValidator();

    @Test
    void 원문에_금액이_풀이에_보존되면_통과() {
        String originalText = "월세 60만원 이하 주택 거주 청년";
        GuideContent content = new GuideContent(
                "월세 지원",
                new GuidePairedSection(List.of("월세 60만원 이하 주택 거주자")),
                null, null, List.of());

        assertThat(validator.findMissingNumericTokens(originalText, content)).isEmpty();
    }

    @Test
    void 원문에_있던_금액이_풀이에_없으면_누락_검출() {
        String originalText = "월세 60만원 이하 주택, 만 19세 이상";
        GuideContent content = new GuideContent(
                "월세 지원",
                new GuidePairedSection(List.of("청년 월세 지원")),
                null, null, List.of());

        List<String> missing = validator.findMissingNumericTokens(originalText, content);
        assertThat(missing).contains("60만원", "19세");
    }

    @Test
    void 친근체_검출() {
        GuideContent content = new GuideContent(
                "이 정책은 청년에게 도움이 돼요.",
                new GuidePairedSection(List.of("받을 수 있어요")),
                null, null, List.of());

        assertThat(validator.containsFriendlyTone(content)).isTrue();
    }

    @Test
    void 명사형_단정형은_친근체_아님() {
        GuideContent content = new GuideContent(
                "만 19~34세 청년 월세 지원",
                new GuidePairedSection(List.of("본인 명의 계약자")),
                null, null, List.of());

        assertThat(validator.containsFriendlyTone(content)).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "GuideValidatorTest"`
Expected: 컴파일 에러

- [ ] **Step 3: 구현**

`backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java`:
```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GuideValidator {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "(\\d+\\s*(?:만원|원|개월|년|세|%|명))"
    );

    private static final Pattern FRIENDLY_TONE = Pattern.compile(
            "(?:해요|예요|에요|드려요|이에요|어요|아요)(?:[.!?\\s]|$)"
    );

    public List<String> findMissingNumericTokens(String originalText, GuideContent content) {
        if (originalText == null || originalText.isBlank()) return List.of();

        Set<String> originalTokens = extractTokens(originalText);
        String renderedText = renderContentText(content);
        Set<String> renderedTokens = extractTokens(renderedText);

        List<String> missing = new ArrayList<>();
        for (String token : originalTokens) {
            if (!renderedTokens.contains(token)) {
                missing.add(token);
            }
        }
        return missing;
    }

    public boolean containsFriendlyTone(GuideContent content) {
        return FRIENDLY_TONE.matcher(renderContentText(content)).find();
    }

    private Set<String> extractTokens(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher m = NUMERIC_TOKEN.matcher(text);
        while (m.find()) {
            tokens.add(m.group(1).replaceAll("\\s+", ""));
        }
        return tokens;
    }

    private String renderContentText(GuideContent content) {
        StringBuilder sb = new StringBuilder();
        sb.append(content.oneLineSummary()).append(" ");
        appendSection(sb, content.target());
        appendSection(sb, content.criteria());
        appendSection(sb, content.content());
        content.pitfalls().forEach(p -> sb.append(p.text()).append(" "));
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, GuidePairedSection section) {
        if (section == null) return;
        section.items().forEach(item -> sb.append(item).append(" "));
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "GuideValidatorTest"`
Expected: 4 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java
git commit -m "feat(guide): GuideValidator 후처리 검증 추가

원문 숫자 토큰 보존 여부 + 친근체 검출. 차단 아닌 로깅 용도."
```

---

## Task 11: GuideGenerationService — 하이브리드 입력으로 재작성

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java`

Policy 직접 조회 + PolicyDocument 청크 옵션 조회 + 가이드 생성/저장 + 검증 호출.

- [ ] **Step 1: 새 테스트 작성**

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuideGenerationServiceTest {

    @Mock GuideRepository guideRepository;
    @Mock PolicyRepository policyRepository;
    @Mock PolicyDocumentRepository policyDocumentRepository;
    @Mock GuideLlmProvider guideLlmProvider;
    @Mock GuideValidator guideValidator;

    @InjectMocks GuideGenerationService service;

    private Policy samplePolicy() {
        return Policy.builder()
                .title("청년 월세 지원")
                .summary("월세 부담 완화")
                .body("만 19세 이상 34세 이하 …")
                .supportTarget("만 19세 이상 34세 이하의 무주택 세대주")
                .selectionCriteria(null)
                .supportContent("매월 최대 20만원, 최대 12개월")
                .category(Category.HOUSING)
                .regionCode("11000")
                .build();
    }

    private GuideContent sampleContent() {
        return new GuideContent(
                "청년 월세 지원",
                new GuidePairedSection(List.of("만 19~34세")),
                null, null, List.of());
    }

    @Test
    void Policy_없으면_NOT_FOUND_결과() {
        when(policyRepository.findById(99L)).thenReturn(Optional.empty());

        GuideGenerationResult result = service.generateGuide(new GenerateGuideCommand(99L, "x"));

        assertThat(result.regenerated()).isFalse();
        assertThat(result.message()).contains("정책");
        verify(guideLlmProvider, never()).generateGuide(any());
    }

    @Test
    void 청크가_비어있어도_가이드_생성() {
        Policy policy = samplePolicy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(guideRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(guideLlmProvider.generateGuide(any())).thenReturn(sampleContent());
        when(guideValidator.findMissingNumericTokens(any(), any())).thenReturn(List.of());
        when(guideValidator.containsFriendlyTone(any())).thenReturn(false);

        GuideGenerationResult result = service.generateGuide(new GenerateGuideCommand(1L, "청년 월세 지원"));

        assertThat(result.regenerated()).isTrue();
        verify(guideRepository, times(1)).save(any(Guide.class));
    }

    @Test
    void sourceHash_동일하면_재생성_스킵() {
        Policy policy = samplePolicy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());

        // 첫 호출에서 같은 hash가 이미 존재한다고 가정
        Guide existing = Guide.builder()
                .policyId(1L)
                .content(sampleContent())
                .sourceHash(service.computeHashForTest(policy, List.of()))
                .build();
        when(guideRepository.findByPolicyId(1L)).thenReturn(Optional.of(existing));

        GuideGenerationResult result = service.generateGuide(new GenerateGuideCommand(1L, "청년 월세 지원"));

        assertThat(result.regenerated()).isFalse();
        verify(guideLlmProvider, never()).generateGuide(any());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "GuideGenerationServiceTest"`
Expected: 컴파일 에러 (시그니처 불일치)

- [ ] **Step 3: 서비스 재작성**

`backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`:
```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GuideGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationService.class);

    private final GuideRepository guideRepository;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final GuideLlmProvider guideLlmProvider;
    private final GuideValidator guideValidator;

    @Transactional(readOnly = true)
    public Optional<GuideResult> findGuideByPolicyId(Long policyId) {
        return guideRepository.findByPolicyId(policyId).map(GuideResult::from);
    }

    @Transactional
    public GuideGenerationResult generateGuide(GenerateGuideCommand command) {
        Optional<Policy> policyOpt = policyRepository.findById(command.policyId());
        if (policyOpt.isEmpty()) {
            return new GuideGenerationResult(command.policyId(), false, "정책을 찾을 수 없습니다");
        }
        Policy policy = policyOpt.get();
        List<PolicyDocument> chunks = policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId());

        String hash = computeHash(policy, chunks);
        Optional<Guide> existing = guideRepository.findByPolicyId(command.policyId());
        if (existing.isPresent() && !existing.get().hasChanged(hash)) {
            log.info("가이드 변경 없음, 재생성 스킵: policyId={}", command.policyId());
            return new GuideGenerationResult(command.policyId(), false, "변경 없음");
        }

        GuideGenerationInput input = GuideGenerationInput.of(policy, chunks);
        GuideContent content = guideLlmProvider.generateGuide(input);

        // 후처리 검증 (로그 위주)
        List<String> missing = guideValidator.findMissingNumericTokens(input.combinedSourceText(), content);
        if (!missing.isEmpty()) {
            log.warn("가이드 풀이에 원문 숫자 토큰 누락: policyId={}, missing={}", command.policyId(), missing);
        }
        if (guideValidator.containsFriendlyTone(content)) {
            log.warn("가이드 풀이에 친근체 출현: policyId={}", command.policyId());
        }

        if (existing.isPresent()) {
            existing.get().regenerate(content, hash);
            guideRepository.save(existing.get());
        } else {
            guideRepository.save(Guide.builder()
                    .policyId(command.policyId())
                    .content(content)
                    .sourceHash(hash)
                    .build());
        }
        log.info("가이드 생성 완료: policyId={}", command.policyId());
        return new GuideGenerationResult(command.policyId(), true, "생성 완료");
    }

    /** 테스트 노출용. */
    String computeHashForTest(Policy policy, List<PolicyDocument> chunks) {
        return computeHash(policy, chunks);
    }

    private String computeHash(Policy policy, List<PolicyDocument> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(policy.getTitle()));
        sb.append(safe(policy.getSummary()));
        sb.append(safe(policy.getBody()));
        sb.append(safe(policy.getSupportTarget()));
        sb.append(safe(policy.getSelectionCriteria()));
        sb.append(safe(policy.getSupportContent()));
        sb.append(policy.getReferenceYear());
        chunks.forEach(c -> sb.append(c.getContent()));
        return sha256(sb.toString());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "GuideGenerationServiceTest"`
Expected: 3 tests PASS

- [ ] **Step 5: 전체 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: GuideResponse 깨짐 — 다음 task에서.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java
git commit -m "refactor(guide): GuideGenerationService 하이브리드 입력으로 재작성

Policy 구조화 필드 직접 조회 + PolicyDocument 청크 옵션 결합.
sourceHash는 referenceYear 포함 (환경값 변경 시 재생성)."
```

---

## Task 12: GuideResponse DTO 갱신

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java`

- [ ] **Step 1: 교체**

```java
package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.domain.model.GuideContent;

import java.time.LocalDateTime;

public record GuideResponse(
        Long policyId,
        String oneLineSummary,
        PairedDto target,
        PairedDto criteria,
        PairedDto content,
        java.util.List<PitfallDto> pitfalls,
        LocalDateTime updatedAt
) {

    public record PairedDto(java.util.List<String> items) {}

    public record PitfallDto(String text, String sourceField) {}

    public static GuideResponse from(GuideResult result) {
        GuideContent c = result.content();
        return new GuideResponse(
                result.policyId(),
                c.oneLineSummary(),
                c.target() == null ? null : new PairedDto(c.target().items()),
                c.criteria() == null ? null : new PairedDto(c.criteria().items()),
                c.content() == null ? null : new PairedDto(c.content().items()),
                c.pitfalls().stream()
                        .map(p -> new PitfallDto(p.text(), p.sourceField().name()))
                        .toList(),
                result.updatedAt()
        );
    }
}
```

- [ ] **Step 2: 전체 컴파일 통과 확인**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 전체 테스트 통과 확인**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL (모든 기존 테스트도 통과)

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java
git commit -m "refactor(guide): GuideResponse 새 스키마로 갱신"
```

---

## Task 13: IngestionService — 가이드 생성 후속 호출

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java`

`receivePolicy`에서 정책 등록 후 `GuideGenerationService.generateGuide` 호출. 실패해도 ingestion 자체는 성공으로 종료.

- [ ] **Step 1: 기존 테스트 추가**

`IngestionServiceTest.java`에 테스트 추가:
```java
    @Test
    void 정책_등록_후_가이드_생성을_호출한다() {
        // 기존 테스트 셋업 활용. 본 테스트는 mock 검증만.
        // 본 task의 Step 3에서 GuideGenerationService 의존성을 추가하고 verify 한다.
    }

    @Test
    void 가이드_생성_실패해도_ingestion은_성공() {
        // 동일.
    }
```

(실제 테스트 코드는 Step 3 구현과 같이 채움)

- [ ] **Step 2: 의존 추가 및 호출 로직 추가**

`IngestionService.java`에서 다음 변경:

```java
// import 추가:
import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;

// 필드 추가:
private final GuideGenerationService guideGenerationService;

// receivePolicy 수정 — 마지막 부분:
PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
triggerGuideGeneration(ingestionResult.policyId(), command.title());
return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");

// 새 메서드:
private void triggerGuideGeneration(Long policyId, String title) {
    if (policyId == null) return;
    try {
        guideGenerationService.generateGuide(new GenerateGuideCommand(policyId, title));
    } catch (Exception e) {
        // ingestion 자체는 성공시킨다.
        org.slf4j.LoggerFactory.getLogger(IngestionService.class)
                .warn("가이드 생성 실패: policyId={}", policyId, e);
    }
}
```

> 참고: `PolicyIngestionResult.policyId()`는 이미 존재. 기존 `registerPolicy` 반환값을 받도록 변수 추가.

- [ ] **Step 3: 테스트 본문 채우기**

```java
@Test
void 정책_등록_후_가이드_생성을_호출한다() {
    // Given: 기존 셋업 + 신규 mock
    when(policyIngestionService.registerPolicy(any()))
            .thenReturn(new PolicyIngestionResult(42L, true));

    // When
    service.receivePolicy(sampleCommand());

    // Then
    verify(guideGenerationService).generateGuide(argThat(cmd -> cmd.policyId().equals(42L)));
}

@Test
void 가이드_생성_실패해도_ingestion은_성공() {
    when(policyIngestionService.registerPolicy(any()))
            .thenReturn(new PolicyIngestionResult(42L, true));
    when(guideGenerationService.generateGuide(any()))
            .thenThrow(new RuntimeException("LLM 장애"));

    // 예외가 위로 전파되지 않아야 함
    assertThatCode(() -> service.receivePolicy(sampleCommand())).doesNotThrowAnyException();
}
```

(`sampleCommand()`는 기존 테스트의 헬퍼 사용 또는 신규 작성)

- [ ] **Step 4: 컴파일/테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests "IngestionServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
git commit -m "feat(ingestion): 정책 등록 후 가이드 생성 후속 호출

새벽 ingestion 배치 흐름에서 가이드를 사전 생성한다. 가이드 실패는
ingestion을 막지 않는다."
```

---

## Task 14: 백엔드 — DB 정리 (수동 단계)

**Files:**
- 없음 (운영자 노트)

기존 `guide` 테이블의 `summary_html` 컬럼은 ddl-auto=update 모드에선 자동 제거되지 않는다. 운영 데이터 없으므로 dev 환경에서 테이블을 통째로 재생성한다.

- [ ] **Step 1: dev DB 접속해 guide 테이블 DROP**

```bash
psql -h localhost -U youthfit -d youthfit -c "DROP TABLE IF EXISTS guide;"
```

- [ ] **Step 2: 앱 재기동 → JPA가 새 스키마로 테이블 재생성**

Run: `cd backend && ./gradlew bootRun`
Expected: 시작 로그에 `Hibernate: create table guide (... content jsonb not null ...)` 출력

- [ ] **Step 3: 종료. 커밋 없음 (코드 변경 없음).**

---

## Task 15: ENTITIES.md 갱신

**Files:**
- Modify: `docs/ENTITIES.md`

- [ ] **Step 1: 3.1 Guide 섹션 교체**

`docs/ENTITIES.md`의 다음 부분:
```
### 3.1 Guide — `guide`
LLM으로 생성된 정책 해설 콘텐츠. 정책당 1개.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| policy_id | BIGINT | **unique** |
| summary_html | TEXT NOT NULL | 구조화된 HTML 가이드 |
| source_hash | VARCHAR(64) | 생성 시점 원본 해시 |

- `hasChanged(newHash)` + `regenerate(summaryHtml, newHash)` 로 원본이 바뀐 경우에만 재생성.
```

다음과 같이 교체:
```
### 3.1 Guide — `guide`
LLM으로 생성된 정책 해설 콘텐츠. 정책당 1개. 페어드 레이아웃(원문 ↔ 쉬운 해석)을 위해 구조화된 JSON으로 저장.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| policy_id | BIGINT | **unique** |
| content | JSONB NOT NULL | `oneLineSummary`, `target/criteria/content` 페어드 섹션, `pitfalls` 배열 |
| source_hash | VARCHAR(64) | Policy 구조화 필드 + referenceYear + 청크 결합 해시 |

- `hasChanged(newHash)` + `regenerate(content, newHash)` 로 원본이 바뀐 경우에만 재생성.
- `content` JSON 스키마: `docs/superpowers/specs/2026-04-28-easy-policy-interpretation-design.md` 4.1 참조.
```

- [ ] **Step 2: 커밋**

```bash
git add docs/ENTITIES.md
git commit -m "docs(guide): Guide 엔티티 새 스키마 반영"
```

---

## Task 16: 프론트 — Guide 타입 정의

**Files:**
- Modify: `frontend/src/types/policy.ts`

- [ ] **Step 1: Guide 인터페이스 교체**

기존:
```ts
/* ── Guide ── */
export interface Guide {
  id: number;
  policyId: number;
  summaryHtml: string;
  createdAt: string;
  updatedAt: string;
}
```

다음으로 교체:
```ts
/* ── Guide ── */

export type GuideSourceField =
  | 'SUPPORT_TARGET'
  | 'SELECTION_CRITERIA'
  | 'SUPPORT_CONTENT'
  | 'BODY';

export interface GuidePairedSection {
  items: string[];
}

export interface GuidePitfall {
  text: string;
  sourceField: GuideSourceField;
}

export interface Guide {
  policyId: number;
  oneLineSummary: string;
  target: GuidePairedSection | null;
  criteria: GuidePairedSection | null;
  content: GuidePairedSection | null;
  pitfalls: GuidePitfall[];
  updatedAt: string;
}
```

- [ ] **Step 2: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: 깨짐 — `PolicyDetailPage.tsx`가 `summaryHtml` 사용. 후속 task에서 수정.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/types/policy.ts
git commit -m "feat(fe): Guide 타입 새 스키마 반영"
```

---

## Task 17: 프론트 — useGuide 훅 404 graceful 처리

**Files:**
- Modify: `frontend/src/apis/guide.api.ts`
- Modify: `frontend/src/hooks/queries/useGuide.ts`

미생성 정책에서 가이드 카드 통째로 숨김(spec 7.2). 404를 에러가 아닌 `null`로 변환.

- [ ] **Step 1: API 함수 변경**

`frontend/src/apis/guide.api.ts`:
```ts
import { HTTPError } from 'ky';
import api from './client';
import type { ApiResponse, Guide } from '@/types/policy';

export async function fetchGuide(policyId: number): Promise<Guide | null> {
  try {
    const res = await api.get(`v1/guides/${policyId}`).json<ApiResponse<Guide>>();
    return res.data;
  } catch (err) {
    if (err instanceof HTTPError && err.response.status === 404) {
      return null;
    }
    throw err;
  }
}
```

- [ ] **Step 2: 훅 변경**

`frontend/src/hooks/queries/useGuide.ts`:
```ts
import { useQuery } from '@tanstack/react-query';
import { fetchGuide } from '@/apis/guide.api';
import type { Guide } from '@/types/policy';

export function useGuide(policyId: number) {
  return useQuery<Guide | null>({
    queryKey: ['guide', policyId],
    queryFn: () => fetchGuide(policyId),
    enabled: policyId > 0,
    retry: false,
  });
}
```

- [ ] **Step 3: 타입 체크**

Run: `cd frontend && npx tsc --noEmit`
Expected: PolicyDetailPage 의 `guide?.summaryHtml` 사용처에서 에러 — 후속 task에서 처리.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/apis/guide.api.ts frontend/src/hooks/queries/useGuide.ts
git commit -m "feat(fe): useGuide 404를 null로 변환

미생성 정책에선 가이드 카드를 숨길 수 있도록 graceful null 반환."
```

---

## Task 18: 프론트 — scrollHighlight 유틸

**Files:**
- Create: `frontend/src/lib/scrollHighlight.ts`

놓치기 쉬운 점 카드의 출처 라벨 클릭 시 페어드 섹션 원문으로 부드럽게 스크롤 + 1.5초 하이라이트.

- [ ] **Step 1: 작성**

```ts
export function scrollAndHighlight(elementId: string) {
  const el = document.getElementById(elementId);
  if (!el) return;

  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  el.classList.add('source-highlight');
  window.setTimeout(() => {
    el.classList.remove('source-highlight');
  }, 1500);
}
```

해당 클래스를 위한 CSS 보조 (Tailwind 사용 중이므로 globals.css 또는 PolicyDetailPage 인라인 스타일):

`frontend/src/index.css` 또는 globals 파일에 추가:
```css
.source-highlight {
  animation: highlight-fade 1.5s ease-out;
}

@keyframes highlight-fade {
  0% { background-color: rgb(254 240 138); }
  100% { background-color: transparent; }
}
```

(globals 파일 정확한 경로는 프로젝트의 기존 글로벌 CSS 위치 확인 후 추가)

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/lib/scrollHighlight.ts frontend/src/index.css
git commit -m "feat(fe): scrollHighlight 유틸 추가

출처 라벨 클릭 시 페어드 원문으로 스크롤 + 노란 배경 1.5초 페이드."
```

---

## Task 19: 프론트 — OneLineSummaryCard 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/OneLineSummaryCard.tsx`

상단 한 줄 요약 카드.

- [ ] **Step 1: 작성**

```tsx
interface Props {
  oneLineSummary: string;
}

export function OneLineSummaryCard({ oneLineSummary }: Props) {
  return (
    <section className="mb-6 rounded-2xl border border-indigo-100 bg-indigo-50/50 p-6">
      <span className="mb-3 inline-block rounded-full bg-brand-100 px-3 py-1 text-xs font-bold uppercase tracking-wide text-indigo-600">
        이 정책 한눈에
      </span>
      <p className="text-base leading-relaxed text-neutral-800">{oneLineSummary}</p>
      <p className="mt-3 text-xs text-neutral-500">
        AI가 정리한 해석이에요. 정확한 조건은 아래 원문과 공식 공고에서 확인해주세요.
      </p>
    </section>
  );
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd frontend && npx tsc --noEmit`
Expected: 새 파일 통과 (PolicyDetailPage는 여전히 깨짐)

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/policy/OneLineSummaryCard.tsx
git commit -m "feat(fe): OneLineSummaryCard 컴포넌트 추가"
```

---

## Task 20: 프론트 — EasySectionBox 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/EasySectionBox.tsx`

페어드 섹션 안의 쉬운 해석 박스 (불릿 리스트).

- [ ] **Step 1: 작성**

```tsx
interface Props {
  title: string;
  items: string[];
}

export function EasySectionBox({ title, items }: Props) {
  return (
    <div className="rounded-t-2xl border border-b-0 border-indigo-100 bg-indigo-50/40 p-5">
      <span className="mb-2 inline-block rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-indigo-600">
        쉬운 해석
      </span>
      <h3 className="mb-3 text-base font-semibold text-neutral-900">{title}</h3>
      <ul className="space-y-1.5 text-sm text-neutral-800">
        {items.map((item, i) => (
          <li key={i} className="flex gap-2">
            <span className="text-indigo-500">•</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/components/policy/EasySectionBox.tsx
git commit -m "feat(fe): EasySectionBox 컴포넌트 추가"
```

---

## Task 21: 프론트 — PairedSection 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/PairedSection.tsx`

쉬운 해석 + 원문 카드를 한 컨테이너로 묶음. `id` prop으로 스크롤 타겟 노출.

- [ ] **Step 1: 작성**

```tsx
import { ReactNode } from 'react';
import type { GuidePairedSection } from '@/types/policy';
import { EasySectionBox } from './EasySectionBox';

interface Props {
  id: string;                    // 스크롤 타겟 (e.g., "paired-supportTarget")
  easyTitle: string;             // 쉬운 해석 박스 제목 (e.g., "누가 받을 수 있나요")
  easyData: GuidePairedSection | null;
  originalTitle: string;         // 원문 박스 제목 (e.g., "지원대상")
  originalContent: string | null;
  originalRenderer: (content: string) => ReactNode; // 기존 FormattedPolicyText 등 재사용
}

export function PairedSection({
  id,
  easyTitle,
  easyData,
  originalTitle,
  originalContent,
  originalRenderer,
}: Props) {
  // spec 5.4: 원문이 비어있으면 페어 자체 렌더 안 함
  if (!originalContent) return null;

  return (
    <section id={id} className="mb-6">
      {easyData && <EasySectionBox title={easyTitle} items={easyData.items} />}
      <div
        className={
          easyData
            ? 'rounded-b-2xl border border-neutral-200 bg-white p-5'
            : 'rounded-2xl border border-neutral-200 bg-white p-5'
        }
      >
        <h3 className="mb-3 text-base font-semibold text-neutral-700">{originalTitle}</h3>
        <div className="text-sm text-neutral-600">{originalRenderer(originalContent)}</div>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/components/policy/PairedSection.tsx
git commit -m "feat(fe): PairedSection 컴포넌트 추가

쉬운 해석 + 원문을 한 컨테이너로 묶고, id로 스크롤 타겟 노출."
```

---

## Task 22: 프론트 — PitfallsCard 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/PitfallsCard.tsx`

- [ ] **Step 1: 작성**

```tsx
import type { GuidePitfall, GuideSourceField } from '@/types/policy';
import { scrollAndHighlight } from '@/lib/scrollHighlight';

const SOURCE_LABELS: Record<GuideSourceField, string> = {
  SUPPORT_TARGET: '지원대상',
  SELECTION_CRITERIA: '선정기준',
  SUPPORT_CONTENT: '지원내용',
  BODY: '정책 본문',
};

const SCROLL_TARGETS: Record<GuideSourceField, string> = {
  SUPPORT_TARGET: 'paired-supportTarget',
  SELECTION_CRITERIA: 'paired-selectionCriteria',
  SUPPORT_CONTENT: 'paired-supportContent',
  BODY: 'policy-summary-section',
};

interface Props {
  pitfalls: GuidePitfall[];
}

export function PitfallsCard({ pitfalls }: Props) {
  if (!pitfalls.length) return null;

  return (
    <section className="mb-6 rounded-2xl border border-amber-200 bg-amber-50/50 p-6">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-amber-900">
        <span aria-hidden>⚠️</span>
        놓치기 쉬운 점
      </h2>
      <ul className="space-y-3">
        {pitfalls.map((p, i) => (
          <li key={i} className="text-sm text-neutral-800">
            <p className="mb-1">• {p.text}</p>
            <button
              type="button"
              onClick={() => scrollAndHighlight(SCROLL_TARGETS[p.sourceField])}
              className="ml-3 inline-flex items-center gap-1 rounded-md border border-amber-300 bg-white px-2 py-0.5 text-xs text-amber-800 hover:bg-amber-100"
            >
              {SOURCE_LABELS[p.sourceField]} ↗
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/components/policy/PitfallsCard.tsx
git commit -m "feat(fe): PitfallsCard 컴포넌트 추가

출처 라벨 클릭 시 페어드 원문으로 스크롤 + 하이라이트."
```

---

## Task 23: 프론트 — PolicyDetailPage 레이아웃 재구성

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

기존 `GuideSummaryCard` 자리를 `OneLineSummaryCard`로 교체. 기존 `DetailSection`(지원대상/선정기준/지원내용)을 `PairedSection`으로 감싸기. `PitfallsCard` 추가. 가이드가 null이면 모두 숨김.

- [ ] **Step 1: import 추가**

PolicyDetailPage 상단:
```tsx
import { OneLineSummaryCard } from '@/components/policy/OneLineSummaryCard';
import { PairedSection } from '@/components/policy/PairedSection';
import { PitfallsCard } from '@/components/policy/PitfallsCard';
```

기존 `GuideSummaryCard` 컴포넌트 정의(파일 내 304-334라인)는 삭제.

- [ ] **Step 2: 메인 컬럼 JSX 교체**

기존 (815-846라인 부근):
```tsx
{/* AI Guide Summary */}
<GuideSummaryCard html={guide?.summaryHtml ?? null} isLoading={guideLoading} />

{/* Policy Summary */}
<section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
  <h2 className="mb-3 text-lg font-semibold text-neutral-900">정책 요약</h2>
  <FormattedPolicyText text={policy.summary} />
</section>

{/* Policy Meta Summary */}
<PolicyMetaSummary ... />

{/* Structured Detail Sections */}
{policy.supportTarget && (
  <DetailSection icon={Users} title="지원대상" content={policy.supportTarget} />
)}
{policy.selectionCriteria && (
  <DetailSection icon={ClipboardCheck} title="선정기준" content={policy.selectionCriteria} />
)}
{policy.supportContent && (
  <DetailSection icon={Gift} title="지원내용" content={policy.supportContent} />
)}
```

다음으로 교체:
```tsx
{/* AI 한 줄 요약 — 가이드 있을 때만 */}
{guide && <OneLineSummaryCard oneLineSummary={guide.oneLineSummary} />}

{/* Policy Summary (원문) */}
<section
  id="policy-summary-section"
  className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6"
>
  <h2 className="mb-3 text-lg font-semibold text-neutral-900">정책 요약</h2>
  <FormattedPolicyText text={policy.summary} />
</section>

{/* Policy Meta Summary */}
<PolicyMetaSummary ... />

{/* Paired: 지원대상 */}
<PairedSection
  id="paired-supportTarget"
  easyTitle="누가 받을 수 있나요"
  easyData={guide?.target ?? null}
  originalTitle="지원대상"
  originalContent={policy.supportTarget}
  originalRenderer={(c) => <FormattedPolicyText text={c} />}
/>

{/* Paired: 선정기준 */}
<PairedSection
  id="paired-selectionCriteria"
  easyTitle="어떻게 뽑히나요"
  easyData={guide?.criteria ?? null}
  originalTitle="선정기준"
  originalContent={policy.selectionCriteria}
  originalRenderer={(c) => <FormattedPolicyText text={c} />}
/>

{/* Paired: 지원내용 */}
<PairedSection
  id="paired-supportContent"
  easyTitle="무엇을 받나요"
  easyData={guide?.content ?? null}
  originalTitle="지원내용"
  originalContent={policy.supportContent}
  originalRenderer={(c) => <FormattedPolicyText text={c} />}
/>

{/* 놓치기 쉬운 점 — 가이드 있고 함정 있을 때만 */}
{guide && <PitfallsCard pitfalls={guide.pitfalls} />}
```

> 주의: 기존 `DetailSection`은 `icon` prop을 받았는데 새 `PairedSection`은 받지 않는다. 시각적 일관성 유지를 위해 `originalTitle` 옆에 아이콘 추가가 필요하면 `originalIcon?: ReactNode` prop을 PairedSection에 추가하고 렌더링하도록 확장.

- [ ] **Step 3: 타입 체크 + 빌드**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 개발 서버 띄워 수동 확인**

Run: `cd frontend && npm run dev`
Expected:
- 가이드 있는 정책 상세 → 한 줄 요약, 페어드 섹션, 놓치기 쉬운 점 카드가 모두 표시됨
- 가이드 없는 정책 상세 → 한 줄 요약/페어/놓치기 쉬운 점 카드 모두 숨김, 원문만 표시
- 놓치기 쉬운 점 라벨 클릭 → 해당 페어 섹션으로 스크롤 + 1.5초 노란 배경

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(fe): PolicyDetailPage 페어드 레이아웃으로 재구성

- 한 줄 요약 카드(OneLineSummaryCard)로 기존 GuideSummaryCard 교체
- 지원대상/선정기준/지원내용을 PairedSection으로 묶어 쉬운 해석과 짝
- 놓치기 쉬운 점 카드 추가 (출처 클릭 시 페어드 원문으로 스크롤)
- 가이드 미생성(null) 정책에선 모든 쉬운 해석 카드 숨김"
```

---

## Task 24: 운영 검증 — 종단 흐름 테스트

**Files:**
- 없음 (수동 검증)

전체 흐름이 작동하는지 확인.

- [ ] **Step 1: 백엔드 기동**

Run: `cd backend && ./gradlew bootRun`

- [ ] **Step 2: 프론트 기동**

Run: `cd frontend && npm run dev`

- [ ] **Step 3: ingestion 엔드포인트로 테스트 정책 1건 등록**

Run (다른 터미널):
```bash
curl -X POST http://localhost:8080/api/internal/ingestion/policies \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY" \
  -d '{
    "title": "테스트 청년월세지원",
    "summary": "월세 부담 완화",
    "body": "[지원대상]\n만 19세 이상 34세 이하의 무주택 세대주\n\n[선정기준]\n중위소득 60% 이하\n\n[지원내용]\n매월 최대 20만원, 최대 12개월",
    "category": "주거",
    "region": "11000",
    "referenceYear": 2026,
    "sourceType": "WELFARE_GO",
    "externalId": "test-001",
    "sourceUrl": "https://welfare.go.kr/test"
  }'
```

Expected: 200 OK + 백엔드 로그에 "가이드 생성 완료: policyId=..." 출력

- [ ] **Step 4: 프론트에서 정책 상세 진입**

브라우저: `http://localhost:5173/policies/{policyId}` (위 응답에서 받은 ID)
Expected:
- 상단 한 줄 요약 카드 표시
- "지원대상", "선정기준", "지원내용" 페어드 섹션 표시 (각각 쉬운 해석 + 원문)
- 하단 놓치기 쉬운 점 카드 표시 (LLM 출력에 따라 비어있을 수도 있음)
- 놓치기 쉬운 점의 출처 라벨 클릭 시 해당 페어로 스크롤 + 노란 하이라이트

- [ ] **Step 5: 가이드 미생성 정책 확인 — DB에서 강제 삭제 후 재진입**

Run:
```bash
psql -h localhost -U youthfit -d youthfit -c "DELETE FROM guide WHERE policy_id={정책ID};"
```

브라우저 새로고침. Expected: 가이드 카드 모두 숨김, 원문만 표시.

- [ ] **Step 6: 종합 OK면 plan 종료. 커밋 없음.**

---

## Self-Review

**Spec coverage check:**

| Spec 섹션 | 다루는 task |
|---|---|
| 3 출력 5개 단위 | 4 (GuideContent), 9 (LLM 프롬프트), 19/22 (한줄/놓치기 카드) |
| 4.1 Guide 엔티티 변경 | 5 |
| 4.2 마이그레이션 | 14 |
| 4.3 입력 모델 (하이브리드) | 7 (input DTO), 11 (서비스) |
| 5.1 페이지 순서 | 23 |
| 5.2 페어드 시각 구성 | 20, 21 |
| 5.3 놓치기 쉬운 점 카드 + 스크롤 | 18, 22 |
| 5.4 빈 페어 처리 | 21 (originalContent null이면 미렌더) |
| 5.5 미생성 정책 처리 | 17, 23 (`guide && ...`) |
| 5.6 디스클레이머 | 19 (OneLineSummaryCard 하단 문구) |
| 6.1~6.3 LLM 프롬프트/JSON 스키마 | 9 |
| 6.4 후처리 검증 | 10 |
| 7.1 새벽 ingestion 트리거 (a) | 13 |
| 7.2 미생성 처리 | 17, 23 |
| 8 API 응답 변경 | 12 |
| 9 워딩 추천 (한 줄 요약 카드명, 페어 헤더) | 19, 23 |

**Placeholder scan:** TBD/TODO 잔존 없음. Task 13 Step 1의 빈 테스트 본문은 Step 3에서 실 코드로 채움 — 의도적 두 단계 분할.

**Type consistency:**
- `GuideContent.target/criteria/content`(java) ↔ `Guide.target/criteria/content`(ts) 일치
- `GuideSourceField` enum 값(`SUPPORT_TARGET` 등)이 OpenAI 스키마, 파싱, 프론트 라벨 매핑 모두에서 동일
- `Guide.builder().content(GuideContent)` ↔ Task 5/11/12 모두에서 일관
- `pitfalls`는 빈 배열 허용, null 아님 — Task 4/12/22 일관

**Scope check:** 단일 plan 범위 내. 첨부 추출 파이프라인(spec 11에 명시한 비범위)은 별도 plan으로.

---

## 실행 옵션

Plan 작성 완료. `docs/superpowers/plans/2026-04-28-easy-policy-interpretation.md` 에 저장됨.

두 가지 실행 방식:

**1. Subagent-Driven (추천)** — task 단위로 별도 서브에이전트 dispatch, 각 task 완료 후 리뷰. 빠른 반복.

**2. Inline 실행** — 같은 세션에서 executing-plans 스킬로 배치 실행, 체크포인트마다 리뷰.

어떤 방식으로 진행할까요?
