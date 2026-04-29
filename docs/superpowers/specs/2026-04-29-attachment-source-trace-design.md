# 첨부 단위 출처 trace (sourceField=ATTACHMENT 도입) — Design Spec

> **상태**: design (brainstorming 완료, plan 대기)
> **작성일**: 2026-04-29
> **출처 backlog**: `docs/superpowers/specs/next/TODO_2026-04-28-attachment-source-trace-backlog.md` (본 spec 으로 승격됨, 삭제됨 — git history 참조)
> **선행 사이클**: 가이드 정확도 강화 (PR #48), 첨부 추출 파이프라인 (PR #46), 정책 상세 쉬운 해석 (PR #44)
> **연관 PR (예정)**: TBD

---

## 1. 목적

가이드 highlights·pitfalls 항목의 출처를 **첨부 PDF 의 페이지 단위까지** 추적해 사용자에게 노출한다. 클릭하면 새 탭에서 해당 PDF 의 정확한 페이지로 점프한다.

현재 가이드 항목의 `sourceField` 는 정책 API 의 4종 구조화 필드 (`SUPPORT_TARGET` / `SELECTION_CRITERIA` / `SUPPORT_CONTENT` / `BODY`) 만 가리킨다. 첨부 PDF 텍스트는 PR #46 으로 RAG 청크에 합쳐져 LLM 입력에는 들어가지만, 항목별로 "이 정보는 어느 첨부의 어느 페이지에서 왔다" 를 trace 할 수 없다.

본 사이클은 (a) 청크 메타에 첨부/페이지 정보를 박고, (b) LLM 이 응답에 그 메타를 인용하도록 강제하고, (c) UI 에서 첨부 PDF 새 탭 + 페이지 deep link 로 검증 동선을 제공한다.

## 2. 사용자 시나리오

페르소나 C (박서연, 23세) 가 정책 7번 (청년 임대료 지원) 상세 페이지의 가이드를 읽다가 다음 항목을 본다:

> ⚠️ **놓치기 쉬운 점**
> · 배우자 명의 자가 주택이 있어도 신청 제외   `[첨부: 시행규칙.pdf · 35페이지 ↗]`

라벨 클릭 → 새 탭이 열리고 시행규칙.pdf 의 35페이지가 자동으로 표시됨. 사용자는 원문을 1초 만에 검증하고, 정책 상세 탭으로 돌아온다.

HWP 첨부의 경우 페이지 deep link 가 표준에 없으므로 라벨이 `[첨부: 안내문.hwp ↗]` 로 표시되고, 클릭 시 첨부의 처음부터 열린다.

## 3. 영향 범위 / 모듈

| 모듈 | 변경 유형 | 핵심 |
|---|---|---|
| `ingestion` (TikaAttachmentExtractor) | 변경 | PDF 페이지별 SAX 이벤트로 텍스트+페이지 메타 추출 |
| `ingestion` (AttachmentReindexService) | 변경 | mergedContent 마커 포맷 강화 (`=== 첨부 attachment-id=N ===`, `--- page=M ---`) |
| `rag` (DocumentChunker) | 변경 | 본문/첨부 boundary 강제 분할 + 페이지 메타 추적 |
| `rag` (PolicyDocument 엔티티) | 변경 | `attachment_id`, `page_start`, `page_end` 컬럼 (NULLable). 본 프로젝트는 Flyway 미사용 — local 은 `ddl-auto=update` 가 자동 ALTER, prod 는 배포 전 SQL 수동 실행 또는 일시 `update` 전환 후 `validate` 복귀 |
| `guide` (GuideSourceField, AttachmentRef) | 변경/신규 | enum `ATTACHMENT` 추가, `AttachmentRef` record 신규 |
| `guide` (GuideHighlight, GuidePitfall) | 변경 | `attachmentRef` 옵셔널 필드 추가 |
| `guide` (GuideGenerationInput.combinedSourceText) | 변경 | 청크 라벨에 attachment/pages 메타 박기 |
| `guide` (OpenAiChatClient) | 변경 | structured output schema 갱신 + 시스템 프롬프트 규칙 추가 + few-shot 1개 |
| `guide` (GuideValidator) | 변경 | 검증 5 (sourceField=ATTACHMENT 유효성) 추가 |
| `policy` (PolicyAttachmentController, 신규) | 신규 | 첨부 redirect endpoint |
| `frontend` (AttachmentSourceLink, 신규) | 신규 | ATTACHMENT 항목 라벨 + 새 탭 링크 |
| `frontend` (SourceLinkedListCard) | 변경 | sourceField 분기 |
| `frontend` (Guide / PolicyAttachment 타입) | 변경 | `AttachmentRef` 타입 추가, enum `ATTACHMENT` 추가, `PolicyAttachment` 에 `id: number` 필드 추가, 기존 `SourceLinkedListCard` 내부의 `AttachmentRef` 는 의미 충돌 회피 위해 `AttachmentSummary` 로 rename |

## 4. 도메인 모델

### 4.1 `guide` 도메인

```java
public record AttachmentRef(
    Long attachmentId,           // not null
    Integer pageStart,           // nullable — HWP fallback 시 null
    Integer pageEnd              // nullable — HWP fallback 시 null
) {
    public AttachmentRef {
        Objects.requireNonNull(attachmentId);
        if ((pageStart == null) != (pageEnd == null))
            throw new IllegalArgumentException("pageStart 와 pageEnd 는 함께 존재해야 함");
        if (pageStart != null && pageStart > pageEnd)
            throw new IllegalArgumentException("pageStart > pageEnd");
    }
}

public enum GuideSourceField {
    SUPPORT_TARGET,
    SELECTION_CRITERIA,
    SUPPORT_CONTENT,
    BODY,
    ATTACHMENT          // ← NEW
}

public record GuideHighlight(
    String text,
    GuideSourceField sourceField,
    AttachmentRef attachmentRef     // ← NEW; sourceField=ATTACHMENT 일 때만 not null
) {}

public record GuidePitfall(
    String text,
    GuideSourceField sourceField,
    AttachmentRef attachmentRef     // ← NEW
) {}
```

`GuidePairedSection`, `GuideContent.oneLineSummary` 는 변경 없음 (paired 섹션은 정책 API 구조화 필드 단위라 첨부 trace 무관).

### 4.2 `rag` 도메인

```java
@Entity
public class PolicyDocument {
    // 기존: id, policyId, chunkIndex, content, sourceHash, embedding
    @Column(name = "attachment_id")
    private Long attachmentId;        // null = 정책 본문 청크
    @Column(name = "page_start")
    private Integer pageStart;        // null = HWP / 본문 청크
    @Column(name = "page_end")
    private Integer pageEnd;
}
```

### 4.3 도메인 불변식

1. `sourceField == ATTACHMENT` ↔ `attachmentRef != null`
2. `attachmentRef.attachmentId` 는 해당 정책의 `policy_attachment.id` 에 존재해야 함
3. `pageStart` 가 있으면 `pageEnd` 도 있고 `pageStart ≤ pageEnd`
4. 페이지 정보가 없으면 (HWP/스캔 PDF) `attachmentId` 만 박힘 → UI 라벨 `[첨부: {name}]`

### 4.4 프론트엔드 타입

```typescript
// frontend/src/types/policy.ts
export type GuideSourceField =
  | 'SUPPORT_TARGET' | 'SELECTION_CRITERIA' | 'SUPPORT_CONTENT' | 'BODY'
  | 'ATTACHMENT';

export interface AttachmentRef {
  attachmentId: number;
  pageStart: number | null;
  pageEnd: number | null;
}

export interface GuideHighlight {
  text: string;
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}
// GuidePitfall 동일 구조

// PolicyAttachment 에 id 필드 추가 (현재는 name/url/mediaType 만 있음)
export interface PolicyAttachment {
  id: number;        // ← NEW
  name: string;
  url: string;
  mediaType: string | null;
}
```

> **이름 충돌 회피**: 기존 `frontend/src/components/policy/SourceLinkedListCard.tsx` 에 컴포넌트 내부 타입 `AttachmentRef { id?, name, url }` 가 있음 (PR #48 의 첨부 바로가기 라벨용). 의미가 다르므로 본 사이클에서 그것을 `AttachmentSummary` 로 rename 하고, 새 전역 `AttachmentRef`(가이드 출처 trace) 를 `types/policy.ts` 에 추가한다.

## 5. 데이터 흐름

```
[1] Tika 커스텀 SAX handler (TikaAttachmentExtractor 변경)
    ↓ PDFParser 의 페이지 마커 이벤트 인지 → List<PageText>(page=N, text=...)
    
[2] PolicyAttachment.extractedText (페이지 sentinel 포함 평문)
    \f<page=1>...\f<page=2>...
    
[3] AttachmentReindexService.mergedContent — 마커 강화
    === 정책 본문 ===
    {body}
    
    === 첨부 attachment-id=12 name="시행규칙.pdf" ===
    --- page=1 --- {page 1 text}
    --- page=2 --- {page 2 text}
    
    === 첨부 attachment-id=13 name="안내문.hwp" ===
    --- page=null --- {hwp text}
    
[4] DocumentChunker — boundary 인지
    · 본문/첨부 boundary (=== 첨부 ===) → 강제 분할 (한 청크 = 단일 출처 보장)
    · 페이지 boundary (--- page=N ---) → 추적만, 강제 분할 X (청크 안에 N~M 걸치면 pageStart=N, pageEnd=M)
    ↓ List<Chunk(text, attachmentId?, pageStart?, pageEnd?)>
    
[5] PolicyDocument 인덱싱 (RagIndexingService)
    메타 그대로 컬럼에 박음. 임베딩은 text 만.
    
[6] GuideGenerationInput.combinedSourceText() — 청크 라벨에 메타
    [chunk-0 source=BODY]
    {본문 청크}
    
    [chunk-1 source=ATTACHMENT attachment-id=12 pages=1-3]
    {첨부 청크}
    
    [chunk-2 source=ATTACHMENT attachment-id=13]
    {HWP 청크 — pages 없음}
    
[7] OpenAiChatClient — structured output + 시스템 프롬프트 규칙 + few-shot 1개
    ↓ JSON schema 에 attachmentRef 추가
    ↓ 프롬프트 규칙: "청크 라벨의 attachment-id/pages 그대로 인용. 추측 금지."
    
[8] GuideContent 응답 → GuideValidator 검증 5
    invalid 항목 → 1회 LLM 재시도 → 그래도 invalid 면 항목 폐기
    
[9] 프론트 — SourceLinkedListCard 렌더
    ATTACHMENT 항목 라벨: [첨부: 시행규칙.pdf · 35-37페이지 ↗]
    클릭: window.open('/api/policies/attachments/12/file#page=35', '_blank')
```

### 5.1 핵심 의사결정

**페이지 sentinel 포맷**: `extractedText` 컬럼 안에 form-feed + page tag (`\f<page=N>`) 박기. 별도 컬럼/테이블 안 만듦. AttachmentReindexService 가 sentinel 인지하고 mergedContent 마커로 변환.

**청크 boundary 정책**:
- 본문 ↔ 첨부 / 첨부 ↔ 첨부 boundary: **강제 분할** (한 청크 = 단일 출처 보장)
- 페이지 boundary: **추적만**, 강제 분할 X (옵션 D 페이지 단위 chunker 는 v0.x+ 검토)

**청크 라벨 형식 (LLM 인용용)**: `[chunk-N source=... attachment-id=... pages=N-M]`. 헤더 마커 (`=== 첨부 ===` `--- page=N ---`) 는 chunker 가 boundary 인지에만 쓰고, LLM 입력에는 청크 라벨이 핵심.

**fileHash 처리**: `extractedText` 의 sentinel 포맷이 바뀌어도 `fileHash` 는 원본 파일 hash 라 유지. AttachmentReindexService 의 청크 sourceHash 가 mergedContent 변경을 자동 감지 → 자동 재인덱싱 (CostGuard allowlist 정책만).

## 6. LLM 입력 / 응답 schema + 검증 5

### 6.1 시스템 프롬프트 규칙 추가 (기존 원칙 뒤에 붙임)

```
원칙 N. 출처 라벨 정확성
- highlights/pitfalls 의 sourceField 는 정보가 발견된 청크 라벨의 source 값을 그대로 쓴다.
- source=ATTACHMENT 인 청크에서 가져온 정보:
  · attachmentRef.attachmentId = 청크 라벨의 attachment-id 그대로
  · attachmentRef.pageStart/pageEnd = 청크 라벨의 pages= 범위 그대로
  · 청크 라벨에 pages 가 없으면 pageStart/pageEnd = null
  · 라벨에 없는 페이지를 추측해서 박지 말 것
- sourceField != ATTACHMENT 일 때 attachmentRef = null
- 여러 청크에 걸친 정보면 가장 핵심 정보가 있는 청크 1개를 선택해 그 라벨 메타를 박는다
```

### 6.2 OpenAI structured output schema 갱신

`GuideHighlight` / `GuidePitfall` items schema (strict mode 호환):

```json
{
  "type": "object",
  "properties": {
    "text": { "type": "string" },
    "sourceField": {
      "enum": ["SUPPORT_TARGET","SELECTION_CRITERIA","SUPPORT_CONTENT","BODY","ATTACHMENT"]
    },
    "attachmentRef": {
      "type": ["object","null"],
      "properties": {
        "attachmentId": { "type": "integer" },
        "pageStart":    { "type": ["integer","null"] },
        "pageEnd":      { "type": ["integer","null"] }
      },
      "required": ["attachmentId","pageStart","pageEnd"],
      "additionalProperties": false
    }
  },
  "required": ["text","sourceField","attachmentRef"],
  "additionalProperties": false
}
```

### 6.3 few-shot 추가 (1개)

```
입력 청크:
  [chunk-1 source=ATTACHMENT attachment-id=12 pages=35-35]
  배우자 명의 자가 주택이 있는 경우도 본 사업의 중복 수혜 제한 대상에 포함된다.

출력 일부:
  {
    "pitfalls": [{
      "text": "배우자 명의 자가 주택이 있어도 신청 제외",
      "sourceField": "ATTACHMENT",
      "attachmentRef": { "attachmentId": 12, "pageStart": 35, "pageEnd": 35 }
    }]
  }
```

### 6.4 prompt.version 증분

`GuideGenerationService.java:40` 의 `static final String PROMPT_VERSION = "v3"` 상수를 `"v4"` 로 증분 (application.yml 아니라 코드 인라인). `computeHash()` 가 `|prompt:v4` 를 sha256 입력에 포함하므로 sourceHash 자동 무효화 → CostGuard allowlist 정책 (7·30) 만 다음 호출 시 자동 재생성.

### 6.5 GuideValidator 검증 5 — sourceField=ATTACHMENT 유효성

기존 검증 1~4 (group mix, highlights 부족, 수치 토큰, 친근 어조) + 신규 검증 5:

```java
Set<Long> validAttachmentIds = policy.getAttachments().stream()
    .map(PolicyAttachment::getId).collect(toSet());

for (item in highlights ∪ pitfalls) {
    case 1: sourceField == ATTACHMENT && attachmentRef == null            → INVALID
    case 2: sourceField == ATTACHMENT
            && !validAttachmentIds.contains(attachmentRef.attachmentId)   → INVALID
    case 3: sourceField != ATTACHMENT && attachmentRef != null            → INVALID
    case 4: pageStart != null XOR pageEnd != null                         → INVALID
    case 5: pageStart != null && pageStart > pageEnd                      → INVALID
    case 6: 정책에 첨부 0개인데 ATTACHMENT 등장                           → INVALID
}
```

처리:
- INVALID 발견 시 → **1회 LLM 재시도** (기존 검증 4 와 동일 패턴, retry 카운터 공유)
- 재시도 후에도 INVALID 면 → **항목 폐기** (가이드 자체는 저장)
- 재시도 / 폐기 발생 시 → **WARN 로그 + 메트릭** (기존 검증과 동일 채널)

## 7. 백엔드 API — 첨부 redirect endpoint

```
GET /api/policies/attachments/{attachmentId}/file
```

| 항목 | 동작 |
|---|---|
| 권한 | 공개 (정책 본문/메타와 동일 민감도). 추후 abuse 발견 시 인증 추가 검토 |
| Storage 추상화 | `AttachmentStorage` 인터페이스에 `Optional<String> presign(String key, Duration ttl)` default method 추가. `LocalAttachmentStorage` 는 default (Optional.empty), `S3AttachmentStorage` 만 override |
| presign 가능 (S3) | `Optional` 채워짐 → presigned URL 로 302 redirect |
| presign 불가 (Local 또는 S3 presign 실패) | `AttachmentStorage.get(key)` 으로 stream → `ResponseEntity<InputStreamResource>` 직접 응답 (Content-Type, Content-Disposition 부착) |
| storageKey 없음 (캐시 안 됨) | `attachment.url` (외부 원본) 으로 302 fallback |
| 셋 다 없음 | 404 |
| Fragment (`#page=N`) | HTTP 302 의 Location 에 안 박음. 브라우저가 요청 URL 의 fragment 를 final URL 에 자동 보존 (RFC 7231 표준). Stream 응답 시에도 브라우저 PDF viewer 가 fragment 따라 페이지 점프 |
| 로깅 | attachmentId, 사용한 응답 종류 (presign / stream / external), 요청 IP, timestamp |
| 컨트롤러 | 신규 `PolicyAttachmentController` (`policy` 모듈 presentation/controller) — 기존 `PolicyController` 와 분리 (SRP). `PolicyAttachmentApi` 인터페이스에 Swagger 어노테이션 |

## 8. 프론트엔드 — AttachmentSourceLink 컴포넌트

### 8.1 신규: `frontend/src/components/policy/AttachmentSourceLink.tsx`

```tsx
// props: { attachmentRef: AttachmentRef, attachments: PolicyAttachment[] }

const target = attachments.find(a => a.id === attachmentRef.attachmentId);
if (!target) return null;   // 검증 5 가 차단해서 발생 가능성 낮음

const pageLabel =
  attachmentRef.pageStart == null              ? '' :
  attachmentRef.pageStart === attachmentRef.pageEnd ? ` · ${attachmentRef.pageStart}페이지`
                                                    : ` · ${attachmentRef.pageStart}-${attachmentRef.pageEnd}페이지`;

const href =
  `/api/policies/attachments/${attachmentRef.attachmentId}/file` +
  (attachmentRef.pageStart != null ? `#page=${attachmentRef.pageStart}` : '');

// 렌더: [첨부: {target.name}{pageLabel} ↗]
// onClick: window.open(href, '_blank', 'noopener,noreferrer')
```

### 8.2 변경: `SourceLinkedListCard.tsx`

각 항목 (GuideHighlight / GuidePitfall) 렌더에서 분기:
- `sourceField === 'ATTACHMENT'` → `<AttachmentSourceLink />`
- 그 외 → 기존 `scrollAndHighlight` 라벨 (변경 없음)

`SOURCE_LABELS` / `SCROLL_TARGETS` 매핑은 4종 enum 그대로 유지 (ATTACHMENT 는 별도 컴포넌트 처리).

### 8.3 데이터 의존성

`useGuide` 응답 또는 `policy` 응답에 `attachments: PolicyAttachment[]` 가 포함되어야 함 — 현재 `PolicyDetail` 에 이미 있음 (PR #45 출처 뱃지 작업 기준). prop drilling 또는 query cache 공유로 `SourceLinkedListCard` 까지 전달.

## 9. 마이그레이션

본 프로젝트는 **Flyway 미사용**, JPA `ddl-auto` 로 스키마 관리:
- local profile: `ddl-auto: update` → 엔티티 변경 시 자동 ALTER TABLE
- prod profile: `ddl-auto: validate` → 스키마 변경 거부

| 단계 | 작업 |
|---|---|
| 스키마 변경 (local) | `PolicyDocument` 엔티티에 `attachment_id`/`page_start`/`page_end` 필드 + `@Column` 추가 → `ddl-auto=update` 가 자동 ALTER TABLE. 인덱스는 `@Table(indexes = @Index(name = "idx_policy_document_attachment", columnList = "attachment_id"))` |
| 스키마 변경 (prod) | 배포 전 SQL 수동 실행 또는 일시 `update` 전환 후 `validate` 복귀. 운영 절차는 attachment-extraction-pipeline runbook 의 백필 절차와 동일 |
| prompt.version | `GuideGenerationService.java:40` 의 `PROMPT_VERSION = "v3"` → `"v4"` |
| AttachmentReindexService | mergedContent 포맷 변경 → 첨부 sourceHash 자동 무효화 → CostGuard allowlist 정책 (7·30) 의 첨부 청크 자동 재인덱싱 |
| GuideGenerationService | 다음 호출 시 정책 7·30 가이드 자동 재생성 |
| 기존 청크 row | `attachment_id`/`page_start`/`page_end` = NULL 유지. 재인덱싱 시 채움. |
| 기존 가이드 row | JSON 역직렬화 시 `attachmentRef` 가 `null` 로 채워짐 (Jackson default). enum `ATTACHMENT` 추가도 안전. |
| 운영 작업 | **없음** (기존 자동 재생성 경로 활용). CostGuard 해제 시점에 다른 정책이 처음 가이드 생성되며 자연스럽게 새 schema 사용. |

## 10. 비범위

- **HWP 페이지 trace** — HWP 는 페이지 deep link 표준 없음. 첨부 단위 라벨 (`[첨부: 안내문.hwp]`) 만.
- **OCR / 스캔 PDF** — 어차피 텍스트 추출 자체가 SKIPPED (PR #46 도입). trace 대상 안 됨.
- **항목별 텍스트 highlight** — PDF viewer 안에서 정확한 문장 강조 (v0.x+).
- **모달 PDF viewer 임베드** — 새 탭 채택. 사용자가 정말 원하면 v0.x 에 react-pdf 로 도입 가능 (외부 PDF 의 X-Frame-Options / CORS 처리 추가).
- **페이지 단위 chunker** — 옵션 D (한 청크 = 한 페이지) 는 retrieval 품질 영향과 재임베딩 비용 부담으로 보류. v0.x+ 에 Q&A retrieval 품질 측정 후 검토.
- **사용자 lazy 트리거 / 가이드 일괄 backfill** — CostGuard 활성과 별개 이슈. 본 사이클에서 변경 없음.
- **첨부 redirect endpoint 의 인증 / rate limit** — 초기 공개. abuse 발견 시 v0.x 에 추가.

## 11. 위험 / 모니터링

| 위험 | 완화 |
|---|---|
| **Tika PDFParser 가 모든 PDF 에 페이지 SAX 이벤트 발생?** | Phase 0 에서 sample PDF 3종 (텍스트형/표/이미지섞임) 으로 검증. 페이지 이벤트 없으면 단일 청크 fallback (`pageStart=null`) |
| gpt-4o-mini 가 strict mode + nullable 객체 안정 처리? | 검증 5 + 1회 retry 패턴 (기존). retry 후에도 invalid 면 항목 폐기, 가이드 자체는 저장 |
| 청크 boundary 강제 분할 → 청크 수 증가 → 임베딩 비용 ↑ | 청크당 token 수 모니터링. CostGuard 활성 중 단기 영향 없음. 해제 시점에 비용 측정 |
| iOS Safari PDF viewer `#page=N` 지원? | iOS Safari 는 PDF 를 OS 앱으로 fallback. `#page=` 처리는 OS 앱 의존. fallback 으로 첫 페이지 열림 (UX 손해 < 깨짐). 모니터링만 |
| redirect endpoint S3 비용 / 트래픽 abuse | access 로그 기반 일일 호출량 모니터링. abuse 시 인증 추가 |

### 11.1 모니터링 메트릭

- **청크 메타 채움 비율**: `policy_document.attachment_id IS NOT NULL` row 비율 (정책별 첨부 청크의 100% 가 채워져야 정상)
- **검증 5 위반율 / retry 비율**: LLM 재시도 트리거 카운트 (기존 검증 메트릭과 같은 채널)
- **redirect endpoint**: 호출 수 / 404 비율 / S3 fallback vs 외부 fallback 비율

## 12. 권장 PR 분할

`writing-plans` 단계에서 정밀화. 일단 권장 4분할:

- **PR 1**: Tika 페이지 추출 + `extractedText` sentinel + Phase 0 sample 검증 (`ingestion`)
- **PR 2**: AttachmentReindexService mergedContent 마커 + DocumentChunker boundary 분할 + PolicyDocument Flyway 마이그레이션 + RagIndexingService 청크 메타 인덱싱 (`ingestion` + `rag`)
- **PR 3**: GuideSourceField/AttachmentRef + 시스템 프롬프트 + few-shot + structured output schema + GuideValidator 검증 5 + `prompt.version` 증분 (`guide`)
- **PR 4**: PolicyAttachmentController redirect endpoint + 프론트 AttachmentSourceLink 컴포넌트 + 타입 (`policy` + `frontend`)

각 PR 은 직전까지 머지된 상태에서도 **사용자에게 깨짐이 보이지 않는** 단위로 끊는다 — PR 3 머지까지는 사용자 노출 변화 없음, PR 4 에서 처음 ATTACHMENT 라벨이 등장.

## 13. 결정 로그

| # | 결정 | 대안 | 채택 이유 |
|---|---|---|---|
| 1 | UX: 새 탭 PDF + `#page=N` deep link | 모달 PDF viewer (react-pdf), iframe 임베드 | 구현 비용 0, 모바일 OS 앱 fallback, 외부 PDF X-Frame-Options 회피, 검증 흐름과 맞음 |
| 2 | Trace 정밀도: 페이지 단위 (PDF) + 첨부 단위 fallback (HWP/스캔) | 첨부 단위만, 청크 단위, 문장 단위 | 사용자 의도(검증)에 가장 맞음, Tika SAX 페이지 이벤트로 구현 가능 |
| 3 | 데이터 구조: enum 확장 + `attachmentRef` 옵셔널 record | sealed/discriminated union, 단일 record + nullable | LLM (gpt-4o-mini) 따라가기 쉬움, 기존 가이드 호환, 프론트 enum 매핑 그대로 |
| 4 | 페이지 정밀도: range (`pageStart`/`pageEnd`) | 단일 정수 `page` | 청크가 토큰 길이로 잘려 페이지 N~M 걸칠 수 있음. LLM 부담 ↓ (라벨 그대로 인용), 정보 손실 ↓ |
| 5 | 첨부 URL 노출: 백엔드 redirect endpoint + S3 우선 / 외부 fallback | JSON URL 엔드포인트, 외부 URL 직접 | URL 안정성, S3 캐시 활용, access 로깅, 프론트 단순 `<a href>` |
| 6 | 마이그레이션: prompt.version 증분 + sourceHash 자동 무효화 | CostGuard 일시 해제 + 전체 backfill | 정책 7·30 외 가이드 없어 backfill 의미 없음, 기존 패턴 활용, 운영 작업 0 |

## 14. 미결 / 후속

- **gpt-4o-mini 의 nullable 객체 처리 안정성** — Phase 0 에서 sample 3개 정책 (7번 대상 우선) 으로 manual smoke. 위반율 높으면 schema 단순화 (예: `attachmentRef` 안의 nullable 필드 제거하고 PageRange 별도 객체화) 검토.
- **Tika PDFParser 페이지 마커 이벤트 일관성** — Phase 0 에서 sample PDF 검증. 일관성 깨지면 PDFBox 직접 호출로 우회 검토.
- **redirect endpoint 의 fragment 보존 (iOS Safari)** — 운영 후 모니터링. 깨지면 fragment 를 query string 으로 전환 (`?page=N` 해석 후 PDF.js 임베드) 검토.
- **페이지 단위 chunker (옵션 D)** — Q&A retrieval 품질 측정 후 v0.x+ 검토.
- **abuse 시 redirect endpoint 인증** — access 로그 모니터링 후 v0.x.
