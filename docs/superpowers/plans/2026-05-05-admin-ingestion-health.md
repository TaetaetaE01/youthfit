# 어드민 Ingestion 헬스 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `IngestionService.receivePolicy(...)` 흐름에 try/catch/finally 적재 hook 을 추가해 정책 수신/정규화 결과(성공/실패/중복)를 `IngestionRunLog` 단위 집계와 `IngestionItemFailure` 개별 행으로 적재. 어드민에서 KPI/일자별 stacked bar/원천별 테이블/실패 리스트 조회 + 단건 재처리 트리거.

**Architecture:**
- 적재: `IngestionService.receivePolicy(IngestPolicyCommand)` 메서드를 try/catch/finally 로 감싸서 (a) 성공/실패/중복 카운트 → `IngestionRunLog`, (b) 실패 시 raw_payload + 사유 → `IngestionItemFailure`. 동기 적재 (호출당 row 1개씩)
- 중복 판정: 기존 `PolicyIngestionResult.isNew == false` → `duplicate_count++`
- source 식별: `IngestPolicyCommand.sourceType` 활용 (없으면 `unknown`)
- 재처리: `RetryFailedIngestionItemUseCase` — admin → ingestion 단방향 의존, raw_payload 살아있을 때만
- Redact 스케줄러: 매일 03:00 KST `raw_payload` 7일 redact + `IngestionItemFailure` 30일 행 삭제
- Admin: `/api/v1/admin/ingestion/**` 7 엔드포인트 + 프론트 메인/실패 상세 2 페이지

**Tech Stack:** Java 21 + Spring Boot 4.0.5, Spring Data JPA, PostgreSQL (JSONB), Spring Scheduling, JUnit 5, Mockito, MockMvc + `@WebMvcTest`. Frontend: React + Vite + Vitest + RTL + Recharts + Tailwind.

---

## Decisions Frozen From Spec

| # | 결정 |
|---|---|
| 1 | `IngestionService.receivePolicy(...)` try/catch/finally hook (동기) |
| 2 | `IngestionRunLog` (run 집계) + `IngestionItemFailure` (실패 단건) 2 테이블 분리 |
| 3 | `raw_payload` JSONB, 7일 redact (SHA-256 hash) + 30일 행 삭제 |
| 4 | source 식별: `IngestPolicyCommand.sourceType` (없으면 `unknown`) |
| 5 | 중복 판정: 기존 `PolicyIngestionResult.isNew == false` → `duplicate_count` |
| 6 | 재처리: `RetryFailedIngestionItemUseCase` (admin → ingestion 단방향) |
| 7 | 단건 재처리만. 일괄 재처리 X |
| 8 | "마지막 수신 없음" 임계 24h 고정 |

## File Structure

### Backend — 신규
- `ingestion/domain/model/IngestionRunLog.java` — JPA 엔티티
- `ingestion/domain/model/IngestionItemFailure.java` — JPA 엔티티
- `ingestion/domain/model/FailureReason.java` — enum
- `ingestion/domain/repository/IngestionRunLogRepository.java`
- `ingestion/domain/repository/IngestionItemFailureRepository.java`
- `ingestion/application/service/RetryFailedIngestionItemUseCase.java` — 재처리 use case
- `ingestion/infrastructure/scheduler/IngestionRedactScheduler.java`
- `admin/presentation/controller/AdminIngestionApi.java`
- `admin/presentation/controller/AdminIngestionController.java`
- `admin/application/service/AdminIngestionService.java`
- 응답 DTO 7종 (`IngestionKpi/DailyStats/SourceSummary/StaleSource/FailureSummary/FailureDetail/Retry Response`)
- `backend/src/main/resources/sql/2026-05-05-ingestion-health.sql`

### Backend — 수정
- `ingestion/application/service/IngestionService.java` — `receivePolicy` 에 try/catch/finally hook
- `backend/src/main/resources/application.yml` — 신규 설정 키 3개

### Frontend — 신규
- `frontend/src/apis/admin.ingestion.api.ts`
- `frontend/src/pages/admin/AdminIngestionPage.tsx`
- `frontend/src/pages/admin/AdminIngestionFailureDetailPage.tsx`
- `frontend/src/components/admin/ingestion/StaleSourceBanner.tsx`
- `frontend/src/components/admin/ingestion/IngestionKpiSection.tsx`
- `frontend/src/components/admin/ingestion/IngestionDailyChart.tsx`
- `frontend/src/components/admin/ingestion/SourceTable.tsx`
- `frontend/src/components/admin/ingestion/FailureTable.tsx`
- `frontend/src/components/admin/ingestion/FailureReasonBadge.tsx`
- `frontend/src/components/admin/ingestion/RetryConfirmModal.tsx`
- `frontend/src/hooks/useAdminIngestion.ts`
- 컴포넌트별 `__tests__/*.test.tsx`

### Frontend — 수정
- `frontend/src/components/layout/AdminSidebar.tsx` — `/admin/ingestion` 항목 추가
- 라우터 — `/admin/ingestion`, `/admin/ingestion/failures/:id` 등록

---

# Stage A — 도메인 모델 + 마이그레이션

## Task A1: `FailureReason` enum

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/model/FailureReason.java`

- [ ] **Step 1: enum 작성**

```java
package com.youthfit.ingestion.domain.model;

public enum FailureReason {
    VALIDATION,
    PARSING,
    MAPPING,
    DEDUPLICATION_CONFLICT,
    OTHER;

    /**
     * 예외 타입을 분류 enum 으로 매핑.
     * receivePolicy 에서 catch 한 exception 을 분류한다.
     */
    public static FailureReason classify(Throwable t) {
        if (t == null) return OTHER;
        Class<?> c = t.getClass();
        String name = c.getName();
        if (name.contains("IllegalArgumentException")) return VALIDATION;
        if (name.contains("Validation") || name.contains("Constraint")) return VALIDATION;
        if (name.contains("Json") || name.contains("Parse")) return PARSING;
        if (name.contains("Mapping") || name.contains("Conversion")) return MAPPING;
        if (name.contains("Duplicate") || name.contains("UniqueConstraint")) return DEDUPLICATION_CONFLICT;
        // chain 조사 (cause)
        Throwable cause = t.getCause();
        if (cause != null && cause != t) return classify(cause);
        return OTHER;
    }
}
```

- [ ] **Step 2: 단위 테스트**

**File:** `backend/src/test/java/com/youthfit/ingestion/domain/model/FailureReasonTest.java`

```java
package com.youthfit.ingestion.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FailureReasonTest {
    @Test
    void IllegalArgumentException_은_VALIDATION_으로_분류된다() {
        assertThat(FailureReason.classify(new IllegalArgumentException("bad"))).isEqualTo(FailureReason.VALIDATION);
    }
    @Test
    void RuntimeException_은_OTHER_로_분류된다() {
        assertThat(FailureReason.classify(new RuntimeException("x"))).isEqualTo(FailureReason.OTHER);
    }
    @Test
    void cause_체인을_따라_분류된다() {
        var inner = new IllegalArgumentException("bad");
        var outer = new RuntimeException("wrap", inner);
        assertThat(FailureReason.classify(outer)).isEqualTo(FailureReason.VALIDATION);
    }
    @Test
    void null_은_OTHER() {
        assertThat(FailureReason.classify(null)).isEqualTo(FailureReason.OTHER);
    }
}
```

- [ ] **Step 3: 테스트 실행 + commit**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.domain.model.FailureReasonTest"`
Expected: 4 tests PASS

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/model/FailureReason.java \
        backend/src/test/java/com/youthfit/ingestion/domain/model/FailureReasonTest.java
git commit -m "feat(ingestion): FailureReason enum + 예외 분류 헬퍼"
```

---

## Task A2: `IngestionRunLog` 엔티티

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/model/IngestionRunLog.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.youthfit.ingestion.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(
    name = "ingestion_run_log",
    indexes = {
        @Index(name = "idx_ingestion_run_log_received_at", columnList = "received_at DESC"),
        @Index(name = "idx_ingestion_run_log_source_received_at", columnList = "source, received_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "received_count", nullable = false)
    private int receivedCount;

    @Column(name = "normalized_success_count", nullable = false)
    private int normalizedSuccessCount;

    @Column(name = "normalized_failure_count", nullable = false)
    private int normalizedFailureCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IngestionRunLog success(String source, Instant start, Instant end, boolean isDuplicate) {
        IngestionRunLog log = new IngestionRunLog();
        log.source = source;
        log.receivedCount = 1;
        log.normalizedSuccessCount = isDuplicate ? 0 : 1;
        log.normalizedFailureCount = 0;
        log.duplicateCount = isDuplicate ? 1 : 0;
        log.receivedAt = start;
        log.processedAt = end;
        log.durationMs = (int) Math.max(0, java.time.Duration.between(start, end).toMillis());
        log.createdAt = Instant.now();
        return log;
    }

    public static IngestionRunLog failure(String source, Instant start, Instant end) {
        IngestionRunLog log = new IngestionRunLog();
        log.source = source;
        log.receivedCount = 1;
        log.normalizedSuccessCount = 0;
        log.normalizedFailureCount = 1;
        log.duplicateCount = 0;
        log.receivedAt = start;
        log.processedAt = end;
        log.durationMs = (int) Math.max(0, java.time.Duration.between(start, end).toMillis());
        log.createdAt = Instant.now();
        return log;
    }
}
```

- [ ] **Step 2: 컴파일 확인 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/domain/model/IngestionRunLog.java
git commit -m "feat(ingestion): IngestionRunLog 엔티티 + 정적 팩토리 success/failure"
```

---

## Task A3: `IngestionItemFailure` 엔티티

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/model/IngestionItemFailure.java`

- [ ] **Step 1: 엔티티 작성 (JSONB 타입은 Hibernate 6 기본 매핑 활용)**

```java
package com.youthfit.ingestion.domain.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@Table(
    name = "ingestion_item_failure",
    indexes = {
        @Index(name = "idx_ingestion_item_failure_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_ingestion_item_failure_source_reason_at",
               columnList = "source, failure_reason, created_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionItemFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_log_id")
    private Long runLogId;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "source_item_id", length = 120)
    private String sourceItemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "raw_payload_hash", length = 64)
    private String rawPayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", nullable = false, length = 30)
    private FailureReason failureReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_retried_at")
    private Instant lastRetriedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IngestionItemFailure of(
            Long runLogId, String source, String sourceItemId,
            String rawPayload, FailureReason reason, String errorMessage) {
        IngestionItemFailure f = new IngestionItemFailure();
        f.runLogId = runLogId;
        f.source = source;
        f.sourceItemId = sourceItemId;
        f.rawPayload = rawPayload;
        f.failureReason = reason;
        f.errorMessage = errorMessage;
        f.retryCount = 0;
        f.createdAt = Instant.now();
        return f;
    }

    public void markRetried() {
        this.retryCount++;
        this.lastRetriedAt = Instant.now();
    }

    public void redactPayload(String hash) {
        this.rawPayload = null;
        this.rawPayloadHash = hash;
    }

    public boolean isPayloadAvailable() {
        return rawPayload != null && !rawPayload.isBlank();
    }
}
```

- [ ] **Step 2: 의존성 확인**

`io.hypersistence.utils.hibernate.type.json.JsonBinaryType` 가 backend 빌드에 있는지 확인:

Run: `grep -r "hypersistence-utils" backend/build.gradle*`
Expected: 있어야 함. 없으면 다음을 `backend/build.gradle.kts` (또는 `build.gradle`) `dependencies` 블록에 추가:

```kotlin
implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.3")
```

> 또는 단순화: `@JdbcTypeCode(SqlTypes.JSON)` 만으로 Hibernate 6 가 jsonb 매핑. 이 경우 hypersistence-utils import 제거 가능. **두 가지 중 build.gradle 의 현재 상태에 맞는 방식 선택.** 본 plan 은 JdbcTypeCode 방식만 사용 (간단).

위 코드의 `import io.hypersistence...` 는 **삭제** 하고 `@JdbcTypeCode(SqlTypes.JSON)` 만 남긴다.

- [ ] **Step 3: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/domain/model/IngestionItemFailure.java
git commit -m "feat(ingestion): IngestionItemFailure 엔티티 (JSONB raw_payload + redact 메서드)"
```

---

## Task A4: 두 Repository

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/repository/IngestionRunLogRepository.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/repository/IngestionItemFailureRepository.java`

- [ ] **Step 1: `IngestionRunLogRepository`**

```java
package com.youthfit.ingestion.domain.repository;

import com.youthfit.ingestion.domain.model.IngestionRunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface IngestionRunLogRepository extends JpaRepository<IngestionRunLog, Long> {

    /** KPI: 기간 합계. */
    @Query(value = """
            SELECT COALESCE(SUM(received_count), 0) AS received,
                   COALESCE(SUM(normalized_success_count), 0) AS success,
                   COALESCE(SUM(normalized_failure_count), 0) AS failure,
                   COALESCE(SUM(duplicate_count), 0) AS duplicate
            FROM ingestion_run_log
            WHERE received_at >= :from AND received_at < :to
            """, nativeQuery = true)
    Map<String, Object> sumBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** 일자별·source 별 stacked bar 용 집계. */
    @Query(value = """
            SELECT (received_at AT TIME ZONE 'Asia/Seoul')::date AS day,
                   source,
                   SUM(normalized_success_count) AS success,
                   SUM(normalized_failure_count) AS failure,
                   SUM(duplicate_count) AS duplicate
            FROM ingestion_run_log
            WHERE received_at >= :from AND received_at < :to
            GROUP BY day, source
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> dailyStats(@Param("from") Instant from, @Param("to") Instant to);

    /** 원천별 마지막 수신 시각 + 7일 합계 + 실패율. */
    @Query(value = """
            SELECT source,
                   MAX(received_at) AS last_received,
                   COALESCE(SUM(received_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0) AS week_received,
                   CASE
                     WHEN COALESCE(SUM(received_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0) = 0 THEN 0.0
                     ELSE COALESCE(SUM(normalized_failure_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0)::numeric
                          / COALESCE(SUM(received_count) FILTER (WHERE received_at >= :sevenDaysAgo), 0)::numeric
                   END AS failure_rate
            FROM ingestion_run_log
            GROUP BY source
            ORDER BY last_received DESC
            """, nativeQuery = true)
    List<Object[]> sourceSummaries(@Param("sevenDaysAgo") Instant sevenDaysAgo);

    /** 24h (또는 임계) 미수신 source 식별. */
    @Query(value = """
            SELECT source, MAX(received_at) AS last_received
            FROM ingestion_run_log
            GROUP BY source
            HAVING MAX(received_at) < :threshold
            """, nativeQuery = true)
    List<Object[]> staleSources(@Param("threshold") Instant threshold);
}
```

- [ ] **Step 2: `IngestionItemFailureRepository`**

```java
package com.youthfit.ingestion.domain.repository;

import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IngestionItemFailureRepository extends JpaRepository<IngestionItemFailure, Long> {

    @Query("""
            SELECT f FROM IngestionItemFailure f
             WHERE (:source IS NULL OR f.source = :source)
               AND (:reason IS NULL OR f.failureReason = :reason)
               AND (:from IS NULL OR f.createdAt >= :from)
               AND (:to IS NULL OR f.createdAt < :to)
             ORDER BY f.createdAt DESC
            """)
    Page<IngestionItemFailure> search(
            @Param("source") String source,
            @Param("reason") FailureReason reason,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT f FROM IngestionItemFailure f WHERE f.rawPayload IS NOT NULL AND f.createdAt < :before")
    List<IngestionItemFailure> findPayloadsToRedact(@Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM IngestionItemFailure f WHERE f.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
```

- [ ] **Step 3: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/ingestion/domain/repository/Ingestion*.java
git commit -m "feat(ingestion): IngestionRunLog/ItemFailureRepository + 집계 쿼리"
```

---

## Task A5: SQL 마이그레이션

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-05-ingestion-health.sql`

- [ ] **Step 1: DDL 작성**

```sql
-- ingestion_run_log: receivePolicy 1회 = 1 row (Spec 5)
CREATE TABLE ingestion_run_log (
    id                          BIGSERIAL PRIMARY KEY,
    source                      VARCHAR(40) NOT NULL,
    received_count              INT NOT NULL DEFAULT 0,
    normalized_success_count    INT NOT NULL DEFAULT 0,
    normalized_failure_count    INT NOT NULL DEFAULT 0,
    duplicate_count             INT NOT NULL DEFAULT 0,
    received_at                 TIMESTAMP NOT NULL,
    processed_at                TIMESTAMP NOT NULL,
    duration_ms                 INT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP NOT NULL
);
CREATE INDEX idx_ingestion_run_log_received_at ON ingestion_run_log (received_at DESC);
CREATE INDEX idx_ingestion_run_log_source_received_at ON ingestion_run_log (source, received_at DESC);

COMMENT ON TABLE ingestion_run_log IS 'IngestionService.receivePolicy 호출 단위 집계 (Spec 5)';

-- ingestion_item_failure: 정규화 실패 단건 (Spec 5)
CREATE TABLE ingestion_item_failure (
    id                  BIGSERIAL PRIMARY KEY,
    run_log_id          BIGINT,
    source              VARCHAR(40) NOT NULL,
    source_item_id      VARCHAR(120),
    raw_payload         JSONB,
    raw_payload_hash    VARCHAR(64),
    failure_reason      VARCHAR(30) NOT NULL,
    error_message       TEXT,
    retry_count         INT NOT NULL DEFAULT 0,
    last_retried_at     TIMESTAMP,
    created_at          TIMESTAMP NOT NULL
);
CREATE INDEX idx_ingestion_item_failure_created_at ON ingestion_item_failure (created_at DESC);
CREATE INDEX idx_ingestion_item_failure_source_reason_at
    ON ingestion_item_failure (source, failure_reason, created_at DESC);

COMMENT ON TABLE ingestion_item_failure IS '정규화 실패 단건 (Spec 5 — raw_payload 7일 후 hash redact, 30일 후 삭제)';
COMMENT ON COLUMN ingestion_item_failure.failure_reason IS 'VALIDATION | PARSING | MAPPING | DEDUPLICATION_CONFLICT | OTHER';
```

- [ ] **Step 2: 마이그레이션 적용**

Run:
```bash
docker compose exec -T postgres psql -U postgres -d youthfit < backend/src/main/resources/sql/2026-05-05-ingestion-health.sql
```

Expected: `CREATE TABLE × 2`, `CREATE INDEX × 4`, `COMMENT × 3`

- [ ] **Step 3: 테이블 확인**

```bash
docker compose exec -T postgres psql -U postgres -d youthfit -c "\d ingestion_run_log"
docker compose exec -T postgres psql -U postgres -d youthfit -c "\d ingestion_item_failure"
```

Expected: 컬럼/인덱스 정상

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/sql/2026-05-05-ingestion-health.sql
git commit -m "feat(ingestion): ingestion_run_log + ingestion_item_failure 테이블 DDL"
```

---

# Stage B — IngestionService 적재 hook

## Task B1: `IngestionService.receivePolicy` 수정 — try/catch/finally hook

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: 의존성 추가 + import**

추가할 imports:

```java
import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.model.IngestionRunLog;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
```

`IngestionService` 의 필드에 두 repository 추가:

```java
    private final PolicyIngestionService policyIngestionService;
    private final ObjectMapper objectMapper;
    private final PolicyPeriodExtractor policyPeriodExtractor;
    private final PolicyPeriodLlmProvider policyPeriodLlmProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentDownloadService attachmentDownloadService;
    private final CostGuard costGuard;
    private final IngestionRunLogRepository ingestionRunLogRepository;
    private final IngestionItemFailureRepository ingestionItemFailureRepository;
```

- [ ] **Step 2: `receivePolicy` 본문을 try/catch/finally 로 감싼다**

기존 `receivePolicy` 본문을 다음으로 교체:

```java
public IngestPolicyResult receivePolicy(IngestPolicyCommand command) {
    Instant runStart = Instant.now();
    String sourceLabel = resolveSourceLabel(command);
    boolean failed = false;
    boolean duplicate = false;

    try {
        Category category = mapCategory(command.category());
        SourceType sourceType = resolveSourceType(command.sourceType());
        String rawJson = serialize(command);
        String sourceHash = sha256(rawJson);
        String externalId = command.externalId() != null && !command.externalId().isBlank()
                ? command.externalId()
                : command.sourceUrl();
        String summary = command.summary() != null && !command.summary().isBlank()
                ? command.summary()
                : command.body();

        Sections sections = parseSections(command.body());
        PolicyPeriod period = resolvePeriod(command);

        RegisterPolicyCommand registerCommand = new RegisterPolicyCommand(
                command.title(), summary, command.body(),
                sections.supportTarget(), sections.selectionCriteria(), sections.supportContent(),
                command.organization(), command.contact(), category, command.region(),
                period.start(), period.end(),
                command.referenceYear(), command.supportCycle(), command.provideType(),
                toSet(command.lifeTags()), toSet(command.themeTags()), toSet(command.targetTags()),
                mapAttachments(command.attachments()),
                mapReferenceSites(command.referenceSites()),
                mapApplyMethods(command.applyMethods()),
                sourceType, externalId, command.sourceUrl(), rawJson, sourceHash
        );

        PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
        duplicate = !ingestionResult.isNew();
        eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
        triggerAttachmentDownload(ingestionResult.policyId());

        return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
    } catch (RuntimeException e) {
        failed = true;
        recordFailure(command, sourceLabel, e);
        throw e;
    } finally {
        Instant runEnd = Instant.now();
        IngestionRunLog runLog = failed
                ? IngestionRunLog.failure(sourceLabel, runStart, runEnd)
                : IngestionRunLog.success(sourceLabel, runStart, runEnd, duplicate);
        try {
            ingestionRunLogRepository.save(runLog);
        } catch (Exception e) {
            log.warn("ingestion run log 적재 실패 (정상 흐름 진행): source={}", sourceLabel, e);
        }
    }
}
```

- [ ] **Step 3: 헬퍼 메서드 2 개 추가 (`IngestionService` 내부)**

```java
private String resolveSourceLabel(IngestPolicyCommand command) {
    if (command == null) return "unknown";
    if (command.sourceType() != null && !command.sourceType().isBlank()) {
        return command.sourceType();
    }
    return "unknown";
}

private void recordFailure(IngestPolicyCommand command, String sourceLabel, Throwable t) {
    try {
        FailureReason reason = FailureReason.classify(t);
        String rawPayload = safeSerialize(command);
        String message = t.getMessage() == null ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + t.getMessage();
        if (message.length() > 4000) message = message.substring(0, 4000);
        ingestionItemFailureRepository.save(IngestionItemFailure.of(
                null,                       // run_log_id 는 finally 시점에 저장돼서 모름 — null
                sourceLabel,
                command == null ? null : command.externalId(),
                rawPayload,
                reason,
                message
        ));
    } catch (Exception inner) {
        log.warn("ingestion failure 적재 실패 (정상 흐름 진행)", inner);
    }
}

private String safeSerialize(IngestPolicyCommand command) {
    if (command == null) return null;
    try {
        return objectMapper.writeValueAsString(command);
    } catch (Exception e) {
        return "{\"_serializeError\":\"" + e.getClass().getSimpleName() + "\"}";
    }
}
```

> 기존 `serialize` 는 throw 가능 → 적재 흐름에서는 실패해도 안 끊기는 `safeSerialize` 사용.

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java
git commit -m "feat(ingestion): receivePolicy try/catch/finally hook — RunLog/ItemFailure 적재"
```

---

## Task B2: 통합 테스트 — receivePolicy 성공/실패/중복 적재

**Files:**
- Create: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceHealthHookTest.java`

- [ ] **Step 1: 통합 테스트 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.model.IngestionRunLog;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceHealthHookTest {

    @Autowired IngestionService service;
    @Autowired IngestionRunLogRepository runLogRepo;
    @Autowired IngestionItemFailureRepository failureRepo;

    @BeforeEach
    void setUp() {
        runLogRepo.deleteAll();
        failureRepo.deleteAll();
    }

    private IngestPolicyCommand validCommand(String externalId) {
        return new IngestPolicyCommand(
                "https://example.test/policy/" + externalId, "YOUTH_SEOUL_CRAWL",
                LocalDateTime.now(), externalId, "테스트 정책 " + externalId,
                "요약", "[개요]\n본문\n", "복지", "전국",
                LocalDate.now(), LocalDate.now().plusDays(30),
                2026, "ANNUAL", "CASH", "테스트기관", "02-0000-0000",
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()
        );
    }

    @Test
    void 성공_적재는_RunLog_success_1_을_적재한다() {
        service.receivePolicy(validCommand("ext-1"));

        List<IngestionRunLog> logs = runLogRepo.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getSource()).isEqualTo("YOUTH_SEOUL_CRAWL");
        assertThat(logs.get(0).getNormalizedSuccessCount()).isEqualTo(1);
        assertThat(logs.get(0).getNormalizedFailureCount()).isZero();

        assertThat(failureRepo.findAll()).isEmpty();
    }

    @Test
    void 동일_externalId_재수신은_duplicate_count_1_로_적재된다() {
        service.receivePolicy(validCommand("ext-2"));
        runLogRepo.deleteAll();
        service.receivePolicy(validCommand("ext-2"));

        List<IngestionRunLog> logs = runLogRepo.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getDuplicateCount()).isEqualTo(1);
        assertThat(logs.get(0).getNormalizedSuccessCount()).isZero();
    }

    @Test
    void 잘못된_payload_는_RunLog_failure_와_ItemFailure_를_적재한다() {
        // category=null → mapCategory("null") 또는 mapCategory(null) 에서 NPE 또는 IllegalArgumentException 유도
        // 실제 receivePolicy 흐름에서 RuntimeException 을 던지는 input 으로 fixture 구성.
        // 만약 category=null 이 default-fallback 으로 처리되면 다른 fixture 사용:
        //   - body=null 로 parseSections 진입 시 NPE
        //   - title=null 로 RegisterPolicyCommand 빌드 시 검증 실패
        // plan 실행 시 실제 IngestionService 의 어떤 input 이 RuntimeException 을 던지는지 한 번 검증 후 채택.
        IngestPolicyCommand bad = new IngestPolicyCommand(
                null, "BAD_SOURCE", null, null,
                null, "x", null,  // body=null 로 parseSections NPE 또는 RegisterPolicyCommand 검증 실패 유도
                null, "전국", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.receivePolicy(bad))
                .isInstanceOf(RuntimeException.class);

        List<IngestionRunLog> logs = runLogRepo.findAll();
        List<IngestionItemFailure> failures = failureRepo.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNormalizedFailureCount()).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getSource()).isEqualTo("BAD_SOURCE");
        assertThat(failures.get(0).isPayloadAvailable()).isTrue();
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.IngestionServiceHealthHookTest"`
Expected: 3 tests PASS

> 만약 정상 path 에서 외부 LLM 호출 등으로 실패하면, `costGuard.enabled=true` (또는 동등 fixture) 로 LLM 비활성화 후 재시도. 테스트 환경 설정은 기존 통합 테스트 (`AttachmentExtractionPipelineTest` 등) 참조.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceHealthHookTest.java
git commit -m "test(ingestion): receivePolicy 성공/실패/중복 hook 적재 통합 테스트"
```

---

# Stage C — 재처리 use case

## Task C1: `RetryFailedIngestionItemUseCase`

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/service/RetryFailedIngestionItemUseCase.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/dto/result/RetryResult.java`

- [ ] **Step 1: 결과 DTO**

```java
package com.youthfit.ingestion.application.dto.result;

public record RetryResult(
        Status status,
        String message,
        Long newFailureId
) {
    public enum Status { SUCCESS, FAILURE, NOT_FOUND, PAYLOAD_EXPIRED }

    public static RetryResult success() {
        return new RetryResult(Status.SUCCESS, "재처리 성공", null);
    }
    public static RetryResult failure(String msg, Long newFailureId) {
        return new RetryResult(Status.FAILURE, msg, newFailureId);
    }
    public static RetryResult notFound() {
        return new RetryResult(Status.NOT_FOUND, "실패 항목을 찾을 수 없습니다", null);
    }
    public static RetryResult payloadExpired() {
        return new RetryResult(Status.PAYLOAD_EXPIRED, "raw_payload 가 7일 경과로 만료되어 재처리할 수 없습니다", null);
    }
}
```

- [ ] **Step 2: Use case**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryFailedIngestionItemUseCase {

    private final IngestionItemFailureRepository failureRepository;
    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RetryResult retry(Long failureId) {
        IngestionItemFailure failure = failureRepository.findById(failureId).orElse(null);
        if (failure == null) {
            return RetryResult.notFound();
        }
        if (!failure.isPayloadAvailable()) {
            return RetryResult.payloadExpired();
        }

        IngestPolicyCommand command;
        try {
            command = objectMapper.readValue(failure.getRawPayload(), IngestPolicyCommand.class);
        } catch (Exception e) {
            log.warn("재처리 raw_payload 파싱 실패: failureId={}", failureId, e);
            return RetryResult.failure("raw_payload 파싱 실패: " + e.getMessage(), null);
        }

        try {
            ingestionService.receivePolicy(command);
            failure.markRetried();
            failureRepository.save(failure);
            return RetryResult.success();
        } catch (RuntimeException e) {
            failure.markRetried();
            failureRepository.save(failure);
            // ingestionService.receivePolicy 내부 hook 이 새 IngestionItemFailure 적재했을 것 — 그 id 를 추적할 수 있으면 반환
            return RetryResult.failure(e.getMessage(), null);
        }
    }
}
```

> `IngestionItemFailure.getRawPayload()` getter — Lombok `@Getter` 가 자동 생성. 위 코드 블록은 이미 `getRawPayload()` 사용.

- [ ] **Step 3: 단위 테스트**

**File:** `backend/src/test/java/com/youthfit/ingestion/application/service/RetryFailedIngestionItemUseCaseTest.java`

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RetryFailedIngestionItemUseCaseTest {

    @Test
    void 존재하지_않는_failureId_는_NOT_FOUND() {
        IngestionItemFailureRepository repo = mock(IngestionItemFailureRepository.class);
        when(repo.findById(99L)).thenReturn(Optional.empty());

        RetryFailedIngestionItemUseCase useCase = new RetryFailedIngestionItemUseCase(
                repo, mock(IngestionService.class), new ObjectMapper());

        RetryResult result = useCase.retry(99L);
        assertThat(result.status()).isEqualTo(RetryResult.Status.NOT_FOUND);
    }

    @Test
    void rawPayload_가_null_이면_PAYLOAD_EXPIRED() {
        IngestionItemFailureRepository repo = mock(IngestionItemFailureRepository.class);
        IngestionItemFailure failure = IngestionItemFailure.of(null, "S", "ext", null, FailureReason.OTHER, "x");
        when(repo.findById(1L)).thenReturn(Optional.of(failure));

        RetryFailedIngestionItemUseCase useCase = new RetryFailedIngestionItemUseCase(
                repo, mock(IngestionService.class), new ObjectMapper());

        RetryResult result = useCase.retry(1L);
        assertThat(result.status()).isEqualTo(RetryResult.Status.PAYLOAD_EXPIRED);
    }
}
```

- [ ] **Step 4: 테스트 실행 + commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.RetryFailedIngestionItemUseCaseTest"
git add backend/src/main/java/com/youthfit/ingestion/application/dto/result/RetryResult.java \
        backend/src/main/java/com/youthfit/ingestion/application/service/RetryFailedIngestionItemUseCase.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/RetryFailedIngestionItemUseCaseTest.java
git commit -m "feat(ingestion): RetryFailedIngestionItemUseCase + RetryResult"
```

---

# Stage D — Redact 스케줄러

## Task D1: `IngestionRedactScheduler`

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/scheduler/IngestionRedactScheduler.java`

- [ ] **Step 1: 스케줄러 작성**

```java
package com.youthfit.ingestion.infrastructure.scheduler;

import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionRedactScheduler {

    private final IngestionItemFailureRepository repository;

    @Value("${youthfit.ingestion.health.payload-redact-days:7}")
    private int redactDays;

    @Value("${youthfit.ingestion.health.failure-retention-days:30}")
    private int retentionDays;

    /**
     * 매일 03:00 KST 실행. payload 7일 redact + failure 30일 행 삭제.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void runDailyRedactAndDelete() {
        Instant redactBefore = Instant.now().minus(Duration.ofDays(redactDays));
        Instant deleteBefore = Instant.now().minus(Duration.ofDays(retentionDays));

        List<IngestionItemFailure> toRedact = repository.findPayloadsToRedact(redactBefore);
        for (IngestionItemFailure f : toRedact) {
            String hash = sha256(f.getRawPayload());
            f.redactPayload(hash);
        }
        repository.saveAll(toRedact);
        log.info("ingestion redact: {} 건 raw_payload → hash", toRedact.size());

        int deleted = repository.deleteOlderThan(deleteBefore);
        log.info("ingestion failure retention 삭제: {} 건", deleted);
    }

    private String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash_error";
        }
    }
}
```

- [ ] **Step 2: `@EnableScheduling` 활성 확인**

Run: `grep -r "@EnableScheduling" backend/src/main/java/`
Expected: 적어도 한 곳 활성. 없으면 `YouthfitApplication` 에 `@EnableScheduling` 추가.

- [ ] **Step 3: 단위 테스트 (수동 호출)**

**File:** `backend/src/test/java/com/youthfit/ingestion/infrastructure/scheduler/IngestionRedactSchedulerTest.java`

```java
package com.youthfit.ingestion.infrastructure.scheduler;

import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IngestionRedactSchedulerTest {

    @Autowired IngestionItemFailureRepository repository;
    @Autowired IngestionRedactScheduler scheduler;

    @BeforeEach
    void cleanUp() { repository.deleteAll(); }

    @Test
    void 7일_경과_payload_는_redact_되고_30일_경과_행은_삭제된다() {
        // 5일 전 — 그대로 유지
        save("recent", "{\"x\":1}", Instant.now().minus(5, ChronoUnit.DAYS));
        // 10일 전 — payload redact
        save("redact-me", "{\"x\":2}", Instant.now().minus(10, ChronoUnit.DAYS));
        // 35일 전 — 삭제
        save("delete-me", "{\"x\":3}", Instant.now().minus(35, ChronoUnit.DAYS));

        // 테스트 단축: 기본값 그대로 (7일 / 30일) 사용 — 위 fixture 가 자연스레 분기
        scheduler.runDailyRedactAndDelete();

        List<IngestionItemFailure> remaining = repository.findAll();
        assertThat(remaining).hasSize(2);

        IngestionItemFailure recent = remaining.stream().filter(f -> f.getSource().equals("recent")).findFirst().orElseThrow();
        assertThat(recent.getRawPayload()).isNotNull();

        IngestionItemFailure redacted = remaining.stream().filter(f -> f.getSource().equals("redact-me")).findFirst().orElseThrow();
        assertThat(redacted.getRawPayload()).isNull();
        assertThat(redacted.getRawPayloadHash()).isNotBlank();
    }

    private void save(String source, String payload, Instant createdAt) {
        IngestionItemFailure f = IngestionItemFailure.of(null, source, "ext", payload, FailureReason.OTHER, "msg");
        ReflectionTestUtils.setField(f, "createdAt", createdAt);
        repository.save(f);
    }
}
```

- [ ] **Step 4: 테스트 + commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.infrastructure.scheduler.IngestionRedactSchedulerTest"
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/scheduler/IngestionRedactScheduler.java \
        backend/src/test/java/com/youthfit/ingestion/infrastructure/scheduler/IngestionRedactSchedulerTest.java
git commit -m "feat(ingestion): IngestionRedactScheduler — 7일 redact + 30일 행 삭제"
```

---

# Stage E — Admin 조회 + 재처리 트리거

## Task E1: 응답 DTO 7종

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionKpiResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionDailyStatsResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionSourceSummaryResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionStaleSourceResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionFailureSummaryResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionFailureDetailResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/IngestionRetryResponse.java`

- [ ] **Step 1: KPI**

```java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record IngestionKpiResponse(
        long yesterdayReceived,
        long yesterdayFailure,
        BigDecimal sevenDayAvgReceivedPerDay,
        BigDecimal sevenDayFailureRate
) {}
```

- [ ] **Step 2: DailyStats**

```java
package com.youthfit.admin.presentation.dto.response;

import java.time.LocalDate;

public record IngestionDailyStatsResponse(
        LocalDate date,
        String source,
        long successCount,
        long failureCount,
        long duplicateCount
) {}
```

- [ ] **Step 3: SourceSummary**

```java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record IngestionSourceSummaryResponse(
        String source,
        Instant lastReceivedAt,
        long sevenDayReceived,
        BigDecimal sevenDayFailureRate,
        boolean stale  // 마지막 수신 24h 초과
) {}
```

- [ ] **Step 4: StaleSource**

```java
package com.youthfit.admin.presentation.dto.response;

import java.time.Instant;

public record IngestionStaleSourceResponse(
        String source,
        Instant lastReceivedAt,
        long hoursSinceLastReceived
) {}
```

- [ ] **Step 5: FailureSummary**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.ingestion.domain.model.FailureReason;

import java.time.Instant;

public record IngestionFailureSummaryResponse(
        Long id,
        String source,
        FailureReason failureReason,
        String sourceItemId,
        String errorMessageExcerpt,
        int retryCount,
        Instant createdAt
) {}
```

- [ ] **Step 6: FailureDetail**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.ingestion.domain.model.FailureReason;

import java.time.Instant;

public record IngestionFailureDetailResponse(
        Long id,
        String source,
        String sourceItemId,
        FailureReason failureReason,
        String errorMessage,
        String rawPayload,
        String rawPayloadHash,
        boolean payloadAvailable,
        int retryCount,
        Instant lastRetriedAt,
        Instant createdAt
) {}
```

- [ ] **Step 7: Retry**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.ingestion.application.dto.result.RetryResult;

public record IngestionRetryResponse(
        RetryResult.Status status,
        String message,
        Long newFailureId
) {
    public static IngestionRetryResponse from(RetryResult result) {
        return new IngestionRetryResponse(result.status(), result.message(), result.newFailureId());
    }
}
```

- [ ] **Step 8: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/admin/presentation/dto/response/Ingestion*.java
git commit -m "feat(admin): Ingestion 헬스 응답 DTO 7종"
```

---

## Task E2: `AdminIngestionService` — 조회 + 재처리 dispatch

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/service/AdminIngestionService.java`

- [ ] **Step 1: 서비스 구현**

```java
package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.application.service.RetryFailedIngestionItemUseCase;
import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminIngestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IngestionRunLogRepository runLogRepo;
    private final IngestionItemFailureRepository failureRepo;
    private final RetryFailedIngestionItemUseCase retryUseCase;

    @Value("${youthfit.ingestion.health.stale-threshold-hours:24}")
    private int staleHours;

    @Transactional(readOnly = true)
    public IngestionKpiResponse getKpi() {
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        Instant yesterdayStart = nowKst.toLocalDate().minusDays(1).atStartOfDay(KST).toInstant();
        Instant todayStart = nowKst.toLocalDate().atStartOfDay(KST).toInstant();
        Instant sevenDaysAgo = nowKst.toLocalDate().minusDays(7).atStartOfDay(KST).toInstant();

        Map<String, Object> yesterday = runLogRepo.sumBetween(yesterdayStart, todayStart);
        Map<String, Object> week = runLogRepo.sumBetween(sevenDaysAgo, nowKst.toInstant());

        long yReceived = ((Number) yesterday.getOrDefault("received", 0)).longValue();
        long yFailure = ((Number) yesterday.getOrDefault("failure", 0)).longValue();
        long wReceived = ((Number) week.getOrDefault("received", 0)).longValue();
        long wFailure = ((Number) week.getOrDefault("failure", 0)).longValue();

        BigDecimal avg = wReceived == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(wReceived).divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
        BigDecimal failureRate = wReceived == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(wFailure)
                .divide(BigDecimal.valueOf(wReceived), 4, RoundingMode.HALF_UP);

        return new IngestionKpiResponse(yReceived, yFailure, avg, failureRate);
    }

    @Transactional(readOnly = true)
    public List<IngestionDailyStatsResponse> getDailyStats(int days) {
        Instant now = Instant.now();
        Instant from = ZonedDateTime.now(KST).toLocalDate().minusDays(days - 1L)
                .atStartOfDay(KST).toInstant();
        List<Object[]> rows = runLogRepo.dailyStats(from, now);
        List<IngestionDailyStatsResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = ((Date) row[0]).toLocalDate();
            String source = (String) row[1];
            long success = ((Number) row[2]).longValue();
            long failure = ((Number) row[3]).longValue();
            long duplicate = ((Number) row[4]).longValue();
            result.add(new IngestionDailyStatsResponse(date, source, success, failure, duplicate));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<IngestionSourceSummaryResponse> getSourceSummaries() {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        List<Object[]> rows = runLogRepo.sourceSummaries(sevenDaysAgo);
        Instant staleThreshold = Instant.now().minus(Duration.ofHours(staleHours));
        List<IngestionSourceSummaryResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String source = (String) row[0];
            Instant lastReceived = ((Timestamp) row[1]).toInstant();
            long weekReceived = ((Number) row[2]).longValue();
            BigDecimal failureRate = (BigDecimal) row[3];
            boolean stale = lastReceived.isBefore(staleThreshold);
            result.add(new IngestionSourceSummaryResponse(
                    source, lastReceived, weekReceived, failureRate, stale));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<IngestionStaleSourceResponse> getStaleSources() {
        Instant threshold = Instant.now().minus(Duration.ofHours(staleHours));
        List<Object[]> rows = runLogRepo.staleSources(threshold);
        Instant now = Instant.now();
        List<IngestionStaleSourceResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String source = (String) row[0];
            Instant lastReceived = ((Timestamp) row[1]).toInstant();
            long hours = Duration.between(lastReceived, now).toHours();
            result.add(new IngestionStaleSourceResponse(source, lastReceived, hours));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<IngestionFailureSummaryResponse> searchFailures(
            String source, FailureReason reason, Instant from, Instant to,
            int page, int size) {
        Page<IngestionItemFailure> p = failureRepo.search(
                source, reason, from, to, PageRequest.of(page, size));
        return p.map(f -> new IngestionFailureSummaryResponse(
                f.getId(), f.getSource(), f.getFailureReason(), f.getSourceItemId(),
                excerpt(f.getErrorMessage(), 120),
                f.getRetryCount(), f.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public IngestionFailureDetailResponse getFailureDetail(Long id) {
        IngestionItemFailure f = failureRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("실패 항목을 찾을 수 없습니다: " + id));
        return new IngestionFailureDetailResponse(
                f.getId(), f.getSource(), f.getSourceItemId(),
                f.getFailureReason(), f.getErrorMessage(),
                f.getRawPayload(), f.getRawPayloadHash(), f.isPayloadAvailable(),
                f.getRetryCount(), f.getLastRetriedAt(), f.getCreatedAt()
        );
    }

    @Transactional
    public IngestionRetryResponse retryFailure(Long failureId) {
        RetryResult result = retryUseCase.retry(failureId);
        return IngestionRetryResponse.from(result);
    }

    private String excerpt(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
```

- [ ] **Step 2: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/admin/application/service/AdminIngestionService.java
git commit -m "feat(admin): AdminIngestionService — 6 조회 + 1 재처리 dispatch"
```

---

## Task E3: `AdminIngestionApi` 인터페이스 + Controller

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminIngestionApi.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminIngestionController.java`

- [ ] **Step 1: Swagger 인터페이스**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.ingestion.domain.model.FailureReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

@Tag(name = "Admin Ingestion Health", description = "어드민 — Ingestion 신선도/실패 (Spec 5)")
public interface AdminIngestionApi {

    @Operation(summary = "Ingestion KPI (어제 신규/실패 + 7일 평균)")
    ResponseEntity<IngestionKpiResponse> getKpi();

    @Operation(summary = "일자별·source 별 stacked bar 통계")
    ResponseEntity<List<IngestionDailyStatsResponse>> getDailyStats(
            @Parameter(description = "조회 일수, 기본 14")
            @RequestParam(required = false, defaultValue = "14") int days);

    @Operation(summary = "원천별 마지막 수신/7일 합계/실패율")
    ResponseEntity<List<IngestionSourceSummaryResponse>> getSourceSummaries();

    @Operation(summary = "24h 미수신 source 알람")
    ResponseEntity<List<IngestionStaleSourceResponse>> getStaleSources();

    @Operation(summary = "실패 항목 리스트 (필터 + 페이지네이션)")
    ResponseEntity<Page<IngestionFailureSummaryResponse>> searchFailures(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) FailureReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size);

    @Operation(summary = "실패 상세 (raw_payload 포함)")
    ResponseEntity<IngestionFailureDetailResponse> getFailureDetail(
            @Parameter(description = "실패 항목 id") @PathVariable Long id);

    @Operation(summary = "실패 항목 재처리 (단건)")
    ResponseEntity<IngestionRetryResponse> retryFailure(@PathVariable Long id);
}
```

- [ ] **Step 2: Controller**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminIngestionService;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.auth.presentation.annotation.RequireAdmin;
import com.youthfit.ingestion.domain.model.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
@RequiredArgsConstructor
@RequireAdmin
public class AdminIngestionController implements AdminIngestionApi {

    private final AdminIngestionService service;

    @Override
    @GetMapping("/kpi")
    public ResponseEntity<IngestionKpiResponse> getKpi() {
        return ResponseEntity.ok(service.getKpi());
    }

    @Override
    @GetMapping("/daily-stats")
    public ResponseEntity<List<IngestionDailyStatsResponse>> getDailyStats(
            @RequestParam(required = false, defaultValue = "14") int days) {
        return ResponseEntity.ok(service.getDailyStats(days));
    }

    @Override
    @GetMapping("/sources")
    public ResponseEntity<List<IngestionSourceSummaryResponse>> getSourceSummaries() {
        return ResponseEntity.ok(service.getSourceSummaries());
    }

    @Override
    @GetMapping("/stale-sources")
    public ResponseEntity<List<IngestionStaleSourceResponse>> getStaleSources() {
        return ResponseEntity.ok(service.getStaleSources());
    }

    @Override
    @GetMapping("/failures")
    public ResponseEntity<Page<IngestionFailureSummaryResponse>> searchFailures(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) FailureReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(service.searchFailures(source, reason, from, to, page, size));
    }

    @Override
    @GetMapping("/failures/{id:\\d+}")
    public ResponseEntity<IngestionFailureDetailResponse> getFailureDetail(@PathVariable Long id) {
        return ResponseEntity.ok(service.getFailureDetail(id));
    }

    @Override
    @PostMapping("/failures/{id:\\d+}/retry")
    public ResponseEntity<IngestionRetryResponse> retryFailure(@PathVariable Long id) {
        return ResponseEntity.ok(service.retryFailure(id));
    }
}
```

- [ ] **Step 3: 컴파일 + commit**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/youthfit/admin/presentation/controller/AdminIngestion*.java
git commit -m "feat(admin): AdminIngestionController — 7 엔드포인트 + Swagger Api"
```

---

## Task E4: 슬라이스 테스트

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminIngestionControllerTest.java`

- [ ] **Step 1: 슬라이스 테스트 작성**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminIngestionService;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.domain.model.FailureReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminIngestionController.class)
class AdminIngestionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AdminIngestionService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void kpi_GET_은_200_을_반환한다() throws Exception {
        when(service.getKpi()).thenReturn(new IngestionKpiResponse(
                10, 1, new BigDecimal("12.50"), new BigDecimal("0.10")));
        mvc.perform(get("/api/v1/admin/ingestion/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yesterdayReceived").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void daily_stats_GET_은_days_파라미터를_전달한다() throws Exception {
        when(service.getDailyStats(7)).thenReturn(List.of());
        mvc.perform(get("/api/v1/admin/ingestion/daily-stats").param("days", "7"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void stale_sources_GET_은_리스트를_반환한다() throws Exception {
        when(service.getStaleSources()).thenReturn(List.of(
                new IngestionStaleSourceResponse("ZZZ", Instant.now().minusSeconds(3600 * 25), 25L)
        ));
        mvc.perform(get("/api/v1/admin/ingestion/stale-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("ZZZ"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failures_GET_은_페이지네이션을_지원한다() throws Exception {
        Page<IngestionFailureSummaryResponse> page = new PageImpl<>(List.of());
        when(service.searchFailures(any(), any(), any(), any(), eq(0), eq(20))).thenReturn(page);
        mvc.perform(get("/api/v1/admin/ingestion/failures"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void retry_POST_는_재처리_결과를_반환한다() throws Exception {
        when(service.retryFailure(1L)).thenReturn(IngestionRetryResponse.from(RetryResult.success()));
        mvc.perform(post("/api/v1/admin/ingestion/failures/1/retry").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failure_id_가_숫자가_아니면_400() throws Exception {
        mvc.perform(get("/api/v1/admin/ingestion/failures/abc"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void 인증_없이_접근하면_4xx() throws Exception {
        mvc.perform(get("/api/v1/admin/ingestion/kpi"))
                .andExpect(status().is4xxClientError());
    }
}
```

- [ ] **Step 2: 테스트 실행 + commit**

Run: `cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminIngestionControllerTest"`
Expected: 7 tests PASS

```bash
git add backend/src/test/java/com/youthfit/admin/presentation/controller/AdminIngestionControllerTest.java
git commit -m "test(admin): AdminIngestionController 슬라이스 테스트 (7 엔드포인트 + 인증 + 숫자 제약)"
```

---

## Task E5: `application.yml` 설정 키

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 키 추가**

```yaml
youthfit:
  ingestion:
    health:
      payload-redact-days: 7
      failure-retention-days: 30
      stale-threshold-hours: 24
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat(ingestion): 헬스 환경 변수 (redact-7d, retention-30d, stale-24h)"
```

---

# Stage F — Frontend

## Task F1: API client

**Files:**
- Create: `frontend/src/apis/admin.ingestion.api.ts`

- [ ] **Step 1: API 함수**

```typescript
import { adminApi } from './admin.api';

export type FailureReason = 'VALIDATION' | 'PARSING' | 'MAPPING' | 'DEDUPLICATION_CONFLICT' | 'OTHER';
export type RetryStatus = 'SUCCESS' | 'FAILURE' | 'NOT_FOUND' | 'PAYLOAD_EXPIRED';

export interface IngestionKpiResponse {
  yesterdayReceived: number;
  yesterdayFailure: number;
  sevenDayAvgReceivedPerDay: number;
  sevenDayFailureRate: number;
}

export interface IngestionDailyStatsResponse {
  date: string;
  source: string;
  successCount: number;
  failureCount: number;
  duplicateCount: number;
}

export interface IngestionSourceSummaryResponse {
  source: string;
  lastReceivedAt: string;
  sevenDayReceived: number;
  sevenDayFailureRate: number;
  stale: boolean;
}

export interface IngestionStaleSourceResponse {
  source: string;
  lastReceivedAt: string;
  hoursSinceLastReceived: number;
}

export interface IngestionFailureSummaryResponse {
  id: number;
  source: string;
  failureReason: FailureReason;
  sourceItemId: string | null;
  errorMessageExcerpt: string;
  retryCount: number;
  createdAt: string;
}

export interface IngestionFailureDetailResponse {
  id: number;
  source: string;
  sourceItemId: string | null;
  failureReason: FailureReason;
  errorMessage: string;
  rawPayload: string | null;
  rawPayloadHash: string | null;
  payloadAvailable: boolean;
  retryCount: number;
  lastRetriedAt: string | null;
  createdAt: string;
}

export interface IngestionRetryResponse {
  status: RetryStatus;
  message: string;
  newFailureId: number | null;
}

export interface FailureSearchParams {
  source?: string;
  reason?: FailureReason;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const fetchIngestionKpi = async (): Promise<IngestionKpiResponse> => {
  const { data } = await adminApi.get('/ingestion/kpi'); return data;
};
export const fetchIngestionDailyStats = async (days = 14): Promise<IngestionDailyStatsResponse[]> => {
  const { data } = await adminApi.get('/ingestion/daily-stats', { params: { days } }); return data;
};
export const fetchIngestionSources = async (): Promise<IngestionSourceSummaryResponse[]> => {
  const { data } = await adminApi.get('/ingestion/sources'); return data;
};
export const fetchIngestionStaleSources = async (): Promise<IngestionStaleSourceResponse[]> => {
  const { data } = await adminApi.get('/ingestion/stale-sources'); return data;
};
export const searchIngestionFailures = async (params: FailureSearchParams) => {
  const { data } = await adminApi.get('/ingestion/failures', { params }); return data;
};
export const fetchIngestionFailureDetail = async (id: number): Promise<IngestionFailureDetailResponse> => {
  const { data } = await adminApi.get(`/ingestion/failures/${id}`); return data;
};
export const retryIngestionFailure = async (id: number): Promise<IngestionRetryResponse> => {
  const { data } = await adminApi.post(`/ingestion/failures/${id}/retry`); return data;
};
```

- [ ] **Step 2: typecheck + commit**

```bash
cd frontend && npm run typecheck
git add frontend/src/apis/admin.ingestion.api.ts
git commit -m "feat(frontend): admin.ingestion.api.ts — 7 endpoint client + 타입"
```

---

## Task F2: 도우미 컴포넌트들

**Files:**
- Create: `frontend/src/components/admin/ingestion/FailureReasonBadge.tsx`
- Create: `frontend/src/components/admin/ingestion/StaleSourceBanner.tsx`
- Create: `frontend/src/components/admin/ingestion/IngestionKpiSection.tsx`
- Create: `frontend/src/components/admin/ingestion/RetryConfirmModal.tsx`

- [ ] **Step 1: FailureReasonBadge**

```typescript
import type { FailureReason } from '@/apis/admin.ingestion.api';

const REASON_STYLES: Record<FailureReason, { color: string; label: string }> = {
  VALIDATION: { color: 'bg-amber-100 text-amber-800', label: '검증 실패' },
  PARSING: { color: 'bg-orange-100 text-orange-800', label: '파싱 실패' },
  MAPPING: { color: 'bg-rose-100 text-rose-800', label: '매핑 실패' },
  DEDUPLICATION_CONFLICT: { color: 'bg-purple-100 text-purple-800', label: '중복 충돌' },
  OTHER: { color: 'bg-slate-100 text-slate-700', label: '기타' },
};

interface Props { reason: FailureReason; }

export function FailureReasonBadge({ reason }: Props) {
  const s = REASON_STYLES[reason];
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${s.color}`}>
      {s.label}
    </span>
  );
}
```

- [ ] **Step 2: StaleSourceBanner**

```typescript
import type { IngestionStaleSourceResponse } from '@/apis/admin.ingestion.api';

interface Props { stale: IngestionStaleSourceResponse[]; }

export function StaleSourceBanner({ stale }: Props) {
  if (stale.length === 0) return null;
  return (
    <div className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
      <strong>마지막 24시간 동안 수신 없는 source:</strong>
      <ul className="mt-1 list-inside list-disc">
        {stale.map((s) => (
          <li key={s.source}>
            <span className="font-mono">{s.source}</span>
            {' — '}
            {s.hoursSinceLastReceived}시간 전 ({new Date(s.lastReceivedAt).toLocaleString('ko-KR')})
          </li>
        ))}
      </ul>
    </div>
  );
}
```

- [ ] **Step 3: IngestionKpiSection**

```typescript
import { KpiCard } from '@/components/admin/email/KpiCard';
import type { IngestionKpiResponse } from '@/apis/admin.ingestion.api';

interface Props { kpi: IngestionKpiResponse | undefined; }

export function IngestionKpiSection({ kpi }: Props) {
  if (!kpi) return null;
  const failurePct = (kpi.sevenDayFailureRate * 100).toFixed(2);
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
      <KpiCard title="어제 신규 수신" value={kpi.yesterdayReceived.toLocaleString()} />
      <KpiCard title="어제 실패" value={kpi.yesterdayFailure.toLocaleString()} />
      <KpiCard title="7일 평균 신규/일" value={Number(kpi.sevenDayAvgReceivedPerDay).toFixed(1)} />
      <KpiCard title="7일 실패율" value={`${failurePct}%`} />
    </div>
  );
}
```

- [ ] **Step 4: RetryConfirmModal**

```typescript
interface Props {
  open: boolean;
  failureId: number | null;
  onClose: () => void;
  onConfirm: () => void;
  loading?: boolean;
}

export function RetryConfirmModal({ open, failureId, onClose, onConfirm, loading }: Props) {
  if (!open || failureId == null) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-base font-semibold">실패 항목 재처리</h3>
        <p className="mt-2 text-sm text-slate-600">
          실패 항목 #{failureId} 를 다시 처리합니다. raw_payload 가 살아있어야 가능하며, 결과는 새 RunLog 로 적재됩니다.
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <button onClick={onClose} className="rounded border border-slate-200 px-3 py-1.5 text-sm">취소</button>
          <button onClick={onConfirm} disabled={loading}
                  className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-60">
            {loading ? '처리 중…' : '재처리'}
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: typecheck + commit**

```bash
cd frontend && npm run typecheck
git add frontend/src/components/admin/ingestion/
git commit -m "feat(frontend): Ingestion 헬스 도우미 컴포넌트 (Badge/Banner/Kpi/Modal)"
```

---

## Task F3: 일자별 차트 + 원천 테이블 + 실패 테이블

**Files:**
- Create: `frontend/src/components/admin/ingestion/IngestionDailyChart.tsx`
- Create: `frontend/src/components/admin/ingestion/SourceTable.tsx`
- Create: `frontend/src/components/admin/ingestion/FailureTable.tsx`

- [ ] **Step 1: IngestionDailyChart (Spec 2 StackedBarChart 활용)**

```typescript
import { StackedBarChart } from '@/components/charts/StackedBarChart';
import type { IngestionDailyStatsResponse } from '@/apis/admin.ingestion.api';

interface Props { rows: IngestionDailyStatsResponse[]; }

export function IngestionDailyChart({ rows }: Props) {
  // group by date
  const map = new Map<string, { date: string; success: number; failure: number; duplicate: number }>();
  rows.forEach((r) => {
    const cur = map.get(r.date) ?? { date: r.date, success: 0, failure: 0, duplicate: 0 };
    cur.success += r.successCount;
    cur.failure += r.failureCount;
    cur.duplicate += r.duplicateCount;
    map.set(r.date, cur);
  });
  const data = Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));

  if (data.length === 0) {
    return <div className="flex h-64 items-center justify-center rounded border border-slate-200 bg-slate-50 text-sm text-slate-500">데이터 없음</div>;
  }
  return (
    <StackedBarChart
      data={data}
      xKey="date"
      series={[
        { key: 'success', color: '#10b981', label: '성공' },
        { key: 'failure', color: '#ef4444', label: '실패' },
        { key: 'duplicate', color: '#94a3b8', label: '중복' },
      ]}
    />
  );
}
```

- [ ] **Step 2: SourceTable**

```typescript
import type { IngestionSourceSummaryResponse } from '@/apis/admin.ingestion.api';

interface Props { rows: IngestionSourceSummaryResponse[]; }

export function SourceTable({ rows }: Props) {
  if (rows.length === 0) {
    return <div className="rounded border border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">수신 기록 없음</div>;
  }
  return (
    <div className="overflow-x-auto rounded border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2 text-left">source</th>
            <th className="px-3 py-2 text-left">마지막 수신</th>
            <th className="px-3 py-2 text-right">7일 신규</th>
            <th className="px-3 py-2 text-right">실패율</th>
            <th className="px-3 py-2 text-center">상태</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.source} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.source}</td>
              <td className="px-3 py-2 text-slate-600">{new Date(r.lastReceivedAt).toLocaleString('ko-KR')}</td>
              <td className="px-3 py-2 text-right">{r.sevenDayReceived.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{(r.sevenDayFailureRate * 100).toFixed(1)}%</td>
              <td className="px-3 py-2 text-center">
                {r.stale
                  ? <span className="inline-block rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700">24h 미수신</span>
                  : <span className="inline-block rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700">정상</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: FailureTable**

```typescript
import { Link } from 'react-router-dom';
import { FailureReasonBadge } from './FailureReasonBadge';
import type { IngestionFailureSummaryResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionFailureSummaryResponse[];
  onRetry: (id: number) => void;
}

export function FailureTable({ rows, onRetry }: Props) {
  if (rows.length === 0) {
    return <div className="rounded border border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">실패 항목 없음</div>;
  }
  return (
    <div className="overflow-x-auto rounded border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2 text-left">시각</th>
            <th className="px-3 py-2 text-left">source</th>
            <th className="px-3 py-2 text-left">사유</th>
            <th className="px-3 py-2 text-left">externalId</th>
            <th className="px-3 py-2 text-left">에러</th>
            <th className="px-3 py-2 text-right">재시도</th>
            <th className="px-3 py-2 text-center">액션</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-t border-slate-100">
              <td className="px-3 py-2 text-slate-600">{new Date(r.createdAt).toLocaleString('ko-KR')}</td>
              <td className="px-3 py-2 font-mono text-xs">{r.source}</td>
              <td className="px-3 py-2"><FailureReasonBadge reason={r.failureReason} /></td>
              <td className="px-3 py-2 text-xs text-slate-500">{r.sourceItemId ?? '-'}</td>
              <td className="px-3 py-2 text-xs text-slate-700 max-w-md truncate">{r.errorMessageExcerpt}</td>
              <td className="px-3 py-2 text-right">{r.retryCount}</td>
              <td className="px-3 py-2 text-center">
                <button onClick={() => onRetry(r.id)} className="rounded bg-indigo-50 px-2 py-1 text-xs text-indigo-700 hover:bg-indigo-100">
                  재처리
                </button>
                {' '}
                <Link to={`/admin/ingestion/failures/${r.id}`} className="text-xs text-slate-500 underline">상세</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 4: typecheck + commit**

```bash
cd frontend && npm run typecheck
git add frontend/src/components/admin/ingestion/
git commit -m "feat(frontend): Ingestion 일자별 차트 + 원천 테이블 + 실패 테이블"
```

---

## Task F4: hook + 메인 페이지 + 상세 페이지

**Files:**
- Create: `frontend/src/hooks/useAdminIngestion.ts`
- Create: `frontend/src/pages/admin/AdminIngestionPage.tsx`
- Create: `frontend/src/pages/admin/AdminIngestionFailureDetailPage.tsx`

- [ ] **Step 1: hook**

```typescript
import { useEffect, useState, useCallback } from 'react';
import {
  fetchIngestionKpi, fetchIngestionDailyStats, fetchIngestionSources,
  fetchIngestionStaleSources, searchIngestionFailures, retryIngestionFailure,
  type IngestionKpiResponse, type IngestionDailyStatsResponse,
  type IngestionSourceSummaryResponse, type IngestionStaleSourceResponse,
  type IngestionFailureSummaryResponse, type FailureSearchParams,
  type IngestionRetryResponse,
} from '@/apis/admin.ingestion.api';

export function useAdminIngestion(searchParams: FailureSearchParams) {
  const [kpi, setKpi] = useState<IngestionKpiResponse>();
  const [daily, setDaily] = useState<IngestionDailyStatsResponse[]>([]);
  const [sources, setSources] = useState<IngestionSourceSummaryResponse[]>([]);
  const [stale, setStale] = useState<IngestionStaleSourceResponse[]>([]);
  const [failures, setFailures] = useState<IngestionFailureSummaryResponse[]>([]);
  const [totalFailures, setTotalFailures] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();

  const reload = useCallback(() => {
    setLoading(true);
    setError(undefined);
    Promise.all([
      fetchIngestionKpi(),
      fetchIngestionDailyStats(14),
      fetchIngestionSources(),
      fetchIngestionStaleSources(),
      searchIngestionFailures(searchParams),
    ])
      .then(([k, d, s, st, f]) => {
        setKpi(k); setDaily(d); setSources(s); setStale(st);
        setFailures(f.content ?? []);
        setTotalFailures(f.totalElements ?? 0);
      })
      .catch((e) => setError(e instanceof Error ? e : new Error(String(e))))
      .finally(() => setLoading(false));
  }, [searchParams]);

  useEffect(() => { reload(); }, [reload]);

  const retry = async (id: number): Promise<IngestionRetryResponse> => {
    const result = await retryIngestionFailure(id);
    reload();
    return result;
  };

  return { kpi, daily, sources, stale, failures, totalFailures, loading, error, retry };
}
```

- [ ] **Step 2: 메인 페이지**

```typescript
import { useState } from 'react';
import { StaleSourceBanner } from '@/components/admin/ingestion/StaleSourceBanner';
import { IngestionKpiSection } from '@/components/admin/ingestion/IngestionKpiSection';
import { IngestionDailyChart } from '@/components/admin/ingestion/IngestionDailyChart';
import { SourceTable } from '@/components/admin/ingestion/SourceTable';
import { FailureTable } from '@/components/admin/ingestion/FailureTable';
import { RetryConfirmModal } from '@/components/admin/ingestion/RetryConfirmModal';
import { useAdminIngestion } from '@/hooks/useAdminIngestion';
import type { FailureReason } from '@/apis/admin.ingestion.api';

export function AdminIngestionPage() {
  const [page, setPage] = useState(0);
  const [reason, setReason] = useState<FailureReason | undefined>();
  const [source, setSource] = useState<string>('');
  const [retryTarget, setRetryTarget] = useState<number | null>(null);
  const [retryLoading, setRetryLoading] = useState(false);

  const { kpi, daily, sources, stale, failures, totalFailures, loading, error, retry } = useAdminIngestion({
    page, size: 20, reason, source: source || undefined,
  });

  const handleConfirmRetry = async () => {
    if (retryTarget == null) return;
    setRetryLoading(true);
    try {
      const result = await retry(retryTarget);
      alert(`재처리 결과: ${result.status} — ${result.message}`);
    } finally {
      setRetryLoading(false);
      setRetryTarget(null);
    }
  };

  return (
    <div className="space-y-6 p-6">
      <header><h1 className="text-xl font-semibold">Ingestion 헬스</h1></header>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          데이터를 불러오지 못했습니다: {error.message}
        </div>
      )}
      {loading && <div className="text-sm text-slate-500">로딩 중…</div>}

      {!loading && (
        <>
          <StaleSourceBanner stale={stale} />
          <IngestionKpiSection kpi={kpi} />

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">일자별 신규/실패/중복 (14일)</h2>
            <IngestionDailyChart rows={daily} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">원천별 요약</h2>
            <SourceTable rows={sources} />
          </section>

          <section>
            <div className="mb-2 flex items-center gap-2">
              <h2 className="text-sm font-medium text-slate-700">실패 항목</h2>
              <input
                placeholder="source 필터"
                value={source}
                onChange={(e) => { setSource(e.target.value); setPage(0); }}
                className="rounded border border-slate-200 px-2 py-1 text-xs"
              />
              <select
                value={reason ?? ''}
                onChange={(e) => { setReason(e.target.value ? (e.target.value as FailureReason) : undefined); setPage(0); }}
                className="rounded border border-slate-200 px-2 py-1 text-xs"
              >
                <option value="">사유 전체</option>
                <option value="VALIDATION">VALIDATION</option>
                <option value="PARSING">PARSING</option>
                <option value="MAPPING">MAPPING</option>
                <option value="DEDUPLICATION_CONFLICT">DEDUPLICATION_CONFLICT</option>
                <option value="OTHER">OTHER</option>
              </select>
              <span className="ml-auto text-xs text-slate-500">총 {totalFailures}건</span>
            </div>
            <FailureTable rows={failures} onRetry={(id) => setRetryTarget(id)} />
            <div className="mt-2 flex justify-end gap-2 text-xs">
              <button onClick={() => setPage(Math.max(0, page - 1))} className="rounded border px-2 py-1">◀ 이전</button>
              <span>{page + 1} / {Math.max(1, Math.ceil(totalFailures / 20))}</span>
              <button onClick={() => setPage(page + 1)} className="rounded border px-2 py-1">다음 ▶</button>
            </div>
          </section>
        </>
      )}

      <RetryConfirmModal
        open={retryTarget != null}
        failureId={retryTarget}
        onClose={() => setRetryTarget(null)}
        onConfirm={handleConfirmRetry}
        loading={retryLoading}
      />
    </div>
  );
}
```

- [ ] **Step 3: 상세 페이지**

```typescript
import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { fetchIngestionFailureDetail, retryIngestionFailure, type IngestionFailureDetailResponse } from '@/apis/admin.ingestion.api';
import { FailureReasonBadge } from '@/components/admin/ingestion/FailureReasonBadge';

export function AdminIngestionFailureDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<IngestionFailureDetailResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    fetchIngestionFailureDetail(Number(id))
      .then(setDetail)
      .catch((e) => setError(e instanceof Error ? e : new Error(String(e))))
      .finally(() => setLoading(false));
  }, [id]);

  const handleRetry = async () => {
    if (!detail) return;
    const r = await retryIngestionFailure(detail.id);
    alert(`재처리 결과: ${r.status} — ${r.message}`);
    fetchIngestionFailureDetail(detail.id).then(setDetail);
  };

  if (loading) return <div className="p-6 text-sm text-slate-500">로딩 중…</div>;
  if (error) return <div className="p-6 text-sm text-red-700">에러: {error.message}</div>;
  if (!detail) return <div className="p-6 text-sm text-slate-500">데이터 없음</div>;

  return (
    <div className="space-y-4 p-6">
      <header className="flex items-center gap-2">
        <Link to="/admin/ingestion" className="text-sm text-indigo-600">← 목록</Link>
        <h1 className="text-xl font-semibold">실패 항목 #{detail.id}</h1>
        <FailureReasonBadge reason={detail.failureReason} />
      </header>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <dt className="text-slate-500">source</dt><dd className="font-mono">{detail.source}</dd>
        <dt className="text-slate-500">externalId</dt><dd className="font-mono text-xs">{detail.sourceItemId ?? '-'}</dd>
        <dt className="text-slate-500">발생 시각</dt><dd>{new Date(detail.createdAt).toLocaleString('ko-KR')}</dd>
        <dt className="text-slate-500">재시도 횟수</dt><dd>{detail.retryCount}</dd>
        <dt className="text-slate-500">마지막 재시도</dt><dd>{detail.lastRetriedAt ? new Date(detail.lastRetriedAt).toLocaleString('ko-KR') : '-'}</dd>
      </dl>

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">에러 메시지</h2>
        <pre className="overflow-x-auto rounded bg-slate-100 p-3 text-xs">{detail.errorMessage}</pre>
      </section>

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">raw_payload</h2>
        {detail.payloadAvailable ? (
          <pre className="max-h-96 overflow-auto rounded bg-slate-100 p-3 text-xs">{detail.rawPayload}</pre>
        ) : (
          <div className="rounded border border-slate-200 bg-slate-50 p-3 text-xs text-slate-500">
            7일 경과로 redact 됨. hash: <span className="font-mono">{detail.rawPayloadHash ?? '(없음)'}</span>
          </div>
        )}
      </section>

      <div className="flex gap-2">
        <button
          onClick={handleRetry}
          disabled={!detail.payloadAvailable}
          className="rounded bg-indigo-600 px-4 py-2 text-sm text-white disabled:cursor-not-allowed disabled:opacity-60"
          title={detail.payloadAvailable ? '재처리 가능' : '7일 경과로 재처리 불가'}
        >
          재처리
        </button>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: typecheck + commit**

```bash
cd frontend && npm run typecheck
git add frontend/src/hooks/useAdminIngestion.ts \
        frontend/src/pages/admin/AdminIngestionPage.tsx \
        frontend/src/pages/admin/AdminIngestionFailureDetailPage.tsx
git commit -m "feat(frontend): Ingestion 헬스 메인 + 상세 페이지 + hook"
```

---

## Task F5: 사이드바 + 라우터 등록

**Files:**
- Modify: `frontend/src/components/layout/AdminSidebar.tsx`
- Modify: 라우터 (`App.tsx` 또는 `routes.tsx`)

- [ ] **Step 1: 사이드바 항목 추가**

`AdminSidebar.tsx` 메뉴 배열에:

```typescript
{ to: '/admin/ingestion', label: 'Ingestion 헬스', icon: <적절한 lucide 아이콘, 예: Activity 또는 Database> },
```

- [ ] **Step 2: 라우터 등록**

기존 라우터 위치에:

```tsx
<Route path="/admin/ingestion" element={<AdminIngestionPage />} />
<Route path="/admin/ingestion/failures/:id" element={<AdminIngestionFailureDetailPage />} />
```

- [ ] **Step 3: typecheck + build**

```bash
cd frontend && npm run typecheck && npm run build
```

Expected: 둘 다 성공

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/layout/AdminSidebar.tsx \
        frontend/src/App.tsx
# 라우터 위치에 따라 add 경로 조정
git commit -m "feat(frontend): /admin/ingestion 라우트 + 사이드바 메뉴"
```

---

# Stage G — 통합 검증

## Task G1: 전체 빌드 + 테스트 + 프론트 lint

**Files:** 없음 — 검증

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 2: 프론트 typecheck + lint + test**

Run:
```bash
cd frontend && npm run typecheck && npm run lint && npm run test
```
Expected: 0 errors

- [ ] **Step 3: 로컬 부팅 + 수동 확인**

```bash
cd backend && ./gradlew bootRun &
cd frontend && npm run dev
```

브라우저:
1. `/admin/ingestion` 접속 가능
2. 사이드바 "Ingestion 헬스" 메뉴 활성
3. (수신 데이터 없을 때) 빈 상태 placeholder 정상

n8n 또는 수동 ingest API 호출 테스트:
```bash
curl -X POST http://localhost:8080/api/internal/ingest/policy \
  -H "Content-Type: application/json" -H "X-Internal-Api-Key: changeme" \
  -d '{ "title": "테스트", "body": "[개요]\n본문\n", "category": "복지", "sourceType": "TEST_E2E", ... }'
```

- 어드민 페이지 새로고침 → KPI/차트/원천 테이블에 등장
- 일부러 잘못된 payload → 실패 테이블에 1행 등장 → 재처리 버튼 동작 확인

- [ ] **Step 4: 부팅 종료**

`pkill -f "bootRun"; pkill -f "vite"` 또는 Ctrl+C

- [ ] **Step 5: 검증 단계 — commit 없음**

---

# 후속 / 미결 (이번 사이클 외)

- **source별 stale 임계 설정**: 24h vs 1주 등 — 운영 데이터 누적 후 v1
- **일괄 재처리**: 사고 위험 검토 후 v1
- **수동 매핑 화면** (실패 raw 수정 후 재시도) — UX 결정 많음, v1
- **raw_payload S3 이전** — DB 부담 측정 후 v1
- **n8n run id 추적** (배치 단위 집계) — n8n 측 변경 필요
- **실시간 알림** (Slack 등) — 외부 모니터링 책임
- **신규 source 자동 등록 알림**
- **재처리 SOP 운영 메모**: failure → confirm → 결과 모니터링 절차. README 또는 별도 wiki 페이지로 (별도 runbook 불요)

---

## 참고 — 인덱스 검증 SQL

```sql
-- daily-stats 쿼리 EXPLAIN
EXPLAIN ANALYZE
SELECT (received_at AT TIME ZONE 'Asia/Seoul')::date, source,
       SUM(normalized_success_count), SUM(normalized_failure_count), SUM(duplicate_count)
FROM ingestion_run_log
WHERE received_at >= now() - interval '14 days'
GROUP BY 1, 2 ORDER BY 1;
```

`Index Scan` 또는 `Bitmap Heap Scan` 이 `idx_ingestion_run_log_received_at` 위에서 동작 확인.
