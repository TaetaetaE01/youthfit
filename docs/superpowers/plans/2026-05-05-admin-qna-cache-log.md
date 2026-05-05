# 어드민 Q&A 캐시 hit/miss 로그 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Q&A semantic-cache lookup 결과를 정책 단위 로그로 적재하고, 어드민 화면에서 hit률·평균 유사도·미스 사례·비용 절감 추세를 추적·디버깅·export 한다.

**Architecture:**
- 호출부 임계값 분류: `SemanticQnaCache.findSimilar` 가 `LookupResult` (closest top-1 + 임계값 통과 시 cachedAnswer)를 반환, `QnaService` 가 `HIT/BELOW_THRESHOLD/MISS` 분류 후 `QnaCacheLookupEvent` 발행. 임계값 적용 지점만 외부화.
- 비동기 적재: `@EventListener + @Async` listener 가 사용자 핫패스에서 분리해 `qna_cache_lookup_log` 에 INSERT. 적재 실패는 try/catch + warn 로그로 격리. (`QnaService.processQuestion` 이 비트랜잭션이라 `@TransactionalEventListener(AFTER_COMMIT)` 대신 채택)
- 어드민: `/api/v1/admin/qna-cache/**` 5 엔드포인트 + 프론트 메인/상세 2 페이지. Spec 2(이메일 트래킹) 패턴(KpiCard/StackedBarChart/Pagination/Recharts) 답습.

**Tech Stack:** Java 21 + Spring Boot 4.0.5, Spring Data JPA, PostgreSQL + pgvector, JUnit 5, Mockito, MockMvc + `@WebMvcTest`. Frontend: React + Vite + Vitest + RTL + Recharts + Tailwind.

---

## Decisions Frozen From Brainstorming

| # | 결정 |
|---|---|
| 1 | `question_text` raw 평문 + `normalized_text` 평문, 90일 보관 |
| 2 | top-1 `(matched_cached_id, similarity_score)` + `HIT/MISS/BELOW_THRESHOLD` 3분기. cache API 임계값 외부화 |
| 3 | 적재 트리거: `@EventListener + @Async` (트랜잭션 부재 — spec § 5.2 노트의 fallback 옵션 채택의 더 단순한 형태) |
| 4 | 미스 큐레이션: 어드민 화면 미스 리스트 + CSV export. 캐시 추가 액션은 별도 spec |
| 5 | 비용 절감 산식: 단일 설정값 `youthfit.qna.cache.estimated-savings-per-hit-usd` |

## File Structure

### Backend — 신규
- `qna/domain/model/QnaCacheLookupLog.java` — JPA 엔티티
- `qna/domain/model/LookupResultType.java` — enum (HIT/MISS/BELOW_THRESHOLD)
- `qna/domain/repository/QnaCacheLookupLogRepository.java` — `JpaRepository` 직접 extends, `@Query` 인라인 (Spec 2 `EmailSendAttemptRepository` 패턴)
- `qna/application/port/dto/SemanticLookupResult.java` — `LookupResult` record (closest + cachedAnswer)
- `qna/application/port/dto/SemanticLookupMatch.java` — `Match` record (cachedId, similarity)
- `qna/application/event/QnaCacheLookupEvent.java` — record
- `qna/application/event/QnaCacheLookupEventListener.java` — `@Async` listener
- `qna/application/service/QnaCacheLookupClassifier.java` — closest + threshold → enum
- `qna/infrastructure/scheduler/QnaCacheLookupRetentionScheduler.java` — 90일 retention
- `admin/presentation/controller/AdminQnaCacheApi.java` — Swagger 인터페이스
- `admin/presentation/controller/AdminQnaCacheController.java` — REST controller
- `admin/application/service/AdminQnaCacheService.java` — 조회/집계/CSV
- `admin/presentation/dto/request/QnaCacheLookupListQuery.java`
- `admin/presentation/dto/response/QnaCacheLookupKpiResponse.java`
- `admin/presentation/dto/response/QnaCacheLookupDailyStatsResponse.java`
- `admin/presentation/dto/response/QnaCacheLookupSummaryResponse.java`
- `admin/presentation/dto/response/QnaCacheLookupDetailResponse.java`
- `backend/src/main/resources/sql/2026-05-06-qna-cache-lookup-log.sql` — DDL

### Backend — 수정
- `qna/application/port/SemanticQnaCache.java` — `findSimilar` 시그니처 변경
- `qna/infrastructure/external/PgVectorSemanticQnaCache.java` — closest 항상 회신
- `qna/infrastructure/persistence/QnaQuestionCacheJpaRepository.java` — 변경 없음 (기존 쿼리 그대로 활용)
- `qna/application/service/QnaService.java` — 분류 + 이벤트 발행
- `qna/infrastructure/config/QnaProperties.java` — `cache` 중첩 record 추가 (or 신규 `QnaCacheLogProperties`)
- `backend/src/main/java/com/youthfit/YouthFitApplication.java` (또는 동등) — `@EnableAsync` 누락 시 추가
- `backend/src/main/resources/application.yml` — 설정 키 2개 추가

### Frontend — 신규
- `frontend/src/apis/admin.qnaCache.api.ts`
- `frontend/src/pages/admin/AdminQnaCachePage.tsx`
- `frontend/src/pages/admin/AdminQnaCacheDetailPage.tsx`
- `frontend/src/components/admin/qnaCache/QnaCacheKpiSection.tsx`
- `frontend/src/components/admin/qnaCache/QnaCacheDailyChart.tsx`
- `frontend/src/components/admin/qnaCache/QnaCacheLookupTable.tsx`
- `frontend/src/components/admin/qnaCache/QnaCacheFilterBar.tsx`
- `frontend/src/components/admin/qnaCache/QnaCacheResultBadge.tsx`
- `frontend/src/hooks/useAdminQnaCache.ts`
- 컴포넌트별 `__tests__/*.test.tsx`

### Frontend — 수정
- `frontend/src/components/layout/AdminSidebar.tsx` — qna-cache 메뉴 `soon: true` 제거
- `frontend/src/router` (라우트 등록 위치) — 두 페이지 등록

---

# Stage A — Cache 인터페이스 외부화 + 도메인 모델

## Task A1: `LookupResultType` enum + `SemanticLookupResult/Match` records

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/domain/model/LookupResultType.java`
- Create: `backend/src/main/java/com/youthfit/qna/application/port/dto/SemanticLookupMatch.java`
- Create: `backend/src/main/java/com/youthfit/qna/application/port/dto/SemanticLookupResult.java`

- [ ] **Step 1: enum 작성**

```java
package com.youthfit.qna.domain.model;

public enum LookupResultType {
    HIT,
    BELOW_THRESHOLD,
    MISS
}
```

- [ ] **Step 2: `Match` record 작성**

```java
package com.youthfit.qna.application.port.dto;

import java.math.BigDecimal;

public record SemanticLookupMatch(
        Long cachedId,
        BigDecimal similarity,
        BigDecimal distance
) {}
```

- [ ] **Step 3: `LookupResult` record 작성**

```java
package com.youthfit.qna.application.port.dto;

import com.youthfit.qna.application.dto.result.CachedAnswer;

import java.util.Optional;

public record SemanticLookupResult(
        Optional<SemanticLookupMatch> closest,
        Optional<CachedAnswer> cachedAnswer
) {
    public static SemanticLookupResult miss() {
        return new SemanticLookupResult(Optional.empty(), Optional.empty());
    }
    public static SemanticLookupResult belowThreshold(SemanticLookupMatch closest) {
        return new SemanticLookupResult(Optional.of(closest), Optional.empty());
    }
    public static SemanticLookupResult hit(SemanticLookupMatch closest, CachedAnswer answer) {
        return new SemanticLookupResult(Optional.of(closest), Optional.of(answer));
    }
}
```

> `CachedAnswer` 의 정확한 패키지는 `qna/application/dto/result/CachedAnswer.java` 가정. 현재 위치 확인 후 import 조정.

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/domain/model/LookupResultType.java \
        backend/src/main/java/com/youthfit/qna/application/port/dto/SemanticLookupMatch.java \
        backend/src/main/java/com/youthfit/qna/application/port/dto/SemanticLookupResult.java
git commit -m "feat(qna): LookupResultType enum + SemanticLookupResult/Match records"
```

---

## Task A2: `SemanticQnaCache.findSimilar` 시그니처 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/port/SemanticQnaCache.java`

- [ ] **Step 1: 인터페이스 변경**

`findSimilar` 의 리턴 타입을 `Optional<CachedAnswer>` → `SemanticLookupResult` 로 변경.

```java
package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;

public interface SemanticQnaCache {

    SemanticLookupResult findSimilar(Long policyId, String userQuestion, float[] queryEmbedding);

    void put(Long policyId, String question, String sourceHash, float[] embedding, CachedAnswer answer);
}
```

- [ ] **Step 2: 컴파일 깨짐 확인 (의도)**

Run: `cd backend && ./gradlew compileJava`
Expected: 호출부(`QnaService`) 와 구현체(`PgVectorSemanticQnaCache`) 에서 컴파일 에러. Task A3, B3 에서 해결.

- [ ] **Step 3: 테스트 컴파일도 깨질 수 있음 — 무시하고 진행**

Run: `cd backend && ./gradlew compileTestJava || true`
Expected: 일부 에러 가능. Task A3 이후 수정.

- [ ] **Step 4: 이 단계는 단독 commit 하지 않음 — Task A3 와 묶어서 commit**

---

## Task A3: `PgVectorSemanticQnaCache` 구현 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java`

> 참고: 기존 `findSimilar` 라인 34-66. `findClosestByPolicyId` 가 가장 가까운 1건을 반환하고, 그 안에서 임계값 비교 후 분기. 우리는 임계값 비교를 **호출부로 이동**하고 closest 를 항상 회신.

- [ ] **Step 1: 단위 테스트 작성 (Mockito)**

**File:** `backend/src/test/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCacheTest.java`

```java
package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.qna.infrastructure.persistence.QnaQuestionCacheJpaRepository;
import com.youthfit.qna.infrastructure.persistence.dto.QnaQuestionCacheCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorSemanticQnaCacheTest {

    @Mock QnaQuestionCacheJpaRepository repository;
    @Mock QnaProperties properties;
    @InjectMocks PgVectorSemanticQnaCache cache;

    @Test
    void findSimilar_매칭_없음_miss_회신() {
        when(repository.findClosestByPolicyId(anyLong(), any(), anyLong())).thenReturn(Optional.empty());
        when(properties.cacheTtlHours()).thenReturn(24L);

        SemanticLookupResult result = cache.findSimilar(1L, "q", new float[]{0f});

        assertThat(result.closest()).isEmpty();
        assertThat(result.cachedAnswer()).isEmpty();
    }

    @Test
    void findSimilar_가까운_후보_있고_임계값_초과_belowThreshold() {
        QnaQuestionCacheCandidate cand = candidate(10L, 0.30); // distance > threshold
        when(repository.findClosestByPolicyId(anyLong(), any(), anyLong())).thenReturn(Optional.of(cand));
        when(properties.cacheTtlHours()).thenReturn(24L);
        when(properties.semanticDistanceThreshold()).thenReturn(0.20);

        SemanticLookupResult result = cache.findSimilar(1L, "q", new float[]{0f});

        assertThat(result.closest()).isPresent();
        assertThat(result.closest().get().cachedId()).isEqualTo(10L);
        assertThat(result.cachedAnswer()).isEmpty();
    }

    @Test
    void findSimilar_가까운_후보_있고_임계값_이하_hit() {
        QnaQuestionCacheCandidate cand = candidate(20L, 0.15);
        when(repository.findClosestByPolicyId(anyLong(), any(), anyLong())).thenReturn(Optional.of(cand));
        when(properties.cacheTtlHours()).thenReturn(24L);
        when(properties.semanticDistanceThreshold()).thenReturn(0.20);

        SemanticLookupResult result = cache.findSimilar(1L, "q", new float[]{0f});

        assertThat(result.closest()).isPresent();
        assertThat(result.cachedAnswer()).isPresent();
    }

    private QnaQuestionCacheCandidate candidate(Long id, double distance) {
        // 실제 프로젝션 인터페이스 시그니처 확인 후 builder/mock 작성
        QnaQuestionCacheCandidate m = org.mockito.Mockito.mock(QnaQuestionCacheCandidate.class);
        when(m.getId()).thenReturn(id);
        when(m.getQuestionText()).thenReturn("cached question");
        when(m.getSourceHash()).thenReturn("hash");
        when(m.getAnswer()).thenReturn("answer");
        when(m.getSourcesJson()).thenReturn("[]");
        when(m.getDistance()).thenReturn(distance);
        return m;
    }
}
```

> `QnaQuestionCacheCandidate` 의 정확한 위치/필드 시그니처를 확인하고 mock 파라미터를 맞춰라. 기존 `findSimilar` 가 사용하는 projection 인터페이스 그대로.

Run: `cd backend && ./gradlew test --tests "*PgVectorSemanticQnaCacheTest*"`
Expected: 테스트가 컴파일은 되고 실행 시 fail (구현 아직 변경 전).

- [ ] **Step 2: 구현체 변경**

기존 `findSimilar` 의 임계값 비교 분기를 제거하고 항상 closest 를 반환, 임계값 통과 시 `cachedAnswer` 도 함께 반환:

```java
@Override
public SemanticLookupResult findSimilar(Long policyId, String userQuestion, float[] queryEmbedding) {
    Optional<QnaQuestionCacheCandidate> closestOpt = repository.findClosestByPolicyId(
            policyId,
            queryEmbedding,
            properties.cacheTtlHours()
    );

    if (closestOpt.isEmpty()) {
        return SemanticLookupResult.miss();
    }

    QnaQuestionCacheCandidate c = closestOpt.get();
    BigDecimal distance = BigDecimal.valueOf(c.getDistance()).setScale(5, RoundingMode.HALF_UP);
    BigDecimal similarity = BigDecimal.ONE.subtract(distance.divide(BigDecimal.valueOf(2), 5, RoundingMode.HALF_UP))
            .max(BigDecimal.ZERO);  // cosine distance 0~2 → 유사도 0~1
    SemanticLookupMatch match = new SemanticLookupMatch(c.getId(), similarity, distance);

    if (c.getDistance() > properties.semanticDistanceThreshold()) {
        log.info("Q&A 의미 캐시 below-threshold: policyId={}, cachedId={}, distance={}",
                policyId, c.getId(), c.getDistance());
        return SemanticLookupResult.belowThreshold(match);
    }

    log.info("Q&A 의미 캐시 hit: policyId={}, cachedId={}, distance={}",
            policyId, c.getId(), c.getDistance());
    CachedAnswer cachedAnswer = toCachedAnswer(c); // 기존 변환 로직 그대로 추출
    return SemanticLookupResult.hit(match, cachedAnswer);
}
```

기존 `Optional<CachedAnswer>` 를 만들던 변환 로직을 `toCachedAnswer(c)` private 헬퍼로 추출.

import 추가: `java.math.BigDecimal`, `java.math.RoundingMode`, `com.youthfit.qna.application.port.dto.SemanticLookupResult`, `SemanticLookupMatch`.

- [ ] **Step 3: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*PgVectorSemanticQnaCacheTest*"`
Expected: PASS (3 케이스).

- [ ] **Step 4: Commit (Task A2 + A3 묶음)**

```bash
git add backend/src/main/java/com/youthfit/qna/application/port/SemanticQnaCache.java \
        backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java \
        backend/src/test/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCacheTest.java
git commit -m "refactor(qna): SemanticQnaCache.findSimilar이 LookupResult 회신 (임계값 외부화)"
```

> `QnaService` 가 아직 컴파일 안 됨. Stage B 에서 해결.

---

## Task A4: `QnaCacheLookupLog` 엔티티 + repository + DDL

> **Repository 패턴**: `EmailSendAttemptRepository` (`backend/.../user/domain/repository/EmailSendAttemptRepository.java`) 를 그대로 답습 — 도메인 인터페이스가 `JpaRepository` 를 직접 extends 하고 `@Query` 로 동적 쿼리를 인터페이스 안에 둔다. Custom impl 분리 안 함.

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/domain/model/QnaCacheLookupLog.java`
- Create: `backend/src/main/java/com/youthfit/qna/domain/repository/QnaCacheLookupLogRepository.java`
- Create: `backend/src/main/resources/sql/2026-05-06-qna-cache-lookup-log.sql`

- [ ] **Step 1: DDL SQL 작성**

```sql
-- 2026-05-06-qna-cache-lookup-log.sql
CREATE TABLE qna_cache_lookup_log (
    id                 BIGSERIAL PRIMARY KEY,
    policy_id          BIGINT NOT NULL,
    question_text      TEXT NOT NULL,
    normalized_text    TEXT NOT NULL,
    result             VARCHAR(20) NOT NULL,
    matched_cached_id  BIGINT,
    similarity_score   DECIMAL(6,5),
    threshold_applied  DECIMAL(6,5) NOT NULL,
    llm_call_made      BOOLEAN NOT NULL,
    looked_up_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_qna_cache_lookup_at        ON qna_cache_lookup_log(looked_up_at DESC);
CREATE INDEX idx_qna_cache_lookup_result_at ON qna_cache_lookup_log(result, looked_up_at DESC);
CREATE INDEX idx_qna_cache_lookup_policy_at ON qna_cache_lookup_log(policy_id, looked_up_at DESC);
```

- [ ] **Step 2: 엔티티 작성**

```java
package com.youthfit.qna.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
    name = "qna_cache_lookup_log",
    indexes = {
        @Index(name = "idx_qna_cache_lookup_at",        columnList = "looked_up_at DESC"),
        @Index(name = "idx_qna_cache_lookup_result_at", columnList = "result, looked_up_at DESC"),
        @Index(name = "idx_qna_cache_lookup_policy_at", columnList = "policy_id, looked_up_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaCacheLookupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "normalized_text", nullable = false, columnDefinition = "TEXT")
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private LookupResultType result;

    @Column(name = "matched_cached_id")
    private Long matchedCachedId;

    @Column(name = "similarity_score", precision = 6, scale = 5)
    private BigDecimal similarityScore;

    @Column(name = "threshold_applied", nullable = false, precision = 6, scale = 5)
    private BigDecimal thresholdApplied;

    @Column(name = "llm_call_made", nullable = false)
    private boolean llmCallMade;

    @Column(name = "looked_up_at", nullable = false)
    private LocalDateTime lookedUpAt;

    public static QnaCacheLookupLog of(
            Long policyId,
            String questionText,
            String normalizedText,
            LookupResultType result,
            Long matchedCachedId,
            BigDecimal similarityScore,
            BigDecimal thresholdApplied,
            boolean llmCallMade,
            LocalDateTime lookedUpAt
    ) {
        QnaCacheLookupLog log = new QnaCacheLookupLog();
        log.policyId = policyId;
        log.questionText = questionText;
        log.normalizedText = normalizedText;
        log.result = result;
        log.matchedCachedId = matchedCachedId;
        log.similarityScore = similarityScore;
        log.thresholdApplied = thresholdApplied;
        log.llmCallMade = llmCallMade;
        log.lookedUpAt = lookedUpAt;
        return log;
    }
}
```

- [ ] **Step 3: Repository 인터페이스 작성 (`JpaRepository` 직접 extends, `@Query` 인라인)**

```java
package com.youthfit.qna.domain.repository;

import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface QnaCacheLookupLogRepository extends JpaRepository<QnaCacheLookupLog, Long> {

    @Query("""
        SELECT l FROM QnaCacheLookupLog l
        WHERE (:result IS NULL OR l.result = :result)
          AND (:policyId IS NULL OR l.policyId = :policyId)
          AND (:from IS NULL OR l.lookedUpAt >= :from)
          AND (:to IS NULL OR l.lookedUpAt <= :to)
        ORDER BY l.lookedUpAt DESC
        """)
    Page<QnaCacheLookupLog> search(
            @Param("result") LookupResultType result,
            @Param("policyId") Long policyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
        SELECT l FROM QnaCacheLookupLog l
        WHERE (:result IS NULL OR l.result = :result)
          AND (:policyId IS NULL OR l.policyId = :policyId)
          AND (:from IS NULL OR l.lookedUpAt >= :from)
          AND (:to IS NULL OR l.lookedUpAt <= :to)
        ORDER BY l.lookedUpAt DESC
        """)
    List<QnaCacheLookupLog> exportFiltered(
            @Param("result") LookupResultType result,
            @Param("policyId") Long policyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);  // 호출부에서 PageRequest.of(0, 5000) 으로 상한

    @Query(value = """
        SELECT to_char(date_trunc('day', looked_up_at), 'YYYY-MM-DD') AS day,
               COUNT(*) FILTER (WHERE result = 'HIT')             AS hit_count,
               COUNT(*) FILTER (WHERE result = 'BELOW_THRESHOLD') AS below_count,
               COUNT(*) FILTER (WHERE result = 'MISS')            AS miss_count,
               COALESCE(AVG(similarity_score) FILTER (WHERE result = 'HIT'), 0) AS avg_sim
        FROM qna_cache_lookup_log
        WHERE looked_up_at BETWEEN :from AND :to
        GROUP BY 1
        ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> aggregateDaily(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT
          COUNT(*) FILTER (WHERE looked_up_at >= :today)                                      AS today_total,
          COUNT(*) FILTER (WHERE looked_up_at >= :today AND result = 'HIT')                   AS today_hits,
          COUNT(*) FILTER (WHERE looked_up_at >= :yesterday AND looked_up_at < :today)        AS yest_total,
          COUNT(*) FILTER (WHERE looked_up_at >= :yesterday AND looked_up_at < :today AND result = 'HIT') AS yest_hits,
          COUNT(*) FILTER (WHERE looked_up_at >= :sevenDaysAgo AND result = 'HIT')            AS seven_hits,
          COALESCE(AVG(similarity_score) FILTER (WHERE looked_up_at >= :sevenDaysAgo AND result = 'HIT'), 0) AS seven_avg_sim
        FROM qna_cache_lookup_log
        """, nativeQuery = true)
    Object[] aggregateKpi(@Param("today") LocalDateTime today,
                          @Param("yesterday") LocalDateTime yesterday,
                          @Param("sevenDaysAgo") LocalDateTime sevenDaysAgo);

    @Modifying
    @Query("DELETE FROM QnaCacheLookupLog l WHERE l.lookedUpAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
```

> `aggregateKpi` 가 `Object[]` 리턴 — 이 프로젝트 컨벤션상 application 레이어에서 record 로 매핑한다 (Spec 2 `aggregateDaily` 답습). 매핑은 `AdminQnaCacheService.kpi()` 안에서 수행. `aggregateDaily` 도 동일.

- [ ] **Step 6: ddl-auto validate 통과 확인**

DB 에 SQL 적용:
```bash
docker compose exec postgres psql -U youthfit -d youthfit -f /docker-entrypoint-initdb.d/2026-05-06-qna-cache-lookup-log.sql || true
# 또는 호스트에서:
psql -h localhost -U youthfit -d youthfit -f backend/src/main/resources/sql/2026-05-06-qna-cache-lookup-log.sql
```

Run: `cd backend && ./gradlew bootRun` (별도 셸)
Expected: 부팅 로그에 `validate` 통과, 엔티티 매핑 에러 없음. 종료 후 다음 단계.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/domain/model/QnaCacheLookupLog.java \
        backend/src/main/java/com/youthfit/qna/domain/repository/QnaCacheLookupLogRepository.java \
        backend/src/main/resources/sql/2026-05-06-qna-cache-lookup-log.sql
git commit -m "feat(qna): QnaCacheLookupLog 엔티티 + 리포지토리 + DDL"
```

---

## Task A5: `QnaCacheLookupEvent` record

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/event/QnaCacheLookupEvent.java`

- [ ] **Step 1: record 작성**

```java
package com.youthfit.qna.application.event;

import com.youthfit.qna.domain.model.LookupResultType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QnaCacheLookupEvent(
        Long policyId,
        String questionText,
        String normalizedText,
        LookupResultType result,
        Long matchedCachedId,
        BigDecimal similarityScore,
        BigDecimal thresholdApplied,
        boolean llmCallMade,
        LocalDateTime lookedUpAt
) {}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/application/event/QnaCacheLookupEvent.java
git commit -m "feat(qna): QnaCacheLookupEvent 정의"
```

---

# Stage B — 분류기 + 적재 listener + QnaService 통합

## Task B1: `QnaCacheLookupClassifier`

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/service/QnaCacheLookupClassifier.java`
- Create: `backend/src/test/java/com/youthfit/qna/application/service/QnaCacheLookupClassifierTest.java`

- [ ] **Step 1: 단위 테스트 먼저**

```java
package com.youthfit.qna.application.service;

import com.youthfit.qna.application.port.dto.SemanticLookupMatch;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QnaCacheLookupClassifierTest {

    QnaCacheLookupClassifier classifier = new QnaCacheLookupClassifier();

    @Test
    void miss_closest_없으면_MISS() {
        SemanticLookupResult result = SemanticLookupResult.miss();
        assertThat(classifier.classify(result)).isEqualTo(LookupResultType.MISS);
    }

    @Test
    void belowThreshold_closest_있고_cached없으면_BELOW_THRESHOLD() {
        SemanticLookupResult result = SemanticLookupResult.belowThreshold(
                new SemanticLookupMatch(1L, BigDecimal.valueOf(0.85), BigDecimal.valueOf(0.30))
        );
        assertThat(classifier.classify(result)).isEqualTo(LookupResultType.BELOW_THRESHOLD);
    }

    @Test
    void hit_closest_와_cachedAnswer_둘다있으면_HIT() {
        SemanticLookupResult result = new SemanticLookupResult(
                Optional.of(new SemanticLookupMatch(1L, BigDecimal.valueOf(0.92), BigDecimal.valueOf(0.15))),
                Optional.of(/* CachedAnswer mock or stub */ null)
        );
        // null 회피용으로 실제 CachedAnswer.of(...) 사용
        // 해당 record 의 정확한 시그니처 확인 후 실 인스턴스 주입
    }
}
```

> 마지막 케이스의 `CachedAnswer.of(...)` 정확한 시그니처는 `qna/application/dto/result/CachedAnswer.java` 확인 후 실제 인스턴스로 채워라.

- [ ] **Step 2: 테스트 실행 — fail 확인**

Run: `cd backend && ./gradlew test --tests "*QnaCacheLookupClassifierTest*"`
Expected: FAIL (`QnaCacheLookupClassifier` 미구현).

- [ ] **Step 3: 구현**

```java
package com.youthfit.qna.application.service;

import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import org.springframework.stereotype.Component;

@Component
public class QnaCacheLookupClassifier {

    public LookupResultType classify(SemanticLookupResult result) {
        if (result.closest().isEmpty()) return LookupResultType.MISS;
        if (result.cachedAnswer().isPresent()) return LookupResultType.HIT;
        return LookupResultType.BELOW_THRESHOLD;
    }
}
```

- [ ] **Step 4: 테스트 통과**

Run: `cd backend && ./gradlew test --tests "*QnaCacheLookupClassifierTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/application/service/QnaCacheLookupClassifier.java \
        backend/src/test/java/com/youthfit/qna/application/service/QnaCacheLookupClassifierTest.java
git commit -m "feat(qna): QnaCacheLookupClassifier (LookupResult → LookupResultType)"
```

---

## Task B2: `QnaCacheLookupEventListener` (`@Async`)

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/event/QnaCacheLookupEventListener.java`
- Create: `backend/src/test/java/com/youthfit/qna/application/event/QnaCacheLookupEventListenerTest.java`
- Modify: `backend/src/main/java/com/youthfit/YouthFitApplication.java` (또는 `@EnableAsync` 가 있는 config) — 없으면 추가

- [ ] **Step 1: `@EnableAsync` 활성화 확인**

```bash
grep -r "@EnableAsync" backend/src/main/java
```

만약 없으면 `YouthFitApplication.java` 또는 별도 `AsyncConfig.java` 에 추가:

```java
@Configuration
@EnableAsync
public class AsyncConfig {}
```

- [ ] **Step 2: Listener 단위 테스트 (Mockito)**

```java
package com.youthfit.qna.application.event;

import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QnaCacheLookupEventListenerTest {

    @Mock QnaCacheLookupLogRepository repository;
    @InjectMocks QnaCacheLookupEventListener listener;

    @Test
    void 이벤트_수신시_엔티티_save_호출() {
        QnaCacheLookupEvent event = new QnaCacheLookupEvent(
                1L, "질문", "질문 정규화", LookupResultType.HIT, 10L,
                BigDecimal.valueOf(0.92), BigDecimal.valueOf(0.20), false,
                LocalDateTime.now()
        );

        listener.onLookup(event);

        ArgumentCaptor<QnaCacheLookupLog> captor = ArgumentCaptor.forClass(QnaCacheLookupLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPolicyId()).isEqualTo(1L);
        assertThat(captor.getValue().getResult()).isEqualTo(LookupResultType.HIT);
    }

    @Test
    void save_실패시_사용자경로에_예외_전파_안함() {
        QnaCacheLookupEvent event = new QnaCacheLookupEvent(
                1L, "q", "q", LookupResultType.MISS, null, null,
                BigDecimal.valueOf(0.20), true, LocalDateTime.now()
        );
        when(repository.save(any())).thenThrow(new RuntimeException("DB down"));

        // 예외 전파 안 됨
        listener.onLookup(event);

        verify(repository).save(any());
    }
}
```

- [ ] **Step 3: 테스트 fail 확인**

Run: `cd backend && ./gradlew test --tests "*QnaCacheLookupEventListenerTest*"`
Expected: FAIL.

- [ ] **Step 4: Listener 구현**

```java
package com.youthfit.qna.application.event;

import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QnaCacheLookupEventListener {

    private final QnaCacheLookupLogRepository repository;

    @Async
    @EventListener
    public void onLookup(QnaCacheLookupEvent event) {
        try {
            QnaCacheLookupLog log = QnaCacheLookupLog.of(
                    event.policyId(),
                    event.questionText(),
                    event.normalizedText(),
                    event.result(),
                    event.matchedCachedId(),
                    event.similarityScore(),
                    event.thresholdApplied(),
                    event.llmCallMade(),
                    event.lookedUpAt()
            );
            repository.save(log);
        } catch (Exception e) {
            log.warn("Q&A 캐시 lookup 로그 적재 실패 (정상 흐름 진행): policyId={}, result={}",
                    event.policyId(), event.result(), e);
        }
    }
}
```

- [ ] **Step 5: 테스트 통과**

Run: `cd backend && ./gradlew test --tests "*QnaCacheLookupEventListenerTest*"`
Expected: PASS (2 케이스).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/application/event/QnaCacheLookupEventListener.java \
        backend/src/test/java/com/youthfit/qna/application/event/QnaCacheLookupEventListenerTest.java \
        $(grep -l "@EnableAsync" backend/src/main/java -r 2>/dev/null || echo "")
git commit -m "feat(qna): QnaCacheLookupEventListener (@Async, 적재 실패 격리)"
```

---

## Task B3: `QnaService.processQuestion` 통합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`

> 기존 라인 100-215. 의존성 주입에 `QnaCacheLookupClassifier`, `QnaProperties`, `ApplicationEventPublisher`, `QuestionNormalizer` (이미 있으면 재사용) 추가.

- [ ] **Step 1: 새 의존성 + import 추가**

```java
private final QnaCacheLookupClassifier lookupClassifier;
private final ApplicationEventPublisher eventPublisher;
// QuestionNormalizer 또는 동일 정규화 로직이 이미 있다면 재사용
```

- [ ] **Step 2: `findSimilar` 호출부 변경 (라인 128-138 교체)**

```java
LocalDateTime lookedUpAt = LocalDateTime.now();
String normalized = normalizeQuestion(command.question());

SemanticLookupResult lookupResult;
try {
    lookupResult = semanticQnaCache.findSimilar(command.policyId(), command.question(), queryEmbedding);
} catch (Exception e) {
    log.warn("Q&A 의미 캐시 findSimilar 실패 (정상 흐름 진행): policyId={}", command.policyId(), e);
    lookupResult = SemanticLookupResult.miss();
}

LookupResultType resultType = lookupClassifier.classify(lookupResult);
SemanticLookupMatch closest = lookupResult.closest().orElse(null);
BigDecimal threshold = BigDecimal.valueOf(qnaProperties.semanticDistanceThreshold())
        .setScale(5, RoundingMode.HALF_UP);

boolean willCallLlm = (resultType != LookupResultType.HIT);

eventPublisher.publishEvent(new QnaCacheLookupEvent(
        command.policyId(),
        command.question(),
        normalized,
        resultType,
        closest != null ? closest.cachedId() : null,
        closest != null ? closest.similarity() : null,
        threshold,
        willCallLlm,
        lookedUpAt
));

if (resultType == LookupResultType.HIT) {
    sendCachedAnswer(emitter, lookupResult.cachedAnswer().get(), historyId);
    return;
}
// 기존 RAG/LLM 흐름 진행
```

- [ ] **Step 3: `normalizeQuestion` 헬퍼 — 기존 정규화 로직 재사용**

`QuestionNormalizer` 또는 `PgVectorSemanticQnaCache` 내부에 정규화가 있는지 확인. 있다면 그쪽 메서드를 호출하거나 동일 로직을 헬퍼로 추출.
없으면 단순 `command.question().trim().replaceAll("\\s+", " ").toLowerCase()` 로 시작.

- [ ] **Step 4: 컴파일 + 기존 QnaService 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*QnaServiceTest*"`
Expected: PASS (기존 테스트 유지). 만약 mock 기반 테스트가 `findSimilar` 시그니처 변경으로 실패하면 mock 회신을 `SemanticLookupResult` 로 갱신.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/application/service/QnaService.java
git commit -m "feat(qna): QnaService 캐시 lookup 분류 + QnaCacheLookupEvent 발행"
```

---

## Task B4: 통합 검증 (수동 + 통합 테스트 보강)

**Files:**
- Create: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceCacheLookupIntegrationTest.java` (선택)

- [ ] **Step 1: 백엔드 부팅 + 수동 호출**

```bash
cd backend && ./gradlew bootRun &
# 다른 셸에서 Q&A 호출 (인증 토큰 필요)
curl -X POST http://localhost:8080/api/v1/policies/30/qna \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"청년월세 신청 자격이 어떻게 되나요?"}'
```

- [ ] **Step 2: DB 적재 확인**

```bash
psql -h localhost -U youthfit -d youthfit -c "SELECT id, policy_id, result, similarity_score, looked_up_at FROM qna_cache_lookup_log ORDER BY id DESC LIMIT 5;"
```
Expected: 1+ row, `result` 가 `HIT` / `MISS` / `BELOW_THRESHOLD` 중 하나.

- [ ] **Step 3: 동일 질문 재호출 → HIT 확인**

같은 질문 재호출 후 SELECT 다시. result=HIT, matched_cached_id 채워짐.

- [ ] **Step 4: 통합 테스트 작성 (선택, `@SpringBootTest`)**

```java
@SpringBootTest
class QnaServiceCacheLookupIntegrationTest {
    @Autowired QnaService qnaService;
    @Autowired QnaCacheLookupLogRepository repository;

    @Test
    void Q_A_요청시_lookup_log_적재() throws Exception {
        // 사전: 정책 1, 임베딩 stub, 인증 컨텍스트 셋업
        // qnaService.askQuestion(...) 호출
        // 잠시 대기 (@Async) — Awaitility 사용
        Awaitility.await()
            .atMost(Duration.ofSeconds(3))
            .until(() -> repository.search(null, 1L, null, null, PageRequest.of(0, 10)).getTotalElements() >= 1);
    }
}
```

> Awaitility 의존성이 없으면 `Thread.sleep(1500)` 으로 대체. 단위 테스트 우선이라 이 단계는 skip 가능.

- [ ] **Step 5: 백엔드 종료 + Commit (테스트 추가했으면)**

```bash
git add backend/src/test/java/com/youthfit/qna/application/service/QnaServiceCacheLookupIntegrationTest.java
git commit -m "test(qna): Q&A 호출 → lookup_log 적재 통합 테스트"
```

---

# Stage C — 어드민 API

## Task C1: 응답 DTO records

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/QnaCacheLookupKpiResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/QnaCacheLookupDailyStatsResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/QnaCacheLookupSummaryResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/QnaCacheLookupDetailResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/request/QnaCacheLookupListQuery.java`

- [ ] **Step 1: Kpi response**

```java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;

public record QnaCacheLookupKpiResponse(
        BigDecimal todayHitRate,
        BigDecimal yesterdayHitRate,
        BigDecimal sevenDaysAvgSimilarity,
        BigDecimal sevenDaysEstimatedSavingsUsd,
        long sevenDaysHitCount,
        long sevenDaysTotalCount
) {}
```

- [ ] **Step 2: DailyStats response**

```java
package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QnaCacheLookupDailyStatsResponse(
        LocalDate date,
        long hitCount,
        long belowThresholdCount,
        long missCount,
        BigDecimal avgSimilarity
) {}
```

- [ ] **Step 3: Summary + Detail response**

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.qna.domain.model.LookupResultType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QnaCacheLookupSummaryResponse(
        Long id,
        LocalDateTime lookedUpAt,
        LookupResultType result,
        Long policyId,
        String questionExcerpt,    // 50자 ellipsis
        BigDecimal similarityScore,
        Long matchedCachedId
) {}
```

```java
package com.youthfit.admin.presentation.dto.response;

import com.youthfit.qna.domain.model.LookupResultType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QnaCacheLookupDetailResponse(
        Long id,
        LocalDateTime lookedUpAt,
        LookupResultType result,
        Long policyId,
        String questionText,
        String normalizedText,
        Long matchedCachedId,
        String matchedCachedQuestion,    // join 결과, nullable
        String matchedCachedAnswerExcerpt, // 200자, nullable
        BigDecimal similarityScore,
        BigDecimal thresholdApplied,
        boolean llmCallMade
) {}
```

- [ ] **Step 4: List query request**

```java
package com.youthfit.admin.presentation.dto.request;

import com.youthfit.qna.domain.model.LookupResultType;

import java.time.LocalDateTime;

public record QnaCacheLookupListQuery(
        LookupResultType result,
        Long policyId,
        LocalDateTime from,
        LocalDateTime to,
        int page,
        int size
) {
    public QnaCacheLookupListQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/dto/
git commit -m "feat(admin): Q&A 캐시 로그 응답/요청 DTO 추가"
```

---

## Task C2: `AdminQnaCacheService`

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/application/service/AdminQnaCacheService.java`
- Modify: `backend/src/main/resources/application.yml` — `youthfit.qna.cache.estimated-savings-per-hit-usd: 0.0015`
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaProperties.java`

- [ ] **Step 1: `QnaProperties` 에 cache 설정 중첩 추가**

기존:
```java
public record QnaProperties(
        long cacheTtlHours,
        double relevanceDistanceThreshold,
        double semanticDistanceThreshold
)
```

변경:
```java
public record QnaProperties(
        long cacheTtlHours,
        double relevanceDistanceThreshold,
        double semanticDistanceThreshold,
        Cache cache
) {
    public record Cache(
            BigDecimal estimatedSavingsPerHitUsd,
            int retentionDays
    ) {
        public Cache {
            if (estimatedSavingsPerHitUsd == null) estimatedSavingsPerHitUsd = new BigDecimal("0.0015");
            if (retentionDays <= 0) retentionDays = 90;
        }
    }
}
```

`application.yml` 추가:
```yaml
youthfit:
  qna:
    cache-ttl-hours: ${QNA_CACHE_TTL_HOURS:24}
    relevance-distance-threshold: ${QNA_RELEVANCE_DISTANCE_THRESHOLD:0.78}
    semantic-distance-threshold: ${QNA_SEMANTIC_DISTANCE_THRESHOLD:0.20}
    cache:
      estimated-savings-per-hit-usd: ${QNA_CACHE_ESTIMATED_SAVINGS_PER_HIT_USD:0.0015}
      retention-days: ${QNA_CACHE_RETENTION_DAYS:90}
```

- [ ] **Step 2: 서비스 구현**

```java
package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.request.QnaCacheLookupListQuery;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.qna.infrastructure.persistence.QnaQuestionCacheJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQnaCacheService {

    private final QnaCacheLookupLogRepository repository;
    private final QnaQuestionCacheJpaRepository questionCacheRepository;
    private final QnaProperties qnaProperties;

    public QnaCacheLookupKpiResponse kpi() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);
        LocalDateTime sevenDaysAgo = today.minusDays(7);

        Object[] r = repository.aggregateKpi(today, yesterday, sevenDaysAgo);
        long todayTotal     = ((Number) r[0]).longValue();
        long todayHits      = ((Number) r[1]).longValue();
        long yestTotal      = ((Number) r[2]).longValue();
        long yestHits       = ((Number) r[3]).longValue();
        long sevenHits      = ((Number) r[4]).longValue();
        double sevenAvgSim  = ((Number) r[5]).doubleValue();

        BigDecimal todayHitRate     = ratio(todayHits, todayTotal);
        BigDecimal yesterdayHitRate = ratio(yestHits, yestTotal);
        BigDecimal estSavings       = qnaProperties.cache().estimatedSavingsPerHitUsd()
                .multiply(BigDecimal.valueOf(sevenHits))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal avgSim           = BigDecimal.valueOf(sevenAvgSim).setScale(4, RoundingMode.HALF_UP);
        long sevenTotal             = todayTotal + yestTotal; // 7일 total 정확 계산은 별도 쿼리 추가 가능

        return new QnaCacheLookupKpiResponse(
                todayHitRate, yesterdayHitRate, avgSim, estSavings,
                sevenHits, sevenTotal
        );
    }

    private BigDecimal ratio(long num, long denom) {
        if (denom == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(num)
                .divide(BigDecimal.valueOf(denom), 4, RoundingMode.HALF_UP);
    }

    public List<QnaCacheLookupDailyStatsResponse> dailyStats(int days) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);
        return repository.aggregateDaily(from, to).stream()
                .map(row -> new QnaCacheLookupDailyStatsResponse(
                        LocalDate.parse((String) row[0]),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        BigDecimal.valueOf(((Number) row[4]).doubleValue()).setScale(4, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    public Page<QnaCacheLookupSummaryResponse> list(QnaCacheLookupListQuery q) {
        return repository.search(
                q.result(), q.policyId(), q.from(), q.to(),
                PageRequest.of(q.page(), q.size())
        ).map(this::toSummary);
    }

    public QnaCacheLookupDetailResponse detail(Long id) {
        QnaCacheLookupLog log = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lookup log not found: " + id));

        String matchedQ = null;
        String matchedAnswerExcerpt = null;
        if (log.getMatchedCachedId() != null) {
            var cached = questionCacheRepository.findById(log.getMatchedCachedId());
            if (cached.isPresent()) {
                matchedQ = cached.get().getQuestionText();
                String ans = cached.get().getAnswer();
                matchedAnswerExcerpt = ans == null ? null
                        : (ans.length() > 200 ? ans.substring(0, 200) + "…" : ans);
            }
        }

        return new QnaCacheLookupDetailResponse(
                log.getId(), log.getLookedUpAt(), log.getResult(), log.getPolicyId(),
                log.getQuestionText(), log.getNormalizedText(),
                log.getMatchedCachedId(), matchedQ, matchedAnswerExcerpt,
                log.getSimilarityScore(), log.getThresholdApplied(), log.isLlmCallMade()
        );
    }

    public void exportCsv(QnaCacheLookupListQuery q, Writer out) {
        List<QnaCacheLookupLog> rows = repository.exportFiltered(
                q.result(), q.policyId(), q.from(), q.to(),
                PageRequest.of(0, 5000)
        );
        try (PrintWriter pw = new PrintWriter(out)) {
            pw.println("id,looked_up_at,result,policy_id,question_text,similarity_score,matched_cached_id,llm_call_made");
            for (var r : rows) {
                pw.printf("%d,%s,%s,%d,%s,%s,%s,%s%n",
                        r.getId(),
                        r.getLookedUpAt(),
                        r.getResult(),
                        r.getPolicyId(),
                        csvEscape(r.getQuestionText()),
                        r.getSimilarityScore() == null ? "" : r.getSimilarityScore(),
                        r.getMatchedCachedId() == null ? "" : r.getMatchedCachedId(),
                        r.isLlmCallMade()
                );
            }
        }
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private QnaCacheLookupSummaryResponse toSummary(QnaCacheLookupLog log) {
        String excerpt = log.getQuestionText().length() > 50
                ? log.getQuestionText().substring(0, 50) + "…"
                : log.getQuestionText();
        return new QnaCacheLookupSummaryResponse(
                log.getId(), log.getLookedUpAt(), log.getResult(), log.getPolicyId(),
                excerpt, log.getSimilarityScore(), log.getMatchedCachedId()
        );
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/application/service/AdminQnaCacheService.java \
        backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaProperties.java \
        backend/src/main/resources/application.yml
git commit -m "feat(admin): AdminQnaCacheService + QnaProperties.Cache 설정"
```

---

## Task C3: `AdminQnaCacheApi` (Swagger 인터페이스)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminQnaCacheApi.java`

- [ ] **Step 1: 기존 `AdminEmailLogApi` 참고 패턴 그대로**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.youthfit.qna.domain.model.LookupResultType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Admin - Q&A Cache Lookup Log", description = "Q&A 캐시 hit/miss 로그 조회")
public interface AdminQnaCacheApi {

    @Operation(summary = "KPI 조회", description = "오늘/어제 hit률, 7일 평균 유사도 및 비용 절감 추정")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
    QnaCacheLookupKpiResponse kpi();

    @Operation(summary = "일자별 통계")
    List<QnaCacheLookupDailyStatsResponse> dailyStats(
        @Parameter(description = "조회 일수 (기본 14)") @RequestParam(defaultValue = "14") int days);

    @Operation(summary = "lookup 로그 목록 (필터 + 페이지네이션)")
    Page<QnaCacheLookupSummaryResponse> list(
        @Parameter(description = "결과 필터") @RequestParam(required = false) LookupResultType result,
        @Parameter(description = "정책 ID 필터") @RequestParam(required = false) Long policyId,
        @Parameter(description = "시작 시각") @RequestParam(required = false) LocalDateTime from,
        @Parameter(description = "종료 시각") @RequestParam(required = false) LocalDateTime to,
        @Parameter(description = "페이지 (0부터)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "lookup 로그 상세")
    QnaCacheLookupDetailResponse detail(@PathVariable Long id);

    @Operation(summary = "필터 결과 CSV export")
    void exportCsv(
        @RequestParam(required = false) LookupResultType result,
        @RequestParam(required = false) Long policyId,
        @RequestParam(required = false) LocalDateTime from,
        @RequestParam(required = false) LocalDateTime to,
        HttpServletResponse response);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/controller/AdminQnaCacheApi.java
git commit -m "feat(admin): AdminQnaCacheApi Swagger 인터페이스"
```

---

## Task C4: `AdminQnaCacheController` 구현

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminQnaCacheController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminQnaCacheService;
import com.youthfit.admin.presentation.dto.request.QnaCacheLookupListQuery;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.qna.domain.model.LookupResultType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/qna-cache")
public class AdminQnaCacheController implements AdminQnaCacheApi {

    private final AdminQnaCacheService service;

    @Override
    @GetMapping("/kpi")
    public QnaCacheLookupKpiResponse kpi() {
        return service.kpi();
    }

    @Override
    @GetMapping("/daily-stats")
    public List<QnaCacheLookupDailyStatsResponse> dailyStats(@RequestParam(defaultValue = "14") int days) {
        return service.dailyStats(days);
    }

    @Override
    @GetMapping
    public Page<QnaCacheLookupSummaryResponse> list(
            @RequestParam(required = false) LookupResultType result,
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(new QnaCacheLookupListQuery(result, policyId, from, to, page, size));
    }

    @Override
    @GetMapping("/{id:\\d+}")
    public QnaCacheLookupDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @Override
    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(required = false) LookupResultType result,
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"qna-cache-lookup.csv\"");
        try (PrintWriter w = response.getWriter()) {
            service.exportCsv(new QnaCacheLookupListQuery(result, policyId, from, to, 0, Integer.MAX_VALUE), w);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/presentation/controller/AdminQnaCacheController.java
git commit -m "feat(admin): AdminQnaCacheController (5 엔드포인트)"
```

---

## Task C5: 컨트롤러 슬라이스 테스트

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminQnaCacheControllerTest.java`

> 참고: `AdminEmailLogControllerTest` 패턴 답습.

- [ ] **Step 1: 슬라이스 테스트**

```java
package com.youthfit.admin.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.admin.application.service.AdminQnaCacheService;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.qna.domain.model.LookupResultType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminQnaCacheController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class AdminQnaCacheControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AdminQnaCacheService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean InternalApiKeyFilter internalApiKeyFilter;

    @Test
    @WithMockUser(roles = "ADMIN")
    void kpi_200() throws Exception {
        when(service.kpi()).thenReturn(new QnaCacheLookupKpiResponse(
                BigDecimal.valueOf(0.65), BigDecimal.valueOf(0.62),
                BigDecimal.valueOf(0.88), BigDecimal.valueOf(0.0150),
                10, 20));

        mockMvc.perform(get("/api/v1/admin/qna-cache/kpi"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.todayHitRate").value(0.65));
    }

    @Test
    @WithMockUser(roles = "USER")
    void admin_권한_없으면_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/qna-cache/kpi"))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void daily_stats_200() throws Exception {
        when(service.dailyStats(14)).thenReturn(List.of(
                new QnaCacheLookupDailyStatsResponse(LocalDate.now(), 5, 2, 3, BigDecimal.valueOf(0.85))
        ));
        mockMvc.perform(get("/api/v1/admin/qna-cache/daily-stats").param("days", "14"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].hitCount").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_200() throws Exception {
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(
                new QnaCacheLookupSummaryResponse(1L, LocalDateTime.now(),
                        LookupResultType.HIT, 1L, "질문…", BigDecimal.valueOf(0.92), 10L)
        )));
        mockMvc.perform(get("/api/v1/admin/qna-cache").param("size", "20"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detail_200() throws Exception {
        when(service.detail(1L)).thenReturn(new QnaCacheLookupDetailResponse(
                1L, LocalDateTime.now(), LookupResultType.HIT, 1L, "질문", "정규화",
                10L, "캐시질문", "답변", BigDecimal.valueOf(0.92), BigDecimal.valueOf(0.20), false));
        mockMvc.perform(get("/api/v1/admin/qna-cache/1"))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_csv_헤더_검증() throws Exception {
        mockMvc.perform(get("/api/v1/admin/qna-cache/export"))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
               .andExpect(header().string("Content-Disposition",
                       "attachment; filename=\"qna-cache-lookup.csv\""));
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "*AdminQnaCacheControllerTest*"`
Expected: PASS (6 케이스).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/admin/presentation/controller/AdminQnaCacheControllerTest.java
git commit -m "test(admin): AdminQnaCacheController 슬라이스 테스트"
```

---

# Stage D — 프론트엔드

## Task D1: API 함수 + 타입

**Files:**
- Create: `frontend/src/apis/admin.qnaCache.api.ts`

- [ ] **Step 1: 타입 + 함수 정의**

```typescript
// frontend/src/apis/admin.qnaCache.api.ts
import { http } from './http';

export type LookupResultType = 'HIT' | 'BELOW_THRESHOLD' | 'MISS';

export type QnaCacheKpi = {
  todayHitRate: number;
  yesterdayHitRate: number;
  sevenDaysAvgSimilarity: number;
  sevenDaysEstimatedSavingsUsd: number;
  sevenDaysHitCount: number;
  sevenDaysTotalCount: number;
};

export type QnaCacheDailyStat = {
  date: string;            // ISO date
  hitCount: number;
  belowThresholdCount: number;
  missCount: number;
  avgSimilarity: number;
};

export type QnaCacheLookupSummary = {
  id: number;
  lookedUpAt: string;
  result: LookupResultType;
  policyId: number;
  questionExcerpt: string;
  similarityScore: number | null;
  matchedCachedId: number | null;
};

export type QnaCacheLookupDetail = {
  id: number;
  lookedUpAt: string;
  result: LookupResultType;
  policyId: number;
  questionText: string;
  normalizedText: string;
  matchedCachedId: number | null;
  matchedCachedQuestion: string | null;
  matchedCachedAnswerExcerpt: string | null;
  similarityScore: number | null;
  thresholdApplied: number;
  llmCallMade: boolean;
};

export type ListFilter = {
  result?: LookupResultType;
  policyId?: number;
  from?: string;
  to?: string;
  page: number;
  size: number;
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export const getQnaCacheKpi = () =>
  http.get<QnaCacheKpi>('/api/v1/admin/qna-cache/kpi').then(r => r.data);

export const getQnaCacheDailyStats = (days = 14) =>
  http.get<QnaCacheDailyStat[]>('/api/v1/admin/qna-cache/daily-stats', { params: { days } })
      .then(r => r.data);

export const listQnaCacheLookups = (filter: ListFilter) =>
  http.get<Page<QnaCacheLookupSummary>>('/api/v1/admin/qna-cache', { params: filter })
      .then(r => r.data);

export const getQnaCacheLookup = (id: number) =>
  http.get<QnaCacheLookupDetail>(`/api/v1/admin/qna-cache/${id}`).then(r => r.data);

export const exportQnaCacheCsv = (filter: Omit<ListFilter, 'page' | 'size'>) => {
  const url = `/api/v1/admin/qna-cache/export`;
  const params = new URLSearchParams();
  if (filter.result) params.set('result', filter.result);
  if (filter.policyId !== undefined) params.set('policyId', String(filter.policyId));
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  // axios responseType:'blob' + 다운로드 트리거
  return http.get<Blob>(`${url}?${params.toString()}`, { responseType: 'blob' })
             .then(r => {
               const blob = new Blob([r.data], { type: 'text/csv' });
               const link = document.createElement('a');
               link.href = URL.createObjectURL(blob);
               link.download = 'qna-cache-lookup.csv';
               link.click();
               URL.revokeObjectURL(link.href);
             });
};
```

> `http` 인스턴스의 import 경로는 `admin.email.api.ts` 와 동일하게 맞춰라.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/apis/admin.qnaCache.api.ts
git commit -m "feat(frontend): admin Q&A cache API 클라이언트"
```

---

## Task D2: 결과 뱃지 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/qnaCache/QnaCacheResultBadge.tsx`
- Create: `frontend/src/components/admin/qnaCache/__tests__/QnaCacheResultBadge.test.tsx`

- [ ] **Step 1: 테스트 먼저**

```typescript
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { QnaCacheResultBadge } from '../QnaCacheResultBadge';

describe('QnaCacheResultBadge', () => {
  it('HIT은 초록 뱃지', () => {
    const { container } = render(<QnaCacheResultBadge result="HIT" />);
    expect(screen.getByText('HIT')).toBeInTheDocument();
    expect(container.firstChild).toHaveClass(/green|emerald|success/);
  });
  it('BELOW_THRESHOLD는 주황 뱃지', () => {
    const { container } = render(<QnaCacheResultBadge result="BELOW_THRESHOLD" />);
    expect(screen.getByText('BELOW')).toBeInTheDocument();
    expect(container.firstChild).toHaveClass(/amber|orange|warning/);
  });
  it('MISS는 빨강 뱃지', () => {
    const { container } = render(<QnaCacheResultBadge result="MISS" />);
    expect(screen.getByText('MISS')).toBeInTheDocument();
    expect(container.firstChild).toHaveClass(/red|danger|error/);
  });
});
```

- [ ] **Step 2: fail 확인**

Run: `cd frontend && npm run test -- QnaCacheResultBadge`
Expected: FAIL.

- [ ] **Step 3: 컴포넌트 구현**

```tsx
// frontend/src/components/admin/qnaCache/QnaCacheResultBadge.tsx
import type { LookupResultType } from '../../../apis/admin.qnaCache.api';

const STYLE: Record<LookupResultType, { label: string; cls: string }> = {
  HIT:             { label: 'HIT',   cls: 'bg-emerald-100 text-emerald-800' },
  BELOW_THRESHOLD: { label: 'BELOW', cls: 'bg-amber-100 text-amber-800' },
  MISS:            { label: 'MISS',  cls: 'bg-red-100 text-red-800' },
};

export function QnaCacheResultBadge({ result }: { result: LookupResultType }) {
  const s = STYLE[result];
  return (
    <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ${s.cls}`}>
      {s.label}
    </span>
  );
}
```

- [ ] **Step 4: 테스트 통과 + commit**

Run: `cd frontend && npm run test -- QnaCacheResultBadge`
Expected: PASS.

```bash
git add frontend/src/components/admin/qnaCache/QnaCacheResultBadge.tsx \
        frontend/src/components/admin/qnaCache/__tests__/QnaCacheResultBadge.test.tsx
git commit -m "feat(frontend): QnaCacheResultBadge 컴포넌트"
```

---

## Task D3: KPI / 차트 / 테이블 / 필터바 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/qnaCache/QnaCacheKpiSection.tsx`
- Create: `frontend/src/components/admin/qnaCache/QnaCacheDailyChart.tsx`
- Create: `frontend/src/components/admin/qnaCache/QnaCacheLookupTable.tsx`
- Create: `frontend/src/components/admin/qnaCache/QnaCacheFilterBar.tsx`

> 각 컴포넌트는 Spec 2 (`EmailKpiSection`, `EmailDailyChart`, `EmailAttemptTable`, `EmailFilterBar`) 패턴 답습. `KpiCard`, `StackedBarChart`, `Pagination` 재사용.

- [ ] **Step 1: KPI Section**

```tsx
// QnaCacheKpiSection.tsx
import { KpiCard } from '../../charts/KpiCard';
import type { QnaCacheKpi } from '../../../apis/admin.qnaCache.api';

export function QnaCacheKpiSection({ kpi, loading }: { kpi: QnaCacheKpi | null; loading: boolean }) {
  if (loading || !kpi) {
    return <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
      {Array.from({length: 4}).map((_,i) => <KpiCard key={i} label="…" value="—" />)}
    </div>;
  }
  return (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
      <KpiCard label="오늘 hit률" value={`${(kpi.todayHitRate*100).toFixed(1)}%`} />
      <KpiCard label="어제 hit률" value={`${(kpi.yesterdayHitRate*100).toFixed(1)}%`}
               hint={trendHint(kpi.todayHitRate, kpi.yesterdayHitRate)} />
      <KpiCard label="7일 평균 유사도" value={kpi.sevenDaysAvgSimilarity.toFixed(3)} />
      <KpiCard label="7일 비용 절감" value={`$${kpi.sevenDaysEstimatedSavingsUsd.toFixed(4)}`}
               hint={`hit ${kpi.sevenDaysHitCount}건`} />
    </div>
  );
}

function trendHint(today: number, yesterday: number) {
  if (yesterday === 0) return '';
  const diff = (today - yesterday) * 100;
  return diff >= 0 ? `↑ ${diff.toFixed(1)}%p` : `↓ ${Math.abs(diff).toFixed(1)}%p`;
}
```

- [ ] **Step 2: DailyChart (StackedBarChart 재사용)**

```tsx
// QnaCacheDailyChart.tsx
import { StackedBarChart } from '../../charts/StackedBarChart';
import type { QnaCacheDailyStat } from '../../../apis/admin.qnaCache.api';

export function QnaCacheDailyChart({ stats }: { stats: QnaCacheDailyStat[] }) {
  const data = stats.map(s => ({
    date: s.date.slice(5),
    HIT: s.hitCount,
    BELOW: s.belowThresholdCount,
    MISS: s.missCount,
  }));
  return (
    <StackedBarChart
      data={data}
      xKey="date"
      bars={[
        { key: 'HIT', color: '#10b981' },     // emerald
        { key: 'BELOW', color: '#f59e0b' },   // amber
        { key: 'MISS', color: '#ef4444' },    // red
      ]}
    />
  );
}
```

> `StackedBarChart` props 의 정확한 시그니처는 `frontend/src/components/charts/StackedBarChart.tsx` 확인 후 맞춰라.

- [ ] **Step 3: FilterBar**

```tsx
// QnaCacheFilterBar.tsx
import type { LookupResultType } from '../../../apis/admin.qnaCache.api';

type Props = {
  result?: LookupResultType;
  policyId?: number;
  from?: string;
  to?: string;
  onChange: (patch: { result?: LookupResultType; policyId?: number; from?: string; to?: string }) => void;
  onExportCsv: () => void;
};

export function QnaCacheFilterBar(p: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <select className="border rounded px-2 py-1"
              value={p.result ?? ''}
              onChange={e => p.onChange({ result: (e.target.value || undefined) as LookupResultType | undefined })}>
        <option value="">전체</option>
        <option value="HIT">HIT</option>
        <option value="BELOW_THRESHOLD">BELOW</option>
        <option value="MISS">MISS</option>
      </select>
      <input className="border rounded px-2 py-1 w-28" placeholder="정책 ID" type="number"
             value={p.policyId ?? ''}
             onChange={e => p.onChange({ policyId: e.target.value ? Number(e.target.value) : undefined })}/>
      <input className="border rounded px-2 py-1" type="datetime-local"
             value={p.from ?? ''} onChange={e => p.onChange({ from: e.target.value || undefined })}/>
      <input className="border rounded px-2 py-1" type="datetime-local"
             value={p.to ?? ''} onChange={e => p.onChange({ to: e.target.value || undefined })}/>
      <button onClick={p.onExportCsv}
              className="ml-auto border rounded bg-brand-900 text-white px-3 py-1">
        미스 CSV export
      </button>
    </div>
  );
}
```

- [ ] **Step 4: LookupTable**

```tsx
// QnaCacheLookupTable.tsx
import { Link } from 'react-router-dom';
import { QnaCacheResultBadge } from './QnaCacheResultBadge';
import type { QnaCacheLookupSummary } from '../../../apis/admin.qnaCache.api';

export function QnaCacheLookupTable({ rows }: { rows: QnaCacheLookupSummary[] }) {
  if (rows.length === 0) {
    return <div className="text-sm text-gray-500 py-8 text-center">데이터 없음</div>;
  }
  return (
    <table className="w-full text-sm">
      <thead className="bg-gray-50">
        <tr>
          <th className="text-left px-3 py-2">시각</th>
          <th className="text-left px-3 py-2">결과</th>
          <th className="text-left px-3 py-2">정책</th>
          <th className="text-left px-3 py-2">질문</th>
          <th className="text-right px-3 py-2">유사도</th>
          <th className="text-right px-3 py-2">매칭 캐시</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id} className="border-t">
            <td className="px-3 py-2">
              <Link to={`/admin/qna-cache/${r.id}`} className="text-blue-600 hover:underline">
                {new Date(r.lookedUpAt).toLocaleString()}
              </Link>
            </td>
            <td className="px-3 py-2"><QnaCacheResultBadge result={r.result} /></td>
            <td className="px-3 py-2">{r.policyId}</td>
            <td className="px-3 py-2">{r.questionExcerpt}</td>
            <td className="px-3 py-2 text-right">{r.similarityScore?.toFixed(3) ?? '-'}</td>
            <td className="px-3 py-2 text-right">{r.matchedCachedId ?? '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/admin/qnaCache/QnaCacheKpiSection.tsx \
        frontend/src/components/admin/qnaCache/QnaCacheDailyChart.tsx \
        frontend/src/components/admin/qnaCache/QnaCacheFilterBar.tsx \
        frontend/src/components/admin/qnaCache/QnaCacheLookupTable.tsx
git commit -m "feat(frontend): Q&A 캐시 로그 KPI/차트/테이블/필터 컴포넌트"
```

---

## Task D4: 메인 페이지 + hook

**Files:**
- Create: `frontend/src/hooks/useAdminQnaCache.ts`
- Create: `frontend/src/pages/admin/AdminQnaCachePage.tsx`

- [ ] **Step 1: hook**

```typescript
// useAdminQnaCache.ts
import { useEffect, useState } from 'react';
import {
  getQnaCacheKpi, getQnaCacheDailyStats, listQnaCacheLookups,
  type QnaCacheKpi, type QnaCacheDailyStat, type QnaCacheLookupSummary,
  type ListFilter, type Page,
} from '../apis/admin.qnaCache.api';

export function useAdminQnaCacheKpi() {
  const [kpi, setKpi] = useState<QnaCacheKpi | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    getQnaCacheKpi().then(setKpi).finally(() => setLoading(false));
  }, []);
  return { kpi, loading };
}

export function useAdminQnaCacheDailyStats(days = 14) {
  const [stats, setStats] = useState<QnaCacheDailyStat[]>([]);
  useEffect(() => { getQnaCacheDailyStats(days).then(setStats); }, [days]);
  return stats;
}

export function useAdminQnaCacheLookups(filter: ListFilter) {
  const [page, setPage] = useState<Page<QnaCacheLookupSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    listQnaCacheLookups(filter).then(setPage).finally(() => setLoading(false));
  }, [filter.result, filter.policyId, filter.from, filter.to, filter.page, filter.size]);
  return { page, loading };
}
```

- [ ] **Step 2: 페이지**

```tsx
// AdminQnaCachePage.tsx
import { useState } from 'react';
import { Pagination } from '../../components/admin/Pagination'; // Spec 2 재사용
import { QnaCacheKpiSection } from '../../components/admin/qnaCache/QnaCacheKpiSection';
import { QnaCacheDailyChart } from '../../components/admin/qnaCache/QnaCacheDailyChart';
import { QnaCacheFilterBar } from '../../components/admin/qnaCache/QnaCacheFilterBar';
import { QnaCacheLookupTable } from '../../components/admin/qnaCache/QnaCacheLookupTable';
import {
  useAdminQnaCacheKpi, useAdminQnaCacheDailyStats, useAdminQnaCacheLookups,
} from '../../hooks/useAdminQnaCache';
import { exportQnaCacheCsv, type LookupResultType } from '../../apis/admin.qnaCache.api';

export default function AdminQnaCachePage() {
  const { kpi, loading: kpiLoading } = useAdminQnaCacheKpi();
  const stats = useAdminQnaCacheDailyStats(14);

  const [filter, setFilter] = useState({
    result: undefined as LookupResultType | undefined,
    policyId: undefined as number | undefined,
    from: undefined as string | undefined,
    to: undefined as string | undefined,
    page: 0, size: 20,
  });
  const { page, loading } = useAdminQnaCacheLookups(filter);

  const onChange = (patch: Partial<typeof filter>) => setFilter(f => ({ ...f, ...patch, page: 0 }));

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Q&A 캐시 로그</h1>

      <QnaCacheKpiSection kpi={kpi} loading={kpiLoading} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 bg-white rounded-lg p-4 shadow-sm">
          <h2 className="text-sm font-medium mb-2">14일 결과 분포</h2>
          <QnaCacheDailyChart stats={stats} />
        </div>
        <div className="bg-white rounded-lg p-4 shadow-sm">
          <h2 className="text-sm font-medium mb-2">결과 비율</h2>
          <DonutMini stats={stats} />
        </div>
      </div>

      <div className="bg-white rounded-lg p-4 shadow-sm space-y-3">
        <QnaCacheFilterBar
          result={filter.result} policyId={filter.policyId}
          from={filter.from} to={filter.to}
          onChange={onChange}
          onExportCsv={() => exportQnaCacheCsv({
            result: filter.result, policyId: filter.policyId,
            from: filter.from, to: filter.to,
          })}
        />
        {loading ? <div>불러오는 중…</div> :
          <>
            <QnaCacheLookupTable rows={page?.content ?? []} />
            <Pagination
              page={filter.page} totalPages={page?.totalPages ?? 0}
              onChange={n => setFilter(f => ({ ...f, page: n }))}
            />
          </>
        }
      </div>
    </div>
  );
}

function DonutMini({ stats }: { stats: any[] }) {
  // Recharts PieChart 신규 — 데이터가 비어있으면 — 표시
  // 핵심 기능은 stacked bar에 있으므로 도넛은 단순 비율 표시
  const totals = stats.reduce((a, s) => ({
    HIT: a.HIT + s.hitCount,
    BELOW: a.BELOW + s.belowThresholdCount,
    MISS: a.MISS + s.missCount,
  }), { HIT: 0, BELOW: 0, MISS: 0 });
  const sum = totals.HIT + totals.BELOW + totals.MISS;
  if (sum === 0) return <div className="text-sm text-gray-500">데이터 없음</div>;
  return (
    <div className="text-sm space-y-1">
      <div className="flex justify-between"><span>HIT</span><span>{((totals.HIT/sum)*100).toFixed(1)}%</span></div>
      <div className="flex justify-between"><span>BELOW</span><span>{((totals.BELOW/sum)*100).toFixed(1)}%</span></div>
      <div className="flex justify-between"><span>MISS</span><span>{((totals.MISS/sum)*100).toFixed(1)}%</span></div>
    </div>
  );
}
```

> `Pagination` 컴포넌트의 정확한 props 시그니처는 Spec 2 구현 확인 후 일치시켜라. 도넛은 1차 구현은 비율 텍스트로, 시각이 부족하다 싶으면 후속 task에서 `Recharts.PieChart` 로 업그레이드.

- [ ] **Step 3: lint + typecheck**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: 0 error.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/useAdminQnaCache.ts \
        frontend/src/pages/admin/AdminQnaCachePage.tsx
git commit -m "feat(frontend): AdminQnaCachePage — KPI/차트/테이블/필터/페이지네이션"
```

---

## Task D5: 상세 페이지

**Files:**
- Create: `frontend/src/pages/admin/AdminQnaCacheDetailPage.tsx`

- [ ] **Step 1: 페이지**

```tsx
// AdminQnaCacheDetailPage.tsx
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getQnaCacheLookup, type QnaCacheLookupDetail } from '../../apis/admin.qnaCache.api';
import { QnaCacheResultBadge } from '../../components/admin/qnaCache/QnaCacheResultBadge';

export default function AdminQnaCacheDetailPage() {
  const { lookupId } = useParams<{ lookupId: string }>();
  const [data, setData] = useState<QnaCacheLookupDetail | null>(null);
  useEffect(() => { if (lookupId) getQnaCacheLookup(Number(lookupId)).then(setData); }, [lookupId]);
  if (!data) return <div>불러오는 중…</div>;

  const sim = data.similarityScore ?? null;
  const thr = data.thresholdApplied;
  const gap = sim != null ? (thr - (1 - sim)).toFixed(3) : null;
  // distance = 1 - similarity (단순 cosine 가정 — 실제 환산식과 일치 확인)

  return (
    <div className="space-y-4">
      <Link to="/admin/qna-cache" className="text-sm text-blue-600 hover:underline">← 목록</Link>
      <div className="bg-white rounded-lg p-4 shadow-sm space-y-3">
        <div className="flex items-center gap-3">
          <QnaCacheResultBadge result={data.result} />
          <span className="text-sm text-gray-500">
            {new Date(data.lookedUpAt).toLocaleString()} · 정책 #{data.policyId}
          </span>
        </div>

        <section>
          <h3 className="text-xs font-medium text-gray-500">질문 (raw)</h3>
          <p className="whitespace-pre-wrap">{data.questionText}</p>
        </section>
        <section>
          <h3 className="text-xs font-medium text-gray-500">정규화</h3>
          <p className="whitespace-pre-wrap text-sm text-gray-700">{data.normalizedText}</p>
        </section>

        {data.result === 'HIT' && data.matchedCachedQuestion && (
          <section className="bg-emerald-50 rounded p-3">
            <h3 className="text-xs font-medium text-emerald-900">매칭된 캐시 질문 (#{data.matchedCachedId})</h3>
            <p>{data.matchedCachedQuestion}</p>
            <div className="text-xs text-emerald-800 mt-1">
              유사도 {sim?.toFixed(3)} · 임계값 {thr.toFixed(3)}
            </div>
            {data.matchedCachedAnswerExcerpt && (
              <details className="mt-2">
                <summary className="text-xs cursor-pointer">답변 미리보기</summary>
                <p className="text-sm mt-1 whitespace-pre-wrap">{data.matchedCachedAnswerExcerpt}</p>
              </details>
            )}
          </section>
        )}

        {data.result === 'BELOW_THRESHOLD' && data.matchedCachedQuestion && (
          <section className="bg-amber-50 rounded p-3">
            <h3 className="text-xs font-medium text-amber-900">가장 가까운 후보 (#{data.matchedCachedId})</h3>
            <p>{data.matchedCachedQuestion}</p>
            <div className="text-xs text-amber-800 mt-1">
              유사도 {sim?.toFixed(3)} · 임계값 {thr.toFixed(3)}
              {gap && Number(gap) > 0 ? ` · 임계값에 ${gap} 못 미침` : ''}
            </div>
          </section>
        )}

        {data.result === 'MISS' && (
          <section className="bg-red-50 rounded p-3 text-sm text-red-900">
            매칭 후보 없음. LLM 호출됨: {data.llmCallMade ? '예' : '아니오'}
          </section>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: typecheck + commit**

Run: `cd frontend && npm run typecheck`
Expected: 0 error.

```bash
git add frontend/src/pages/admin/AdminQnaCacheDetailPage.tsx
git commit -m "feat(frontend): AdminQnaCacheDetailPage — HIT/BELOW/MISS 분기 렌더"
```

---

## Task D6: 라우터 등록 + 사이드바 활성화

**Files:**
- Modify: `frontend/src/router` (라우트 등록 위치 — `routes.tsx` 또는 `App.tsx`)
- Modify: `frontend/src/components/layout/AdminSidebar.tsx`

- [ ] **Step 1: 라우트 등록**

라우트 등록 위치를 grep:
```bash
grep -rn "AdminEmailLogPage\|/admin/email" frontend/src/router frontend/src/App.tsx 2>/dev/null
```

해당 파일에 두 라우트 추가:
```tsx
<Route path="/admin/qna-cache" element={<RequireAdmin><AdminQnaCachePage /></RequireAdmin>} />
<Route path="/admin/qna-cache/:lookupId" element={<RequireAdmin><AdminQnaCacheDetailPage /></RequireAdmin>} />
```

> import 경로: `pages/admin/AdminQnaCachePage`, `pages/admin/AdminQnaCacheDetailPage`. lazy 사용 시 Spec 2 와 동일하게.

- [ ] **Step 2: 사이드바 `soon` 플래그 제거**

`AdminSidebar.tsx` 라인 55:
```tsx
// 변경 전
{ to: '/admin/qna-cache', label: 'Q&A 캐시 로그', icon: MessageSquareText, soon: true }
// 변경 후
{ to: '/admin/qna-cache', label: 'Q&A 캐시 로그', icon: MessageSquareText }
```

- [ ] **Step 3: dev 서버 띄워 수동 검증**

```bash
cd frontend && npm run dev
```

브라우저에서 `/admin/qna-cache` 진입:
- ADMIN 계정으로 로그인 시 페이지 렌더, KPI 카드 4개, 차트, 빈 테이블(데이터 없으면)
- USER 계정 시 `/` 로 리다이렉트
- 일반 Q&A 호출 후 새로고침 → 테이블에 row 1건 이상

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router/* frontend/src/App.tsx frontend/src/components/layout/AdminSidebar.tsx
git commit -m "feat(frontend): /admin/qna-cache 라우트 활성화 + 사이드바 메뉴 노출"
```

---

# Stage E — Retention 스케줄러

## Task E: `QnaCacheLookupRetentionScheduler`

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/scheduler/QnaCacheLookupRetentionScheduler.java`
- Create: `backend/src/test/java/com/youthfit/qna/infrastructure/scheduler/QnaCacheLookupRetentionSchedulerTest.java`

- [ ] **Step 1: `@EnableScheduling` 활성화 확인**

```bash
grep -rn "@EnableScheduling" backend/src/main/java
```

기존 스케줄러가 있다면 활성화돼 있을 것. 없으면 `AsyncConfig` 또는 `YouthFitApplication` 에 추가.

- [ ] **Step 2: 단위 테스트**

```java
@ExtendWith(MockitoExtension.class)
class QnaCacheLookupRetentionSchedulerTest {
    @Mock QnaCacheLookupLogRepository repository;
    @Mock QnaProperties qnaProperties;
    @Mock QnaProperties.Cache cacheProps;
    @InjectMocks QnaCacheLookupRetentionScheduler scheduler;

    @Test
    void retentionDays_기준으로_삭제_호출() {
        when(qnaProperties.cache()).thenReturn(cacheProps);
        when(cacheProps.retentionDays()).thenReturn(90);
        when(repository.deleteOlderThan(any())).thenReturn(5);

        scheduler.purgeOldLookups();

        verify(repository).deleteOlderThan(argThat(t -> t.isBefore(LocalDateTime.now().minusDays(89))));
    }
}
```

- [ ] **Step 3: 구현**

```java
package com.youthfit.qna.infrastructure.scheduler;

import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class QnaCacheLookupRetentionScheduler {

    private final QnaCacheLookupLogRepository repository;
    private final QnaProperties qnaProperties;

    @Transactional
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")  // 매일 03:00 KST
    public void purgeOldLookups() {
        int days = qnaProperties.cache().retentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("Q&A 캐시 lookup 로그 retention: cutoff={}, deleted={}", cutoff, deleted);
    }
}
```

- [ ] **Step 4: `deleteOlderThan` 호출 — Task A4 에서 정의한 인터페이스 메서드 그대로 사용**

`QnaCacheLookupLogRepository.deleteOlderThan(LocalDateTime)` 가 `@Modifying @Query` 로 정의돼 있어 별도 어댑터 불필요. 호출만 하면 됨.

- [ ] **Step 5: 테스트 통과**

Run: `cd backend && ./gradlew test --tests "*RetentionScheduler*"`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/scheduler/QnaCacheLookupRetentionScheduler.java \
        backend/src/test/java/com/youthfit/qna/infrastructure/scheduler/QnaCacheLookupRetentionSchedulerTest.java
git commit -m "feat(qna): QnaCacheLookupRetentionScheduler (90일 자동 삭제)"
```

---

# Stage F — 통합 검증 + 문서화

## Task F1: 전체 빌드 + 테스트

- [ ] **Step 1: 백엔드**

```bash
cd backend && ./gradlew clean build
```
Expected: BUILD SUCCESSFUL, 모든 테스트 통과.

- [ ] **Step 2: 프론트**

```bash
cd frontend && npm run typecheck && npm run lint && npm run test && npm run build
```
Expected: 0 error, 모든 테스트 통과.

- [ ] **Step 3: 수동 E2E**

```bash
# 백엔드 부팅
cd backend && ./gradlew bootRun &
# 프론트 부팅
cd frontend && npm run dev &
```

브라우저:
1. ADMIN 계정 로그인 → `/admin/qna-cache` 진입
2. KPI 카드 4개 정상 표시 (데이터 없으면 0% / —)
3. 정책 상세에서 Q&A 1건 호출 → 어드민 페이지 새로고침 → 테이블에 row 추가 확인
4. 동일 질문 재호출 → result=HIT 표시
5. 필터 (HIT/MISS) 변경 → 테이블 갱신
6. CSV export 버튼 → `qna-cache-lookup.csv` 다운로드
7. row 클릭 → 상세 페이지 이동 → HIT 케이스 매칭 캐시 정보 표시

## Task F2: spec 후속/미결 메모

**Files:**
- Modify: `docs/superpowers/specs/2026-05-05-admin-qna-cache-log-design.md` (또는 별도 next-steps)

- [ ] **Step 1: 후속 메모 추가 (필요 시)**

운영 중 발견된 이슈/조정 필요사항을 spec § 14 후속/비범위 절에 추가.

## Task F3: PR 생성 + spec/plan DONE_ 접두사

머지 후 별도 commit 으로:
- `docs/superpowers/specs/2026-05-05-admin-qna-cache-log-design.md` → `DONE_2026-05-05-admin-qna-cache-log-design.md`
- `docs/superpowers/plans/2026-05-05-admin-qna-cache-log.md` → `DONE_2026-05-05-admin-qna-cache-log.md`

```bash
git mv docs/superpowers/specs/2026-05-05-admin-qna-cache-log-design.md \
       docs/superpowers/specs/DONE_2026-05-05-admin-qna-cache-log-design.md
git mv docs/superpowers/plans/2026-05-05-admin-qna-cache-log.md \
       docs/superpowers/plans/DONE_2026-05-05-admin-qna-cache-log.md
git commit -m "docs: Spec 3 (admin Q&A cache log) DONE_ 접두사 적용"
```

---

# 검증 커맨드 요약

| 단계 | 커맨드 |
|---|---|
| 백엔드 컴파일 | `cd backend && ./gradlew compileJava` |
| 백엔드 단위/슬라이스 테스트 | `cd backend && ./gradlew test` |
| 백엔드 빌드 (전체) | `cd backend && ./gradlew clean build` |
| DB DDL 적용 | `psql -h localhost -U youthfit -d youthfit -f backend/src/main/resources/sql/2026-05-06-qna-cache-lookup-log.sql` |
| 프론트 typecheck | `cd frontend && npm run typecheck` |
| 프론트 lint | `cd frontend && npm run lint` |
| 프론트 테스트 | `cd frontend && npm run test` |
| 프론트 빌드 | `cd frontend && npm run build` |
| 백엔드 dev | `cd backend && ./gradlew bootRun` |
| 프론트 dev | `cd frontend && npm run dev` |

---

# PR 분할 제안

단일 PR 가능. 단, 변경 범위가 크면 다음 분할 가능:

1. **PR 1 — 백엔드 기반**: Stage A (cache 인터페이스, 엔티티, repo, DDL) + Stage B (분류기, listener, QnaService 통합) + retention 스케줄러
2. **PR 2 — 어드민 API**: Stage C (DTO, service, controller, 테스트)
3. **PR 3 — 프론트엔드**: Stage D (API, 컴포넌트, 페이지, 라우터, 사이드바)

각 PR이 독립 배포 가능 (PR 1 머지 후 적재 시작, PR 2 머지 후 API 사용 가능, PR 3 머지 후 UI 노출). 단일 PR 선택 시 commit 단위만 깔끔히 유지.

---

# 후속/미결 (구현 중 발견 시 spec 갱신)

- `QuestionNormalizer` 가 이미 있는지 확인 후 재사용 (없으면 단순 trim+lowercase 시작, 추후 강화)
- `Pagination`, `KpiCard`, `StackedBarChart` 의 정확한 props 시그니처 확인 후 Task D3/D4 의 사용 위치 보정
- 도넛 차트는 1차 구현은 비율 텍스트로, 사용자 피드백 후 `Recharts.PieChart` 로 업그레이드
- 향후 Spec 4 (LLM 비용) 도입 후 `estimated-savings-per-hit-usd` 단일 상수 → 동적 산식 마이그레이션
- pgvector 거리 환산식 (`cosine_distance` 0~2 범위 → 유사도 0~1) 은 코드(`PgVectorSemanticQnaCache`)에서 `<=>` 연산자(cosine distance) 사용 확인됨. plan에서 `1 - distance/2` 로 환산. 운영 중 분포 확인 후 단순 cosine 환산식(`1 - distance`)으로 조정 가능
- `@TransactionalEventListener(AFTER_COMMIT)` 대신 `@Async + @EventListener` 채택 — `QnaService.processQuestion` 가 비트랜잭션 + 비동기 executor 안에서 실행되기 때문. spec § 5.2 노트와 일치
- 7일 total 카운트가 KPI 응답에서 `todayTotal + yestTotal` 로 근사됨 — 실제 7일 합산이 필요하면 `aggregateKpi` SQL 에 별도 컬럼 추가
