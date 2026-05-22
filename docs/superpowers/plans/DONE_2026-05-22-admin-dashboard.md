# Admin Dashboard (지통실) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/admin` 첫 화면을 placeholder에서 "지금 손봐야 할 게 있는가?"에 답하는 지휘통제실로 교체. 9개 이상 신호를 평가해 액션 큐 + 6개 영역 상태 카드로 표시.

**Architecture:** 백엔드에 통합 엔드포인트 `GET /api/v1/admin/dashboard/overview` 신설. 각 신호는 `DashboardSignal` 인터페이스를 구현하는 빈으로 분리(Open/Closed). `DashboardSignalEvaluator`가 빈을 모두 주입받아 평가하고 try/catch로 부분 실패 격리. 프론트는 React Query 30초 폴링.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA, JUnit 5, Mockito, React 19, TypeScript, TanStack Query v5, Tailwind v4, Vitest + RTL, MSW

**Spec:** `docs/superpowers/specs/2026-05-22-admin-dashboard-design.md`

---

## File Map

### 신규 (backend)

```
admin/
├── application/
│   ├── service/
│   │   └── AdminDashboardOverviewService.java
│   └── dashboard/
│       ├── DashboardThresholds.java
│       ├── DashboardSignal.java                       (interface)
│       ├── DashboardSignalResult.java                 (record)
│       ├── DashboardSignalEvaluator.java
│       ├── AreaStatusBuilder.java
│       └── signals/
│           ├── IngestionFailureSignal.java
│           ├── IngestionStaleSignal.java
│           ├── EnrichmentFailureSignal.java
│           ├── EnrichmentBacklogSignal.java
│           ├── LlmCostSpikeSignal.java
│           ├── LlmWeeklyBudgetSignal.java
│           ├── EmailFailureSignal.java
│           ├── QnaCacheHitDropSignal.java
│           └── PolicyIntakeStallSignal.java
├── infrastructure/
│   └── persistence/
│       └── DashboardPolicyQueryRepository.java        (admin 모듈 read-only JPA)
└── presentation/
    ├── controller/
    │   ├── AdminDashboardApi.java
    │   └── AdminDashboardController.java
    └── dto/response/
        ├── DashboardOverviewResponse.java
        ├── DashboardActionItemResponse.java
        ├── DashboardAreaStatusResponse.java
        └── DashboardSeverity.java                     (enum HIGH/MEDIUM)
```

### 신규 (frontend)

```
src/
├── apis/adminDashboard.api.ts
├── hooks/queries/useAdminDashboardOverview.ts
├── types/adminDashboard.ts
├── components/admin/dashboard/
│   ├── ActionQueueSection.tsx
│   ├── ActionItemRow.tsx
│   ├── AllClearBanner.tsx
│   ├── AreaStatusGrid.tsx
│   ├── AreaStatusCard.tsx
│   ├── StatusBadge.tsx
│   └── Sparkline.tsx
└── pages/admin/AdminDashboardPage.tsx                 (전면 교체)
```

### 수정

- `backend/src/main/resources/application.yml` — `admin.dashboard.*` 설정 추가

---

## Conventions Recap (반드시 따를 것)

- DDD + Clean Architecture: 의존 방향 `presentation → application → domain`. presentation DTO를 application에서 import 금지.
- Controller는 `XxxApi` 인터페이스(Swagger 어노테이션 포함) + `XxxController`(`implements`) 분리.
- API prefix: `/api/v1/admin`
- 응답은 `com.youthfit.common.response.ApiResponse<T>`로 감싼다.
- DTO는 모두 `record`.
- Lombok: `@RequiredArgsConstructor`, `@Getter`만 사용.
- 트랜잭션 경계는 application service에만.
- 테스트: `@SpringBootTest`는 통합 테스트 한정. 단위 테스트는 Mockito 사용.

---

## Task 1: 임계치 + 인터페이스 + Result 타입

**Why first:** 모든 Signal이 이 인터페이스와 임계치 객체에 의존. 가장 먼저 골격 확립.

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/dashboard/DashboardThresholds.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dashboard/DashboardSignal.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dashboard/DashboardSignalResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/DashboardSeverity.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1.1: `DashboardSeverity` enum 작성**

```java
package com.youthfit.admin.presentation.dto.response;

public enum DashboardSeverity {
    HIGH, MEDIUM
}
```

- [ ] **Step 1.2: `DashboardSignalResult` 작성**

```java
package com.youthfit.admin.application.dashboard;

import com.youthfit.admin.presentation.dto.response.DashboardSeverity;

import java.time.Instant;

public record DashboardSignalResult(
        String code,
        DashboardSeverity severity,
        String title,
        String detail,
        String deeplink,
        Instant detectedAt
) {}
```

- [ ] **Step 1.3: `DashboardSignal` 인터페이스 작성**

```java
package com.youthfit.admin.application.dashboard;

import java.time.Instant;
import java.util.Optional;

public interface DashboardSignal {

    /** 신호 식별자. 예: "INGESTION_STALE" */
    String code();

    /**
     * 신호 평가. 이상이 없으면 Optional.empty().
     * 이상이 있으면 액션 큐에 표시할 결과를 반환한다.
     * @param now 평가 기준 시점 (테스트 가능성을 위해 외부 주입)
     */
    Optional<DashboardSignalResult> evaluate(Instant now);
}
```

- [ ] **Step 1.4: `DashboardThresholds` 작성**

```java
package com.youthfit.admin.application.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("admin.dashboard")
@Getter
@RequiredArgsConstructor
public class DashboardThresholds {

    private final Llm llm;
    private final Ingestion ingestion;
    private final Enrichment enrichment;
    private final Email email;
    private final QnaCache qnaCache;
    private final PolicyIntake policyIntake;

    @Getter
    @RequiredArgsConstructor
    public static class Llm {
        private final BigDecimal weeklyBudgetKrw;
        private final BigDecimal dailySpikeMultiplier;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Ingestion {
        private final int staleDays;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Enrichment {
        private final int backlogWarn;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Email {
        private final BigDecimal failureRateThreshold;
        private final int failureCountThreshold;
    }

    @Getter
    @RequiredArgsConstructor
    public static class QnaCache {
        private final BigDecimal hitDropThresholdPp;
    }

    @Getter
    @RequiredArgsConstructor
    public static class PolicyIntake {
        private final BigDecimal stallRatio;
    }
}
```

- [ ] **Step 1.5: `application.yml`에 default 임계치 추가**

기존 yaml 맨 아래에 추가:

```yaml
admin:
  dashboard:
    llm:
      weekly-budget-krw: 100000
      daily-spike-multiplier: 1.5
    ingestion:
      stale-days: 7
    enrichment:
      backlog-warn: 20
    email:
      failure-rate-threshold: 0.05
      failure-count-threshold: 10
    qna-cache:
      hit-drop-threshold-pp: 10
    policy-intake:
      stall-ratio: 0.3
```

- [ ] **Step 1.6: `@ConfigurationProperties` 활성화**

`youthfit/YouthfitApplication.java`에 어노테이션이 이미 `@EnableConfigurationProperties` 또는 `@ConfigurationPropertiesScan` 있는지 확인. 없으면 추가:

Read 후, 없는 경우만:
```java
@ConfigurationPropertiesScan
```

또는 admin 모듈 config 클래스에서:
```java
@Configuration
@EnableConfigurationProperties(DashboardThresholds.class)
public class AdminDashboardConfig {}
```
파일 경로: `backend/src/main/java/com/youthfit/admin/infrastructure/config/AdminDashboardConfig.java`

- [ ] **Step 1.7: 빌드만 통과 확인**

```bash
cd backend && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 1.8: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard \
        backend/src/main/java/com/youthfit/admin/presentation/dto/response/DashboardSeverity.java \
        backend/src/main/java/com/youthfit/admin/infrastructure/config/AdminDashboardConfig.java \
        backend/src/main/resources/application.yml
git commit -m "feat(admin): 지통실 대시보드 신호 인터페이스·임계치 스캐폴드"
```

---

## Task 2: Ingestion 신호 2종 (Failure + Stale)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/dashboard/signals/IngestionFailureSignal.java`
- Create: `backend/src/main/java/com/youthfit/admin/application/dashboard/signals/IngestionStaleSignal.java`
- Create: `backend/src/test/java/com/youthfit/admin/application/dashboard/signals/IngestionFailureSignalTest.java`
- Create: `backend/src/test/java/com/youthfit/admin/application/dashboard/signals/IngestionStaleSignalTest.java`

- [ ] **Step 2.1: `IngestionFailureSignalTest` 작성 (TDD)**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionFailureSignalTest {

    private final IngestionItemFailureRepository repo = mock(IngestionItemFailureRepository.class);
    private final IngestionFailureSignal signal = new IngestionFailureSignal(repo);

    @Test
    void code_is_INGESTION_FAILURE() {
        assertThat(signal.code()).isEqualTo("INGESTION_FAILURE");
    }

    @Test
    void returns_empty_when_no_failures_in_last_24h() {
        when(repo.countCreatedAfter(any())).thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void returns_high_severity_when_failures_present() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        when(repo.countCreatedAfter(now.minusSeconds(24 * 3600))).thenReturn(3L);

        Optional<DashboardSignalResult> r = signal.evaluate(now);

        assertThat(r).isPresent();
        DashboardSignalResult result = r.get();
        assertThat(result.code()).isEqualTo("INGESTION_FAILURE");
        assertThat(result.severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(result.title()).contains("3");
        assertThat(result.deeplink()).isEqualTo("/admin/ingestion?tab=failures");
        assertThat(result.detectedAt()).isEqualTo(now);
    }
}
```

Import 추가: `import static org.mockito.ArgumentMatchers.any;`

- [ ] **Step 2.2: `IngestionFailureSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IngestionFailureSignal implements DashboardSignal {

    private final IngestionItemFailureRepository repo;

    @Override
    public String code() {
        return "INGESTION_FAILURE";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        Instant since = now.minus(Duration.ofHours(24));
        long count = repo.countCreatedAfter(since);
        if (count == 0) return Optional.empty();
        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "Ingestion 실패 " + count + "건 (최근 24시간)",
                null,
                "/admin/ingestion?tab=failures",
                now
        ));
    }
}
```

- [ ] **Step 2.3: `IngestionItemFailureRepository`에 `countCreatedAfter` 추가**

Read `backend/src/main/java/com/youthfit/ingestion/domain/repository/IngestionItemFailureRepository.java` 한 뒤, 메서드가 없으면 다음 추가:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

@Query("SELECT COUNT(f) FROM IngestionItemFailure f WHERE f.createdAt >= :since")
long countCreatedAfter(@Param("since") Instant since);
```

- [ ] **Step 2.4: 테스트 실행 → PASS 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.IngestionFailureSignalTest"
```
Expected: 3 tests passed

- [ ] **Step 2.5: `IngestionStaleSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardThresholds.Ingestion;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionStaleSignalTest {

    private final IngestionRunLogRepository repo = mock(IngestionRunLogRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
    );
    private final IngestionStaleSignal signal = new IngestionStaleSignal(repo, thresholds);

    @Test
    void empty_when_no_stale_sources() {
        when(repo.staleSources(any())).thenReturn(List.of());
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_severity_with_source_names_in_detail() {
        when(repo.staleSources(any())).thenReturn(List.of(
                new Object[]{"onlineyouthcenter.kr", LocalDateTime.now().minusDays(10)},
                new Object[]{"gov24.go.kr", LocalDateTime.now().minusDays(8)}
        ));

        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());

        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.get().title()).contains("2");
        assertThat(r.get().detail()).contains("onlineyouthcenter.kr").contains("gov24.go.kr");
        assertThat(r.get().deeplink()).isEqualTo("/admin/ingestion?filter=stale");
    }
}
```

- [ ] **Step 2.6: `IngestionStaleSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.ingestion.domain.repository.IngestionRunLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IngestionStaleSignal implements DashboardSignal {

    private final IngestionRunLogRepository repo;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return "INGESTION_STALE";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        Instant threshold = now.minus(Duration.ofDays(thresholds.getIngestion().getStaleDays()));
        List<Object[]> rows = repo.staleSources(threshold);
        if (rows.isEmpty()) return Optional.empty();

        String detail = rows.stream()
                .map(r -> (String) r[0])
                .collect(Collectors.joining(", "));
        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "출처 " + rows.size() + "개가 " + thresholds.getIngestion().getStaleDays() + "일 이상 미갱신",
                detail,
                "/admin/ingestion?filter=stale",
                now
        ));
    }
}
```

`IngestionRunLogRepository#staleSources(Instant)`는 이미 존재 (AdminIngestionService에서 사용 확인). 시그니처가 `Instant` 받는지 `LocalDateTime` 받는지 한 번 더 Read로 확인. `LocalDateTime`이면 변환해서 호출하도록 본문 수정:
```java
Instant threshold = ...;
List<Object[]> rows = repo.staleSources(threshold);  // Instant 받는다고 확인된 경우
```
만약 `LocalDateTime` 받으면:
```java
LocalDateTime thresholdLdt = LocalDateTime.ofInstant(threshold, java.time.ZoneOffset.UTC);
List<Object[]> rows = repo.staleSources(thresholdLdt);
```

- [ ] **Step 2.7: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.Ingestion*Test"
```
Expected: 5 tests passed

- [ ] **Step 2.8: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard/signals/IngestionFailureSignal.java \
        backend/src/main/java/com/youthfit/admin/application/dashboard/signals/IngestionStaleSignal.java \
        backend/src/main/java/com/youthfit/ingestion/domain/repository/IngestionItemFailureRepository.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/IngestionFailureSignalTest.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/IngestionStaleSignalTest.java
git commit -m "feat(admin): ingestion 이상 신호 2종(실패·stale) 추가"
```

---

## Task 3: Enrichment 신호 2종 (Failure + Backlog)

**Files:**
- Create: `signals/EnrichmentFailureSignal.java`, `signals/EnrichmentBacklogSignal.java`
- Create: 동일 위치의 Test 파일 2개
- Modify: `policy/domain/repository/EnrichmentJobRepository.java` — `countFailedSince(Instant)` 메서드 추가

- [ ] **Step 3.1: `EnrichmentJobRepository`에 메서드 추가**

Read `backend/src/main/java/com/youthfit/policy/domain/repository/EnrichmentJobRepository.java` 한 뒤 추가:

```java
@Query("SELECT COUNT(j) FROM EnrichmentJob j WHERE j.status = com.youthfit.policy.domain.model.EnrichmentJobStatus.FAILED AND j.updatedAt >= :since")
long countFailedSince(@Param("since") Instant since);
```

(상태 enum 이름이 다르면 실제 이름으로 교체. `EnrichmentJobStatus` 또는 유사명 — 모듈 내 Read로 확인)

- [ ] **Step 3.2: `EnrichmentFailureSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnrichmentFailureSignalTest {

    private final EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
    private final EnrichmentFailureSignal signal = new EnrichmentFailureSignal(repo);

    @Test
    void empty_when_zero_failures() {
        when(repo.countFailedSince(any())).thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_when_at_least_one_failure() {
        when(repo.countFailedSince(any())).thenReturn(2L);
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.get().title()).contains("2");
        assertThat(r.get().deeplink()).isEqualTo("/admin/enrichment?filter=failed");
    }
}
```

- [ ] **Step 3.3: `EnrichmentFailureSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EnrichmentFailureSignal implements DashboardSignal {

    private final EnrichmentJobRepository repo;

    @Override
    public String code() {
        return "ENRICHMENT_FAILURE";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        long failed = repo.countFailedSince(now.minus(Duration.ofHours(24)));
        if (failed == 0) return Optional.empty();
        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "Enrichment 실패 " + failed + "건 (최근 24시간)",
                null,
                "/admin/enrichment?filter=failed",
                now
        ));
    }
}
```

- [ ] **Step 3.4: `EnrichmentBacklogSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnrichmentBacklogSignalTest {

    private final AdminEnrichmentQueryService queryService = mock(AdminEnrichmentQueryService.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
    );
    private final EnrichmentBacklogSignal signal = new EnrichmentBacklogSignal(queryService, thresholds);

    @Test
    void empty_when_below_threshold() {
        when(queryService.findReviewSummary()).thenReturn(summary(19));
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void empty_when_equal_threshold_minus_one() {
        when(queryService.findReviewSummary()).thenReturn(summary(19));
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void medium_when_at_threshold() {
        when(queryService.findReviewSummary()).thenReturn(summary(20));
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.MEDIUM);
        assertThat(r.get().title()).contains("20");
    }

    private EnrichmentReviewSummaryResult summary(int reviewCount) {
        // 실제 result 타입 시그니처에 맞춰 인스턴스화 — 도메인 모듈의 Result 클래스 확인 후 채움
        // 본문 구현 시 실제 fields로 교체. 예: new EnrichmentReviewSummaryResult(reviewCount, 0L, 0L)
        throw new UnsupportedOperationException("EnrichmentReviewSummaryResult 시그니처 확인 후 채울 것");
    }
}
```

**Note for implementer:** `EnrichmentReviewSummaryResult` 또는 동등 타입의 시그니처를 `policy.application.dto.result` 패키지에서 Read로 확인 후 `summary(int)` 헬퍼 본문을 실제 fields로 채움. 미리뷰 후보 카운트를 담는 필드 이름을 신호 본문에서도 동일하게 사용.

- [ ] **Step 3.5: `EnrichmentBacklogSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EnrichmentBacklogSignal implements DashboardSignal {

    private final AdminEnrichmentQueryService queryService;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return "ENRICHMENT_BACKLOG";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        EnrichmentReviewSummaryResult summary = queryService.findReviewSummary();
        long count = summary.reviewCandidateCount();   // 실제 accessor 이름 확인 후 교체
        int threshold = thresholds.getEnrichment().getBacklogWarn();
        if (count < threshold) return Optional.empty();
        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.MEDIUM,
                "Enrichment 미리뷰 후보 " + count + "건 누적",
                null,
                "/admin/enrichment",
                now
        ));
    }
}
```

**Note:** `summary.reviewCandidateCount()`는 실제 accessor 이름으로 교체. 테스트의 헬퍼와 일치시킬 것.

- [ ] **Step 3.6: 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.Enrichment*Test"
```
Expected: 5 tests passed (위 헬퍼 본문 채운 후)

- [ ] **Step 3.7: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard/signals/Enrichment*.java \
        backend/src/main/java/com/youthfit/policy/domain/repository/EnrichmentJobRepository.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/Enrichment*Test.java
git commit -m "feat(admin): enrichment 이상 신호 2종(실패·누적) 추가"
```

---

## Task 4: LLM 비용 신호 2종 (Spike + WeeklyBudget)

**Files:**
- Create: `signals/LlmCostSpikeSignal.java`, `signals/LlmWeeklyBudgetSignal.java`
- Create: 동일 위치 Test 파일 2개

`LlmCostBucketRepository#sumBetween(Instant from, Instant to)`는 이미 존재 (`AdminLlmCostService`에서 사용). KRW 환산은 service 단에서 하므로, signal에서도 동일하게 환산 필요. 단순화를 위해 `LlmCostBucketRepository`에 KRW 변환된 합계를 제공하는 메서드를 두지 않고 **USD 합계를 가져와 환율 곱셈은 signal 내부에서** 수행. 환율 상수는 `application.yml`의 `youthfit.metrics.llm-cost.usd-to-krw`를 `@Value`로 주입.

- [ ] **Step 4.1: `LlmCostSpikeSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCostSpikeSignalTest {

    private final LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
    private final DashboardThresholds thresholds = thresholds("1.5");
    private final LlmCostSpikeSignal signal = new LlmCostSpikeSignal(repo, thresholds, new BigDecimal("1350"));

    @Test
    void empty_when_yesterday_below_threshold() {
        // 어제 1.0, 직전 7일 평균 1.0 → 어제는 평균의 1.0배. threshold 1.5 미만
        when(repo.sumBetween(any(), any()))
                .thenReturn(List.of(new Object[]{ new BigDecimal("1.00"), 0L })) // 어제
                .thenReturn(List.of(new Object[]{ new BigDecimal("7.00"), 0L })); // 직전 7일
        assertThat(signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"))).isEmpty();
    }

    @Test
    void high_when_yesterday_exceeds_multiplier() {
        when(repo.sumBetween(any(), any()))
                .thenReturn(List.of(new Object[]{ new BigDecimal("2.00"), 0L }))  // 어제 2.0 USD
                .thenReturn(List.of(new Object[]{ new BigDecimal("7.00"), 0L })); // 직전 7일 합 7.0 → 일평균 1.0
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"));
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.get().title()).contains("어제").contains("LLM");
        assertThat(r.get().deeplink()).isEqualTo("/admin/llm-cost");
    }

    private static DashboardThresholds thresholds(String multiplier) {
        return new DashboardThresholds(
                new DashboardThresholds.Llm(new BigDecimal("100000"), new BigDecimal(multiplier)),
                new DashboardThresholds.Ingestion(7),
                new DashboardThresholds.Enrichment(20),
                new DashboardThresholds.Email(BigDecimal.ZERO, 0),
                new DashboardThresholds.QnaCache(BigDecimal.ZERO),
                new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
        );
    }
}
```

- [ ] **Step 4.2: `LlmCostSpikeSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class LlmCostSpikeSignal implements DashboardSignal {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LlmCostBucketRepository repo;
    private final DashboardThresholds thresholds;
    private final BigDecimal usdToKrw;

    public LlmCostSpikeSignal(LlmCostBucketRepository repo,
                              DashboardThresholds thresholds,
                              @Value("${youthfit.metrics.llm-cost.usd-to-krw:1350}") BigDecimal usdToKrw) {
        this.repo = repo;
        this.thresholds = thresholds;
        this.usdToKrw = usdToKrw;
    }

    @Override
    public String code() {
        return "LLM_COST_SPIKE";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        LocalDate today = ZonedDateTime.ofInstant(now, KST).toLocalDate();
        Instant yStart = today.minusDays(1).atStartOfDay(KST).toInstant();
        Instant tStart = today.atStartOfDay(KST).toInstant();
        Instant weekStart = today.minusDays(8).atStartOfDay(KST).toInstant();

        BigDecimal yesterdayUsd = sumUsd(yStart, tStart);
        BigDecimal lastSevenUsd = sumUsd(weekStart, yStart);
        BigDecimal avgUsd = lastSevenUsd.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP);

        BigDecimal threshold = avgUsd.multiply(thresholds.getLlm().getDailySpikeMultiplier());
        if (yesterdayUsd.compareTo(threshold) <= 0) return Optional.empty();
        if (avgUsd.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();   // 7일 합 0이면 비교 무의미

        BigDecimal yesterdayKrw = yesterdayUsd.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP);
        BigDecimal multiplier = yesterdayUsd.divide(avgUsd, 1, RoundingMode.HALF_UP);

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "어제 LLM 비용 ₩" + format(yesterdayKrw) + " (7일 평균 " + multiplier + "배)",
                null,
                "/admin/llm-cost",
                now
        ));
    }

    private BigDecimal sumUsd(Instant from, Instant to) {
        List<Object[]> rows = repo.sumBetween(from, to);
        if (rows.isEmpty() || rows.get(0)[0] == null) return BigDecimal.ZERO;
        return (BigDecimal) rows.get(0)[0];
    }

    private static String format(BigDecimal v) {
        return String.format("%,d", v.longValueExact());
    }
}
```

- [ ] **Step 4.3: `LlmWeeklyBudgetSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmWeeklyBudgetSignalTest {

    private final LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(new BigDecimal("100000"), new BigDecimal("1.5")),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
    );
    private final LlmWeeklyBudgetSignal signal = new LlmWeeklyBudgetSignal(repo, thresholds, new BigDecimal("1350"));

    @Test
    void empty_when_below_budget() {
        // 이번주 누적 USD * 1350 < 100000
        when(repo.sumBetween(any(), any())).thenReturn(List.of(new Object[]{ new BigDecimal("70"), 0L }));
        // 70 * 1350 = 94500 → 100000 미만
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_when_at_or_above_budget() {
        when(repo.sumBetween(any(), any())).thenReturn(List.of(new Object[]{ new BigDecimal("75"), 0L }));
        // 75 * 1350 = 101250 → 100000 초과
        assertThat(signal.evaluate(Instant.now())).isPresent();
        assertThat(signal.evaluate(Instant.now()).get().severity()).isEqualTo(DashboardSeverity.HIGH);
    }
}
```

- [ ] **Step 4.4: `LlmWeeklyBudgetSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;
import java.util.Optional;

@Component
public class LlmWeeklyBudgetSignal implements DashboardSignal {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LlmCostBucketRepository repo;
    private final DashboardThresholds thresholds;
    private final BigDecimal usdToKrw;

    public LlmWeeklyBudgetSignal(LlmCostBucketRepository repo,
                                 DashboardThresholds thresholds,
                                 @Value("${youthfit.metrics.llm-cost.usd-to-krw:1350}") BigDecimal usdToKrw) {
        this.repo = repo;
        this.thresholds = thresholds;
        this.usdToKrw = usdToKrw;
    }

    @Override
    public String code() {
        return "LLM_WEEKLY_BUDGET";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        ZonedDateTime nowKst = ZonedDateTime.ofInstant(now, KST);
        LocalDate today = nowKst.toLocalDate();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L); // 월요일
        Instant from = weekStart.atStartOfDay(KST).toInstant();

        List<Object[]> rows = repo.sumBetween(from, now);
        BigDecimal usd = (rows.isEmpty() || rows.get(0)[0] == null) ? BigDecimal.ZERO : (BigDecimal) rows.get(0)[0];
        BigDecimal krw = usd.multiply(usdToKrw).setScale(0, RoundingMode.HALF_UP);

        if (krw.compareTo(thresholds.getLlm().getWeeklyBudgetKrw()) <= 0) return Optional.empty();
        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "이번주 LLM 누적 ₩" + String.format("%,d", krw.longValueExact())
                        + " (예산 ₩" + String.format("%,d", thresholds.getLlm().getWeeklyBudgetKrw().longValueExact()) + " 초과)",
                null,
                "/admin/llm-cost",
                now
        ));
    }
}
```

- [ ] **Step 4.5: 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.Llm*Test"
```
Expected: 4 tests passed

- [ ] **Step 4.6: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard/signals/Llm*.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/Llm*Test.java
git commit -m "feat(admin): LLM 비용 이상 신호 2종(급증·주간 예산) 추가"
```

---

## Task 5: Email 실패 신호

**Files:**
- Create: `signals/EmailFailureSignal.java`
- Create: `EmailFailureSignalTest.java`
- Modify: `user/domain/repository/EmailSendAttemptRepository.java` — `countByStatusSince(...)` 메서드 추가

기존 `aggregateDaily`로 일별 status별 집계가 가능하나, 24시간 윈도우 단순 카운트가 더 간단. 직접 메서드 추가.

- [ ] **Step 5.1: Repository 메서드 추가**

```java
@Query("SELECT COUNT(a) FROM EmailSendAttempt a WHERE a.sentAt >= :since")
long countSentSince(@Param("since") LocalDateTime since);

@Query("SELECT COUNT(a) FROM EmailSendAttempt a WHERE a.sentAt >= :since AND a.status = com.youthfit.user.domain.model.EmailSendStatus.FAILED")
long countFailedSince(@Param("since") LocalDateTime since);
```

EmailSendStatus enum 값은 모듈 내에서 확인 (`FAILED` 또는 동등명). `BOUNCED`도 실패에 포함해야 한다면 `WHERE a.status IN (FAILED, BOUNCED)`로 변경.

- [ ] **Step 5.2: `EmailFailureSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailFailureSignalTest {

    private final EmailSendAttemptRepository repo = mock(EmailSendAttemptRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(new BigDecimal("0.05"), 10),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
    );
    private final EmailFailureSignal signal = new EmailFailureSignal(repo, thresholds);

    @Test
    void empty_when_no_emails_sent() {
        when(repo.countSentSince(any())).thenReturn(0L);
        when(repo.countFailedSince(any())).thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void high_when_failure_rate_exceeds() {
        when(repo.countSentSince(any())).thenReturn(100L);
        when(repo.countFailedSince(any())).thenReturn(6L);  // 6% > 5%
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.HIGH);
        assertThat(r.get().title()).contains("6").contains("실패");
    }

    @Test
    void high_when_absolute_count_exceeds_even_if_rate_low() {
        when(repo.countSentSince(any())).thenReturn(10000L);
        when(repo.countFailedSince(any())).thenReturn(15L);  // 0.15% but absolute > 10
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());
        assertThat(r).isPresent();
    }

    @Test
    void empty_when_below_both_thresholds() {
        when(repo.countSentSince(any())).thenReturn(1000L);
        when(repo.countFailedSince(any())).thenReturn(5L);  // 0.5%, 절대 5건
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }
}
```

- [ ] **Step 5.3: `EmailFailureSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailFailureSignal implements DashboardSignal {

    private final EmailSendAttemptRepository repo;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return "EMAIL_FAILURE";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        LocalDateTime since = LocalDateTime.ofInstant(now.minus(Duration.ofHours(24)), ZoneOffset.UTC);
        long sent = repo.countSentSince(since);
        long failed = repo.countFailedSince(since);
        if (sent == 0 && failed == 0) return Optional.empty();

        BigDecimal rate = sent == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(failed).divide(BigDecimal.valueOf(sent), 4, RoundingMode.HALF_UP);

        boolean rateExceeded = rate.compareTo(thresholds.getEmail().getFailureRateThreshold()) > 0;
        boolean countExceeded = failed > thresholds.getEmail().getFailureCountThreshold();

        if (!rateExceeded && !countExceeded) return Optional.empty();

        BigDecimal pct = rate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.HIGH,
                "이메일 실패 " + failed + "건 / " + sent + "건 (" + pct + "%)",
                null,
                "/admin/email?filter=failed",
                now
        ));
    }
}
```

- [ ] **Step 5.4: 테스트 통과 확인 + Commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.EmailFailureSignalTest"
```
Expected: 4 tests passed

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard/signals/EmailFailureSignal.java \
        backend/src/main/java/com/youthfit/user/domain/repository/EmailSendAttemptRepository.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/EmailFailureSignalTest.java
git commit -m "feat(admin): 이메일 발송 실패 이상 신호 추가"
```

---

## Task 6: Q&A 캐시 적중률 급락 신호

**Files:**
- Create: `signals/QnaCacheHitDropSignal.java`
- Create: `QnaCacheHitDropSignalTest.java`

기존 `QnaCacheLookupLogRepository#aggregateKpi`가 7일 적중률 합계를 반환. 추가로 14일치가 필요. 기존 메서드는 KPI 한 줄만 반환하므로, **14일 윈도우를 두 번 호출**해서 비교한다 (직전 7일 vs 그 이전 7일). 다음 7일 시작 시점을 `today - 14d`로 잡고 두 번 `aggregateDaily` 결과를 합산.

단순화를 위해, `QnaCacheLookupLogRepository`에 다음 메서드를 추가:

```java
@Query(value = """
    SELECT
      COUNT(*) FILTER (WHERE result = 'HIT') AS hits,
      COUNT(*) AS total
    FROM qna_cache_lookup_log
    WHERE looked_up_at >= :from AND looked_up_at < :to
    """, nativeQuery = true)
List<Object[]> hitTotalsBetween(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
```

- [ ] **Step 6.1: Repository 메서드 추가** (위 코드 참고)

- [ ] **Step 6.2: `QnaCacheHitDropSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QnaCacheHitDropSignalTest {

    private final QnaCacheLookupLogRepository repo = mock(QnaCacheLookupLogRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(new BigDecimal("10")),
            new DashboardThresholds.PolicyIntake(BigDecimal.ZERO)
    );
    private final QnaCacheHitDropSignal signal = new QnaCacheHitDropSignal(repo, thresholds);

    @Test
    void empty_when_no_data() {
        when(repo.hitTotalsBetween(any(), any())).thenReturn(List.of(row(0L, 0L)));
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void empty_when_drop_below_threshold_pp() {
        // 직전 7일: 70/100 = 70%, 이전 7일: 75/100 = 75% → 5pp 하락. threshold 10pp 미만
        when(repo.hitTotalsBetween(any(), any()))
                .thenReturn(List.of(row(70L, 100L)))   // 최근 7일
                .thenReturn(List.of(row(75L, 100L))); // 직전 7일
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }

    @Test
    void medium_when_drop_exceeds_threshold_pp() {
        // 60% vs 75% → 15pp 하락
        when(repo.hitTotalsBetween(any(), any()))
                .thenReturn(List.of(row(60L, 100L)))
                .thenReturn(List.of(row(75L, 100L)));
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.now());
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.MEDIUM);
        assertThat(r.get().title()).contains("적중률").contains("15");
    }

    private static Object[] row(long hits, long total) {
        return new Object[]{BigInteger.valueOf(hits), BigInteger.valueOf(total)};
    }
}
```

**Note for implementer:** native query에서 `COUNT(*)` 결과 타입은 `BigInteger` 또는 `Long` — 환경에 따라 다르므로 실제 반환 타입을 확인 후 캐스팅. 본문 `((Number) row[0]).longValue()`이면 둘 다 호환.

- [ ] **Step 6.3: `QnaCacheHitDropSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QnaCacheHitDropSignal implements DashboardSignal {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QnaCacheLookupLogRepository repo;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return "QNA_CACHE_HIT_DROP";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        ZonedDateTime nowKst = ZonedDateTime.ofInstant(now, KST);
        LocalDate today = nowKst.toLocalDate();
        LocalDateTime t0 = today.atStartOfDay();
        LocalDateTime t7 = today.minusDays(7).atStartOfDay();
        LocalDateTime t14 = today.minusDays(14).atStartOfDay();

        BigDecimal recent = hitRate(t7, t0);
        BigDecimal prior = hitRate(t14, t7);
        if (recent == null || prior == null) return Optional.empty();

        BigDecimal dropPp = prior.subtract(recent).multiply(BigDecimal.valueOf(100));
        if (dropPp.compareTo(thresholds.getQnaCache().getHitDropThresholdPp()) < 0) return Optional.empty();

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.MEDIUM,
                "Q&A 캐시 적중률 " + dropPp.setScale(0, RoundingMode.HALF_UP) + "pp 하락",
                "직전 7일 " + percent(prior) + " → 최근 7일 " + percent(recent),
                "/admin/qna-cache",
                now
        ));
    }

    private BigDecimal hitRate(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = repo.hitTotalsBetween(from, to);
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(0);
        long hits = row[0] == null ? 0 : ((Number) row[0]).longValue();
        long total = row[1] == null ? 0 : ((Number) row[1]).longValue();
        if (total == 0) return null;
        return BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";
    }
}
```

- [ ] **Step 6.4: 테스트 통과 + Commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.QnaCacheHitDropSignalTest"
```
Expected: 3 tests passed

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard/signals/QnaCacheHitDropSignal.java \
        backend/src/main/java/com/youthfit/qna/domain/repository/QnaCacheLookupLogRepository.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/QnaCacheHitDropSignalTest.java
git commit -m "feat(admin): Q&A 캐시 적중률 급락 이상 신호 추가"
```

---

## Task 7: Policy Intake 정체 신호 + admin 내부 read-only 쿼리

**Files:**
- Create: `admin/infrastructure/persistence/DashboardPolicyQueryRepository.java`
- Create: `signals/PolicyIntakeStallSignal.java`
- Create: `PolicyIntakeStallSignalTest.java`

`PolicyRepository` 도메인 인터페이스에 일별 카운트를 추가하면 다른 모듈에 새 책임이 새는 느낌. 대신 **admin 모듈 내부**에 cross-cutting 집계용 read-only repository를 둔다.

- [ ] **Step 7.1: `DashboardPolicyQueryRepository` 작성**

```java
package com.youthfit.admin.infrastructure.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
public class DashboardPolicyQueryRepository {

    private final JdbcTemplate jdbc;

    public DashboardPolicyQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countPolicyCreatedBetween(Instant from, Instant to) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM policy WHERE created_at >= ? AND created_at < ?",
                Long.class,
                LocalDateTime.ofInstant(from, ZoneOffset.UTC),
                LocalDateTime.ofInstant(to, ZoneOffset.UTC));
        return n == null ? 0L : n;
    }
}
```

테이블 이름이 `policy`가 아니면 실제 이름으로 교체. 컬럼명도 동일하게 확인.

- [ ] **Step 7.2: `PolicyIntakeStallSignalTest` 작성**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.infrastructure.persistence.DashboardPolicyQueryRepository;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyIntakeStallSignalTest {

    private final DashboardPolicyQueryRepository repo = mock(DashboardPolicyQueryRepository.class);
    private final DashboardThresholds thresholds = new DashboardThresholds(
            new DashboardThresholds.Llm(BigDecimal.ZERO, BigDecimal.ZERO),
            new DashboardThresholds.Ingestion(7),
            new DashboardThresholds.Enrichment(20),
            new DashboardThresholds.Email(BigDecimal.ZERO, 0),
            new DashboardThresholds.QnaCache(BigDecimal.ZERO),
            new DashboardThresholds.PolicyIntake(new BigDecimal("0.3"))
    );
    private final PolicyIntakeStallSignal signal = new PolicyIntakeStallSignal(repo, thresholds);

    @Test
    void empty_when_today_meets_baseline() {
        // 오늘 5, 직전 7일 70 → 일평균 10. 5 / 10 = 0.5 >= 0.3 → 정상
        when(repo.countPolicyCreatedBetween(any(), any()))
                .thenReturn(5L)    // 오늘
                .thenReturn(70L);  // 직전 7일
        assertThat(signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"))).isEmpty();
    }

    @Test
    void medium_when_today_below_ratio() {
        // 오늘 2, 직전 7일 70 → 일평균 10. 2/10=0.2 < 0.3
        when(repo.countPolicyCreatedBetween(any(), any()))
                .thenReturn(2L)
                .thenReturn(70L);
        Optional<DashboardSignalResult> r = signal.evaluate(Instant.parse("2026-05-22T05:00:00Z"));
        assertThat(r).isPresent();
        assertThat(r.get().severity()).isEqualTo(DashboardSeverity.MEDIUM);
    }

    @Test
    void empty_when_no_baseline() {
        // 직전 7일 0이면 비교 불가 → 정상으로 간주
        when(repo.countPolicyCreatedBetween(any(), any()))
                .thenReturn(0L)
                .thenReturn(0L);
        assertThat(signal.evaluate(Instant.now())).isEmpty();
    }
}
```

- [ ] **Step 7.3: `PolicyIntakeStallSignal` 구현**

```java
package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.admin.infrastructure.persistence.DashboardPolicyQueryRepository;
import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PolicyIntakeStallSignal implements DashboardSignal {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DashboardPolicyQueryRepository repo;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return "POLICY_INTAKE_STALL";
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        ZonedDateTime nowKst = ZonedDateTime.ofInstant(now, KST);
        LocalDate today = nowKst.toLocalDate();
        Instant todayStart = today.atStartOfDay(KST).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(KST).toInstant();
        Instant sevenDaysAgo = today.minusDays(7).atStartOfDay(KST).toInstant();

        long todayCount = repo.countPolicyCreatedBetween(todayStart, tomorrowStart);
        long priorWeek = repo.countPolicyCreatedBetween(sevenDaysAgo, todayStart);

        if (priorWeek == 0) return Optional.empty();
        BigDecimal dailyAvg = BigDecimal.valueOf(priorWeek).divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
        BigDecimal threshold = dailyAvg.multiply(thresholds.getPolicyIntake().getStallRatio());
        if (BigDecimal.valueOf(todayCount).compareTo(threshold) >= 0) return Optional.empty();

        return Optional.of(new DashboardSignalResult(
                code(),
                DashboardSeverity.MEDIUM,
                "오늘 신규 정책 " + todayCount + "건 (7일 평균 " + dailyAvg.setScale(1, RoundingMode.HALF_UP) + "건 대비 저조)",
                null,
                "/admin/ingestion",
                now
        ));
    }
}
```

- [ ] **Step 7.4: 테스트 통과 + Commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.signals.PolicyIntakeStallSignalTest"
```
Expected: 3 tests passed

```bash
git add backend/src/main/java/com/youthfit/admin/infrastructure/persistence/DashboardPolicyQueryRepository.java \
        backend/src/main/java/com/youthfit/admin/application/dashboard/signals/PolicyIntakeStallSignal.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard/signals/PolicyIntakeStallSignalTest.java
git commit -m "feat(admin): 신규 정책 수집 정체 이상 신호 추가"
```

---

## Task 8: Evaluator + AreaStatusBuilder

**Files:**
- Create: `DashboardSignalEvaluator.java`
- Create: `AreaStatusBuilder.java`
- Create: 두 클래스의 Test 파일

- [ ] **Step 8.1: `DashboardSignalEvaluatorTest` 작성**

```java
package com.youthfit.admin.application.dashboard;

import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSignalEvaluatorTest {

    @Test
    void collects_all_present_results_and_sorts_high_first() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        DashboardSignal med = stub("A", Optional.of(result("A", DashboardSeverity.MEDIUM, now.minusSeconds(60))));
        DashboardSignal high = stub("B", Optional.of(result("B", DashboardSeverity.HIGH, now.minusSeconds(120))));
        DashboardSignal none = stub("C", Optional.empty());

        DashboardSignalEvaluator evaluator = new DashboardSignalEvaluator(List.of(med, high, none));

        List<DashboardSignalResult> results = evaluator.evaluateAll(now);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).code()).isEqualTo("B");   // HIGH first
        assertThat(results.get(1).code()).isEqualTo("A");
    }

    @Test
    void same_severity_sorted_by_detectedAt_desc() {
        Instant now = Instant.parse("2026-05-22T05:00:00Z");
        DashboardSignal older = stub("A", Optional.of(result("A", DashboardSeverity.HIGH, now.minusSeconds(300))));
        DashboardSignal newer = stub("B", Optional.of(result("B", DashboardSeverity.HIGH, now.minusSeconds(60))));

        DashboardSignalEvaluator evaluator = new DashboardSignalEvaluator(List.of(older, newer));

        List<DashboardSignalResult> results = evaluator.evaluateAll(now);

        assertThat(results.get(0).code()).isEqualTo("B");   // newer first
    }

    @Test
    void thrown_signal_is_isolated_others_still_evaluated() {
        Instant now = Instant.now();
        DashboardSignal broken = code -> { throw new RuntimeException("boom"); };
        DashboardSignal ok = stub("OK", Optional.of(result("OK", DashboardSeverity.HIGH, now)));

        DashboardSignalEvaluator evaluator = new DashboardSignalEvaluator(List.of(broken, ok));

        List<DashboardSignalResult> results = evaluator.evaluateAll(now);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).code()).isEqualTo("OK");
    }

    private static DashboardSignal stub(String code, Optional<DashboardSignalResult> r) {
        return new DashboardSignal() {
            @Override public String code() { return code; }
            @Override public Optional<DashboardSignalResult> evaluate(Instant now) { return r; }
        };
    }

    private static DashboardSignalResult result(String code, DashboardSeverity sev, Instant at) {
        return new DashboardSignalResult(code, sev, "t", null, "/d", at);
    }
}
```

**Note:** "broken" 변수의 람다는 `DashboardSignal` 단일 메서드 인터페이스가 아니므로 람다 변환 불가 — 익명 클래스로 교체:
```java
DashboardSignal broken = new DashboardSignal() {
    @Override public String code() { return "BROKEN"; }
    @Override public Optional<DashboardSignalResult> evaluate(Instant now) { throw new RuntimeException("boom"); }
};
```

- [ ] **Step 8.2: `DashboardSignalEvaluator` 구현**

```java
package com.youthfit.admin.application.dashboard;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DashboardSignalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DashboardSignalEvaluator.class);

    private final List<DashboardSignal> signals;

    public List<DashboardSignalResult> evaluateAll(Instant now) {
        List<DashboardSignalResult> results = new ArrayList<>();
        for (DashboardSignal s : signals) {
            try {
                Optional<DashboardSignalResult> r = s.evaluate(now);
                r.ifPresent(results::add);
            } catch (RuntimeException ex) {
                log.warn("Dashboard signal {} failed: {}", s.code(), ex.getMessage(), ex);
            }
        }
        results.sort(Comparator
                .comparing(DashboardSignalResult::severity)               // HIGH < MEDIUM alphabetically
                .thenComparing(DashboardSignalResult::detectedAt, Comparator.reverseOrder()));
        return results;
    }

    public List<DashboardSignal> signals() {
        return signals;
    }
}
```

**중요:** `DashboardSeverity`의 enum 선언 순서가 `HIGH, MEDIUM`이라 `Comparator.comparing(severity)`는 HIGH 먼저 정렬됨 (enum `compareTo`는 ordinal 기반). 테스트도 같은 가정. enum 순서 변경 금지.

- [ ] **Step 8.3: `AreaStatusBuilder` 작성**

영역 6개 매핑 정의가 spec 3.2와 일치하도록.

```java
package com.youthfit.admin.application.dashboard;

import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AreaStatusBuilder {

    public enum Status { OK, WARN, CRITICAL }

    public record AreaKey(String key, String label, String deeplink, List<String> signalCodes) {}

    private static final List<AreaKey> AREAS = List.of(
            new AreaKey("ingestion", "Ingestion", "/admin/ingestion",
                    List.of("INGESTION_FAILURE", "INGESTION_STALE")),
            new AreaKey("enrichment", "Enrichment", "/admin/enrichment",
                    List.of("ENRICHMENT_FAILURE", "ENRICHMENT_BACKLOG")),
            new AreaKey("llm-cost", "LLM 비용", "/admin/llm-cost",
                    List.of("LLM_COST_SPIKE", "LLM_WEEKLY_BUDGET")),
            new AreaKey("email", "Email", "/admin/email",
                    List.of("EMAIL_FAILURE")),
            new AreaKey("qna-cache", "Q&A Cache", "/admin/qna-cache",
                    List.of("QNA_CACHE_HIT_DROP")),
            new AreaKey("policy-intake", "신규 정책", "/admin/ingestion",
                    List.of("POLICY_INTAKE_STALL"))
    );

    public List<AreaKey> areas() { return AREAS; }

    public Status statusFor(AreaKey area, List<DashboardSignalResult> firedResults) {
        DashboardSeverity worst = null;
        for (DashboardSignalResult r : firedResults) {
            if (area.signalCodes().contains(r.code())) {
                if (worst == null || r.severity().compareTo(worst) < 0) {
                    worst = r.severity();
                }
            }
        }
        if (worst == null) return Status.OK;
        return worst == DashboardSeverity.HIGH ? Status.CRITICAL : Status.WARN;
    }
}
```

- [ ] **Step 8.4: `AreaStatusBuilderTest` 작성**

```java
package com.youthfit.admin.application.dashboard;

import com.youthfit.admin.presentation.dto.response.DashboardSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AreaStatusBuilderTest {

    private final AreaStatusBuilder builder = new AreaStatusBuilder();

    @Test
    void area_with_no_signal_returns_ok() {
        AreaStatusBuilder.AreaKey ingestion = builder.areas().get(0);
        assertThat(builder.statusFor(ingestion, List.of())).isEqualTo(AreaStatusBuilder.Status.OK);
    }

    @Test
    void single_high_signal_returns_critical() {
        AreaStatusBuilder.AreaKey ingestion = builder.areas().get(0);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("INGESTION_FAILURE", DashboardSeverity.HIGH, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(ingestion, fired)).isEqualTo(AreaStatusBuilder.Status.CRITICAL);
    }

    @Test
    void medium_only_returns_warn() {
        AreaStatusBuilder.AreaKey enrichment = builder.areas().get(1);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("ENRICHMENT_BACKLOG", DashboardSeverity.MEDIUM, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(enrichment, fired)).isEqualTo(AreaStatusBuilder.Status.WARN);
    }

    @Test
    void mixed_signals_take_worst() {
        AreaStatusBuilder.AreaKey enrichment = builder.areas().get(1);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("ENRICHMENT_BACKLOG", DashboardSeverity.MEDIUM, "t", null, "/d", Instant.now()),
                new DashboardSignalResult("ENRICHMENT_FAILURE", DashboardSeverity.HIGH, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(enrichment, fired)).isEqualTo(AreaStatusBuilder.Status.CRITICAL);
    }

    @Test
    void signals_from_other_area_are_ignored() {
        AreaStatusBuilder.AreaKey email = builder.areas().get(3);
        List<DashboardSignalResult> fired = List.of(
                new DashboardSignalResult("INGESTION_FAILURE", DashboardSeverity.HIGH, "t", null, "/d", Instant.now())
        );
        assertThat(builder.statusFor(email, fired)).isEqualTo(AreaStatusBuilder.Status.OK);
    }
}
```

- [ ] **Step 8.5: 테스트 실행 + Commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.dashboard.*EvaluatorTest" --tests "com.youthfit.admin.application.dashboard.*BuilderTest"
```
Expected: 8 tests passed

```bash
git add backend/src/main/java/com/youthfit/admin/application/dashboard/DashboardSignalEvaluator.java \
        backend/src/main/java/com/youthfit/admin/application/dashboard/AreaStatusBuilder.java \
        backend/src/test/java/com/youthfit/admin/application/dashboard
git commit -m "feat(admin): 대시보드 신호 평가기·영역 상태 빌더 추가"
```

---

## Task 9: DTO + Service + Controller + ControllerTest

**Files:**
- Create: `presentation/dto/response/DashboardActionItemResponse.java`
- Create: `presentation/dto/response/DashboardAreaStatusResponse.java`
- Create: `presentation/dto/response/DashboardOverviewResponse.java`
- Create: `application/service/AdminDashboardOverviewService.java`
- Create: `presentation/controller/AdminDashboardApi.java`
- Create: `presentation/controller/AdminDashboardController.java`
- Create: `test/.../AdminDashboardControllerTest.java`

영역별 sparkline 7일 값은 일단 **빈 배열로 반환 (TODO 아닌 *명시적인 v1 결정*)** — sparkline 데이터 소스를 각 영역마다 끌어오는 건 별도 작업이고 카드 첫 출시에 큰 손실 없음. spec 의 5번 섹션에서 약속한 sparkline은 후속 1주 내 보강. v1 응답 스키마에는 `sparkline` 필드를 *항상 빈 배열로* 포함해 프론트 컴파일 차질을 막는다.

> **Spec 보강:** sparkline 데이터는 v1.1에서 채운다 — 첫 머지 시 빈 배열, 프론트는 빈 배열일 때 차트 영역을 숨김.

- [ ] **Step 9.1: DTO 3종 작성**

```java
// DashboardActionItemResponse.java
package com.youthfit.admin.presentation.dto.response;

import java.time.Instant;

public record DashboardActionItemResponse(
        String code,
        DashboardSeverity severity,
        String title,
        String detail,
        String deeplink,
        Instant detectedAt
) {}
```

```java
// DashboardAreaStatusResponse.java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardAreaStatusResponse(
        String key,
        String label,
        String status,     // "OK" | "WARN" | "CRITICAL"
        String summary,
        List<BigDecimal> sparkline,
        String deeplink
) {}
```

```java
// DashboardOverviewResponse.java
package com.youthfit.admin.presentation.dto.response;

import java.time.Instant;
import java.util.List;

public record DashboardOverviewResponse(
        Instant generatedAt,
        List<DashboardActionItemResponse> actionItems,
        List<DashboardAreaStatusResponse> areas
) {}
```

- [ ] **Step 9.2: `AdminDashboardOverviewService` 작성**

```java
package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dashboard.AreaStatusBuilder;
import com.youthfit.admin.application.dashboard.DashboardSignalEvaluator;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.presentation.dto.response.DashboardActionItemResponse;
import com.youthfit.admin.presentation.dto.response.DashboardAreaStatusResponse;
import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardOverviewService {

    private final DashboardSignalEvaluator evaluator;
    private final AreaStatusBuilder areaBuilder;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        Instant now = Instant.now();
        List<DashboardSignalResult> fired = evaluator.evaluateAll(now);

        List<DashboardActionItemResponse> actionItems = fired.stream()
                .map(r -> new DashboardActionItemResponse(
                        r.code(), r.severity(), r.title(), r.detail(), r.deeplink(), r.detectedAt()))
                .toList();

        List<DashboardAreaStatusResponse> areas = areaBuilder.areas().stream()
                .map(area -> {
                    AreaStatusBuilder.Status status = areaBuilder.statusFor(area, fired);
                    return new DashboardAreaStatusResponse(
                            area.key(),
                            area.label(),
                            status.name(),
                            summaryFor(area.key(), status, fired),
                            List.of(),                  // sparkline: v1.1에서 채움
                            area.deeplink());
                })
                .toList();

        return new DashboardOverviewResponse(now, actionItems, areas);
    }

    private String summaryFor(String areaKey, AreaStatusBuilder.Status status, List<DashboardSignalResult> fired) {
        return switch (status) {
            case OK -> "정상";
            case WARN -> "주의";
            case CRITICAL -> "확인 필요";
        };
    }
}
```

> **Spec 보강:** summary 텍스트는 일단 status 그대로의 한국어. 운영자 피드백을 받아 v1.1에서 영역별 실제 수치(예: "12 출처 활성")로 교체.

- [ ] **Step 9.3: `AdminDashboardApi` 인터페이스**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Dashboard", description = "지통실 대시보드 (ROLE_ADMIN 필수)")
public interface AdminDashboardApi {

    @Operation(summary = "대시보드 overview", description = "액션 큐 + 6개 영역 상태")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview();
}
```

- [ ] **Step 9.4: `AdminDashboardController` 작성**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminDashboardOverviewService;
import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import com.youthfit.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController implements AdminDashboardApi {

    private final AdminDashboardOverviewService service;

    @GetMapping("/overview")
    @Override
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.ok(service.getOverview()));
    }
}
```

- [ ] **Step 9.5: `AdminDashboardControllerTest` 작성 (`@WebMvcTest` 슬라이스)**

기존 admin controller test 한 개를 Read해서 보안·MockMvc 셋업 패턴을 정확히 따른다 (예: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminEnrichmentControllerTest.java`). 핵심 시나리오:

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminDashboardOverviewService;
import com.youthfit.admin.presentation.dto.response.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AdminDashboardOverviewService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void overview_returns_200_with_payload() throws Exception {
        when(service.getOverview()).thenReturn(new DashboardOverviewResponse(
                Instant.parse("2026-05-22T05:00:00Z"),
                List.of(new DashboardActionItemResponse(
                        "INGESTION_STALE", DashboardSeverity.HIGH, "stale", "x", "/admin/ingestion?filter=stale",
                        Instant.parse("2026-05-22T05:00:00Z"))),
                List.of()
        ));

        mvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionItems[0].code").value("INGESTION_STALE"))
                .andExpect(jsonPath("$.data.actionItems[0].severity").value("HIGH"));
    }

    @Test
    void overview_requires_authentication() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void overview_requires_admin_role() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/overview"))
                .andExpect(status().isForbidden());
    }
}
```

**Note:** 응답 JSON 구조가 `ApiResponse.ok(...)` 형태이므로 `$.data...`. 기존 admin controller test에서 jsonPath prefix를 확인.

- [ ] **Step 9.6: 테스트 통과 + Commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminDashboardControllerTest"
```
Expected: 3 tests passed

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminDashboardOverviewService.java \
        backend/src/main/java/com/youthfit/admin/presentation/controller/AdminDashboard*.java \
        backend/src/main/java/com/youthfit/admin/presentation/dto/response/Dashboard*.java \
        backend/src/test/java/com/youthfit/admin/presentation/controller/AdminDashboardControllerTest.java
git commit -m "feat(admin): 대시보드 overview API + Controller 추가"
```

---

## Task 10: 백엔드 통합 테스트

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/application/service/AdminDashboardOverviewServiceIntegrationTest.java`

전체 9개 신호 빈이 트리거되도록 fixture 데이터를 testcontainer DB에 셋업해 end-to-end 흐름 검증.

- [ ] **Step 10.1: 기존 `@SpringBootTest` 통합 테스트 1개를 Read해 testcontainer/profile 셋업 패턴 확인**

대상: `backend/src/test/java/com/youthfit/policy/application/service/` 또는 `ingestion` 통합 테스트.

- [ ] **Step 10.2: 통합 테스트 작성 (스켈레톤)**

```java
package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AdminDashboardOverviewServiceIntegrationTest {

    @Autowired AdminDashboardOverviewService service;

    @Test
    void returns_payload_with_six_areas_even_when_no_data() {
        DashboardOverviewResponse r = service.getOverview();
        assertThat(r.areas()).hasSize(6);
        assertThat(r.areas()).extracting("key")
                .containsExactly("ingestion", "enrichment", "llm-cost", "email", "qna-cache", "policy-intake");
        // 빈 DB → 모두 OK
        assertThat(r.areas()).allSatisfy(a -> assertThat(a.status()).isEqualTo("OK"));
        assertThat(r.actionItems()).isEmpty();
    }
}
```

> v1 한정: fixture 셋업 확장은 후속. **빈 DB에서 6개 영역 OK 반환**을 최소 통합 검증으로 두고, 다양한 트리거 시나리오는 단위 테스트에 위임. 단위 테스트가 9종 신호 각각 임계치 경계를 이미 커버하므로 통합 테스트는 wiring 확인 목적.

- [ ] **Step 10.3: 테스트 통과 + Commit**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.application.service.AdminDashboardOverviewServiceIntegrationTest"
```
Expected: 1 test passed

```bash
git add backend/src/test/java/com/youthfit/admin/application/service/AdminDashboardOverviewServiceIntegrationTest.java
git commit -m "test(admin): 대시보드 overview 통합 테스트 (wiring 검증)"
```

---

## Task 11: 프론트엔드 — types · api · hook

**Files:**
- Create: `frontend/src/types/adminDashboard.ts`
- Create: `frontend/src/apis/adminDashboard.api.ts`
- Create: `frontend/src/hooks/queries/useAdminDashboardOverview.ts`

- [ ] **Step 11.1: 기존 `adminIngestion.api.ts` 또는 동등 파일 1개를 Read해 ky 호출 패턴 확인**

```bash
ls frontend/src/apis/ | grep admin
```

- [ ] **Step 11.2: `types/adminDashboard.ts` 작성**

```ts
export type DashboardSeverity = 'HIGH' | 'MEDIUM';
export type AreaStatus = 'OK' | 'WARN' | 'CRITICAL';

export interface DashboardActionItem {
  code: string;
  severity: DashboardSeverity;
  title: string;
  detail: string | null;
  deeplink: string;
  detectedAt: string;
}

export interface DashboardAreaStatus {
  key: string;
  label: string;
  status: AreaStatus;
  summary: string;
  sparkline: number[];
  deeplink: string;
}

export interface DashboardOverview {
  generatedAt: string;
  actionItems: DashboardActionItem[];
  areas: DashboardAreaStatus[];
}
```

- [ ] **Step 11.3: `apis/adminDashboard.api.ts` 작성**

```ts
import { client } from './client';
import type { DashboardOverview } from '@/types/adminDashboard';

interface ApiResponse<T> { data: T }

export async function getDashboardOverview(): Promise<DashboardOverview> {
  const res = await client.get('api/v1/admin/dashboard/overview').json<ApiResponse<DashboardOverview>>();
  return res.data;
}
```

ky 인스턴스 import 경로(`./client`)는 기존 `apis/*.api.ts`와 일치시킬 것. 기존 파일에서 `import { client } from './client'` 또는 동등 경로 확인.

- [ ] **Step 11.4: `hooks/queries/useAdminDashboardOverview.ts` 작성**

```ts
import { useQuery } from '@tanstack/react-query';
import { getDashboardOverview } from '@/apis/adminDashboard.api';

export function useAdminDashboardOverview() {
  return useQuery({
    queryKey: ['admin', 'dashboard', 'overview'],
    queryFn: getDashboardOverview,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    staleTime: 25_000,
  });
}
```

- [ ] **Step 11.5: 타입체크 통과**

```bash
cd frontend && npm run build -- --mode development
```
또는:
```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors

- [ ] **Step 11.6: Commit**

```bash
git add frontend/src/types/adminDashboard.ts \
        frontend/src/apis/adminDashboard.api.ts \
        frontend/src/hooks/queries/useAdminDashboardOverview.ts
git commit -m "feat(fe/admin): 대시보드 overview API/hook/type 추가"
```

---

## Task 12: 프론트엔드 — atomic 컴포넌트 (Sparkline + StatusBadge)

**Files:**
- Create: `frontend/src/components/admin/dashboard/Sparkline.tsx`
- Create: `frontend/src/components/admin/dashboard/StatusBadge.tsx`
- Create: 각 `__tests__/*.test.tsx`

- [ ] **Step 12.1: `Sparkline.test.tsx` 작성**

```tsx
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Sparkline from '../Sparkline';

describe('Sparkline', () => {
  it('renders empty when values are empty', () => {
    const { container } = render(<Sparkline values={[]} />);
    expect(container.querySelector('svg')).toBeNull();
  });

  it('renders polyline with correct point count', () => {
    const { container } = render(<Sparkline values={[1, 2, 3, 4]} />);
    const polyline = container.querySelector('polyline');
    expect(polyline).not.toBeNull();
    const points = polyline!.getAttribute('points')!.trim().split(/\s+/);
    expect(points).toHaveLength(4);
  });

  it('normalizes values so max value is at top', () => {
    const { container } = render(<Sparkline values={[0, 10]} />);
    const points = container.querySelector('polyline')!.getAttribute('points')!.trim().split(/\s+/);
    // 값 10에 해당하는 점의 y좌표는 0 (top), 값 0의 y좌표는 24 (bottom)
    const ys = points.map((p) => parseFloat(p.split(',')[1]));
    expect(ys[0]).toBeCloseTo(24, 1);
    expect(ys[1]).toBeCloseTo(0, 1);
  });
});
```

- [ ] **Step 12.2: `Sparkline.tsx` 구현**

```tsx
interface SparklineProps {
  values: number[];
}

export default function Sparkline({ values }: SparklineProps) {
  if (values.length === 0) return null;
  const max = Math.max(...values, 1);
  const points = values
    .map((v, i) => {
      const x = values.length === 1 ? 40 : (i / (values.length - 1)) * 80;
      const y = 24 - (v / max) * 24;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
  return (
    <svg viewBox="0 0 80 24" className="h-6 w-20 text-slate-400" aria-hidden>
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}
```

- [ ] **Step 12.3: `StatusBadge.test.tsx` 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from '../StatusBadge';

describe('StatusBadge', () => {
  it('renders OK in green', () => {
    const { container } = render(<StatusBadge status="OK" />);
    expect(screen.getByText(/정상/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass('bg-success-50');
  });

  it('renders WARN in amber', () => {
    const { container } = render(<StatusBadge status="WARN" />);
    expect(screen.getByText(/주의/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass('bg-amber-50');
  });

  it('renders CRITICAL in error', () => {
    const { container } = render(<StatusBadge status="CRITICAL" />);
    expect(screen.getByText(/확인 필요/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass('bg-error-50');
  });
});
```

- [ ] **Step 12.4: `StatusBadge.tsx` 구현**

```tsx
import type { AreaStatus } from '@/types/adminDashboard';

const CONFIG: Record<AreaStatus, { label: string; className: string; icon: string }> = {
  OK: { label: '정상', className: 'bg-success-50 text-success-700', icon: '✓' },
  WARN: { label: '주의', className: 'bg-amber-50 text-amber-700', icon: '⚠' },
  CRITICAL: { label: '확인 필요', className: 'bg-error-50 text-error-700', icon: '⚠' },
};

export default function StatusBadge({ status }: { status: AreaStatus }) {
  const c = CONFIG[status];
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${c.className}`}>
      <span aria-hidden>{c.icon}</span>
      {c.label}
    </span>
  );
}
```

- [ ] **Step 12.5: 테스트 + Commit**

```bash
cd frontend && npm test -- Sparkline StatusBadge
```
Expected: 6 tests passed

```bash
git add frontend/src/components/admin/dashboard/Sparkline.tsx \
        frontend/src/components/admin/dashboard/StatusBadge.tsx \
        frontend/src/components/admin/dashboard/__tests__/
git commit -m "feat(fe/admin): Sparkline·StatusBadge 컴포넌트 추가"
```

---

## Task 13: ActionItemRow · ActionQueueSection · AllClearBanner

**Files:**
- Create: `frontend/src/components/admin/dashboard/ActionItemRow.tsx`
- Create: `frontend/src/components/admin/dashboard/ActionQueueSection.tsx`
- Create: `frontend/src/components/admin/dashboard/AllClearBanner.tsx`
- Create: 각 테스트 파일

- [ ] **Step 13.1: `ActionItemRow.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import ActionItemRow from '../ActionItemRow';

const item = {
  code: 'INGESTION_STALE',
  severity: 'HIGH' as const,
  title: '출처 2개가 7일 이상 미갱신',
  detail: 'onlineyouthcenter.kr, gov24.go.kr',
  deeplink: '/admin/ingestion?filter=stale',
  detectedAt: '2026-05-22T05:00:00Z',
};

describe('ActionItemRow', () => {
  it('renders title, detail and link', () => {
    render(<BrowserRouter><ActionItemRow item={item} /></BrowserRouter>);
    expect(screen.getByText(item.title)).toBeInTheDocument();
    expect(screen.getByText(item.detail)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /확인/ })).toHaveAttribute('href', item.deeplink);
  });

  it('uses red dot for HIGH severity', () => {
    const { container } = render(<BrowserRouter><ActionItemRow item={item} /></BrowserRouter>);
    expect(container.querySelector('.bg-error-500')).not.toBeNull();
  });

  it('uses amber dot for MEDIUM severity', () => {
    const med = { ...item, severity: 'MEDIUM' as const };
    const { container } = render(<BrowserRouter><ActionItemRow item={med} /></BrowserRouter>);
    expect(container.querySelector('.bg-amber-500')).not.toBeNull();
  });
});
```

- [ ] **Step 13.2: `ActionItemRow.tsx`**

```tsx
import { Link } from 'react-router-dom';
import type { DashboardActionItem } from '@/types/adminDashboard';

export default function ActionItemRow({ item }: { item: DashboardActionItem }) {
  const dotClass = item.severity === 'HIGH' ? 'bg-error-500' : 'bg-amber-500';
  return (
    <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 py-3 last:border-b-0">
      <div className="flex items-start gap-3">
        <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${dotClass}`} aria-hidden />
        <div>
          <p className="text-sm font-semibold text-slate-900">{item.title}</p>
          {item.detail && <p className="mt-0.5 text-xs text-slate-500">{item.detail}</p>}
        </div>
      </div>
      <Link
        to={item.deeplink}
        className="shrink-0 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
      >
        확인
      </Link>
    </div>
  );
}
```

- [ ] **Step 13.3: `ActionQueueSection.tsx`**

```tsx
import type { DashboardActionItem } from '@/types/adminDashboard';
import ActionItemRow from './ActionItemRow';

export default function ActionQueueSection({ items }: { items: DashboardActionItem[] }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
      <header className="border-b border-slate-100 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">
          <span className="text-error-500" aria-hidden>⚠</span> Action Required ({items.length})
        </h2>
      </header>
      <div>{items.map((i) => <ActionItemRow key={i.code} item={i} />)}</div>
    </section>
  );
}
```

- [ ] **Step 13.4: `AllClearBanner.tsx`**

```tsx
export default function AllClearBanner() {
  return (
    <section className="flex items-center gap-2 rounded-xl border border-success-100 bg-success-50 px-4 py-3 text-sm text-success-700">
      <span aria-hidden>✅</span>
      <span>현재 이상 없음 — 운영 정상</span>
    </section>
  );
}
```

- [ ] **Step 13.5: Test + Commit**

```bash
cd frontend && npm test -- ActionItemRow
```
Expected: 3 tests passed

```bash
git add frontend/src/components/admin/dashboard/ActionItemRow.tsx \
        frontend/src/components/admin/dashboard/ActionQueueSection.tsx \
        frontend/src/components/admin/dashboard/AllClearBanner.tsx \
        frontend/src/components/admin/dashboard/__tests__/ActionItemRow.test.tsx
git commit -m "feat(fe/admin): 액션 큐 섹션·All clear 배너 추가"
```

---

## Task 14: AreaStatusCard + AreaStatusGrid

**Files:**
- Create: `frontend/src/components/admin/dashboard/AreaStatusCard.tsx`
- Create: `frontend/src/components/admin/dashboard/AreaStatusGrid.tsx`
- Create: `__tests__/AreaStatusCard.test.tsx`

- [ ] **Step 14.1: `AreaStatusCard.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import AreaStatusCard from '../AreaStatusCard';

const area = {
  key: 'ingestion',
  label: 'Ingestion',
  status: 'CRITICAL' as const,
  summary: '확인 필요',
  sparkline: [3, 5, 4, 8, 6, 7, 4],
  deeplink: '/admin/ingestion',
};

describe('AreaStatusCard', () => {
  it('renders label, summary and status badge', () => {
    render(<BrowserRouter><AreaStatusCard area={area} /></BrowserRouter>);
    expect(screen.getByText('Ingestion')).toBeInTheDocument();
    expect(screen.getByText(/확인 필요/)).toBeInTheDocument();
  });

  it('wraps content in a link to deeplink', () => {
    render(<BrowserRouter><AreaStatusCard area={area} /></BrowserRouter>);
    expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/ingestion');
  });

  it('hides sparkline when values are empty', () => {
    const empty = { ...area, sparkline: [] };
    const { container } = render(<BrowserRouter><AreaStatusCard area={empty} /></BrowserRouter>);
    expect(container.querySelector('svg')).toBeNull();
  });
});
```

- [ ] **Step 14.2: `AreaStatusCard.tsx`**

```tsx
import { Link } from 'react-router-dom';
import type { DashboardAreaStatus } from '@/types/adminDashboard';
import StatusBadge from './StatusBadge';
import Sparkline from './Sparkline';

export default function AreaStatusCard({ area }: { area: DashboardAreaStatus }) {
  return (
    <Link
      to={area.deeplink}
      className="flex flex-col gap-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:shadow-md"
    >
      <div className="flex items-start justify-between">
        <h3 className="text-sm font-semibold text-slate-900">{area.label}</h3>
        <StatusBadge status={area.status} />
      </div>
      <div className="flex items-end justify-between">
        <p className="text-sm text-slate-600">{area.summary}</p>
        <Sparkline values={area.sparkline} />
      </div>
    </Link>
  );
}
```

- [ ] **Step 14.3: `AreaStatusGrid.tsx`**

```tsx
import type { DashboardAreaStatus } from '@/types/adminDashboard';
import AreaStatusCard from './AreaStatusCard';

export default function AreaStatusGrid({ areas }: { areas: DashboardAreaStatus[] }) {
  return (
    <section>
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">영역별 상태 ({areas.length})</h2>
        <span className="text-xs text-slate-500">최근 7일</span>
      </header>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {areas.map((a) => <AreaStatusCard key={a.key} area={a} />)}
      </div>
    </section>
  );
}
```

- [ ] **Step 14.4: Test + Commit**

```bash
cd frontend && npm test -- AreaStatusCard
```
Expected: 3 tests passed

```bash
git add frontend/src/components/admin/dashboard/AreaStatusCard.tsx \
        frontend/src/components/admin/dashboard/AreaStatusGrid.tsx \
        frontend/src/components/admin/dashboard/__tests__/AreaStatusCard.test.tsx
git commit -m "feat(fe/admin): 영역별 상태 카드·그리드 추가"
```

---

## Task 15: AdminDashboardPage 전면 교체 + 페이지 테스트

**Files:**
- Modify: `frontend/src/pages/admin/AdminDashboardPage.tsx` (전면 재작성)
- Modify: `frontend/src/pages/admin/__tests__/AdminDashboardPage.test.tsx` (MSW 시나리오 교체)

- [ ] **Step 15.1: 기존 `__tests__/AdminDashboardPage.test.tsx` Read해 MSW 셋업 패턴 확인**

```bash
cat frontend/src/pages/admin/__tests__/AdminDashboardPage.test.tsx | head -60
```

기존 테스트가 MSW로 admin ping을 mocking 중이면 그 패턴을 그대로 따라 overview endpoint를 추가.

- [ ] **Step 15.2: `AdminDashboardPage.tsx` 전면 재작성**

```tsx
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminSkeleton from '@/components/admin/AdminSkeleton';
import ActionQueueSection from '@/components/admin/dashboard/ActionQueueSection';
import AllClearBanner from '@/components/admin/dashboard/AllClearBanner';
import AreaStatusGrid from '@/components/admin/dashboard/AreaStatusGrid';
import { useAdminDashboardOverview } from '@/hooks/queries/useAdminDashboardOverview';

function formatRelative(iso: string): string {
  const diffSec = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (diffSec < 60) return `${diffSec}초 전`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}분 전`;
  return `${Math.floor(diffSec / 3600)}시간 전`;
}

export default function AdminDashboardPage() {
  const overview = useAdminDashboardOverview();

  if (overview.isLoading) return <AdminSkeleton />;

  if (overview.isError || !overview.data) {
    return (
      <div className="space-y-6">
        <AdminPageHeader title="관리자 대시보드" description="데이터 조회에 실패했습니다." />
        <div className="rounded-xl border border-error-200 bg-error-50 p-4 text-sm text-error-700">
          <p>대시보드 데이터를 불러오지 못했습니다.</p>
          <button
            onClick={() => overview.refetch()}
            className="mt-2 rounded-md border border-error-300 bg-white px-3 py-1 text-xs text-error-700 hover:bg-error-50"
          >
            다시 시도
          </button>
        </div>
      </div>
    );
  }

  const { actionItems, areas, generatedAt } = overview.data;

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="관리자 대시보드"
        description={`마지막 갱신 ${formatRelative(generatedAt)}`}
        status={
          <span className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 shadow-sm md:inline-flex">
            <span
              className={`h-1.5 w-1.5 rounded-full ${
                overview.isFetching ? 'bg-amber-500 animate-pulse' : 'bg-success-500'
              }`}
              aria-hidden
            />
            {overview.isFetching ? '갱신 중…' : '실시간'}
          </span>
        }
      />

      {actionItems.length > 0 ? <ActionQueueSection items={actionItems} /> : <AllClearBanner />}
      <AreaStatusGrid areas={areas} />
    </div>
  );
}
```

- [ ] **Step 15.3: 페이지 테스트 (MSW 4 시나리오)**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { BrowserRouter } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import AdminDashboardPage from '../AdminDashboardPage';

const URL = 'http://localhost/api/v1/admin/dashboard/overview';

const server = setupServer();
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter><AdminDashboardPage /></BrowserRouter>
    </QueryClientProvider>
  );
}

describe('AdminDashboardPage', () => {
  it('shows loading skeleton initially', () => {
    server.use(http.get(URL, async () => { await new Promise((r) => setTimeout(r, 50)); return HttpResponse.json({ data: { generatedAt: new Date().toISOString(), actionItems: [], areas: [] } }); }));
    renderPage();
    // AdminSkeleton 마커 클래스 또는 ARIA 확인 — 기존 컴포넌트 확인 후 교체
    expect(document.body.textContent).not.toContain('현재 이상 없음');
  });

  it('shows all-clear banner when no action items', async () => {
    server.use(http.get(URL, () => HttpResponse.json({
      data: {
        generatedAt: new Date().toISOString(),
        actionItems: [],
        areas: [{ key: 'ingestion', label: 'Ingestion', status: 'OK', summary: '정상', sparkline: [], deeplink: '/admin/ingestion' }],
      },
    })));
    renderPage();
    await waitFor(() => expect(screen.getByText(/현재 이상 없음/)).toBeInTheDocument());
  });

  it('shows action queue when items present', async () => {
    server.use(http.get(URL, () => HttpResponse.json({
      data: {
        generatedAt: new Date().toISOString(),
        actionItems: [{
          code: 'INGESTION_STALE', severity: 'HIGH',
          title: '출처 2개 미갱신', detail: 'a, b',
          deeplink: '/admin/ingestion?filter=stale',
          detectedAt: new Date().toISOString(),
        }],
        areas: [],
      },
    })));
    renderPage();
    await waitFor(() => expect(screen.getByText('출처 2개 미갱신')).toBeInTheDocument());
    expect(screen.getByText(/Action Required \(1\)/)).toBeInTheDocument();
  });

  it('shows error state and retry button on failure', async () => {
    server.use(http.get(URL, () => HttpResponse.json({}, { status: 500 })));
    renderPage();
    await waitFor(() => expect(screen.getByText(/불러오지 못했습니다/)).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /다시 시도/ })).toBeInTheDocument();
  });
});
```

**Note for implementer:** `URL` 호스트 prefix는 ky 인스턴스의 `prefixUrl` 설정과 MSW 절대 URL이 맞도록. 기존 admin 페이지 테스트의 baseURL 패턴을 참고 — `vite proxy` 환경에선 `http://localhost/...`로 잡으면 보통 매치. 잘못 잡혀 모킹이 안 되면 MSW의 `passthrough` 콘솔 로그를 보고 실제 호출 URL로 교체.

- [ ] **Step 15.4: 테스트 통과**

```bash
cd frontend && npm test -- AdminDashboardPage
```
Expected: 4 tests passed

- [ ] **Step 15.5: 수동 검증**

```bash
cd backend && ./gradlew bootRun &
cd frontend && npm run dev
```
- 브라우저에서 `/admin` 접속
- ROLE_ADMIN 계정으로 로그인
- 빈 DB 환경에서 6개 영역 카드가 모두 OK 표시, "현재 이상 없음" 배너 표시
- 30초 후 헤더 인디케이터가 잠깐 amber로 깜빡이는지 확인 (폴링)
- Network 탭에서 `/api/v1/admin/dashboard/overview`가 30초 간격으로 호출되는지 확인

- [ ] **Step 15.6: Commit**

```bash
git add frontend/src/pages/admin/AdminDashboardPage.tsx \
        frontend/src/pages/admin/__tests__/AdminDashboardPage.test.tsx
git commit -m "feat(fe/admin): 대시보드 페이지를 지통실(액션 큐+영역 그리드)로 교체"
```

---

## Task 16: 전체 회귀 확인 + spec 상태 갱신

- [ ] **Step 16.1: 백엔드 전체 테스트**

```bash
cd backend && ./gradlew test
```
Expected: BUILD SUCCESSFUL, all green

- [ ] **Step 16.2: 프론트엔드 전체 테스트 + 빌드**

```bash
cd frontend && npm test && npm run build
```
Expected: all green, build artifact 생성

- [ ] **Step 16.3: spec 파일을 `DONE_` 접두사로 이동**

```bash
git mv docs/superpowers/specs/2026-05-22-admin-dashboard-design.md \
       docs/superpowers/specs/DONE_2026-05-22-admin-dashboard-design.md
git mv docs/superpowers/plans/2026-05-22-admin-dashboard.md \
       docs/superpowers/plans/DONE_2026-05-22-admin-dashboard.md
git commit -m "chore(docs): admin dashboard spec/plan 에 DONE_ 접두사 적용"
```

---

## Spec Coverage Checklist (self-review)

- [x] **Spec 1.1 (1순위 용도)** → Task 15에서 액션 큐가 항상 최상단, 빈 상태에 All Clear 표시
- [x] **Spec 1.2 (비범위)** → 본 plan에 슬랙/SSE/음소거/UI 임계치 편집 없음
- [x] **Spec 2 (레이아웃)** → Task 13/14/15에서 액션 큐 + 6칸 그리드 + 헤더 모두 구현
- [x] **Spec 3 (이상 신호 9종)** → Task 2~7에서 9종 신호 모두 구현
- [x] **Spec 3.1 (정렬 규칙)** → Task 8의 evaluator 정렬 + 단위 테스트 검증
- [x] **Spec 3.2 (영역 카드 매핑)** → Task 8 `AreaStatusBuilder` + 단위 테스트 검증
- [x] **Spec 4.1/4.2 (API + 디렉토리)** → Task 9에서 endpoint·DTO·Controller·Service
- [x] **Spec 4.3 (Signal 추상화 + 빈 자동 수집)** → Task 1 인터페이스 + Task 8 evaluator
- [x] **Spec 4.4 (`DashboardSignal` 인터페이스)** → Task 1 정의 일치
- [x] **Spec 4.5 (임계치 외부화)** → Task 1 `DashboardThresholds` + yaml
- [x] **Spec 4.6 (부분 실패 격리)** → Task 8 try/catch + 단위 테스트
- [x] **Spec 4.7 (성능)** → 캐시 없음 (어드민 트래픽 낮음). 후속 검토.
- [x] **Spec 5 (프론트 디렉토리)** → Task 11~15 모두 일치
- [x] **Spec 5.2 (페이지 골격)** → Task 15 page 코드와 일치
- [x] **Spec 5.3 (폴링 설정)** → Task 11 hook과 일치
- [x] **Spec 5.4 (상태 처리)** → Task 15 page 코드에 4상태 모두 처리
- [x] **Spec 5.5 (Sparkline)** → Task 12 SVG polyline 구현
- [x] **Spec 5.6 (Status Badge 색상)** → Task 12 StatusBadge 구현
- [x] **Spec 6.1/6.2 (테스트 전략)** → 백엔드 단위/통합 + 프론트 컴포넌트/페이지 테스트 모두 포함
- [x] **Spec 7 (롤아웃)** → Task 15에서 placeholder 페이지 전면 교체, 공용 컴포넌트 유지

**Sparkline 데이터는 v1에서 비워둔다 (spec 보강 메모 추가).** summary 텍스트도 v1.1에서 영역별 실수치로 채울 예정 — 두 항목 모두 plan과 spec에서 명시적으로 결정.

## Type/Method Name Consistency

- `DashboardSignal#evaluate(Instant)` → 9개 Signal 구현 모두 동일 시그니처
- `DashboardSignalResult` 필드명 (code, severity, title, detail, deeplink, detectedAt) → DTO 필드명과 1:1
- `AreaStatusBuilder.Status` (OK/WARN/CRITICAL) → 프론트 `AreaStatus` 타입과 1:1
- Frontend `DashboardOverview.areas[].status` → 백엔드 `DashboardAreaStatusResponse.status` (둘 다 string enum `OK|WARN|CRITICAL`)
- Frontend `DashboardActionItem.severity` → 백엔드 `DashboardSeverity` (둘 다 `HIGH|MEDIUM`)
- API path `/api/v1/admin/dashboard/overview` → Controller `@RequestMapping("/api/v1/admin/dashboard") + @GetMapping("/overview")` 일치

## Risks / Watch-outs

1. **`EmailSendStatus` enum 이름**: `FAILED`/`BOUNCED` 여부를 Read로 확인. 모듈 내 실제 값 사용.
2. **`EnrichmentJobStatus` enum 이름**: 동일하게 실제 값 확인.
3. **`EnrichmentReviewSummaryResult` accessor**: Task 3 미리뷰 후보 카운트 필드명 확정 필요.
4. **`policy` 테이블 컬럼명**: `created_at`이 아닐 수 있음 — Task 7 native query 작성 전 schema 확인.
5. **`IngestionRunLogRepository#staleSources` 파라미터 타입**: `Instant` vs `LocalDateTime` — Task 2 구현 시 확인.
6. **MSW URL 매칭**: Task 15 페이지 테스트는 ky `prefixUrl` 설정과 MSW handler URL을 일치시킬 것.
7. **`@ConfigurationPropertiesScan`**: Task 1.6 — 기존 메인 클래스에 이미 있는지 Read로 확인 후 결정.
