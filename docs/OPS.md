# OPS.md

## 시크릿 관리
- `.env`, 개인 키, 인증 정보, 토큰은 절대 커밋하지 않는다.
- `.env`, `*.pem`, credentials 디렉터리는 처음부터 ignore 한다.
- 워크플로우 export 파일을 커밋하기 전에 민감값을 제거한다.

## 환경 변수 범주
관리해야 할 대표 범주:
- Spring profile 및 서버 설정
- DB 연결 설정
- Redis 연결 설정
- OpenAI 모델 및 API 설정
- OAuth 설정
- JWT 설정
- AWS 설정
- 내부 연동 시크릿
- 알림 설정
- n8n 인증 설정

## 배포 노트
- 로컬과 운영용 compose 또는 배포 설정은 명확히 분리한다.
- 숨겨진 기본값보다 명시적 설정을 선호한다.
- 서비스 기동 또는 핵심 플로우를 막을 수 있는 외부 의존성은 문서화한다.

## 비용 및 신뢰성 안전장치
- 재사용 가능한 LLM 출력은 캐시한다.
- 임베딩이나 guide는 source content가 바뀔 때만 다시 계산한다.
- 가능하면 수집, 인덱싱, 사용자 응답 제공 책임을 분리한다.
- 실패 경로는 로그와 메트릭으로 관측 가능해야 한다.

## 토큰 비용 가드 (cost-guard) — 🚨 local 활성 중
- **현재 상태**: local profile 에 `POLICY_ALLOWLIST=7,30` default. 정책 7·30 외 ingestion 자동 LLM/임베딩 호출 모두 skip.
- **prod 영향**: 0 (prod default 빈 값 = 전체 허용).
- **해제 절차 + 체크리스트**: `docs/runbooks/2026-04-29-cost-guard-active.md`
- 환경변수 override: `POLICY_ALLOWLIST=` (빈 값 → 전체 허용) 또는 `POLICY_ALLOWLIST=7,30,42` (추가).

## 수집 운영 원칙
- 공공 API(복지로·온통청년) 호출은 rate limit과 스펙 가이드라인을 준수한다.
- 식별 가능한 User-Agent를 사용하고, 서비스 키·토큰은 절대 커밋하지 않는다.
- 추적 가능성을 위해 source URL, source type, source hash를 기록한다.
- 각 정책 레코드가 어디서 왔는지 설명할 수 있을 정도의 메타데이터(rawJson 포함)를 보존한다.
- 원문 전체를 그대로 노출하지 않고 요약·인용 범위로 제한한다.

## Q&A 의미 캐시 테이블 (2026-05-01)

`qna_question_cache` 테이블을 운영 PG에 수동 적용한다 (Flyway 미사용).

```bash
psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql
```

배포 순서:
1. 운영 PG에 위 DDL 적용
2. 백엔드 재배포

DDL 미적용 상태로 배포되면 `qna_question_cache`를 매핑한 엔티티 검증(`ddl-auto: validate`)에서 부팅 실패한다.

## 이메일 발송 (AWS SES, 2026-05-05)

### 환경변수 슬롯 (`.env`)

```bash
EMAIL_TRANSPORT=ses                       # logging | ses
MAIL_FROM_ADDRESS=...                     # SES 콘솔에서 검증한 발신 주소
MAIL_FROM_NAME=YouthFit
MAIL_BASE_URL=https://your-domain.tld     # 본문 링크 base
AWS_SES_REGION=ap-northeast-2
AWS_SES_ACCESS_KEY_ID=AKIA...             # IAM 사용자 (ses:SendEmail 만 허용)
AWS_SES_SECRET_ACCESS_KEY=...
```

### 운영 절차

1. AWS 콘솔에서 IAM 사용자 `youthfit-ses-sender` 생성 — 정책 `ses:SendEmail`, `ses:SendRawEmail` 만 허용
2. SES 콘솔에서 `MAIL_FROM_ADDRESS` 와 (sandbox 모드 시) 모든 수신자 이메일 검증
3. `.env` 슬롯 채우기 (커밋 금지 — `.gitignore` 확인)
4. 운영 PG 에 `backend/src/main/resources/sql/2026-05-05-notification-history-status.sql` 적용
5. 백엔드 재배포
6. dry-run: `EMAIL_TRANSPORT=logging` 으로 띄워서 렌더된 HTML 로그 확인 → OK 면 `EMAIL_TRANSPORT=ses` 로 전환
7. 검증된 수신자 1명에게 dry-run 발송으로 본문/CTA 확인

### Sandbox 모드 한계

SES 신규 계정은 기본 sandbox: 수신자 모두 검증 필수 + 일일 200통 / 초당 1통 제한.
운영급(베타/공개) 발송 전 sandbox 해제 신청 필요.

### 트러블슈팅

- `EmailSendException: SES 발송 실패` 로그 누적 → SES 콘솔에서 발신자 검증 상태 확인
- `notification_history.status='FAILED'` 영구 누적 → 일시적 실패면 SQL 로 reset 가능:
  ```sql
  DELETE FROM notification_history WHERE status = 'FAILED' AND failed_at < NOW() - INTERVAL '7 days';
  ```
- 24시간 이상 PENDING 행 → 운영자 수동 정리 (JVM crash 등으로 잔존):
  ```sql
  DELETE FROM notification_history WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '24 hours';
  ```

## 어드민 이메일 발송 추적 (Spec 2, 2026-05-05)

### 환경변수 슬롯 (`.env`)

```bash
YOUTHFIT_EMAIL_ATTEMPT_RETENTION_DAYS=90  # EmailSendAttempt 보관 기간 (기본 90일)
```

### DB 마이그레이션

운영 PG 에 `email_send_attempt` 테이블 수동 적용 (Flyway 미사용):

```bash
psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-05-email-send-attempt.sql
```

`ddl-auto: validate` 라 미적용 상태로 배포 시 부팅 실패.

### AWS SES + SNS 운영 작업 (1회)

DELIVERED/BOUNCED/COMPLAINED 추적은 SES Configuration Set + SNS Topic + 백엔드 webhook 으로 동작한다.

1. SES → Configuration Sets → 신규 생성 (예: `youthfit-tracking`)
2. Event destinations 추가 → Destination type **Amazon SNS** → 새 SNS Topic 생성 (예: `youthfit-ses-events`)
3. 발행 이벤트 종류 체크: **Delivery, Bounce, Complaint** (Send/Open/Click 은 비용 절약 위해 OFF)
4. SNS Topic → Subscriptions → Create subscription
   - Protocol: **HTTPS**
   - Endpoint: `https://<백엔드 호스트>/api/internal/notifications/ses-event`
5. 첫 호출 시 SubscribeURL 자동 처리 확인 — 백엔드 로그 `SNS subscription 확인 호출` 1회 출력 + AWS 콘솔에서 subscription 상태가 *Confirmed* 로 전환
6. SES 발송 시 Configuration Set 적용 (`SesEmailSender` 가 명시 호출하도록 옵션화는 후속 작업 — 현재는 SES default 또는 sender 헤더 통해 적용)

### 보안

- `/api/internal/notifications/ses-event` 는 `permitAll()` (인증은 SNS 메시지 서명 검증으로 처리)
- 표면 검증 (cert URL 도메인 + signatureVersion=1) 만 현재 코드 — 본격 X.509 서명 검증은 후속 강화 항목

### 보관 정리

매일 03:30 (UTC) `EmailSendAttemptCleanupScheduler` 가 90일 경과 row 삭제. 보관 기간 변경은 위 환경변수.

### 어드민 화면

- `/admin/email` — 일자별 차트 + KPI + 건별 테이블 + 필터 (기간/상태/타입/수신자)
- `/admin/email/:attemptId` — 메타 + 입력 데이터 + 본문 미리보기 (lazy) + 재발송 (FAILED 만)
- 어드민 권한 (`ROLE_ADMIN`) 필요 — Spec 1 의 `RequireAdmin` 가드 + `hasRole("ADMIN")` 적용

## n8n / 정책 텍스트 백필 (2026-05-19)

`youth-center-seoul.json` 의 transform 노드가 갱신됐다 (line-wrap 해제 + contact 에 enrichment 전화번호 합치기). 새로 ingest 되는 정책은 자동 적용되지만, **기존 적재 정책 행에는 일회성 backfill SQL 두 건을 실행해야 한다.**

```bash
# 1) line-wrap 해제 (모든 정책 텍스트 컬럼)
docker compose exec -T postgres psql -U youthfit -d youthfit \
  < backend/src/main/resources/sql/2026-05-19-policy-text-unwrap.sql

# 2) contact 에 enrichment.sections.contactPhone 합치기
docker compose exec -T postgres psql -U youthfit -d youthfit \
  < backend/src/main/resources/sql/2026-05-19-policy-contact-merge-phone.sql
```

(호스트에서 psql 이 가능하면 `psql -h localhost -U youthfit -d youthfit -f backend/src/main/resources/sql/2026-05-19-*.sql` 로 직접 적용도 가능.)

두 SQL 모두 idempotent (이미 정리된 행은 skip). 적용 순서는 정해져 있지 않으나 위 순서를 권장한다.
운영 환경에서는 워크플로우 재배포(`docker compose restart n8n` + 워크플로우 import) 직후 같은 시점에 적용한다.

## 어드민 Enrichment 강제 재크롤 (2026-05-21)

### 환경변수 슬롯 (`.env`)

| 키 | 용도 | 예시 / 기본값 |
|---|---|---|
| `N8N_FORCE_ENRICH_WEBHOOK_URL` | 백엔드 → n8n `force-enrich` 워크플로우 webhook URL | `https://n8n.internal/webhook/force-enrich` (local 기본 `http://localhost:5678/webhook/force-enrich`) |
| `INTERNAL_API_KEY` (기존, `youthfit.internal.api-key`) | 백엔드 ↔ n8n 콜백 공유 시크릿 — 기존 키 재사용 | 32+자 랜덤 |
| `ENRICHMENT_TIMEOUT_FIXED_DELAY_MS` (선택) | 타임아웃 스케줄러 실행 주기(ms). application.yml `enrichment.timeout.fixed-delay-ms` 기본 60000 | `60000` |

### DB 마이그레이션

운영 PG 에 enrichment_job 테이블 + `policy` 변경분 수동 적용 (Flyway 미사용):

```bash
psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-21-enrichment-job.sql
psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-21-policy-reference-site-source.sql
```

`ddl-auto: validate` 환경이라 미적용 상태로 배포 시 부팅 실패.

### 운영 노트

- 5분(`EnrichmentJobTimeoutScheduler.TIMEOUT`) 이상 PENDING/RUNNING 인 잡은 자동으로 `FAILED`(error=`timeout`) 처리된다.
- 같은 정책에 1시간 내 5회를 초과하는 재크롤 시도는 `429`(`EnrichmentJobRateLimitException`)로 거절된다.
- 같은 정책에 이미 진행 중인(`PENDING`/`RUNNING`) 잡이 있으면 새 요청은 `409`(`EnrichmentJobConflictException`)로 거절된다 — DB unique index + service guard 의 이중 방어.
- n8n webhook 호출 실패 시 잡은 즉시 `FAILED`(error=`n8n_unreachable: ...`)로 저장된다.
- 멀티 인스턴스 도입 시 `EnrichmentJobTimeoutScheduler` 에 ShedLock 추가가 필요하다 (현재 단일 인스턴스 가정).
