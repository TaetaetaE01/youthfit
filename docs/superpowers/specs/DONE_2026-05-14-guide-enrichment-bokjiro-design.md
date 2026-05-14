# 가이드에 enrichment 흡수 + 복지로식 섹션 구조 도입 — Design Spec

> **버전**: v0.1
> **작성일**: 2026-05-14
> **모듈**: `guide` (도메인/입력/프롬프트/검증 확장), frontend `features/guide` (카드 레이아웃 재설계)
> **선행 사이클**: `DONE_2026-05-12-youth-center-enrichment-design.md` (온통청년 enrichment 파이프라인 완성)
> **선행 사이클**: `DONE_2026-04-28-guide-accuracy-income-bracket-design.md` (highlights/그룹 분리/환산값 도입)
> **관련 PR**: #97 (qna follow-up grounding), #98 (frontend enrichment fallback)

---

## 1. 목표와 비목표

### 1.1 목표

- 온통청년 enrichment 파이프라인이 만들어 둔 `policy.enrichment.sections` 의 9개 필드를 **guide 생성의 1급 입력**으로 흡수해, 본문이 빈약한 정책에서도 풀이 정확도/풍부도가 본문 충실한 정책 수준에 근접하도록 만든다.
- 가이드 출력 스키마를 **복지로 정책 상세 화면 섹션 구조** 에 맞춰 재정렬한다. YouthFit 고유 섹션(`highlights` / `pitfalls`) 이 복지로 표준 섹션을 위/아래로 감싸는 형태.
- 출력 항목의 출처 라벨링에 **`ENRICHMENT`** 를 1급 출처로 추가해 "원본 본문 / 첨부 / AI 자동 수집" 을 사용자가 항목 단위로 구분할 수 있게 한다.
- `Guide.sourceHash` 입력에 enrichment 핑거프린트를 포함해 enrichment 변경만으로도 가이드가 자동 재생성되게 한다.

### 1.2 비목표

- 외부 정책 안내 페이지 본문 자체를 별도 `PolicyDocument` 청크로 인덱싱 (다음 사이클, D).
- `enrichment.extraAttachments` 자동 다운로드 → 첨부 청킹 파이프라인 연결 (다음 사이클, E).
- 적합도 판정(`eligibility`) 의 enrichment 활용 (이번 사이클 변경 없음).
- 복지로 측 정책의 ingestion 변경 (출력 스키마 변경은 양쪽 정책 모두 적용되지만, ingestion 워크플로우는 그대로).
- RAG / Q&A 변경 (enrichment 는 이미 청크에 포함됨, commit 21379e4).
- 다국어 / 다단계 신청 흐름 도해 / 사용자 맞춤 환산.

---

## 2. 배경

- 온통청년 enrichment 파이프라인(2026-05-12 spec) 도입 후, 외부 정책 안내 페이지에서 9개 섹션(supportTarget / supportContent / applyMethod / requiredDocuments / deadlineNote / policyOverview / eligibilityCriteria / operatingOrganization / contactPhone) 이 LLM 으로 추출되어 `policy.enrichment` jsonb 컬럼에 저장되고 있다.
- 그러나 현재 `GuideGenerationInput.of(Policy, chunks, reference)` 는 `Policy` 본문 컬럼(supportTarget / selectionCriteria / supportContent / body / contact / organization) 만을 직접 입력으로 받고, enrichment 는 **RAG 청크 경유로만** 간접 흐른다. 결과적으로 본문이 빈약한 온통청년 정책은 enrichment 가 있어도 가이드 풀이가 충분히 풍부해지지 않는다.
- 동시에 가이드 출력 스키마(`GuideContent`) 는 `oneLineSummary / highlights / target / criteria / content / pitfalls` 6개로, 복지로 정책 상세 화면 표준 섹션(서비스목적 / 지원대상 / 선정기준 / 지원내용 / 신청방법 / 신청기한 / 제출서류 / 문의처) 중 **신청방법 / 신청기한 / 제출서류 / 문의처** 4개가 출력 단위에 없다.
- 사용자(YouthFit 운영자)는 가이드 UX 를 복지로 정책 상세 화면처럼 정형화된 섹션 카드 흐름으로 가져가고 싶다고 결정. 4개 누락 섹션을 출력에 추가하고, 동시에 enrichment 를 1급 입력으로 끌어올린다.

---

## 3. 범위

### 포함 (A~C)

- `guide` 도메인 모델:
  - `GuideContent` 에 4개 섹션(`applyMethod`, `deadlineNote`, `requiredDocuments`, `contact`) 추가
  - 새 record `GuideListSection(items, sourceField, attachmentRef)` 신설
  - `GuideSourceField` enum 에 `ENRICHMENT` 추가
- `guide` 입력/생성:
  - `GuideGenerationInput` 에 `EnrichmentInput` 절 추가
  - `GuideGenerationInput.combinedSourceText` 에 `[enrichment.*]` 절 직렬화
  - `OpenAiChatClient` 시스템 프롬프트 v4 → v5 (출력 스키마 4개 필드 추가, ENRICHMENT 라벨 규칙, few-shot 예시 추가)
  - `OpenAiChatClient.buildResponseFormat` JSON 스키마 4개 필드 추가
  - `GuideValidator` 검증 6/7 추가 (신규 섹션 sourceField 유효성, 빈 items 방지)
  - `GuideGenerationService.computeHash` 입력에 enrichment 핑거프린트 포함
  - `PROMPT_VERSION` v4 → v5 증분
- Frontend:
  - `GuideListSectionCard` 신설 (4개 섹션 공통 컴포넌트)
  - `GuideSourceBadge` 신설 (ENRICHMENT/ATTACHMENT 시각화)
  - `GuideCard.tsx` 순서 재배치 (highlights 위, pitfalls 아래로 복지로 섹션 감싸기)
  - 응답 타입 4개 필드 추가
- 운영:
  - 머지 직후 admin LLM 비용 대시보드 모니터링 (PROMPT_VERSION 변경에 의한 일회성 재생성 spike)

### 제외 (D~E)

- 외부 정책 안내 페이지 cleaned text 의 PolicyDocument 청크 저장 (D, 다음 사이클).
- `enrichment.extraAttachments` 자동 다운로드 + 첨부 청킹 (E, 다음 사이클).

---

## 4. 아키텍처

### 4.1 영향 모듈

```
┌────────────────────────────────────────────────────────────┐
│ policy 모듈 (이미 존재)                                       │
│  - PolicyEnrichment.Sections (9개 필드, 변경 없음)            │
│  - PolicyUpsertedEvent 는 ingestion intake 마다 발행          │
└────┬───────────────────────────────────────────────────────┘
     ↓ (의존)
┌────────────────────────────────────────────────────────────┐
│ guide 모듈                                                   │
│  - GuideContent: 4개 NEW 섹션 추가                            │
│  - GuideListSection (NEW record)                            │
│  - GuideSourceField: ENRICHMENT 추가                         │
│  - GuideGenerationInput: EnrichmentInput 추가                │
│  - GuideGenerationService: hash 입력 변경, PROMPT_VERSION v5  │
│  - GuideValidator: 검증 6/7 추가                              │
│  - OpenAiChatClient: 시스템 프롬프트/JSON 스키마 갱신          │
└────────────────────────────────────────────────────────────┘
     ↓ (응답)
┌────────────────────────────────────────────────────────────┐
│ frontend                                                     │
│  - GuideListSectionCard.tsx (NEW)                            │
│  - GuideSourceBadge.tsx (NEW)                                │
│  - GuideCard.tsx 순서 재배치                                  │
│  - guide 응답 타입 4개 필드 추가                              │
└────────────────────────────────────────────────────────────┘
```

의존 방향: `guide → policy` (기존). DB 스키마 변경 없음 (`Guide.content` JSONB).

### 4.2 손대지 않는 컴포넌트

- ingestion / n8n 워크플로우 — enrichment 파이프라인은 이미 운영 중, 변경 없음.
- RAG (`PolicyDocument` 청크) — enrichment 는 이미 청크 입력에 포함 (21379e4).
- 적합도 (`eligibility`) — 변경 없음.
- Q&A (`qna`) — 변경 없음.
- `eligibility` / `qna` 의 LLM 프롬프트 — 변경 없음.
- `IncomeBracketAnnotator`, `IncomeBracketReference` — 변경 없음.
- DB 마이그레이션 — 불필요.

---

## 5. 도메인 모델

### 5.1 신규 record: `GuideListSection`

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

- `items` 1개짜리(예: 신청기한 "2026-03-01 ~ 2026-05-31") 도 허용. 시각 렌더에서 불릿 생략.
- `attachmentRef` 는 sourceField 가 `ATTACHMENT` 일 때만 not-null. 검증 4(기존)와 같이 정합성 강제.
- 섹션 자체가 데이터 없으면 `GuideContent` 에서 해당 필드 자체를 `null` 로 둔다.

### 5.2 `GuideContent` 새 구조

```java
public record GuideContent(
        String oneLineSummary,
        List<GuideHighlight> highlights,
        GuidePairedSection target,
        GuidePairedSection criteria,
        GuidePairedSection content,
        GuideListSection applyMethod,          // NEW
        GuideListSection deadlineNote,         // NEW
        GuideListSection requiredDocuments,    // NEW
        GuideListSection contact,              // NEW
        List<GuidePitfall> pitfalls
) {
    public GuideContent {
        if (oneLineSummary == null || oneLineSummary.isBlank()) {
            throw new IllegalArgumentException("oneLineSummary는 비어있을 수 없습니다");
        }
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
        // 4개 NEW 섹션은 nullable — 정책마다 정보 부재 가능
    }
}
```

- 4개 NEW 섹션 모두 nullable. LLM 이 본문/enrichment 어느 쪽에서도 정보를 찾지 못하면 `null` 반환 → 프론트는 카드 미렌더.
- 기존 `target / criteria / content` 의 `GuidePairedSection` (그룹 분리 강제) 유지. 차상위 분류 등 그룹 의미가 살아있는 섹션이라 변경 없음.
- `highlights / pitfalls` 도 그대로. 시각적 위치(위/아래로 정형 섹션 감싸기) 는 frontend 렌더 순서로 결정.

### 5.3 `GuideSourceField` 확장

```java
public enum GuideSourceField {
    SUPPORT_TARGET,        // policy.supportTarget
    SELECTION_CRITERIA,    // policy.selectionCriteria
    SUPPORT_CONTENT,       // policy.supportContent
    BODY,                  // policy.body
    ATTACHMENT,            // 첨부 청크
    ENRICHMENT             // NEW: policy.enrichment.sections.*
}
```

- 기존 `enforceAttachmentSourceField` / `filterInvalidSourceFields` 후처리에 `ENRICHMENT` 허용 분기 추가.
- `filterInvalidSourceFields` 의 `nonEmpty` 화이트리스트에 `policy.enrichment` 가 `isExposable() == true` 면 `ENRICHMENT` 추가.

### 5.4 본문/enrichment 필드 ↔ GuideContent 섹션 매핑

| GuideContent 섹션 | Policy 본문 입력 | enrichment.sections 입력 | 비고 |
|---|---|---|---|
| `oneLineSummary` | `summary` | `policyOverview` (보조) | LLM 이 두 입력 통합 후 1문장 압축 |
| `highlights[]` | LLM 추출 (전체 본문) | LLM 추출 (enrichment 전체) | 항목 단위 sourceField |
| `target` (paired) | `supportTarget` | `supportTarget` | 그룹 분리 강제 유지 |
| `criteria` (paired) | `selectionCriteria` | `eligibilityCriteria` | enrichment 의 별칭 매핑 |
| `content` (paired) | `supportContent` | `supportContent` | — |
| `applyMethod` (list) | (본문 일부 가능) | `applyMethod` | NEW |
| `deadlineNote` (list) | `applyStart` ~ `applyEnd` 컬럼 보조 | `deadlineNote` | NEW. 본문/enrichment 둘 다 비면 apply* 컬럼으로 fallback (Service 단계) |
| `requiredDocuments` (list) | (본문 일부 가능) | `requiredDocuments` | NEW |
| `contact` (list) | `contact` + `organization` | `operatingOrganization` + `contactPhone` | NEW. 두 출처가 같은 섹션으로 합쳐짐 |
| `pitfalls[]` | LLM 추출 | LLM 추출 | 항목 단위 sourceField |

- `policyOverview` 는 별도 출력 섹션을 만들지 않고, LLM 이 `oneLineSummary` 와 `highlights` 를 만들 때 보조 입력으로만 활용. 복지로의 "서비스 목적" 별도 섹션을 신설하지 않는 이유는 YouthFit 의 `oneLineSummary` 가 이미 그 역할을 하기 때문.
- `eligibilityCriteria` 는 `selectionCriteria` 와 의미가 같음 → 같은 섹션(`criteria`) 으로 합쳐짐. LLM 프롬프트에 별칭 매핑 안내 명시.
- `operatingOrganization` + `contactPhone` 은 `contact` 섹션의 `items` 리스트 안에 함께 표시 (예: `["서울특별시 청년정책담당관", "02-2133-6586"]`).

---

## 6. 입력

### 6.1 `GuideGenerationInput` 확장

```java
public record GuideGenerationInput(
        Long policyId,
        String title,
        Integer referenceYear,
        String summary,
        String body,
        // 본문(원본) 필드 — 그대로
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String contact,
        String organization,
        // NEW: enrichment 절
        EnrichmentInput enrichment,
        List<ChunkInput> chunks,
        IncomeBracketReference referenceData
) {

    public GuideGenerationInput { /* 기존 검증 + chunks 사본 */ }

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference referenceData) {
        EnrichmentInput enrichmentInput = null;
        if (policy.getEnrichment() != null && policy.getEnrichment().isExposable()) {
            PolicyEnrichment.Sections s = policy.getEnrichment().sections();
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
                    policy.getEnrichment().fetchedAt(),
                    policy.getEnrichment().sourceUrl(),
                    policy.getEnrichment().confidence()
            );
        }
        // ... 기존 chunk 변환 로직 ...
        return new GuideGenerationInput(/* ... */);
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
}
```

- enrichment 가 `null` 이거나 `status != OK` 이거나 `confidence < 0.6` 이면 `enrichment` 절 자체를 `null` 로 주입. `isExposable()` 메서드 그대로 활용.
- 본문 우선/enrichment 우선 같은 fallback 로직은 입력 단계에서 **하지 않는다**. LLM 이 두 출처를 보고 더 풍부한 쪽을 골라 생성한다는 결정에 충실.

### 6.2 `combinedSourceText` 직렬화 갱신

```
[summary]
... 정책 요약 ...

[body]
... 정책 본문 ...

[supportTarget]
... 원본 본문 supportTarget ...

[selectionCriteria] ...
[supportContent] ...
[contact] ...
[organization] ...

[enrichment.meta]
sourceUrl=https://...
fetchedAt=2026-05-12T04:12:33Z
confidence=0.82
(LLM 은 이 메타 절을 출력 생성에 직접 인용하지 않는다. 출처 라벨이나 신뢰도 판단의 참고 정보로만 활용)

[enrichment.policyOverview]
... 외부 페이지에서 추출한 정책 개요 ...

[enrichment.supportTarget]
... AI 추출 지원대상 ...

[enrichment.eligibilityCriteria] ...
[enrichment.supportContent] ...
[enrichment.applyMethod] ...
[enrichment.requiredDocuments] ...
[enrichment.deadlineNote] ...
[enrichment.operatingOrganization] ...
[enrichment.contactPhone] ...

[referenceYear]
2026

[chunk-0 source=ATTACHMENT attachment-id=...]
...
```

- 본문 절들은 enrichment 절들보다 **위쪽**에 놓아 LLM 이 본문을 1차 출처로 인식하도록 한다.
- enrichment 의 `policyOverview` 는 별도 출력 섹션이 없지만 LLM 의 1문장 요약 입력으로 노출.

---

## 7. LLM 프롬프트 / JSON 스키마

### 7.1 시스템 프롬프트 변경 (v4 → v5)

`OpenAiChatClient.SYSTEM_PROMPT` 에 추가/변경할 핵심 규칙:

1. **출력 스키마 확장**: `applyMethod / deadlineNote / requiredDocuments / contact` 4개 필드를 `GuideListSection` 모양 또는 `null` 로 반환.
2. **신규 sourceField `ENRICHMENT` 사용 규칙**:
   - "외부 정책 안내 페이지에서 AI 가 자동 추출한 보조 정보다."
   - "원본 본문(`[body]`, `[supportTarget]`, `[selectionCriteria]`, `[supportContent]`, `[contact]`) 에 같거나 유사한 정보가 있으면 본문의 sourceField (`BODY` / `SUPPORT_TARGET` / `SELECTION_CRITERIA` / `SUPPORT_CONTENT`) 를 **우선** 사용한다."
   - "본문에 없고 enrichment 절에만 있는 정보만 `ENRICHMENT` 로 라벨링한다."
3. **`eligibilityCriteria` 별칭**: enrichment 의 `eligibilityCriteria` 는 본문의 `selectionCriteria` 와 같은 의미로 취급하라.
4. **`contact` 섹션 합성 규칙**: `operatingOrganization` 과 `contactPhone` 을 같이 묶어 `contact.items` 에 줄 단위로 배치하라. 예: `["서울특별시 청년정책담당관", "02-2133-6586", "youth@seoul.go.kr"]`.
5. **빈 섹션 처리**: 본문과 enrichment 양쪽에 정보가 없으면 절대 추측해서 채우지 말고 `null` 반환. 빈 items 리스트 금지.
6. **`policyOverview` 보조 활용**: `oneLineSummary` 와 `highlights` 를 만들 때 `policyOverview` 도 참고하라. 단 별도 출력 섹션은 만들지 마라.
7. **신청기한 형식**: `deadlineNote.items` 는 가능하면 `YYYY-MM-DD ~ YYYY-MM-DD` 또는 사람이 읽는 형태("상시", "예산 소진 시까지") 로 정리.
8. **few-shot 예시**: 2개 추가
   - 예시 A: 본문 풍부 + enrichment 일부 보강 → 4개 NEW 섹션 일부 enrichment 라벨
   - 예시 B: 본문 빈약 + enrichment 풍부 (온통청년 케이스) → 4개 NEW 섹션 대부분 ENRICHMENT 라벨

### 7.2 JSON 스키마 변경

`OpenAiChatClient.buildResponseFormat` 의 `json_schema.schema.properties` 에 4개 필드 추가:

```json
{
  "applyMethod": {
    "anyOf": [
      { "type": "null" },
      {
        "type": "object",
        "properties": {
          "items": { "type": "array", "items": { "type": "string" }, "minItems": 1 },
          "sourceField": { "type": "string", "enum": ["SUPPORT_TARGET", "SELECTION_CRITERIA", "SUPPORT_CONTENT", "BODY", "ATTACHMENT", "ENRICHMENT"] },
          "attachmentRef": { /* 기존 attachmentRef 스키마 재사용 */ }
        },
        "required": ["items", "sourceField"]
      }
    ]
  },
  "deadlineNote": { /* 동일 */ },
  "requiredDocuments": { /* 동일 */ },
  "contact": { /* 동일 */ }
}
```

- `sourceField` enum 에 `ENRICHMENT` 추가는 4개 NEW 섹션뿐 아니라 `highlights` / `pitfalls` 의 `sourceField` 정의에도 적용.

---

## 8. 검증 (GuideValidator)

기존 검증 1~5 그대로 유지하고 신규 두 가지 추가:

### 8.1 검증 6: 신규 섹션 sourceField 유효성

- 4개 NEW 섹션(`applyMethod / deadlineNote / requiredDocuments / contact`) 의 `sourceField` 가 입력으로 들어온 절에 실제로 존재하는지 검사.
- 예: 입력 enrichment 가 null 인데 `applyMethod.sourceField == ENRICHMENT` → 환각 → 섹션 자체 폐기 (null 처리).
- 예: 본문 `contact` 가 비어있고 enrichment 도 contactPhone 만 있고 본문 `contact` 가 null 인데 `contact.sourceField == SUPPORT_CONTENT` → 환각 폐기.
- 폐기만 하고 재시도는 안 함 (재시도 한 번 비용 통제, 검증 1~3 만 재시도 트리거).

### 8.2 검증 7: 빈 items 방지

- 4개 NEW 섹션의 `items` 가 빈 리스트면 섹션 자체 폐기 (null 처리).
- 도메인 record 가 이미 빈 리스트 거부하지만, LLM 응답 파싱 후 후처리 단계에서도 한 번 더 가드.

### 8.3 후처리

`GuideGenerationService` 에서 4개 NEW 섹션도 기존 후처리 단계와 동일하게 처리:
- `enforceAttachmentSourceField` 후처리에 4개 섹션 분기 추가 (attachmentRef 가 not-null 인데 sourceField 가 ATTACHMENT 가 아니면 ATTACHMENT 로 보정)
- `filterInvalidSourceFields` 화이트리스트에 enrichment 노출 가능 시 `ENRICHMENT` 추가

---

## 9. Hash / 트리거

### 9.1 hash 입력 변경

`GuideGenerationService.computeHash` 에 enrichment 핑거프린트 추가:

```java
private String computeHash(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
    StringBuilder sb = new StringBuilder();
    // ... 기존 ...
    if (policy.getEnrichment() != null && policy.getEnrichment().isExposable()) {
        // confidence 는 hash 에 넣지 않는다 — 동일 enrichment 재추출에서 미세한 confidence 변동만으로
        // 매번 재생성되는 것을 막기 위함. enrichment 재추출 시 fetchedAt 이 항상 갱신되므로
        // 변경 감지엔 fetchedAt 만으로 충분.
        sb.append("|enrich:")
          .append(policy.getEnrichment().fetchedAt() == null
                  ? ""
                  : policy.getEnrichment().fetchedAt().toString());
    }
    sb.append("|prompt:").append(PROMPT_VERSION);   // v5
    sb.append("|annotator:").append(ANNOTATOR_VERSION);
    return sha256(sb.toString());
}
```

- enrichment 변경(fetchedAt / confidence 갱신) 만으로도 hash 가 변경되어 자동 재생성 트리거.
- `PROMPT_VERSION` v4 → v5 자체로도 기존 가이드 hash 가 모두 무효화됨 (운영 일회성 spike, §11 참조).

### 9.2 재생성 이벤트 트리거

- enrichment 갱신은 ingestion intake 흐름을 거치므로 `IngestionService` 가 매번 `PolicyUpsertedEvent` 를 발행한다 (확인 완료: `IngestionService.java:164`).
- 따라서 `GuideGenerationEventListener.onPolicyUpserted` 가 그대로 동작, 별도 이벤트 신설 불요.

---

## 10. Frontend

### 10.1 가이드 카드 레이아웃

```
┌──────────────────────────────────────────────────────────────┐
│ 정책 제목  · 카테고리 · 마감일                                  │
│                                                              │
│ [한 줄 요약]                                                  │
│ [이 정책의 특징]                                              │
│ [지원대상]                                                    │
│ [선정기준]                                                    │
│ [지원내용]                                                    │
│ [신청방법]               🤖 AI 자동수집                       │
│ [신청기한]               🤖 AI 자동수집                       │
│ [제출서류]               🤖 AI 자동수집                       │
│ [문의처]                                                     │
│ [놓치기 쉬운 점]                                              │
│                                                              │
│ [공식 신청 채널 →]   [북마크 ★]                              │
└──────────────────────────────────────────────────────────────┘
```

- highlights(특징) 가 정형 섹션 위, pitfalls(놓치기 쉬운 점) 가 아래.
- 4개 NEW 섹션 카드 헤더 우측에 출처 배지 (sourceField 가 `ENRICHMENT` 일 때 "🤖 AI 자동수집" 배지).

### 10.2 컴포넌트 구조

```
src/features/guide/components/
  GuideCard.tsx                       (큰 폭 개편 — 순서 재배치, 4개 카드 추가)
    ├ GuideOneLineSummary.tsx         (기존)
    ├ GuideHighlightsCard.tsx         (기존)
    ├ GuideTargetCard.tsx             (기존, PairedSection 렌더)
    ├ GuideCriteriaCard.tsx           (기존)
    ├ GuideContentCard.tsx            (기존)
    ├ GuideListSectionCard.tsx        (NEW — 4개 섹션 공통 렌더)
    │   ├ props: title, listSection (GuideListSection | null), icon
    │   └ null 이면 자체 미렌더
    ├ GuidePitfallsCard.tsx           (기존)
    └ GuideSourceBadge.tsx            (NEW — sourceField 시각화)
        └ ENRICHMENT 만 시각 배지, BODY/SUPPORT_* 는 미표시
```

### 10.3 출처 배지 규칙

| sourceField | 항목 단위 (highlights/pitfalls 내) | 섹션 헤더 (4개 NEW 카드) |
|---|---|---|
| `BODY` / `SUPPORT_TARGET` / `SELECTION_CRITERIA` / `SUPPORT_CONTENT` | 미표시 (기본 출처) | 미표시 |
| `ATTACHMENT` | 📎 + 첨부명·페이지 (기존 그대로) | 미표시 (4개 섹션은 첨부 출처 가능성 낮음) |
| `ENRICHMENT` | 🤖 작은 배지 | "🤖 AI 자동수집" 배지 + ⓘ 정보 아이콘 |

- ENRICHMENT 정보 아이콘 hover/tap 툴팁: "외부 정책 안내 페이지를 AI 가 자동으로 정리한 정보입니다. 원본은 공식 신청 채널을 확인하세요."
- 시각 강도: 모두 회색/투명 톤 + 작은 글자. 본문 흐름을 방해하지 않음.

### 10.4 응답 타입

```typescript
type GuideSourceField =
  | 'BODY' | 'SUPPORT_TARGET' | 'SELECTION_CRITERIA'
  | 'SUPPORT_CONTENT' | 'ATTACHMENT' | 'ENRICHMENT';

type GuideListSection = {
  items: string[];
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
};

type GuideContent = {
  oneLineSummary: string;
  highlights: GuideHighlight[];
  target: GuidePairedSection;
  criteria: GuidePairedSection;
  content: GuidePairedSection;
  applyMethod: GuideListSection | null;        // NEW
  deadlineNote: GuideListSection | null;       // NEW
  requiredDocuments: GuideListSection | null;  // NEW
  contact: GuideListSection | null;            // NEW
  pitfalls: GuidePitfall[];
};
```

### 10.5 빈 / 부분 출력 처리

- `applyMethod === null` 등이면 해당 카드 자체 미렌더.
- `items.length === 1` 이면 불릿 생략하고 한 줄로.
- highlights/pitfalls 빈 리스트면 카드 미렌더 (기존 동작 유지).

---

## 11. 비용 / 운영 안전장치

### 11.1 PROMPT_VERSION v4 → v5 일회성 영향

- 모든 기존 가이드의 `sourceHash` 무효화 → `CostGuard.allowlist` 통과 정책 N건이 한 차례 자동 재생성.
- 예상 비용: N (allowlist 정책 수) × gpt-4o-mini 호출 1회 (입력 +30~40% 증가) × 검증 위반 시 최대 1회 재시도 ≈ 일회성 spike.
- `admin-llm-cost` 대시보드에서 머지 후 24시간 모니터링. 일일 한도 근접 시 운영자 차단 가능 (기존 운영 패턴).

### 11.2 정상 운영 시 비용

- 정책별 enrichment 갱신은 변경 시에만 발생 (n8n 워크플로우의 contentHash 비교).
- 가이드 재생성도 hash 입력 변경 시에만 발생 → 비용 추가 증가 미미.
- enrichment 가 있는 정책만 enrichment 절 주입 → 본문 충실한 복지로 정책은 토큰 증가 없음.

### 11.3 CostGuard / Cost 관리

- `CostGuard` 메커니즘 변경 없음. allowlist 기반 보호망 그대로.
- 일일 한도 / Micrometer 카운터 추가 없음 (이번 사이클 비범위).

---

## 12. 점진 롤아웃

1. **1단계 — 백엔드 머지**: 도메인 모델 확장 + 프롬프트 v5 + Validator + hash 입력 변경. allowlist 정책 자동 재생성 시작. frontend 가 아직 4개 NEW 필드를 무시하므로 사용자 변화 없음.
2. **2단계 — 검증 게이트**: 운영자가 admin 대시보드에서 재생성된 가이드 샘플 5~10건 시각 검증. 4개 NEW 섹션 누락률 / 환각률 확인. enrichment 가 있는 정책 / 없는 정책 양쪽 비교.
3. **3단계 — frontend 머지**: `GuideListSectionCard` + `GuideSourceBadge` + `GuideCard` 순서 재배치. 사용자에게 4개 NEW 섹션 노출 시작.
4. **롤백 경로**:
   - 백엔드 단독 롤백: frontend 는 `applyMethod || null` 가드로 안전.
   - frontend 단독 롤백: 백엔드 응답에 4개 NEW 필드가 있어도 frontend 가 무시.
   - 양쪽 동시 롤백: hash 가 v4 로 돌아가지만 기존 가이드는 v5 hash 로 저장된 상태 → 한 번 더 자동 재생성 (CostGuard 통제).

---

## 13. 테스트 전략

### 13.1 백엔드 (`spring-test` 컨벤션)

| 종류 | 대상 | 시나리오 |
|---|---|---|
| 단위 | `GuideContent` record | 4개 NEW 섹션 nullable 허용, oneLineSummary 빈 값 거부 |
| 단위 | `GuideListSection` record | items null/empty 거부, sourceField null 거부, attachmentRef nullable |
| 단위 | `GuideGenerationInput.of` | enrichment null 인 정책 / isExposable false 인 정책 / OK 정책 각각 EnrichmentInput 매핑 |
| 단위 | `GuideGenerationInput.combinedSourceText` | enrichment null → `[enrichment.*]` 절 미포함, enrichment 있음 → 절 직렬화 |
| 단위 | `GuideGenerationService.computeHash` | enrichment fetchedAt 다른 두 정책의 hash 다름, enrichment 동일하면 hash 동일, PROMPT_VERSION 다르면 hash 다름 |
| 단위 | `GuideValidator.validate` 검증 6 | enrichment 없는데 ENRICHMENT 라벨링 → 폐기. 본문 contact 없는데 SUPPORT_CONTENT 라벨링 → 폐기 |
| 단위 | `GuideValidator.validate` 검증 7 | 빈 items 섹션 → 폐기 |
| 통합 | `GuideGenerationService.generateGuide` | enrichment OK 정책 / enrichment null 정책 / confidence 미달 정책 각각 호출 후 결과 일관성. `GuideLlmProvider` 는 fake stub |
| 슬라이스 | `GuideController.findGuideByPolicyId` | 응답 JSON 에 4개 NEW 필드 직렬화 (null 인 경우 포함), GuideSourceField.ENRICHMENT 직렬화 |
| 회귀 | 기존 검증 1~5 통과 시나리오 | 기존 케이스가 4개 NEW 섹션 추가 후에도 그대로 통과 |

### 13.2 Frontend

| 종류 | 대상 | 시나리오 |
|---|---|---|
| 단위 | `GuideListSectionCard` | items 다중 / items 1개 (불릿 생략) / null section (미렌더) 분기 |
| 단위 | `GuideSourceBadge` | ENRICHMENT 툴팁 텍스트, ATTACHMENT 첨부명 표시, 기본 출처 미표시 |
| 통합 | `GuideCard` | 4개 NEW 섹션 nullable 조합 전수 (4개 모두 null / 일부 null / 모두 있음) 시 렌더 일관성 |
| 시각 회귀 | Storybook | 4개 NEW 섹션 카드의 출처 배지 시각 비교 |

### 13.3 E2E

- 실 정책 1건 (서울시 청년월세지원 등 enrichment 가 풍부한 케이스) 으로 가이드 카드 전체 출력 시각 검증.
- 본문 빈약 + enrichment 풍부한 온통청년 정책 1건으로 4개 NEW 섹션이 enrichment 출처로 채워지는지 확인.

---

## 14. 관찰 가능성

- `GuideGenerationService.generateGuide` 완료 시 로그에 추가 필드:
  - `enrichment_used`: boolean (입력에 enrichment 포함 여부)
  - `new_sections_present`: List<String> (applyMethod/deadlineNote/requiredDocuments/contact 중 not-null 인 섹션)
  - `enrichment_source_count`: int (응답에서 sourceField == ENRICHMENT 인 항목 수)
- admin LLM 비용 대시보드 / Q&A 캐시 대시보드 — 변경 없음.

---

## 15. 미래 확장 (다음 사이클 후보)

- **D 사이클** (외부 본문 청킹): n8n 의 cleaned text(최대 8000자) 를 `PolicyDocument` 청크로 저장 → RAG/Guide 입력에 본문 청크 추가. 가이드 풀이가 5개 LLM 추출 섹션 외에도 원문 어휘 인용 가능해짐.
- **E 사이클** (extraAttachments 청킹): `enrichment.extraAttachments` 의 PDF/HWP 를 기존 첨부 다운로드 / Tika/Hwp 추출 / 청킹 파이프라인에 연결. 자동 발견 첨부도 가이드 입력으로 포함.
- 적합도 판정에 enrichment 활용: `supportTarget` / `eligibilityCriteria` 를 룰 추출 LLM 입력으로.
- 어드민 가이드 재처리 트리거: 정책별 수동 재생성 버튼.
- enrichment confidence 임계값 카테고리별 튜닝.

---

## 16. 의존 관계 정리

- `policy → guide`: `PolicyEnrichment` 가 1급 입력. 변경 없음.
- `ingestion → policy`: 변경 없음 (intake 시점에 PolicyUpsertedEvent 발행 이미 동작).
- `guide → frontend`: `GuideResponse` 에 4개 NEW 필드 + ENRICHMENT sourceField 노출.
- `rag` / `qna` / `eligibility`: 변경 없음.
- n8n 워크플로우: 변경 없음.
