# 어드민 — Spec 2: 이메일 발송 추적 설계 (Outline)

> **상태**: Outline (구현 직전 brainstorming 필요)
> **작성일**: 2026-05-05
> **시리즈**: 어드민 시리즈 5개 중 #2
> **선행**: Spec 1 (admin foundation, 완료)

---

## 1. 목표

어드민이 다음을 운영적으로 추적할 수 있게 한다.
- **건별**: 누구에게, 언제, 어떤 이메일이 발송됐고 결과가 무엇인지
- **집계**: 일자별 발송/성공/실패/바운스 수치, 추세

알림은 v0의 핵심 기능(F-09)이라 운영상 critical. 발송 실패를 빠르게 식별·재처리할 수 있어야 한다.

## 2. 범위

### In
- `EmailSendLog` 엔티티 신설(또는 기존 발송 트랜잭션에 적재 hook)
- SES 이벤트(전송/바운스/컴플레인) 수신 → 로그 상태 업데이트
- 어드민 화면:
  - 일자별 집계 차트 (발송/성공/실패/바운스)
  - 건별 테이블 (필터: 상태/기간/수신자)
  - 행 클릭 → 상세(헤더, 본문 일부, SES 메시지ID, 재시도 이력)
- 재발송 버튼 (실패한 건에 한해)

### Out
- A/B 테스트, 발송 캠페인 UI
- 사용자별 구독/수신거부 화면 (이건 일반 사용자 영역)
- 외부로의 이메일 export

## 3. 데이터 모델 outline

```
EmailSendLog (user 모듈 또는 별도 notification 모듈)
- id
- recipient_email
- recipient_user_id (nullable)
- email_type: enum (BOOKMARK_DEADLINE, RECOMMENDATION, ...)
- subject
- ses_message_id
- status: enum (QUEUED, SENT, DELIVERED, BOUNCED, COMPLAINED, FAILED)
- error_code (nullable)
- error_message (nullable)
- retry_count
- sent_at
- updated_at
```

> **결정 보류**: 본문 보관 여부 — 본문은 대용량이므로 별도 테이블 또는 외부 스토리지(S3) 고려. 우선은 보관 안 함, 디버깅 시 재구성 가능하도록 input 데이터(user_id + email_type + 시점)만 저장.

## 4. SES 이벤트 통합 outline

옵션 두 가지:
- **a. SNS → 백엔드 webhook 엔드포인트**: SES → SNS 토픽 → 백엔드가 받아서 EmailSendLog 업데이트
- **b. 폴링**: 주기적으로 SES API로 상태 확인 (지연/비용 부담)

→ a 권장. 이미 SES 인프라가 있으므로 SNS 토픽 추가가 자연스러움. webhook 엔드포인트는 `/api/internal/notifications/ses-event` 형태로 만들고 InternalApiKeyFilter로 보호.

## 5. 어드민 화면 outline

### 5.1 라우트
- `/admin/email` — 메인 (집계 + 건별 리스트)
- `/admin/email/:logId` — 상세

### 5.2 메인 화면 구성
- 상단: 기간 선택(7D/30D/90D) + 상태 필터(전체/실패만/바운스만)
- 차트: 일자별 stacked bar (성공/실패/바운스)
- KPI: 오늘 / 이번주 / 성공률
- 테이블: 발송 시각 / 수신자 / 타입 / 상태 / 재시도 수 / 액션(재발송)

### 5.3 상세 화면
- 메타: SES message_id, 시각, 재시도 이력
- 입력 데이터 (user_id, email_type, 변수)
- 에러 정보 (있을 경우)
- 액션: 재발송, 수신자 차단(suppression list 추가)

## 6. 보관 정책

- 발송 로그: 90일 (TTL 삭제 또는 cold storage 이전)
- 본문 보관 안 함 (위 §3 참고)

## 7. 테스트 전략 outline

- 단위: SES 이벤트 파싱, 상태 전이 (QUEUED→SENT→DELIVERED 등)
- 통합: SNS 페이로드 → webhook → DB 업데이트
- 슬라이스: 어드민 컨트롤러(@WebMvcTest), 필터/페이지네이션
- 프론트: Vitest + Testing Library, 차트는 placeholder 검증

## 8. 의존성

- Spec 1 (admin foundation) 완료 ← 의존
- 후속 spec은 의존 안 함

## 9. 열린 질문 (구현 직전 brainstorming에서 결정)

- 본문 보관: 진짜 안 할 건지 / 일부 메타만 (template_id + variables)
- SES 이벤트 받는 webhook 인증 방식 (SNS subscription confirmation 자동 처리 vs 수동)
- 재발송 시 새 EmailSendLog row 생성 vs retry_count 증가
- 어드민에서 사용자 차단(suppression)을 직접 관리할지, AWS SES 콘솔로 위임할지
- 차트 라이브러리 선정 (recharts, visx, 자체 SVG)

## 10. 변경 영향 범위 (예상)

- `user/notification` 또는 신규 `notification` 모듈에 `EmailSendLog` 엔티티/리포지토리
- `EmailSender` 포트 구현체(`SesEmailSender`)에 적재 호출 추가
- 신규 `notification/infrastructure/external/SesEventListener` (SNS webhook)
- `admin/presentation/controller/AdminEmailLogController` (조회용 ReadModel)
- 프론트: `pages/admin/AdminEmailLogPage`, `apis/admin.email.api.ts`, `hooks/queries/useAdminEmailLogs.ts`
