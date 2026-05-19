# 온통청년 enrichment 첨부의 다운로드/임베딩 파이프라인 승격 — Design Spec

- 날짜: 2026-05-16
- 스코프: 온통청년(YOUTH_CENTER) 한정. 변경은 `n8n/workflows/youth-center-seoul.json` 만.
- 백엔드/DB 스키마 변경 없음.

## 1. 배경

복지로(BOKJIRO_CENTRAL) 는 공식 API `basfrmList` 에서 첨부 메타를 받아 `rawData.attachments[]` 로 보내고, 백엔드는 `PolicyAttachment` → 다운로드 → Tika/HWP 추출 → `ATTACHMENT` 청크 임베딩 → 가이드/QnA 인용까지 일관된 파이프라인을 탄다.

온통청년은 공식 API 에 첨부 메타가 없어, n8n 의 외부 상세 페이지 HTML 휴리스틱(`a[href]` 의 확장자/다운로드 키워드)에서 첨부 후보를 모은다. 이 결과는 `_enrichment.extraAttachments` 로만 적재되고 백엔드는 메타(이름, URL)만 보존한다 — 다운로드/추출/임베딩이 전혀 일어나지 않는다.

따라서 온통청년 정책은 첨부 텍스트가 RAG/가이드/QnA 컨텍스트에서 빠져 있다.

## 2. 목표

온통청년 enrichment 단계에서 수집된 `extraAttachments` 중 다운로드 가능성이 높은 항목을 `rawData.attachments` 로도 전달해, 백엔드 기존 첨부 파이프라인(다운로드 → 추출 → 임베딩 → 인용) 을 그대로 태운다.

비목표(non-goals):
- 다른 enrichment 소스(향후 추가될 수도 있음)에 대한 일반화 — 이번 변경은 온통청년 워크플로우 한정. 향후 같은 패턴을 따르도록 운영 컨벤션으로만 명시한다.
- 외부 도메인 화이트리스트/SSRF 가드 추가 — 별개 후속 작업.
- 첨부 url 단위 fileHash 캐시 최적화 — 별개 후속 작업.
- 백엔드/DB 변경.

## 3. 접근 선택

| | A. n8n promote 노드 (채택) | B. 백엔드 ingestion 단 promote | C. 하이브리드 |
|---|---|---|---|
| 변경 범위 | n8n 워크플로우 1개 | 백엔드 코드 | 둘 다 |
| 책임 경계 | 첨부 출처 = 수집 단 | 백엔드가 두 의미 통합 | 흐릿함 |
| 테스트 비용 | 픽스처만 | 단위 + 통합 | 가장 큼 |

**A 채택.** 가장 작은 변경, 책임 경계 명확(수집은 n8n, 다운로드 이후는 백엔드), 백엔드 회귀 위험 0.

## 4. 아키텍처

워크플로우의 6개 분기 — (a) enrich 안함, (b) enrich 함/NO_LINK, (c) FETCH_FAILED 또는 TOO_SHORT(`enrichment skip: cleaned`), (d) PARSE_FAILED/LLM_FAILED, (e) LOW_CONFIDENCE, (f) OK — 가 모두 `정책 → IngestPolicyRequest 변환` 노드로 합쳐진다. 이 노드가 `rawData` 와 `rawData.enrichment` 를 최종 조립한 `json` 을 만들고, 다음으로 `백엔드 API 전송` 으로 보낸다.

따라서 신규 노드는 **`정책 → IngestPolicyRequest 변환` 직후, `백엔드 API 전송` 직전** 에 단 한 곳 끼워 넣는다. 모든 분기가 이 노드를 통과하지만, `_enrichment` 가 없거나 `extraAttachments` 가 비어 있으면 no-op이라 안전.

```
[정책별 순차 처리]
   └─> [enrichment 여부]
         ├─ enrich 함 ──> ... ──> [boilerplate 제거 + 첨부 후보]
         │                            (extraAttachments 추출)
         │                          └─> [LLM 구조화 추출] or [enrichment skip: cleaned]
         │                                ↓
         │                          [enrichment 객체 조립 / skip 변형]
         │                                ↓ (합류)
         └─ enrich 안함 ────────────────> [정책 → IngestPolicyRequest 변환]
                                                ↓
                                ┌───────────────────────────┐
                                │ ★ attachments 승격         │ ← 신규 노드 (단 1개소)
                                │   rawData.enrichment.extraAttachments
                                │   → rawData.attachments 머지
                                └───────────────────────────┘
                                                ↓
                                          [백엔드 API 전송]
```

## 5. 신규 노드 — 입출력 계약

**입력**: `정책 → IngestPolicyRequest 변환` 의 출력 `json`. 다음 구조 보장:
- `json.rawData.attachments` — `Array<{name, url, mediaType}>` (현재는 항상 빈 배열로 초기화됨)
- `json.rawData.enrichment` — `PolicyEnrichment` JSON 객체 또는 `null`
- `json.rawData.enrichment.extraAttachments` — `Array<{name, url}>` 또는 부재

**동작**:
1. `json.rawData.enrichment` 또는 `json.rawData.enrichment.extraAttachments` 가 falsy 면 입력을 그대로 통과 (no-op). 모든 enrich 미수행/실패 분기 안전.
2. `json.rawData.attachments` 가 undefined 면 빈 배열로 초기화.
3. `enrichment.extraAttachments` 순회:
   - `url` 이 string 이 아니면 skip.
   - URL 에서 쿼리스트링 `?`, fragment `#` 제거 후 소문자 변환, 마지막 `.` 이후 확장자 추출.
   - 확장자가 `pdf|hwp|hwpx|doc|docx|xls|xlsx` 중 하나면 mediaType 매핑 적용, 아니면 skip.
   - 동일 URL(대소문자 무시) 이 `rawData.attachments` 에 이미 있으면 skip.
   - 통과한 항목을 `{name, url, mediaType}` 으로 `rawData.attachments` 끝에 append. `name` 은 enrichment 가 제공한 값을 그대로 사용.
4. `enrichment.extraAttachments` 자체는 변경하지 않음 (enrichment 원형 보존).

**출력**: 동일 `json` 객체, `rawData.attachments` 만 확장된 상태.

**mediaType 매핑** (백엔드 `attachment.mime-whitelist` 의 부분집합 — 휴리스틱으로 안정적으로 탐지되는 ext 만 매핑하고, `text/html` / `text/plain` 처럼 정책 페이지의 일반 링크와 구분이 어려운 타입은 의도적으로 제외):

| ext | mediaType |
|---|---|
| pdf | application/pdf |
| hwp, hwpx | application/x-hwp |
| doc | application/msword |
| docx | application/vnd.openxmlformats-officedocument.wordprocessingml.document |
| xls | application/vnd.ms-excel |
| xlsx | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

## 6. 백엔드 하류 흐름 (변경 없음 검증)

```
POST /internal/ingestion/policies
  └─ IngestPolicyRequest.toCommand()
        rawData.attachments[] → IngestPolicyCommand.Attachment(name, url, mediaType)
  └─ IngestionService.receivePolicy()
        ├─ mapAttachments() → RegisterPolicyCommand.Attachment
        ├─ PolicyIngestionService.registerPolicy()
        │     └─ policy.replaceAttachments(...)
        └─ triggerAttachmentDownload(policyId)
  └─ AttachmentDownloadService.downloadForPolicyAsync(policyId)
        isAllowed(mediaType) ?
          ├─ Y → download → storage.put → markDownloaded(key, sha256)
          └─ N → markSkipped(UNSUPPORTED_MIME)
  └─ AttachmentExtractionScheduler
        DOWNLOADED → Tika/HWP 추출 → markExtracted(text)
  └─ PolicyUpsertedEvent → RagIndexingService.indexPolicyDocument()
        DocumentChunker.chunkWithEnrichment()
          ├─ BODY 청크
          ├─ ATTACHMENT 청크  ← 신규 첨부 텍스트가 합류
          └─ ENRICHMENT_BODY 청크
        EmbeddingProvider.embedBatch() → PolicyDocument 저장
```

보장:
- 50MB 다운로드 cap, MIME whitelist, async 실행 — `AttachmentDownloadService` 기본값 그대로
- `sourceHash` 동일 시 RAG 재임베딩 skip
- Guide/QnA 인용 시 `attachmentRef` + `ATTACHMENT` source 라벨

## 7. 에러 처리

| 시나리오 | 동작 |
|---|---|
| `_enrichment == null` 또는 `extraAttachments` 빈 배열 | no-op, 입력 통과 |
| `rawData.attachments == undefined` | 빈 배열로 초기화 후 머지 |
| `url` 이 falsy / non-string | 해당 항목 skip |
| 확장자 매칭 실패 | enrichment 에만 남고 attachments 로 승격 안 됨 |
| 동일 URL 이 본 attachments 에 이미 존재 | 머지 skip |
| 노드 자체 예외 | n8n 기본 동작으로 워크플로우 실패 (fail-fast). 승격 로직은 순수 함수 수준이라 가능성 낮음. |
| 백엔드 다운로드 실패 | 기존 `markFailed`/`markSkipped` 흐름. 정책 등록은 성공으로 종료. |

## 8. 테스트

### 8.1 n8n 픽스처 (이번 스코프 포함)

`n8n/workflows/__fixtures__/promote-attachments/` 에 케이스별 입력/기대 출력 JSON 을 둔다:

- `case-empty-enrichment.json` — `_enrichment: null` → no-op
- `case-mixed-extensions.json` — pdf, hwp, 확장자 없는 download.do 혼합 → pdf/hwp 2건만 승격, download.do 는 enrichment 에만 잔존
- `case-duplicate-url.json` — 본 attachments 에 동일 URL 존재 → 머지 skip
- `case-mixed-case-ext.json` — `.PDF` 대문자 → 정상 인식
- `case-query-suffix.json` — `report.pdf?ts=123` → 정상 인식
- `case-non-string-url.json` — `url: null` 한 건 + 정상 1건 → 한 건만 승격

검증은 노드 코드와 동일 알고리즘을 픽스처에 수동 적용 후 diff (n8n 자동 test harness 도입은 별개 작업).

### 8.2 백엔드 (변경 없음 회귀 확인)

신규 테스트 추가 없음. 다음 기존 테스트가 통과함을 확인:
- `IngestionInternalControllerTest` — `rawData.attachments` 가 채워진 경로
- `AttachmentDownloadServiceTest` — whitelist / oversize / failed 분기

### 8.3 단대단 (배포 후 1회 수동)

- 온통청년 워크플로우 1정책 수동 실행
- DB 확인:
  - `policy_attachment` 신규 row 들 (URL 도메인이 외부 정책 안내 페이지)
  - `extraction_status = EXTRACTED` 도달
  - `policy_document` 에 `source = ATTACHMENT` 청크 존재
- QnA 콘솔에서 해당 정책 질문 → `attachmentRef` 인용 노출

## 9. 운영/모니터링

- `policy_attachment.extraction_status` 별 분포 모니터링:
  - `SKIPPED(UNSUPPORTED_MIME)` 비율 증가 → n8n 추론 ↔ 백엔드 whitelist 어긋남 신호
  - `FAILED` 비율 증가 → 외부 다운로드 도메인 응답성 신호
- 신규 attachment 다운로드량 증가 → 스토리지 사용량 추적

## 10. 후속 작업 후보 (이번 스코프 밖)

- **외부 도메인 화이트리스트** — `AttachmentHttpClient` 에 호스트 화이트리스트/SSRF 가드. 휴리스틱이 임의 외부 도메인을 가리킬 수 있어 노출 표면이 넓어진다.
- **첨부 url 단위 fileHash 캐시** — `replaceAttachments` 가 전체 교체라 동일 URL 의 이전 파일도 재다운로드된다. URL 키 기준 idempotent 보존.
- **다른 enrichment 소스 대응** — 향후 BOKJIRO 외 enrichment 가 추가되면 같은 promote 노드 패턴 재사용. 운영 컨벤션 문서화.
- **첨부 개수 cap** — 현재는 cap 없음. 이상 페이지 대응 필요해지면 정책당 N개 제한.
- **n8n 노드 자동화 테스트** — 픽스처 → CLI 실행 → diff 자동화.
