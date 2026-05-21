# Admin Enrichment Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 enrichment 빈약·실패 정책을 식별하고, 검증된 reference URL 을 입력하면 n8n force-enrich 워크플로우로 재크롤·재 enrich 하는 운영 워크플로우를 구현한다.

**Architecture:** 신규 `EnrichmentJob` 테이블 + on-the-fly `needsReview` 판정 + `PolicyReferenceSite.source` 플래그(AUTO/ADMIN). 백엔드는 잡 생성 후 n8n webhook 호출 → n8n 이 기존 ingestion 엔드포인트로 결과 송신 + `/api/internal/enrichment/jobs/{id}/callback` 으로 라이프사이클 신호. 프론트엔드는 `AdminIngestionPage` 신규 탭 + 3초/5초 backoff 폴링.

**Tech Stack:** Spring Boot 4.x · Java 21 · Hibernate 6 (`@JdbcTypeCode(SqlTypes.JSON)`) · Spring Security · React + TypeScript + Vite · TanStack Query · Vitest + Testing Library · n8n.

**스펙 참조:** `docs/superpowers/specs/2026-05-21-admin-enrichment-review-design.md`

---

## File Structure

### 백엔드 (com.youthfit)

신규:
- `policy/domain/model/PolicyReferenceSiteSource.java` — enum AUTO/ADMIN
- `policy/domain/model/EnrichmentJob.java` — entity
- `policy/domain/model/EnrichmentJobStatus.java` — enum PENDING/RUNNING/SUCCESS/FAILED
- `policy/domain/repository/EnrichmentJobRepository.java` — interface
- `policy/infrastructure/persistence/EnrichmentJobJpaRepository.java`
- `policy/infrastructure/persistence/EnrichmentJobRepositoryImpl.java`
- `policy/domain/service/EnrichmentReviewPolicy.java` — needsReview 판정
- `policy/domain/service/PolicyReferenceSiteMerger.java` — ADMIN/AUTO 머지
- `policy/application/service/EnrichmentJobService.java` — create/complete 트랜잭션 경계
- `policy/application/service/EnrichmentJobTimeoutScheduler.java` — 5분 타임아웃
- `policy/infrastructure/N8nForceEnrichClient.java` — RestClient 기반 webhook 호출
- `policy/presentation/dto/*` — 후보 목록·summary·잡 생성 요청/응답 DTO
- `admin/presentation/controller/AdminEnrichmentController.java`
- `ingestion/presentation/controller/InternalEnrichmentJobCallbackController.java`
- `src/main/resources/sql/2026-05-21-policy-reference-site-source.sql`
- `src/main/resources/sql/2026-05-21-enrichment-job.sql`

수정:
- `policy/domain/model/PolicyReferenceSite.java` — record 에 source 필드 추가
- (해당 record 를 생성하는 모든 코드: ingestion 측 매퍼) — 컴파일 깨지므로 source 명시 필요

테스트 (mirror):
- `policy/domain/service/EnrichmentReviewPolicyTest.java`
- `policy/domain/service/PolicyReferenceSiteMergerTest.java`
- `policy/application/service/EnrichmentJobServiceTest.java`
- `policy/application/service/EnrichmentJobTimeoutSchedulerTest.java`
- `policy/infrastructure/N8nForceEnrichClientTest.java`
- `admin/presentation/controller/AdminEnrichmentControllerTest.java`
- `ingestion/presentation/controller/InternalEnrichmentJobCallbackControllerTest.java`
- `policy/application/service/EnrichmentJobLifecycleIntegrationTest.java`

### 프론트엔드 (frontend/src)

신규:
- `apis/adminEnrichment.ts` — fetch wrapper + 타입
- `hooks/useAdminEnrichment.ts` — react-query hooks
- `pages/admin/AdminEnrichmentReviewTab.tsx`
- `pages/admin/enrichment/EnrichmentSummaryCards.tsx`
- `pages/admin/enrichment/EnrichmentCandidateTable.tsx`
- `pages/admin/enrichment/EnrichmentReviewPanel.tsx`
- `pages/admin/enrichment/EnrichmentReferenceSiteEditor.tsx`
- `pages/admin/enrichment/EnrichmentJobBadge.tsx`
- `types/adminEnrichment.ts`

수정:
- `pages/admin/AdminIngestionPage.tsx` — 탭 시스템 도입(`수집 현황` / `Enrichment 검토`)

테스트 (mirror):
- `pages/admin/enrichment/__tests__/EnrichmentCandidateTable.test.tsx`
- `pages/admin/enrichment/__tests__/EnrichmentReviewPanel.test.tsx`
- `pages/admin/enrichment/__tests__/EnrichmentReferenceSiteEditor.test.tsx`
- `hooks/__tests__/useAdminEnrichment.test.tsx`

### n8n

신규:
- `n8n/workflows/force-enrich.json`
- `n8n/workflows/__fixtures__/force-enrich/verify.mjs`

### 문서

수정:
- `docs/OPS.md` — `N8N_FORCE_ENRICH_WEBHOOK_URL` 환경변수 추가

---

## 빌드·테스트 명령 (참고)

- 백엔드 전체 테스트: `cd backend && ./gradlew test`
- 백엔드 단일 클래스: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.EnrichmentReviewPolicyTest"`
- 백엔드 빌드: `cd backend && ./gradlew build -x test`
- 프론트엔드 테스트: `cd frontend && npm test -- src/path/file.test.tsx`
- 프론트엔드 빌드: `cd frontend && npm run build`

---

## Stage A — 데이터 모델 & 마이그레이션

### Task 1: SQL 마이그레이션 — `policy.reference_sites` 의 각 원소에 `source` 백필

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-21-policy-reference-site-source.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- 2026-05-21-policy-reference-site-source.sql
-- 기존 policy.reference_sites JSONB 의 각 원소에 source 필드를 'AUTO' 로 백필한다.
-- 이미 source 키가 있는 원소는 그대로 둔다 (멱등).
UPDATE policy
SET reference_sites = (
  SELECT jsonb_agg(
    CASE
      WHEN elem ? 'source' THEN elem
      ELSE elem || jsonb_build_object('source', 'AUTO')
    END
  )
  FROM jsonb_array_elements(reference_sites) AS elem
)
WHERE jsonb_typeof(reference_sites) = 'array'
  AND jsonb_array_length(reference_sites) > 0;
```

- [ ] **Step 2: 로컬 DB 에서 dry-run 실행 (사용자 컨펌 단계)**

본 SQL 은 운영 적용 직전에 sue 가 직접 실행한다. 계획 시점에서는 파일 생성만 한다.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-21-policy-reference-site-source.sql
git commit -m "chore(sql): policy.reference_sites 원소에 source=AUTO 백필 마이그레이션"
```

---

### Task 2: SQL 마이그레이션 — `enrichment_job` 테이블

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-21-enrichment-job.sql`

- [ ] **Step 1: 테이블 + 인덱스 SQL 작성**

```sql
-- 2026-05-21-enrichment-job.sql
CREATE TABLE IF NOT EXISTS enrichment_job (
  id              BIGSERIAL PRIMARY KEY,
  policy_id       BIGINT      NOT NULL REFERENCES policy(id),
  requested_by    VARCHAR(64) NOT NULL,
  requested_urls  JSONB       NOT NULL,
  status          VARCHAR(16) NOT NULL,
  attempt         INT         NOT NULL,
  error_message   TEXT,
  requested_at    TIMESTAMP   NOT NULL,
  started_at      TIMESTAMP,
  finished_at     TIMESTAMP
);

-- 정책당 진행 중 잡은 1건만 허용
CREATE UNIQUE INDEX IF NOT EXISTS ix_enrichment_job_one_active
  ON enrichment_job (policy_id)
  WHERE status IN ('PENDING', 'RUNNING');

-- 최근 잡 조회·이력 표시용
CREATE INDEX IF NOT EXISTS ix_enrichment_job_policy_recent
  ON enrichment_job (policy_id, requested_at DESC);

-- 타임아웃 스캔용
CREATE INDEX IF NOT EXISTS ix_enrichment_job_active_requested_at
  ON enrichment_job (requested_at)
  WHERE status IN ('PENDING', 'RUNNING');
```

- [ ] **Step 2: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-21-enrichment-job.sql
git commit -m "chore(sql): enrichment_job 테이블 + partial unique/조회 인덱스"
```

---

### Task 3: 도메인 모델 변경 — `PolicyReferenceSite.source` + `EnrichmentJob` 엔티티

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyReferenceSite.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyReferenceSiteSource.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/EnrichmentJob.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/EnrichmentJobStatus.java`

- [ ] **Step 1: `PolicyReferenceSiteSource` enum 추가**

```java
package com.youthfit.policy.domain.model;

public enum PolicyReferenceSiteSource {
    AUTO,
    ADMIN
}
```

- [ ] **Step 2: `PolicyReferenceSite` 에 source 필드 추가 (정적 팩토리 보존)**

```java
package com.youthfit.policy.domain.model;

public record PolicyReferenceSite(
        String name,
        String url,
        PolicyReferenceSiteSource source
) {
    public PolicyReferenceSite {
        if (source == null) {
            source = PolicyReferenceSiteSource.AUTO;
        }
    }

    public static PolicyReferenceSite auto(String name, String url) {
        return new PolicyReferenceSite(name, url, PolicyReferenceSiteSource.AUTO);
    }

    public static PolicyReferenceSite admin(String name, String url) {
        return new PolicyReferenceSite(name, url, PolicyReferenceSiteSource.ADMIN);
    }
}
```

- [ ] **Step 3: 컴파일 깨지는 호출자 정리**

`cd backend && ./gradlew compileJava 2>&1 | grep "PolicyReferenceSite"` 로 식별 → 모든 생성자 호출을 `PolicyReferenceSite.auto(...)` 또는 `PolicyReferenceSite.admin(...)` 으로 치환. 테스트 코드도 동일.

Expected: ingestion 모듈 매퍼·테스트에서 호출자 N건 갱신.

- [ ] **Step 4: `EnrichmentJobStatus` enum 추가**

```java
package com.youthfit.policy.domain.model;

public enum EnrichmentJobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
```

- [ ] **Step 5: `EnrichmentJob` 엔티티 추가 (JSONB 매핑 + 상태 전이 메서드)**

```java
package com.youthfit.policy.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "enrichment_job")
public class EnrichmentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_urls", nullable = false, columnDefinition = "jsonb")
    private List<PolicyReferenceSite> requestedUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrichmentJobStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected EnrichmentJob() { }

    public static EnrichmentJob pending(Long policyId,
                                        String requestedBy,
                                        List<PolicyReferenceSite> urls,
                                        int attempt,
                                        LocalDateTime now) {
        EnrichmentJob job = new EnrichmentJob();
        job.policyId = policyId;
        job.requestedBy = requestedBy;
        job.requestedUrls = List.copyOf(urls);
        job.status = EnrichmentJobStatus.PENDING;
        job.attempt = attempt;
        job.requestedAt = now;
        return job;
    }

    public void markRunning(LocalDateTime now) {
        if (status != EnrichmentJobStatus.PENDING) {
            return;
        }
        this.status = EnrichmentJobStatus.RUNNING;
        this.startedAt = now;
    }

    public void markSuccess(LocalDateTime now) {
        if (status.isTerminal()) {
            return; // 멱등
        }
        this.status = EnrichmentJobStatus.SUCCESS;
        this.finishedAt = now;
    }

    public void markFailed(String reason, LocalDateTime now) {
        if (status.isTerminal()) {
            return; // 멱등
        }
        this.status = EnrichmentJobStatus.FAILED;
        this.errorMessage = reason;
        this.finishedAt = now;
    }

    public Long getId() { return id; }
    public Long getPolicyId() { return policyId; }
    public String getRequestedBy() { return requestedBy; }
    public List<PolicyReferenceSite> getRequestedUrls() { return requestedUrls; }
    public EnrichmentJobStatus getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
```

- [ ] **Step 6: 빌드 확인**

```bash
cd backend && ./gradlew compileJava compileTestJava
```

Expected: 0 errors.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/
git commit -m "feat(policy): PolicyReferenceSite.source + EnrichmentJob 엔티티 추가"
```

---

### Task 4: `EnrichmentJobRepository` 인터페이스 + JPA 구현

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/repository/EnrichmentJobRepository.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/EnrichmentJobJpaRepository.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/EnrichmentJobRepositoryImpl.java`

- [ ] **Step 1: 도메인 repository 인터페이스 작성**

```java
package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnrichmentJobRepository {

    EnrichmentJob save(EnrichmentJob job);

    Optional<EnrichmentJob> findById(Long id);

    Optional<EnrichmentJob> findActiveByPolicyId(Long policyId);

    int maxAttemptByPolicyId(Long policyId);

    List<EnrichmentJob> findRecentByPolicyId(Long policyId, int limit);

    List<EnrichmentJob> findActiveStaleBefore(LocalDateTime threshold);

    long countRecentByPolicyId(Long policyId, LocalDateTime since);
}
```

- [ ] **Step 2: JPA repository 작성**

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnrichmentJobJpaRepository extends JpaRepository<EnrichmentJob, Long> {

    @Query("""
            SELECT j FROM EnrichmentJob j
             WHERE j.policyId = :policyId
               AND j.status IN ('PENDING','RUNNING')
            """)
    Optional<EnrichmentJob> findActiveByPolicyId(@Param("policyId") Long policyId);

    @Query("""
            SELECT COALESCE(MAX(j.attempt), 0)
              FROM EnrichmentJob j
             WHERE j.policyId = :policyId
            """)
    int maxAttemptByPolicyId(@Param("policyId") Long policyId);

    List<EnrichmentJob> findByPolicyIdOrderByRequestedAtDesc(Long policyId, Pageable pageable);

    @Query("""
            SELECT j FROM EnrichmentJob j
             WHERE j.status IN ('PENDING','RUNNING')
               AND j.requestedAt < :threshold
            """)
    List<EnrichmentJob> findActiveStaleBefore(@Param("threshold") LocalDateTime threshold);

    long countByPolicyIdAndRequestedAtAfter(Long policyId, LocalDateTime since);
}
```

- [ ] **Step 3: Impl 어댑터 작성**

```java
package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class EnrichmentJobRepositoryImpl implements EnrichmentJobRepository {

    private final EnrichmentJobJpaRepository jpa;

    public EnrichmentJobRepositoryImpl(EnrichmentJobJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override public EnrichmentJob save(EnrichmentJob job) { return jpa.save(job); }
    @Override public Optional<EnrichmentJob> findById(Long id) { return jpa.findById(id); }
    @Override public Optional<EnrichmentJob> findActiveByPolicyId(Long policyId) { return jpa.findActiveByPolicyId(policyId); }
    @Override public int maxAttemptByPolicyId(Long policyId) { return jpa.maxAttemptByPolicyId(policyId); }

    @Override
    public List<EnrichmentJob> findRecentByPolicyId(Long policyId, int limit) {
        return jpa.findByPolicyIdOrderByRequestedAtDesc(policyId, PageRequest.of(0, limit));
    }

    @Override
    public List<EnrichmentJob> findActiveStaleBefore(LocalDateTime threshold) {
        return jpa.findActiveStaleBefore(threshold);
    }

    @Override
    public long countRecentByPolicyId(Long policyId, LocalDateTime since) {
        return jpa.countByPolicyIdAndRequestedAtAfter(policyId, since);
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: 0 errors.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/repository/EnrichmentJobRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/EnrichmentJobJpaRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/EnrichmentJobRepositoryImpl.java
git commit -m "feat(policy): EnrichmentJob repository (도메인 인터페이스 + JPA 구현)"
```

---

## Stage B — 도메인 로직 (TDD)

### Task 5: `EnrichmentReviewPolicy.needsReview` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/service/EnrichmentReviewPolicy.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/service/EnrichmentReviewPolicyTest.java`

- [ ] **Step 1: 실패 테스트 작성 — 6 케이스**

```java
package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentReviewPolicyTest {

    private final EnrichmentReviewPolicy policy = new EnrichmentReviewPolicy();

    @Test
    void enrichment_이_null_이면_검토필요() {
        Policy p = policyWithEnrichmentAndLevel(null, DetailLevel.MEDIUM);
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    void status_가_OK_가_아니면_검토필요() {
        PolicyEnrichment e = enrichmentBuilder()
                .status(EnrichmentStatus.FETCH_FAILED).confidence(0.9).build();
        Policy p = policyWithEnrichmentAndLevel(e, DetailLevel.MEDIUM);
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    void confidence_가_0_6_미만이면_검토필요() {
        PolicyEnrichment e = enrichmentBuilder()
                .status(EnrichmentStatus.OK).confidence(0.59).build();
        Policy p = policyWithEnrichmentAndLevel(e, DetailLevel.MEDIUM);
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    void detailLevel_LITE_이면_검토필요() {
        PolicyEnrichment e = enrichmentBuilder()
                .status(EnrichmentStatus.OK).confidence(0.9).build();
        Policy p = policyWithEnrichmentAndLevel(e, DetailLevel.LITE);
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    void 핵심섹션_2개이상_결측이면_검토필요() {
        PolicyEnrichment e = enrichmentBuilder()
                .status(EnrichmentStatus.OK).confidence(0.9)
                .sections(new PolicyEnrichment.Sections(
                        "지원대상 본문",   // supportTarget present
                        null,             // supportContent missing
                        null,             // selectionCriteria missing
                        null, null, null  // 그 외 결측 (계산 대상 아님)
                )).build();
        Policy p = policyWithEnrichmentAndLevel(e, DetailLevel.MEDIUM);
        assertThat(policy.needsReview(p)).isTrue();
    }

    @Test
    void 모든조건통과면_검토불필요() {
        PolicyEnrichment e = enrichmentBuilder()
                .status(EnrichmentStatus.OK).confidence(0.9)
                .sections(new PolicyEnrichment.Sections(
                        "지원대상", "지원내용", "선정기준", null, null, null))
                .build();
        Policy p = policyWithEnrichmentAndLevel(e, DetailLevel.MEDIUM);
        assertThat(policy.needsReview(p)).isFalse();
    }

    // --- helpers ---

    private Policy policyWithEnrichmentAndLevel(PolicyEnrichment e, DetailLevel level) {
        // 프로젝트의 Policy 빌더/팩토리 패턴에 맞춰 작성. 최소 enrichment 와 detailLevel 만 세팅.
        // 빌더가 없으면 reflection 또는 테스트 픽스처 도입을 별도 PR 로 분리할 수 있음.
        // 본 plan 에서는 기존 Policy 의 생성 패턴을 따른다.
        return PolicyTestFixtures.minimal(e, level);
    }

    private PolicyEnrichmentBuilder enrichmentBuilder() {
        return new PolicyEnrichmentBuilder()
                .sourceUrl("https://example.com")
                .fetchedAt(LocalDateTime.now())
                .extractor("test");
    }
}
```

> 주: `PolicyTestFixtures.minimal` / `PolicyEnrichmentBuilder` 는 기존 테스트에 이미 있다면 재사용, 없다면 본 task 에서 단순한 헬퍼를 함께 추가한다 (각 도메인 객체의 `new` 호출만 모은 thin wrapper). 새 fixture 파일을 만든다면 `backend/src/test/java/com/youthfit/policy/domain/PolicyTestFixtures.java`.

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.EnrichmentReviewPolicyTest"
```

Expected: compile error — `EnrichmentReviewPolicy` 없음.

- [ ] **Step 3: `EnrichmentReviewPolicy` 최소 구현**

```java
package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.*;
import org.springframework.stereotype.Component;

@Component
public class EnrichmentReviewPolicy {

    public static final double CONFIDENCE_THRESHOLD = 0.6;
    public static final int MIN_MISSING_CORE_SECTIONS = 2;

    public boolean needsReview(Policy policy) {
        PolicyEnrichment e = policy.getEnrichment();
        if (e == null) return true;
        if (e.status() != EnrichmentStatus.OK) return true;
        if (e.confidence() < CONFIDENCE_THRESHOLD) return true;
        if (policy.getDetailLevel() == DetailLevel.LITE) return true;
        if (missingCoreSections(e) >= MIN_MISSING_CORE_SECTIONS) return true;
        return false;
    }

    private int missingCoreSections(PolicyEnrichment e) {
        PolicyEnrichment.Sections s = e.sections();
        if (s == null) return 3;
        int missing = 0;
        if (isBlank(s.supportTarget())) missing++;
        if (isBlank(s.supportContent())) missing++;
        if (isBlank(s.selectionCriteria())) missing++;
        return missing;
    }

    private boolean isBlank(String v) { return v == null || v.isBlank(); }
}
```

> 주: 위 코드는 `PolicyEnrichment.Sections` 의 실제 필드명을 `supportTarget`/`supportContent`/`selectionCriteria` 라고 가정한다. 실제 record 의 필드명이 다르면 (예: `eligibility`/`benefits`/`criteria`) 그에 맞춰 한 곳에서만 수정한다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.EnrichmentReviewPolicyTest"
```

Expected: 6 PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/service/EnrichmentReviewPolicy.java \
        backend/src/test/java/com/youthfit/policy/domain/service/EnrichmentReviewPolicyTest.java
git commit -m "feat(policy): EnrichmentReviewPolicy — needsReview 판정 규칙 도입"
```

---

### Task 6: `PolicyReferenceSiteMerger` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/service/PolicyReferenceSiteMerger.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/service/PolicyReferenceSiteMergerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyReferenceSiteMergerTest {

    private final PolicyReferenceSiteMerger merger = new PolicyReferenceSiteMerger();

    @Test
    void ADMIN_입력이_AUTO_보존하면서_추가된다() {
        var existing = List.of(PolicyReferenceSite.auto("기존", "https://a.example.com"));
        var adminInputs = List.of(PolicyReferenceSite.admin("새URL", "https://b.example.com"));

        var merged = merger.merge(existing, adminInputs);

        assertThat(merged).extracting(PolicyReferenceSite::url)
                .containsExactly("https://b.example.com", "https://a.example.com");
        assertThat(merged.get(0).source()).isEqualTo(PolicyReferenceSiteSource.ADMIN);
        assertThat(merged.get(1).source()).isEqualTo(PolicyReferenceSiteSource.AUTO);
    }

    @Test
    void 같은URL은_AUTO에서_ADMIN으로_승격된다() {
        var existing = List.of(PolicyReferenceSite.auto("기존", "https://a.example.com"));
        var adminInputs = List.of(PolicyReferenceSite.admin("재확인", "https://a.example.com"));

        var merged = merger.merge(existing, adminInputs);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).source()).isEqualTo(PolicyReferenceSiteSource.ADMIN);
        assertThat(merged.get(0).name()).isEqualTo("재확인");
    }

    @Test
    void 같은URL중복입력은_제거된다() {
        var adminInputs = List.of(
                PolicyReferenceSite.admin("A", "https://x.example.com"),
                PolicyReferenceSite.admin("A2", "https://x.example.com"));

        var merged = merger.merge(List.of(), adminInputs);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).name()).isEqualTo("A");
    }

    @Test
    void 정렬은_ADMIN우선_그다음_AUTO() {
        var existing = List.of(
                PolicyReferenceSite.auto("A", "https://a.example.com"),
                PolicyReferenceSite.auto("B", "https://b.example.com"));
        var adminInputs = List.of(PolicyReferenceSite.admin("Z", "https://z.example.com"));

        var merged = merger.merge(existing, adminInputs);

        assertThat(merged).extracting(PolicyReferenceSite::source)
                .containsExactly(
                        PolicyReferenceSiteSource.ADMIN,
                        PolicyReferenceSiteSource.AUTO,
                        PolicyReferenceSiteSource.AUTO);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.PolicyReferenceSiteMergerTest"
```

Expected: compile error.

- [ ] **Step 3: 구현**

```java
package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PolicyReferenceSiteMerger {

    public List<PolicyReferenceSite> merge(List<PolicyReferenceSite> existing,
                                           List<PolicyReferenceSite> adminInputs) {
        LinkedHashMap<String, PolicyReferenceSite> byUrl = new LinkedHashMap<>();

        // 1) ADMIN 입력을 먼저 넣는다 (중복 시 첫 번째 유지).
        for (PolicyReferenceSite s : adminInputs) {
            if (s == null || s.url() == null) continue;
            byUrl.putIfAbsent(
                    s.url(),
                    new PolicyReferenceSite(s.name(), s.url(), PolicyReferenceSiteSource.ADMIN)
            );
        }

        // 2) 기존 사이트를 뒤에 붙인다. ADMIN 으로 들어간 URL 은 그대로 두고(승격 유지),
        //    그 외에는 원본을 보존한다.
        for (PolicyReferenceSite s : existing) {
            if (s == null || s.url() == null) continue;
            byUrl.putIfAbsent(s.url(), s);
        }

        return new ArrayList<>(byUrl.values());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.domain.service.PolicyReferenceSiteMergerTest"
```

Expected: 4 PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/service/PolicyReferenceSiteMerger.java \
        backend/src/test/java/com/youthfit/policy/domain/service/PolicyReferenceSiteMergerTest.java
git commit -m "feat(policy): PolicyReferenceSiteMerger — ADMIN/AUTO 머지 및 승격"
```

---

### Task 7: `EnrichmentJobService.create` + `complete` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/application/service/EnrichmentJobService.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/service/EnrichmentJobServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성 — 핵심 4 케이스**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.infrastructure.N8nForceEnrichClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnrichmentJobServiceTest {

    private EnrichmentJobRepository jobRepo;
    private PolicyRepository policyRepo;
    private N8nForceEnrichClient n8n;
    private EnrichmentJobService service;

    @BeforeEach
    void setUp() {
        jobRepo = mock(EnrichmentJobRepository.class);
        policyRepo = mock(PolicyRepository.class);
        n8n = mock(N8nForceEnrichClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneId.of("UTC"));
        service = new EnrichmentJobService(jobRepo, policyRepo, n8n, clock);
    }

    @Test
    void create_은_새_PENDING_잡을_생성하고_n8n을_호출한다() {
        Policy policy = PolicyTestFixtures.withReferenceSites(
                List.of(PolicyReferenceSite.auto("기존", "https://a.example.com")));
        when(policyRepo.findById(42L)).thenReturn(Optional.of(policy));
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(Optional.empty());
        when(jobRepo.maxAttemptByPolicyId(42L)).thenReturn(0);
        when(jobRepo.save(any())).thenAnswer(inv -> {
            EnrichmentJob j = inv.getArgument(0);
            // 저장된 잡에 id 부여를 시뮬레이션
            return ReflectionTestUtils.injectId(j, 100L);
        });

        EnrichmentJob created = service.create(42L, "admin@youthfit", null);

        assertThat(created.getStatus()).isEqualTo(EnrichmentJobStatus.PENDING);
        assertThat(created.getAttempt()).isEqualTo(1);
        verify(n8n).forceEnrich(eq(100L), eq(42L),
                argThat(urls -> urls.size() == 1
                        && urls.get(0).url().equals("https://a.example.com")));
    }

    @Test
    void 진행중_잡이_있으면_409_예외() {
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(
                Optional.of(EnrichmentJob.pending(42L, "x", List.of(), 1,
                        java.time.LocalDateTime.now())));

        assertThatThrownBy(() -> service.create(42L, "admin@youthfit", null))
                .isInstanceOf(EnrichmentJobConflictException.class);

        verifyNoInteractions(n8n);
    }

    @Test
    void 빈_referenceSites_와_빈_urls_이면_거절() {
        Policy policy = PolicyTestFixtures.withReferenceSites(List.of());
        when(policyRepo.findById(42L)).thenReturn(Optional.of(policy));
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(42L, "admin@youthfit", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenceSites");
    }

    @Test
    void complete_SUCCESS_콜백은_status를_SUCCESS로_옮긴다() {
        EnrichmentJob job = ReflectionTestUtils.injectId(
                EnrichmentJob.pending(42L, "x",
                        List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                        1, java.time.LocalDateTime.now()),
                100L);
        when(jobRepo.findById(100L)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.complete(100L, EnrichmentJobStatus.SUCCESS, null);

        assertThat(job.getStatus()).isEqualTo(EnrichmentJobStatus.SUCCESS);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void complete_이미_종결된_잡에_대한_재콜백은_무시한다() {
        EnrichmentJob job = ReflectionTestUtils.injectId(
                EnrichmentJob.pending(42L, "x",
                        List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                        1, java.time.LocalDateTime.now()),
                100L);
        job.markSuccess(java.time.LocalDateTime.now()); // 이미 SUCCESS

        when(jobRepo.findById(100L)).thenReturn(Optional.of(job));

        service.complete(100L, EnrichmentJobStatus.FAILED, "redundant");

        assertThat(job.getStatus()).isEqualTo(EnrichmentJobStatus.SUCCESS); // 변하지 않음
    }
}
```

> 주: `ReflectionTestUtils.injectId` 는 본 task 의 헬퍼 (테스트용 reflection set field). 기존 프로젝트에 있다면 재사용. 없으면 `backend/src/test/java/com/youthfit/policy/ReflectionTestUtils.java` 에 4줄짜리 wrapper 로 추가.

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.EnrichmentJobServiceTest"
```

Expected: compile error.

- [ ] **Step 3: `EnrichmentJobConflictException` 추가**

```java
package com.youthfit.policy.application.service;

public class EnrichmentJobConflictException extends RuntimeException {
    public EnrichmentJobConflictException(Long policyId) {
        super("EnrichmentJob already active for policy " + policyId);
    }
}
```

위치: `backend/src/main/java/com/youthfit/policy/application/service/EnrichmentJobConflictException.java`

- [ ] **Step 4: `EnrichmentJobService` 구현**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.infrastructure.N8nForceEnrichClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EnrichmentJobService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentJobService.class);
    private static final int RATE_LIMIT_PER_HOUR = 5;

    private final EnrichmentJobRepository jobRepo;
    private final PolicyRepository policyRepo;
    private final N8nForceEnrichClient n8n;
    private final Clock clock;

    public EnrichmentJobService(EnrichmentJobRepository jobRepo,
                                PolicyRepository policyRepo,
                                N8nForceEnrichClient n8n,
                                Clock clock) {
        this.jobRepo = jobRepo;
        this.policyRepo = policyRepo;
        this.n8n = n8n;
        this.clock = clock;
    }

    @Transactional
    public EnrichmentJob create(Long policyId, String requestedBy, List<PolicyReferenceSite> urlsOverride) {
        if (jobRepo.findActiveByPolicyId(policyId).isPresent()) {
            throw new EnrichmentJobConflictException(policyId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long recent = jobRepo.countRecentByPolicyId(policyId, now.minusHours(1));
        if (recent >= RATE_LIMIT_PER_HOUR) {
            throw new EnrichmentJobRateLimitException(policyId, RATE_LIMIT_PER_HOUR);
        }

        Policy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        List<PolicyReferenceSite> urls = (urlsOverride != null && !urlsOverride.isEmpty())
                ? urlsOverride
                : policy.getReferenceSites();
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("Cannot create job: referenceSites is empty");
        }

        int attempt = jobRepo.maxAttemptByPolicyId(policyId) + 1;
        EnrichmentJob job = jobRepo.save(EnrichmentJob.pending(policyId, requestedBy, urls, attempt, now));
        log.info("EnrichmentJob created: jobId={}, policyId={}, attempt={}, urls={}, actor={}",
                job.getId(), policyId, attempt, urls.size(), requestedBy);

        // n8n 호출 실패는 즉시 FAILED 로 마킹 (스펙 §7 에러 처리 매트릭스)
        try {
            n8n.forceEnrich(job.getId(), policyId, urls);
        } catch (RuntimeException e) {
            log.warn("n8n force-enrich failed: jobId={} cause={}", job.getId(), e.getMessage());
            job.markFailed("n8n_unreachable: " + e.getMessage(), LocalDateTime.now(clock));
            jobRepo.save(job);
        }
        return job;
    }

    @Transactional
    public void markRunning(Long jobId) {
        EnrichmentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        job.markRunning(LocalDateTime.now(clock));
        jobRepo.save(job);
        log.info("EnrichmentJob RUNNING: jobId={}", jobId);
    }

    @Transactional
    public void complete(Long jobId, EnrichmentJobStatus terminal, String errorMessage) {
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException("complete() requires terminal status: " + terminal);
        }
        EnrichmentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus().isTerminal()) {
            log.info("EnrichmentJob callback ignored (already terminal): jobId={} current={} attempted={}",
                    jobId, job.getStatus(), terminal);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (terminal == EnrichmentJobStatus.SUCCESS) {
            job.markSuccess(now);
        } else {
            job.markFailed(errorMessage != null ? errorMessage : "failed", now);
        }
        jobRepo.save(job);
        log.info("EnrichmentJob {} : jobId={} elapsedMs={}",
                terminal, jobId,
                job.getStartedAt() == null ? -1
                        : java.time.Duration.between(job.getStartedAt(), now).toMillis());
    }
}
```

- [ ] **Step 5: `EnrichmentJobRateLimitException` 추가**

```java
package com.youthfit.policy.application.service;

public class EnrichmentJobRateLimitException extends RuntimeException {
    public EnrichmentJobRateLimitException(Long policyId, int limitPerHour) {
        super("Rate limit reached for policy " + policyId + " (max " + limitPerHour + "/hour)");
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.EnrichmentJobServiceTest"
```

Expected: 5 PASS.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/EnrichmentJob*.java \
        backend/src/test/java/com/youthfit/policy/application/service/EnrichmentJobServiceTest.java
git commit -m "feat(policy): EnrichmentJobService — 잡 생성·완료 + 동시성·레이트리밋"
```

---

### Task 8: `EnrichmentJobTimeoutScheduler` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/application/service/EnrichmentJobTimeoutScheduler.java`
- Test: `backend/src/test/java/com/youthfit/policy/application/service/EnrichmentJobTimeoutSchedulerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnrichmentJobTimeoutSchedulerTest {

    @Test
    void 활성_5분초과_잡을_FAILED로_마킹한다() {
        EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-21T00:10:00Z"), ZoneId.of("UTC"));
        EnrichmentJobTimeoutScheduler scheduler = new EnrichmentJobTimeoutScheduler(repo, clock);

        EnrichmentJob stale = EnrichmentJob.pending(1L, "actor",
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                1, LocalDateTime.of(2026, 5, 21, 0, 4, 0));
        when(repo.findActiveStaleBefore(any())).thenReturn(List.of(stale));

        scheduler.expireStaleJobs();

        verify(repo).save(argThat(j -> j.getStatus() == EnrichmentJobStatus.FAILED
                && j.getErrorMessage().contains("timeout")));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.EnrichmentJobTimeoutSchedulerTest"
```

- [ ] **Step 3: 구현**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class EnrichmentJobTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentJobTimeoutScheduler.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final EnrichmentJobRepository repo;
    private final Clock clock;

    public EnrichmentJobTimeoutScheduler(EnrichmentJobRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${enrichment.timeout.fixed-delay-ms:60000}")
    @Transactional
    public void expireStaleJobs() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(TIMEOUT);
        List<EnrichmentJob> stale = repo.findActiveStaleBefore(threshold);
        if (stale.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now(clock);
        for (EnrichmentJob job : stale) {
            job.markFailed("timeout", now);
            repo.save(job);
            log.warn("EnrichmentJob expired: jobId={} policyId={} attempt={}",
                    job.getId(), job.getPolicyId(), job.getAttempt());
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인 + `@EnableScheduling` 활성화 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.EnrichmentJobTimeoutSchedulerTest"
grep -rn "@EnableScheduling" backend/src/main/java | head -1
```

Expected: 1 PASS, `@EnableScheduling` 가 application 클래스(또는 Config) 에 이미 있음 (`AttachmentExtractionScheduler` 가 동작하므로 활성화돼 있음).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/service/EnrichmentJobTimeoutScheduler.java \
        backend/src/test/java/com/youthfit/policy/application/service/EnrichmentJobTimeoutSchedulerTest.java
git commit -m "feat(policy): EnrichmentJobTimeoutScheduler — 5분 초과 활성 잡 FAILED 처리"
```

---

## Stage C — n8n 클라이언트

### Task 9: `N8nForceEnrichClient` (RestClient + 환경변수 + 시크릿 헤더)

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/N8nForceEnrichClient.java`
- Modify: `backend/src/main/resources/application.yml` — `n8n.force-enrich-webhook-url` 추가
- Test: `backend/src/test/java/com/youthfit/policy/infrastructure/N8nForceEnrichClientTest.java`

- [ ] **Step 1: 실패 테스트 작성 (MockRestServiceServer)**

```java
package com.youthfit.policy.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class N8nForceEnrichClientTest {

    @Test
    void forceEnrich_은_시크릿_헤더와_payload를_전송한다() {
        RestClient.Builder builder = RestClient.builder();
        N8nForceEnrichClient client = new N8nForceEnrichClient(
                builder,
                "http://n8n.test/webhook/force-enrich",
                "secret-token",
                new ObjectMapper()
        );

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://n8n.test/webhook/force-enrich"))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(header("X-Internal-Api-Key", "secret-token"))
              .andExpect(jsonPath("$.jobId").value(100))
              .andExpect(jsonPath("$.policyId").value(42))
              .andExpect(jsonPath("$.urls[0].url").value("https://a.example.com"))
              .andRespond(withSuccess());

        client.forceEnrich(100L, 42L,
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")));

        server.verify();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.N8nForceEnrichClientTest"
```

- [ ] **Step 3: 구현**

```java
package com.youthfit.policy.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class N8nForceEnrichClient {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public N8nForceEnrichClient(RestClient.Builder builder,
                                @Value("${n8n.force-enrich-webhook-url}") String webhookUrl,
                                @Value("${ingestion.internal-api-key}") String apiKey,
                                ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    public void forceEnrich(Long jobId, Long policyId, List<PolicyReferenceSite> urls) {
        Map<String, Object> payload = Map.of(
                "jobId", jobId,
                "policyId", policyId,
                "urls", urls
        );
        restClient.post()
                .uri(webhookUrl)
                .header("X-Internal-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
```

- [ ] **Step 4: `application.yml` 설정 추가**

기존 `ingestion.internal-api-key` 가 있다면 그대로 재사용. 신규 키만 추가:

```yaml
n8n:
  force-enrich-webhook-url: ${N8N_FORCE_ENRICH_WEBHOOK_URL:http://localhost:5678/webhook/force-enrich}

enrichment:
  timeout:
    fixed-delay-ms: 60000
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.infrastructure.N8nForceEnrichClientTest"
```

Expected: 1 PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/infrastructure/N8nForceEnrichClient.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/N8nForceEnrichClientTest.java \
        backend/src/main/resources/application.yml
git commit -m "feat(policy): N8nForceEnrichClient — force-enrich webhook 호출"
```

---

## Stage D — REST API

### Task 10: 후보 목록 조회용 read-side (Repository 확장 + Query Service)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java` — 신규 메서드 1개 시그니처
- Modify: `backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepository.java` — JSONB 쿼리
- Create: `backend/src/main/java/com/youthfit/policy/application/service/AdminEnrichmentQueryService.java`

- [ ] **Step 1: `PolicyRepository` 메서드 추가**

```java
// 기존 PolicyRepository 인터페이스에 추가:
import org.springframework.data.domain.Page;

Page<Policy> searchForEnrichmentReview(EnrichmentReviewFilter filter, org.springframework.data.domain.Pageable pageable);

EnrichmentReviewSummary summarizeEnrichmentReview();
```

- [ ] **Step 2: Query 파라미터·요약 DTO 추가**

위치: `backend/src/main/java/com/youthfit/policy/application/dto/`

```java
package com.youthfit.policy.application.dto;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;

import java.util.Set;

public record EnrichmentReviewFilter(
        Boolean needsReviewOnly,
        Set<EnrichmentStatus> statuses,
        Set<DetailLevel> detailLevels,
        String keyword
) { }
```

```java
package com.youthfit.policy.application.dto;

import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;

import java.util.Map;

public record EnrichmentReviewSummary(
        long total,
        long needsReview,
        Map<EnrichmentStatus, Long> byStatus,
        Map<DetailLevel, Long> byDetailLevel
) { }
```

- [ ] **Step 3: JSONB 검색 native query 추가**

`PolicyJpaRepository` 가 `JpaSpecificationExecutor<Policy>` 를 상속하지 않는다면, native query 로 처리.

```java
// PolicyJpaRepository 에 추가:
@Query(value = """
    SELECT * FROM policy p
     WHERE (:needsReviewOnly = false OR (
            p.enrichment IS NULL
         OR (p.enrichment->>'status') <> 'OK'
         OR ((p.enrichment->>'confidence')::numeric) < 0.6
         OR p.detail_level = 'LITE'
         OR (
              (CASE WHEN COALESCE(p.enrichment->'sections'->>'supportTarget','') = '' THEN 1 ELSE 0 END)
            + (CASE WHEN COALESCE(p.enrichment->'sections'->>'supportContent','') = '' THEN 1 ELSE 0 END)
            + (CASE WHEN COALESCE(p.enrichment->'sections'->>'selectionCriteria','') = '' THEN 1 ELSE 0 END)
           ) >= 2
       ))
       AND (:keyword IS NULL OR p.title ILIKE CONCAT('%', :keyword, '%'))
    """,
    countQuery = "/* same WHERE */",
    nativeQuery = true)
Page<Policy> searchForEnrichmentReview(@Param("needsReviewOnly") boolean needsReviewOnly,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);
```

> 주: 상태·detailLevel 필터까지 native query 에 모두 넣으면 동적 쿼리가 복잡해진다. 1차 구현에서는 위 `needsReviewOnly + keyword` 만 native 로 처리하고, status / detailLevel 추가 필터는 후속(같은 컬럼 EQ 조합) 으로 둔다. 본 task 의 DTO 에는 모두 정의해 두되, native query 분기에는 두 가지만 반영.

- [ ] **Step 4: `AdminEnrichmentQueryService` 구현**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.EnrichmentReviewFilter;
import com.youthfit.policy.application.dto.EnrichmentReviewSummary;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.service.EnrichmentReviewPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminEnrichmentQueryService {

    private final PolicyRepository policyRepo;
    private final EnrichmentReviewPolicy reviewPolicy;

    public AdminEnrichmentQueryService(PolicyRepository policyRepo,
                                       EnrichmentReviewPolicy reviewPolicy) {
        this.policyRepo = policyRepo;
        this.reviewPolicy = reviewPolicy;
    }

    public Page<Policy> candidates(EnrichmentReviewFilter filter, Pageable pageable) {
        return policyRepo.searchForEnrichmentReview(filter, pageable);
    }

    public EnrichmentReviewSummary summary() {
        return policyRepo.summarizeEnrichmentReview();
    }

    public boolean needsReview(Policy policy) {
        return reviewPolicy.needsReview(policy);
    }
}
```

- [ ] **Step 5: 빌드 확인**

```bash
cd backend && ./gradlew compileJava
```

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/ \
        backend/src/main/java/com/youthfit/policy/application/service/AdminEnrichmentQueryService.java \
        backend/src/main/java/com/youthfit/policy/domain/repository/PolicyRepository.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/persistence/PolicyJpaRepository.java
git commit -m "feat(policy): 어드민 enrichment 검토용 read-side 쿼리/요약"
```

---

### Task 11: `AdminEnrichmentController` (@WebMvcTest 슬라이스)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminEnrichmentController.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/*` — request/response DTO
- Test: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminEnrichmentControllerTest.java`

- [ ] **Step 1: 응답 DTO 추가**

```java
package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.EnrichmentJobStatus;

import java.time.LocalDateTime;
import java.util.List;

public record EnrichmentJobView(
        Long id,
        Long policyId,
        EnrichmentJobStatus status,
        int attempt,
        String requestedBy,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<UrlView> requestedUrls
) {
    public record UrlView(String name, String url, String source) { }
}
```

```java
package com.youthfit.admin.presentation.dto;

import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReferenceSiteRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^https?://.+") String url,
        PolicyReferenceSiteSource source
) { }
```

(추가로 `CandidateView`, `CandidateSummaryView`, `CreateJobRequest`, `JobAcceptedResponse` 등을 같은 패키지에 둔다 — 모두 단순 record. 본 plan 에서는 핵심 두 개만 보여주고, 나머지는 컨트롤러 본문 참조로 충분.)

- [ ] **Step 2: 실패 테스트 작성 (권한 + 핵심 경로 4건)**

```java
package com.youthfit.admin.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.admin.presentation.dto.ReferenceSiteRequest;
import com.youthfit.config.SecurityConfig;
import com.youthfit.config.JwtAuthenticationEntryPoint;
import com.youthfit.policy.application.service.*;
import com.youthfit.policy.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEnrichmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminEnrichmentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AdminEnrichmentQueryService queryService;
    @MockBean EnrichmentJobService jobService;
    @MockBean com.youthfit.policy.domain.repository.PolicyRepository policyRepo;
    @MockBean com.youthfit.policy.domain.repository.EnrichmentJobRepository jobRepo;
    @MockBean com.youthfit.policy.domain.service.PolicyReferenceSiteMerger merger;

    @Test
    void 인증되지않은_요청은_401() throws Exception {
        mvc.perform(get("/api/v1/admin/enrichment/candidates"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void ADMIN이아니면_403() throws Exception {
        mvc.perform(get("/api/v1/admin/enrichment/candidates"))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void POST_jobs_는_202와_jobId를_돌려준다() throws Exception {
        when(jobService.create(eq(42L), any(), any()))
                .thenReturn(EnrichmentJobTestFixtures.pendingWithId(42L, 100L));

        mvc.perform(post("/api/v1/admin/enrichment/policies/42/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.jobId").value(100))
           .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void POST_jobs_는_진행중_잡_있으면_409() throws Exception {
        when(jobService.create(eq(42L), any(), any()))
                .thenThrow(new EnrichmentJobConflictException(42L));

        mvc.perform(post("/api/v1/admin/enrichment/policies/42/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
           .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void PUT_reference_sites_는_저장후_200() throws Exception {
        var body = List.of(new ReferenceSiteRequest("새URL", "https://new.example.com",
                PolicyReferenceSiteSource.ADMIN));
        mvc.perform(put("/api/v1/admin/enrichment/policies/42/reference-sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
           .andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminEnrichmentControllerTest"
```

- [ ] **Step 4: 컨트롤러 구현**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.*;
import com.youthfit.policy.application.dto.EnrichmentReviewFilter;
import com.youthfit.policy.application.service.*;
import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.*;
import com.youthfit.policy.domain.service.PolicyReferenceSiteMerger;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/enrichment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEnrichmentController {

    private final AdminEnrichmentQueryService queryService;
    private final EnrichmentJobService jobService;
    private final PolicyRepository policyRepo;
    private final EnrichmentJobRepository jobRepo;
    private final PolicyReferenceSiteMerger merger;

    public AdminEnrichmentController(AdminEnrichmentQueryService queryService,
                                     EnrichmentJobService jobService,
                                     PolicyRepository policyRepo,
                                     EnrichmentJobRepository jobRepo,
                                     PolicyReferenceSiteMerger merger) {
        this.queryService = queryService;
        this.jobService = jobService;
        this.policyRepo = policyRepo;
        this.jobRepo = jobRepo;
        this.merger = merger;
    }

    @GetMapping("/candidates")
    public Page<CandidateView> candidates(
            @RequestParam(defaultValue = "true") boolean needsReview,
            @RequestParam(required = false) Set<EnrichmentStatus> statuses,
            @RequestParam(required = false) Set<DetailLevel> detailLevels,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        var filter = new EnrichmentReviewFilter(needsReview, statuses, detailLevels, q);
        return queryService.candidates(filter, pageable)
                .map(p -> CandidateView.of(p, jobRepo.findRecentByPolicyId(p.getId(), 1), queryService.needsReview(p)));
    }

    @GetMapping("/candidates/summary")
    public CandidateSummaryView summary() {
        return CandidateSummaryView.of(queryService.summary());
    }

    @GetMapping("/policies/{policyId}")
    public PolicyEnrichmentDetailView detail(@PathVariable Long policyId) {
        Policy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        var jobs = jobRepo.findRecentByPolicyId(policyId, 5);
        return PolicyEnrichmentDetailView.of(policy, jobs, queryService.needsReview(policy));
    }

    @PutMapping("/policies/{policyId}/reference-sites")
    @Transactional
    public ResponseEntity<Void> updateReferenceSites(@PathVariable Long policyId,
                                                     @RequestBody @Valid List<ReferenceSiteRequest> body) {
        Policy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        var adminInputs = body.stream()
                .map(r -> new PolicyReferenceSite(r.name(), r.url(), PolicyReferenceSiteSource.ADMIN))
                .toList();
        var merged = merger.merge(policy.getReferenceSites(), adminInputs);
        policy.replaceReferenceSites(merged);  // Policy 엔티티에 setter 또는 정확히 이 이름의 메서드 존재 가정. 없으면 Policy 에 추가.
        policyRepo.save(policy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/policies/{policyId}/jobs")
    public ResponseEntity<JobAcceptedResponse> createJob(@PathVariable Long policyId,
                                                         @RequestBody CreateJobRequest body,
                                                         Authentication authentication) {
        List<PolicyReferenceSite> urls = body == null || body.urls() == null ? null
                : body.urls().stream()
                    .map(u -> new PolicyReferenceSite(u.name(), u.url(), PolicyReferenceSiteSource.ADMIN))
                    .toList();
        EnrichmentJob job = jobService.create(policyId, authentication.getName(), urls);
        return ResponseEntity.accepted()
                .body(new JobAcceptedResponse(job.getId(), job.getStatus()));
    }

    @GetMapping("/jobs/{jobId}")
    public EnrichmentJobView job(@PathVariable Long jobId) {
        var job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        return EnrichmentJobView.of(job);
    }

    @ExceptionHandler(EnrichmentJobConflictException.class)
    public ResponseEntity<String> onConflict(EnrichmentJobConflictException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    @ExceptionHandler(EnrichmentJobRateLimitException.class)
    public ResponseEntity<String> onRateLimit(EnrichmentJobRateLimitException e) {
        return ResponseEntity.status(429).body(e.getMessage());
    }
}
```

위 컨트롤러에서 사용하는 view 클래스(`CandidateView`, `CandidateSummaryView`, `PolicyEnrichmentDetailView`, `CreateJobRequest`, `JobAcceptedResponse`) 와 각 `of(...)` 팩토리는 `backend/src/main/java/com/youthfit/admin/presentation/dto/` 에 함께 추가한다. 모두 단순 record + 정적 팩토리, 본 plan 에서는 시그니처만 필요.

- [ ] **Step 5: `Policy.replaceReferenceSites(List<PolicyReferenceSite>)` 메서드 추가 (없을 경우)**

`Policy.java` 에 다음을 추가:

```java
public void replaceReferenceSites(List<PolicyReferenceSite> sites) {
    this.referenceSites = new ArrayList<>(sites);
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminEnrichmentControllerTest"
```

Expected: 5 PASS.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/ \
        backend/src/main/java/com/youthfit/policy/domain/model/Policy.java \
        backend/src/test/java/com/youthfit/admin/presentation/controller/AdminEnrichmentControllerTest.java
git commit -m "feat(admin): AdminEnrichmentController — 후보 목록·summary·잡 생성·URL 갱신"
```

---

### Task 12: n8n 콜백 컨트롤러 (Internal API)

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/presentation/controller/InternalEnrichmentJobCallbackController.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/presentation/controller/InternalEnrichmentJobCallbackControllerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.ingestion.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InternalEnrichmentJobCallbackController.class)
class InternalEnrichmentJobCallbackControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean EnrichmentJobService jobService;

    @Test
    void RUNNING_콜백은_markRunning을_호출한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RUNNING"}
                                """))
           .andExpect(status().isNoContent());

        verify(jobService).markRunning(100L);
    }

    @Test
    void SUCCESS_콜백은_complete를_호출한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUCCESS"}
                                """))
           .andExpect(status().isNoContent());

        verify(jobService).complete(100L, EnrichmentJobStatus.SUCCESS, null);
    }

    @Test
    void FAILED_콜백은_에러메시지와_함께_complete를_호출한다() throws Exception {
        mvc.perform(post("/api/internal/enrichment/jobs/100/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FAILED","error":"fetch_timeout"}
                                """))
           .andExpect(status().isNoContent());

        verify(jobService).complete(100L, EnrichmentJobStatus.FAILED, "fetch_timeout");
    }
}
```

> 주: `InternalApiKeyFilter` 가 `/api/internal/*` 를 보호하므로 슬라이스 테스트에서는 시큐리티 비활성 또는 필터 미포함 상태로 컨트롤러 로직만 검증한다. 실제 시크릿 검증은 통합 테스트(Task 18) 에서 다룬다.

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.presentation.controller.InternalEnrichmentJobCallbackControllerTest"
```

- [ ] **Step 3: 컨트롤러 구현**

```java
package com.youthfit.ingestion.presentation.controller;

import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/enrichment")
public class InternalEnrichmentJobCallbackController {

    private final EnrichmentJobService service;

    public InternalEnrichmentJobCallbackController(EnrichmentJobService service) {
        this.service = service;
    }

    public record CallbackRequest(EnrichmentJobStatus status, String error) { }

    @PostMapping("/jobs/{jobId}/callback")
    public ResponseEntity<Void> callback(@PathVariable Long jobId, @RequestBody CallbackRequest body) {
        switch (body.status()) {
            case RUNNING -> service.markRunning(jobId);
            case SUCCESS -> service.complete(jobId, EnrichmentJobStatus.SUCCESS, null);
            case FAILED -> service.complete(jobId, EnrichmentJobStatus.FAILED, body.error());
            case PENDING -> { /* no-op */ }
        }
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: `InternalApiKeyFilter` 경로 패턴 확인**

```bash
grep -n "api/internal" backend/src/main/java/com/youthfit/ingestion/infrastructure/config/InternalApiKeyFilter.java
```

Expected: `/api/internal/*` 매칭 — 본 신규 엔드포인트가 자동으로 시크릿 검증 대상이 됨.

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.presentation.controller.InternalEnrichmentJobCallbackControllerTest"
```

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/presentation/controller/InternalEnrichmentJobCallbackController.java \
        backend/src/test/java/com/youthfit/ingestion/presentation/controller/InternalEnrichmentJobCallbackControllerTest.java
git commit -m "feat(ingestion): n8n force-enrich 콜백 엔드포인트 — /api/internal/enrichment/jobs/{id}/callback"
```

---

## Stage E — n8n 워크플로우

### Task 13: `force-enrich.json` 워크플로우 + verify 픽스처

**Files:**
- Create: `n8n/workflows/force-enrich.json`
- Create: `n8n/workflows/__fixtures__/force-enrich/verify.mjs`

- [ ] **Step 1: 워크플로우 JSON 구성 (수동 노드 정의)**

n8n editor 에서 다음 5개 노드를 직선 연결:

1. **Webhook** — POST `/webhook/force-enrich`, header `X-Internal-Api-Key` 검증 (n8n credential `internalApiKey`)
2. **HTTP Request "callback RUNNING"** — POST `${BACKEND_BASE}/api/internal/enrichment/jobs/{{$json.jobId}}/callback`, body `{"status":"RUNNING"}`, header `X-Internal-Api-Key`
3. **Code 노드 "enrich"** — 기존 `youth-center-seoul.json` 의 enrichment-merge mega-node 와 동일한 코드를 share. `urls` 인풋을 그대로 받아 `selectUrls/mergeFetchResults` 호출
4. **HTTP Request "ingestion update"** — POST `${BACKEND_BASE}/api/internal/policy-enrichment` (기존 ingestion 엔드포인트와 동일 형식), enrichment 결과 송신
5. **HTTP Request "callback SUCCESS/FAILED"** — POST `${BACKEND_BASE}/api/internal/enrichment/jobs/{{$json.jobId}}/callback`, body `{"status":"SUCCESS"}` (Error Workflow 또는 3번 노드 에러 분기에서는 `{"status":"FAILED","error":"<message>"}`)

JSON 저장 위치: `n8n/workflows/force-enrich.json`. 실제 노드 좌표·credential 명은 기존 워크플로우 컨벤션을 따른다.

- [ ] **Step 2: `verify.mjs` 작성**

```js
// n8n/workflows/__fixtures__/force-enrich/verify.mjs
// 기존 enrich.mjs / verify.mjs 패턴 따름.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dir = path.dirname(fileURLToPath(import.meta.url));
const workflow = JSON.parse(readFileSync(path.join(__dir, '..', '..', 'force-enrich.json'), 'utf8'));

const nodes = workflow.nodes.map(n => n.name);
for (const required of [
    'Webhook',
    'callback RUNNING',
    'enrich',
    'ingestion update',
    'callback SUCCESS',
]) {
    assert.ok(nodes.includes(required), `force-enrich.json 에 노드 "${required}" 가 있어야 함`);
}

console.log('OK: force-enrich workflow has all required nodes');
```

- [ ] **Step 3: 픽스처 실행 확인**

```bash
node n8n/workflows/__fixtures__/force-enrich/verify.mjs
```

Expected: `OK: force-enrich workflow has all required nodes`

- [ ] **Step 4: 커밋**

```bash
git add n8n/workflows/force-enrich.json n8n/workflows/__fixtures__/force-enrich/verify.mjs
git commit -m "feat(n8n): force-enrich 워크플로우 + verify 픽스처"
```

---

## Stage F — 프론트엔드

### Task 14: 타입 + API 클라이언트 + react-query 훅

**Files:**
- Create: `frontend/src/types/adminEnrichment.ts`
- Create: `frontend/src/apis/adminEnrichment.ts`
- Create: `frontend/src/hooks/useAdminEnrichment.ts`

- [ ] **Step 1: 타입 정의**

```ts
// frontend/src/types/adminEnrichment.ts
export type EnrichmentStatus =
  | 'OK' | 'NO_LINK' | 'FETCH_FAILED' | 'TOO_SHORT'
  | 'LLM_FAILED' | 'PARSE_FAILED' | 'LOW_CONFIDENCE';

export type DetailLevel = 'LITE' | 'MEDIUM' | 'FULL';

export type ReferenceSiteSource = 'AUTO' | 'ADMIN';

export interface ReferenceSite {
  name: string;
  url: string;
  source: ReferenceSiteSource;
}

export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface EnrichmentJobView {
  id: number;
  policyId: number;
  status: JobStatus;
  attempt: number;
  requestedBy: string;
  errorMessage: string | null;
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  requestedUrls: ReferenceSite[];
}

export interface CandidateView {
  id: number;
  title: string;
  organization: string;
  status: EnrichmentStatus | null;
  confidence: number | null;
  detailLevel: DetailLevel;
  needsReview: boolean;
  latestJob: EnrichmentJobView | null;
}

export interface CandidateSummary {
  total: number;
  needsReview: number;
  byStatus: Record<EnrichmentStatus, number>;
  byDetailLevel: Record<DetailLevel, number>;
}

export interface PolicyEnrichmentDetail {
  policyId: number;
  title: string;
  enrichment: {
    status: EnrichmentStatus | null;
    confidence: number | null;
    fetchedAt: string | null;
    extractor: string | null;
    sections: Record<string, string | null> | null;
  } | null;
  detailLevel: DetailLevel;
  referenceSites: ReferenceSite[];
  recentJobs: EnrichmentJobView[];
  needsReview: boolean;
}
```

- [ ] **Step 2: API 클라이언트**

```ts
// frontend/src/apis/adminEnrichment.ts
import type {
  CandidateView, CandidateSummary, PolicyEnrichmentDetail,
  EnrichmentJobView, ReferenceSite,
} from '../types/adminEnrichment';
import { apiFetch } from './client';   // 기존 fetch wrapper

export interface CandidateQuery {
  needsReview?: boolean;
  statuses?: string[];
  detailLevels?: string[];
  q?: string;
  page?: number;
  size?: number;
}

export async function fetchCandidates(q: CandidateQuery) {
  const params = new URLSearchParams();
  if (q.needsReview != null) params.set('needsReview', String(q.needsReview));
  if (q.q) params.set('q', q.q);
  q.statuses?.forEach(s => params.append('statuses', s));
  q.detailLevels?.forEach(d => params.append('detailLevels', d));
  params.set('page', String(q.page ?? 0));
  params.set('size', String(q.size ?? 20));
  return apiFetch<{ content: CandidateView[]; totalElements: number }>(
    `/api/v1/admin/enrichment/candidates?${params}`);
}

export async function fetchSummary() {
  return apiFetch<CandidateSummary>('/api/v1/admin/enrichment/candidates/summary');
}

export async function fetchPolicyDetail(policyId: number) {
  return apiFetch<PolicyEnrichmentDetail>(`/api/v1/admin/enrichment/policies/${policyId}`);
}

export async function updateReferenceSites(policyId: number, body: ReferenceSite[]) {
  return apiFetch<void>(`/api/v1/admin/enrichment/policies/${policyId}/reference-sites`, {
    method: 'PUT', body: JSON.stringify(body),
  });
}

export async function createEnrichmentJob(policyId: number, urls?: ReferenceSite[]) {
  return apiFetch<{ jobId: number; status: 'PENDING' }>(
    `/api/v1/admin/enrichment/policies/${policyId}/jobs`,
    { method: 'POST', body: JSON.stringify({ urls: urls ?? null }) });
}

export async function fetchJob(jobId: number) {
  return apiFetch<EnrichmentJobView>(`/api/v1/admin/enrichment/jobs/${jobId}`);
}
```

- [ ] **Step 3: react-query 훅 — 폴링 포함**

```tsx
// frontend/src/hooks/useAdminEnrichment.ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from '../apis/adminEnrichment';
import type { CandidateQuery, ReferenceSite } from '../types/adminEnrichment';

const POLL_FAST_MS = 3000;
const POLL_SLOW_MS = 5000;
const BACKOFF_AFTER_MS = 60_000;

export function useCandidates(q: CandidateQuery) {
  return useQuery({
    queryKey: ['adminEnrichment', 'candidates', q],
    queryFn: () => api.fetchCandidates(q),
    refetchInterval: (data) => {
      const items = data?.content ?? [];
      const hasActive = items.some(c =>
        c.latestJob && (c.latestJob.status === 'PENDING' || c.latestJob.status === 'RUNNING')
      );
      if (!hasActive) return false;
      const oldest = Math.min(...items
        .filter(c => c.latestJob)
        .map(c => Date.parse(c.latestJob!.requestedAt)));
      return Date.now() - oldest > BACKOFF_AFTER_MS ? POLL_SLOW_MS : POLL_FAST_MS;
    },
  });
}

export function useSummary() {
  return useQuery({
    queryKey: ['adminEnrichment', 'summary'],
    queryFn: api.fetchSummary,
  });
}

export function usePolicyDetail(policyId: number | null) {
  return useQuery({
    queryKey: ['adminEnrichment', 'policy', policyId],
    queryFn: () => api.fetchPolicyDetail(policyId!),
    enabled: policyId != null,
    refetchInterval: (data) => {
      const j = data?.recentJobs[0];
      if (!j || j.status === 'SUCCESS' || j.status === 'FAILED') return false;
      const started = Date.parse(j.requestedAt);
      return Date.now() - started > BACKOFF_AFTER_MS ? POLL_SLOW_MS : POLL_FAST_MS;
    },
  });
}

export function useUpdateReferenceSites(policyId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ReferenceSite[]) => api.updateReferenceSites(policyId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['adminEnrichment', 'policy', policyId] }),
  });
}

export function useCreateJob(policyId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (urls?: ReferenceSite[]) => api.createEnrichmentJob(policyId, urls),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['adminEnrichment', 'policy', policyId] });
      qc.invalidateQueries({ queryKey: ['adminEnrichment', 'candidates'] });
    },
  });
}
```

- [ ] **Step 4: 타입체크**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/types/adminEnrichment.ts \
        frontend/src/apis/adminEnrichment.ts \
        frontend/src/hooks/useAdminEnrichment.ts
git commit -m "feat(admin-fe): enrichment 검토용 API/훅 + 폴링 backoff"
```

---

### Task 15: 작은 컴포넌트 두 개 — `EnrichmentJobBadge`, `EnrichmentReferenceSiteEditor`

**Files:**
- Create: `frontend/src/pages/admin/enrichment/EnrichmentJobBadge.tsx`
- Create: `frontend/src/pages/admin/enrichment/EnrichmentReferenceSiteEditor.tsx`
- Test: `frontend/src/pages/admin/enrichment/__tests__/EnrichmentReferenceSiteEditor.test.tsx`

- [ ] **Step 1: `EnrichmentJobBadge` 구현**

```tsx
import type { JobStatus } from '../../../types/adminEnrichment';

const STYLES: Record<JobStatus, { bg: string; label: string }> = {
  PENDING: { bg: 'bg-gray-200',  label: '대기' },
  RUNNING: { bg: 'bg-blue-200',  label: '진행' },
  SUCCESS: { bg: 'bg-green-200', label: '성공' },
  FAILED:  { bg: 'bg-red-200',   label: '실패' },
};

export function EnrichmentJobBadge({ status }: { status: JobStatus }) {
  const s = STYLES[status];
  return <span className={`px-2 py-0.5 rounded text-xs ${s.bg}`}>{s.label}</span>;
}
```

- [ ] **Step 2: `EnrichmentReferenceSiteEditor` 실패 테스트 작성**

```tsx
// __tests__/EnrichmentReferenceSiteEditor.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { EnrichmentReferenceSiteEditor } from '../EnrichmentReferenceSiteEditor';

describe('EnrichmentReferenceSiteEditor', () => {
  test('https 가 아닌 URL 입력 시 인라인 에러', () => {
    render(<EnrichmentReferenceSiteEditor initialSites={[]} onSave={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText(/URL/i), { target: { value: 'not-a-url' } });
    fireEvent.click(screen.getByRole('button', { name: /추가/ }));
    expect(screen.getByText(/올바른 URL/i)).toBeInTheDocument();
  });

  test('동일 URL 중복 입력 시 인라인 에러', () => {
    render(<EnrichmentReferenceSiteEditor
        initialSites={[{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }]}
        onSave={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText(/URL/i),
        { target: { value: 'https://a.example.com' } });
    fireEvent.click(screen.getByRole('button', { name: /추가/ }));
    expect(screen.getByText(/이미 등록/i)).toBeInTheDocument();
  });

  test('저장 클릭 시 onSave가 ADMIN 플래그로 호출된다', () => {
    const onSave = vi.fn();
    render(<EnrichmentReferenceSiteEditor initialSites={[]} onSave={onSave} />);
    fireEvent.change(screen.getByPlaceholderText(/이름/i), { target: { value: '확인된 사이트' } });
    fireEvent.change(screen.getByPlaceholderText(/URL/i),
        { target: { value: 'https://verified.example.com' } });
    fireEvent.click(screen.getByRole('button', { name: /추가/ }));
    fireEvent.click(screen.getByRole('button', { name: /저장/ }));
    expect(onSave).toHaveBeenCalledWith([
      { name: '확인된 사이트', url: 'https://verified.example.com', source: 'ADMIN' },
    ]);
  });
});
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
cd frontend && npm test -- src/pages/admin/enrichment/__tests__/EnrichmentReferenceSiteEditor.test.tsx
```

- [ ] **Step 4: `EnrichmentReferenceSiteEditor` 구현**

```tsx
import { useState } from 'react';
import type { ReferenceSite } from '../../../types/adminEnrichment';

interface Props {
  initialSites: ReferenceSite[];
  onSave: (sites: ReferenceSite[]) => void;
}

const URL_REGEX = /^https?:\/\/.+/i;

export function EnrichmentReferenceSiteEditor({ initialSites, onSave }: Props) {
  const [sites, setSites] = useState<ReferenceSite[]>(initialSites);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [error, setError] = useState<string | null>(null);

  function add() {
    setError(null);
    if (!URL_REGEX.test(url)) {
      setError('올바른 URL 형식이 아닙니다 (https://)');
      return;
    }
    if (sites.some(s => s.url === url)) {
      setError('이미 등록된 URL 입니다');
      return;
    }
    setSites([...sites, { name: name || url, url, source: 'ADMIN' }]);
    setName(''); setUrl('');
  }

  function remove(target: string) {
    setSites(sites.filter(s => s.url !== target));
  }

  return (
    <div className="space-y-2">
      <ul className="space-y-1">
        {sites.map(s => (
          <li key={s.url} className="flex items-center gap-2 text-sm">
            <span className={`px-1.5 text-xs rounded ${s.source === 'ADMIN' ? 'bg-amber-200' : 'bg-gray-200'}`}>
              {s.source}
            </span>
            <a href={s.url} target="_blank" rel="noreferrer" className="underline">{s.url}</a>
            <button type="button" onClick={() => remove(s.url)} className="ml-auto text-xs text-red-600">제거</button>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <input value={name} onChange={e => setName(e.target.value)}
               placeholder="이름 (선택)" className="border px-2 py-1 text-sm" />
        <input value={url}  onChange={e => setUrl(e.target.value)}
               placeholder="URL (https://...)" className="border px-2 py-1 text-sm flex-1" />
        <button type="button" onClick={add} className="px-3 py-1 text-sm bg-gray-100">추가</button>
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
      <button type="button" onClick={() => onSave(sites)} className="px-3 py-1 text-sm bg-blue-600 text-white">
        저장
      </button>
    </div>
  );
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd frontend && npm test -- src/pages/admin/enrichment/__tests__/EnrichmentReferenceSiteEditor.test.tsx
```

Expected: 3 PASS.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/pages/admin/enrichment/EnrichmentJobBadge.tsx \
        frontend/src/pages/admin/enrichment/EnrichmentReferenceSiteEditor.tsx \
        frontend/src/pages/admin/enrichment/__tests__/EnrichmentReferenceSiteEditor.test.tsx
git commit -m "feat(admin-fe): EnrichmentJobBadge + ReferenceSiteEditor (URL 입력·검증)"
```

---

### Task 16: 사이드 패널 — `EnrichmentReviewPanel`

**Files:**
- Create: `frontend/src/pages/admin/enrichment/EnrichmentReviewPanel.tsx`
- Test: `frontend/src/pages/admin/enrichment/__tests__/EnrichmentReviewPanel.test.tsx`

- [ ] **Step 1: 실패 테스트 작성 — 재크롤 버튼 비활성 조건**

```tsx
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EnrichmentReviewPanel } from '../EnrichmentReviewPanel';
import * as api from '../../../../apis/adminEnrichment';

function renderWithClient(ui: React.ReactNode) {
  const client = new QueryClient();
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('EnrichmentReviewPanel — 재크롤 버튼 비활성 조건', () => {
  beforeEach(() => {
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue({
      policyId: 1, title: '테스트', detailLevel: 'MEDIUM',
      enrichment: null, referenceSites: [], recentJobs: [], needsReview: true,
    } as any);
  });

  test('referenceSites 비어있으면 비활성', async () => {
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).toBeDisabled();
  });

  test('PENDING 잡이 있으면 비활성', async () => {
    (api.fetchPolicyDetail as any).mockResolvedValueOnce({
      policyId: 1, title: '테스트', detailLevel: 'MEDIUM',
      enrichment: null,
      referenceSites: [{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }],
      recentJobs: [{ id: 1, status: 'PENDING', policyId: 1, attempt: 1,
                     requestedBy: 'x', errorMessage: null,
                     requestedAt: new Date().toISOString(), startedAt: null,
                     finishedAt: null, requestedUrls: [] }],
      needsReview: true,
    } as any);
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).toBeDisabled();
  });

  test('referenceSites 있고 진행중 잡 없으면 활성', async () => {
    (api.fetchPolicyDetail as any).mockResolvedValueOnce({
      policyId: 1, title: '테스트', detailLevel: 'MEDIUM',
      enrichment: null,
      referenceSites: [{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }],
      recentJobs: [],
      needsReview: true,
    } as any);
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).not.toBeDisabled();
  });
});
```

- [ ] **Step 2: 패널 구현**

```tsx
import { useEffect, useRef, useState } from 'react';
import { usePolicyDetail, useUpdateReferenceSites, useCreateJob } from '../../../hooks/useAdminEnrichment';
import { EnrichmentReferenceSiteEditor } from './EnrichmentReferenceSiteEditor';
import { EnrichmentJobBadge } from './EnrichmentJobBadge';
import type { ReferenceSite } from '../../../types/adminEnrichment';

export function EnrichmentReviewPanel({ policyId }: { policyId: number }) {
  const { data, isLoading } = usePolicyDetail(policyId);
  const updateSites = useUpdateReferenceSites(policyId);
  const createJob = useCreateJob(policyId);
  const [confirmOpen, setConfirmOpen] = useState(false);

  // 잡 완료 토스트 (마지막 본 상태와 비교)
  const lastStatus = useRef<string | null>(null);
  useEffect(() => {
    const cur = data?.recentJobs[0]?.status ?? null;
    if (lastStatus.current && (cur === 'SUCCESS' || cur === 'FAILED') && cur !== lastStatus.current) {
      // 프로젝트에 토스트 컴포넌트가 있다면 호출. 없으면 alert 임시 대체.
      // toast.success/.error or notify(...)
    }
    lastStatus.current = cur;
  }, [data?.recentJobs]);

  if (isLoading || !data) return <div className="p-4 text-sm">로딩 중...</div>;

  const active = data.recentJobs[0];
  const disabled = data.referenceSites.length === 0
                || (active != null && (active.status === 'PENDING' || active.status === 'RUNNING'));

  return (
    <aside className="p-4 space-y-4 border-l w-[420px]">
      <h2 className="font-semibold">정책 #{data.policyId} — {data.title}</h2>

      <section>
        <h3 className="text-sm font-semibold mb-1">Enrichment 현황</h3>
        {data.enrichment ? (
          <ul className="text-xs space-y-0.5">
            <li>status: {data.enrichment.status ?? '-'}</li>
            <li>confidence: {data.enrichment.confidence ?? '-'}</li>
            <li>fetched: {data.enrichment.fetchedAt ?? '-'}</li>
            <li>extractor: {data.enrichment.extractor ?? '-'}</li>
          </ul>
        ) : (
          <p className="text-xs text-gray-500">아직 enrich 되지 않음</p>
        )}
      </section>

      <section>
        <h3 className="text-sm font-semibold mb-1">Reference URLs</h3>
        <EnrichmentReferenceSiteEditor
          initialSites={data.referenceSites}
          onSave={(sites: ReferenceSite[]) => updateSites.mutate(sites)}
        />
      </section>

      <section>
        <h3 className="text-sm font-semibold mb-1">잡 이력 (최근 5)</h3>
        <ul className="text-xs space-y-0.5">
          {data.recentJobs.map(j => (
            <li key={j.id} className="flex items-center gap-2">
              <EnrichmentJobBadge status={j.status} />
              <span>#{j.id} · {j.requestedAt}</span>
              {j.errorMessage && <span className="text-red-600">— {j.errorMessage}</span>}
            </li>
          ))}
          {data.recentJobs.length === 0 && <li className="text-gray-500">잡 없음</li>}
        </ul>
      </section>

      <button
        type="button"
        disabled={disabled || createJob.isPending}
        onClick={() => setConfirmOpen(true)}
        className="w-full py-2 bg-blue-600 text-white text-sm disabled:bg-gray-300"
      >
        재크롤 실행
      </button>

      {confirmOpen && (
        <div role="dialog" className="fixed inset-0 bg-black/30 flex items-center justify-center">
          <div className="bg-white p-4 space-y-2 w-[360px]">
            <p className="font-semibold">"{data.title}" 재크롤을 실행할까요?</p>
            <ul className="text-xs">
              {data.referenceSites.map(s => <li key={s.url}>· {s.url}</li>)}
            </ul>
            <div className="flex justify-end gap-2 text-sm">
              <button onClick={() => setConfirmOpen(false)}>취소</button>
              <button
                onClick={() => { setConfirmOpen(false); createJob.mutate(undefined); }}
                className="bg-blue-600 text-white px-3 py-1">실행</button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}
```

- [ ] **Step 3: 테스트 통과 확인**

```bash
cd frontend && npm test -- src/pages/admin/enrichment/__tests__/EnrichmentReviewPanel.test.tsx
```

Expected: 3 PASS.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/admin/enrichment/EnrichmentReviewPanel.tsx \
        frontend/src/pages/admin/enrichment/__tests__/EnrichmentReviewPanel.test.tsx
git commit -m "feat(admin-fe): EnrichmentReviewPanel — 진단/URL 편집/잡 이력/재크롤 confirm"
```

---

### Task 17: 후보 목록·summary 카드 + 탭 통합

**Files:**
- Create: `frontend/src/pages/admin/enrichment/EnrichmentSummaryCards.tsx`
- Create: `frontend/src/pages/admin/enrichment/EnrichmentCandidateTable.tsx`
- Create: `frontend/src/pages/admin/AdminEnrichmentReviewTab.tsx`
- Modify: `frontend/src/pages/admin/AdminIngestionPage.tsx`
- Test: `frontend/src/pages/admin/enrichment/__tests__/EnrichmentCandidateTable.test.tsx`

- [ ] **Step 1: Summary cards 구현**

```tsx
import { useSummary } from '../../../hooks/useAdminEnrichment';

export function EnrichmentSummaryCards() {
  const { data } = useSummary();
  if (!data) return null;
  const failed = ['FETCH_FAILED','LLM_FAILED','PARSE_FAILED','LOW_CONFIDENCE','NO_LINK','TOO_SHORT']
      .reduce((sum, k) => sum + (data.byStatus[k as keyof typeof data.byStatus] ?? 0), 0);
  return (
    <div className="grid grid-cols-4 gap-3 mb-4">
      <Card label="전체" value={data.total} />
      <Card label="검토 필요" value={data.needsReview} />
      <Card label="실패" value={failed} />
      <Card label="LITE" value={data.byDetailLevel.LITE ?? 0} />
    </div>
  );
}

function Card({ label, value }: { label: string; value: number }) {
  return (
    <div className="p-3 border rounded">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-2xl font-semibold">{value}</p>
    </div>
  );
}
```

- [ ] **Step 2: `EnrichmentCandidateTable` 실패 테스트 작성**

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EnrichmentCandidateTable } from '../EnrichmentCandidateTable';
import * as api from '../../../../apis/adminEnrichment';

function renderWithClient(ui: React.ReactNode) {
  return render(<QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>);
}

describe('EnrichmentCandidateTable', () => {
  beforeEach(() => {
    vi.spyOn(api, 'fetchCandidates').mockResolvedValue({
      content: [
        { id: 1, title: '청년주택', organization: '서울시',
          status: 'TOO_SHORT', confidence: 0.42, detailLevel: 'MEDIUM',
          needsReview: true, latestJob: null },
      ],
      totalElements: 1,
    } as any);
  });

  test('row 클릭 시 onSelect 호출', async () => {
    const onSelect = vi.fn();
    renderWithClient(<EnrichmentCandidateTable onSelect={onSelect} />);
    fireEvent.click(await screen.findByText('청년주택'));
    expect(onSelect).toHaveBeenCalledWith(1);
  });

  test('checkbox 토글 시 fetchCandidates 가 다시 호출된다', async () => {
    renderWithClient(<EnrichmentCandidateTable onSelect={() => {}} />);
    await screen.findByText('청년주택');
    fireEvent.click(screen.getByLabelText(/검토 필요만/));
    await waitFor(() => expect(api.fetchCandidates).toHaveBeenCalledTimes(2));
  });
});
```

- [ ] **Step 3: 테이블 구현**

```tsx
import { useState } from 'react';
import { useCandidates } from '../../../hooks/useAdminEnrichment';

interface Props { onSelect: (policyId: number) => void; }

export function EnrichmentCandidateTable({ onSelect }: Props) {
  const [needsReview, setNeedsReview] = useState(true);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, isLoading } = useCandidates({ needsReview, q, page, size: 20 });

  return (
    <div>
      <div className="flex items-center gap-3 mb-2 text-sm">
        <label className="flex items-center gap-1">
          <input type="checkbox" checked={needsReview}
                 onChange={e => setNeedsReview(e.target.checked)} />
          검토 필요만
        </label>
        <input value={q} onChange={e => setQ(e.target.value)}
               placeholder="제목/기관 검색" className="border px-2 py-1 flex-1" />
      </div>

      <table className="w-full text-sm">
        <thead className="bg-gray-50 text-left">
          <tr>
            <th className="p-2">제목</th>
            <th className="p-2">상태</th>
            <th className="p-2">신뢰도</th>
            <th className="p-2">레벨</th>
            <th className="p-2">최근 잡</th>
          </tr>
        </thead>
        <tbody>
          {isLoading && <tr><td colSpan={5} className="p-4">로딩...</td></tr>}
          {data?.content.map(c => (
            <tr key={c.id} onClick={() => onSelect(c.id)} className="cursor-pointer hover:bg-gray-50">
              <td className="p-2">{c.title}</td>
              <td className="p-2">{c.status ?? '-'}</td>
              <td className="p-2">{c.confidence ?? '-'}</td>
              <td className="p-2">{c.detailLevel}</td>
              <td className="p-2">{c.latestJob?.status ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="flex justify-between mt-2 text-xs">
        <span>전체 {data?.totalElements ?? 0}건</span>
        <div className="flex gap-2">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>이전</button>
          <span>{page + 1}</span>
          <button onClick={() => setPage(p => p + 1)}
                  disabled={(data?.content.length ?? 0) < 20}>다음</button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: `AdminEnrichmentReviewTab` 구현**

```tsx
import { useState } from 'react';
import { EnrichmentSummaryCards } from './enrichment/EnrichmentSummaryCards';
import { EnrichmentCandidateTable } from './enrichment/EnrichmentCandidateTable';
import { EnrichmentReviewPanel } from './enrichment/EnrichmentReviewPanel';

export function AdminEnrichmentReviewTab() {
  const [selected, setSelected] = useState<number | null>(null);
  return (
    <div className="p-4">
      <EnrichmentSummaryCards />
      <div className="flex gap-4">
        <div className="flex-1"><EnrichmentCandidateTable onSelect={setSelected} /></div>
        {selected != null && <EnrichmentReviewPanel policyId={selected} />}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: `AdminIngestionPage` 에 탭 시스템 도입**

기존 `AdminIngestionPage.tsx` 의 본문을 `IngestionHealthTab` 컴포넌트로 추출 후, 상위에 탭 라우터 추가:

```tsx
// AdminIngestionPage.tsx
import { useState } from 'react';
import { IngestionHealthTab } from './IngestionHealthTab';        // 기존 본문 이동
import { AdminEnrichmentReviewTab } from './AdminEnrichmentReviewTab';

type TabKey = 'health' | 'enrichment';

export default function AdminIngestionPage() {
  const [tab, setTab] = useState<TabKey>('health');
  return (
    <div>
      <nav className="flex gap-2 border-b px-4">
        <TabButton active={tab === 'health'}     onClick={() => setTab('health')}>수집 현황</TabButton>
        <TabButton active={tab === 'enrichment'} onClick={() => setTab('enrichment')}>Enrichment 검토</TabButton>
      </nav>
      {tab === 'health'     && <IngestionHealthTab />}
      {tab === 'enrichment' && <AdminEnrichmentReviewTab />}
    </div>
  );
}

function TabButton({ active, ...props }: { active: boolean } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button {...props} className={`px-3 py-2 text-sm ${active ? 'border-b-2 border-blue-600' : ''}`} />;
}
```

> 주: `IngestionHealthTab.tsx` 는 기존 `AdminIngestionPage` 본문(KPI/Daily/Failures 등) 을 그대로 옮기는 단순 추출. import 경로만 정리.

- [ ] **Step 6: 모든 프론트 테스트 통과 확인**

```bash
cd frontend && npm test -- src/pages/admin/enrichment
cd frontend && npm run build
```

Expected: PASS, build 성공.

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/pages/admin/
git commit -m "feat(admin-fe): Enrichment 검토 탭 — summary·table·패널·AdminIngestionPage 통합"
```

---

## Stage G — 통합 테스트·운영·문서

### Task 18: 잡 라이프사이클 통합 테스트 + OPS 문서

**Files:**
- Create: `backend/src/test/java/com/youthfit/policy/application/service/EnrichmentJobLifecycleIntegrationTest.java`
- Modify: `docs/OPS.md`

- [ ] **Step 1: `@SpringBootTest` 통합 테스트 작성**

```java
package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.*;
import com.youthfit.policy.infrastructure.N8nForceEnrichClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
class EnrichmentJobLifecycleIntegrationTest {

    @Autowired EnrichmentJobService service;
    @Autowired EnrichmentJobRepository jobRepo;
    @Autowired PolicyRepository policyRepo;
    @Autowired EnrichmentJobTimeoutScheduler scheduler;

    @MockBean N8nForceEnrichClient n8n;

    @Test
    void PENDING_RUNNING_SUCCESS_라이프사이클() {
        doNothing().when(n8n).forceEnrich(anyLong(), anyLong(), anyList());
        Policy policy = policyRepo.save(PolicyTestFixtures.withReferenceSites(
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com"))));

        EnrichmentJob created = service.create(policy.getId(), "admin@youthfit", null);
        assertThat(created.getStatus()).isEqualTo(EnrichmentJobStatus.PENDING);

        service.markRunning(created.getId());
        assertThat(jobRepo.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrichmentJobStatus.RUNNING);

        service.complete(created.getId(), EnrichmentJobStatus.SUCCESS, null);
        assertThat(jobRepo.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrichmentJobStatus.SUCCESS);
    }

    @Test
    void 동일정책_동시_생성은_unique_index로_막힌다() {
        doNothing().when(n8n).forceEnrich(anyLong(), anyLong(), anyList());
        Policy policy = policyRepo.save(PolicyTestFixtures.withReferenceSites(
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com"))));

        service.create(policy.getId(), "admin@youthfit", null);
        org.junit.jupiter.api.Assertions.assertThrows(
                EnrichmentJobConflictException.class,
                () -> service.create(policy.getId(), "admin@youthfit", null));
    }
}
```

> 주: `PolicyTestFixtures.withReferenceSites` 는 Task 5 에서 도입한 헬퍼. 통합 테스트는 Testcontainers postgres 가 설정돼 있다면 자동 작동, 아니라면 H2 + jsonb 호환 어댑터가 필요 — 이 경우 본 테스트만 `@DisabledIfEnvironmentVariable(named="CI", matches="true")` 같은 가드를 두고 로컬 검증으로 둔다.

- [ ] **Step 2: 통합 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.youthfit.policy.application.service.EnrichmentJobLifecycleIntegrationTest"
```

Expected: 2 PASS (또는 CI 가드 시 SKIP).

- [ ] **Step 3: `docs/OPS.md` 환경변수 항목 추가**

```markdown
## 어드민 Enrichment 강제 재크롤 — 환경변수

| 키 | 용도 | 예시 |
|---|---|---|
| `N8N_FORCE_ENRICH_WEBHOOK_URL` | 백엔드 → n8n force-enrich 워크플로우 webhook | `https://n8n.internal/webhook/force-enrich` |
| `INGESTION_INTERNAL_API_KEY` | 백엔드 ↔ n8n 콜백 공유 시크릿 (기존 키 재사용) | (32+자 랜덤) |
| `ENRICHMENT_TIMEOUT_FIXED_DELAY_MS` | 타임아웃 스케줄러 주기 (기본 60000) | `60000` |

- 5분 이상 PENDING/RUNNING 인 잡은 자동으로 `FAILED`(error=timeout) 처리됨.
- 같은 정책에 1시간 내 5회 초과 재크롤 시도 시 429 응답.
- 멀티 인스턴스 도입 시 ShedLock 추가 필요 (현재 단일 인스턴스 가정).
```

- [ ] **Step 4: 백엔드 + 프론트엔드 전체 빌드 확인**

```bash
cd backend && ./gradlew build -x test && cd ../frontend && npm run build
```

Expected: 모두 성공.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/test/java/com/youthfit/policy/application/service/EnrichmentJobLifecycleIntegrationTest.java \
        docs/OPS.md
git commit -m "test(policy): EnrichmentJob 라이프사이클 통합 테스트 + OPS 문서"
```

---

## 자가 점검 (Self-Review 결과)

- **Spec coverage**:
  - §3 (데이터 모델) → Task 1·2·3·4 ✓
  - §4 (API 표면) → Task 11·12 ✓
  - §5 (실행 흐름·타임아웃) → Task 7·8·9·13 ✓
  - §6 (Frontend UI) → Task 14·15·16·17 ✓
  - §7 (에러 처리 매트릭스) → Task 7 (n8n_unreachable, 멱등 콜백), Task 8 (timeout), 패널의 fetchedAt 비교 표시는 §16 패널 enrichment 진단 영역에서 표시 가능 (별도 구현 단계 없음) ✓
  - §8 (테스트 전략) → 각 Task 의 테스트 step ✓
  - §9 (운영 안전장치) → Task 7 (레이트리밋), Task 8 (타임아웃), Task 18 (시크릿·문서) ✓
  - §10 (마이그레이션 순서) → Task 1·2·3·9·11·12·13·17 으로 단계화 ✓

- **Placeholder scan**: 없음. (모든 step 에 실행 가능한 코드/명령 포함)

- **Type consistency**:
  - `EnrichmentJobStatus.PENDING/RUNNING/SUCCESS/FAILED` 일관 사용 ✓
  - `PolicyReferenceSite(name, url, source)` 시그니처 일관 ✓
  - `EnrichmentJobService` 메서드: `create / markRunning / complete` — Task 7·12·18 일치 ✓
  - 콜백 status payload: 백엔드 `EnrichmentJobStatus` enum 그대로 — n8n 워크플로우가 동일 문자열 송신해야 함 (Task 13 명시)

- **명시적 가정 (스펙 §12 Open Questions 해소)**:
  - 어드민 식별자: `Authentication.getName()`
  - `requested_urls` 스키마: `[{name, url, source}]`
  - 폴링 backoff: 3초/5초 fixed, visibility 기반 정지는 범위 외

---

Plan complete and saved to `docs/superpowers/plans/2026-05-21-admin-enrichment-review.md`.
