# 어드민 — Spec 2: 이메일 발송 추적 설계

> **상태**: Design 확정 (구현 plan 작성 직전)
> **작성일**: 2026-05-05
> **시리즈**: 어드민 시리즈 5개 중 #2
> **선행**: Spec 1 (admin foundation, 완료)

---

## 1. 목표

어드민이 다음을 운영적으로 추적할 수 있게 한다.
- **건별**: 누구에게, 언제, 어떤 이메일이 발송됐고 결과가 무엇인지 (송신 시점 + SES 전달 결과까지)
- **집계**: 일자별 발송/성공/실패/바운스 수치, 추세
- **재시도**: 실패한 발송을 어드민이 직접 재발송

알림은 v0의 핵심 기능(F-09)이라 운영상 critical. 발송 실패와 바운스를 빠르게 식별·재처리할 수 있어야 한다.

## 2. 범위

### In
- `EmailSendAttempt` 엔티티 신설 (시도 단위, NotificationHistory 와 1:N)
- `EmailDispatcher` application 서비스 신설 — send + 적재 + NotificationHistory 상태 전이의 단일 지점
- `EmailSender` 포트 시그니처 변경 — `void → EmailSendResult` (messageId 반환)
- SES Configuration Set + SNS Topic + 백엔드 webhook (`/api/internal/notifications/ses-event`) — DELIVERED/BOUNCED/COMPLAINED 추적
- 어드민 화면: 메인(KPI + 일자별 차트 + 건별 테이블) / 상세(메타 + 입력 데이터 + 본문 미리보기 + 재발송) / 라우트 2개
- 차트 라이브러리 도입 (`recharts`) — Spec 3/4/5 baseline
- 보관 정책 cron (90일)

### Out
- A/B 테스트, 발송 캠페인 UI
- 사용자별 구독/수신거부 화면 (일반 사용자 영역)
- 외부로의 이메일 export
- Suppression(차단 주소) 직접 관리 — AWS SES 콘솔 위임
- LocalStack 등으로 SES/SNS 통합 자동화 테스트 (수동 검증)

## 3. 핵심 설계 결정 (브레인스토밍 결과)

| 결정 | 채택 | 근거 |
|---|---|---|
| 데이터 모델 | NotificationHistory + 신규 `EmailSendAttempt` (1:N, FK) | 시도별 row 보존, NotificationHistory 의 멱등성 책임은 그대로, SES 이벤트 매핑 자연스러움 |
| SES 이벤트 통합 | SNS webhook (DELIVERED/BOUNCED/COMPLAINED 추적) | 어드민에서 바운스 사유 즉시 확인 가능, SES 콘솔 의존 제거 |
| 발송 흐름 책임 | `EmailDispatcher` application 서비스 신설 | 호출자 일관성, EmailSender 포트는 SES 추상화 단일 책임 유지 |
| 본문 보관 | 미저장. `subject` + `input_payload(JSON)` 만 보관, 본문은 `NotificationEmailRenderer` 재호출로 미리보기 | DB 부담 최소, PII 표면 최소, 본문 결정론적 재현 가능 |
| 재발송 | FAILED 상태만 어드민 버튼 → 같은 dispatcher 재호출 → 새 attempt row 생성 | 시도별 row 모델과 정합, 이력 보존 |
| Suppression | AWS SES 콘솔 위임, 어드민에선 카운트만 | v0 운영 부담 최소화 |
| 모듈 위치 | `user` 모듈 내부 유지 | 알림 코드가 user 안에 있어 자연스러움, 분리 동인 없음 |
| webhook 인증 | SNS 메시지 서명 검증 (X.509) | AWS 표준, SubscriptionConfirmation 자동 처리 |
| 차트 라이브러리 | `recharts` | React 친화, 디자인 토큰 적용 용이, Spec 3/4/5 재사용 |
| 어드민 컨트롤러 위치 | `admin` 모듈 (`AdminEmailLogController`) | DDD 경계: admin = ReadModel, 데이터 적재는 user 도메인 |

## 4. 아키텍처

### 4.1 발송 흐름 (변경 후)

```
NotificationScheduleService ──> EmailDispatcher.dispatchDeadline()
RecommendationOneDispatcher ──> EmailDispatcher.dispatchRecommendation()
                                  │
                                  ├─> NotificationEmailRenderer (기존)
                                  ├─> EmailSender.send (포트, EmailSendResult 반환)
                                  ├─> EmailSendAttemptRepository.save
                                  └─> NotificationHistory.markSent / markFailed
```

### 4.2 SES 이벤트 흐름

```
SES → SNS Topic → POST /api/internal/notifications/ses-event
                  → SnsMessageVerifier (X.509 서명 검증)
                  → SesEventListener
                  → EmailSendAttemptRepository.findBySesMessageId(...)
                  → EmailSendAttempt.markDelivered/Bounced/Complained
```

### 4.3 어드민 조회 흐름

```
프론트(/admin/email) 
  → GET /api/v1/admin/email-attempts (필터/페이징)
  → AdminEmailLogController
  → EmailSendAttemptQueryService (user 모듈, read-only)
  → EmailSendAttemptRepository
```

## 5. 데이터 모델

### 5.1 `EmailSendAttempt` (신규)

```java
// user/domain/model/EmailSendAttempt.java
@Entity
@Table(name = "email_send_attempt", indexes = {
    @Index(name = "idx_email_attempt_message_id", columnList = "ses_message_id"),
    @Index(name = "idx_email_attempt_history_id", columnList = "notification_history_id"),
    @Index(name = "idx_email_attempt_sent_at", columnList = "sent_at"),
    @Index(name = "idx_email_attempt_status", columnList = "status")
})
public class EmailSendAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_history_id")  // nullable — 비알림 이메일 대비
    private Long notificationHistoryId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "recipient_user_id")  // nullable — 비로그인 대상 대비
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 30)
    private NotificationType emailType;  // 기존 NotificationType 재사용

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    private String inputPayloadJson;

    @Column(name = "ses_message_id", length = 255)  // FAILED 케이스는 null
    private String sesMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailSendStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "bounce_type", length = 50)  // Permanent / Transient
    private String bounceType;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static EmailSendAttempt success(Long historyId, User user, NotificationType type,
                                           String sesMessageId, String subject,
                                           String inputPayload, LocalDateTime now) { ... }
    public static EmailSendAttempt failure(Long historyId, User user, NotificationType type,
                                           String subject, String inputPayload,
                                           String errorCode, String errorMessage,
                                           LocalDateTime now) { ... }

    public void markDelivered(LocalDateTime now) {
        // SENT 에서만 전이 허용
    }
    public void markBounced(LocalDateTime now, String type, String reason) {
        // SENT 에서만 전이 허용
    }
    public void markComplained(LocalDateTime now) {
        // SENT/DELIVERED 에서 전이 허용
    }
}
```

### 5.2 `EmailSendStatus` (신규 enum)

```java
public enum EmailSendStatus {
    SENT,         // SES API 호출 성공 (초기 상태)
    DELIVERED,    // SES → 수신 SMTP 성공 (webhook)
    BOUNCED,      // 반송 (webhook)
    COMPLAINED,   // 스팸 신고 (webhook)
    FAILED        // SES API 호출 자체 실패
}
```

상태 전이 규칙:
- `SENT → DELIVERED | BOUNCED` (정상 흐름)
- `SENT | DELIVERED → COMPLAINED` (스팸 신고는 전달 후에도 가능)
- `FAILED` 는 종착 상태 (재발송 시 새 row 생성)

### 5.3 NotificationHistory 와의 관계

- `EmailSendAttempt.notification_history_id` 는 application-level FK (DB 제약 X)
- 1 NotificationHistory : N EmailSendAttempt (재발송 시도마다 attempt 추가)
- NotificationHistory 변경 없음 — 기존 `(user_id, policy_id, notification_type)` UNIQUE 멱등성 유지
- 재발송 시 NotificationHistory.status 가 FAILED → 다시 markSent 가능 (한 번 SENT 된 건은 재발송 불가)

## 6. 컴포넌트 설계

### 6.1 `EmailSender` 포트 시그니처 변경

```java
// user/application/port/EmailSender.java
public interface EmailSender {
    EmailSendResult sendDeadlineNotification(String recipientEmail, Policy policy);
    EmailSendResult sendRecommendationNotification(String recipientEmail, List<Policy> policies);
}

// user/application/email/EmailSendResult.java
public record EmailSendResult(String sesMessageId, String subject) { }
// 본문(html/text)은 EmailSendResult 에 담지 않음. 미리보기는 NotificationEmailRenderer 재호출.
```

- `SesEmailSender` — `SesV2Client.sendEmail()` 응답의 `messageId()` 추출 + 렌더된 subject/body 반환
- `LoggingEmailSender` — `UUID.randomUUID().toString()` 가짜 ID 반환 (테스트/dev)

### 6.2 `EmailDispatcher` (신규)

```java
// user/application/email/EmailDispatcher.java
@Service
@RequiredArgsConstructor
public class EmailDispatcher {
    private final EmailSender emailSender;
    private final NotificationEmailRenderer renderer;
    private final EmailSendAttemptRepository attemptRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void dispatchDeadline(NotificationHistory history, User user, Policy policy) {
        String inputJson = toJson(Map.of("policyId", policy.getId()));
        try {
            EmailSendResult result = emailSender.sendDeadlineNotification(user.getEmail(), policy);
            attemptRepository.save(EmailSendAttempt.success(
                history.getId(), user, NotificationType.DEADLINE,
                result.sesMessageId(), result.subject(), inputJson, clock.now()));
            dispatchService.markSent(history.getId());
        } catch (EmailSendException e) {
            attemptRepository.save(EmailSendAttempt.failure(
                history.getId(), user, NotificationType.DEADLINE,
                renderer.renderSubject(NotificationType.DEADLINE, policy), inputJson,
                errorCodeOf(e), e.getMessage(), clock.now()));
            dispatchService.markFailed(history.getId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void dispatchRecommendation(NotificationHistory history, User user, List<Policy> policies) {
        // 동일 패턴
    }

    @Transactional
    public Long redispatch(Long attemptId) {
        EmailSendAttempt original = attemptRepository.findById(attemptId).orElseThrow();
        if (original.getStatus() != EmailSendStatus.FAILED) {
            throw new IllegalStateException("FAILED 상태만 재발송 가능");
        }
        // input_payload 디시리얼라이즈 → 동일 dispatch 메서드 호출 → 새 attempt id 반환
    }
}
```

> **트랜잭션 주의**: SES API 호출이 외부 I/O. v0 에서 호출당 수백 ms 수준이라 단일 `@Transactional` 로 충분. 호출량이 늘어 트랜잭션 길이가 문제되면 attempt 저장만 `REQUIRES_NEW` 로 분리.

### 6.3 `SesEventListener` + `SnsMessageVerifier` (신규)

```java
// user/infrastructure/email/SesEventListener.java
@RestController
@RequestMapping("/api/internal/notifications")
@RequiredArgsConstructor
public class SesEventListener {
    private final SnsMessageVerifier verifier;
    private final SesEventHandler handler;

    @PostMapping("/ses-event")
    public ResponseEntity<Void> handle(@RequestBody String rawJson,
                                        @RequestHeader Map<String, String> headers) {
        SnsMessage message = SnsMessage.parse(rawJson);
        verifier.verify(message);

        switch (message.type()) {
            case SUBSCRIPTION_CONFIRMATION -> verifier.confirmSubscription(message.subscribeUrl());
            case NOTIFICATION -> handler.handle(message.payload());
            case UNSUBSCRIBE_CONFIRMATION -> log.info("Unsubscribe: {}", message.topicArn());
        }
        return ResponseEntity.ok().build();
    }
}
```

`SesEventHandler` — eventType (Delivery/Bounce/Complaint) 분기 + `attemptRepository.findBySesMessageId(...)` 매칭 후 상태 전이.

매칭 실패 케이스 (관계 없는 SES 이벤트, 또는 attempt 가 이미 cleanup 으로 삭제된 경우): WARN 로그만 남기고 200 응답 (SNS 재시도 방지).

### 6.4 어드민 API 표면

```
GET  /api/v1/admin/email-attempts                      # 페이지 + 필터 조회
     ?from=2026-05-01&to=2026-05-05
     &status=FAILED,BOUNCED                            # CSV 다중
     &emailType=DEADLINE
     &recipient=user@example.com                       # partial match
     &page=0&size=20

GET  /api/v1/admin/email-attempts/stats/daily          # 일자별 집계 (차트용)
     ?from=...&to=...
     → [{date, sent, delivered, bounced, complained, failed}, ...]

GET  /api/v1/admin/email-attempts/stats/kpi            # KPI 카드용
     → {today: {sent, failed, deliveredRate}, thisWeek: {...}, successRate}

GET  /api/v1/admin/email-attempts/{id}                 # 상세
     → 메타 + input_payload + 에러 정보

POST /api/v1/admin/email-attempts/{id}/redispatch      # 재발송 (FAILED만)
     → 201 Created + {newAttemptId}

GET  /api/v1/admin/email-attempts/{id}/preview         # 본문 미리보기 (재렌더링)
     → {subject, htmlBody, textBody}
```

DTO: `admin/presentation/dto/{request, response}/` 하위에 record 로 작성. `EmailSendAttempt` 엔티티는 admin 모듈에 노출하지 않음 (ReadModel DTO 변환).

### 6.5 보안 설정 변경

`common/config/SecurityConfig.java`:
```java
.requestMatchers("/api/internal/notifications/ses-event").permitAll()  // SNS 서명으로 인증
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")                  // 기존
```

## 7. 프론트엔드 설계

### 7.1 라우팅 (`App.tsx`)

```jsx
<Route element={<RequireAdmin />}>
  <Route path="/admin" element={<AdminLayout />}>
    <Route index element={<AdminDashboardPage />} />
    <Route path="email" element={<AdminEmailLogPage />} />
    <Route path="email/:attemptId" element={<AdminEmailDetailPage />} />
  </Route>
</Route>
```

`AdminSidebar.tsx`: "이메일 발송" 메뉴 `soon: true` 플래그 제거 + `to: "/admin/email"` 활성화.

### 7.2 차트 baseline (`components/charts/`)

Spec 3/4/5 모두 재사용할 공통 래퍼:
- `StackedBarChart.tsx` — recharts `BarChart` 래핑, X=date, Y=stacked status counts
- `KpiCard.tsx` — Spec 1 카드 토큰 재사용, 값 + 변화율 + 라벨

색상 매핑 (디자인 토큰 기반):
- `SENT` indigo
- `DELIVERED` green
- `BOUNCED` amber
- `COMPLAINED` red
- `FAILED` red-700

### 7.3 메인 화면 (`AdminEmailLogPage`)

상단부터: `EmailFilterBar` (기간 7D/30D/90D, 상태 멀티셀렉트, 타입, 수신자 검색) → `EmailKpiSection` (KPI 4카드: 오늘 발송, 성공률, 바운스율, 실패 건수) → `EmailDailyChart` (일자별 stacked bar) → `EmailAttemptTable` (페이징 테이블, 실패 건은 재발송 액션).

### 7.4 상세 화면 (`AdminEmailDetailPage`)

섹션 순: 메타(수신자/타입/제목/messageId/시각/상태 변경 시각) → 입력 데이터(JSON syntax-highlight) → 본문 미리보기(lazy, 클릭 시 `GET /preview` 호출, HTML/Text 탭) → 에러 정보(FAILED/BOUNCED 만) → 재발송 버튼(FAILED 만 활성).

### 7.5 API client / Query hooks

```
frontend/src/apis/admin.email.api.ts
  - listEmailAttempts(filter)
  - getEmailAttempt(id)
  - getEmailDailyStats(range)
  - getEmailKpi()
  - getEmailPreview(id)
  - redispatchEmail(id)

frontend/src/hooks/queries/
  - useAdminEmailAttempts.ts        (queryKey: ['admin','email','list',filter])
  - useAdminEmailAttempt.ts         (queryKey: ['admin','email','detail',id])
  - useAdminEmailDailyStats.ts      (queryKey: ['admin','email','stats','daily',range])
  - useAdminEmailKpi.ts             (queryKey: ['admin','email','stats','kpi'])
  - useAdminEmailPreview.ts         (enabled 토글, lazy)

frontend/src/hooks/mutations/
  - useRedispatchEmail.ts           (성공 시 list/detail/kpi 캐시 invalidate)
```

`useAdminPing` 패턴 동일 (Spec 1 기준).

## 8. 보관 정책

| 데이터 | 보관 기간 | 정리 방식 |
|---|---|---|
| `EmailSendAttempt` (모든 status) | 90일 | 매일 03:30 cron — `sent_at < now() - 90d` DELETE |
| `input_payload` JSON | attempt 와 동일 | 별도 처리 없음 |
| `error_message` | attempt 와 동일 | 별도 처리 없음 |
| `NotificationHistory` | 변경 없음 | — |

`EmailSendAttemptCleanupScheduler` — `user/infrastructure/scheduler/`, `@Scheduled(cron = "0 30 3 * * *")`. 환경 변수 `youthfit.email.attempt.retention-days=90` 으로 조정 가능.

## 9. 테스트 전략

### 9.1 단위
- `EmailSendAttemptTest` — 정적 팩토리 (`success`, `failure`), 상태 전이 invariant
- `EmailDispatcherTest` (Mockito) — SES 성공/실패, redispatch FAILED/SENT 분기
- `SesEventPayloadParserTest` — AWS docs 픽스처 (Delivery/Bounce Permanent/Bounce Transient/Complaint) 파싱
- `SesEventHandlerTest` — messageId 매칭 + 상태 전이 호출 검증
- `SnsMessageVerifierTest` — 서명 검증 케이스 (정상/실패/SubscriptionConfirmation)

### 9.2 슬라이스 (`@WebMvcTest`)
- `AdminEmailLogControllerTest`
  - 비인증 401, USER 롤 403, ADMIN 롤 200
  - 필터 파라미터 바인딩 (status CSV, emailType, recipient, from/to)
  - 페이지네이션
  - 재발송 (FAILED → 201, SENT → 400)
- `SesEventListenerSliceTest` — `permitAll()` 라우트, SubscriptionConfirmation 처리

### 9.3 통합 (`@SpringBootTest` + H2)
- `EmailDispatcherIntegrationTest` — `LoggingEmailSender` 로 실제 흐름, attempt + history 양쪽 적재 검증
- `AdminEmailLogQueryIntegrationTest` — 실제 DB 조회/필터/집계 SQL 검증
- `EmailSendAttemptCleanupSchedulerTest` — 90일 경과 row 삭제 검증

> SES 실제 호출과 SNS webhook 의 end-to-end 는 통합 테스트 범위 외 (수동 검증). LocalStack 도입은 v0 외.

### 9.4 프론트 (Vitest + Testing Library)
- `AdminEmailLogPage.test.tsx` — 필터 변경 → query refetch, 빈 상태/에러 상태
- `EmailDailyChart.test.tsx` — recharts 렌더링 (data shape 만 검증)
- `useRedispatchEmail.test.tsx` — mutation 성공 → list 캐시 invalidation
- `AdminEmailDetailPage.test.tsx` — 미리보기 lazy, FAILED 일 때만 재발송 버튼 활성

## 10. 변경 영향 범위

### 10.1 신규 — 백엔드

```
backend/src/main/java/com/youthfit/user/
├── domain/model/
│   ├── EmailSendAttempt.java
│   └── EmailSendStatus.java
├── domain/repository/
│   └── EmailSendAttemptRepository.java
├── application/email/
│   ├── EmailDispatcher.java
│   ├── EmailSendResult.java
│   └── EmailSendAttemptQueryService.java
├── infrastructure/email/
│   ├── SesEventListener.java
│   ├── SesEventHandler.java
│   ├── SesEventPayloadParser.java
│   ├── SnsMessageVerifier.java
│   └── SnsMessage.java
└── infrastructure/scheduler/
    └── EmailSendAttemptCleanupScheduler.java

backend/src/main/java/com/youthfit/admin/
├── presentation/controller/
│   ├── AdminEmailLogApi.java
│   └── AdminEmailLogController.java
└── presentation/dto/
    ├── request/EmailAttemptListQuery.java
    └── response/
        ├── EmailAttemptSummaryResponse.java
        ├── EmailAttemptDetailResponse.java
        ├── EmailAttemptDailyStatsResponse.java
        ├── EmailAttemptKpiResponse.java
        └── EmailAttemptPreviewResponse.java
```

### 10.2 수정 — 백엔드

- `user/application/port/EmailSender.java` — 반환 타입 `void → EmailSendResult`
- `user/infrastructure/email/SesEmailSender.java` — messageId 반환
- `user/infrastructure/email/LoggingEmailSender.java` — UUID 반환
- `user/application/service/NotificationScheduleService.java` — `EmailDispatcher.dispatchDeadline` 으로 교체
- `user/application/service/RecommendationOneDispatcher.java` — `EmailDispatcher.dispatchRecommendation` 으로 교체
- `common/config/SecurityConfig.java` — `/api/internal/notifications/ses-event` `permitAll()` 추가

### 10.3 신규 — 프론트엔드

```
frontend/src/
├── apis/admin.email.api.ts
├── hooks/queries/
│   ├── useAdminEmailAttempts.ts
│   ├── useAdminEmailAttempt.ts
│   ├── useAdminEmailDailyStats.ts
│   ├── useAdminEmailKpi.ts
│   └── useAdminEmailPreview.ts
├── hooks/mutations/
│   └── useRedispatchEmail.ts
├── pages/admin/
│   ├── AdminEmailLogPage.tsx
│   └── AdminEmailDetailPage.tsx
├── components/admin/email/
│   ├── EmailFilterBar.tsx
│   ├── EmailKpiSection.tsx
│   ├── EmailDailyChart.tsx
│   └── EmailAttemptTable.tsx
└── components/charts/
    ├── StackedBarChart.tsx
    └── KpiCard.tsx
```

### 10.4 수정 — 프론트엔드

- `frontend/src/App.tsx` — 라우트 2개 추가
- `frontend/src/components/layout/AdminSidebar.tsx` — `email` 메뉴 활성화
- `frontend/package.json` — `recharts` 추가

### 10.5 의존성

- 백엔드: 추가 라이브러리 없음 (SubscribeURL 호출은 표준 HTTP client)
- 프론트: `recharts ^2.x`

## 11. 데이터베이스 마이그레이션

```sql
-- V{next}__create_email_send_attempt.sql
CREATE TABLE email_send_attempt (
    id                      BIGSERIAL PRIMARY KEY,
    notification_history_id BIGINT,
    recipient_email         VARCHAR(255) NOT NULL,
    recipient_user_id       BIGINT,
    email_type              VARCHAR(30) NOT NULL,
    subject                 VARCHAR(500) NOT NULL,
    input_payload           JSONB NOT NULL,
    ses_message_id          VARCHAR(255),
    status                  VARCHAR(20) NOT NULL,
    error_code              VARCHAR(100),
    error_message           VARCHAR(1000),
    bounce_type             VARCHAR(50),
    sent_at                 TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL
);

CREATE INDEX idx_email_attempt_message_id ON email_send_attempt(ses_message_id)
    WHERE ses_message_id IS NOT NULL;
CREATE INDEX idx_email_attempt_history_id ON email_send_attempt(notification_history_id);
CREATE INDEX idx_email_attempt_sent_at    ON email_send_attempt(sent_at DESC);
CREATE INDEX idx_email_attempt_status     ON email_send_attempt(status);
```

NotificationHistory 변경 없음. `notification_history_id` 는 application-level FK (DB FK 제약 X).

## 12. OPS / 환경 변수

`docs/OPS.md` 갱신 항목:

| 변수 | 기본값 | 설명 |
|---|---|---|
| `youthfit.email.attempt.retention-days` | 90 | EmailSendAttempt 보관 기간 |
| `youthfit.email.ses.configuration-set-name` | (env) | SES Configuration Set 이름 |
| `youthfit.email.sns.event-topic-arn` | (env, 참고용) | SNS Topic ARN |

AWS 콘솔 운영 작업 (수동 1회):
1. SES Configuration Set 생성 → Event Destination 으로 SNS topic 연결 → Delivery, Bounce, Complaint 이벤트 발행
2. SNS Topic → HTTPS Subscription → `https://<host>/api/internal/notifications/ses-event`
3. 첫 SubscribeURL confirmation 자동 처리 확인 (백엔드 로그)

## 13. 의존성 / 후속 spec 영향

- **Spec 1 (admin foundation, 완료)** ← 의존
- **Spec 3 (Q&A 캐시), Spec 4 (LLM 비용), Spec 5 (Ingestion)** ← 본 spec 의 차트 baseline (`recharts` + `StackedBarChart` + `KpiCard`) 재사용
- 이 spec 자체는 다른 spec 에 의존 안 함

## 14. 부록: 시리즈 5개 spec 간 공통 사항

| 항목 | 결정 |
|---|---|
| 인증/라우팅 | Spec 1: `/api/v1/admin/**`, `hasRole("ADMIN")`, `RequireAdmin` |
| ReadModel 패턴 | admin 모듈은 조회만; 데이터 적재는 각 도메인 (DDD 경계) |
| 차트 라이브러리 | **본 spec 에서 결정: `recharts`** + `StackedBarChart`/`KpiCard` 공통 컴포넌트 |
| 보관 정책 | 항목별 다름 (각 spec § 보관 정책 참고) |
| 디자인 토큰 | Spec 1: 다크 사이드바 + 브랜드 indigo |
