# 온통청년 정책 데이터 풍부화 (n8n LLM 크롤링) — 설계

> **작성일**: 2026-05-12
> **대상 모듈**: `ingestion`, `policy`, frontend `features/policy`, n8n `youth-center-seoul.json`
> **우선순위**: P1
> **선결 의존**: 기존 온통청년 수집 워크플로우 (`youth-center-seoul.json`), 첨부 추출 파이프라인 (`AttachmentExtractionScheduler`)

---

## 배경

온통청년 API (`getPlcy`) 응답은 코드 풀이 + 짧은 본문 정도로 정보가 빈약하다. 응답에 포함된 `aplyUrlAddr` / `refUrlAddr1` / `refUrlAddr2` 링크가 가리키는 정책 안내 페이지에는 본문·첨부파일 등 풍부한 정보가 있으나, 사이트별 DOM 구조가 제각각이라 사이트별 크롤러 작성은 유지보수 부담이 크다.

복지로 중앙부처 응답은 이미 본문이 풍부해서 풍부화가 불필요하다. 따라서 풍부화 대상은 **온통청년 서울 스코프 (서울특별시 11000 + 자치구 25개) 정책에 한정**한다.

## 목적

- 사용자가 보는 본문, RAG Q&A 컨텍스트, 적합도 판정 입력을 한 번에 풍부화하는 통합 파이프라인을 구축한다.
- 사이트별 어댑터 없이 범용 추출 + LLM 보완 방식으로 커버리지를 확보한다.
- 자동 추출 결과는 별도 섹션·라벨로 노출해 원본과 신뢰 분리를 명확히 한다.
- 풍부화 실패가 정책 등록 자체를 막지 않는다.

## 비범위

- 복지로 중앙부처 워크플로우 변경
- 온통청년 전국 확대 (현재 워크플로우는 서울 스코프 전용)
- 외부 페이지가 단독으로 변경되었을 때의 재풍부화 (API 응답 변동 기준만)
- 사이트별 어댑터 작성
- 첨부파일 본문 텍스트의 LLM 재요약 (기존 Tika/Hwp 추출 파이프라인 그대로 사용)
- 실시간(요청 시) 풍부화 — 일 1회 배치만

---

## 핵심 결정

| 항목 | 결정 |
|------|------|
| 풍부화 위치 | **n8n 워크플로우 안**. 백엔드는 가공된 결과 수신·저장만 |
| 추출 전략 | 하이브리드: HTTP fetch → cheerio boilerplate 제거 → OpenAI gpt-4o-mini JSON 모드 |
| LLM 모델 | OpenAI `gpt-4o-mini` (백엔드 기존 사용처와 통일) |
| 변동 식별 | n8n 이 백엔드에 외부 hash 맵을 먼저 조회하여 신규/변동분만 풍부화 |
| 적용 범위 | 온통청년 서울 스코프 + 외부 링크 존재 + 신규/변동분 |
| 신뢰 노출 | 원본과 분리된 별도 섹션 + 출처 라벨 + 원문 링크 |
| 실패 처리 | enrichment 단계 실패는 정책 등록을 막지 않음. status 필드로 단계별 추적 |
| 갱신 주기 | 일 1회 (기존 04:00 스케줄) |

---

## 아키텍처 개요

```
[n8n: youth-center-seoul 워크플로우]
  (1) Schedule 04:00
  (2) GET 온통청년 getPlcy (전 페이지 순회)
  (3) GET /api/internal/ingestion/policies/external-hashes?source=YOUTH_CENTER   ← NEW
  (4) Code 노드: plcyNo별 contentHash 계산 → 신규/변동/미변동 분류                ← NEW
  (5) Enrichment 브랜치 (변동분 한정)                                          ← NEW
       a. 링크 선택 (aplyUrlAddr → refUrlAddr1 → refUrlAddr2)
       b. HTTP fetch (timeout 10s, max 3MB, Continue on Fail)
       c. Code 노드: cheerio boilerplate 제거 + 첨부 후보 수집
       d. OpenAI Chat (gpt-4o-mini, JSON schema)
       e. enrichment 객체 조립 (status 부착)
  (6) 기존 변환 노드 (코드사전 풀이) → IngestPolicyRequest 구성
  (7) POST /api/internal/ingestion/policies

[백엔드]
  - 신규 API: GET external-hashes (read-only)
  - intake 확장: rawData.enrichment / rawData.contentHash 수신
  - DB: policy.enrichment jsonb 컬럼 추가
  - PolicyDetailResponse: 신뢰 임계값 통과 시에만 enrichment 노출

[프론트엔드]
  - 정책 상세 페이지에 PolicyEnrichmentSection 추가
  - 출처 라벨 + 원문 링크 + 수집 일시 + extraAttachments
```

---

## n8n 워크플로우 상세

기존 노드는 유지하고 변동분 식별 + Enrichment 브랜치를 삽입한다.

### 변동 식별 단계

```
[JSON 파싱 + 서울 필터] (기존)
  ↓
[외부 hash 맵 조회]
   HTTP Request:
     GET {{ $env.BACKEND_URL }}/api/internal/ingestion/policies/external-hashes
         ?source=YOUTH_CENTER
     Headers: X-Internal-Api-Key
   응답: { "PLY00012345": "9a2c...", ... }
  ↓
[변동 판정 Code 노드]
   for each policy:
     normalized = {
       plcyNm, plcyExplnCn, aplyYmd, sprvsnInstCdNm, operInstCdNm,
       aplyUrlAddr, refUrlAddr1, refUrlAddr2, zipCd,
       mrgSttsCd, jobCd, schoolCd, plcyMajorCd, sbizCd, plcyPvsnMthdCd, bizPrdSeCd,
       sprtTrgtMinAge, sprtTrgtMaxAge, earnMinAmt, earnMaxAmt,
       sbmsnDcmntCn, etcMttrCn
     }
     contentHash = sha256(JSON.stringify(정렬된 키 순서))
     existing = hashMap[plcyNo]
     enrich   = (existing == null) || (existing !== contentHash)
     status   = existing == null ? "NEW" : (enrich ? "CHANGED" : "UNCHANGED")
```

### Enrichment 브랜치

```
[SplitInBatches batchSize=1] → [1초 대기]
  ↓
[IF enrich == true]
  ├─ TRUE
  │   [Code: 링크 선택]
  │     url = aplyUrlAddr || refUrlAddr1 || refUrlAddr2 || null
  │   ↓
  │   [HTTP Request]
  │     timeout 10s, follow redirects, max body 3MB
  │     User-Agent: YouthFit-Bot/1.0 (+https://youthfit.kr/bot)
  │     "Continue on Fail" = true
  │   ↓
  │   [Code: boilerplate 제거 + 첨부 후보 수집]
  │     - cheerio: script/style/nav/footer/aside/header 제거
  │     - 본문 우선순위: main / article / [role="main"] / #content / body
  │     - 연속 공백/개행 정리, 8000자 컷
  │     - <a href> 중 .pdf/.hwp/.hwpx 확장자 → extraAttachments[]
  │     - 본문 < 200자 → status = "TOO_SHORT"
  │   ↓
  │   [OpenAI Chat — gpt-4o-mini, JSON mode]
  │     response_format: json_schema
  │     system: "정책 안내 텍스트에서 지원대상·지원내용·신청방법·제출서류·마감안내를 추출하라.
  │              근거 없는 내용은 절대 만들지 마라. 모르면 null."
  │     user: cleaned_text + 원본 API body 일부
  │     output:
  │       {
  │         supportTarget: string|null,
  │         supportContent: string|null,
  │         applyMethod: string|null,
  │         requiredDocuments: string|null,
  │         deadlineNote: string|null,
  │         confidence: 0..1
  │       }
  │   ↓
  │   [Code: enrichment 객체 조립]
  │     enrichment = {
  │       sourceUrl, fetchedAt: ISO timestamp,
  │       extractor: "openai:gpt-4o-mini",
  │       confidence, status,
  │       sections: { ... },
  │       extraAttachments: [ { name, url } ]
  │     }
  │
  └─ FALSE (UNCHANGED 또는 링크 없음)
      → enrichment 미부착
  ↓
[기존 변환 Code 노드]
   - 코드사전 풀이 + 본문 섹션 결합 (기존 로직 유지)
   - rawData.enrichment, rawData.contentHash 부착
  ↓
[POST /api/internal/ingestion/policies]
```

### status 값

| status | 의미 |
|--------|------|
| `OK` | 정상 추출 완료, 사용자 노출 후보 |
| `NO_LINK` | aplyUrlAddr/refUrlAddr* 모두 부재 |
| `FETCH_FAILED` | HTTP 4xx/5xx/타임아웃/3MB 초과 |
| `TOO_SHORT` | boilerplate 제거 후 본문 200자 미만 |
| `LLM_FAILED` | OpenAI 5xx/timeout/quota |
| `PARSE_FAILED` | LLM JSON 스키마 불일치 |
| `LOW_CONFIDENCE` | `confidence < 0.6` |

`OK` 외에는 enrichment 객체를 저장하되 사용자 응답에서 마스킹.

### 환경변수

- `YOUTH_CENTER_API_KEY` (기존)
- `BACKEND_URL` (기존)
- `INTERNAL_API_KEY` (기존)
- `OPENAI_API_KEY` (NEW, n8n credentials)

---

## 백엔드 변경사항

### 신규 API: 외부 hash 조회

```
GET /api/internal/ingestion/policies/external-hashes
    ?source=YOUTH_CENTER
Header: X-Internal-Api-Key

응답 200:
{
  "PLY00012345": "9a2c...",
  "PLY00012346": "ab11..."
}
```

- 위치: `ingestion.presentation.controller.IngestionInternalController`
- 서비스: `IngestionService.getExternalHashes(sourceType)` (read-only)
- 쿼리: `policy_source` 테이블에서 `source_type = ?` AND `external_id IS NOT NULL` → `Map<externalId, contentHash>`
- 페이지네이션 불필요 (서울 스코프 수천 건). 전국 확대 시 재검토
- 인증: 기존 `InternalApiKeyFilter` 재사용

### intake 스키마 확장

`POST /api/internal/ingestion/policies` 의 `rawData` 에 선택 필드 추가:

```json
{
  "rawData": {
    "...": "기존 필드 유지",
    "contentHash": "9a2c...",
    "enrichment": {
      "sourceUrl": "https://...",
      "fetchedAt": "2026-05-12T04:12:33Z",
      "extractor": "openai:gpt-4o-mini",
      "confidence": 0.82,
      "status": "OK",
      "sections": {
        "supportTarget": "...",
        "supportContent": "...",
        "applyMethod": "...",
        "requiredDocuments": "...",
        "deadlineNote": null
      },
      "extraAttachments": [
        { "name": "신청서_2026.hwp", "url": "https://..." }
      ]
    }
  }
}
```

- `enrichment` 와 `contentHash` 모두 optional. 없으면 기존 동작 그대로
- `contentHash` 가 들어오면 백엔드는 다시 계산하지 않고 그대로 `PolicySource.contentHash` 에 저장. 들어오지 않으면 (복지로 등) 기존 방식대로 계산

### DB 변경

```sql
ALTER TABLE policy
  ADD COLUMN enrichment JSONB;
```

- `enrichment.extraAttachments` 는 기존 `attachments` 와 **분리 보관**
  - 이유: 출처가 다름 (API 응답 vs 외부 페이지 자동 수집) → 신뢰 라벨링·관리자 도구에서 구분 필요
  - 단, 기존 `AttachmentDownloadService` / `AttachmentExtractionScheduler` 는 두 배열 모두 처리하도록 확장

### 정책 응답 DTO 확장

`PolicyDetailResponse` 에 `enrichment` 노출:

```json
{
  "...": "기존 필드",
  "enrichment": {
    "sourceUrl": "...",
    "fetchedAt": "2026-05-12T04:12:33Z",
    "sections": { "..." },
    "extraAttachments": [ "..." ]
  }
}
```

- 노출 조건: `status == "OK"` AND `confidence >= 0.6`
- 그 외 모두 `enrichment: null`
- `extractor` / `confidence` / `status` 같은 내부 필드는 응답에서 제외 (관리자 API 에서만 노출)

### RAG·적합도 연동

- **RAG**: `rag` 모듈 청크 분할 입력에 `enrichment.sections` 를 추가 청크로 포함. 출처 메타데이터로 `source: "enriched"` 기록 → 검색 결과 인용 시 출처 구분
- **적합도**: `eligibility` 모듈은 이번 범위에서 변경 없음. v1 에서 `supportTarget` 를 LLM 보조 규칙 추출 입력으로 사용 검토

### 변경 표면 요약

- 마이그레이션 1개 (jsonb 컬럼 추가)
- 컨트롤러 메서드 1개 신설 (external-hashes 조회)
- 서비스 메서드 2개 추가 (`getExternalHashes`, intake 의 enrichment 매핑)
- 응답 DTO 확장 1개
- `attachments` 처리 파이프라인이 `extraAttachments` 도 수용하도록 분기 확장

---

## 프론트엔드 변경사항

### 정책 상세 페이지 레이아웃

```
┌─────────────────────────────────────────┐
│ 정책 제목                                │
│ 카테고리 · 기관 · 마감일                  │
│─────────────────────────────────────────│
│ 요약 (summary)                           │
│ 본문 (body) ─ 온통청년 API 원본           │
│─────────────────────────────────────────│
│ ▼ 정책 안내 페이지에서 자동 수집한 정보    │ ← NEW
│   ⓘ aplyUrlAddr 페이지 본문에서 AI 가     │
│     추출한 보조 정보입니다.               │
│   [원문 보기 →]   수집: 2026-05-12 04:12  │
│                                          │
│   • 지원대상                              │
│   • 지원내용                              │
│   • 신청방법                              │
│   • 제출서류                              │
│   • 마감안내                              │
│                                          │
│   첨부 파일 (자동 발견)                    │
│   • 신청서_2026.hwp [다운로드]            │
│─────────────────────────────────────────│
│ 첨부 파일 (공식) / 신청 채널 / 북마크      │
└─────────────────────────────────────────┘
```

### 컴포넌트 구조

```
src/features/policy/components/
  PolicyDetail.tsx                 (기존)
  PolicyEnrichmentSection.tsx      (NEW)
    ├ EnrichmentHeader
    ├ EnrichmentSectionItem
    └ EnrichmentAttachmentList
```

### 표시 규칙

- `enrichment === null` → 섹션 자체 미렌더
- `sections.*` 중 null 인 항목은 미렌더
- 출처 라벨은 시각적으로 명확히 구분 (다른 배경색 카드 + 정보 아이콘 + "AI 자동 수집" 배지)
- `sourceUrl` 은 새 탭 (`target="_blank" rel="noopener noreferrer"`)
- `fetchedAt` 은 한국어 상대 시간 + 절대 시간 툴팁
- `extraAttachments` 는 v0 에서 외부 URL 새 탭 직접 열기 (`target="_blank" rel="noopener noreferrer"`). 자동 다운로드·본문 추출 파이프라인 연결은 v1
- 모바일: 라벨 1줄 + 자세히 보기 토글

### API 타입

```typescript
type EnrichmentSection = {
  supportTarget: string | null;
  supportContent: string | null;
  applyMethod: string | null;
  requiredDocuments: string | null;
  deadlineNote: string | null;
};

type PolicyEnrichment = {
  sourceUrl: string;
  fetchedAt: string;
  sections: EnrichmentSection;
  extraAttachments: Array<{ name: string; url: string }>;
};

type PolicyDetailResponse = {
  // ... 기존
  enrichment: PolicyEnrichment | null;
};
```

---

## 에러 처리·관찰 가능성

### 실패 매트릭스

| 단계 | 실패 사유 | n8n 동작 | 백엔드 저장 | 사용자 노출 |
|------|----------|---------|------------|------------|
| external-hashes 조회 | 백엔드 다운/타임아웃 | 워크플로우 실패 (재시도 3회) | — | — |
| HTTP fetch | 404/타임아웃/3MB 초과 | `status="FETCH_FAILED"` | enrichment 저장 (마스킹) | 미노출 |
| boilerplate 파싱 | 본문 200자 미만 | `status="TOO_SHORT"` | 동상 | 미노출 |
| LLM 호출 | OpenAI 5xx/quota | `status="LLM_FAILED"` | 동상 | 미노출 |
| LLM JSON 파싱 | 스키마 불일치 | `status="PARSE_FAILED"` | 동상 | 미노출 |
| 신뢰도 미달 | confidence < 0.6 | `status="LOW_CONFIDENCE"` | 동상 | 미노출 |
| 링크 부재 | URL 모두 null | `status="NO_LINK"` | 동상 | 미노출 |
| 정상 | — | `status="OK"` | 저장 | 노출 |

핵심 원칙: 풍부화 실패는 정책 등록을 막지 않는다.

### 관찰 가능성

**n8n**:
- 워크플로우 마지막에 요약 노드 추가: 처리 건수 / NEW / CHANGED / UNCHANGED / enrichment status 별 카운트
- 실패 시 운영자 알림 webhook (선택)

**백엔드**:
- `IngestionRunLog` 에 enrichment 통계 컬럼 추가:
  - `enrichment_attempted` (int)
  - `enrichment_ok` (int)
  - `enrichment_failed_by_status` (jsonb)
- enrichment 실패는 `IngestionItemFailure` 에 기록하지 않음 (정책 등록 실패가 아님)
- (선택, v1) Micrometer 카운터 `youthfit_enrichment_total{status="..."}`

### 운영 안전장치

- **LLM 비용 캡**: v0 에서는 변동분 수가 자연 캡 역할. v1 에서 일일 호출 임계치 + 카운터 추가
- **외부 사이트 부하**: 정책별 1초 대기 유지. 동일 호스트 연속 호출 시 0.5초 지터 추가 권장
- **개인정보 마스킹**: LLM 출력 후처리에서 주민번호/전화번호 패턴 마스킹 (`IngestionRedactScheduler` 와 동일 정책 재사용)

---

## 테스트 전략

### 백엔드 (`spring-test` 컨벤션)

| 종류 | 대상 | 시나리오 |
|------|------|---------|
| 슬라이스 | `IngestionInternalController` GET | external-hashes 응답 형식, 인증 실패, source 별 필터 |
| 슬라이스 | `IngestionInternalController` POST | enrichment 있는 요청 / 없는 요청 / contentHash 우선 사용 |
| 단위 | `IngestionService.getExternalHashes` | external_id null 제외, source 필터 |
| 단위 | `IngestionService.receivePolicy` 확장 | enrichment 저장, contentHash 저장 |
| 단위 | `PolicyQueryService` | 응답 노출 조건 (status OK + confidence ≥ 0.6) |
| 마이그레이션 | Flyway | enrichment 컬럼 추가 |

### n8n Code 노드

- 핵심 함수(`computeContentHash`, `extractCleanText`, `collectAttachmentCandidates`, `buildEnrichmentObject`)를 Code 노드 내부 함수로 분리
- v0: 수동 검증 — 서울 자치구별 샘플 정책 5~10건으로 워크플로우 수동 트리거 후 결과 시각 검증
- v1: `n8n/workflows/__tests__/` 에 jest 단위 테스트

### 프론트엔드

- `PolicyEnrichmentSection` 단위 테스트: enrichment null / 일부 sections null / extraAttachments 있음·없음
- 정책 상세 페이지 통합 테스트: enrichment 있는 정책 vs 없는 정책

---

## 점진적 롤아웃

1. **1단계**: 백엔드 변경 + 마이그레이션 배포. enrichment 컬럼은 비어있고 응답 영향 없음
2. **2단계**: n8n 워크플로우 enrichment 브랜치를 **off** 상태로 머지 (IF 노드 false 고정)
3. **3단계**: 수동 webhook 으로 **테스트 모드 (lastPage=1, 25건 제한)** 실행 → status 분포·결과 검증
4. **4단계**: 신뢰도 임계값 0.6 적절성 확인 후 enrichment 브랜치 **on**, 풀 페이징 활성화
5. **5단계**: 프론트엔드 PolicyEnrichmentSection 배포

---

## 미래 확장 (v1)

- 외부 페이지 단독 변경 감지 (페이지 hash 별도 보관, 일정 주기로 재크롤)
- LLM 일일 호출 캡·카운터 정식 구현
- `enrichment.supportTarget` 를 적합도 규칙 추출 입력으로 사용
- 어드민에서 정책별 enrichment status/confidence 조회 + 재처리 트리거
- 신뢰 임계값 사이트별·카테고리별 튜닝
- 사이트별 어댑터: 빈도 상위 1~2개 호스트 (서울시 청년몽땅정보통 등) 에 한해 작성
- 첨부파일 자동 발견 결과를 기존 첨부 추출 파이프라인에 연결 (현재는 URL 만 저장)

---

## 의존 관계 정리

- `ingestion → policy`: 정규화 전 데이터 + enrichment 전달
- `policy → rag`: enrichment.sections 를 청크 입력에 추가
- `policy → frontend`: PolicyDetailResponse 에 enrichment 노출
- n8n → `ingestion`: external-hashes 조회 + intake 두 엔드포인트만 호출
