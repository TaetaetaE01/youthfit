# 첨부파일 추출 / 임베딩 파이프라인 운영 가이드

> 관련 spec: `docs/superpowers/specs/DONE_2026-04-28-attachment-extraction-pipeline-design.md`
> 관련 plan: `docs/superpowers/plans/DONE_2026-04-28-attachment-extraction-pipeline.md`

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `ATTACHMENT_STORAGE_TYPE` | `local` | `local` 또는 `s3` |
| `ATTACHMENT_STORAGE_LOCAL_PATH` | `/data/attachments` (local) / `/tmp/youthfit-attachments` (local profile) | 로컬 저장 디렉토리 |
| `S3_BUCKET` | (빈 값) | S3 버킷명. type=s3 일 때 필수 |
| `S3_REGION` | `ap-northeast-2` | S3 리전 |
| `S3_ACCESS_KEY_ID` | (빈 값) | S3 전용 IAM 액세스 키. 비우면 IAM role/credentials 체인 사용 |
| `S3_SECRET_ACCESS_KEY` | (빈 값) | S3 전용 IAM 시크릿 키. 비우면 IAM role/credentials 체인 사용 |
| `ATTACHMENT_DOWNLOAD_CONNECT_TIMEOUT_SECONDS` | `10` | HTTP connect timeout |
| `ATTACHMENT_DOWNLOAD_READ_TIMEOUT_SECONDS` | `60` | HTTP read timeout |
| `ATTACHMENT_DOWNLOAD_MAX_SIZE_MB` | `50` | 단일 첨부 최대 크기 |
| `ATTACHMENT_EXTRACTION_MIN_TEXT_CHARS` | `100` | 추출 결과 최소 길이 (미만이면 SCANNED_PDF 스킵) |
| `ATTACHMENT_EXTRACTION_RETRY_LIMIT` | `3` | FAILED 재시도 한도 |
| `ATTACHMENT_REINDEX_MAX_CONTENT_KB` | `200` | 정책 단위 합산 콘텐츠 최대 KB |
| `ATTACHMENT_SCHEDULER_FIXED_DELAY_MS` | `60000` | 스케줄러 사이클 간격 (1분) |
| `ATTACHMENT_SCHEDULER_BATCH_SIZE` | `20` | 사이클당 처리 한도 |

## 첫 배포 절차

1. backend 빌드 + 컨테이너 재기동:
   ```bash
   docker compose up -d --build backend
   ```

2. JPA `ddl-auto: update` (local) 또는 마이그레이션으로 `policy_attachment` 테이블에 새 컬럼 7개 추가됨:
   - `extraction_status`, `storage_key`, `file_hash`, `extracted_text`,
     `extraction_retry_count`, `extraction_error`, `skip_reason`
   - 인덱스: `idx_policy_attachment_status_updated`

3. 기존 첨부 백필:
   ```bash
   docker compose exec postgres psql -U youthfit -d youthfit \
     -c "UPDATE policy_attachment
         SET extraction_status='PENDING', extraction_retry_count=0
         WHERE extraction_status IS NULL;"
   ```

4. 다음 스케줄러 사이클(기본 1분)부터 자동 처리 시작.

## 운영 모니터링

### 주요 로그 키워드 (slf4j)

| 메시지 | 정상 빈도 | 알람 트리거 기준 |
|---|---|---|
| `downloaded attachment: id=...` | 정책 ingestion 시 | — |
| `attachment download failed: ...` | 드물게 (네트워크 문제) | 10분간 같은 ID 3회 이상 |
| `attachment oversized skipped` | 큰 PDF | — |
| `reindex policyId=... chunks=...` | 정책당 1회 (변경 시 재호출) | — |
| `reindex failed: policyId=` | **알람** | 발생 즉시 |
| `Reset N FAILED attachments to PENDING` | 매 사이클 | 같은 정책 첨부 5회 이상 reset |

### 상태별 SQL

```sql
-- 상태 분포 한눈에
SELECT extraction_status, count(*)
FROM policy_attachment
GROUP BY extraction_status;

-- 영구 실패 (3회 재시도 모두 실패)
SELECT id, name, url, extraction_error
FROM policy_attachment
WHERE extraction_status='FAILED' AND extraction_retry_count >= 3;

-- 스킵 사유 분포
SELECT skip_reason, count(*)
FROM policy_attachment
WHERE extraction_status='SKIPPED'
GROUP BY skip_reason;

-- 처리 중 (오래 머물면 비정상)
SELECT id, name, updated_at
FROM policy_attachment
WHERE extraction_status IN ('DOWNLOADING','EXTRACTING')
  AND updated_at < now() - interval '10 minutes';
```

## S3 전환 절차

S3 버킷/IAM 키를 받은 시점에 다음을 적용:

### IAM 키 발급 정책 (루트 키 금지)

- **루트 계정 액세스 키 사용 금지**. 항상 서비스 전용 IAM 사용자로 발급
- **최소 권한**: 해당 버킷 한정 정책 권장
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": ["s3:PutObject", "s3:GetObject", "s3:HeadObject"],
        "Resource": "arn:aws:s3:::youthfit-attachments/*"
      }
    ]
  }
  ```
- 발급된 키는 S3 외 다른 AWS 서비스 (EC2/RDS/IAM) 권한 없어야 함

### 환경변수 변경

```
ATTACHMENT_STORAGE_TYPE=s3
S3_BUCKET=<버킷명>
S3_REGION=<리전>
S3_ACCESS_KEY_ID=<발급받은 IAM 액세스 키>
S3_SECRET_ACCESS_KEY=<발급받은 IAM 시크릿 키>
```

### 키 vs IAM Role

- **로컬/dev**: `S3_ACCESS_KEY_ID` / `S3_SECRET_ACCESS_KEY` 직접 주입 (.env)
- **운영 (EC2/ECS)**: `S3_ACCESS_KEY_ID` 비워두고 IAM role 사용 → DefaultCredentialsProvider 체인이 instance metadata 에서 자동 획득

### 절차

1. IAM 사용자 + 정책 발급
2. 환경변수 위처럼 설정
3. 백엔드 재기동
4. 기존 로컬 저장본은 그대로 두되, 신규 다운로드는 S3 로 감
5. (선택) 기존 EXTRACTED 첨부의 storageKey 는 로컬 경로 그대로 → 재추출 트리거하면 S3 로 옮겨짐

> **주의**: 로컬과 S3 키 포맷이 동일 (`attachments/yyyy/mm/{id}-{uuid}.{ext}`) 하므로 마이그레이션 스크립트로 한 번에 옮겨도 됨.

## 알려진 한계 (v0)

- **OCR 미지원**: 스캔 PDF (텍스트 < 100자) 는 `SKIPPED(SCANNED_PDF)` 로 박제됨
- **HWP 추출 품질**: hwplib 가 표/도장/이미지 처리 약함. 텍스트 본문만 안정적
- **이미지 첨부**: JPG/PNG 등은 화이트리스트 밖 → `SKIPPED(UNSUPPORTED_MIME)`
- **임베딩 일일 한도 없음**: 본문 + 첨부 합산 200KB 가드만. 이상 정책 폭주 시 비용 가드는 별도 도입 필요
- **분산 락 없음**: 단일 컨테이너 가정. 멀티 인스턴스 시 같은 첨부 중복 처리 가능 (단, `markDownloading()` PENDING→DOWNLOADING 단일 전이가 부분적으로 락 역할)

## 트러블슈팅

### 첨부가 영원히 PENDING 에 남아있음

원인 후보:
- 스케줄러가 안 돌고 있음 → `@EnableScheduling` 확인 (`SchedulingConfig.java`)
- 다운로드 도중 컨테이너 종료 → 다음 사이클의 `findPendingForDownload` 가 자동 픽업
- DB 인덱스 안 탐 → `EXPLAIN` 으로 확인

### 같은 정책의 모든 첨부가 EXTRACTED 인데 가이드가 안 바뀜

원인 후보:
- `RagIndexingService.indexPolicyDocument` 가 hash 동일 감지 (`updated=false`) → 정상. 이미 인덱싱된 콘텐츠
- 가이드 입력 hash 가드 (`GuideContent.hasChanged()`) 가 false 반환 → 정상

확인:
```bash
docker compose logs backend | grep -E "reindex policyId.*updated="
```

### S3 권한 오류

`s3 put failed: ...` 로그 + AWS SDK exception. IAM 권한:
- `s3:PutObject`, `s3:GetObject`, `s3:HeadObject`

### Tika 가 PDF 를 못 읽음

`tika extract: TikaException` 로그. 보통 손상 파일 또는 암호화 PDF. `SKIPPED(SCANNED_PDF)` 또는 재시도 후 `FAILED` 처리됨.

## 관련 모듈

- `ingestion`: 다운로드 / 추출 / 인덱싱 오케스트레이션
- `policy`: PolicyAttachment 도메인 + 상태 전이
- `rag`: 변경 없음 (재사용)
- `guide`: 변경 없음 (재사용)
