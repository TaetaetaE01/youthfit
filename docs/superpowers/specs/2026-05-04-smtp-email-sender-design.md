# SMTP 이메일 발송 어댑터 도입

작성일: 2026-05-04
관련 spec: `2026-05-04-notification-recommendation-design.md`

## 1. 배경

YouthFit의 이메일 알림 기능(마감 알림 / 맞춤 정책 추천)은 이미 구현되어 있으나, 현재 `EmailSender` 포트의 유일한 구현체는 `LoggingEmailSender`로 **백엔드 로그에 발송 시뮬레이션을 출력할 뿐 실제 이메일은 전송되지 않는다**.

운영 환경에서 사용자가 실제 이메일을 받으려면 SMTP 어댑터가 필요하다. 본 문서는 그 도입 설계를 정의한다.

## 2. 목적

- 마감 알림 / 맞춤 추천 알림이 **실제 사용자 메일함에 도착**하도록 한다.
- 시크릿(SMTP 비밀번호·API key)은 **환경변수 / 외부 시크릿 저장소**로만 주입한다.
- dev/test 환경에서는 **실제 발송이 일어나지 않도록** 격리한다.
- 발송 실패가 알림 스케줄러 전체를 막지 않도록 한다 (현재 구조 유지).

## 3. 비범위 (이번 작업 제외)

- 메일 발송 큐 / 비동기 워커 (현재는 `@Transactional` 안에서 동기 호출).
- Bounce / Spam complaint webhook 처리.
- Click / Open tracking.
- HTML 템플릿 시스템 도입 (Thymeleaf 등) — 일단 단순 plain text + 최소 HTML.
- 다국어 / 다언어 템플릿.
- Unsubscribe 토큰 시스템 (현재는 마이페이지 알림 설정 링크로 충분).

## 4. 결정해야 할 항목 (브레인스토밍 대상)

설계 시작 시 사용자와 합의해야 할 결정 포인트:

### 4.1 SMTP Provider 선택

| 옵션 | 장점 | 단점 |
|------|------|------|
| **Gmail SMTP + App Password** | 무료, 즉시 사용 가능, 사용자 본인 계정으로 발송 | 일일 발송 한도(500건/일), 비즈니스용 부적합, App Password 필요 |
| **AWS SES** | 저렴(약 $0.10/1000건), 대량 발송 안정, 프로덕션급 | AWS 계정 + sandbox out 절차, region 설정 필요 |
| **SendGrid / Mailgun / Resend** | API 기반, dashboard, 프로덕션 친화 | 무료 한도 제한, 외부 의존 추가 |
| **자체 메일서버** | 완전 제어 | 운영 부담 큼, IP reputation 관리 필요 |

**권장 시나리오**:
- v0 — Gmail SMTP (개발/테스트, 발송량 매우 적음)
- v1 — AWS SES (프로덕션, 발송량 늘어나면)

### 4.2 Provider 추상화 수준

- **A. 단일 구현체 (`SmtpEmailSender`)** — `JavaMailSender` 기반으로 SMTP 어떤 provider든 동일 코드로 동작. application.yml 설정만 바꿔서 Gmail ↔ SES 등 전환.
- **B. Provider별 구현체** (`GmailEmailSender`, `SesEmailSender`) — provider별 SDK 사용, 더 풍부한 기능 (SES Bounce webhook 등). 코드 늘어남.

(추천: **A**. v0~v1 모두 SMTP로 충분. AWS SES도 SMTP 인터페이스 제공.)

### 4.3 LoggingEmailSender와의 분기

- **A. Spring Profile** — `dev`/`test` 프로필에서는 `LoggingEmailSender`, `prod`에서는 `SmtpEmailSender`. `@Profile` 어노테이션으로 분기.
- **B. `@ConditionalOnProperty`** — `youthfit.email.mode=smtp|logging` 같은 명시적 설정으로 분기. 프로필과 독립.
- **C. `@Primary` + 조건** — `SmtpEmailSender`를 `@Primary`로 두고, 환경변수가 비어있으면 LoggingEmailSender로 fallback.

(추천: **A 또는 B**. 명시성과 운영 직관성이 좋음. `application-{profile}.yml`로 설정 분리하는 기존 패턴과 일관.)

### 4.4 즉시 트리거(admin) 엔드포인트 필요 여부

- 운영 중 "지금 즉시 발송 테스트"를 위한 `POST /api/v1/admin/notifications/dispatch-deadlines` / `dispatch-recommendations` 엔드포인트.
- 인증: `@PreAuthorize("hasRole('ADMIN')")` — 관리자 권한 필요. 기존 인증 체계에 ADMIN 롤이 있는지 확인 필요.
- v0 한정 / 운영 메뉴얼화 / 또는 영구 운영 도구화 — 결정 필요.

(추천: **포함**. 운영 디버깅 / 신규 사용자 테스트 / cron 발화 외 즉시 검증 등 가치 큼.)

### 4.5 발송 실패 시 정책

- **A. 실패하면 로그만 남기고 다음 사용자 진행** — 현재 `RecommendationDispatchService`의 사용자 단위 try/catch 패턴 유지. 메일 한 통 실패해도 다른 사용자에게 영향 X.
- **B. 재시도 큐 도입** — Spring Retry 또는 별도 retry 테이블. 복잡도 증가.

(추천: **A**. v0에서 충분. 실패율이 운영 모니터링에서 임계값 넘으면 그때 B로.)

### 4.6 메일 템플릿 형식

- **A. plain text 단일** — 가장 단순. 마감 알림, 추천 알림 모두 텍스트.
- **B. plain text + 간단한 HTML** — `MimeMessage`로 두 파트 모두 보내고 클라이언트가 선택. HTML은 정책명 굵게 + 링크 정도.
- **C. Thymeleaf 템플릿** — 별도 템플릿 파일, 변수 치환. 가장 풍부.

(추천: **B**. 메일 클라이언트의 fallback 보장 + 최소 가독성. Thymeleaf는 v1로.)

### 4.7 발송 메타데이터 / 헤더

- `From`: `noreply@youthfit.kr` 또는 환경변수 (`MAIL_FROM`)
- `Reply-To`: 비워두거나 운영 이메일
- `X-Mailer`: `YouthFit/1.0` 같은 식별자

(결정 필요: 도메인 / 이메일 주소.)

## 5. 작업 단계 (잠정)

브레인스토밍 결과 따라 조정.

### 단계 1 — Spring Mail 의존성 + 환경변수 / 설정

- `build.gradle`에 `org.springframework.boot:spring-boot-starter-mail` 추가.
- `application.yml`에 `spring.mail.*` 설정 (host/port/username/password from env). 비밀값은 환경변수 placeholder.
- `application-dev.yml` / `application-prod.yml` 분기 (선택지 4.3 결정 따라).
- `.env.example`에 SMTP 환경변수 키 추가.

### 단계 2 — `SmtpEmailSender` 구현체

- `backend/src/main/java/com/youthfit/user/infrastructure/email/SmtpEmailSender.java`
- `JavaMailSender` 주입, `sendDeadlineNotification` / `sendRecommendationNotification` 구현.
- 메일 본문 builder (plain text + 간단 HTML).
- 단위 테스트: `JavaMailSender` mock, 호출 인자 검증.

### 단계 3 — 분기 구성

- `LoggingEmailSender`에 `@Profile("!prod")` 또는 `@ConditionalOnProperty`.
- `SmtpEmailSender`에 반대 조건.
- 어떤 프로필에서 어떤 빈이 활성화되는지 통합 테스트로 검증.

### 단계 4 — 즉시 트리거 admin 엔드포인트

- `backend/src/main/java/com/youthfit/user/presentation/controller/AdminNotificationController.java` (+ Api 인터페이스)
- `POST /api/v1/admin/notifications/dispatch-deadlines`
- `POST /api/v1/admin/notifications/dispatch-recommendations`
- `@PreAuthorize("hasRole('ADMIN')")` (또는 동등 인가).
- Body 없음, 즉시 동기 발송 트리거. 응답: 발송 시도 사용자 수 + 실패 수 (옵션).

### 단계 5 — 메일 템플릿 / 본문

- 마감 알림: 정책명 + 마감일 + YouthFit 상세 링크 + 공식 신청 채널 링크 + 알림 설정 변경 안내.
- 추천 알림: 5건 정책 리스트 + 각 정책 카테고리·요약·마감일·상세 링크 + 추천 해제 안내.
- HTML 마크업 최소: `<p>`, `<a>`, `<ul>` / `<li>` 정도.

### 단계 6 — OPS 문서

- `docs/OPS.md`에 SMTP 환경변수 / Gmail App Password 발급 가이드 / SES 전환 시 절차 추가.

### 단계 7 — 검증

- 로컬: dev 프로필에서 LoggingEmailSender 그대로 동작.
- 스테이징/프로덕션: 본인 메일로 수동 테스트.
- admin 엔드포인트로 즉시 트리거 후 실제 inbox 도착 확인.

## 6. 시크릿 관리

- 절대 코드/`application.yml`에 평문 시크릿 커밋 금지.
- 환경변수 → `.env` (로컬, gitignore) → 운영 환경에서는 secret manager (예: GitHub Actions secrets, AWS Parameter Store 등).
- `application.yml`에는 placeholder만:
  ```yaml
  spring:
    mail:
      host: ${MAIL_HOST}
      port: ${MAIL_PORT:587}
      username: ${MAIL_USERNAME}
      password: ${MAIL_PASSWORD}
  ```

## 7. 보안·운영 고려

- App Password 노출 시 즉시 폐기 후 재발급 절차.
- 발송량 모니터링: 일일 발송 수 / 실패 수 / 도달 시간 측정.
- Provider 일일 한도 초과 시 graceful degradation (다음 cron으로 미루기보다는 LoggingEmailSender fallback도 옵션).
- `From` 도메인의 SPF / DKIM 설정 (도메인 운영 시).

## 8. 의존 / 후속 작업

- **선행**: Phase 1~3 알림 추천 PR 머지 완료.
- **후속**:
  - 발송 모니터링 / 실패 알림 (별도 spec).
  - HTML 템플릿 시스템 (Thymeleaf 등, v1).
  - Bounce / Complaint webhook 처리 (v1+).

## 9. 다음 단계

이 문서는 **spec draft**다. 실제 구현 전에 brainstorming 세션을 통해 4번 결정 항목들을 확정하고, plan 문서로 변환한 뒤 작업한다.

명령 흐름 예:
```
/brainstorm SMTP 어댑터 도입 — 위 spec의 결정 항목들 합의
/write-plan
/subagent-driven-execute (또는 /executing-plans)
```
