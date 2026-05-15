# RAG 하이브리드 검색 (벡터 + Trigram) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 벡터 검색에 pg_trgm 기반 trigram 검색을 RRF 로 결합한 하이브리드 검색 path 를 추가하여, 한국어 고유명사·약어·코드 매칭 정확도를 보완한다.

**Architecture:** 기존 `RagSearchService` 에 `rag.hybrid.enabled` feature flag 분기를 추가한다. true 일 때 vector top-N 과 trigram top-N 을 각각 조회하고 도메인 서비스 `ReciprocalRankFusion` 으로 결합한다. 청크 재생성·재임베딩 없이 인덱스 추가만으로 도입 가능하며, false 일 때 기존 path 그대로 동작 (무영향).

**Tech Stack:** Spring Boot 4.x, Java 21, PostgreSQL 17 + pgvector + pg_trgm, JUnit 5, Mockito, Testcontainers, AssertJ

**Spec:** [`docs/superpowers/specs/2026-05-15-rag-hybrid-search-design.md`](../specs/2026-05-15-rag-hybrid-search-design.md)

---

## 파일 구조

### 신규
- `backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql` — pg_trgm 확장 + GIN 인덱스 마이그레이션
- `backend/src/main/java/com/youthfit/rag/infrastructure/config/HybridSearchProperties.java` — `rag.hybrid` 설정 record
- `backend/src/main/java/com/youthfit/rag/domain/service/ReciprocalRankFusion.java` — RRF 점수 계산 도메인 서비스
- `backend/src/test/java/com/youthfit/rag/domain/service/ReciprocalRankFusionTest.java` — RRF 단위 테스트
- `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryTrigramTest.java` — trigram 쿼리 슬라이스 테스트

### 수정
- `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java` — `findTopByTrigram` 인터페이스 메서드 추가
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java` — 위임 메서드 추가
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java` — trigram native query 추가
- `backend/src/main/java/com/youthfit/rag/infrastructure/config/RagConfig.java` — `HybridSearchProperties` + `ReciprocalRankFusion` 빈 등록
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` — hybrid path 분기 추가
- `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java` — hybrid 시나리오 테스트 추가
- `backend/src/main/resources/application.yml` — `rag.hybrid.*` 설정 추가

---

## Task 1: DB 마이그레이션 SQL 작성

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql`

- [ ] **Step 1: SQL 파일 작성**

```sql
-- Postgres 17 contrib 기본 탑재. 멱등 보장.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- content 컬럼 trigram GIN 인덱스. similarity() 함수 호출에 자동 활용됨.
CREATE INDEX IF NOT EXISTS policy_document_content_trgm_idx
    ON policy_document USING GIN (content gin_trgm_ops);
```

- [ ] **Step 2: 파일 경로·내용 검증**

Run: `cat backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql`
Expected: 위 SQL 내용 그대로 출력

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql
git commit -m "feat(rag): policy_document content trigram GIN 인덱스 마이그레이션"
```

---

## Task 2: HybridSearchProperties record 작성

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/infrastructure/config/HybridSearchProperties.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/config/RagConfig.java` (line 14: @EnableConfigurationProperties 배열에 추가)
- Modify: `backend/src/main/resources/application.yml` (rag.hybrid 설정 블록 추가)

- [ ] **Step 1: HybridSearchProperties record 작성**

```java
package com.youthfit.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.hybrid")
public record HybridSearchProperties(
        boolean enabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold
) {
    public HybridSearchProperties {
        if (topNPerSearch <= 0) topNPerSearch = 20;
        if (rrfK <= 0) rrfK = 60;
        if (trigramThreshold <= 0) trigramThreshold = 0.1;
    }
}
```

- [ ] **Step 2: RagConfig 에 등록**

기존 `RagConfig.java:14` 라인:
```java
@EnableConfigurationProperties({OpenAiEmbeddingProperties.class, KeywordBoostProperties.class})
```
다음으로 변경:
```java
@EnableConfigurationProperties({
        OpenAiEmbeddingProperties.class,
        KeywordBoostProperties.class,
        HybridSearchProperties.class
})
```

- [ ] **Step 3: application.yml 에 설정 추가**

기존 파일의 `openai:` 블록 직전 (line 50 부근) 또는 적절한 위치에 다음 블록 추가:

```yaml
rag:
  hybrid:
    enabled: ${RAG_HYBRID_ENABLED:false}
    top-n-per-search: ${RAG_HYBRID_TOP_N:20}
    rrf-k: ${RAG_HYBRID_RRF_K:60}
    trigram-threshold: ${RAG_HYBRID_TRIGRAM_THRESHOLD:0.1}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Properties 바인딩 동작 검증**

Run: `cd backend && ./gradlew test --tests "com.youthfit.YouthfitApplicationTests"`
Expected: PASS (애플리케이션 컨텍스트 로딩 시 properties 바인딩 성공)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/infrastructure/config/HybridSearchProperties.java \
        backend/src/main/java/com/youthfit/rag/infrastructure/config/RagConfig.java \
        backend/src/main/resources/application.yml
git commit -m "feat(rag): HybridSearchProperties + application.yml rag.hybrid 설정 추가"
```

---

## Task 3: ReciprocalRankFusion 도메인 서비스 (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/domain/service/ReciprocalRankFusion.java`
- Create: `backend/src/test/java/com/youthfit/rag/domain/service/ReciprocalRankFusionTest.java`

- [ ] **Step 1: 테스트 먼저 작성**

```java
package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.SimilarChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ReciprocalRankFusion")
class ReciprocalRankFusionTest {

    private final ReciprocalRankFusion rrf = new ReciprocalRankFusion();

    private SimilarChunk chunk(Long id, double distance) {
        return new SimilarChunk(
                id, 1L, id.intValue(), "content-" + id,
                null, null, null, distance
        );
    }

    @Test
    @DisplayName("양쪽 모두에 같은 청크가 있으면 두 점수가 합산되어 더 높은 순위가 된다")
    void mergesSameChunkScores() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.2), chunk(2L, 0.3));
        List<SimilarChunk> tri = List.of(chunk(2L, 0.0), chunk(1L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        // 청크 1: 1/(60+0) + 1/(60+1) = 0.03333 + 0.01639 = 0.04972
        // 청크 2: 1/(60+1) + 1/(60+0) = 0.01639 + 0.03333 = 0.04972
        // 동률이지만 두 청크 모두 양쪽 매칭이라 1, 2 순서대로 (입력 안정 정렬)
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SimilarChunk::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("한쪽에만 있는 청크는 한쪽 점수만 받는다")
    void singleSideChunk() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.2));
        List<SimilarChunk> tri = List.of(chunk(2L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        // 두 청크 모두 1/(60+0) = 0.01667 동률
        assertThat(result).extracting(SimilarChunk::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("양쪽 매칭 청크가 단일 매칭보다 상위에 정렬된다")
    void bothSidesRanksHigher() {
        // 청크 1: vec rank 0 + tri rank 0 → 1/60 + 1/60 = 0.03333
        // 청크 2: vec rank 0 만 → 1/60 = 0.01667
        // 청크 3: tri rank 0 만 → 1/60 = 0.01667
        List<SimilarChunk> vec = List.of(chunk(1L, 0.2), chunk(2L, 0.3));
        List<SimilarChunk> tri = List.of(chunk(1L, 0.0), chunk(3L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("topK 컷으로 결과 수가 제한된다")
    void topKLimits() {
        List<SimilarChunk> vec = List.of(
                chunk(1L, 0.1), chunk(2L, 0.2), chunk(3L, 0.3), chunk(4L, 0.4), chunk(5L, 0.5)
        );
        List<SimilarChunk> tri = List.of();

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("양쪽 모두 비어 있으면 빈 결과를 반환한다")
    void bothEmpty() {
        List<SimilarChunk> result = rrf.merge(List.of(), List.of(), 60, 10);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("양쪽 매칭 시 vector 의 distance 가 결과 distance 로 유지된다")
    void preservesVectorDistance() {
        List<SimilarChunk> vec = List.of(chunk(1L, 0.25));
        // trigram 결과는 distance 0.0 (DB 에서 의미 없음)
        List<SimilarChunk> tri = List.of(chunk(1L, 0.0));

        List<SimilarChunk> result = rrf.merge(vec, tri, 60, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distance()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("trigram 단독 청크는 distance 가 1.0-similarity 로 채워진다")
    void trigramOnlyChunkUsesConvertedDistance() {
        // trigram 쿼리 결과의 distance 필드에는 similarity(0~1) 가 그대로 들어왔다고 가정
        // (Repository 가 그렇게 채움)
        SimilarChunk trigramOnly = new SimilarChunk(
                1L, 1L, 0, "c", null, null, null, 0.8
        );
        List<SimilarChunk> result = rrf.merge(List.of(), List.of(trigramOnly), 60, 10);

        assertThat(result).hasSize(1);
        // 1.0 - 0.8 = 0.2 (부동소수 오차 허용)
        assertThat(result.get(0).distance()).isCloseTo(0.2, within(1e-9));
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "ReciprocalRankFusionTest" -i`
Expected: FAIL — `ReciprocalRankFusion` 클래스 없음

- [ ] **Step 3: ReciprocalRankFusion 구현**

> 도메인 레이어이므로 `application` DTO 가 아닌 `domain.model.SimilarChunk` 를 사용한다 (의존 방향 Presentation → Application → Domain 준수). `RagSearchService` 에서 결과를 `PolicyDocumentChunkResult` 로 변환한다.

```java
package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.SimilarChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion 으로 두 ranked list 를 결합한다.
 *
 * <p>점수 공식: {@code RRF_score(chunk) = Σ 1 / (k + rank_in_each_search)}
 *
 * <p>distance 처리 규칙 (spec §6.4):
 * <ul>
 *   <li>양쪽 모두 매칭: vector 의 distance 유지 (의미적으로 안정)</li>
 *   <li>vector 단독: vector 의 distance 그대로</li>
 *   <li>trigram 단독: {@code 1.0 - similarity} 로 변환 (입력 list 의 distance 필드에 similarity 가 들어있다고 가정)</li>
 * </ul>
 */
public class ReciprocalRankFusion {

    public List<SimilarChunk> merge(
            List<SimilarChunk> vectorRanks,
            List<SimilarChunk> trigramRanks,
            int k,
            int topK
    ) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, SimilarChunk> chunkById = new LinkedHashMap<>();
        Map<Long, Boolean> inVector = new LinkedHashMap<>();

        for (int rank = 0; rank < vectorRanks.size(); rank++) {
            SimilarChunk c = vectorRanks.get(rank);
            scores.merge(c.id(), 1.0 / (k + rank), Double::sum);
            chunkById.putIfAbsent(c.id(), c);
            inVector.put(c.id(), true);
        }
        for (int rank = 0; rank < trigramRanks.size(); rank++) {
            SimilarChunk c = trigramRanks.get(rank);
            scores.merge(c.id(), 1.0 / (k + rank), Double::sum);
            chunkById.putIfAbsent(c.id(), c);
            inVector.putIfAbsent(c.id(), false);
        }

        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        List<SimilarChunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            Long id = sorted.get(i).getKey();
            SimilarChunk original = chunkById.get(id);
            double distance = inVector.get(id)
                    ? original.distance()
                    : 1.0 - original.distance();
            result.add(new SimilarChunk(
                    original.id(),
                    original.policyId(),
                    original.chunkIndex(),
                    original.content(),
                    original.attachmentId(),
                    original.pageStart(),
                    original.pageEnd(),
                    distance
            ));
        }
        return result;
    }
}
```

- [ ] **Step 4: 테스트 실행 → 성공 확인**

Run: `cd backend && ./gradlew test --tests "ReciprocalRankFusionTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/ReciprocalRankFusion.java \
        backend/src/test/java/com/youthfit/rag/domain/service/ReciprocalRankFusionTest.java
git commit -m "feat(rag): ReciprocalRankFusion 도메인 서비스 + 단위 테스트"
```

---

## Task 4: Trigram 쿼리 — Repository 인터페이스·구현·슬라이스 테스트 (TDD)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java`
- Create: `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryTrigramTest.java`

- [ ] **Step 1: 슬라이스 테스트 먼저 작성**

```java
package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PolicyDocumentRepositoryImpl.class})
@Testcontainers
@DisplayName("PolicyDocumentRepository trigram 검색")
class PolicyDocumentRepositoryTrigramTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void initExtensions(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS pg_trgm;");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PolicyDocumentJpaRepository jpaRepository;

    @Autowired
    private PolicyDocumentRepository repository;

    private Long policyId;

    @BeforeEach
    void setUp() {
        policyId = 9101L;
        jpaRepository.deleteByPolicyId(policyId);
        jpaRepository.saveAll(List.of(
                document(0, "희망저축계좌Ⅰ 가입 요건은 기준 중위소득 40% 이하입니다"),
                document(1, "내일키움수익금 자활근로사업단 12일 이상 실근무"),
                document(2, "청년내일저축계좌 2026년부터 차상위 초과자 신규모집 중단"),
                document(3, "이 청크는 전혀 무관한 내용입니다 lorem ipsum")
        ));
    }

    @Test
    @DisplayName("정확 토큰 매칭 청크가 가장 높은 similarity 로 반환된다")
    void exactTokenMatch() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.1, 10
        );

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).content()).contains("희망저축계좌Ⅰ");
    }

    @Test
    @DisplayName("threshold 미만 청크는 결과에서 제외된다")
    void thresholdFilters() {
        List<SimilarChunk> highThreshold = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.9, 10
        );
        List<SimilarChunk> lowThreshold = repository.findTopByTrigram(
                policyId, "희망저축계좌", 0.05, 10
        );

        assertThat(lowThreshold.size()).isGreaterThanOrEqualTo(highThreshold.size());
    }

    @Test
    @DisplayName("limit 으로 결과 수가 제한된다")
    void limitWorks() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                policyId, "청크", 0.05, 2
        );

        assertThat(result).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("다른 policyId 청크는 검색되지 않는다")
    void scopedByPolicyId() {
        List<SimilarChunk> result = repository.findTopByTrigram(
                9999L, "희망저축계좌", 0.05, 10
        );

        assertThat(result).isEmpty();
    }

    private PolicyDocument document(int chunkIndex, String content) {
        return PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash")
                .source(PolicyDocumentSource.BODY)
                .build();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "PolicyDocumentRepositoryTrigramTest" -i`
Expected: FAIL — `findTopByTrigram` 메서드 없음

- [ ] **Step 3: 도메인 인터페이스에 메서드 추가**

`PolicyDocumentRepository.java` 마지막 메서드 뒤에 추가:

```java
List<SimilarChunk> findTopByTrigram(
        Long policyId,
        String query,
        double threshold,
        int limit
);
```

- [ ] **Step 4: JpaRepository 에 native 쿼리 추가**

> `similarity()` 함수는 GIN trigram 인덱스 가속 대상이 아니다. KNN 형태의 `<->` distance operator + `LIMIT` 으로 인덱스를 활용한다. threshold 컷은 Java 레이어 (`PolicyDocumentRepositoryImpl`) 에서 수행.

`PolicyDocumentJpaRepository.java` 의 `findSimilarByEmbedding` 메서드 다음에 추가:

```java
@Query(value = """
        SELECT id,
               policy_id     AS policyId,
               chunk_index   AS chunkIndex,
               content,
               attachment_id AS attachmentId,
               page_start    AS pageStart,
               page_end      AS pageEnd,
               similarity(content, :query) AS sim
          FROM policy_document
         WHERE policy_id = :policyId
         ORDER BY content <-> :query
         LIMIT :limit
        """, nativeQuery = true)
List<Object[]> findTopByTrigram(
        @Param("policyId") Long policyId,
        @Param("query") String query,
        @Param("limit") int limit
);
```

- [ ] **Step 5: Impl 에 위임 메서드 추가**

`PolicyDocumentRepositoryImpl.java` 의 `findSimilarByEmbedding` 메서드 다음에 추가:

```java
@Override
public List<SimilarChunk> findTopByTrigram(Long policyId, String query, double threshold, int limit) {
    return jpaRepository.findTopByTrigram(policyId, query, limit).stream()
            .map(this::toTrigramChunk)
            .filter(c -> c.distance() >= threshold)
            .toList();
}

private SimilarChunk toTrigramChunk(Object[] row) {
    // sim (0~1) 을 distance 필드에 그대로 담아 반환한다.
    // 후속 ReciprocalRankFusion 가 trigram-only 청크의 distance 를 1.0-sim 으로 변환한다.
    double sim = ((Number) row[7]).doubleValue();
    return new SimilarChunk(
            ((Number) row[0]).longValue(),
            ((Number) row[1]).longValue(),
            ((Number) row[2]).intValue(),
            (String) row[3],
            row[4] == null ? null : ((Number) row[4]).longValue(),
            row[5] == null ? null : ((Number) row[5]).intValue(),
            row[6] == null ? null : ((Number) row[6]).intValue(),
            sim
    );
}
```

- [ ] **Step 6: 테스트 실행 → 성공 확인**

Run: `cd backend && ./gradlew test --tests "PolicyDocumentRepositoryTrigramTest"`
Expected: PASS (4 tests)

- [ ] **Step 7: 기존 repository 테스트 회귀 확인**

Run: `cd backend && ./gradlew test --tests "PolicyDocumentRepository*"`
Expected: 모든 테스트 PASS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java \
        backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java \
        backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java \
        backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryTrigramTest.java
git commit -m "feat(rag): PolicyDocumentRepository.findTopByTrigram + 슬라이스 테스트"
```

---

## Task 5: RagSearchService 에 hybrid path 분기 + ReciprocalRankFusion 빈 등록 (TDD)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/config/RagConfig.java` (ReciprocalRankFusion 빈 추가)
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` (hybrid 분기 추가)
- Modify: `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java` (hybrid 시나리오 테스트 추가)

- [ ] **Step 1: 테스트 먼저 — RagSearchServiceTest 에 hybrid 시나리오 추가**

`RagSearchServiceTest.java` 의 `@Mock` 필드 묶음에 추가:

```java
@Mock
private HybridSearchProperties hybridSearchProperties;

@Mock
private com.youthfit.rag.domain.service.ReciprocalRankFusion reciprocalRankFusion;
```

그리고 import 추가:
```java
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
```

새 `@Nested` 블록 추가 (기존 `@Nested` 들과 같은 레벨):

```java
@Nested
@DisplayName("Hybrid 검색 path")
class HybridPath {

    @Test
    @DisplayName("hybrid.enabled=false 면 기존 vector-only path 동작")
    void hybridDisabled_usesVectorOnly() {
        SearchChunksCommand command = new SearchChunksCommand(1L, "query");
        float[] embedding = new float[]{0.1f, 0.2f};
        given(hybridSearchProperties.enabled()).willReturn(false);
        given(keywordBoostProperties.enabled()).willReturn(false);
        given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(10)))
                .willReturn(List.of(new SimilarChunk(10L, 1L, 0, "c", null, null, null, 0.2)));

        List<PolicyDocumentChunkResult> result =
                ragSearchService.searchRelevantChunks(command, embedding);

        assertThat(result).hasSize(1);
        verify(policyDocumentRepository, never()).findTopByTrigram(any(), any(), anyDouble(), anyInt());
        verify(reciprocalRankFusion, never()).merge(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("hybrid.enabled=true 면 vector + trigram 호출 후 RRF 결합")
    void hybridEnabled_callsRrf() {
        SearchChunksCommand command = new SearchChunksCommand(1L, "희망저축계좌");
        float[] embedding = new float[]{0.1f, 0.2f};
        given(hybridSearchProperties.enabled()).willReturn(true);
        given(hybridSearchProperties.topNPerSearch()).willReturn(20);
        given(hybridSearchProperties.rrfK()).willReturn(60);
        given(hybridSearchProperties.trigramThreshold()).willReturn(0.1);
        given(keywordBoostProperties.enabled()).willReturn(false);

        List<SimilarChunk> vecChunks = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
        List<SimilarChunk> triChunks = List.of(new SimilarChunk(20L, 1L, 1, "t", null, null, null, 0.7));
        given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(20)))
                .willReturn(vecChunks);
        given(policyDocumentRepository.findTopByTrigram(eq(1L), eq("희망저축계좌"), eq(0.1), eq(20)))
                .willReturn(triChunks);

        PolicyDocumentChunkResult merged = new PolicyDocumentChunkResult(
                10L, 1L, 0, "v", 0.2, null, null, null);
        given(reciprocalRankFusion.merge(any(), any(), eq(60), eq(10)))
                .willReturn(List.of(merged));

        List<PolicyDocumentChunkResult> result =
                ragSearchService.searchRelevantChunks(command, embedding);

        assertThat(result).hasSize(1);
        verify(reciprocalRankFusion).merge(any(), any(), eq(60), eq(10));
    }

    @Test
    @DisplayName("trigram 쿼리 예외 발생 시 vector 결과로 폴백한다")
    void trigramFailure_fallsBackToVector() {
        SearchChunksCommand command = new SearchChunksCommand(1L, "쿼리");
        float[] embedding = new float[]{0.1f};
        given(hybridSearchProperties.enabled()).willReturn(true);
        given(hybridSearchProperties.topNPerSearch()).willReturn(20);
        given(hybridSearchProperties.rrfK()).willReturn(60);
        given(hybridSearchProperties.trigramThreshold()).willReturn(0.1);
        given(keywordBoostProperties.enabled()).willReturn(false);

        List<SimilarChunk> vecChunks = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
        given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(embedding), any(), eq(20)))
                .willReturn(vecChunks);
        given(policyDocumentRepository.findTopByTrigram(eq(1L), eq("쿼리"), eq(0.1), eq(20)))
                .willThrow(new RuntimeException("trigram down"));

        List<PolicyDocumentChunkResult> result =
                ragSearchService.searchRelevantChunks(command, embedding);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        verify(reciprocalRankFusion, never()).merge(any(), any(), anyInt(), anyInt());
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "RagSearchServiceTest"`
Expected: FAIL — `HybridSearchProperties` / `ReciprocalRankFusion` 필드가 `RagSearchService` 에 없음, 컴파일 에러

- [ ] **Step 3: RagConfig 에 ReciprocalRankFusion 빈 등록**

`RagConfig.java` 의 기존 `keywordExtractor` 빈 메서드 다음에 추가:

```java
import com.youthfit.rag.domain.service.ReciprocalRankFusion;

@Bean
public ReciprocalRankFusion reciprocalRankFusion() {
    return new ReciprocalRankFusion();
}
```

- [ ] **Step 4: RagSearchService 수정 — hybrid path 추가**

`RagSearchService.java` 의 import 에 추가:
```java
import com.youthfit.rag.domain.service.ReciprocalRankFusion;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
```

필드 추가 (`KeywordBoostProperties` 옆):
```java
private final HybridSearchProperties hybridSearchProperties;
private final ReciprocalRankFusion reciprocalRankFusion;
```

기존 `searchRelevantChunks(SearchChunksCommand, float[])` 메서드 본문을 다음과 같이 교체:

```java
@Transactional(readOnly = true)
public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command,
                                                            float[] precomputedEmbedding) {
    if (command.query() == null || command.query().isBlank()) {
        return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                .map(PolicyDocumentChunkResult::from)
                .toList();
    }

    List<String> keywords = keywordBoostProperties.enabled()
            ? keywordExtractor.extract(command.query())
            : List.of();

    if (hybridSearchProperties.enabled()) {
        return hybridSearch(command, precomputedEmbedding, keywords);
    }

    if (log.isInfoEnabled()) {
        log.info("RAG 키워드 boost: policyId={}, enabled={}, keywords={}",
                command.policyId(), keywordBoostProperties.enabled(), keywords);
    }

    List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
            command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);

    if (similar.isEmpty()) {
        log.info("벡터 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
        return fallbackKeywordSearch(command);
    }

    if (log.isInfoEnabled()) {
        String distanceSummary = similar.stream()
                .map(c -> String.format("%.3f", c.distance()))
                .toList()
                .toString();
        log.info("RAG 검색 결과: policyId={}, top{}={}",
                command.policyId(), similar.size(), distanceSummary);
    }

    return similar.stream()
            .map(PolicyDocumentChunkResult::from)
            .toList();
}

private List<PolicyDocumentChunkResult> hybridSearch(
        SearchChunksCommand command, float[] embedding, List<String> keywords
) {
    int topN = hybridSearchProperties.topNPerSearch();
    int k = hybridSearchProperties.rrfK();
    double threshold = hybridSearchProperties.trigramThreshold();

    List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
            command.policyId(), embedding, keywords, topN);

    List<SimilarChunk> tri;
    try {
        tri = policyDocumentRepository.findTopByTrigram(
                command.policyId(), command.query(), threshold, topN);
    } catch (RuntimeException e) {
        log.warn("trigram 쿼리 실패, vector 결과로 폴백: policyId={}, error={}",
                command.policyId(), e.toString());
        tri = List.of();
    }

    if (vec.isEmpty() && tri.isEmpty()) {
        log.info("hybrid 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
        return fallbackKeywordSearch(command);
    }

    if (tri.isEmpty()) {
        return vec.stream().map(PolicyDocumentChunkResult::from).toList();
    }

    List<PolicyDocumentChunkResult> vecResults = vec.stream()
            .map(PolicyDocumentChunkResult::from).toList();
    List<PolicyDocumentChunkResult> triResults = tri.stream()
            .map(PolicyDocumentChunkResult::from).toList();

    List<PolicyDocumentChunkResult> merged =
            reciprocalRankFusion.merge(vecResults, triResults, k, DEFAULT_TOP_K);

    if (log.isInfoEnabled()) {
        log.info("hybrid 검색: policyId={}, vector_top{}={}, trigram_top{}={}, merged_top{}={}",
                command.policyId(),
                vec.size(), summarize(vec, c -> String.format("%.3f", c.distance())),
                tri.size(), summarize(tri, c -> String.format("%.3f", c.distance())),
                merged.size(), summarize(merged, c -> String.format("%.3f", c.distance())));
    }

    return merged;
}

private <T> String summarize(List<T> items, java.util.function.Function<T, String> fmt) {
    return items.stream().map(fmt).toList().toString();
}
```

- [ ] **Step 5: 테스트 실행 → 성공 확인**

Run: `cd backend && ./gradlew test --tests "RagSearchServiceTest"`
Expected: 모든 테스트 PASS (기존 + 신규 3건)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/infrastructure/config/RagConfig.java \
        backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java \
        backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java
git commit -m "feat(rag): RagSearchService 에 hybrid path 분기 + trigram 폴백 처리"
```

---

## Task 6: 전체 빌드·테스트 검증

- [ ] **Step 1: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 2: 전체 빌드**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 회귀 확인**

Run: `cd backend && ./gradlew test --tests "RagSearchServiceTest" --tests "RagIndexingServiceTest" --tests "PolicyDocumentRepository*" --tests "QnaServiceTest"`
Expected: 모든 테스트 PASS

- [ ] **Step 4: spec 의 마이그레이션 파일명 placeholder 업데이트**

`docs/superpowers/specs/2026-05-15-rag-hybrid-search-design.md` 의 섹션 5.1 마이그레이션 파일 경로를 실제 파일명 (`2026-05-16-policy-document-trigram-index.sql`) 으로 교체.

```bash
# 수동 편집 또는 다음 sed 적용
```

기존:
```
파일: `backend/src/main/resources/sql/V<다음버전>__add_policy_document_trigram_index.sql` — 마이그레이션 버전 번호는 implementation plan 단계에서 현재 `db/migration` 최대 버전 + 1 로 확정
```
변경:
```
파일: `backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql` — 운영 적용은 spec §12 의 배포 순서 1단계 (PR 머지 직후) 에 수동 실행
```

- [ ] **Step 5: spec 업데이트 commit (변경이 있으면)**

```bash
git add docs/superpowers/specs/2026-05-15-rag-hybrid-search-design.md
git commit -m "docs(spec): hybrid 검색 spec 의 마이그레이션 파일명 확정 (2026-05-16-policy-document-trigram-index.sql)"
```

(변경 없으면 이 단계는 skip)

---

## 운영 출시 가이드 (구현 외)

이 plan 의 모든 task 가 완료되고 PR 머지되어도 **하이브리드 검색은 기본 OFF** 입니다. 다음 단계로 출시:

1. **운영 DB 에 마이그레이션 SQL 수동 적용**:
   ```bash
   psql $DATABASE_URL -f backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql
   ```
   인덱스 빌드 시간은 청크 수에 비례 (수십만 청크 기준 수 분 이내).

2. **검증 — 스테이징에서 환경변수 ON**:
   ```bash
   RAG_HYBRID_ENABLED=true ./gradlew bootRun
   ```
   주요 질문 10~20개로 retrieval 품질 비교.

3. **운영 출시** — `RAG_HYBRID_ENABLED=true` 로 환경변수 설정 후 컨테이너 재기동.

4. **롤백** — 회귀 발견 시 `RAG_HYBRID_ENABLED=false` 로 즉시 복귀 (코드 배포 불필요).
