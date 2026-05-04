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
- **해제 절차 + 체크리스트**: `docs/superpowers/operations/2026-04-29-cost-guard-active.md`
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
