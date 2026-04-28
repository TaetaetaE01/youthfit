# 첨부파일 추출 / 임베딩 파이프라인 설계

> 작성: 2026-04-28
> 상태: 설계 합의됨, 구현 플랜 대기
> 후속: `docs/superpowers/plans/2026-04-28-attachment-extraction-pipeline.md` (예정)
> 연관 사이클: `docs/superpowers/specs/2026-04-28-easy-policy-interpretation-design.md` (가이드)

## 0. 한 줄 요약

복지로 등 외부 출처에서 들어오는 정책 첨부(PDF / HWP / Office 문서)를 비동기 파이프라인으로 다운로드 → 텍스트 추출 → 정책 단위로 합쳐 RagIndexingService에 인덱싱 → 가이드 재생성까지 자동 진행한다. 가이드/Q&A의 입력이 정책 본문 외에 첨부 텍스트까지 자동으로 풍부해진다.

## 1. 배경 / 동기

- 복지로 API 응답의 구조화 필드(`supportTarget`, `selectionCriteria`, `supportContent`)는 짧고 디테일 부족
- 자격 예외, 세부 조건, 신청 절차는 첨부 PDF (공고문, 시행규칙)에 박혀 있음
- 현재 `policy_attachment` 테이블에는 URL만 저장 → 가이드/Q&A 가 첨부 정보를 전혀 모름
- 본 사이클은 사용자 원래 비전("복지로 첨부 → 임베딩 → 하이브리드 가이드")의 후반부

## 2. 범위

### 포함
- PDF 텍스트 추출 (Apache Tika)
- HWP 텍스트 추출 (hwplib)
- Office 문서 (DOC, DOCX, XLS, XLSX), HTML, TXT — Tika 자동 처리
- 비동기 다운로드 + 스케줄링된 추출
- S3 저장 (로컬 파일시스템 fallback 추상화)
- 정책 단위 인덱스 재생성 + 가이드 재생성
- 정책 본문 변경 감지 시 첨부 강제 재추출

### 제외 (§14 참고)
- OCR (스캔 PDF 텍스트화) — `SKIPPED(SCANNED_PDF)` 처리
- 이미지 첨부, 청크-단위 attachment_id trace, 임베딩 일일 한도, 분산 락, 작업 이력 테이블

## 3. 아키텍처

### 모듈 배치

```
┌─────────────────────────────────────────────────────────────┐
│ ingestion 모듈                                               │
│  - n8n 수신 (기존)                                            │
│  - 정책 본문 hash 변경 감지 시 첨부 PENDING 마킹                │
│  - @Async 다운로드 트리거                                      │
│  - AttachmentExtractionScheduler                            │
│  - AttachmentReindexService (정책 단위 인덱싱 + 가이드 재생성)  │
└────┬────────────────────────────────────────────────────────┘
     ↓
┌─────────────────────────────────────────────────────────────┐
│ policy 모듈                                                  │
│  - PolicyAttachment 도메인 모델 확장 (상태 머신)               │
│  - PolicyAttachmentApplicationService (전이 트랜잭션 경계)     │
└────┬────────────────────────────────────────────────────────┘
     ↓
┌─────────────────────────────────────────────────────────────┐
│ rag, guide 모듈                                              │
│  - RagIndexingService.indexPolicyDocument 그대로 호출         │
│  - GuideGenerationService.generateGuide 그대로 호출           │
└─────────────────────────────────────────────────────────────┘
```

의존 방향: ingestion → policy → (rag, guide). 기존 패턴과 동일.

### 외부 의존

- AWS S3 (또는 호환 스토리지) — 첨부 원본 저장. 로컬 fallback 가능
- Apache Tika — PDF / Office / HTML / TXT 추출
- hwplib — HWP 5.x 추출
- OpenAI Embedding API — 기존 `RagIndexingService` 통해 호출 (변경 없음)

## 4. 도메인 모델 변경

### `PolicyAttachment` (policy 모듈)

기존 컬럼 유지: `id`, `policy`, `name`, `url`, `mediaType`.

추가 컬럼:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `extractionStatus` | enum | `AttachmentStatus` |
| `storageKey` | varchar(500) nullable | S3 object key 또는 로컬 경로. DOWNLOADED 이후 채워짐 |
| `fileHash` | varchar(64) nullable | 다운로드한 원본 파일 SHA-256. 향후 변경 감지용 |
| `extractedText` | text nullable | 추출 결과. EXTRACTED 만 채워짐 |
| `extractionRetryCount` | int default 0 | 실패마다 +1 |
| `extractionError` | varchar(500) nullable | 마지막 실패 사유 (truncate) |
| `skipReason` | enum nullable | `SkipReason` (SKIPPED 일 때만) |

`BaseTimeEntity.updatedAt` 으로 마지막 전이 시각 자동 추적.

인덱스: `(extraction_status, updated_at)` — 스케줄러 폴링 효율.

### `AttachmentStatus` enum

```
PENDING       — 신규 또는 재추출 마킹됨
DOWNLOADING   — 워커 점유 중 (다운로드)
DOWNLOADED    — 다운로드 완료, 추출 대기
EXTRACTING    — 워커 점유 중 (추출)
EXTRACTED     — 종료. extractedText 사용 가능
FAILED        — 일시 오류. retryCount<3 이면 다음 사이클에서 PENDING 복귀
SKIPPED       — 영구 스킵. skipReason 보관
```

### `SkipReason` enum

```
SCANNED_PDF        — 추출 텍스트 100자 미만 (스캔 추정)
UNSUPPORTED_MIME   — 화이트리스트 외 MIME
OVERSIZED          — 50MB 초과
```

### 상태 전이 규약

```
markDownloading()         : PENDING                    → DOWNLOADING
markDownloaded(key, hash) : DOWNLOADING                → DOWNLOADED
markExtracting()          : DOWNLOADED                 → EXTRACTING
markExtracted(text)       : EXTRACTING                 → EXTRACTED
markSkipped(reason)       : (모든 비-종료 상태)         → SKIPPED
markFailed(error)         : (모든 비-종료 상태)         → FAILED  (retryCount += 1)
markPendingReextraction() : EXTRACTED|SKIPPED|FAILED   → PENDING (retryCount=0, error=null, skipReason=null)
```

위반 시 `IllegalStateException("invalid transition: " + current + " → " + target)`.

도메인 단위 테스트로 모든 전이/위반 케이스 검증.

## 5. 컴포넌트

### `policy` 모듈

```
domain/model/
  ├─ PolicyAttachment.java                  (확장)
  ├─ AttachmentStatus.java                  (신규 enum)
  └─ SkipReason.java                        (신규 enum)
domain/repository/
  └─ PolicyAttachmentRepository.java        (신규)
application/service/
  └─ PolicyAttachmentApplicationService.java (신규)
infrastructure/persistence/
  └─ PolicyAttachmentJpaRepository.java     (신규)
```

**`PolicyAttachmentRepository`** 추가 메서드:
- `findPendingForDownload(int limit)` — `status = PENDING`
- `findDownloadedForExtraction(int limit)` — `status = DOWNLOADED`
- `findFailedRetryable(int limit)` — `status = FAILED AND retryCount < 3`
- `findExtractedByPolicyId(Long policyId)` — RagIndexing 입력 합칠 때
- `existsNonTerminalByPolicyId(Long policyId)` — 정책 단위 완료 체크 (PENDING/DOWNLOADING/DOWNLOADED/EXTRACTING/FAILED 가 하나도 없는지)

**`PolicyAttachmentApplicationService`** 메서드 (모두 `@Transactional`):
- `markDownloading(Long id)` / `markDownloaded(Long id, String key, String hash)` / `markExtracting(Long id)` / `markExtracted(Long id, String text)` / `markSkipped(Long id, SkipReason reason)` / `markFailed(Long id, String error)`
- `resetFailedToPending(int limit)` — 사이클 시작 시 호출. retryCount<3 인 FAILED → PENDING
- `markPendingReextraction(Long policyId)` — 정책의 모든 첨부를 PENDING 으로

### `ingestion` 모듈

```
application/port/
  ├─ AttachmentStorage.java                 (인터페이스)
  └─ AttachmentExtractor.java               (인터페이스)
application/service/
  ├─ AttachmentDownloadService.java         (@Async, ingestion 직후 트리거)
  ├─ AttachmentExtractionScheduler.java     (@Scheduled — 추출 사이클)
  ├─ AttachmentReindexService.java          (정책 단위 인덱싱 + 가이드 재생성)
  └─ ExtractionDispatcher.java              (MIME → AttachmentExtractor 선택)
infrastructure/external/
  ├─ S3AttachmentStorage.java               (구현)
  ├─ LocalAttachmentStorage.java            (fallback 구현)
  ├─ TikaAttachmentExtractor.java           (PDF/Office/HTML/TXT)
  └─ HwpAttachmentExtractor.java            (HWP)
infrastructure/config/
  ├─ AttachmentStorageConfig.java           (env로 S3 vs Local 스위치)
  └─ AsyncConfig.java                       (확장 — 다운로드 풀)
```

**`AttachmentStorage` 포트**:
```
put(InputStream, String key, String mediaType) -> StorageReference
get(String key) -> InputStream
exists(String key) -> boolean
```

**`AttachmentExtractor` 포트**:
```
boolean supports(String mediaType)
ExtractionResult extract(InputStream stream, long sizeBytes)
```

`ExtractionResult`: sealed/record 형태로 `Success(String text)` | `Skipped(SkipReason reason)` | `Failed(String error)` 표현.

`ExtractionDispatcher`: 등록된 `AttachmentExtractor` 빈들을 순회 → `supports(mediaType) = true` 인 첫 구현 사용. HWP 우선순위가 Tika 보다 위에 오도록 `@Order` 또는 명시 등록.

### `IngestionService` 변경 (기존)

- `receivePolicy()` 마지막에 `attachmentDownloadService.downloadForPolicyAsync(policyId)` 호출 추가
- `PolicyIngestionService.registerPolicy()` 내부에서 정책 본문 sourceHash 변경 감지 시 `policyAttachmentApplicationService.markPendingReextraction(policyId)` 호출

### `rag`, `guide` 모듈

변경 없음. 기존 API 그대로 호출.

## 6. 데이터 흐름

### 정상 경로

```
[1] n8n → POST /ingestion/policies (기존)
[2] IngestionService.receivePolicy()
    ├─ PolicyIngestionService.registerPolicy()         — 정책 + 첨부(PENDING) 저장
    ├─ GuideGenerationService.generateGuide()          — 기존: 첨부 없는 1차 가이드
    └─ AttachmentDownloadService.downloadForPolicyAsync(policyId)  ← 추가
    (응답 반환, 비동기 진행)

[3] @Async — AttachmentDownloadService.downloadForPolicyAsync()
    For each attachment of policy:
    ├─ markDownloading()
    ├─ HTTP GET attachment.url (timeout: connect 10s, read 60s)
    ├─ MIME 화이트리스트 / 크기 검증 → 위반 시 markSkipped()
    ├─ AttachmentStorage.put(stream, key) → 저장 + SHA-256 계산
    └─ markDownloaded(key, hash)

[4] @Scheduled (fixedDelay 60s) — AttachmentExtractionScheduler.runCycle()
    ├─ (4-1) resetFailedToPending(limit)
    │       FAILED 중 retryCount<3 → PENDING (다음 단계에서 자연 픽업)
    │
    ├─ (4-2) findPendingForDownload(limit) — fallback / 백필 다운로드
    │       For each attachment:
    │       └─ AttachmentDownloadService.downloadOne(id) — 동기 호출
    │          (실패 시 markFailed, 성공 시 markDownloaded — 다음 단계로)
    │
    ├─ (4-3) findDownloadedForExtraction(limit) — 메인 추출 단계
    │       For each attachment:
    │       │  ├─ markExtracting()
    │       │  ├─ AttachmentStorage.get(key) → InputStream
    │       │  ├─ ExtractionDispatcher.dispatch(mediaType) → AttachmentExtractor 선택
    │       │  ├─ extract():
    │       │  │     Success(text) → 100자 미만이면 markSkipped(SCANNED_PDF), 아니면 markExtracted(text)
    │       │  │     Skipped(r)    → markSkipped(r)
    │       │  │     Failed(e)     → markFailed(e)
    │       │  (각 step 트랜잭션 분리)
    │
    └─ (4-4) 사이클 마지막: 이번 사이클에서 EXTRACTED/SKIPPED 된 첨부의 정책ID 수집
            For each policyId:
            ├─ existsNonTerminalByPolicyId(policyId) == false 면
            └─ AttachmentReindexService.reindex(policyId)
```

```
[5] AttachmentReindexService.reindex(policyId)
    ├─ 정책 본문 + EXTRACTED 첨부 텍스트 합치기 (200KB 가드 적용)
    ├─ RagIndexingService.indexPolicyDocument(policyId, mergedContent)
    │     - hash 다르면 deleteByPolicyId + 재청크 + 임베딩
    │     - hash 같으면 noop
    └─ wasIndexed=true 면 GuideGenerationService.generateGuide(policyId)
```

**[3] @Async 와 [4-2] 스케줄러 다운로드의 관계**:
- [3] 은 신규 ingestion 직후 빠른 피드백을 위한 *최적화*. PENDING → DOWNLOADING 전이를 즉시 수행
- [4-2] 는 *fallback / 백필* — `@Async` 실패, 컨테이너 재시작 중단, 기존 정책 백필 시 PENDING 이 영원히 멈추지 않도록
- 두 경로 모두 `AttachmentDownloadService.downloadOne(id)` 같은 메서드 호출. 차이는 호출 컨텍스트 (`@Async` vs 스케줄러 스레드)
- 동일 첨부 동시 다운로드 방지: `markDownloading()` 이 PENDING → DOWNLOADING 단일 전이라 두 번째 호출은 `IllegalStateException` (도메인 락 효과)

### 재추출 경로

```
[A] n8n → POST /ingestion/policies (같은 externalId, 본문 변경)
[B] PolicyIngestionService.registerPolicy()
    ├─ 정책 본문 sourceHash 비교 → 다름 감지
    ├─ 정책 본문 업데이트
    └─ PolicyAttachmentApplicationService.markPendingReextraction(policyId)
[C] 다음 스케줄러 사이클이 [3]~[5] 그대로 반복
```

### 합치기 포맷

`AttachmentReindexService` 가 만드는 mergedContent:

```
=== 정책 본문 ===
{policy.body}

=== 첨부: {attachment1.name} ===
{attachment1.extractedText}

=== 첨부: {attachment2.name} ===
{attachment2.extractedText}
...
```

200KB 초과 시 정책 본문을 우선 보존하고, 첨부는 등록 순으로 잘라 넣다가 합산 200KB 도달 시 다음 첨부부터 생략. 잘린 첨부 ID 는 로그로만.

## 7. 에러 / 리트라이

| 단계 | 가능한 실패 | 처리 |
|---|---|---|
| HTTP 다운로드 | timeout, 4xx/5xx, DNS | `markFailed`. 다음 사이클에서 PENDING 복귀 (retryCount<3) |
| MIME / 크기 검증 | 화이트리스트 외 / >50MB | `markSkipped`. 재시도 없음 |
| S3 업로드 | 네트워크 / 권한 | `markFailed`. 재시도 |
| 추출 (Tika/hwplib) | 라이브러리 예외, 손상 파일 | `markFailed`. 재시도 |
| 추출 텍스트 < 100자 | 스캔 PDF 추정 | `markSkipped(SCANNED_PDF)`. 재시도 없음 |
| RagIndexing 호출 실패 | 임베딩 API / DB | 단순 로그. 다음 ingestion 의 본문 hash 변경 감지로 자연 복구 (Q5 Option C) |

### 트랜잭션 경계
- 상태 전이 메서드 1개 = 1 트랜잭션 (`PolicyAttachmentApplicationService` 메서드 단위)
- 다운로드 / 추출 자체는 트랜잭션 밖 (네트워크 I/O)
- 흐름: T1(`markDownloading`) → 다운로드 (T 밖) → T2(`markDownloaded` 또는 `markFailed`)

### 멱등성
- S3 PUT: 같은 key 덮어쓰기 OK (자체 멱등)
- RagIndexingService: hash 비교 후 noop (기존)
- GuideGenerationService: 같은 입력에 대해 LLM 호출 스킵 가드가 있는지 구현 단계에서 확인. 없으면 추가 (입력 hash 비교)

### 동시성
- 한 인스턴스 단일 스케줄러. 분산 락 없음
- DOWNLOADING / EXTRACTING 자체가 in-flight 락 — 같은 첨부 두 번 잡지 않음
- 멀티 인스턴스 가면 ShedLock — 본 spec 비범위

## 8. 비용 / 자원 가드

| 가드 | 임계치 | 동작 |
|---|---|---|
| 단일 첨부 크기 | > 50MB | `SKIPPED(OVERSIZED)` |
| MIME 화이트리스트 | PDF/HWP/DOC/DOCX/XLS/XLSX/HTML/TXT 외 | `SKIPPED(UNSUPPORTED_MIME)` |
| 추출 텍스트 길이 | < 100자 | `SKIPPED(SCANNED_PDF)` |
| 정책 단위 합산 콘텐츠 | > 200KB | 첨부부터 잘라냄 (정책 본문 우선) |
| HTTP timeout | connect 10s / read 60s | 실패 처리 후 재시도 |
| HTTP 재시도 | 3회 | 다음 사이클에서 PENDING 복귀 |
| 사이클 처리량 | 다운로드 20건 / 추출 20건 | 사이클당 한도 |
| `@Async` 풀 | core 4 / max 8 | 다운로드 동시성 |

### 환경변수

```
ATTACHMENT_STORAGE_TYPE=s3|local
ATTACHMENT_STORAGE_S3_BUCKET=...
ATTACHMENT_STORAGE_S3_REGION=...
ATTACHMENT_STORAGE_LOCAL_PATH=/data/attachments
ATTACHMENT_DOWNLOAD_CONNECT_TIMEOUT_SECONDS=10
ATTACHMENT_DOWNLOAD_READ_TIMEOUT_SECONDS=60
ATTACHMENT_DOWNLOAD_MAX_SIZE_MB=50
ATTACHMENT_EXTRACTION_MIN_TEXT_CHARS=100
ATTACHMENT_REINDEX_MAX_CONTENT_KB=200
ATTACHMENT_EXTRACTION_RETRY_LIMIT=3
ATTACHMENT_SCHEDULER_FIXED_DELAY_MS=60000
ATTACHMENT_SCHEDULER_BATCH_SIZE=20
```

## 9. 테스트 전략

### 단위 테스트
- `PolicyAttachment` 도메인 모델: 모든 상태 전이 + 위반 매트릭스
- `ExtractionDispatcher.dispatch()`: MIME 매칭 + 미지원 MIME 처리
- 길이 / 크기 검증 로직: 100자 / 50MB / 200KB 경계값
- `TikaAttachmentExtractor` / `HwpAttachmentExtractor`: 작은 fixture (PDF / HWP / 스캔 PDF) 를 `src/test/resources/` 에 두고 추출 결과 검증

### 통합 테스트 (Spring slice / `@SpringBootTest`)
- `AttachmentDownloadService`: WireMock 으로 첨부 URL stub → 정상 / 4xx / timeout / oversized 시나리오 → 상태 + DB 검증
- `AttachmentExtractionScheduler.runCycle()`: PENDING / DOWNLOADED 픽스처 → 사이클 → 최종 상태 + `GuideGenerationService` 호출 검증 (mock)
- 재추출 흐름: 본문 hash 변경 → 첨부 PENDING 복귀 → 사이클 후 EXTRACTED 재진입

### 컨벤션
- 백엔드 CLAUDE.md, `docs/CONVENTIONS.md`, spring-test 스킬 컨벤션 준수

## 10. 운영 / 관찰성

- slf4j 구조화 로그: `attachmentId`, `policyId`, `status`, `transition` 키 일관 사용
- 단계별 INFO 로그 (다운로드 시작/완료, 추출 시작/완료, 사이클 통계)
- ERROR 로그: 실패 사유 stack trace, but `extractionError` 컬럼 truncate (500자)
- 사이클 통계 INFO: `processed=N, succeeded=M, failed=K, skipped=L, reindexed=P` (모니터링 알람 hook 지점)
- ELK / Loki 등 로그 인프라는 별도 — 본 spec 범위 밖

## 11. 마이그레이션 / 백필

- DB 마이그레이션: `policy_attachment` 테이블에 컬럼 추가 (Flyway/Liquibase 사용 여부 미확인 — 구현 플랜에서 결정)
- 기존 첨부는 `extractionStatus=PENDING` 으로 백필 (UPDATE 한 번)
- 다음 스케줄러 사이클부터 자연 처리됨
- 가이드 재생성 폭주 우려: 기존 정책 ~30개 수준이라 한 사이클 (20건) ~ 두 사이클이면 끝. 운영 부하 미미

## 12. 환경 / 의존성 추가

`backend/build.gradle` 에 추가:
- Apache Tika: `org.apache.tika:tika-core`, `tika-parsers-standard-package` (PDF / Office 등 포함)
- hwplib: `kr.dogfoot:hwplib`
- AWS SDK v2 S3: `software.amazon.awssdk:s3` (S3 사용 시)

기존 의존: Spring Boot 4.0.5, Java 21, JPA, Postgres + pgvector — 변경 없음.

## 13. PR 분할 제안

본 spec 의 작업량이 한 PR 에 다 들어가면 리뷰 부담 — 구현 플랜에서 다음 단위로 분할 검토:

1. 도메인 모델 + 상태 전이 + 마이그레이션 (정적 변경, 단위 테스트)
2. AttachmentStorage 포트 + S3/Local 구현 + 설정
3. AttachmentExtractor 포트 + Tika/Hwp 구현
4. AttachmentDownloadService (`@Async`) + IngestionService 변경
5. AttachmentExtractionScheduler + AttachmentReindexService

각 PR 은 독립적으로 머지 가능하도록 의존성 최소화. 4-5번이 함께 가야 e2e 동작.

## 14. 명시적 비범위

- **OCR (스캔 PDF 텍스트화)** — Tesseract 미도입. SKIPPED 처리
- **이미지 첨부 추출** — JPG/PNG 화이트리스트 밖
- **임베딩 일일 한도 / 큐잉 인프라** — 운영 데이터 본 후 도입 검토 (가이드/Q&A 근거 추출 빈약하면 우선순위 상승)
- **청크-단위 attachment_id trace** — Q&A 인용 정밀도 필요 시 V1
- **다중 인스턴스 분산 락 (ShedLock)** — v0 단일 컨테이너 가정
- **추출 작업 이력 테이블** — 디버깅 SQL 안정성 필요해질 때
- **다중 출처 정책 dedup** — 별도 brainstorming 사이클
- **사용자 lazy 트리거 / 가이드 피드백 / Accordion UX** — `next-steps.md` 별도 항목

## 15. 결정 로그

| # | 결정 | 선택 | 사유 |
|---|---|---|---|
| Q1 | 추출 범위 | PDF + HWP, OCR 제외 | OCR 도입 비용 vs 가치 — v0 부담 큼 |
| Q2 | 트리거 모델 | 비동기 (별도 스케줄러) | ingestion 응답시간 / 신뢰성 분리 |
| Q2-2 | 저장 위치 | S3 (로컬 fallback 추상화) | 운영 표준, 키 발급 전엔 fallback |
| Q2-3 | 다운로드 처리 | `@Async` (ingestion 직후 백그라운드) | 응답시간 + 미처리 재시도 인프라 어차피 필요 |
| Q3 | 추출 텍스트 저장 | PolicyAttachment 컬럼, 정책 단위 합쳐 RagIndexing | 기존 hash/재인덱싱 로직 재사용 |
| Q4 | 추출 라이브러리 | Tika + hwplib | 다양한 MIME 자동 처리 + HWP 별도 |
| Q5 | 가이드 재생성 트리거 | 추출 워커가 직접 호출 | 이벤트 드리븐 미사용 (CLAUDE.md) |
| Q6 | 상태 전이 추적 | 단계 분리 + 컬럼만 (이력 테이블 없음) | 상태 머신 강제 + v0 단순 |
| Q6 sub | FAILED 재시도 | 다음 사이클에 PENDING 자동 복귀 | 폴링 쿼리 단순 |
| Q6 sub | 재시도 횟수 / 백오프 | 3회 / 즉시 | 새벽 배치 시나리오 |
| Q6 sub | SKIPPED 트리거 | <100자 / 화이트리스트 외 / >50MB | 운영 보고 조정 |
| Q7 | 재추출 정책 | ingestion 본문 hash 변경 시 강제 PENDING | 매 사이클 다운로드 부담 회피 |
| Q8 | 비용 가드 | 정책 단위 합산 200KB 제한 | CLAUDE.md 비용 방어 요구사항 |
| Q5 후속 | RagIndex 실패 재시도 | 단순 로그 (다음 ingestion 으로 복구) | 임베딩 장애 드물고 자연 복구 |

## 16. 후속 / 미결

- GuideGenerationService 입력 hash 캐시 가드 존재 여부 — 구현 플랜 단계에서 코드 1줄 확인. 없으면 작업 항목 추가
- DB 마이그레이션 도구 (Flyway / Liquibase / 수동) — 구현 플랜에서 결정
