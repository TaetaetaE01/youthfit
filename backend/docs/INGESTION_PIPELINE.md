# Ingestion Pipeline — n8n 부터 데이터 추출까지

> **목적**: 외부 정책 사이트에서 데이터를 긁어와 YouthFit DB 에 적재되기까지의 전 과정을 한 페이지로 정리한다.
> **대상**: 백엔드/운영 담당자, 신규 합류 개발자.

---

## TL;DR

```
[외부 정책 사이트]
       ↓ (HTML 스크래핑)
[n8n 워크플로우]  ── 매일 03:00 스케줄
       ↓ (HTTP POST + X-Internal-Api-Key)
[Spring Boot 백엔드]
   /api/internal/ingestion/policies
       ↓
[IngestionService]  →  정책 정규화·중복 제거·기간 추출
       ↓
[PostgreSQL]  +  [비동기 첨부 다운로드 / 텍스트 추출 / RAG 인덱싱]
```

전체 파이프라인은 **3 계층**으로 나뉜다:

| 계층 | 책임 | 핵심 컴포넌트 |
|------|------|----------------|
| 1. 수집 | 외부 페이지 크롤링·파싱 | n8n 워크플로우 |
| 2. 수신 | 데이터 검증·정규화·DB 적재 | `IngestionService` |
| 3. 후속 처리 | 첨부 다운로드·텍스트 추출·RAG 인덱싱 | `AttachmentDownloadService`, `AttachmentExtractionScheduler` |

---

## 1. 수집 계층 (n8n)

### 1-1. 워크플로우 목록

`n8n/workflows/` 디렉토리에 4 개의 워크플로우가 있다.

| 파일 | 소스 | 트리거 | 용도 |
|------|------|--------|------|
| `youth-seoul-crawl.json` | 청년몽땅정보통 (youth.seoul.go.kr) | 매일 03:00 cron | 서울시 청년 정책 일반 수집 |
| `youth-center-seoul.json` | 온라인청년센터 | 매일 03:00 cron | 중앙부처 정책 수집 |
| `bokjiro-central-welfare.json` | 복지로 (bokjiro.go.kr) | 매일 03:00 cron + Webhook | 중앙부처 복지 정책 수집 |
| `force-enrich.json` | (백엔드 콜백 전용) | Webhook | 특정 정책 enrichment 강제 재실행 |

### 1-2. 표준 수집 흐름 (youth-seoul 기준)

```
[Schedule Trigger]              매일 03:00
       ↓
[페이지 초기화]                 pageIndex = 1
       ↓
[목록 페이지 요청]              GET /infoData/plcyInfo/ctList.do?pageIndex=N
       ↓
[plcyBizId 추출]                정규식 goView\('([A-Z]\d+)'\) 로 ID 추출
       ↓                        마지막 페이지 번호도 같이 파싱
[Split In Batches (size=1)]     정책 1 건씩 순차 처리
       ↓
[3 초 대기]                      Rate Limit
       ↓
[상세 페이지 요청]              GET /infoData/plcyInfo/view.do?plcyBizId=...
       ↓
[상세 데이터 파싱]              제목/카테고리/기관/지원내용/신청기간 추출
       ↓
[백엔드 API 전송]               POST /api/internal/ingestion/policies
       ↓
(배치 끝나면) [다음 페이지 확인] → 다음 페이지 있으면 목록 요청 반복
```

### 1-3. 운영 원칙

- **User-Agent**: `YouthFit-Bot/1.0 (+https://youthfit.kr/bot)` (식별 가능한 봇)
- **Rate Limit**: 정책 간 3 초 대기
- **robots.txt 준수**, 보수적 수집
- **민감값 분리**: `INTERNAL_API_KEY`, `BACKEND_URL` 은 환경변수로 주입 (워크플로우 JSON 에 하드코딩 금지)

### 1-4. 백엔드로 보내는 페이로드 형태

```json
{
  "source": {
    "url": "https://youth.seoul.go.kr/.../view.do?plcyBizId=V202600006&...",
    "type": "YOUTH_SEOUL",
    "fetchedAt": "2026-05-26T03:00:00"
  },
  "rawData": {
    "title": "청년 월세 지원",
    "body": "사업개요: ...\n지원대상: ...\n지원내용: ...\n신청방법: ...\n제출서류: ...",
    "category": "복지",
    "region": "서울",
    "applyStart": "2026-04-01",
    "applyEnd": "2026-05-31"
  },
  "pipeline": {            // optional, 실패 추적용
    "workflowName": "youth-seoul-crawl",
    "executionId": "...",
    "nodeName": "백엔드 API 전송"
  }
}
```

---

## 2. 수신 계층 (Spring Boot)

### 2-1. 엔드포인트 & 인증

| 항목 | 값 |
|------|-----|
| URL | `POST /api/internal/ingestion/policies` |
| 인증 | `X-Internal-Api-Key` 헤더 — `InternalApiKeyFilter` 가 검증 |
| Controller | `IngestionController` |
| Service | `IngestionService.receivePolicy()` |
| 응답 코드 | `202 ACCEPTED` |

> `/api/internal/*` 전체 경로는 `InternalApiKeyFilter` 로 차단된다. 외부에서 직접 호출 불가.

### 2-2. `IngestionService.receivePolicy()` 가 하는 일

순서대로:

1. **카테고리 매핑** — 한글 카테고리("복지", "주거", "일자리"…) → `Category` enum
2. **소스 타입 검증** — `SourceType` enum (`YOUTH_SEOUL`, `BOKJIRO`, …). 잘못된 값은 `YOUTH_SEOUL_CRAWL` 로 fallback
3. **콘텐츠 해시 계산** — n8n 이 보낸 `sourceHash` 가 없으면 페이로드 전체를 SHA-256 으로 해싱
4. **본문 섹션 분리** — `[개요]`, `[지원대상]`, `[선정기준]`, `[지원내용]` 마커 기준으로 분리
5. **신청 기간 해석** (`PeriodResolver`) — n8n 이 못 뽑은 경우 본문/첨부에서 정규식·LLM 으로 추출
6. **정책 등록 위임** — `PolicyIngestionService.registerPolicy()` 호출
   - `source_hash` 가 같으면 중복으로 판단 → `SKIPPED_DUPLICATE` 반환
   - 신규/변경이면 `policy`, `policy_source` 테이블 upsert
7. **코드 기반 룰 추출** (`CodeBasedRuleExtractionService`) — `rawCodes` 가 있으면 자격 룰 생성
8. **이벤트 발행** — `PolicyUpsertedEvent` 발행 → 가이드 생성 (`GuideGenerationEventListener`), 적합도 룰 추출 (`EligibilityRuleGenerationEventListener`), RAG 1차 인덱싱 (`RagIndexingEventListener`) 트리거. RAG 는 본문+enrichment 만으로 즉시 인덱싱하고, 첨부 추출이 종결되면 `AttachmentReindexService` 가 본문+첨부 merged content 로 2차 재인덱싱한다 (`source_hash` 변경으로 자동 갱신).
9. **첨부 다운로드 트리거** — `attachmentDownloadService.downloadForPolicyAsync(policyId)` (비동기)
10. **실행 로그 적재** — 성공/실패 모두 `ingestion_run_log` 테이블에 기록
11. **실패 적재** — 예외 발생 시 `ingestion_item_failure` 테이블에 페이로드/스택트레이스/n8n 메타와 함께 기록

### 2-3. 중복 제거 메커니즘

- **key**: `policy_source.source_hash` (SHA-256)
- 동일 정책의 동일 콘텐츠가 다시 들어오면 `Outcome.SKIPPED_DUPLICATE` 로 빠짐
- 콘텐츠가 변경되면 hash 가 달라지므로 `policy` 의 내용을 업데이트하고 신규 source 행 적재

---

## 3. 후속 처리 계층 (비동기)

### 3-1. 첨부 다운로드 (`AttachmentDownloadService`)

```
[IngestionService 완료 직후]
       ↓ (비동기, attachmentDownloadExecutor)
[downloadForPolicyAsync(policyId)]
       ↓
[정책별 PENDING 첨부 조회]
       ↓ MIME 화이트리스트 검증 (pdf/hwp/docx/xlsx/html/txt)
       ↓ 최대 50MB 제한 (attachment.download.max-size-mb)
[HTTP 다운로드]
       ↓ 성공 시 S3 또는 LocalAttachmentStorage 로 업로드
[상태 전이: PENDING → DOWNLOADING → DOWNLOADED]
```

실패하면 `markFailed`, 너무 크면 `markSkipped(OVERSIZED)`, 지원 안하는 MIME 이면 `markSkipped(UNSUPPORTED_MIME)`.

### 3-2. 텍스트 추출 (`AttachmentExtractionScheduler`)

**60 초마다** 실행되는 폴링 스케줄러. 각 사이클에서:

| 단계 | 동작 |
|------|------|
| ① | FAILED 상태 중 `retry_count < retryLimit (=3)` 인 첨부 → PENDING 으로 되돌림 |
| ② | PENDING 첨부 → 다운로드 시도 (fallback / 백필) |
| ③ | DOWNLOADED 첨부 → `ExtractionDispatcher` 로 텍스트 추출 |
| ④ | 한 정책의 모든 첨부가 종결 상태가 되면 → 정책 단위 **RAG 재인덱싱** 트리거 |

### 3-3. `ExtractionDispatcher` 의 추출기 선택

`AttachmentExtractor` 포트를 구현한 빈들을 순회하며 `supports(mediaType)` 가 true 인 첫 번째 구현체로 위임:

| 구현체 | 담당 MIME |
|--------|-----------|
| `TikaAttachmentExtractor` | pdf, doc, docx, xlsx, html, txt 등 Apache Tika 가 처리 가능한 전부 |
| `HwpAttachmentExtractor` | hwp (한글 파일) — Tika 가 약해서 별도 구현 |

추출 결과는 3 가지 중 하나:

- **Success(text)** — 글자 수가 `min-text-chars` (=100) 이상이면 `EXTRACTED`. 미만이면 스캔본 PDF 로 간주, `SKIPPED(SCANNED_PDF)`.
- **Skipped(reason)** — 지원 안 함 / 스캔본 등
- **Failed(error)** — 추출 중 예외

### 3-4. 신청 기간 해석 (`PeriodResolver`)

n8n 단계에서 정규식으로 못 뽑은 케이스를 위한 다단계 추출. **소스 우선순위**:

1. `N8nApplyFieldsSource` — n8n 이 명시적으로 보낸 `applyStart`/`applyEnd`
2. `BodyLabeledRegexSource` — 본문에서 "신청기간: …" 같은 라벨 기반 매칭
3. `BodyGenericRegexSource` — 본문에서 일반 날짜 패턴
4. `AttachmentLabeledRegexSource` — 첨부 텍스트에서 라벨 기반 매칭
5. `OpenAiPolicyPeriodDisambiguator` / `OpenAiPolicyPeriodExtractor` — 위에서 못 뽑으면 LLM 으로 추출 (비용 방어 장치 있음)

해석 결과는 `policy.apply_start_date`, `policy.apply_end_date` 와 함께 출처·confidence 도 같이 저장.

---

## 4. 관측 / 운영

### 4-1. 실행 로그

| 테이블 | 기록 시점 | 컬럼 요약 |
|--------|-----------|-----------|
| `ingestion_run_log` | 정책 1 건 수신할 때마다 (성공·실패 모두) | source, 시작/종료 시각, 중복 여부, period 해석 메타 |
| `ingestion_item_failure` | 수신 중 예외 발생 시 | 원본 페이로드, 실패 사유 분류, 스택트레이스, n8n workflow/execution/node 이름 |

### 4-2. 어드민 가시성

- `admin` 모듈의 ingestion 헬스 대시보드에서 실행 빈도·실패율·기간 추출 신뢰도 확인
- 실패 페이로드는 어드민에서 재전송 (`RetryFailedIngestionItemUseCase`) 가능

### 4-3. 운영 변수

| 환경변수 | 기본값 | 용도 |
|----------|--------|------|
| `INTERNAL_API_KEY` | (필수) | n8n → 백엔드 내부 호출 인증 |
| `BACKEND_URL` | `http://backend:8080` | n8n 이 호출할 백엔드 주소 |
| `attachment.download.max-size-mb` | 50 | 첨부 최대 크기 |
| `attachment.scheduler.fixed-delay-ms` | 60000 | 추출 스케줄러 폴링 주기 |
| `attachment.extraction.retry-limit` | 3 | FAILED → PENDING 재시도 한계 |
| `attachment.extraction.min-text-chars` | 100 | 이 미만이면 스캔본 PDF 로 간주 |
| `S3_KEY_PREFIX` | (env 별) | S3 첨부 키 prefix |

---

## 5. 코드 위치 빠른 참조

### n8n
- `n8n/workflows/youth-seoul-crawl.json` — 청년몽땅정보통 표준 수집
- `n8n/workflows/youth-center-seoul.json` — 온라인청년센터
- `n8n/workflows/bokjiro-central-welfare.json` — 복지로
- `n8n/workflows/force-enrich.json` — 강제 enrichment
- `n8n/README.md` — 워크플로우 운영 가이드

### 백엔드 — 수신
- `presentation/controller/IngestionController.java` — `/api/internal/ingestion/policies`
- `presentation/dto/request/IngestPolicyRequest.java` — 요청 스키마
- `application/service/IngestionService.java` — 메인 오케스트레이션
- `infrastructure/config/InternalApiKeyFilter.java` — API 키 인증

### 백엔드 — 첨부 처리
- `application/service/AttachmentDownloadService.java` — 다운로드
- `application/service/AttachmentExtractionScheduler.java` — 추출 스케줄러
- `application/service/ExtractionDispatcher.java` — MIME 별 추출기 선택
- `infrastructure/external/TikaAttachmentExtractor.java` — Tika 추출기
- `infrastructure/external/HwpAttachmentExtractor.java` — HWP 추출기
- `infrastructure/external/S3AttachmentStorage.java` / `LocalAttachmentStorage.java` — 저장소

### 백엔드 — 기간 해석
- `domain/service/PeriodResolver.java` — 다단계 fallback
- `domain/service/source/*` — 5 가지 추출 소스
- `infrastructure/external/OpenAiPolicyPeriodExtractor.java` — LLM 폴백

### 백엔드 — 관측
- `domain/model/IngestionRunLog.java`
- `domain/model/IngestionItemFailure.java`
- `application/service/RetryFailedIngestionItemUseCase.java`

---

## 6. 자주 묻는 질문

**Q. 같은 정책이 매일 들어오는데 DB 가 안 부풀어 오르나요?**
A. `policy_source.source_hash` 가 같으면 `SKIPPED_DUPLICATE` 로 빠집니다. 콘텐츠가 변경된 경우에만 정책이 업데이트되고 새 source 행이 추가됩니다.

**Q. n8n 이 죽으면 어떻게 되나요?**
A. n8n 은 cron 기반이라 다음 03:00 에 다시 돌면 됩니다. 동일 정책은 위 dedup 로직으로 걸러져서 부작용 없습니다.

**Q. 첨부 추출이 실패하면 영영 안 되나요?**
A. `AttachmentExtractionScheduler` 가 60 초마다 돌면서 FAILED → PENDING 으로 최대 3 회까지 재시도합니다. 그 이후엔 어드민에서 수동 재시도 필요.

**Q. 신청기간이 본문에 없으면 어떻게 되나요?**
A. `PeriodResolver` 가 본문 → 첨부 → LLM 순으로 fallback 합니다. LLM 까지 가도 못 뽑으면 `apply_end_date` 가 null 로 들어가고 운영팀에서 보완합니다.

**Q. 새로운 정책 사이트를 추가하려면?**
A. ① n8n 에 새 워크플로우 추가 → ② `SourceType` enum 에 새 값 추가 → ③ 필요 시 카테고리 매핑 보완 → ④ 페이로드 형식이 다르면 `IngestPolicyRequest` 에 필드 추가.
