# 의미 기반 Q&A 캐시 (Semantic Cache) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Q&A에서 정확 일치 캐시(Redis) 미스 시 의미 기반 캐시(pgvector)를 거쳐, 의미적으로 동일한 질문에 LLM 호출 없이 캐시된 답변을 스트리밍한다.

**Architecture:** ① Redis 정확 캐시 → ② 임베딩 1회 → ③ pgvector 의미 캐시(코사인 거리 ≤ 0.15) → ④ RAG 청크 검색(같은 임베딩 재사용) → ⑤ LLM 스트림. RAG가 어차피 호출하던 임베딩을 의미 캐시 조회와 청크 검색에 모두 재사용해 추가 토큰 비용 0. 정책 본문 인덱싱 갱신 시 `QnaCacheInvalidator` port를 통해 같은 `@Transactional` 안에서 의미 캐시 무효화. 정확 캐시는 v0에선 TTL 24h에만 의존.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA, PostgreSQL 17 + pgvector(1536), JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-05-01-semantic-qna-cache-design.md`

---

## File Structure

### 신규 파일 (10개)
- `backend/src/main/java/com/youthfit/qna/domain/model/QnaQuestionCache.java` — JPA Entity
- `backend/src/main/java/com/youthfit/qna/domain/model/SimilarCachedAnswer.java` — 값 객체 (`CachedAnswer` + distance)
- `backend/src/main/java/com/youthfit/qna/domain/repository/QnaQuestionCacheRepository.java` — 도메인 인터페이스
- `backend/src/main/java/com/youthfit/qna/application/port/SemanticQnaCache.java` — application port
- `backend/src/main/java/com/youthfit/qna/application/port/QnaCacheInvalidator.java` — application port (rag→qna 호출용)
- `backend/src/main/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidator.java` — invalidator 구현
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java` — `SemanticQnaCache` 구현
- `backend/src/main/java/com/youthfit/qna/infrastructure/persistence/QnaQuestionCacheJpaRepository.java`
- `backend/src/main/java/com/youthfit/qna/infrastructure/persistence/QnaQuestionCacheRepositoryImpl.java`
- `backend/src/test/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidatorTest.java`

### 수정 파일 (5개)
- `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaProperties.java` — 필드 추가
- `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` — 흐름 변경
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` — 오버로드 추가
- `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java` — invalidator 주입·호출
- `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`, `backend/src/test/java/com/youthfit/rag/application/service/RagIndexingServiceTest.java` — 새 흐름 검증

### 운영 자료
- `docs/OPS.md` — `qna_question_cache` 테이블 DDL을 운영 PG에 수동 적용 절차 추가

---

## Task 1: `QnaProperties`에 의미 캐시 임계값 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaProperties.java`

- [ ] **Step 1: 현재 파일 확인**

확인 사항: `cacheTtlHours`, `relevanceDistanceThreshold` 두 필드를 가진 record. 기본값을 컴팩트 생성자에서 보정.

- [ ] **Step 2: 필드 추가 (최종 코드)**

```java
package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youthfit.qna")
public record QnaProperties(
        long cacheTtlHours,
        double relevanceDistanceThreshold,
        double semanticDistanceThreshold
) {

    public QnaProperties {
        if (cacheTtlHours <= 0) cacheTtlHours = 24;
        if (relevanceDistanceThreshold <= 0) relevanceDistanceThreshold = 0.4;
        if (semanticDistanceThreshold <= 0) semanticDistanceThreshold = 0.15;
    }
}
```

- [ ] **Step 3: `application.yml`에서 새 필드 노출 (선택)**

`backend/src/main/resources/application.yml`의 `youthfit.qna` 섹션에 다음을 추가하면 운영 환경 변수로 튜닝 가능. 없어도 기본값 0.15가 적용됨:

```yaml
youthfit:
  qna:
    semantic-distance-threshold: ${QNA_SEMANTIC_DISTANCE_THRESHOLD:0.15}  # 의미 캐시 히트 컷오프. 더 엄격: 0.10, 더 관대: 0.20
```

- [ ] **Step 4: 빌드 검증**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL (현재 `QnaProperties`를 직접 인스턴스화하는 곳은 없으므로 컴파일 영향 없음)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaProperties.java backend/src/main/resources/application.yml
git commit -m "feat(qna): semantic cache distance threshold 설정 추가"
```

---

## Task 2: `QnaQuestionCache` Entity + 도메인 값 객체

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/domain/model/QnaQuestionCache.java`
- Create: `backend/src/main/java/com/youthfit/qna/domain/model/SimilarCachedAnswer.java`

- [ ] **Step 1: 참조 패턴 재확인**

`backend/src/main/java/com/youthfit/rag/domain/model/PolicyDocument.java`에서 pgvector 매핑 패턴(`@JdbcTypeCode(SqlTypes.VECTOR)`, `@Array(length=1536)`, `@Column(columnDefinition="vector(1536)")`) 확인.

- [ ] **Step 2: `SimilarCachedAnswer` 값 객체 작성 (도메인-순수 record)**

```java
package com.youthfit.qna.domain.model;

public record SimilarCachedAnswer(
        Long id,
        String answer,
        String sourcesJson,
        double distance
) {
}
```

도메인 리포지토리가 이 record를 반환하고, application 어댑터(`PgVectorSemanticQnaCache`)에서 `CachedAnswer`로 변환한다. domain → application 의존이 생기지 않도록 도메인-순수 형태로만 둔다.

- [ ] **Step 3: `QnaQuestionCache` Entity 작성 (최종 코드)**

```java
package com.youthfit.qna.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "qna_question_cache",
        indexes = {
                @Index(name = "idx_qna_question_cache_policy", columnList = "policy_id")
        }
)
public class QnaQuestionCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sources_json", nullable = false, columnDefinition = "JSONB")
    private String sourcesJson;

    @Builder
    private QnaQuestionCache(Long policyId,
                             String questionText,
                             float[] embedding,
                             String answer,
                             String sourcesJson) {
        this.policyId = policyId;
        this.questionText = questionText;
        this.embedding = embedding;
        this.answer = answer;
        this.sourcesJson = sourcesJson;
    }
}
```

- [ ] **Step 4: 빌드 검증**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/domain/model/
git commit -m "feat(qna): QnaQuestionCache 엔티티와 SimilarCachedAnswer 값 객체 추가"
```

---

## Task 3: 도메인 리포지토리 인터페이스

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/domain/repository/QnaQuestionCacheRepository.java`

- [ ] **Step 1: 인터페이스 작성 (최종 코드)**

```java
package com.youthfit.qna.domain.repository;

import com.youthfit.qna.domain.model.QnaQuestionCache;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;

import java.time.Duration;
import java.util.Optional;

public interface QnaQuestionCacheRepository {

    Optional<SimilarCachedAnswer> findClosestByPolicyId(Long policyId,
                                                        float[] queryEmbedding,
                                                        Duration ttl);

    QnaQuestionCache save(QnaQuestionCache entity);

    void deleteByPolicyId(Long policyId);
}
```

- [ ] **Step 2: 빌드 검증**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/domain/repository/QnaQuestionCacheRepository.java
git commit -m "feat(qna): QnaQuestionCacheRepository 도메인 인터페이스 추가"
```

---

## Task 4: JPA 리포지토리 + 어댑터 (네이티브 쿼리)

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/persistence/QnaQuestionCacheJpaRepository.java`
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/persistence/QnaQuestionCacheRepositoryImpl.java`

- [ ] **Step 1: `JpaRepository` 작성 (최종 코드)**

```java
package com.youthfit.qna.infrastructure.persistence;

import com.youthfit.qna.domain.model.QnaQuestionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QnaQuestionCacheJpaRepository extends JpaRepository<QnaQuestionCache, Long> {

    @Query(value = """
            SELECT id,
                   answer,
                   sources_json AS sourcesJson,
                   (embedding <=> cast(:queryEmbedding AS vector)) AS distance
              FROM qna_question_cache
             WHERE policy_id = :policyId
               AND created_at >= now() - make_interval(hours => :ttlHours)
             ORDER BY distance
             LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findClosestByPolicyId(
            @Param("policyId") Long policyId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("ttlHours") int ttlHours
    );

    @Modifying
    @Query("DELETE FROM QnaQuestionCache c WHERE c.policyId = :policyId")
    void deleteByPolicyId(@Param("policyId") Long policyId);
}
```

- [ ] **Step 2: `RepositoryImpl` 작성 (최종 코드)**

```java
package com.youthfit.qna.infrastructure.persistence;

import com.youthfit.qna.domain.model.QnaQuestionCache;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@Repository
@RequiredArgsConstructor
public class QnaQuestionCacheRepositoryImpl implements QnaQuestionCacheRepository {

    private final QnaQuestionCacheJpaRepository jpaRepository;

    @Override
    public Optional<SimilarCachedAnswer> findClosestByPolicyId(Long policyId,
                                                               float[] queryEmbedding,
                                                               Duration ttl) {
        String vectorString = toVectorString(queryEmbedding);
        int ttlHours = (int) Math.max(1, ttl.toHours());
        List<Object[]> rows = jpaRepository.findClosestByPolicyId(policyId, vectorString, ttlHours);
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.get(0);
        return Optional.of(new SimilarCachedAnswer(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).doubleValue()
        ));
    }

    @Override
    public QnaQuestionCache save(QnaQuestionCache entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public void deleteByPolicyId(Long policyId) {
        jpaRepository.deleteByPolicyId(policyId);
    }

    private String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : embedding) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }
}
```

- [ ] **Step 3: 빌드 검증**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/persistence/
git commit -m "feat(qna): QnaQuestionCache JPA 리포지토리와 코사인 거리 네이티브 쿼리 추가"
```

---

## Task 5: `SemanticQnaCache` port + `PgVectorSemanticQnaCache` 어댑터

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/port/SemanticQnaCache.java`
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java`

- [ ] **Step 1: port 인터페이스 작성 (최종 코드)**

```java
package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.result.CachedAnswer;

import java.util.Optional;

public interface SemanticQnaCache {

    /**
     * 임계값과 TTL 안에서 가장 가까운 캐시 항목을 반환한다. 임계값을 넘으면 Optional.empty().
     */
    Optional<CachedAnswer> findSimilar(Long policyId, float[] queryEmbedding);

    void put(Long policyId, String question, float[] embedding, CachedAnswer answer);
}
```

- [ ] **Step 2: 어댑터 구현 (최종 코드)**

```java
package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.domain.model.QnaQuestionCache;
import com.youthfit.qna.domain.model.SimilarCachedAnswer;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PgVectorSemanticQnaCache implements SemanticQnaCache {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSemanticQnaCache.class);

    private final QnaQuestionCacheRepository repository;
    private final QnaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<CachedAnswer> findSimilar(Long policyId, float[] queryEmbedding) {
        Duration ttl = Duration.ofHours(properties.cacheTtlHours());
        Optional<SimilarCachedAnswer> closest = repository.findClosestByPolicyId(policyId, queryEmbedding, ttl);
        if (closest.isEmpty()) {
            return Optional.empty();
        }
        SimilarCachedAnswer hit = closest.get();
        if (hit.distance() > properties.semanticDistanceThreshold()) {
            log.info("Q&A 의미 캐시 미스: policyId={}, closestDistance={}", policyId, hit.distance());
            return Optional.empty();
        }
        log.info("Q&A 의미 캐시 히트: policyId={}, distance={}", policyId, hit.distance());
        try {
            List<QnaSourceResult> sources = objectMapper.readValue(
                    hit.sourcesJson(), new TypeReference<List<QnaSourceResult>>() {});
            return Optional.of(new CachedAnswer(hit.answer(), sources, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 sources 역직렬화 실패: policyId={}, error={}", policyId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long policyId, String question, float[] embedding, CachedAnswer answer) {
        try {
            String sourcesJson = objectMapper.writeValueAsString(answer.sources());
            QnaQuestionCache entity = QnaQuestionCache.builder()
                    .policyId(policyId)
                    .questionText(question)
                    .embedding(embedding)
                    .answer(answer.answer())
                    .sourcesJson(sourcesJson)
                    .build();
            repository.save(entity);
        } catch (RuntimeException e) {
            log.warn("Q&A 의미 캐시 write 실패 (사용자 응답엔 영향 없음): policyId={}, error={}",
                    policyId, e.toString());
        }
    }
}
```

- [ ] **Step 3: 빌드 검증**

Run: `cd backend && ./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/application/port/SemanticQnaCache.java backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java
git commit -m "feat(qna): SemanticQnaCache port와 pgvector 어댑터 추가"
```

---

## Task 6: `QnaCacheInvalidator` port + `DefaultQnaCacheInvalidator` (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/port/QnaCacheInvalidator.java`
- Create: `backend/src/main/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidator.java`
- Test: `backend/src/test/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidatorTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.qna.application.service;

import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("DefaultQnaCacheInvalidator")
@ExtendWith(MockitoExtension.class)
class DefaultQnaCacheInvalidatorTest {

    @InjectMocks
    private DefaultQnaCacheInvalidator invalidator;

    @Mock
    private QnaQuestionCacheRepository repository;

    @Test
    @DisplayName("invalidatePolicy 는 해당 정책의 의미 캐시를 모두 삭제한다")
    void invalidatePolicy_deletesAll() {
        invalidator.invalidatePolicy(42L);

        verify(repository).deleteByPolicyId(42L);
        verifyNoMoreInteractions(repository);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.DefaultQnaCacheInvalidatorTest" -q`
Expected: FAIL — `DefaultQnaCacheInvalidator` 클래스 없음.

- [ ] **Step 3: port + 구현 작성**

```java
// backend/src/main/java/com/youthfit/qna/application/port/QnaCacheInvalidator.java
package com.youthfit.qna.application.port;

public interface QnaCacheInvalidator {
    void invalidatePolicy(Long policyId);
}
```

```java
// backend/src/main/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidator.java
package com.youthfit.qna.application.service;

import com.youthfit.qna.application.port.QnaCacheInvalidator;
import com.youthfit.qna.domain.repository.QnaQuestionCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultQnaCacheInvalidator implements QnaCacheInvalidator {

    private final QnaQuestionCacheRepository repository;

    @Override
    public void invalidatePolicy(Long policyId) {
        repository.deleteByPolicyId(policyId);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.DefaultQnaCacheInvalidatorTest" -q`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/application/port/QnaCacheInvalidator.java backend/src/main/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidator.java backend/src/test/java/com/youthfit/qna/application/service/DefaultQnaCacheInvalidatorTest.java
git commit -m "feat(qna): QnaCacheInvalidator port와 기본 구현 추가"
```

---

## Task 7: `RagSearchService` 오버로드 추가 (사전 계산 임베딩 재사용)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java`

- [ ] **Step 1: 현재 메서드 시그니처 확인**

`searchRelevantChunks(SearchChunksCommand command)` — 내부에서 `embeddingProvider.embed(command.query())` 호출.

- [ ] **Step 2: 메서드 추출 + 오버로드 추가 (최종 코드)**

```java
package com.youthfit.rag.application.service;

import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);
    private static final int DEFAULT_TOP_K = 5;

    private final PolicyDocumentRepository policyDocumentRepository;
    private final EmbeddingProvider embeddingProvider;

    @Transactional(readOnly = true)
    public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command) {
        if (command.query() == null || command.query().isBlank()) {
            return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                    .map(PolicyDocumentChunkResult::from)
                    .toList();
        }
        float[] queryEmbedding = embeddingProvider.embed(command.query());
        return searchRelevantChunks(command, queryEmbedding);
    }

    @Transactional(readOnly = true)
    public List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command,
                                                                float[] precomputedEmbedding) {
        if (command.query() == null || command.query().isBlank()) {
            return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                    .map(PolicyDocumentChunkResult::from)
                    .toList();
        }

        List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
                command.policyId(), precomputedEmbedding, DEFAULT_TOP_K);

        if (similar.isEmpty()) {
            log.info("벡터 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
            return fallbackKeywordSearch(command);
        }

        if (log.isInfoEnabled()) {
            String distanceSummary = similar.stream()
                    .map(c -> String.format("%.3f", c.distance()))
                    .toList()
                    .toString();
            log.info("RAG 검색 결과: policyId={}, top{}={}", command.policyId(), similar.size(), distanceSummary);
        }

        return similar.stream()
                .map(PolicyDocumentChunkResult::from)
                .toList();
    }

    private List<PolicyDocumentChunkResult> fallbackKeywordSearch(SearchChunksCommand command) {
        String lowerQuery = command.query().toLowerCase();
        return policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId()).stream()
                .filter(chunk -> chunk.getContent().toLowerCase().contains(lowerQuery))
                .map(PolicyDocumentChunkResult::from)
                .toList();
    }
}
```

- [ ] **Step 3: 기존 `RagSearchServiceTest` 통과 확인 (회귀 방지)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagSearchServiceTest" -q`
Expected: PASS (단일 인자 메서드 동작 변화 없음)

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java
git commit -m "feat(rag): RagSearchService 사전 계산 임베딩 재사용 오버로드 추가"
```

---

## Task 8: `QnaService` 흐름 변경 (의미 캐시 단계 삽입) — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`
- Modify: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`

- [ ] **Step 1: 새 테스트 케이스 추가 (실패 테스트)**

`QnaServiceTest`에 다음 필드와 케이스를 추가한다.

새 필드 추가 (기존 `@Mock` 들 옆):

```java
@Mock private com.youthfit.qna.application.port.SemanticQnaCache semanticQnaCache;
@Mock private com.youthfit.rag.application.port.EmbeddingProvider embeddingProvider;
```

`setUp()`에 다음 라인 추가:

```java
given(qnaProperties.semanticDistanceThreshold()).willReturn(0.15);
```

새 `@Nested` 클래스 추가:

```java
@Nested
@DisplayName("의미 캐시")
class SemanticCache {

    @Test
    @DisplayName("정확 캐시 미스 → 의미 캐시 히트 시 임베딩 1회 호출, RAG/LLM 호출 0회")
    void semanticHit_skipsRagAndLlm() throws Exception {
        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        float[] embedding = new float[]{0.1f, 0.2f};
        given(embeddingProvider.embed("재학생도 가능?")).willReturn(embedding);
        CachedAnswer cached = new CachedAnswer(
                "이전 답변(의미 일치)",
                List.of(new QnaSourceResult(10L, null, null, null, null, "발췌")),
                Instant.now()
        );
        given(semanticQnaCache.findSimilar(10L, embedding)).willReturn(Optional.of(cached));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        AskQuestionCommand command = new AskQuestionCommand(10L, "재학생도 가능?", 1L);
        qnaService.askQuestion(command);
        Thread.sleep(100);

        verify(embeddingProvider, times(1)).embed("재학생도 가능?");
        verify(ragSearchService, never()).searchRelevantChunks(any());
        verify(ragSearchService, never()).searchRelevantChunks(any(), any());
        verify(qnaLlmProvider, never()).generateAnswer(anyString(), anyString(), anyString(), any());
        verify(historyWriter).markCompleted(eq(99L), eq("이전 답변(의미 일치)"), anyString());
    }

    @Test
    @DisplayName("정확 캐시 히트 시 임베딩 호출 0회, 의미 캐시 조회 0회")
    void exactHit_skipsEmbeddingAndSemantic() throws Exception {
        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        CachedAnswer cached = new CachedAnswer(
                "정확 답변", List.of(), Instant.now());
        given(qnaAnswerCache.get(10L, "재학생?")).willReturn(Optional.of(cached));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        AskQuestionCommand command = new AskQuestionCommand(10L, "재학생?", 1L);
        qnaService.askQuestion(command);
        Thread.sleep(100);

        verify(embeddingProvider, never()).embed(anyString());
        verify(semanticQnaCache, never()).findSimilar(anyLong(), any());
    }

    @Test
    @DisplayName("의미 캐시 미스 → RAG에 동일한 임베딩이 전달되고 LLM 1회 호출")
    void semanticMiss_passesSameEmbeddingToRag() throws Exception {
        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        float[] embedding = new float[]{0.3f, 0.4f};
        given(embeddingProvider.embed("질문")).willReturn(embedding);
        given(semanticQnaCache.findSimilar(10L, embedding)).willReturn(Optional.empty());
        given(ragSearchService.searchRelevantChunks(any(), eq(embedding))).willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
        qnaService.askQuestion(command);
        Thread.sleep(200);

        verify(embeddingProvider, times(1)).embed("질문");
        verify(ragSearchService, times(1)).searchRelevantChunks(any(), eq(embedding));
        verify(qnaLlmProvider, times(1)).generateAnswer(anyString(), anyString(), anyString(), any());
        verify(qnaAnswerCache).put(eq(10L), eq("질문"), any(CachedAnswer.class));
        verify(semanticQnaCache).put(eq(10L), eq("질문"), eq(embedding), any(CachedAnswer.class));
    }

    @Test
    @DisplayName("의미 캐시 findSimilar 가 예외를 던지면 RAG 흐름으로 폴백")
    void semanticCacheError_fallsBackToRag() throws Exception {
        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        float[] embedding = new float[]{0.5f};
        given(embeddingProvider.embed("질문")).willReturn(embedding);
        given(semanticQnaCache.findSimilar(anyLong(), any()))
                .willThrow(new RuntimeException("DB 장애"));
        given(ragSearchService.searchRelevantChunks(any(), eq(embedding))).willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        AskQuestionCommand command = new AskQuestionCommand(10L, "질문", 1L);
        qnaService.askQuestion(command);
        Thread.sleep(200);

        verify(qnaLlmProvider, times(1)).generateAnswer(anyString(), anyString(), anyString(), any());
    }
}
```

기존 `Happy.threshold_passesAndCallsLlm` 테스트는 `searchRelevantChunks(any())`를 mock하지만, 새 흐름에선 두 인자 오버로드가 호출되므로 stubbing을 변경해야 함. 다음과 같이 수정:

```java
// 기존
given(ragSearchService.searchRelevantChunks(any())).willReturn(List.of(...));
// 변경 후
given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
given(semanticQnaCache.findSimilar(anyLong(), any())).willReturn(Optional.empty());
given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(
        chunk(0.2),
        chunk(0.6)
));
```

다른 테스트(`Reject.noIndexedChunks_failsWithNoIndexedDocument`, `Reject.allChunksOverThreshold_failsWithNoRelevantChunk`, `LlmError.llmThrows_marksFailed`)도 동일하게 두 인자 오버로드를 stubbing하도록 수정.

`cacheMissDefaults()` 헬퍼에 임베딩과 의미 캐시 미스 기본값 추가:

```java
private void cacheMissDefaults() {
    given(costGuard.allows(10L)).willReturn(true);
    given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
    given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
    given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
    given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
    given(semanticQnaCache.findSimilar(anyLong(), any())).willReturn(Optional.empty());
}
```

기존 `searchRelevantChunks(any())` stubbing은 `searchRelevantChunks(any(), any())`로 모두 교체.

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaServiceTest" -q`
Expected: FAIL — `SemanticQnaCache`, `EmbeddingProvider` 의존이 `QnaService`에 없음. 컴파일도 실패.

- [ ] **Step 3: `QnaService` 변경 (최종 코드)**

```java
package com.youthfit.qna.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.dto.result.QnaSourceResult;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.domain.model.QnaFailedReason;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QnaService {

    private static final Logger log = LoggerFactory.getLogger(QnaService.class);
    private static final long SSE_TIMEOUT = 120_000L;
    private static final String NO_INDEXED_MESSAGE =
            "이 정책은 아직 본문 인덱싱이 되어 있지 않아 답변을 만들 수 없습니다. 정책 상세 페이지에서 원문을 확인해 주세요.";
    private static final String NO_RELEVANT_MESSAGE =
            "해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다.";
    private static final String COST_GUARD_BLOCKED_MESSAGE =
            "현재 환경에서 이 정책은 Q&A를 지원하지 않습니다.";
    private static final String LLM_ERROR_MESSAGE =
            "답변 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    private final CostGuard costGuard;
    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository policyAttachmentRepository;
    private final RagSearchService ragSearchService;
    private final QnaLlmProvider qnaLlmProvider;
    private final QnaAnswerCache qnaAnswerCache;
    private final SemanticQnaCache semanticQnaCache;
    private final EmbeddingProvider embeddingProvider;
    private final QnaHistoryWriter historyWriter;
    private final QnaProperties qnaProperties;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = new DelegatingSecurityContextExecutorService(
            Executors.newVirtualThreadPerTaskExecutor());

    public SseEmitter askQuestion(AskQuestionCommand command) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        if (!costGuard.allows(command.policyId())) {
            costGuard.logSkip("qna.askQuestion", command.policyId());
            sendErrorEvent(emitter, COST_GUARD_BLOCKED_MESSAGE);
            emitter.complete();
            return emitter;
        }

        Policy policy = policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        Long historyId = historyWriter.startInProgress(command.userId(), command.policyId(), command.question());

        executor.execute(() -> {
            try {
                processQuestion(emitter, command, policy, historyId);
            } catch (Exception e) {
                log.error("Q&A 스트리밍 처리 중 예상치 못한 오류: policyId={}", command.policyId(), e);
                sendErrorEvent(emitter, LLM_ERROR_MESSAGE);
                historyWriter.markFailed(historyId, QnaFailedReason.INTERNAL_ERROR);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void processQuestion(SseEmitter emitter, AskQuestionCommand command, Policy policy, Long historyId)
            throws IOException {
        // ① 정확 일치 캐시
        Optional<CachedAnswer> exact;
        try {
            exact = qnaAnswerCache.get(command.policyId(), command.question());
        } catch (Exception e) {
            log.warn("Q&A 정확 캐시 get 실패 (정상 흐름 진행): policyId={}", command.policyId(), e);
            exact = Optional.empty();
        }
        if (exact.isPresent()) {
            sendCachedAnswer(emitter, exact.get(), historyId);
            return;
        }

        // ② 임베딩 1회
        float[] queryEmbedding = embeddingProvider.embed(command.question());

        // ③ 의미 캐시
        Optional<CachedAnswer> semantic;
        try {
            semantic = semanticQnaCache.findSimilar(command.policyId(), queryEmbedding);
        } catch (Exception e) {
            log.warn("Q&A 의미 캐시 findSimilar 실패 (정상 흐름 진행): policyId={}", command.policyId(), e);
            semantic = Optional.empty();
        }
        if (semantic.isPresent()) {
            sendCachedAnswer(emitter, semantic.get(), historyId);
            return;
        }

        // ④ RAG (임베딩 재사용)
        List<PolicyDocumentChunkResult> chunks = ragSearchService.searchRelevantChunks(
                new SearchChunksCommand(command.policyId(), command.question()), queryEmbedding);

        if (chunks.isEmpty()) {
            rejectAndComplete(emitter, historyId, NO_INDEXED_MESSAGE, QnaFailedReason.NO_INDEXED_DOCUMENT);
            return;
        }

        double threshold = qnaProperties.relevanceDistanceThreshold();
        List<PolicyDocumentChunkResult> passing = chunks.stream()
                .filter(c -> c.distance() <= threshold)
                .toList();

        if (passing.isEmpty()) {
            rejectAndComplete(emitter, historyId, NO_RELEVANT_MESSAGE, QnaFailedReason.NO_RELEVANT_CHUNK);
            return;
        }

        String context = buildContext(passing);
        List<QnaSourceResult> sources = buildSources(command.policyId(), passing);

        // ⑤ LLM 스트림
        String fullAnswer;
        try {
            fullAnswer = qnaLlmProvider.generateAnswer(
                    policy.getTitle(), context, command.question(),
                    chunk -> sendChunkEvent(emitter, chunk)
            );
        } catch (Exception e) {
            log.error("LLM 호출 실패: policyId={}", command.policyId(), e);
            sendErrorEvent(emitter, LLM_ERROR_MESSAGE);
            historyWriter.markFailed(historyId, QnaFailedReason.LLM_ERROR);
            emitter.completeWithError(e);
            return;
        }

        sendSourcesEvent(emitter, sources);
        sendDoneEvent(emitter);
        emitter.complete();

        // ⑥ 캐시 저장
        CachedAnswer answer = new CachedAnswer(fullAnswer, sources, Instant.now());
        try {
            qnaAnswerCache.put(command.policyId(), command.question(), answer);
        } catch (Exception e) {
            log.warn("Q&A 정확 캐시 put 실패: policyId={}", command.policyId(), e);
        }
        try {
            semanticQnaCache.put(command.policyId(), command.question(), queryEmbedding, answer);
        } catch (Exception e) {
            log.warn("Q&A 의미 캐시 put 실패: policyId={}", command.policyId(), e);
        }

        try {
            String sourcesJson = objectMapper.writeValueAsString(sources);
            historyWriter.markCompleted(historyId, fullAnswer, sourcesJson);
        } catch (Exception e) {
            log.error("Q&A 히스토리 markCompleted 실패: historyId={}", historyId, e);
        }
    }

    private void sendCachedAnswer(SseEmitter emitter, CachedAnswer cached, Long historyId) {
        sendChunkEvent(emitter, cached.answer());
        sendSourcesEvent(emitter, cached.sources());
        sendDoneEvent(emitter);
        emitter.complete();
        try {
            String sourcesJson = objectMapper.writeValueAsString(cached.sources());
            historyWriter.markCompleted(historyId, cached.answer(), sourcesJson);
        } catch (Exception e) {
            log.error("Q&A 캐시 히트 history markCompleted 실패: historyId={}", historyId, e);
        }
    }

    private void rejectAndComplete(SseEmitter emitter, Long historyId, String message, QnaFailedReason reason) {
        sendChunkEvent(emitter, message);
        sendSourcesEvent(emitter, List.of());
        sendDoneEvent(emitter);
        emitter.complete();
        historyWriter.markFailed(historyId, reason);
    }

    private String buildContext(List<PolicyDocumentChunkResult> chunks) {
        StringBuilder sb = new StringBuilder();
        for (PolicyDocumentChunkResult chunk : chunks) {
            sb.append("[청크 ").append(chunk.chunkIndex()).append("]\n");
            sb.append(chunk.content()).append("\n\n");
        }
        return sb.toString();
    }

    private List<QnaSourceResult> buildSources(Long policyId, List<PolicyDocumentChunkResult> chunks) {
        Map<Long, String> attachmentLabels = policyAttachmentRepository.findByPolicyId(policyId).stream()
                .collect(Collectors.toMap(PolicyAttachment::getId, a -> stripExtension(a.getName())));

        return chunks.stream()
                .map(chunk -> new QnaSourceResult(
                        policyId,
                        chunk.attachmentId(),
                        resolveAttachmentLabel(chunk.attachmentId(), attachmentLabels),
                        chunk.pageStart(),
                        chunk.pageEnd(),
                        truncateExcerpt(chunk.content())
                ))
                .toList();
    }

    private String resolveAttachmentLabel(Long attachmentId, Map<Long, String> labels) {
        if (attachmentId == null) return null;
        String name = labels.get(attachmentId);
        return name != null ? name : "첨부 #" + attachmentId;
    }

    private String stripExtension(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String truncateExcerpt(String content) {
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    private void sendChunkEvent(SseEmitter emitter, String content) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "CHUNK", "content", content)));
        } catch (IOException e) {
            log.warn("SSE CHUNK 이벤트 전송 실패", e);
        }
    }

    private void sendSourcesEvent(SseEmitter emitter, List<QnaSourceResult> sources) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "SOURCES", "sources", sources)));
        } catch (IOException e) {
            log.warn("SSE SOURCES 이벤트 전송 실패", e);
        }
    }

    private void sendDoneEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "DONE")));
        } catch (IOException e) {
            log.warn("SSE DONE 이벤트 전송 실패", e);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "ERROR", "content", message)));
        } catch (IOException e) {
            log.warn("SSE ERROR 이벤트 전송 실패", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.qna.application.service.QnaServiceTest" -q`
Expected: PASS (모든 기존 케이스 + 신규 의미 캐시 케이스)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/application/service/QnaService.java backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java
git commit -m "feat(qna): processQuestion 흐름에 의미 캐시 단계 삽입"
```

---

## Task 9: `RagIndexingService`에 invalidator 호출 추가 — TDD

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java`
- Modify: `backend/src/test/java/com/youthfit/rag/application/service/RagIndexingServiceTest.java`

- [ ] **Step 1: 테스트 변경 (실패 테스트)**

`RagIndexingServiceTest`에 mock 필드 추가:

```java
@Mock
private com.youthfit.qna.application.port.QnaCacheInvalidator qnaCacheInvalidator;
```

새 케이스 추가:

```java
@Test
@DisplayName("해시가 변경되면 의미 캐시도 같은 트랜잭션 안에서 비운다")
void differentHash_invalidatesSemanticCache() {
    IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "변경된 내용");
    PolicyDocument existing = createChunk(1L, 0, "기존 청크", "old-hash");

    given(documentChunker.computeHash("변경된 내용")).willReturn("new-hash");
    given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of(existing));
    given(documentChunker.chunk(1L, "변경된 내용"))
            .willReturn(List.of(createChunk(1L, 0, "새 청크", "new-hash")));
    given(embeddingProvider.embedBatch(List.of("새 청크")))
            .willReturn(List.of(new float[]{0.5f}));

    ragIndexingService.indexPolicyDocument(command);

    verify(qnaCacheInvalidator).invalidatePolicy(1L);
}

@Test
@DisplayName("해시가 동일하면 의미 캐시 invalidate 를 호출하지 않는다")
void sameHash_doesNotInvalidate() {
    IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "정책 내용");
    PolicyDocument existing = createChunk(1L, 0, "기존 청크", "same-hash");
    given(documentChunker.computeHash("정책 내용")).willReturn("same-hash");
    given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of(existing));

    ragIndexingService.indexPolicyDocument(command);

    verify(qnaCacheInvalidator, never()).invalidatePolicy(anyLong());
}

@Test
@DisplayName("신규 정책(기존 인덱스 없음)은 invalidate 호출 없이 그대로 인덱싱한다")
void newDocument_doesNotInvalidate() {
    IndexPolicyDocumentCommand command = new IndexPolicyDocumentCommand(1L, "정책 내용");
    given(documentChunker.computeHash("정책 내용")).willReturn("hash");
    given(policyDocumentRepository.findByPolicyId(1L)).willReturn(List.of());
    given(documentChunker.chunk(1L, "정책 내용"))
            .willReturn(List.of(createChunk(1L, 0, "청크", "hash")));
    given(embeddingProvider.embedBatch(any())).willReturn(List.of(new float[]{0.1f}));

    ragIndexingService.indexPolicyDocument(command);

    verify(qnaCacheInvalidator, never()).invalidatePolicy(anyLong());
}
```

import 추가:
```java
import static org.mockito.ArgumentMatchers.anyLong;
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagIndexingServiceTest" -q`
Expected: FAIL — `QnaCacheInvalidator`가 `RagIndexingService`에 없음.

- [ ] **Step 3: `RagIndexingService` 변경 (최종 코드)**

```java
package com.youthfit.rag.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.qna.application.port.QnaCacheInvalidator;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import com.youthfit.rag.domain.service.DocumentChunker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagIndexingService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexingService.class);

    private final PolicyDocumentRepository policyDocumentRepository;
    private final DocumentChunker documentChunker;
    private final EmbeddingProvider embeddingProvider;
    private final CostGuard costGuard;
    private final QnaCacheInvalidator qnaCacheInvalidator;

    @Transactional
    public IndexingResult indexPolicyDocument(IndexPolicyDocumentCommand command) {
        if (!costGuard.allows(command.policyId())) {
            costGuard.logSkip("indexPolicyDocument", command.policyId());
            return new IndexingResult(command.policyId(), 0, false);
        }
        String newHash = documentChunker.computeHash(command.content());

        List<PolicyDocument> existing = policyDocumentRepository.findByPolicyId(command.policyId());
        if (!existing.isEmpty()) {
            String existingHash = existing.get(0).getSourceHash();
            if (existingHash.equals(newHash)) {
                return new IndexingResult(command.policyId(), existing.size(), false);
            }
            policyDocumentRepository.deleteByPolicyId(command.policyId());
            qnaCacheInvalidator.invalidatePolicy(command.policyId());
        }

        List<PolicyDocument> chunks = documentChunker.chunk(command.policyId(), command.content());
        generateEmbeddings(chunks);
        policyDocumentRepository.saveAll(chunks);

        return new IndexingResult(command.policyId(), chunks.size(), true);
    }

    private void generateEmbeddings(List<PolicyDocument> chunks) {
        List<String> texts = chunks.stream()
                .map(PolicyDocument::getContent)
                .toList();

        List<float[]> embeddings = embeddingProvider.embedBatch(texts);

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).updateEmbedding(embeddings.get(i));
        }

        log.info("{}개 청크에 대한 임베딩 생성 완료", chunks.size());
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagIndexingServiceTest" -q`
Expected: PASS

- [ ] **Step 5: 전체 테스트 회귀 검증**

Run: `cd backend && ./gradlew test -q`
Expected: PASS (전체)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java backend/src/test/java/com/youthfit/rag/application/service/RagIndexingServiceTest.java
git commit -m "feat(rag): 정책 본문 갱신 시 의미 캐시 무효화"
```

---

## Task 10: 운영 DDL 스크립트 작성 + 운영 노트

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql`
- Modify: `docs/OPS.md` (DDL 적용 절차 추가)

- [ ] **Step 1: DDL 파일 작성**

```sql
-- backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql
-- Q&A 의미 캐시 테이블. 운영 환경에 수동 적용한다.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS qna_question_cache (
    id            BIGSERIAL PRIMARY KEY,
    policy_id     BIGINT       NOT NULL,
    question_text TEXT         NOT NULL,
    embedding     vector(1536) NOT NULL,
    answer        TEXT         NOT NULL,
    sources_json  JSONB        NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_qna_question_cache_policy
    ON qna_question_cache (policy_id);

CREATE INDEX IF NOT EXISTS idx_qna_question_cache_embedding
    ON qna_question_cache
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

- [ ] **Step 2: `docs/OPS.md`에 적용 절차 한 단락 추가**

`docs/OPS.md` 파일 끝에 아래 섹션을 추가한다:

```markdown
## Q&A 의미 캐시 테이블 (2026-05-01)

`qna_question_cache` 테이블을 운영 PG에 수동 적용한다 (Flyway 미사용).

```bash
psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql
```

배포 순서:
1. 운영 PG에 위 DDL 적용
2. 백엔드 재배포

DDL 미적용 상태로 배포되면 `qna_question_cache`를 매핑한 엔티티 검증(`ddl-auto: validate`)에서 부팅 실패한다.
```

- [ ] **Step 3: 빌드 검증 (final smoke)**

Run: `cd backend && ./gradlew build -x test -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-01-qna-question-cache.sql docs/OPS.md
git commit -m "ops: Q&A 의미 캐시 테이블 DDL과 적용 절차"
```

---

## Validation (final)

- [ ] 전체 테스트: `cd backend && ./gradlew test -q` → PASS
- [ ] 전체 빌드: `cd backend && ./gradlew build -q` → SUCCESS
- [ ] 운영 PG에 DDL 적용 (배포 시점)
- [ ] 배포 후 정확 캐시 미스 → 의미 캐시 미스 → LLM 호출 흐름 한 번 → 같은 질문 반복 시 의미 캐시 히트(로그 확인: `Q&A 의미 캐시 히트: policyId=..., distance=...`)
