# 이메일 발송 인프라 도입 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `LoggingEmailSender` stub 만 있던 알림 발송 경로에 AWS SES 어댑터를 추가하고, partial failure 방어를 위한 `NotificationHistory` 상태 전이(PENDING/SENT/FAILED) 멱등성을 도입한다.

**Architecture:** `EmailSender` 포트는 그대로 두고 (1) Thymeleaf 기반 `NotificationEmailRenderer` 로 본문 렌더링을 분리, (2) `LoggingEmailSender` / `SesEmailSender` 두 어댑터를 `@ConditionalOnProperty` 로 분기, (3) 발송 흐름을 `reservePending → SES → markSent/markFailed` 패턴으로 변경하고 메서드 레벨 `@Transactional` 을 제거한다.

**Tech Stack:** Spring Boot 4.0.5, Java 21, Thymeleaf, AWS SDK v2 (`software.amazon.awssdk:sesv2`), JUnit 5 + Mockito + AssertJ, PostgreSQL 17.

**Spec:** `docs/superpowers/specs/2026-05-05-email-transport-design.md`

**커밋 규칙:** Conventional Commits (`feat`, `fix`, `chore`, `docs`, `test`). 각 Task 끝에 단일 커밋. 사용자 룰: 매 Task 후 `./gradlew build` 통과 확인 후 커밋.

---

## Task 1: 빌드 의존성 추가

**Files:**
- Modify: `backend/build.gradle`

- [ ] **Step 1: Thymeleaf + SES SDK 의존성 추가**

`backend/build.gradle` 의 `dependencies { ... }` 블록 안에 다음 두 줄을 추가한다 (기존 `'org.springframework.boot:spring-boot-starter-data-jpa'` 등 옆에 정렬해서 배치):

```gradle
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'software.amazon.awssdk:sesv2'
```

`sesv2` 는 이미 적재된 BOM (`platform('software.amazon.awssdk:bom:2.28.16')`) 으로부터 버전을 받아온다.

- [ ] **Step 2: 의존성 해석 검증**

```bash
cd backend && ./gradlew dependencies --configuration runtimeClasspath | grep -E "thymeleaf|sesv2"
```

Expected: `org.springframework.boot:spring-boot-starter-thymeleaf` 와 `software.amazon.awssdk:sesv2` 라인이 출력됨.

- [ ] **Step 3: 빌드 검증**

```bash
cd backend && ./gradlew build -x test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add backend/build.gradle
git commit -m "chore(user): Thymeleaf + AWS SES SDK 의존성 추가"
```

---

## Task 2: NotificationStatus enum, EmailContent record, EmailSendException 추가

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/domain/model/NotificationStatus.java`
- Create: `backend/src/main/java/com/youthfit/user/application/dto/result/EmailContent.java`
- Create: `backend/src/main/java/com/youthfit/user/domain/exception/EmailSendException.java`

- [ ] **Step 1: `NotificationStatus` enum 작성**

```java
package com.youthfit.user.domain.model;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
```

- [ ] **Step 2: `EmailContent` record 작성**

```java
package com.youthfit.user.application.dto.result;

public record EmailContent(
        String subject,
        String htmlBody,
        String textBody
) {
}
```

- [ ] **Step 3: `EmailSendException` 작성**

`AttachmentNotFoundException` 의 패키지 컨벤션(`{module}/domain/exception/`) 을 따른다.

```java
package com.youthfit.user.domain.exception;

/**
 * 이메일 발송 어댑터에서 외부 게이트웨이(SES 등) 호출이 실패했을 때 던지는 도메인 예외.
 * 호출자(NotificationScheduleService 등) 가 catch 하여 NotificationHistory 를 FAILED 로 마킹한다.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: 컴파일 검증**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/domain/model/NotificationStatus.java \
        backend/src/main/java/com/youthfit/user/application/dto/result/EmailContent.java \
        backend/src/main/java/com/youthfit/user/domain/exception/EmailSendException.java
git commit -m "feat(user): NotificationStatus, EmailContent, EmailSendException 추가"
```

---

## Task 3: NotificationHistory 도메인 모델 확장

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java`
- Modify: `backend/src/test/java/com/youthfit/user/domain/model/NotificationHistoryTest.java`

- [ ] **Step 1: 실패 테스트 추가** — `NotificationHistoryTest`

기존 두 테스트는 그대로 두고 아래 테스트들을 클래스 끝에 추가한다.

```java
    @Test
    @DisplayName("pending 정적 팩토리는 status=PENDING, createdAt=now 으로 생성한다")
    void pending_setsStatusAndCreatedAt() {
        // given & when
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(history.getCreatedAt()).isNotNull();
        assertThat(history.getSentAt()).isNull();
        assertThat(history.getFailedAt()).isNull();
        assertThat(history.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markSent 는 PENDING → SENT 로 전이하고 sentAt 을 채운다")
    void markSent_pendingToSent() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        LocalDateTime now = LocalDateTime.now();

        // when
        history.markSent(now);

        // then
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(history.getSentAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("markFailed 는 PENDING → FAILED 로 전이하고 failedAt/failureReason 을 채운다")
    void markFailed_pendingToFailed() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        LocalDateTime now = LocalDateTime.now();

        // when
        history.markFailed(now, "SES 발송 실패: foo@example.com");

        // then
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(history.getFailedAt()).isEqualTo(now);
        assertThat(history.getFailureReason()).isEqualTo("SES 발송 실패: foo@example.com");
    }

    @Test
    @DisplayName("SENT 상태에서 markSent 재호출은 IllegalStateException")
    void markSent_fromSent_throws() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        history.markSent(LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> history.markSent(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FAILED 상태에서 markFailed 재호출은 IllegalStateException")
    void markFailed_fromFailed_throws() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        history.markFailed(LocalDateTime.now(), "1차 실패");

        // when & then
        assertThatThrownBy(() -> history.markFailed(LocalDateTime.now(), "2차 실패"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFailed 의 reason 이 500자 초과면 자른다")
    void markFailed_truncatesReason() {
        // given
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);
        String longReason = "x".repeat(600);

        // when
        history.markFailed(LocalDateTime.now(), longReason);

        // then
        assertThat(history.getFailureReason()).hasSize(500);
    }
```

import 추가:
```java
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.domain.model.NotificationHistoryTest"
```

Expected: FAIL — `pending`, `markSent`, `markFailed`, `getStatus`, `getCreatedAt`, `getFailedAt`, `getFailureReason` 메서드 미정의.

- [ ] **Step 3: `NotificationHistory` 확장 구현**

`backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java` 를 다음으로 교체:

```java
package com.youthfit.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_user_policy_type",
                columnNames = {"user_id", "policy_id", "notification_type"})
})
public class NotificationHistory {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    private NotificationHistory(Long userId, Long policyId, NotificationType notificationType,
                                NotificationStatus status, LocalDateTime createdAt) {
        this.userId = userId;
        this.policyId = policyId;
        this.notificationType = notificationType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static NotificationHistory pending(Long userId, Long policyId, NotificationType notificationType) {
        return new NotificationHistory(userId, policyId, notificationType,
                NotificationStatus.PENDING, LocalDateTime.now());
    }

    public void markSent(LocalDateTime now) {
        if (this.status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 SENT 로 전이할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
    }

    public void markFailed(LocalDateTime now, String reason) {
        if (this.status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 FAILED 로 전이할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = NotificationStatus.FAILED;
        this.failedAt = now;
        this.failureReason = truncate(reason);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= FAILURE_REASON_MAX_LENGTH
                ? s
                : s.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
```

> **주의**: 기존 public 생성자 `NotificationHistory(Long, Long, NotificationType)` 는 제거된다 → 다음 Task 들에서 호출자 코드를 모두 `pending(...)` 으로 마이그레이션한다.

- [ ] **Step 4: 기존 두 테스트 메서드 마이그레이션**

`NotificationHistoryTest` 의 기존 `create_setsAllFields()` 와 `create_idAndSentAtAreNull()` 도 `pending(...)` 호출로 변경:

```java
    @Test
    @DisplayName("pending 호출 시 userId, policyId, notificationType이 설정된다")
    void pending_setsAllFields() {
        // given & when
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history.getUserId()).isEqualTo(1L);
        assertThat(history.getPolicyId()).isEqualTo(100L);
        assertThat(history.getNotificationType()).isEqualTo(NotificationType.DEADLINE);
    }

    @Test
    @DisplayName("pending 직후 id와 sentAt은 null이다")
    void pending_idAndSentAtAreNull() {
        // given & when
        NotificationHistory history = NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history.getId()).isNull();
        assertThat(history.getSentAt()).isNull();
    }
```

(기존 `new NotificationHistory(...)` 호출 두 곳을 위 코드로 교체)

- [ ] **Step 5: 테스트 실행해서 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.domain.model.NotificationHistoryTest"
```

Expected: PASS (모든 테스트 통과).

- [ ] **Step 6: 다른 곳의 컴파일 에러 확인 (다음 Task 들에서 수정 예정)**

```bash
cd backend && ./gradlew compileJava 2>&1 | head -30
```

Expected: `NotificationScheduleService.java`, `RecommendationOneDispatcher.java`, `RecommendationOneDispatcherTest.java` 등에서 `new NotificationHistory(...)` 호출에 대한 컴파일 에러. 이는 Task 11, 12 에서 수정한다.

- [ ] **Step 7: 커밋 (컴파일 에러 있는 채로 진행)**

> **주의**: 이 Task 끝 시점에는 `compileJava` 가 실패한다. 다음 Task 들에서 호출자를 마이그레이션한 후 한꺼번에 빌드를 통과시킨다. 사용자 룰 "매 Task 후 빌드 통과" 와 충돌하므로, **이 Task 만 예외적으로 다음 Task(11, 12) 까지 한 시리즈로 묶어서 빌드 검증**한다. 따라서 Task 3~12 가 한 작업 단위로 PR 분할에서 함께 묶일 수 있다.

```bash
git add backend/src/main/java/com/youthfit/user/domain/model/NotificationHistory.java \
        backend/src/test/java/com/youthfit/user/domain/model/NotificationHistoryTest.java
git commit -m "feat(user): NotificationHistory 상태 전이(PENDING/SENT/FAILED) 도입"
```

---

## Task 4: 마이그레이션 SQL 작성

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-05-notification-history-status.sql`

- [ ] **Step 1: SQL 파일 작성**

```sql
-- notification_history: 상태 전이(PENDING/SENT/FAILED) 도입
-- 적용 절차: psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-05-notification-history-status.sql
-- 기존 행은 status='SENT' 로 백필 (이미 발송 완료로 간주)

ALTER TABLE notification_history ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'SENT';
ALTER TABLE notification_history ADD COLUMN created_at TIMESTAMP;
ALTER TABLE notification_history ADD COLUMN failed_at TIMESTAMP;
ALTER TABLE notification_history ADD COLUMN failure_reason VARCHAR(500);

ALTER TABLE notification_history ALTER COLUMN sent_at DROP NOT NULL;

UPDATE notification_history SET created_at = sent_at WHERE created_at IS NULL;

ALTER TABLE notification_history ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notification_history ALTER COLUMN status DROP DEFAULT;
```

- [ ] **Step 2: 로컬 PG 에 적용 (검증)**

> 이 단계는 사용자가 직접 진행. docker compose 의 PG 컨테이너 또는 로컬 PG 에 적용:

```bash
docker compose exec -T postgres psql -U youthfit -d youthfit < backend/src/main/resources/sql/2026-05-05-notification-history-status.sql
```

Expected: 모든 ALTER/UPDATE 가 에러 없이 완료. `\d notification_history` 로 확인 시 status, created_at, failed_at, failure_reason 컬럼이 보임.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-05-notification-history-status.sql
git commit -m "chore(user): notification_history 상태 전이 컬럼 마이그레이션 SQL 추가"
```

---

## Task 5: NotificationDispatchService 작성 (REQUIRES_NEW + 통합 테스트)

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/application/service/NotificationDispatchService.java`
- Create: `backend/src/test/java/com/youthfit/user/application/service/NotificationDispatchServiceIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 먼저 작성 (실패)**

```java
package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationStatus;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.infrastructure.persistence.NotificationHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationDispatchService 통합 테스트")
@SpringBootTest
class NotificationDispatchServiceIntegrationTest {

    @Autowired
    private NotificationDispatchService dispatchService;

    @Autowired
    private NotificationHistoryJpaRepository jpaRepository;

    @Autowired
    private NotificationHistoryRepository repository;

    @BeforeEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("reservePending 은 PENDING 행을 별도 트랜잭션으로 commit 한다")
    void reservePending_commitsInSeparateTransaction() {
        // when
        NotificationHistory history = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history).isNotNull();
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(jpaRepository.findById(history.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 동일 (user, policy, type) 행이 있으면 reservePending 은 null 반환")
    void reservePending_existingRow_returnsNull() {
        // given
        repository.save(NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE));

        // when
        NotificationHistory result = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("markSent 는 PENDING → SENT 로 전이한다")
    void markSent_transitionsToSent() {
        // given
        NotificationHistory pending = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // when
        dispatchService.markSent(pending.getId());

        // then
        Optional<NotificationHistory> found = jpaRepository.findById(pending.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.get().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed 는 PENDING → FAILED 로 전이하고 failureReason 저장")
    void markFailed_transitionsToFailed() {
        // given
        NotificationHistory pending = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // when
        dispatchService.markFailed(pending.getId(), "SES 호출 실패");

        // then
        Optional<NotificationHistory> found = jpaRepository.findById(pending.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(found.get().getFailedAt()).isNotNull();
        assertThat(found.get().getFailureReason()).isEqualTo("SES 호출 실패");
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.application.service.NotificationDispatchServiceIntegrationTest"
```

Expected: FAIL — `NotificationDispatchService` 빈 미정의 또는 클래스 없음.

- [ ] **Step 3: `NotificationDispatchService` 구현**

```java
package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationHistoryRepository repository;

    /**
     * PENDING 행을 별도 트랜잭션(REQUIRES_NEW)으로 INSERT 하고 즉시 commit 한다.
     * 동일 (userId, policyId, type) 행이 이미 존재하면 null 을 반환하여 호출자가 skip 하도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationHistory reservePending(Long userId, Long policyId, NotificationType type) {
        if (repository.existsByUserIdAndPolicyIdAndNotificationType(userId, policyId, type)) {
            return null;
        }
        try {
            return repository.save(NotificationHistory.pending(userId, policyId, type));
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 동일 키로 INSERT 한 race condition
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long historyId) {
        repository.findById(historyId)
                .ifPresent(h -> h.markSent(LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long historyId, String reason) {
        repository.findById(historyId)
                .ifPresent(h -> h.markFailed(LocalDateTime.now(), reason));
    }
}
```

- [ ] **Step 4: `NotificationHistoryRepository` 에 `findById` 시그니처 추가**

`backend/src/main/java/com/youthfit/user/domain/repository/NotificationHistoryRepository.java`:

```java
package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationType;

import java.util.Optional;

public interface NotificationHistoryRepository {

    NotificationHistory save(NotificationHistory notificationHistory);

    boolean existsByUserIdAndPolicyIdAndNotificationType(Long userId, Long policyId, NotificationType notificationType);

    Optional<NotificationHistory> findById(Long id);
}
```

- [ ] **Step 5: `NotificationHistoryRepositoryImpl` 에 `findById` 구현 추가**

`backend/src/main/java/com/youthfit/user/infrastructure/persistence/NotificationHistoryRepositoryImpl.java`:

```java
package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationHistoryRepositoryImpl implements NotificationHistoryRepository {

    private final NotificationHistoryJpaRepository jpaRepository;

    @Override
    public NotificationHistory save(NotificationHistory notificationHistory) {
        return jpaRepository.save(notificationHistory);
    }

    @Override
    public boolean existsByUserIdAndPolicyIdAndNotificationType(Long userId, Long policyId, NotificationType notificationType) {
        return jpaRepository.existsByUserIdAndPolicyIdAndNotificationType(userId, policyId, notificationType);
    }

    @Override
    public Optional<NotificationHistory> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
```

- [ ] **Step 6: `application.yml` 의 `local` profile 에 ddl-auto: update 가 설정되어 있는지 확인**

기존 application.yml 의 local profile 섹션에 `ddl-auto: update` 가 이미 설정되어 있으면 통합 테스트는 자동으로 status 컬럼을 추가한다. **위치**: `application.yml:124` 부근. 확인만 필요.

- [ ] **Step 7: 통합 테스트 실행 (PASS)**

```bash
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew test --tests "com.youthfit.user.application.service.NotificationDispatchServiceIntegrationTest"
```

Expected: PASS (4개 테스트 모두 통과). 단, 통합 테스트는 PostgreSQL + pgvector 가 필요하므로 docker compose 가 떠 있어야 한다.

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/application/service/NotificationDispatchService.java \
        backend/src/main/java/com/youthfit/user/domain/repository/NotificationHistoryRepository.java \
        backend/src/main/java/com/youthfit/user/infrastructure/persistence/NotificationHistoryRepositoryImpl.java \
        backend/src/test/java/com/youthfit/user/application/service/NotificationDispatchServiceIntegrationTest.java
git commit -m "feat(user): NotificationDispatchService 추가 (REQUIRES_NEW 상태 전이)"
```

---

## Task 6: NotificationEmailRenderer 작성 + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/infrastructure/email/EmailTemplateConfig.java`
- Create: `backend/src/main/java/com/youthfit/user/application/service/NotificationEmailRenderer.java`
- Create: `backend/src/test/java/com/youthfit/user/application/service/NotificationEmailRendererTest.java`

> **배경**: Spring Boot 의 Thymeleaf autoconfigure 는 `templates/*.html` 만 default 로 처리한다. 우리는 `.html` (HTML 모드) 와 `.txt` (TEXT 모드) 두 종류 템플릿이 필요하므로, 별도 TemplateEngine 빈 두 개(`emailHtmlTemplateEngine`, `emailTextTemplateEngine`) 를 만들어 `NotificationEmailRenderer` 에 주입한다. Spring Boot autoconfigure 의 기본 TemplateEngine 빈과 충돌하지 않도록 `@Bean` 이름으로 격리.

- [ ] **Step 1: 단위 테스트 먼저 작성**

```java
package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationEmailRenderer")
class NotificationEmailRendererTest {

    private NotificationEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        TemplateEngine htmlEngine = newEngine("HTML", ".html");
        TemplateEngine textEngine = newEngine("TEXT", ".txt");
        renderer = new NotificationEmailRenderer(htmlEngine, textEngine, "https://youthfit.test");
    }

    @Test
    @DisplayName("renderDeadline: subject 에 정책명이 포함된다")
    void renderDeadline_subjectIncludesTitle() {
        // given
        Policy policy = createPolicy(10L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.subject()).contains("청년 취업 지원");
        assertThat(content.subject()).contains("마감");
    }

    @Test
    @DisplayName("renderDeadline: htmlBody 에 정책명·마감일·상세 링크가 포함된다")
    void renderDeadline_htmlBodyIncludesAll() {
        // given
        Policy policy = createPolicy(10L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.htmlBody()).contains("청년 취업 지원");
        assertThat(content.htmlBody()).contains("2026-06-30");
        assertThat(content.htmlBody()).contains("https://youthfit.test/policies/10");
    }

    @Test
    @DisplayName("renderDeadline: textBody 에도 같은 정보가 포함된다")
    void renderDeadline_textBodyIncludesAll() {
        // given
        Policy policy = createPolicy(10L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.textBody()).contains("청년 취업 지원");
        assertThat(content.textBody()).contains("2026-06-30");
        assertThat(content.textBody()).contains("https://youthfit.test/policies/10");
    }

    @Test
    @DisplayName("renderRecommendation: subject 에 정책 개수가 포함된다")
    void renderRecommendation_subjectIncludesCount() {
        // given
        List<Policy> policies = List.of(
                createPolicy(10L, "정책1", LocalDate.of(2026, 6, 30)),
                createPolicy(11L, "정책2", LocalDate.of(2026, 7, 15)),
                createPolicy(12L, "정책3", LocalDate.of(2026, 8, 1))
        );

        // when
        EmailContent content = renderer.renderRecommendation(policies);

        // then
        assertThat(content.subject()).contains("3");
    }

    @Test
    @DisplayName("renderRecommendation: htmlBody 에 모든 정책의 링크가 들어간다")
    void renderRecommendation_htmlBodyIncludesAllLinks() {
        // given
        List<Policy> policies = List.of(
                createPolicy(10L, "정책1", LocalDate.of(2026, 6, 30)),
                createPolicy(11L, "정책2", LocalDate.of(2026, 7, 15))
        );

        // when
        EmailContent content = renderer.renderRecommendation(policies);

        // then
        assertThat(content.htmlBody()).contains("https://youthfit.test/policies/10");
        assertThat(content.htmlBody()).contains("https://youthfit.test/policies/11");
        assertThat(content.htmlBody()).contains("정책1");
        assertThat(content.htmlBody()).contains("정책2");
    }

    @Test
    @DisplayName("htmlBody 에 알림 설정 페이지 unsubscribe 링크가 포함된다")
    void htmlBody_includesSettingsLink() {
        // given
        Policy policy = createPolicy(10L, "정책", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.htmlBody()).contains("https://youthfit.test/settings/notifications");
    }

    // ── 헬퍼 ──

    private Policy createPolicy(Long id, String title, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title(title)
                .category(Category.JOBS)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private TemplateEngine newEngine(String mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.application.service.NotificationEmailRendererTest"
```

Expected: FAIL — 클래스 미정의 또는 템플릿 파일 미존재.

- [ ] **Step 3: `EmailTemplateConfig` 작성**

```java
package com.youthfit.user.infrastructure.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
public class EmailTemplateConfig {

    @Bean("emailHtmlTemplateEngine")
    public TemplateEngine emailHtmlTemplateEngine() {
        return buildEngine(TemplateMode.HTML, ".html");
    }

    @Bean("emailTextTemplateEngine")
    public TemplateEngine emailTextTemplateEngine() {
        return buildEngine(TemplateMode.TEXT, ".txt");
    }

    private TemplateEngine buildEngine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
```

- [ ] **Step 4: `NotificationEmailRenderer` 구현**

```java
package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

@Component
public class NotificationEmailRenderer {

    private static final Locale KOREAN = Locale.KOREAN;

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;
    private final String baseUrl;

    public NotificationEmailRenderer(@Qualifier("emailHtmlTemplateEngine") TemplateEngine htmlEngine,
                                     @Qualifier("emailTextTemplateEngine") TemplateEngine textEngine,
                                     @Value("${youthfit.email.base-url}") String baseUrl) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
        this.baseUrl = baseUrl;
    }

    public EmailContent renderDeadline(Policy policy) {
        Context ctx = new Context(KOREAN);
        ctx.setVariable("policy", policy);
        ctx.setVariable("baseUrl", baseUrl);

        String subject = "[YouthFit] " + policy.getTitle() + " 마감 임박 알림";
        String html = htmlEngine.process("email/deadline", ctx);
        String text = textEngine.process("email/deadline", ctx);
        return new EmailContent(subject, html, text);
    }

    public EmailContent renderRecommendation(List<Policy> policies) {
        Context ctx = new Context(KOREAN);
        ctx.setVariable("policies", policies);
        ctx.setVariable("count", policies.size());
        ctx.setVariable("baseUrl", baseUrl);

        String subject = "[YouthFit] 이번 주 당신에게 맞을 수 있는 정책 " + policies.size() + "개";
        String html = htmlEngine.process("email/recommendation", ctx);
        String text = textEngine.process("email/recommendation", ctx);
        return new EmailContent(subject, html, text);
    }
}
```

- [ ] **Step 5: 다음 Task 7 에서 템플릿 파일을 만들면 통과한다 — 일단 컴파일만 검증**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL (NotificationHistory 호출자 에러는 Task 11/12 까지 미해결 — 단, NotificationEmailRenderer 자체는 컴파일됨).

- [ ] **Step 6: 커밋 (테스트는 Task 7 이후 통과)**

```bash
git add backend/src/main/java/com/youthfit/user/infrastructure/email/EmailTemplateConfig.java \
        backend/src/main/java/com/youthfit/user/application/service/NotificationEmailRenderer.java \
        backend/src/test/java/com/youthfit/user/application/service/NotificationEmailRendererTest.java
git commit -m "feat(user): NotificationEmailRenderer + EmailTemplateConfig 추가 (HTML/TEXT 분리)"
```

---

## Task 7: 이메일 HTML/TXT 템플릿 4개 작성

**Files:**
- Create: `backend/src/main/resources/templates/email/deadline.html`
- Create: `backend/src/main/resources/templates/email/deadline.txt`
- Create: `backend/src/main/resources/templates/email/recommendation.html`
- Create: `backend/src/main/resources/templates/email/recommendation.txt`

- [ ] **Step 1: `deadline.html` 작성**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="|${policy.title} 마감 임박 알림|">정책 마감 임박 알림</title>
</head>
<body style="font-family: -apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1a1a1a;">

<h1 style="font-size: 20px; margin-bottom: 16px;" th:text="|${policy.title} 마감이 다가옵니다|">정책 마감이 다가옵니다</h1>

<p style="font-size: 14px; line-height: 1.6;">
    북마크하신 정책의 신청 마감일이 임박했습니다. 아래 일정 안에 신청 절차를 마무리해 주세요.
</p>

<div style="background: #f6f8fa; border-radius: 8px; padding: 16px 20px; margin: 20px 0;">
    <p style="margin: 0 0 8px 0; font-size: 13px; color: #666;">정책명</p>
    <p style="margin: 0 0 12px 0; font-size: 16px; font-weight: 600;" th:text="${policy.title}">정책명</p>

    <p style="margin: 0 0 8px 0; font-size: 13px; color: #666;">신청 마감일</p>
    <p style="margin: 0;" th:text="${policy.applyEnd}">2026-06-30</p>
</div>

<p style="text-align: center; margin: 28px 0;">
    <a th:href="@{|${baseUrl}/policies/${policy.id}|}"
       style="display: inline-block; padding: 12px 24px; background: #0a66c2; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: 600;">
        정책 자세히 보기
    </a>
</p>

<hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;">

<p style="font-size: 12px; color: #6b7280; line-height: 1.5;">
    이 메일은 YouthFit 의 마감일 알림 기능에 의해 발송되었습니다.<br>
    더 이상 받고 싶지 않으시면 <a th:href="@{|${baseUrl}/settings/notifications|}" style="color: #0a66c2;">알림 설정</a>에서 해제하실 수 있습니다.
</p>

</body>
</html>
```

- [ ] **Step 2: `deadline.txt` 작성**

```
[YouthFit] [(${policy.title})] 마감이 다가옵니다

북마크하신 정책의 신청 마감일이 임박했습니다.

정책명: [(${policy.title})]
신청 마감일: [(${policy.applyEnd})]

자세히 보기: [(${baseUrl})]/policies/[(${policy.id})]

------
이 메일은 YouthFit 의 마감일 알림 기능에 의해 발송되었습니다.
알림 해제: [(${baseUrl})]/settings/notifications
```

- [ ] **Step 3: `recommendation.html` 작성**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="|이번 주 추천 정책 ${count}개|">이번 주 추천 정책</title>
</head>
<body style="font-family: -apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1a1a1a;">

<h1 style="font-size: 20px; margin-bottom: 16px;">
    이번 주 당신에게 맞을 수 있는 정책 <span th:text="${count}">3</span>개
</h1>

<p style="font-size: 14px; line-height: 1.6;">
    프로필 기준 자격 가능성이 높은 정책을 추려 보내드립니다. 관심 있는 정책의 자세한 내용을 확인해 보세요.
</p>

<ul style="list-style: none; padding: 0; margin: 24px 0;">
    <li th:each="p : ${policies}"
        style="border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; margin-bottom: 12px;">
        <a th:href="@{|${baseUrl}/policies/${p.id}|}"
           style="font-size: 16px; font-weight: 600; color: #0a66c2; text-decoration: none;"
           th:text="${p.title}">정책명</a>
        <p style="margin: 8px 0 0 0; font-size: 13px; color: #666;">
            마감일: <span th:text="${p.applyEnd != null ? p.applyEnd : '상시 모집'}">2026-06-30</span>
        </p>
    </li>
</ul>

<hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;">

<p style="font-size: 12px; color: #6b7280; line-height: 1.5;">
    이 메일은 YouthFit 의 맞춤 정책 추천 기능에 의해 발송되었습니다.<br>
    더 이상 받고 싶지 않으시면 <a th:href="@{|${baseUrl}/settings/notifications|}" style="color: #0a66c2;">알림 설정</a>에서 해제하실 수 있습니다.
</p>

</body>
</html>
```

- [ ] **Step 4: `recommendation.txt` 작성**

```
[YouthFit] 이번 주 당신에게 맞을 수 있는 정책 [(${count})]개

프로필 기준 자격 가능성이 높은 정책을 추려 보내드립니다.

[# th:each="p : ${policies}"]
- [(${p.title})]
  마감일: [(${p.applyEnd})]
  자세히: [(${baseUrl})]/policies/[(${p.id})]
[/]

------
알림 해제: [(${baseUrl})]/settings/notifications
```

- [ ] **Step 5: 렌더러 테스트 실행 (PASS)**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.application.service.NotificationEmailRendererTest"
```

Expected: PASS — 6개 테스트 모두 통과.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/resources/templates/email/
git commit -m "feat(user): 이메일 본문 Thymeleaf 템플릿 4종 추가 (deadline/recommendation × html/txt)"
```

---

## Task 8: LoggingEmailSender 변경 — 렌더러 주입 + @ConditionalOnProperty

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/infrastructure/email/LoggingEmailSender.java`
- Modify: `backend/src/test/java/com/youthfit/user/infrastructure/email/LoggingEmailSenderTest.java`

- [ ] **Step 1: `LoggingEmailSender` 변경**

```java
package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "youthfit.email.transport", havingValue = "logging", matchIfMissing = true)
@RequiredArgsConstructor
public class LoggingEmailSender implements EmailSender {

    private static final int LOG_PREVIEW_LENGTH = 200;

    private final NotificationEmailRenderer renderer;

    @Override
    public void sendDeadlineNotification(String recipientEmail, Policy policy) {
        EmailContent content = renderer.renderDeadline(policy);
        log.info("[이메일 발송][마감][logging] to={} subject={} htmlPreview={}",
                recipientEmail,
                content.subject(),
                preview(content.htmlBody()));
    }

    @Override
    public void sendRecommendationNotification(String recipientEmail, List<Policy> policies) {
        EmailContent content = renderer.renderRecommendation(policies);
        log.info("[이메일 발송][추천][logging] to={} subject={} count={} htmlPreview={}",
                recipientEmail,
                content.subject(),
                policies.size(),
                preview(content.htmlBody()));
    }

    private String preview(String body) {
        if (body == null) return "";
        return body.length() <= LOG_PREVIEW_LENGTH
                ? body
                : body.substring(0, LOG_PREVIEW_LENGTH) + "...";
    }
}
```

- [ ] **Step 2: `LoggingEmailSenderTest` 갱신**

```java
package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("LoggingEmailSender")
class LoggingEmailSenderTest {

    private NotificationEmailRenderer renderer;
    private LoggingEmailSender loggingEmailSender;

    @BeforeEach
    void setUp() {
        renderer = mock(NotificationEmailRenderer.class);
        loggingEmailSender = new LoggingEmailSender(renderer);

        given(renderer.renderDeadline(any()))
                .willReturn(new EmailContent("[YouthFit] 마감", "<html>...</html>", "text"));
        given(renderer.renderRecommendation(any()))
                .willReturn(new EmailContent("[YouthFit] 추천", "<html>...</html>", "text"));
    }

    @Test
    @DisplayName("마감일 알림 발송 시 예외 없이 로그를 출력한다")
    void sendDeadlineNotification_logsWithoutException() {
        // given
        Policy policy = createPolicy(1L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when & then
        assertThatCode(() ->
                loggingEmailSender.sendDeadlineNotification("test@example.com", policy)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("추천 알림 발송 시 예외 없이 로그를 출력한다")
    void sendRecommendationNotification_logsWithoutException() {
        // given
        Policy policy = createPolicy(1L, "정책1", LocalDate.of(2026, 6, 30));

        // when & then
        assertThatCode(() ->
                loggingEmailSender.sendRecommendationNotification("test@example.com", List.of(policy))
        ).doesNotThrowAnyException();
    }

    private Policy createPolicy(Long id, String title, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title(title)
                .category(Category.JOBS)
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.infrastructure.email.LoggingEmailSenderTest"
```

Expected: PASS.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/infrastructure/email/LoggingEmailSender.java \
        backend/src/test/java/com/youthfit/user/infrastructure/email/LoggingEmailSenderTest.java
git commit -m "feat(user): LoggingEmailSender 에 렌더러 주입 + property toggle 분기"
```

---

## Task 9: SesEmailConfig + SesEmailSender + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/user/infrastructure/email/SesEmailConfig.java`
- Create: `backend/src/main/java/com/youthfit/user/infrastructure/email/SesEmailSender.java`
- Create: `backend/src/test/java/com/youthfit/user/infrastructure/email/SesEmailSenderTest.java`

- [ ] **Step 1: 단위 테스트 먼저 작성**

```java
package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@DisplayName("SesEmailSender")
@ExtendWith(MockitoExtension.class)
class SesEmailSenderTest {

    @Mock
    private SesV2Client sesClient;

    @Mock
    private NotificationEmailRenderer renderer;

    private SesEmailSender sesEmailSender;

    @BeforeEach
    void setUp() {
        sesEmailSender = new SesEmailSender(sesClient, renderer,
                "noreply@youthfit.example.com", "YouthFit");
        given(renderer.renderDeadline(any()))
                .willReturn(new EmailContent("[YouthFit] 마감", "<html>마감</html>", "마감 텍스트"));
        given(renderer.renderRecommendation(any()))
                .willReturn(new EmailContent("[YouthFit] 추천", "<html>추천</html>", "추천 텍스트"));
    }

    @Test
    @DisplayName("sendDeadlineNotification: SendEmailRequest 가 정확히 빌드된다")
    void sendDeadlineNotification_buildsRequestCorrectly() {
        // given
        Policy policy = createPolicy(10L);

        // when
        sesEmailSender.sendDeadlineNotification("user@example.com", policy);

        // then
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        then(sesClient).should().sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertThat(req.fromEmailAddress()).contains("noreply@youthfit.example.com");
        assertThat(req.fromEmailAddress()).contains("YouthFit");
        assertThat(req.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(req.content().simple().subject().data()).contains("마감");
        assertThat(req.content().simple().body().html().data()).contains("마감");
        assertThat(req.content().simple().body().text().data()).contains("마감");
    }

    @Test
    @DisplayName("sendRecommendationNotification: SendEmailRequest 가 정확히 빌드된다")
    void sendRecommendationNotification_buildsRequestCorrectly() {
        // given
        Policy policy = createPolicy(10L);

        // when
        sesEmailSender.sendRecommendationNotification("user@example.com", List.of(policy));

        // then
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        then(sesClient).should().sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertThat(req.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(req.content().simple().subject().data()).contains("추천");
    }

    @Test
    @DisplayName("SES 호출이 SesV2Exception 을 던지면 EmailSendException 으로 변환")
    void sesV2Exception_translatedToEmailSendException() {
        // given
        Policy policy = createPolicy(10L);
        willThrow(SesV2Exception.builder().message("AccessDenied").build())
                .given(sesClient).sendEmail(any(SendEmailRequest.class));

        // when & then
        assertThatThrownBy(() -> sesEmailSender.sendDeadlineNotification("user@example.com", policy))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("user@example.com");
    }

    private Policy createPolicy(Long id) {
        Policy policy = Policy.builder()
                .title("테스트")
                .category(Category.JOBS)
                .applyEnd(LocalDate.of(2026, 6, 30))
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.infrastructure.email.SesEmailSenderTest"
```

Expected: FAIL — `SesEmailSender` 클래스 미정의.

- [ ] **Step 3: `SesEmailSender` 구현**

```java
package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "youthfit.email.transport", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private static final String CHARSET = "UTF-8";

    private final SesV2Client sesClient;
    private final NotificationEmailRenderer renderer;
    private final String fromAddress;
    private final String fromName;

    public SesEmailSender(SesV2Client sesClient,
                          NotificationEmailRenderer renderer,
                          @Value("${youthfit.email.from.address}") String fromAddress,
                          @Value("${youthfit.email.from.name:YouthFit}") String fromName) {
        this.sesClient = sesClient;
        this.renderer = renderer;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendDeadlineNotification(String recipientEmail, Policy policy) {
        com.youthfit.user.application.dto.result.EmailContent content = renderer.renderDeadline(policy);
        sendInternal(recipientEmail, content, "DEADLINE");
    }

    @Override
    public void sendRecommendationNotification(String recipientEmail, List<Policy> policies) {
        com.youthfit.user.application.dto.result.EmailContent content = renderer.renderRecommendation(policies);
        sendInternal(recipientEmail, content, "RECOMMENDATION");
    }

    private void sendInternal(String recipientEmail,
                              com.youthfit.user.application.dto.result.EmailContent content,
                              String type) {
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(formatFrom(fromAddress, fromName))
                    .destination(Destination.builder().toAddresses(recipientEmail).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(charsetContent(content.subject()))
                                    .body(Body.builder()
                                            .html(charsetContent(content.htmlBody()))
                                            .text(charsetContent(content.textBody()))
                                            .build())
                                    .build())
                            .build())
                    .build());
            log.info("SES 발송 성공 to={} type={}", recipientEmail, type);
        } catch (SesV2Exception | SdkException e) {
            log.error("SES 발송 실패 to={} type={}", recipientEmail, type, e);
            throw new EmailSendException("SES 발송 실패: " + recipientEmail, e);
        }
    }

    private static Content charsetContent(String data) {
        return Content.builder().data(data).charset(CHARSET).build();
    }

    private static String formatFrom(String address, String name) {
        if (name == null || name.isBlank()) {
            return address;
        }
        return name + " <" + address + ">";
    }
}
```

- [ ] **Step 4: `SesEmailConfig` 작성**

```java
package com.youthfit.user.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "youthfit.email.transport", havingValue = "ses")
public class SesEmailConfig {

    @Bean
    public SesV2Client sesV2Client(
            @Value("${youthfit.email.ses.region}") String region,
            @Value("${youthfit.email.ses.access-key-id}") String accessKeyId,
            @Value("${youthfit.email.ses.secret-access-key}") String secretAccessKey) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }
}
```

- [ ] **Step 5: 테스트 실행 (PASS)**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.infrastructure.email.SesEmailSenderTest"
```

Expected: PASS — 3개 테스트 모두 통과.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/infrastructure/email/SesEmailConfig.java \
        backend/src/main/java/com/youthfit/user/infrastructure/email/SesEmailSender.java \
        backend/src/test/java/com/youthfit/user/infrastructure/email/SesEmailSenderTest.java
git commit -m "feat(user): SesEmailSender + SesEmailConfig 추가 (AWS SES SDK 기반)"
```

---

## Task 10: application.yml 키 추가

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 공통 섹션에 `youthfit.email.*` 추가**

기존 `youthfit:` 섹션 아래의 적절한 위치에 (예: `qna` 키 다음에) 다음 블록을 추가:

```yaml
  email:
    transport: ${EMAIL_TRANSPORT:logging}
    from:
      address: ${MAIL_FROM_ADDRESS:}
      name:    ${MAIL_FROM_NAME:YouthFit}
    base-url: ${MAIL_BASE_URL:http://localhost:5173}
    ses:
      region:            ${AWS_SES_REGION:ap-northeast-2}
      access-key-id:     ${AWS_SES_ACCESS_KEY_ID:}
      secret-access-key: ${AWS_SES_SECRET_ACCESS_KEY:}
```

- [ ] **Step 2: `local` profile 에 transport: logging override 추가**

`spring.config.activate.on-profile: local` 섹션의 `youthfit:` 블록 아래에:

```yaml
  email:
    transport: ${EMAIL_TRANSPORT:logging}
```

(이미 공통 섹션에서 default 가 logging 이므로 사실상 중복이지만, 명시적 의도를 보이기 위해 둠)

- [ ] **Step 3: `prod` profile 에 transport: ses override 추가**

`spring.config.activate.on-profile: prod` 섹션 끝부분에 `youthfit:` 블록을 추가 (없으면 신규):

```yaml
youthfit:
  email:
    transport: ${EMAIL_TRANSPORT:ses}
```

- [ ] **Step 4: 부팅 검증**

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' &
sleep 15
curl -s http://localhost:8080/actuator/health
kill %1
```

Expected: `{"status":"UP"}` 응답. 부팅 로그에서 `youthfit.email.transport=logging` 분기로 `LoggingEmailSender` 만 활성화되는지 확인 (`SesEmailSender` 빈 등록되지 않음).

> **주의**: 별도 SES 환경변수 미지정 시 `SesEmailSender` 가 활성화되지 않으므로, sandbox 검증 없이도 부팅 가능.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/application.yml
git commit -m "chore(user): youthfit.email.* 환경변수 슬롯 추가 (logging|ses 분기)"
```

---

## Task 11: NotificationScheduleService 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/service/NotificationScheduleService.java`
- Modify: `backend/src/test/java/com/youthfit/user/application/service/NotificationScheduleServiceTest.java`

- [ ] **Step 1: `NotificationScheduleService` 변경**

```java
package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import com.youthfit.user.domain.repository.PolicyNotificationSubscriptionRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.NotificationTargetResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final PolicyNotificationSubscriptionRepository subscriptionRepository;
    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final NotificationDispatchService dispatchService;
    private final EmailSender emailSender;

    /**
     * 메서드 레벨 @Transactional 제거 — SES 외부 IO 동안 DB 커넥션 점유 방지.
     * 상태 전이는 NotificationDispatchService 의 REQUIRES_NEW 메서드들이 담당.
     */
    public void sendDeadlineNotifications() {
        LocalDate today = LocalDate.now();
        List<NotificationSetting> activeSettings = notificationSettingRepository.findAllByEmailEnabled(true);

        for (NotificationSetting setting : activeSettings) {
            Long userId = setting.getUserId();
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("알림 설정 userId={} 에 해당하는 사용자를 찾을 수 없습니다", userId);
                continue;
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }

            List<PolicyNotificationSubscription> subscriptions = subscriptionRepository.findAllByUserId(userId);
            for (PolicyNotificationSubscription subscription : subscriptions) {
                processOnePolicy(setting, user, subscription, today);
            }
        }
    }

    private void processOnePolicy(NotificationSetting setting, User user,
                                  PolicyNotificationSubscription subscription, LocalDate today) {
        Long userId = user.getId();
        Long policyId = subscription.getPolicyId();
        Policy policy = policyRepository.findById(policyId).orElse(null);
        if (policy == null) return;
        if (!NotificationTargetResolver.shouldNotify(policy, setting.getDaysBeforeDeadline(), today)) {
            return;
        }

        NotificationHistory history = dispatchService.reservePending(userId, policyId, NotificationType.DEADLINE);
        if (history == null) {
            log.debug("마감일 알림 PENDING 충돌 — skip userId={} policyId={}", userId, policyId);
            return;
        }

        try {
            emailSender.sendDeadlineNotification(user.getEmail(), policy);
            dispatchService.markSent(history.getId());
            log.info("마감일 알림 발송 완료 userId={} policyId={} historyId={}",
                    userId, policyId, history.getId());
        } catch (EmailSendException e) {
            dispatchService.markFailed(history.getId(), e.getMessage());
            log.error("마감일 알림 발송 실패 userId={} policyId={} historyId={}",
                    userId, policyId, history.getId(), e);
        } catch (Exception e) {
            // dispatchService.markFailed 도 실패한 케이스 등 — PENDING 으로 잔존
            log.error("마감일 알림 처리 중 예외 userId={} policyId={}", userId, policyId, e);
        }
    }
}
```

- [ ] **Step 2: 기존 테스트 갱신 — `NotificationScheduleServiceTest`**

```java
package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import com.youthfit.user.domain.repository.PolicyNotificationSubscriptionRepository;
import com.youthfit.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@DisplayName("NotificationScheduleService")
@ExtendWith(MockitoExtension.class)
class NotificationScheduleServiceTest {

    @InjectMocks
    private NotificationScheduleService notificationScheduleService;

    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private PolicyNotificationSubscriptionRepository subscriptionRepository;
    @Mock private PolicyRepository policyRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationDispatchService dispatchService;
    @Mock private EmailSender emailSender;

    @Nested
    @DisplayName("sendDeadlineNotifications")
    class SendDeadlineNotifications {

        @Test
        @DisplayName("마감 임박 정책에 대해 reservePending → 발송 → markSent 흐름")
        void eligiblePolicy_reservesAndSends() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));
            NotificationHistory pending = NotificationHistory.pending(1L, 10L, NotificationType.DEADLINE);
            ReflectionTestUtils.setField(pending, "id", 999L);

            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(dispatchService.reservePending(1L, 10L, NotificationType.DEADLINE)).willReturn(pending);

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should().sendDeadlineNotification(eq("test@example.com"), eq(policy));
            then(dispatchService).should().markSent(999L);
        }

        @Test
        @DisplayName("reservePending 이 null 이면 발송 안 함 (이미 처리됨)")
        void reservePendingNull_skipsSend() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));

            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(dispatchService.reservePending(1L, 10L, NotificationType.DEADLINE)).willReturn(null);

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("EmailSendException 발생 시 markFailed 호출")
        void emailSendException_callsMarkFailed() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));
            NotificationHistory pending = NotificationHistory.pending(1L, 10L, NotificationType.DEADLINE);
            ReflectionTestUtils.setField(pending, "id", 999L);

            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(dispatchService.reservePending(1L, 10L, NotificationType.DEADLINE)).willReturn(pending);
            willThrow(new EmailSendException("SES 발송 실패: test@example.com", new RuntimeException()))
                    .given(emailSender).sendDeadlineNotification(any(), any());

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(dispatchService).should().markFailed(eq(999L), any());
            then(dispatchService).should(never()).markSent(any());
        }

        @Test
        @DisplayName("사용자 없음 → 건너뛰기")
        void userNotFound_skips() {
            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(new NotificationSetting(1L)));
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            notificationScheduleService.sendDeadlineNotifications();

            then(subscriptionRepository).should(never()).findAllByUserId(any());
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("정책 없음 → 건너뛰기")
        void policyNotFound_skips() {
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L))
                    .willReturn(List.of(new PolicyNotificationSubscription(1L, 10L)));
            given(policyRepository.findById(10L)).willReturn(Optional.empty());

            notificationScheduleService.sendDeadlineNotifications();

            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("마감 먼 정책 → 건너뛰기")
        void farDeadline_skips() {
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(30));
            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L))
                    .willReturn(List.of(new PolicyNotificationSubscription(1L, 10L)));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));

            notificationScheduleService.sendDeadlineNotifications();

            then(dispatchService).should(never()).reservePending(any(), any(), any());
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }
    }

    private User createUser(Long id) {
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_" + id)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Policy createOpenPolicyWithDeadline(Long id, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title("테스트 정책")
                .category(Category.JOBS)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        policy.open();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
```

- [ ] **Step 3: 테스트 실행 (PASS)**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.application.service.NotificationScheduleServiceTest"
```

Expected: PASS — 모든 테스트 통과.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/application/service/NotificationScheduleService.java \
        backend/src/test/java/com/youthfit/user/application/service/NotificationScheduleServiceTest.java
git commit -m "refactor(user): NotificationScheduleService 상태 전이 흐름 + try/catch 격리"
```

---

## Task 12: RecommendationOneDispatcher 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/service/RecommendationOneDispatcher.java`
- Modify: `backend/src/test/java/com/youthfit/user/application/service/RecommendationOneDispatcherTest.java`

- [ ] **Step 1: `RecommendationOneDispatcher` 변경**

```java
package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.BookmarkRepository;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.PolicyRecommender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationOneDispatcher {

    private final UserRepository userRepository;
    private final EligibilityProfileRepository profileRepository;
    private final PolicyRepository policyRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationHistoryRepository historyRepository;
    private final EligibilityService eligibilityService;
    private final NotificationDispatchService dispatchService;
    private final EmailSender emailSender;
    private final PolicyRecommender recommender;

    /**
     * v0: 사용자당 후보 N건 × judgeEligibility/exists 호출 (N+1 패턴).
     * 메서드 레벨 @Transactional 제거 — SES 외부 IO 동안 DB 커넥션 점유 방지.
     * 상태 전이는 NotificationDispatchService 의 REQUIRES_NEW 메서드들이 담당.
     */
    public void dispatchOne(NotificationSetting setting) {
        Long userId = setting.getUserId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        EligibilityProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (!setting.canDispatchRecommendation(profile)) {
            return;
        }

        List<Policy> openPolicies = policyRepository.findAllByStatus(PolicyStatus.OPEN);
        List<Policy> matched = recommender.filterByInterest(setting, openPolicies);

        List<Policy> notSeen = matched.stream()
                .filter(p -> !bookmarkRepository.existsByUserIdAndPolicyId(userId, p.getId()))
                .filter(p -> !historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                        userId, p.getId(), NotificationType.RECOMMENDATION))
                .toList();

        List<Policy> eligible = notSeen.stream()
                .filter(p -> {
                    EligibilityJudgmentResult result = eligibilityService.judgeEligibility(
                            userId, new JudgeEligibilityCommand(p.getId()));
                    return EligibilityResult.LIKELY_ELIGIBLE.name().equals(result.overallResult());
                })
                .toList();

        List<Policy> picks = recommender.sortAndLimit(eligible);
        if (picks.isEmpty()) return;

        // 발송 전에 모든 pick 에 대해 PENDING 행 예약
        List<NotificationHistory> reserved = new ArrayList<>();
        List<Policy> toSend = new ArrayList<>();
        for (Policy p : picks) {
            NotificationHistory h = dispatchService.reservePending(userId, p.getId(), NotificationType.RECOMMENDATION);
            if (h != null) {
                reserved.add(h);
                toSend.add(p);
            }
        }
        if (toSend.isEmpty()) return;   // 모두 이미 처리됨

        try {
            emailSender.sendRecommendationNotification(user.getEmail(), toSend);
            for (NotificationHistory h : reserved) {
                dispatchService.markSent(h.getId());
            }
            log.info("추천 알림 발송 완료 userId={} count={}", userId, toSend.size());
        } catch (EmailSendException e) {
            for (NotificationHistory h : reserved) {
                dispatchService.markFailed(h.getId(), e.getMessage());
            }
            log.error("추천 알림 발송 실패 userId={} count={}", userId, toSend.size(), e);
        } catch (Exception e) {
            log.error("추천 알림 처리 중 예외 userId={}", userId, e);
        }
    }
}
```

- [ ] **Step 2: `RecommendationOneDispatcherTest` 갱신**

기존 9개 테스트의 `historyRepository.save(...)` 검증을 `dispatchService.markSent(...)` / `reservePending` 호출 검증으로 변경. 대표적인 변경점:

(a) Mock 추가:
```java
    @Mock
    private NotificationDispatchService dispatchService;
```

(b) `eligiblePolicy` 류 테스트에 `reservePending` stub 추가:
```java
    NotificationHistory pending = NotificationHistory.pending(1L, 10L, NotificationType.RECOMMENDATION);
    ReflectionTestUtils.setField(pending, "id", 100L);
    given(dispatchService.reservePending(eq(1L), eq(10L), eq(NotificationType.RECOMMENDATION)))
            .willReturn(pending);
```

(c) `then(historyRepository).should(times(N)).save(any())` → `then(dispatchService).should(times(N)).markSent(any())` 로 변경.

전체 갱신본:

```java
package com.youthfit.user.application.service;

import com.youthfit.eligibility.application.dto.command.JudgeEligibilityCommand;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.RegionSidoCode;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.BookmarkRepository;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.PolicyRecommender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("RecommendationOneDispatcher")
@ExtendWith(MockitoExtension.class)
class RecommendationOneDispatcherTest {

    @InjectMocks
    private RecommendationOneDispatcher dispatcher;

    @Mock private UserRepository userRepository;
    @Mock private EligibilityProfileRepository profileRepository;
    @Mock private PolicyRepository policyRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private EligibilityService eligibilityService;
    @Mock private NotificationDispatchService dispatchService;
    @Mock private EmailSender emailSender;
    @Spy  private PolicyRecommender recommender = new PolicyRecommender();

    private NotificationSetting enabledSetting;
    private EligibilityProfile profileFilled;

    @BeforeEach
    void setUp() {
        enabledSetting = new NotificationSetting(1L);
        enabledSetting.updateSetting(true, 7, true);
        enabledSetting.replaceInterestCategories(Set.of(Category.JOBS));
        enabledSetting.replaceInterestRegions(Set.of(RegionSidoCode.SEOUL));

        profileFilled = EligibilityProfile.empty(1L);
        profileFilled.changeLegalDongCode("1100000000");
        profileFilled.changeAge(28);
    }

    @Test
    @DisplayName("이메일 미등록 사용자는 발송하지 않는다")
    void noEmail_skips() {
        User user = User.builder().nickname("x").authProvider(AuthProvider.KAKAO).providerId("k_1").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        dispatcher.dispatchOne(enabledSetting);

        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("토글 OFF 사용자는 발송하지 않는다")
    void toggleOff_skips() {
        NotificationSetting offSetting = new NotificationSetting(1L);
        offSetting.updateSetting(true, 7, false);
        offSetting.replaceInterestCategories(Set.of(Category.JOBS));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));

        dispatcher.dispatchOne(offSetting);

        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("적합도 프로필 미입력자는 발송하지 않는다")
    void noProfile_skips() {
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.empty());

        dispatcher.dispatchOne(enabledSetting);

        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("후보 정책이 없으면 발송하지 않는다")
    void noCandidates_skips() {
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of());

        dispatcher.dispatchOne(enabledSetting);

        then(emailSender).should(never()).sendRecommendationNotification(any(), any());
    }

    @Test
    @DisplayName("LIKELY_ELIGIBLE 정책만 통과하고 발송 후 markSent 호출")
    void onlyLikelyEligible_passed_andMarkSent() {
        Policy eligible = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        Policy uncertain = createPolicy(11L, Category.JOBS, "11", LocalDate.now().plusDays(6));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(eligible, uncertain));
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willAnswer(inv -> {
                    JudgeEligibilityCommand cmd = inv.getArgument(1);
                    String r = cmd.policyId() == 10L
                            ? EligibilityResult.LIKELY_ELIGIBLE.name()
                            : EligibilityResult.UNCERTAIN.name();
                    return new EligibilityJudgmentResult(cmd.policyId(), "t", r, null, null, "");
                });
        givenReservePendingReturnsHistory();

        dispatcher.dispatchOne(enabledSetting);

        then(emailSender).should().sendRecommendationNotification(eq("test@example.com"), any());
        then(dispatchService).should(times(1)).markSent(anyLong());
    }

    @Test
    @DisplayName("EmailSendException 시 reserved 모든 행을 markFailed")
    void emailSendException_marksAllFailed() {
        Policy eligible = createPolicy(10L, Category.JOBS, "11", LocalDate.now().plusDays(5));
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(List.of(eligible));
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willReturn(new EligibilityJudgmentResult(
                        10L, "t", EligibilityResult.LIKELY_ELIGIBLE.name(), null, null, ""));
        givenReservePendingReturnsHistory();
        willThrow(new EmailSendException("SES 실패", new RuntimeException()))
                .given(emailSender).sendRecommendationNotification(any(), any());

        dispatcher.dispatchOne(enabledSetting);

        then(dispatchService).should(times(1)).markFailed(anyLong(), any());
        then(dispatchService).should(never()).markSent(any());
    }

    @Test
    @DisplayName("추천은 5건으로 절단된다 (markSent 5회 호출)")
    void picksLimitedToFive() {
        List<Policy> openPolicies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            openPolicies.add(createPolicy((long) (10 + i), Category.JOBS, "11", LocalDate.now().plusDays(2 + i)));
        }
        User user = createUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(1L)).willReturn(Optional.of(profileFilled));
        given(policyRepository.findAllByStatus(PolicyStatus.OPEN)).willReturn(openPolicies);
        given(bookmarkRepository.existsByUserIdAndPolicyId(eq(1L), anyLong())).willReturn(false);
        given(historyRepository.existsByUserIdAndPolicyIdAndNotificationType(
                eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION))).willReturn(false);
        given(eligibilityService.judgeEligibility(eq(1L), any(JudgeEligibilityCommand.class)))
                .willAnswer(inv -> {
                    JudgeEligibilityCommand cmd = inv.getArgument(1);
                    return new EligibilityJudgmentResult(
                            cmd.policyId(), "t", EligibilityResult.LIKELY_ELIGIBLE.name(), null, null, "");
                });
        givenReservePendingReturnsHistory();

        dispatcher.dispatchOne(enabledSetting);

        then(emailSender).should().sendRecommendationNotification(eq("test@example.com"), any());
        then(dispatchService).should(times(5)).markSent(anyLong());
    }

    // ── 헬퍼 ──

    /** reservePending 호출 시 unique id 의 새 PENDING history 를 반환하도록 stub */
    private void givenReservePendingReturnsHistory() {
        AtomicLong idSeq = new AtomicLong(1000L);
        given(dispatchService.reservePending(eq(1L), anyLong(), eq(NotificationType.RECOMMENDATION)))
                .willAnswer(inv -> {
                    Long policyId = inv.getArgument(1);
                    NotificationHistory h = NotificationHistory.pending(1L, policyId, NotificationType.RECOMMENDATION);
                    ReflectionTestUtils.setField(h, "id", idSeq.incrementAndGet());
                    return h;
                });
    }

    private User createUser(Long id) {
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_" + id)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Policy createPolicy(Long id, Category category, String regionCode, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title("정책-" + id)
                .category(category)
                .regionCode(regionCode)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        ReflectionTestUtils.setField(policy, "createdAt", LocalDateTime.now());
        return policy;
    }
}
```

- [ ] **Step 3: 전체 테스트 실행 (PASS)**

```bash
cd backend && ./gradlew test
```

Expected: 모든 테스트 통과. 단, `NotificationDispatchServiceIntegrationTest` 는 PG 컨테이너 필요.

- [ ] **Step 4: `./gradlew build` 통과 확인**

```bash
cd backend && ./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/application/service/RecommendationOneDispatcher.java \
        backend/src/test/java/com/youthfit/user/application/service/RecommendationOneDispatcherTest.java
git commit -m "refactor(user): RecommendationOneDispatcher 일괄 PENDING 예약 → markSent/markFailed 흐름"
```

---

## Task 13: OPS.md 환경변수 슬롯 갱신

**Files:**
- Modify: `docs/OPS.md`

- [ ] **Step 1: "환경 변수 범주" 섹션 갱신 + "이메일 발송 (SES)" 섹션 신규 추가**

기존 `docs/OPS.md` 의 `## 환경 변수 범주` 아래 항목 그대로 두고, 하단에 다음 섹션을 추가 (Q&A 의미 캐시 섹션과 같은 위치 패턴):

```markdown
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
```

- [ ] **Step 2: 커밋**

```bash
git add docs/OPS.md
git commit -m "docs(ops): 이메일 발송(SES) 환경변수 슬롯 + 운영 절차 추가"
```

---

## Task 14: PRD 07-notification.md 구현 상태 갱신

**Files:**
- Modify: `docs/prd/07-notification.md`

- [ ] **Step 1: 헤더의 "구현 상태" 갱신**

`docs/prd/07-notification.md` 의 헤더 부분:

```markdown
> **구현 상태**: 미구현
```

→

```markdown
> **구현 상태**: 부분 구현 (백엔드 발송 인프라 완료, SES sandbox 해제 + 도메인 검증 후속)
```

- [ ] **Step 2: 커밋**

```bash
git add docs/prd/07-notification.md
git commit -m "docs(prd): 알림(F-09) 구현 상태 갱신 — 발송 인프라 완료 표기"
```

---

## Task 15: 운영 런북 작성

**Files:**
- Create: `docs/superpowers/operations/2026-05-05-email-transport-runbook.md`

- [ ] **Step 1: 런북 작성**

```markdown
# Email Transport 운영 런북 (2026-05-05)

> AWS SES 어댑터 + Thymeleaf 본문 + NotificationHistory 상태 전이 도입 후 운영 가이드.

## 활성 모드 확인

```bash
# 부팅 후 health check
curl -s http://localhost:8080/actuator/health

# 어댑터 빈 분기 확인 (로그)
grep -E "LoggingEmailSender|SesEmailSender" backend/logs/*.log | head -5
```

## 환경변수 점검

```bash
# 운영 컨테이너에서 키 채워졌는지만 확인 (값은 출력 X)
docker compose exec backend printenv | grep -E "EMAIL_TRANSPORT|MAIL_FROM|AWS_SES" | sed 's/=.*/=***/'
```

기대값:
- `EMAIL_TRANSPORT=ses`
- `MAIL_FROM_ADDRESS=***`
- `AWS_SES_ACCESS_KEY_ID=***`

## SES sandbox 해제 신청 (베타/공개 직전)

1. AWS 콘솔 → SES → Account dashboard → "Request production access"
2. 사용 사례: "Transactional emails — deadline reminders and weekly policy recommendations"
3. 일일 발송량 추정: 사용자 수 × 7 (마감 1일/추천 1주)
4. unsubscribe 메커니즘 설명: 풋터 설정 페이지 링크

## 발송 실패 모니터링

```bash
# 최근 1시간 SES 발송 실패 카운트
docker compose logs backend --since 1h | grep "SES 발송 실패" | wc -l

# 최근 1시간 markFailed 호출
docker compose logs backend --since 1h | grep "markFailed" | wc -l
```

## DB 상태 확인

```sql
-- 상태별 분포
SELECT status, notification_type, COUNT(*) FROM notification_history GROUP BY status, notification_type;

-- 최근 24h FAILED 원인
SELECT failed_at, failure_reason FROM notification_history
WHERE status = 'FAILED' AND failed_at > NOW() - INTERVAL '24 hours'
ORDER BY failed_at DESC LIMIT 20;

-- stale PENDING (24h 초과)
SELECT id, user_id, policy_id, notification_type, created_at FROM notification_history
WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '24 hours';
```

## 영구 실패 행 정리

운영자 판단으로 일시적 실패 였다고 보면:

```sql
-- 7일 이상 된 FAILED 행 정리 → 다음 cron 에서 재시도됨
DELETE FROM notification_history WHERE status = 'FAILED' AND failed_at < NOW() - INTERVAL '7 days';

-- 24h 이상 PENDING 정리 (JVM crash 등으로 잔존)
DELETE FROM notification_history WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '24 hours';
```

## dry-run 절차

```bash
# 1. logging 모드로 띄워서 본문 확인
EMAIL_TRANSPORT=logging docker compose up -d --build backend

# 2. 스케줄러 트리거 대기 또는 수동 호출 (cron 시각 외 직접 테스트 시)
#    NotificationScheduler 의 cron 문자열 확인

# 3. 로그에서 렌더된 HTML 미리보기
docker compose logs backend | grep "이메일 발송"
```

## 비상 정지

```bash
# SES 호출만 즉시 중단 (logging 으로 fallback)
docker compose exec backend printenv EMAIL_TRANSPORT
EMAIL_TRANSPORT=logging docker compose up -d backend
```

## 후속 작업 (backlog)

- SES sandbox 해제 + 도메인 확보 (DNS 작업)
- List-Unsubscribe 헤더 + one-click unsubscribe
- stale PENDING 자동 cleanup cron
- FAILED 자동 재시도 정책
- Actuator/Prometheus metrics 노출
```

- [ ] **Step 2: 커밋**

```bash
git add docs/superpowers/operations/2026-05-05-email-transport-runbook.md
git commit -m "docs(ops): 이메일 발송 운영 런북 추가"
```

---

## Task 16: 최종 빌드 & 테스트 검증

- [ ] **Step 1: 전체 빌드**

```bash
cd backend && ./gradlew clean build
```

Expected: BUILD SUCCESSFUL. JaCoCo 리포트 생성됨.

- [ ] **Step 2: 신규 클래스 커버리지 확인**

```bash
open backend/build/reports/jacoco/test/html/index.html
```

확인 대상:
- `NotificationEmailRenderer`: 라인 90%+
- `SesEmailSender`: 라인 90%+
- `NotificationDispatchService`: 라인 90%+
- `NotificationHistory` 신규 메서드: 라인 90%+

- [ ] **Step 3: 부팅 검증 (logging 모드)**

```bash
docker compose up -d backend
sleep 15
docker compose logs backend | tail -50
```

Expected: 부팅 성공, `LoggingEmailSender` 만 활성화 (`SesEmailSender` 빈 등록 안 됨).

- [ ] **Step 4: PR 분할 검토**

이 plan 의 모든 커밋을 단일 PR 로 묶거나, 다음 두 개로 분할:

- **PR-A** (백엔드 인프라): Task 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 — 코드 변경
- **PR-B** (문서): Task 13, 14, 15 — OPS / PRD / 런북 갱신

전체 단일 PR 권장 (모든 변경이 한 사이클로 묶여 있음).

---

## PR 본문 템플릿

```markdown
## Summary

- AWS SES 어댑터 추가로 stub 이던 이메일 발송 경로를 운영급으로 확장
- Thymeleaf 본문 렌더링 분리 (`NotificationEmailRenderer`)
- `NotificationHistory` 상태 전이(PENDING/SENT/FAILED) 도입으로 partial failure 시 중복 발송 차단
- 메서드 레벨 `@Transactional` 제거 + REQUIRES_NEW 분리

## Test plan

- [ ] `./gradlew build` 통과
- [ ] `NotificationDispatchServiceIntegrationTest` PG 컨테이너에서 통과
- [ ] `EMAIL_TRANSPORT=logging` 로 부팅 → 스케줄러 트리거 → 렌더된 HTML 로그 확인
- [ ] (운영자) `.env` 채운 후 `EMAIL_TRANSPORT=ses` 로 전환 → 검증된 수신자 1명에게 dry-run 발송
- [ ] (운영자) `notification_history.status` 분포 SQL 확인
- [ ] (운영자) 운영 PG 에 마이그레이션 SQL 적용 후 ddl-auto: validate 부팅 통과

## 후속 사이클

- SES sandbox 해제 + 도메인 확보 (next-steps 에 명시)
- List-Unsubscribe 헤더 + one-click unsubscribe
```
