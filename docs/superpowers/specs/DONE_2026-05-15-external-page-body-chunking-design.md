# 외부 정책 안내 페이지 본문 청킹 — Design Spec

> **버전**: v1.0
> **작성일**: 2026-05-15
> **최종 갱신**: 2026-05-15 (§16 검토 포인트 5건 합의 완료)
> **모듈**: `ingestion`, `rag`, `policy`, n8n `youth-center-seoul.json`
> **선행 사이클**: `DONE_2026-05-12-youth-center-enrichment-design.md` (온통청년 enrichment 파이프라인)
> **선행 사이클**: `2026-05-14-guide-enrichment-bokjiro-design.md` (PR #100 머지 완료 — 2026-05-14)
> **D 사이클 — 가이드 후속 시리즈 中 외부 본문 청킹**

---

## 0. 전제 (선행 사이클 의존)

본 spec 의 모든 도메인 가정은 **PR #100 (`feat/guide-enrichment-bokjiro`) 머지가 main 에 반영된 상태**를 전제로 한다 (2026-05-14 머지 완료). 구체적으로:

- `GuideSourceField` enum 이 6개 값(`SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY / ATTACHMENT / ENRICHMENT`)을 가진다. ENRICHMENT 는 #100 에서 도입.
- `PolicyEnrichment` 에 9개 sections + cleanedText 가 아닌 8000자 cap cheerio 텍스트가 n8n 내부에서 처리되고 있다. cleanedText 의 ingestion 흐름 노출은 본 사이클의 범위.
- `GuideContent` 에 4개 신규 섹션(`applyMethod / deadlineNote / requiredDocuments / contact`) 이 추가되어 있다.
- `OpenAiChatClient.SYSTEM_PROMPT` 가 v5 (ENRICHMENT 라벨 사용 규칙 + 변환 예시 7·8 포함).

본 사이클은 #100 머지 위에서 진행된다.

---

## 1. 목표와 비목표

### 1.1 목표

- 온통청년 enrichment 파이프라인이 cheerio 로 정리한 **cleaned text(최대 8000자)** 를 별도 `PolicyDocument` 청크로 보관해, RAG / Guide 입력에 외부 페이지 원문 어휘가 그대로 흐르도록 한다.
- 현재 enrichment 파이프라인은 cleaned text 에서 5~9개 섹션만 LLM 으로 추출하고 본문은 버린다. 그 결과 가이드 풀이와 Q&A 답변이 LLM 추출 섹션의 표현으로만 작성되어 **원문 인용·세부 정보 표현이 약하다**. 청크화로 이 갭을 메운다.
- 신뢰 라벨링을 명확히 유지한다: 새 청크의 source 는 `ENRICHMENT_BODY` 로 구분되어 본문(`BODY`) / 첨부(`ATTACHMENT`) 청크와 명시적으로 분리된다.

### 1.2 비목표

- `enrichment.extraAttachments` 의 PDF/HWP 자동 다운로드 및 첨부 청킹 (E 사이클).
- 가이드 출력 도메인 모델 변경 — `GuideContent` 구조는 그대로 유지.
- `GuideSourceField` enum 확장 — `ENRICHMENT_BODY` 라벨은 청크 메타데이터에만 존재하고, 가이드 출력 sourceField 는 기존 6개(`SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY / ATTACHMENT / ENRICHMENT`)를 그대로 사용.
- 외부 페이지가 단독으로 변경되었을 때의 재청킹 (현재는 enrichment 자체가 변경될 때만 재청킹).
- 복지로 정책 — 이번 사이클은 온통청년 enrichment 가 있는 정책에 한정.
- n8n LLM 추출(`enrichment.sections`) 비활성화 — 청크와 LLM 추출은 보완 관계로 **공존**한다.

---

## 2. 배경

- enrichment 파이프라인(2026-05-12 spec)이 외부 정책 안내 페이지를 HTTP fetch → cheerio boilerplate 제거 → cleaned text(8000자 cap) → OpenAI gpt-4o-mini JSON 추출 흐름으로 처리한다.
- 가이드 흡수(2026-05-14 spec) 이후 가이드는 9개 sections 와 정책 본문을 둘 다 LLM 입력으로 받지만, **cleaned text 그 자체**는 여전히 ingestion intake 에 전달되지 않고 n8n 내부에서만 살았다 사라진다.
- 결과적으로:
  - 가이드가 LLM 추출 섹션 외 원문 디테일(예: "신청서 양식 다운로드 위치", "예외 사례")을 인용하지 못함
  - Q&A(`RagSearchService`)가 외부 페이지에만 있는 정보를 검색하지 못함 (현재는 정책 본문 + 첨부 청크만 인덱싱됨)
- cleaned text 는 이미 boilerplate 제거가 끝난 상태이므로 추가 정제 없이 RAG 청크 분할 입력으로 그대로 쓸 수 있다.

---

## 3. 범위

### 포함

- **n8n 워크플로우**: cleaned text 를 ingestion intake payload 의 `rawData.enrichment` 객체에 `cleanedText` 필드로 추가 전달.
- **ingestion 모듈**: `IngestionPolicyRequest.rawData.enrichment.cleanedText` 수신 → `PolicyEnrichment.cleanedText` 컬럼/필드에 보관.
- **policy 도메인**: `PolicyEnrichment` record 에 `cleanedText` 추가. `policy.enrichment` jsonb 컬럼이라 **DDL 불요** — record 필드만 추가하면 기존 row 는 `cleanedText=null` 로 deserialize. Jackson `@JsonIgnoreProperties(ignoreUnknown=true)` 또는 record canonical constructor 기본값 처리는 기존 `PolicyEnrichment` 패턴 그대로.
- **rag 모듈**:
  - `RagIndexingService` 가 정책 인덱싱 시 `policy.enrichment.cleanedText` 를 추가 청크 source 로 처리
  - 새 `PolicyDocumentSource` enum 값 `ENRICHMENT_BODY` 추가 (기존 `BODY` / `ATTACHMENT` 와 병렬)
  - `PolicyDocument` 에 source 컬럼이 없다면 이번에 추가 (현재는 attachmentId 유무로 BODY/ATTACHMENT 구분)
  - 청크 분할: 기존 `BODY` 청크와 동일한 splitter 재활용 (chunk size / overlap 동일)
  - 임베딩: 기존 OpenAI text-embedding-3-small 그대로 사용
- **guide 모듈**: 변경 없음. `GuideGenerationInput.combinedSourceText` 의 `[chunk-N source=...]` 라벨이 이미 attachment 외 경우를 `BODY` 로 직렬화하므로, `ENRICHMENT_BODY` 도 라벨 문자열을 따라가 자동 노출됨 (사소한 라벨 문자열 확장만 필요).
- **qna 모듈**: 변경 없음. Q&A 답변 시 `RagSearchService` 가 거리 기반으로 모든 청크를 동일하게 검색하므로 `ENRICHMENT_BODY` 청크도 자연스럽게 포함됨.

### 제외

- `extraAttachments` 자동 다운로드 + 첨부 청킹 (E 사이클).
- 외부 페이지 단독 변경 감지 (현재는 n8n contentHash 가 API 응답 변동만 감지).
- 가이드 출력 sourceField 확장.
- 청크 신뢰도 가중치(예: ENRICHMENT_BODY 는 BODY 보다 낮은 score) — 현재 단순 cosine distance 만으로 충분.

---

## 4. 아키텍처

### 4.1 데이터 흐름

```
[n8n youth-center-seoul]
  (기존) cheerio cleanText (8000자 cap)
       ↓
  (NEW) IngestPolicyRequest.rawData.enrichment.cleanedText 에 첨부
       ↓
[ingestion 모듈]
  IngestionService.receivePolicy()
       ↓
  PolicyEnrichment.cleanedText 보관 (jsonb 안)
       ↓
  PolicyUpsertedEvent 발행
       ↓
[rag 모듈: RagIndexingEventListener]
  RagIndexingService.reindex(policy)
       ↓
  기존 BODY 청크 + ATTACHMENT 청크 + (NEW) ENRICHMENT_BODY 청크
       ↓
  각각 임베딩 → PolicyDocument 저장
       ↓
[guide 모듈]
  GuideGenerationInput.of() → policy + chunks
  combinedSourceText 에 [chunk-N source=ENRICHMENT_BODY] 라벨 포함
       ↓
[qna 모듈]
  RagSearchService.search(question)
       ↓
  거리 기반 top-k 청크 (BODY / ATTACHMENT / ENRICHMENT_BODY 혼합)
```

### 4.2 영향 모듈

| 모듈 | 변경 강도 | 핵심 변경 |
|---|---|---|
| n8n | 小 | 워크플로우 마지막 단계에서 cleaned text 를 enrichment 객체에 부착 |
| `ingestion` | 中 | intake DTO 확장 + IngestionService 가 cleanedText 를 PolicyEnrichment 에 매핑 |
| `policy` | 小 | `PolicyEnrichment` record 에 `cleanedText` 필드 추가 (jsonb 컬럼이라 DDL 불요) |
| `rag` | **大** | `PolicyDocumentSource` enum 신설, `RagIndexingService` 가 ENRICHMENT_BODY 청크 생성, `PolicyDocument` 에 source 컬럼 추가 |
| `guide` | 小 | `combinedSourceText` 의 chunk 라벨 문자열에 source 값 매핑 추가 (기존 분기 한 줄 확장) |
| `qna` | 변경 없음 | RagSearchService 가 source 무관하게 거리 기반 검색 |
| DB | 마이그레이션 1개 (DDL) | `policy_document` 테이블에 `source` 컬럼(varchar) 추가. **`policy.enrichment` jsonb 는 스키마 변경 없음** — record 필드 추가만 |

---

## 5. 도메인 모델

### 5.1 `PolicyEnrichment.cleanedText` 추가

```java
public record PolicyEnrichment(
        String sourceUrl,
        Instant fetchedAt,
        String extractor,
        Double confidence,
        EnrichmentStatus status,
        Sections sections,
        List<ExtraAttachment> extraAttachments,
        String cleanedText        // NEW
) {
    // ...
}
```

- `cleanedText`: nullable. n8n 이 cleanedText 를 전달하지 않거나 너무 짧으면 null.
- 최대 길이 8000자 (n8n 단에서 이미 cap).
- masking: enrichment 가 `isExposable() == false` 라도 cleanedText 자체는 보관(향후 재청킹용). 단 사용자 응답에는 노출되지 않음.

### 5.2 `PolicyDocumentSource` enum 신설

```java
package com.youthfit.rag.domain.model;

public enum PolicyDocumentSource {
    BODY,              // policy.body / supportTarget / supportContent 등 정책 본문 컬럼
    ATTACHMENT,        // attachment 추출 텍스트
    ENRICHMENT_BODY    // policy.enrichment.cleanedText
}
```

### 5.3 `PolicyDocument` 에 source 컬럼 추가

```java
@Entity
@Table(name = "policy_document")
public class PolicyDocument {
    // ... 기존 필드 ...
    
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private PolicyDocumentSource source;
}
```

마이그레이션:
```sql
ALTER TABLE policy_document
  ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'BODY';

-- attachment_id 가 있는 기존 row 는 ATTACHMENT 로 표시
UPDATE policy_document SET source = 'ATTACHMENT' WHERE attachment_id IS NOT NULL;

-- DEFAULT 제거 (앞으로는 명시 입력 필수)
ALTER TABLE policy_document ALTER COLUMN source DROP DEFAULT;
```

---

## 6. ingestion 변경

### 6.1 intake DTO 확장

`IngestPolicyRequest.rawData.enrichment` 에 `cleanedText` 필드 추가 (optional):

```json
{
  "rawData": {
    "enrichment": {
      "sourceUrl": "https://...",
      "fetchedAt": "...",
      "confidence": 0.82,
      "status": "OK",
      "sections": { /* 기존 9개 */ },
      "extraAttachments": [],
      "cleanedText": "외부 정책 안내 페이지 본문 (boilerplate 제거 후 8000자 cap)..."
    }
  }
}
```

### 6.2 IngestionService 매핑

`IngestionService.receivePolicy()` 에서 `rawData.enrichment.cleanedText` 를 그대로 `PolicyEnrichment.cleanedText` 에 매핑.

`PolicyEnrichment.isExposable()` 의 동작은 변경 없음 (cleanedText 와 무관).

---

## 7. rag 변경

### 7.1 `RagIndexingService` 청크 생성 흐름 확장

기존 흐름:
1. 정책 본문(body / supportTarget / supportContent 등)을 splitter 로 청크 분할 → source=BODY 청크 N개
2. 정책 첨부 각각의 추출 텍스트를 splitter 로 청크 분할 → source=ATTACHMENT 청크 M개
3. 각 청크 임베딩 → PolicyDocument 저장

신규 흐름:
4. **(NEW)** `policy.enrichment.cleanedText` 가 있으면 splitter 로 청크 분할 → source=ENRICHMENT_BODY 청크 K개
5. 임베딩 → PolicyDocument 저장 (`attachmentId = null`)

청크 분할 splitter: 기존 BODY 청크 splitter 와 동일 (chunk size / overlap). 별도 튜닝 없음.

### 7.2 재인덱싱 트리거

기존 `PolicyUpsertedEvent` 가 enrichment 변경 시에도 발행되므로 새 이벤트 불요. enrichment hash 변경(`PolicyEnrichment.fetchedAt` 갱신) 시 자연스럽게 재인덱싱 호출.

### 7.3 source hash

`PolicyDocument` 의 source_hash 또는 content_hash 계산식에 source 필드를 포함시켜 같은 텍스트라도 source 가 다르면 중복 청크로 인식하지 않도록 한다.

---

## 8. guide 변경

### 8.1 `combinedSourceText` 의 청크 라벨

기존:
```java
sb.append("[chunk-").append(i);
if (c.attachmentId() == null) {
    sb.append(" source=BODY]\n");
} else {
    sb.append(" source=ATTACHMENT attachment-id=").append(c.attachmentId());
    if (c.pageStart() != null) {
        sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
    }
    sb.append("]\n");
}
```

확장 후 `ChunkInput` 에 `source` 필드를 추가하고 라벨이 그 값을 사용:

```java
sb.append("[chunk-").append(i).append(" source=").append(c.source());
if (c.attachmentId() != null) {
    sb.append(" attachment-id=").append(c.attachmentId());
    if (c.pageStart() != null) {
        sb.append(" pages=").append(c.pageStart()).append('-').append(c.pageEnd());
    }
}
sb.append("]\n");
```

`ChunkInput` 정의에 **`String source`** 추가 (예: `"BODY"`, `"ATTACHMENT"`, `"ENRICHMENT_BODY"`).

**결합 회피 결정**: 의도적으로 `rag.domain.model.PolicyDocumentSource` enum 을 그대로 import 하지 않고 String 으로 매핑한다. 이유:
- `guide.application.dto.command` 가 다른 모듈의 domain enum 을 직접 참조하면 향후 rag 의 source 값이 추가/변경될 때 guide 의 ChunkInput 시그니처도 함께 바뀌어야 한다 — 모듈 간 결합도 상승.
- `GuideGenerationInput.of(Policy, List<PolicyDocument>...)` 가 이미 `rag.domain.model.PolicyDocument` entity 를 받는 표면이 있으니, 그 변환 단계에서 `String` 으로 한 번 풀어주면 ChunkInput 이하의 표면이 깨끗해진다.
- LLM 프롬프트의 라벨 문자열로 흘러갈 값이라 enum 타입성이 주는 이득(타입 검증)이 작다.

매핑 규약: `PolicyDocument.getSource() == null → "BODY"` (기존 row 호환). 그 외 enum 값은 `.name()` 결과를 그대로 사용.

### 8.2 LLM 프롬프트 영향

SYSTEM_PROMPT 의 첨부 trace 규칙 (`source=ATTACHMENT` 일 때 attachmentRef 정확성)은 그대로 유지. `source=ENRICHMENT_BODY` 청크는 attachmentRef 없이 인용되므로 sourceField 라벨은 `ENRICHMENT` 또는 `BODY` 중 선택 — **권장: `ENRICHMENT`** (외부 페이지 출처를 사용자에게 시각으로 분리).

이를 위해 SYSTEM_PROMPT 의 ENRICHMENT 라벨 규칙(2026-05-14 spec §7)에 한 줄 추가:

> "[chunk-N source=ENRICHMENT_BODY] 청크에서 가져온 정보는 sourceField=ENRICHMENT 로 라벨링한다."

`PROMPT_VERSION` v5 → v6 으로 증분.

### 8.3 hash 입력

`GuideGenerationService.computeHash` 의 chunks 직렬화에 source 가 포함되도록 `ChunkInput.content()` 이전에 source 도 hash 입력. 기존 코드는 `c.getContent()` 만 들어가는데 source 가 다른 같은 텍스트 청크 충돌 방지.

---

## 9. qna 변경

이번 사이클에서는 **변경 없음**. `RagSearchService` 가 source 라벨과 무관하게 거리 기반 top-k 검색하므로 ENRICHMENT_BODY 청크도 자연스럽게 검색됨.

향후 개선 후보 (이번 비범위):
- Q&A 답변에서 출처 표시를 source 별로 분리 ("정책 본문 / 외부 안내 페이지")
- ENRICHMENT_BODY 청크에 낮은 거리 가중치 부여 (신뢰도 분리)

---

## 10. 비용 / 운영 안전장치

### 10.1 임베딩 비용

- 정책당 추가 청크 평균 5~10개 (8000자 / 1500자 chunk 추정)
- 온통청년 enrichment 가 있는 정책 24건 기준 → 추가 임베딩 호출 약 120~240 회 (일회성)
- 정책별 enrichment 갱신 빈도가 낮으므로(`fetchedAt` 변경 시만 재인덱싱) 지속 비용 부담 작음
- 임베딩 모델: text-embedding-3-small (저비용)

### 10.2 PROMPT_VERSION v5 → v6

가이드 청크 라벨 문자열 변경 + 출처 라벨 규칙 추가로 인한 일회성 가이드 재생성. allowlist 정책에 한해서만 발생하므로 비용 통제됨.

### 10.3 CostGuard 영향

기존 메커니즘 그대로. 새로운 게이트 불요.

### 10.4 cleanedText 보존 정책

- enrichment 가 LOW_CONFIDENCE 등으로 `isExposable() == false` 인 정책의 cleanedText 도 DB 에 저장하되 청크화는 하지 않는다 — 이유: confidence 가 낮으면 외부 페이지 내용 자체의 신뢰도가 떨어질 가능성. 사용자 노출 위험 차단.
- 운영자가 confidence 임계값 튜닝 후 재인덱싱이 필요하면 admin API 로 트리거.

---

## 11. 점진 롤아웃

1. **1단계 — DB 마이그레이션**: `policy_document.source` 컬럼 추가, 기존 row 마이그레이션. RAG 검색은 source 무관하게 작동하므로 무중단.
2. **2단계 — backend 머지**: rag 모듈의 ENRICHMENT_BODY 청크 생성 활성화. n8n 이 아직 cleanedText 를 전달하지 않으므로 실제 청크 생성은 0건.
3. **3단계 — n8n 워크플로우 머지**: cleanedText 부착 활성화. 다음 04:00 스케줄 실행 시 enrichment 가 있는 정책부터 자동 청크 생성.
4. **4단계 — guide PROMPT_VERSION v6**: 가이드 ENRICHMENT_BODY 청크 인용 활성화. 머지 즉시 allowlist 정책 일회성 재생성.
5. **롤백 경로**:
   - n8n 단독 롤백: backend 는 cleanedText null 처리로 안전.
   - backend 단독 롤백: §5.3 의 `DROP DEFAULT` 는 마이그레이션 검증 후 운영자가 별도로 수행한다. 롤백 시점에 아직 DEFAULT 가 살아 있다면 NULL 위반 없음. 이미 DEFAULT 가 제거된 상태라면 롤백 직전에 `ALTER TABLE policy_document ALTER COLUMN source SET DEFAULT 'BODY'` 를 먼저 복구해서 구버전 backend 의 INSERT 가 NOT NULL 에 안 걸리도록 한다.
   - ENRICHMENT_BODY row 가 이미 있어도 RAG 검색 / GuideGenerationInput 조립에 영향 없음 (구 코드는 source 컬럼을 읽지 않음).

---

## 12. 테스트 전략

### 12.1 백엔드 (`spring-test` 컨벤션)

| 종류 | 대상 | 시나리오 |
|---|---|---|
| 단위 | `PolicyEnrichment` record | cleanedText nullable, 8000자 cap 검증 |
| 단위 | `PolicyDocument` record | source 필수 검증 |
| 단위 | `IngestionService.receivePolicy` | cleanedText 매핑, null 처리 |
| 단위 | `RagIndexingService.reindex` | enrichment.cleanedText 있을 때 ENRICHMENT_BODY 청크 생성, isExposable=false 면 미생성, 청크 분할 결과 일관성 |
| 단위 | `GuideGenerationInput.combinedSourceText` | ENRICHMENT_BODY 청크의 라벨이 `source=ENRICHMENT_BODY` 로 직렬화 |
| 단위 | `GuideGenerationService.computeHash` | 같은 텍스트 + 다른 source 의 청크가 다른 hash 를 생성 |
| 통합 | RAG search → Q&A 흐름 | ENRICHMENT_BODY 청크가 거리 기반 top-k 에 포함되는지 |
| 마이그레이션 | Flyway | source 컬럼 추가 + 기존 row 마이그레이션 |

### 12.2 n8n

기존 spec(2026-05-12) 의 수동 검증 패턴 그대로. 워크플로우 마지막 단계에서 cleanedText 가 payload 에 들어가는지 시각 확인 1건.

---

## 13. 관찰 가능성

- `RagIndexingService.reindex` 완료 시 로그에 추가:
  - `body_chunks_count`
  - `attachment_chunks_count`
  - `enrichment_body_chunks_count` (NEW)
- `IngestionRunLog` 에 `enrichment_body_chunks_total` 통계 컬럼 추가 (선택, v1).

---

## 14. 미래 확장 (다음 사이클 후보)

- **E 사이클**: `enrichment.extraAttachments` PDF/HWP 자동 다운로드 → 첨부 청킹 파이프라인 연결.
- Q&A 출처 표시 분리: 본문 / 외부 페이지 / 첨부 시각 구분.
- ENRICHMENT_BODY 청크의 신뢰도 가중치 (cosine distance 보정).
- 외부 페이지 단독 변경 감지 (별도 contentHash 보관, 일정 주기로 재크롤).
- cleanedText 도 함께 변경 시점 추적 (cleanedText hash) — fetchedAt 외 추가 식별자로 hash 입력 강화.

---

## 15. 의존 관계 정리

- `ingestion → policy`: cleanedText 매핑
- `policy → rag`: enrichment.cleanedText 가 rag 청크 입력
- `rag → guide`: source 라벨이 GuideGenerationInput 의 chunk 라벨로 전달
- `rag → qna`: 검색 결과에 source 라벨 그대로 노출
- n8n → ingestion: intake DTO 의 cleanedText 필드

---

## 16. 검토 포인트 — 합의 완료 (2026-05-15)

spec 초안에서 머지 전 합의가 필요했던 5개 항목 모두 spec 본문 권장안으로 확정.

| # | 항목 | 결정 | 근거 |
|---|---|---|---|
| 16-1 | `PolicyDocument.source` 컬럼 위치 | **별도 `source VARCHAR(32) NOT NULL` 컬럼** | 현재 PolicyDocument 의 다른 필드 8개 전부 별도 컬럼 — 패턴 일관성. 인덱스 가능. NOT NULL 강제. §5.3 마이그레이션 그대로 적용 |
| 16-2 | `isExposable() == false` 인 cleanedText 처리 | **저장 O + 청크화 X** | 청크 미생성 → RAG·가이드 LLM 입력 자체에 미도달 (노출 위험 0). 저장 보존 → 임계값 튜닝 시 재크롤 회피. §10.4 와 일관 |
| 16-3 | `GuideSourceField` 확장 여부 | **6개 그대로 유지** | ENRICHMENT_BODY 청크 → guide 출력의 `sourceField=ENRICHMENT` 매핑. 사용자가 "추출 섹션 vs 본문 발췌"를 구분해서 얻는 가치 불명확. BE+FE 변경 0. §1.2 비목표·§8.2 권장과 일관 |
| 16-4 | `PROMPT_VERSION` v5 → v6 증분 시점 | **backend 머지 시 즉시 v6** | SYSTEM_PROMPT 에 "ENRICHMENT_BODY 청크 → sourceField=ENRICHMENT 매핑" 규칙 명시 필요. 미명시 시 LLM 이 `sourceField=ENRICHMENT_BODY` 출력하면 enum 파싱 실패 위험. 비용은 allowlist 정책에만 영향(통제 가능). §8.2·§10.2 와 일관 |
| 16-5 | 청크 splitter 튜닝 | **BODY 와 동일 splitter** | 운영 데이터 없이 별도 튜닝은 premature optimization. PR #91 의 table-aware + 줄 보존 + overlap 이 외부 페이지에도 유용. 추후 검색 품질 데이터 누적 후 별도 사이클로 튜닝 가능 (§14 미래 확장) |
