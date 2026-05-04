# 이메일 발송 인프라 도입 (AWS SES + Thymeleaf + 상태 전이 멱등성)

- **상태**: TODO
- **작성일**: 2026-05-05
- **대상 모듈**: `backend/user` (notification 하위)
- **선행 작업**:
  - `DONE_2026-05-04-notification-recommendation` (알림 흐름·스케줄러·디스패처)
  - PRD `docs/prd/07-notification.md` (F-09)
- **연관 모듈**: `policy` (발송 본문에 정책 데이터 사용)

## 1. 배경

`EmailSender` 포트와 `LoggingEmailSender` stub 구현체는 이미 도입되어 있으나, 실제 메일은 발송되지 않는다 (콘솔 로그만 출력). 마감일 알림 / 주간 추천 알림 두 흐름이 호출 지점까지 완성되어 있어, 발송 어댑터를 추가하면 F-09 의 사용자 노출 부분이 완결된다.

이번 사이클은 다음을 한 번에 처리한다.

1. **AWS SES 어댑터** 추가 — 운영 단계의 실제 발송 경로
2. **Thymeleaf 기반 HTML 본문 렌더링** — 디자이너/뷰가 분리된 템플릿
3. **`property toggle` 기반 환경 분기** — `attachment.storage.type` 패턴 재사용
4. **상태 전이(PENDING/SENT/FAILED) 멱등성** — partial failure 방어를 위한 `NotificationHistory` 스키마 확장
5. **트랜잭션 패턴 정리** — 메서드 레벨 `@Transactional` 제거, `REQUIRES_NEW` 분리

PRD 07 의 "이메일 발송 인프라 (AWS SES 등)" 의존이 이 사이클로 충족된다.

## 2. 결정 로그

| # | 결정 포인트 | 선택 | 대안 | 비고 |
|---|---|---|---|---|
| 1 | 게이트웨이 | **AWS SES (SesV2Client)** | SMTP via JavaMailSender / Resend / SendGrid | AWS SDK BOM 이미 적재(S3 용도). 의존성 1줄 추가만. v0 볼륨에서 비용 사실상 0 |
| 2 | 템플릿 형식 | **Thymeleaf HTML + plain text fallback** | String.format / FreeMarker / MJML | `templates/` 디렉토리 비어 있음 → 자리 잡기 좋음. multipart로 모든 클라이언트 호환 |
| 3 | 환경 분기 방식 | **property toggle** (`youthfit.email.transport: logging\|ses`) | Spring Profile / SMTP catcher 추가 | 기존 `attachment.storage.type` 패턴과 일관. 같은 profile 내에서도 환경변수로 즉시 전환 |
| 4 | 실패 처리 | **어댑터 catch → `EmailSendException` → 호출자 catch + `markFailed`** | throw → 트랜잭션 롤백 / Spring Retry / DLQ | 한 사용자 실패가 batch 전체 차단 안 함. 영구 실패는 로그·DB row 로 가시화 |
| 5 | 발신 도메인 | **단일 이메일 검증** (개인 Gmail) → 도메인 확보 후 A 로 마이그레이션 | 도메인 검증 (예: noreply@youthfit.app) | 도메인 미보유. 마이그레이션 비용은 환경변수 변경뿐 |
| 6 | Unsubscribe | **풋터에 설정 페이지 링크만** | one-click 토큰 / List-Unsubscribe 헤더 | v0 발송량 적음. 사용자가 카카오 로그인 후 토글 OFF 흐름. 토큰 도입은 sandbox 해제 사이클에서 |
| 7 | 코드 구조 | **렌더러 분리** (`NotificationEmailRenderer`) — 어댑터는 transport 만 | 어댑터 안 직접 렌더 / `EmailSender.send(EmailMessage)` 추상화 | 인터페이스 변경 0 → 호출자 영향 최소. 렌더 결과 단위 테스트 용이. dev 모드에서도 렌더된 HTML 로그 가능 |
| 8 | 멱등성 패턴 | **상태 전이 (PENDING/SENT/FAILED)** | UNIQUE 제약 자연 멱등 / 별도 `email_dispatch_log` 테이블 | partial failure(JVM crash, DB 단절) 시 중복 발송 차단. 스키마 확장만으로 도입 |
| 8a | FAILED 재시도 | **재시도 안 함** (수동 reset) | 다음 cron 자동 재시도 | 영구 실패(검증 안 된 발신자, 잘못된 주소)에 대한 SES 호출 누적 차단. 운영 데이터 보고 자동화 후속 |
| 8b | stale PENDING 처리 | **수동 운영** (24h 이상 PENDING SQL 정리) | 별도 cron 자동 cleanup | 발생 빈도 매우 낮음 (JVM crash 등). 빈도 보고 자동화 후속 |

## 3. 변경 범위

### 3.1 빌드 의존성 (`backend/build.gradle`)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
implementation 'software.amazon.awssdk:sesv2'
```

AWS BOM 이미 적재 → 버전 명시 불필요.

### 3.2 환경변수 / 설정 (`application.yml`)

```yaml
youthfit:
  email:
    transport: ${EMAIL_TRANSPORT:logging}        # logging | ses
    from:
      address: ${MAIL_FROM_ADDRESS:}              # SES 검증된 발신 주소
      name:    ${MAIL_FROM_NAME:YouthFit}         # 표시명
    base-url: ${MAIL_BASE_URL:http://localhost:5173}
    ses:
      region:            ${AWS_SES_REGION:ap-northeast-2}
      access-key-id:     ${AWS_SES_ACCESS_KEY_ID:}
      secret-access-key: ${AWS_SES_SECRET_ACCESS_KEY:}
```

profile override:
- `local`: `transport: logging` (기본)
- `prod`: `transport: ses` (기본)

### 3.3 신규 클래스

| 경로 | 역할 |
|---|---|
| `user/application/dto/result/EmailContent.java` | record `{ subject, htmlBody, textBody }` |
| `user/domain/exception/EmailSendException.java` | `RuntimeException` 상속, cause 보존. 위치는 `AttachmentNotFoundException` 컨벤션과 일관 (`{module}/domain/exception/`). cause 는 JDK `Throwable` 타입만 노출 → 도메인이 SDK 의존 갖지 않음 |
| `user/application/service/NotificationEmailRenderer.java` | Thymeleaf 호출 → `EmailContent` 생성 |
| `user/application/service/NotificationDispatchService.java` | `reservePending` / `markSent` / `markFailed` (REQUIRES_NEW) |
| `user/domain/model/NotificationStatus.java` | enum `PENDING / SENT / FAILED` |
| `user/infrastructure/email/SesEmailSender.java` | `EmailSender` 구현 (ses 모드) |
| `user/infrastructure/email/SesEmailConfig.java` | `SesV2Client` Bean (`@ConditionalOnProperty`) |

### 3.4 신규 템플릿 / SQL

```
backend/src/main/resources/
├── templates/email/
│   ├── deadline.html
│   ├── deadline.txt
│   ├── recommendation.html
│   └── recommendation.txt
└── sql/2026-05-05-notification-history-status.sql
```

### 3.5 수정 파일

| 경로 | 변경 |
|---|---|
| `user/application/port/EmailSender.java` | **변경 없음** (시그니처 유지) |
| `user/domain/model/NotificationHistory.java` | `status`, `createdAt`, `failedAt`, `failureReason` 필드 + `pending(...)`, `markSent(...)`, `markFailed(...)` 도메인 메서드. setter 금지 규칙 유지 |
| `user/application/service/NotificationScheduleService.java` | 메서드 레벨 `@Transactional` 제거. `reservePending → SES → markSent/Failed` 흐름 + 사용자 단위 try/catch |
| `user/application/service/RecommendationOneDispatcher.java` | 동일 패턴 적용 |
| `user/infrastructure/email/LoggingEmailSender.java` | 렌더러 주입, 렌더된 HTML 첫 N자 로그 |
| `application.yml` | `youthfit.email.*` 키 + profile override |
| `docs/OPS.md` | 환경변수 슬롯, SES 운영 절차 추가 |
| `docs/prd/07-notification.md` | 구현 상태 갱신 |

## 4. 데이터 모델 변경

### 4.1 `NotificationHistory` 스키마 확장

| 필드 | 타입 | 변경 | 설명 |
|---|---|---|---|
| id | Long | (기존) | PK |
| userId | Long | (기존) | FK |
| policyId | Long | (기존) | FK |
| notificationType | enum | (기존) | DEADLINE / RECOMMENDATION |
| **status** | enum | **신규** | PENDING / SENT / FAILED |
| **createdAt** | LocalDateTime | **신규, NOT NULL** | PENDING 행 생성 시각 |
| sentAt | LocalDateTime | **NULLable 변경** | SENT 시점에 채움 |
| **failedAt** | LocalDateTime | **신규, NULLable** | FAILED 시점 |
| **failureReason** | VARCHAR(500) | **신규, NULLable** | SES 예외 메시지 (truncate) |

UNIQUE 제약: `(userId, policyId, notificationType)` — 유지.

### 4.2 마이그레이션 SQL

`backend/src/main/resources/sql/2026-05-05-notification-history-status.sql`:

```sql
ALTER TABLE notification_history ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'SENT';
ALTER TABLE notification_history ADD COLUMN created_at TIMESTAMP;
ALTER TABLE notification_history ADD COLUMN failed_at TIMESTAMP;
ALTER TABLE notification_history ADD COLUMN failure_reason VARCHAR(500);
ALTER TABLE notification_history ALTER COLUMN sent_at DROP NOT NULL;
UPDATE notification_history SET created_at = sent_at WHERE created_at IS NULL;
ALTER TABLE notification_history ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notification_history ALTER COLUMN status DROP DEFAULT;
```

기존 행은 `status='SENT'` 로 백필 (이미 발송 완료로 간주). 마지막 `DROP DEFAULT` 는 신규 행이 status 누락 시 자동 SENT 로 채워지는 것을 막아 코드 버그를 빠르게 드러나게 함. Flyway 미사용 — OPS.md 의 Q&A 캐시 패턴과 동일하게 운영 PG 에 수동 적용.

## 5. 발송 흐름

### 5.1 마감일 알림 (일일 09:00 KST)

```
NotificationScheduleService.sendDeadlineNotifications()
  for each NotificationSetting (emailEnabled=true):
    User user = userRepository.findById(userId)
    if (user == null || email.isBlank()) continue

    for each PolicyNotificationSubscription:
      Policy policy = ...
      if (!shouldNotify(policy, daysBefore, today)) continue

      try:
        NotificationHistory history = dispatchService.reservePending(   # REQUIRES_NEW
            userId, policyId, DEADLINE)
        if (history == null) continue                                    # 이미 처리 중/완료/실패

        try:
          emailSender.sendDeadlineNotification(email, policy)            # 트랜잭션 밖
          dispatchService.markSent(history.id)                           # REQUIRES_NEW
        except EmailSendException e:
          dispatchService.markFailed(history.id, e.message)              # REQUIRES_NEW
      except Exception e:
        log.error("마감일 알림 처리 실패 userId=... policyId=...", e)
```

### 5.2 주간 추천 알림 (월요일 09:00 KST)

```
RecommendationDispatchService → RecommendationOneDispatcher.dispatchOne(setting)
  ... 기존 적합도 판정/picks 추출 (변경 없음) ...

  if (picks.isEmpty()) return

  for each pick:
    history = dispatchService.reservePending(userId, pickId, RECOMMENDATION)
    if (history == null) continue
    histories.put(pickId, history)

  if (histories.isEmpty()) return                                        # 모두 이미 처리됨

  try:
    emailSender.sendRecommendationNotification(email, histories.keys())  # 한 번의 발송
    histories.values().forEach(h -> dispatchService.markSent(h.id))
  except EmailSendException e:
    histories.values().forEach(h -> dispatchService.markFailed(h.id, e.message))
```

추천은 한 번의 메일에 정책 N개를 묶어 발송하므로, 발송 전에 N개의 PENDING 행을 모두 예약하고 발송 결과에 따라 일괄 SENT/FAILED.

### 5.3 어댑터 분기

```java
@Component
@ConditionalOnProperty(name="youthfit.email.transport", havingValue="logging", matchIfMissing=true)
public class LoggingEmailSender implements EmailSender { ... }

@Component
@ConditionalOnProperty(name="youthfit.email.transport", havingValue="ses")
public class SesEmailSender implements EmailSender { ... }
```

`matchIfMissing=true` → 환경변수 누락 시 logging 로 안전 fallback.

### 5.4 SES 어댑터 내부

```java
EmailContent content = renderer.renderDeadline(policy);
try {
    sesClient.sendEmail(SendEmailRequest.builder()
        .fromEmailAddress(formatFrom(fromAddress, fromName))
        .destination(Destination.builder().toAddresses(recipientEmail).build())
        .content(EmailContent.builder()
            .simple(Message.builder()
                .subject(Content.builder().data(content.subject()).charset("UTF-8").build())
                .body(Body.builder()
                    .html(Content.builder().data(content.htmlBody()).charset("UTF-8").build())
                    .text(Content.builder().data(content.textBody()).charset("UTF-8").build())
                    .build())
                .build())
            .build())
        .build());
} catch (SesV2Exception | SdkException e) {
    log.error("SES 발송 실패 to={} type={}", recipientEmail, type, e);
    throw new EmailSendException("SES 발송 실패: " + recipientEmail, e);
}
```

## 6. 트랜잭션 경계

| 작업 | 트랜잭션 |
|---|---|
| 활성 사용자 조회 | 읽기 (트랜잭션 짧게) |
| 후보 정책 추출 | 트랜잭션 밖 |
| `reservePending` (PENDING INSERT) | **REQUIRES_NEW** |
| SES 발송 | **트랜잭션 밖** |
| `markSent` / `markFailed` | **REQUIRES_NEW** |

→ `NotificationScheduleService`, `RecommendationOneDispatcher` 의 메서드 레벨 `@Transactional` 제거. SES 외부 IO 동안 DB 커넥션 점유 방지.

## 7. 도메인 모델 — `NotificationHistory`

```java
public class NotificationHistory {
    // ... 기존 필드 ...
    private NotificationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;       // NULLable
    private LocalDateTime failedAt;     // NULLable
    private String failureReason;       // NULLable, max 500

    public static NotificationHistory pending(Long userId, Long policyId, NotificationType type) {
        // status=PENDING, createdAt=now
    }

    public void markSent(LocalDateTime now) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 SENT 로 전이 가능");
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
    }

    public void markFailed(LocalDateTime now, String reason) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 FAILED 로 전이 가능");
        }
        this.status = NotificationStatus.FAILED;
        this.failedAt = now;
        this.failureReason = reason;
    }
}
```

setter 금지. 상태 전이는 도메인 메서드만으로.

## 8. 예외 처리

```
RuntimeException
   └── EmailSendException
            ├── 메시지: "SES 발송 실패: {recipientEmail}"
            └── cause: SesV2Exception 또는 SdkException
```

- `LoggingEmailSender` 는 예외 던지지 않음 (dev 노이즈 방지)
- `SesEmailSender` 는 SES SDK 예외를 모두 `EmailSendException` 으로 변환
- 호출자(NotificationScheduleService, RecommendationOneDispatcher) 가 catch → `markFailed` → 다음 사용자/정책 진행

## 9. 테스트 전략

### 9.1 단위 테스트

| 클래스 | 검증 |
|---|---|
| `NotificationEmailRendererTest` | subject/htmlBody/textBody 변수 바인딩, 추천 알림 정책 N개 반복, baseUrl 링크 주입 |
| `SesEmailSenderTest` | `SendEmailRequest` 캡처(from/to/subject/html/text 정확성), SDK 예외 → `EmailSendException` 변환 |
| `LoggingEmailSenderTest` | 렌더러 호출 후 로그 출력, 예외 미발생 |
| `NotificationHistoryTest` | `pending` / `markSent` / `markFailed` 상태 전이 규칙 (SENT/FAILED 에서 추가 전이 차단) |
| `NotificationScheduleServiceTest` | reservePending null → skip, EmailSendException → markFailed, 정상 → markSent |
| `RecommendationOneDispatcherTest` | picks 일괄 PENDING 예약 → 일괄 SENT/FAILED |

### 9.2 통합 테스트

| 클래스 | 검증 |
|---|---|
| `NotificationDispatchServiceIntegrationTest` | (1) `reservePending` REQUIRES_NEW 가 별도 트랜잭션 commit, (2) UNIQUE 충돌 시 null, (3) `markSent` / `markFailed` 가 행 갱신 |

### 9.3 슬라이스 테스트

| 클래스 | 검증 |
|---|---|
| `NotificationHistoryRepositoryTest` | (보강) `existsByUserIdAndPolicyIdAndNotificationType` 가 status 무관하게 PENDING/SENT/FAILED 모두 포함하여 true |

### 9.4 자동화 안 함

- 실제 SES 호출: 운영 런북에 dry-run 절차 명시 (수동)
- HTML 전체 스냅샷: brittle — 핵심 substring 만 검증

### 9.5 커버리지 목표

- 신규 클래스: 라인 90% 이상
- 변경된 호출자: try/catch 분기 모두 커버

## 10. 위험

| 위험 | 영향도 | 완화 |
|---|---|---|
| SES sandbox 모드 → 검증 안 된 수신자 발송 실패 | 🟠 중 | 운영 런북에 검증 절차. 베타 전까지 sandbox 유지 가능 |
| `MAIL_FROM_ADDRESS` 미검증 → SES 모든 호출 거부 | 🔴 고 | 환경변수 채워졌는지만 검증 (검증 자체는 SES 콘솔 절차) |
| JVM crash 후 PENDING 영구 잔존 | 🟡 저 | 결정 8b: 수동 SQL 정리. 빈도 보고 자동화 후속 |
| FAILED 누적 → 사용자 알림 영구 소실 | 🟡 저 | 결정 8a: 수동 reset. 운영 런북에 SQL 명시 |
| 트랜잭션 분리 회귀 — 기존 동작 깨짐 | 🟠 중 | 기존 + 신규 통합 테스트로 REQUIRES_NEW 동작 검증 |
| Thymeleaf 자동 설정 부작용 (MVC ViewResolver) | 🟢 저 | 백엔드는 SSR 안 함 → controller 응답에 영향 없음. 실측 확인 |
| 이메일 본문 스팸 처리 | 🟠 중 | sandbox 단계 무관. DKIM/SPF 셋업은 후속 (도메인 확보 후) |
| AWS 자격 증명 유출 | 🔴 고 | `.env` 만 사용, `.gitignore` 확인. IAM 사용자에 `ses:SendEmail` 만 부여 |

## 11. 비범위

- **List-Unsubscribe 헤더 / one-click unsubscribe**: 결정 6 — 풋터 링크만. 도메인 확보 + sandbox 해제 사이클에서 함께 도입
- **HTML 이메일 디자인 시스템**: v0 단순 (제목/본문/CTA/풋터)
- **`SendBulkEmail`**: 사용자 단위 멱등성 + 개별 본문 (정책 데이터 다름) → 단일 발송이 적절
- **메트릭 / 대시보드**: log.error 카운트만. Actuator/Prometheus 노출은 후속
- **stale PENDING 자동 cleanup cron**: 결정 8b
- **FAILED 자동 재시도**: 결정 8a
- **Spring Retry / DLQ**: cron 자체가 자연 재시도. 추가 인프라 불필요
- **Resend / SendGrid / SMTP**: 결정 1
- **SMTP catcher (Mailpit) for local dev**: 결정 3 — `EMAIL_TRANSPORT=logging` 으로 충분
- **이메일 발송 이력 사용자 노출 UI**: 백엔드 데이터는 있으나 frontend 노출 없음. 후속 PRD 항목

## 12. 후속 작업 (backlog)

이번 사이클 종료 후 `next-steps` 또는 별도 spec 으로 이관:

1. **SES sandbox 해제 + 도메인 확보**: 운영급 발송 전 필수 (DNS 작업 포함)
2. **List-Unsubscribe + one-click unsubscribe**: sandbox 해제와 함께 도입
3. **stale PENDING 정리 cron**: 운영 데이터 보고 빈도 결정
4. **FAILED 자동 재시도**: 동일
5. **메트릭 노출**: SES 호출 카운트, 실패 카운트, 평균 latency
6. **이메일 본문 다국어**: 현재 한국어 only

## 13. 운영 절차 (배포 노트)

운영 런북: `docs/superpowers/operations/2026-05-05-email-transport-runbook.md` (이번 사이클에 함께 작성)

요약:

1. AWS 콘솔에서 IAM 사용자 `youthfit-ses-sender` 생성 — 정책 `ses:SendEmail`, `ses:SendRawEmail` 만 허용
2. SES 콘솔에서 `MAIL_FROM_ADDRESS` 와 (sandbox 모드 시) 모든 수신자 이메일 검증
3. `.env` 슬롯 채우기 (커밋 금지 확인)
4. 운영 PG 에 `2026-05-05-notification-history-status.sql` 적용
5. 백엔드 재배포
6. dry-run: `EMAIL_TRANSPORT=logging` 으로 한 번 띄워서 렌더된 HTML 로그 확인
7. `EMAIL_TRANSPORT=ses` 로 전환 → 검증된 수신자 1명에게 dry-run 발송

## 14. 참고

- PRD: `docs/prd/07-notification.md`
- 선행 사이클: `DONE_2026-05-04-notification-recommendation-design.md`, `DONE_2026-05-04-notification-recommendation.md`
- AWS SDK: `software.amazon.awssdk:sesv2` (BOM 2.28.16)
- Thymeleaf: `spring-boot-starter-thymeleaf` (Boot 4.0.5 동봉)
