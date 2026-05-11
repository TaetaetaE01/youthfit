# RAG Retrieval Keyword Boost Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RAG retrieval 단계에서 query 의 한글 키워드가 청크 본문에 정확히 들어있으면 cosine distance 에 multiplicative boost 를 줘서 정책 7번 같은 표 청크를 top-K 안으로 끌어올린다.

**Architecture:** `RagSearchService` 가 `KeywordExtractor` (도메인 서비스) 로 query 토큰 추출 → `PolicyDocumentRepository.findSimilarByEmbedding` 시그니처에 `List<String> keywords` 추가 → native SQL 안 `unnest(:keywords) ILIKE` 로 hit_count 산출 후 `CASE` 단계 함수로 distance 보정 → `ORDER BY boosted_distance LIMIT top-K`. feature flag `youthfit.qna.keyword-boost.enabled` 로 즉시 비활성화 가능.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Hibernate + Spring Data JPA, PostgreSQL 17 + pgvector, JUnit 5, Mockito, Testcontainers

**Spec:** `docs/superpowers/specs/2026-05-11-rag-retrieval-keyword-boost-design.md`

---

## File Structure

**신규 파일**
- `backend/src/main/java/com/youthfit/rag/domain/service/KeywordExtractor.java` — 정규식 토큰화 + stopword 필터 + max 상한. 순수 Java (Spring 의존 X)
- `backend/src/main/java/com/youthfit/qna/infrastructure/config/KeywordBoostProperties.java` — `youthfit.qna.keyword-boost` 바인딩 record
- `backend/src/test/java/com/youthfit/rag/domain/service/KeywordExtractorTest.java` — 단위 테스트
- `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryKeywordBoostTest.java` — Testcontainers + pgvector slice 테스트

**수정 파일**
- `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java` — `findSimilarByEmbedding` 시그니처에 `List<String> keywords` 추가
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java` — keywords 전달
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java` — native SQL 수정
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` — KeywordExtractor + Properties 주입, keywords 추출 + flag 분기
- `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java` — KeywordBoostProperties 등록 + KeywordExtractor `@Bean`
- `backend/src/main/resources/application.yml` — `youthfit.qna.keyword-boost` 섹션 추가
- `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java` — 변경된 시그니처 반영, keyword 흐름 신규 테스트 추가

**무변경 (확인용)**
- `backend/src/main/java/com/youthfit/rag/application/service/RagIndexingService.java`
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/PgVectorSemanticQnaCache.java`

---

## Task 1: KeywordBoostProperties 정의 + Spring 등록 + application.yml

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/config/KeywordBoostProperties.java`
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java`
- Modify: `backend/src/main/resources/application.yml`

목적: 후속 Task 에서 사용할 properties 와 yaml 키를 미리 만들어 컴파일·기동만 통과시킨다. (이 시점엔 아직 사용처 없음)

- [ ] **Step 1: 현재 `QnaConfig` 내용 확인**

Run: `cat backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java`
Expected: `@EnableConfigurationProperties(QnaProperties.class)` 가 클래스 어노테이션으로 있는 형태. (다른 형태면 동일 위치에 KeywordBoostProperties 만 추가)

- [ ] **Step 2: `KeywordBoostProperties` record 작성**

Create `backend/src/main/java/com/youthfit/qna/infrastructure/config/KeywordBoostProperties.java`:

```java
package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "youthfit.qna.keyword-boost")
public record KeywordBoostProperties(
        boolean enabled,
        int maxKeywords,
        List<String> stopwords
) {
    public KeywordBoostProperties {
        if (maxKeywords <= 0) maxKeywords = 5;
        if (stopwords == null) stopwords = List.of();
    }
}
```

`enabled` 의 기본값 처리는 yaml/환경변수에 위임 (Step 4 에서 `true` 기본). compact constructor 로 maxKeywords/stopwords null-guard.

- [ ] **Step 3: `QnaConfig` 에 `KeywordBoostProperties` 등록**

Modify `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java`:

`@EnableConfigurationProperties` 인자에 `KeywordBoostProperties.class` 추가. 예 (현재가 `@EnableConfigurationProperties(QnaProperties.class)` 라면):

```java
@EnableConfigurationProperties({QnaProperties.class, KeywordBoostProperties.class})
```

`@Configuration` 위치는 그대로 둔다.

- [ ] **Step 4: `application.yml` 의 `youthfit.qna` 섹션에 `keyword-boost` 추가**

Modify `backend/src/main/resources/application.yml`. `youthfit.qna.cache:` 아래 (또는 `semantic-distance-threshold:` 아래) 들여쓰기 맞춰 추가:

```yaml
    keyword-boost:
      enabled: ${QNA_KEYWORD_BOOST_ENABLED:true}
      max-keywords: ${QNA_KEYWORD_BOOST_MAX_KEYWORDS:5}
      stopwords:
        - 뭐
        - 무엇
        - 어떤
        - 어떻게
        - 얼마
        - 언제
        - 어디
        - 어디서
        - 누구
        - 가능
        - 필요
        - 알려줘
        - 정보
        - 내용
```

들여쓰기 주의: `qna:` 가 `youthfit:` 의 child 라면 `keyword-boost:` 는 `qna:` 의 child 위치 (스페이스 4개).

- [ ] **Step 5: 빌드 통과 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

기동 검증은 다음 Task 들에서 통합 후 일괄. (이 단계는 클래스 추가만)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/config/KeywordBoostProperties.java \
        backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java \
        backend/src/main/resources/application.yml
git commit -m "feat(qna): KeywordBoostProperties + application.yml 키 추가"
```

---

## Task 2: KeywordExtractor 도메인 서비스 (TDD)

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/domain/service/KeywordExtractor.java`
- Create: `backend/src/test/java/com/youthfit/rag/domain/service/KeywordExtractorTest.java`

목적: query 문자열에서 한글/영숫 토큰을 추출하고 stopword·길이 상한을 적용. 순수 Java (Spring 의존 없음, final field constructor injection).

- [ ] **Step 1: 실패하는 첫 테스트 (한글 토큰 추출)**

Create `backend/src/test/java/com/youthfit/rag/domain/service/KeywordExtractorTest.java`:

```java
package com.youthfit.rag.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeywordExtractor")
class KeywordExtractorTest {

    private final KeywordExtractor extractor =
            new KeywordExtractor(Set.of(), 5);

    @Nested
    @DisplayName("기본 토큰 추출")
    class BasicExtraction {

        @Test
        @DisplayName("한글 합성어를 한 토큰으로 추출한다")
        void koreanCompoundWord() {
            List<String> keywords = extractor.extract("디딤씨앗통장 중복 가능?");
            assertThat(keywords).containsExactly("디딤씨앗통장", "중복", "가능");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.KeywordExtractorTest"`
Expected: COMPILE FAIL — "KeywordExtractor 를 찾을 수 없음".

- [ ] **Step 3: KeywordExtractor 최소 구현**

Create `backend/src/main/java/com/youthfit/rag/domain/service/KeywordExtractor.java`:

```java
package com.youthfit.rag.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeywordExtractor {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[가-힣A-Za-z0-9]{2,}");

    private final Set<String> stopwords;
    private final int maxKeywords;

    public KeywordExtractor(Set<String> stopwords, int maxKeywords) {
        this.stopwords = stopwords == null ? Set.of() : Set.copyOf(stopwords);
        this.maxKeywords = maxKeywords > 0 ? maxKeywords : 5;
    }

    public List<String> extract(String query) {
        if (query == null || query.isBlank()) return List.of();

        Matcher matcher = TOKEN_PATTERN.matcher(query);
        Set<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (stopwords.contains(token)) continue;
            seen.add(token);
            if (seen.size() >= maxKeywords) break;
        }
        return new ArrayList<>(seen);
    }
}
```

- [ ] **Step 4: 첫 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.KeywordExtractorTest"`
Expected: PASS (1 test).

- [ ] **Step 5: 추가 테스트 — null/blank, 한영숫 혼용, 1글자 무시**

Append to `KeywordExtractorTest`:

```java
    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("null query 는 빈 리스트를 반환한다")
        void nullQuery() {
            assertThat(extractor.extract(null)).isEmpty();
        }

        @Test
        @DisplayName("blank query 는 빈 리스트를 반환한다")
        void blankQuery() {
            assertThat(extractor.extract("   ")).isEmpty();
        }

        @Test
        @DisplayName("한영숫 혼용 토큰을 한 단위로 추출한다")
        void mixedAlphanumeric() {
            List<String> keywords = extractor.extract("30만원 GPT4 디딤");
            assertThat(keywords).containsExactly("30만원", "GPT4", "디딤");
        }

        @Test
        @DisplayName("1글자 토큰은 제외된다")
        void singleCharIgnored() {
            List<String> keywords = extractor.extract("나 너 우리 디딤");
            assertThat(keywords).containsExactly("우리", "디딤");
        }

        @Test
        @DisplayName("동일 토큰 중복은 1회만 유지한다")
        void deduplicate() {
            List<String> keywords = extractor.extract("청년 청년 청년 정책");
            assertThat(keywords).containsExactly("청년", "정책");
        }
    }
```

- [ ] **Step 6: 추가 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.KeywordExtractorTest"`
Expected: PASS (6 tests).

- [ ] **Step 7: stopword + max 상한 테스트**

Append:

```java
    @Nested
    @DisplayName("stopword 와 상한")
    class StopwordAndLimit {

        @Test
        @DisplayName("stopword 는 결과에서 제외된다")
        void stopwordExcluded() {
            KeywordExtractor withStopword = new KeywordExtractor(
                    Set.of("가능", "얼마"), 5);

            List<String> keywords = withStopword.extract("디딤씨앗통장 중복 가능 얼마");
            assertThat(keywords).containsExactly("디딤씨앗통장", "중복");
        }

        @Test
        @DisplayName("maxKeywords 상한이 적용된다")
        void maxKeywordsLimit() {
            KeywordExtractor limited = new KeywordExtractor(Set.of(), 2);

            List<String> keywords = limited.extract("청년 정책 지원 통장 신청");
            assertThat(keywords).hasSize(2).containsExactly("청년", "정책");
        }

        @Test
        @DisplayName("stopword 가 상한 카운트에 들어가지 않는다")
        void stopwordNotCounted() {
            KeywordExtractor limited = new KeywordExtractor(
                    Set.of("얼마", "어떻게"), 3);

            List<String> keywords = limited.extract("얼마 어떻게 청년 정책 지원 통장");
            assertThat(keywords).containsExactly("청년", "정책", "지원");
        }
    }
```

- [ ] **Step 8: 전체 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.domain.service.KeywordExtractorTest"`
Expected: PASS (9 tests).

- [ ] **Step 9: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/service/KeywordExtractor.java \
        backend/src/test/java/com/youthfit/rag/domain/service/KeywordExtractorTest.java
git commit -m "feat(rag): KeywordExtractor 도메인 서비스 (정규식 토큰 + stopword + 상한)"
```

---

## Task 3: PolicyDocumentRepository 시그니처 + native SQL 변경

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java`
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java`
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java` (호출부 컴파일 통과 위해 인자 추가만)

목적: keyword-boost 가 SQL 안에서 적용되도록 시그니처와 native query 를 한 번에 변경한다. RagSearchService 호출부는 임시로 빈 리스트(`List.of()`) 만 전달해서 회귀 zero 검증 → 후속 Task 4 에서 실제 KeywordExtractor 결과 사용으로 교체.

- [ ] **Step 1: 도메인 인터페이스 시그니처 변경**

Modify `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java`:

`findSimilarByEmbedding` 메서드를 다음으로 교체 (기존 메서드 삭제, 시그니처 1개로 통일):

```java
List<SimilarChunk> findSimilarByEmbedding(
        Long policyId,
        float[] queryEmbedding,
        List<String> keywords,
        int limit
);
```

`import java.util.List;` 가 있어야 한다 (없으면 추가).

- [ ] **Step 2: PolicyDocumentRepositoryImpl 시그니처 + 전달 변경**

Modify `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java`:

```java
@Override
public List<SimilarChunk> findSimilarByEmbedding(Long policyId, float[] queryEmbedding,
                                                  List<String> keywords, int limit) {
    String vectorString = toVectorString(queryEmbedding);
    List<String> safeKeywords = keywords == null ? List.of() : keywords;
    return jpaRepository.findSimilarByEmbedding(policyId, vectorString, safeKeywords, limit).stream()
            .map(this::toSimilarChunk)
            .toList();
}
```

- [ ] **Step 3: PolicyDocumentJpaRepository native SQL 수정**

Modify `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java`. `findSimilarByEmbedding` 메서드를 다음으로 교체:

```java
@Query(value = """
        WITH base AS (
          SELECT id, policy_id, chunk_index, content,
                 attachment_id, page_start, page_end,
                 (embedding <=> cast(:queryEmbedding AS vector)) AS distance
            FROM policy_document
           WHERE policy_id = :policyId
             AND embedding IS NOT NULL
        ), scored AS (
          SELECT b.*,
                 (SELECT COUNT(*) FROM unnest(cast(:keywords AS text[])) k
                   WHERE b.content ILIKE '%' || k || '%') AS hit_count
            FROM base b
        )
        SELECT id,
               policy_id     AS policyId,
               chunk_index   AS chunkIndex,
               content,
               attachment_id AS attachmentId,
               page_start    AS pageStart,
               page_end      AS pageEnd,
               CASE
                 WHEN hit_count >= 3 THEN distance * 0.78
                 WHEN hit_count = 2 THEN distance * 0.85
                 WHEN hit_count = 1 THEN distance * 0.92
                 ELSE distance
               END AS distance
          FROM scored
         ORDER BY 8
         LIMIT :limit
        """, nativeQuery = true)
List<Object[]> findSimilarByEmbedding(
        @Param("policyId") Long policyId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("keywords") List<String> keywords,
        @Param("limit") int limit
);
```

`import java.util.List;` 확인. JPA 4 (Hibernate 6+) 는 `List<String>` → PostgreSQL `text[]` 을 `cast(:keywords AS text[])` 로 명시 캐스트 시 자동 변환 처리. 변환이 실패하면 Step 5 의 Repository 통합 테스트에서 즉시 드러난다.

- [ ] **Step 4: RagSearchService 호출부 임시 수정 (빈 리스트 전달)**

Modify `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java`. `findSimilarByEmbedding` 호출부 1곳을 다음으로 교체:

```java
List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
        command.policyId(), precomputedEmbedding, List.of(), DEFAULT_TOP_K);
```

`import java.util.List;` 가 이미 있는지 확인. 이 단계는 컴파일·회귀 zero 만 보장. KeywordExtractor 실제 호출은 Task 4.

- [ ] **Step 5: 기존 RagSearchServiceTest 의 깨진 mock setup 임시 수정**

Modify `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java`. `findSimilarByEmbedding` mock 호출 3곳을 모두 다음 형태로 변경 (기존 `eq(10)` 위치 앞에 `eq(List.of())` 삽입):

```java
given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(queryEmbedding), eq(List.of()), eq(10)))
        .willReturn(...);
```

3곳: `vectorSearchHasResults_returnsSimilarChunks`, `vectorSearchEmpty_fallsBackToKeyword`, `keywordFallback_isCaseInsensitive`, `keywordFallbackNoMatch_returnsEmpty` (총 4곳일 수 있음 — 파일에서 `findSimilarByEmbedding` grep 후 모두 변경).

`import java.util.List;` 확인.

- [ ] **Step 6: 단위 테스트 + 빌드 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagSearchServiceTest"`
Expected: 기존 6개 테스트 PASS (회귀 zero).

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: pgvector slice 테스트 작성 (회귀 + boost 동작)**

Create `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryKeywordBoostTest.java`:

```java
package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
@Testcontainers
@DisplayName("PolicyDocumentRepository keyword boost")
class PolicyDocumentRepositoryKeywordBoostTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void initVectorExtension(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector");
    }

    @Autowired
    private PolicyDocumentJpaRepository jpaRepository;

    private Long policyId;

    @BeforeEach
    void setUp() {
        policyId = 9001L;
        jpaRepository.deleteByPolicyId(policyId);
        // 표 청크: distance 가 가장 멀지만 키워드 2개 hit
        savePolicyDocument(policyId, 0, "표 항목: 디딤씨앗통장 중복 가능 통장 리스트",
                new float[]{1.0f, 0.0f, 0.0f});
        // 자연어 청크: distance 더 가깝지만 키워드 1개만 hit
        savePolicyDocument(policyId, 1, "중복관리 대상사업 안내",
                new float[]{0.7f, 0.7f, 0.0f});
        // 무관 청크: 키워드 0개
        savePolicyDocument(policyId, 2, "기타 안내",
                new float[]{0.0f, 1.0f, 0.0f});
    }

    @Test
    @DisplayName("keywords 빈 리스트면 순수 cosine distance 순서")
    void emptyKeywords_pureDistanceOrder() {
        float[] query = new float[]{0.7f, 0.7f, 0.0f};

        List<Object[]> rows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), List.of(), 10);

        assertThat(rows).hasSize(3);
        // 순서: 자연어(0,1) -> 무관(0,2) -> 표(1,0)  (cosine distance 기준)
        assertThat((Integer) rows.get(0)[2]).isEqualTo(1);
        assertThat((Integer) rows.get(2)[2]).isEqualTo(0);
    }

    @Test
    @DisplayName("키워드 hit 청크가 boost 후 ranking 위로 올라온다")
    void keywordsBoost_movesHitChunkUp() {
        float[] query = new float[]{0.7f, 0.7f, 0.0f};
        List<String> keywords = List.of("디딤씨앗통장", "중복");

        List<Object[]> rows = jpaRepository.findSimilarByEmbedding(
                policyId, toVectorLiteral(query), keywords, 10);

        assertThat(rows).hasSize(3);
        // 표 청크(2 keyword hit, ×0.85) 가 자연어 청크(1 keyword hit, ×0.92) 보다 위
        assertThat((Integer) rows.get(0)[2]).isEqualTo(0);
    }

    private void savePolicyDocument(Long policyId, int chunkIndex, String content, float[] embedding) {
        PolicyDocument doc = PolicyDocument.builder()
                .policyId(policyId)
                .chunkIndex(chunkIndex)
                .content(content)
                .sourceHash("test-hash")
                .build();
        // embedding 컬럼은 Entity 의 setter 또는 builder 에 있을 것 — 아래는 ReflectionTestUtils 대안
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "embedding", embedding);
        jpaRepository.save(doc);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
```

주의: `PolicyDocument` 의 embedding 필드명 / 자료형이 다르면 `ReflectionTestUtils.setField` 의 두 번째 인자만 맞춰 조정. embedding 차원도 실제 운영(1536) 과 다르게 작은 차원(3) 을 쓰는 이유는 cosine distance 분포를 설계대로 통제하기 위함.

- [ ] **Step 8: pgvector 이미지 + Testcontainers 의존성 확인**

Run: `cd backend && grep -n "testcontainers\|pgvector" build.gradle build.gradle.kts settings.gradle 2>/dev/null`
Expected: `org.testcontainers:postgresql` 또는 `org.springframework.boot:spring-boot-testcontainers` 가 testImplementation 에 있어야 한다.
- 없으면 build.gradle 의 `dependencies { ... }` 에 다음 추가:
  ```
  testImplementation 'org.springframework.boot:spring-boot-testcontainers'
  testImplementation 'org.testcontainers:postgresql'
  testImplementation 'org.testcontainers:junit-jupiter'
  ```
- pgvector 이미지(`pgvector/pgvector:pg17`)는 Docker Hub 공개. 별도 dependency 불필요. 로컬 Docker daemon 만 떠있으면 됨.

- [ ] **Step 9: slice 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.infrastructure.persistence.PolicyDocumentRepositoryKeywordBoostTest"`
Expected: PASS (2 tests). 첫 실행은 pgvector 이미지 pull 로 1~2분 소요 가능.

만약 `cast(:keywords AS text[])` 부분에서 Hibernate 가 `List<String>` 을 변환 못하면, `PolicyDocumentJpaRepository` 의 `:keywords` 파라미터 타입을 `String[]` 으로 바꾸고 호출부에서 `keywords.toArray(new String[0])` 로 전달. 이 경우 `PolicyDocumentRepositoryImpl.findSimilarByEmbedding` 도 함께 수정.

- [ ] **Step 10: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java \
        backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java \
        backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java \
        backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java \
        backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java \
        backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryKeywordBoostTest.java
# build.gradle 변경 있으면 그것도 add
git commit -m "feat(rag): findSimilarByEmbedding 에 keyword boost SQL + slice test"
```

---

## Task 4: RagSearchService 통합 (KeywordExtractor + flag 분기)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java`
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java`
- Modify: `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java`

목적: KeywordExtractor 를 Spring Bean 으로 등록 (Properties 의 stopwords/maxKeywords 로 초기화) → RagSearchService 에 주입하고 flag 분기 적용 → 단위 테스트로 enabled/disabled 흐름 검증.

- [ ] **Step 1: KeywordExtractor 를 `@Bean` 으로 등록**

Modify `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java`. 클래스 안에 다음 메서드 추가:

```java
@org.springframework.context.annotation.Bean
public com.youthfit.rag.domain.service.KeywordExtractor keywordExtractor(
        KeywordBoostProperties properties) {
    return new com.youthfit.rag.domain.service.KeywordExtractor(
            properties.stopwords() == null
                    ? java.util.Set.of()
                    : new java.util.HashSet<>(properties.stopwords()),
            properties.maxKeywords()
    );
}
```

가독성 위해 import 정리 (`org.springframework.context.annotation.Bean`, `com.youthfit.rag.domain.service.KeywordExtractor`, `java.util.HashSet`, `java.util.Set`).

- [ ] **Step 2: RagSearchService 에 KeywordExtractor + Properties 주입**

Modify `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java`. 필드와 생성자 주입 부분에 추가 (`@RequiredArgsConstructor` 사용 중이라면 final field 만 추가):

```java
private final KeywordExtractor keywordExtractor;
private final KeywordBoostProperties keywordBoostProperties;
```

import 추가:
```java
import com.youthfit.rag.domain.service.KeywordExtractor;
import com.youthfit.qna.infrastructure.config.KeywordBoostProperties;
```

- [ ] **Step 3: searchRelevantChunks 안에서 keywords 추출 + flag 분기**

`searchRelevantChunks(SearchChunksCommand command, float[] precomputedEmbedding)` 안에서 Task 3 Step 4 에서 임시로 둔 `List.of()` 부분을 다음으로 교체:

```java
List<String> keywords = keywordBoostProperties.enabled()
        ? keywordExtractor.extract(command.query())
        : List.of();

List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
        command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);
```

`log.info` 의 distanceSummary 출력 직후에 다음 한 줄을 추가해 운영 가시성 확보:

```java
log.info("RAG 키워드 boost: policyId={}, keywords={}", command.policyId(), keywords);
```

- [ ] **Step 4: 기존 테스트의 mock 시그니처 keywords 인자 정정**

Modify `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java`. Task 3 Step 5 에서 `eq(List.of())` 로 두었던 부분을, 실제 query 별로 추출되는 keywords 와 일치시킨다. 해당 테스트들은 `enabled=true` 가 기본인 상황 가정.

각 테스트의 query 와 매핑:
- `"주거 지원"` → `["주거", "지원"]`
- `"월세"` → `["월세"]`
- `"HOUSING"` → `["HOUSING"]`
- `"존재하지않는키워드"` → `["존재하지않는키워드"]`

`@InjectMocks` 가 `KeywordExtractor`/`KeywordBoostProperties` 도 자동 mock 으로 주입하도록 mock 필드 추가:

```java
@Mock
private KeywordExtractor keywordExtractor;

@Mock
private KeywordBoostProperties keywordBoostProperties;
```

각 테스트 given 블록 앞에 추가:

```java
given(keywordBoostProperties.enabled()).willReturn(true);
given(keywordExtractor.extract("주거 지원")).willReturn(List.of("주거", "지원"));
```

그리고 `findSimilarByEmbedding` mock 매처를 변경된 keywords 와 매칭:

```java
given(policyDocumentRepository.findSimilarByEmbedding(
        eq(1L), eq(queryEmbedding), eq(List.of("주거", "지원")), eq(10)))
        .willReturn(similar);
```

`vectorSearchEmpty_fallsBackToKeyword`, `keywordFallback_isCaseInsensitive`, `keywordFallbackNoMatch_returnsEmpty` 도 동일 패턴으로 정정 (각 query 의 추출 키워드 stub).

- [ ] **Step 5: enabled=false 일 때 빈 리스트 전달 테스트 추가**

`SearchRelevantChunks` Nested 클래스 안에 신규 테스트 추가:

```java
@Test
@DisplayName("keyword-boost 비활성화 시 keywords 는 빈 리스트로 전달된다")
void keywordBoostDisabled_passesEmptyKeywords() {
    // given
    SearchChunksCommand command = new SearchChunksCommand(1L, "디딤씨앗통장 중복");
    float[] queryEmbedding = new float[]{0.1f};
    List<SimilarChunk> similar = List.of(
            createSimilarChunk(1L, 1L, 0, "표 항목 디딤씨앗통장", 0.5)
    );

    given(keywordBoostProperties.enabled()).willReturn(false);
    given(embeddingProvider.embed("디딤씨앗통장 중복")).willReturn(queryEmbedding);
    given(policyDocumentRepository.findSimilarByEmbedding(
            eq(1L), eq(queryEmbedding), eq(List.of()), eq(10)))
            .willReturn(similar);

    // when
    List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

    // then
    assertThat(result).hasSize(1);
    verify(keywordExtractor, never()).extract(any());
}
```

- [ ] **Step 6: enabled=true 일 때 KeywordExtractor 호출 + 추출 결과 전달 테스트 추가**

```java
@Test
@DisplayName("keyword-boost 활성화 시 추출된 키워드를 repository 에 전달한다")
void keywordBoostEnabled_passesExtractedKeywords() {
    // given
    SearchChunksCommand command = new SearchChunksCommand(1L, "디딤씨앗통장 중복 가능?");
    float[] queryEmbedding = new float[]{0.1f};
    List<String> extracted = List.of("디딤씨앗통장", "중복");
    List<SimilarChunk> similar = List.of(
            createSimilarChunk(1L, 1L, 0, "표 항목 디딤씨앗통장", 0.5)
    );

    given(keywordBoostProperties.enabled()).willReturn(true);
    given(keywordExtractor.extract("디딤씨앗통장 중복 가능?")).willReturn(extracted);
    given(embeddingProvider.embed("디딤씨앗통장 중복 가능?")).willReturn(queryEmbedding);
    given(policyDocumentRepository.findSimilarByEmbedding(
            eq(1L), eq(queryEmbedding), eq(extracted), eq(10)))
            .willReturn(similar);

    // when
    List<PolicyDocumentChunkResult> result = ragSearchService.searchRelevantChunks(command);

    // then
    assertThat(result).hasSize(1);
    verify(keywordExtractor).extract("디딤씨앗통장 중복 가능?");
}
```

- [ ] **Step 7: 모든 RagSearchService 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.rag.application.service.RagSearchServiceTest"`
Expected: PASS (8 tests — 기존 6 + 신규 2). 회귀 zero.

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java \
        backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java \
        backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java
git commit -m "feat(rag): RagSearchService 에 KeywordExtractor + flag 분기 통합"
```

---

## Task 5: 전체 빌드 + 회귀 검증 + 수동 검증 + spec DONE_ prefix

**Files:**
- Modify: `docs/superpowers/specs/2026-05-11-rag-retrieval-keyword-boost-design.md` → `DONE_2026-05-11-...` 로 rename
- Modify: `docs/superpowers/specs/2026-05-11-rag-retrieval-improvements-design.md` → `DONE_2026-05-11-...` 로 rename (입력 spec 도 사이클 종료 시 함께 처리)

목적: 전체 테스트 회귀 zero 확인 → bootRun 으로 정책 7번 검증 query 수동 실행 → spec DONE_ prefix.

- [ ] **Step 1: 전체 빌드 + 테스트 실행**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL. 전체 테스트 PASS. (Testcontainers 사용 테스트들이 Docker daemon 없으면 skip 또는 fail — 로컬 환경에서 Docker 띄우고 실행)

- [ ] **Step 2: 정책 7번 reindex 환경 준비 (수동 사전조건)**

Run: 직전 사이클 (`DONE_2026-05-11-rag-table-aware-chunking-design.md`) 검증에서 사용한 로컬 DB 그대로 재사용. 만약 정책 7번 청크가 비어있으면, 해당 정책 reindex API 호출 또는 ingestion 재실행. 구체 절차는 직전 사이클 §검증 노트 참고.

확인: `psql` 로 다음 쿼리 결과가 chunk #80, #82~83, #85 등 반환:
```sql
SELECT id, chunk_index, left(content, 60) FROM policy_document
 WHERE policy_id = 7 ORDER BY chunk_index;
```

- [ ] **Step 3: 백엔드 기동 + 프런트 또는 swagger 에서 검증 query 실행**

Run: `cd backend && ./gradlew bootRun`

다음 10개 query 를 정책 7번 컨텍스트에서 Q&A 채팅으로 실행하고 답변과 retrieve 된 청크 로그를 기록:

**표 query (≥4 정답 필요)**:
1. "중복수혜 안되는 통장 리스트 알려줘"
2. "어떤 통장이 중복수혜인지 리스트 알려줘"
3. "디딤씨앗통장 중복 가능한가요?"
4. "꿈나래통장 중복 가능한가요?"
5. "안되는 통장 리스트"

**자연어 query (회귀 없어야)**:
6. "신청 자격이 뭐야?"
7. "지원 금액은 얼마야?"
8. "신청 기간은 언제야?"
9. "어디서 신청해?"
10. "지원 대상은?"

각 query 의 결과를 표로 정리:
| # | Query | 정답/오답 | 핵심 retrieve chunk_index | 비고 |
|---|---|---|---|---|

- [ ] **Step 4: 판정**

판정 기준 (spec §9):
- 표 5개 중 ≥4개 정답 → ✅
- 자연어 5개 모두 회귀 없음 → ✅

둘 다 충족 못하면:
- 키워드 hit 청크가 여전히 top-10 밖 → boost factor 강화 (`0.92/0.85/0.78` → `0.85/0.75/0.65`) 검토 후 Task 3 Step 3 SQL 수정 + 재실행
- 자연어 회귀 발생 → stopword 리스트 보강 또는 maxKeywords 축소 검토

조정 시 변경은 **새 commit** 으로 (이전 작업 amend X).

- [ ] **Step 5: spec DONE_ prefix 적용**

Run:
```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git mv docs/superpowers/specs/2026-05-11-rag-retrieval-keyword-boost-design.md \
       docs/superpowers/specs/DONE_2026-05-11-rag-retrieval-keyword-boost-design.md
git mv docs/superpowers/specs/2026-05-11-rag-retrieval-improvements-design.md \
       docs/superpowers/specs/DONE_2026-05-11-rag-retrieval-improvements-design.md
```

- [ ] **Step 6: 검증 결과 표를 spec 끝에 트러블슈팅 노트로 추가 (선택)**

만약 검증 결과 표 또는 운영 노트(예: 발견된 stopword 누락) 가 후속 사이클에 가치 있다면, `DONE_2026-05-11-rag-retrieval-keyword-boost-design.md` 끝에 `## 14. 검증 결과` 섹션 추가. 단순 정답/오답 결과만이면 PR description 에 적고 spec 변경은 생략 가능.

- [ ] **Step 7: 커밋 + PR 생성**

```bash
git add docs/superpowers/specs/
git commit -m "chore(docs): rag-retrieval-keyword-boost 사이클 DONE_ prefix"
```

PR 생성: 별도 `create-pr` skill 또는 `gh pr create` 로 진행. PR body 에 Task 5 Step 3 의 검증 결과 표 + spec 링크 첨부.

---

## Self-Review 결과

**1. Spec coverage**
- §5.1 KeywordExtractor → Task 2 ✅
- §5.2 KeywordBoostProperties → Task 1 ✅
- §5.3 PolicyDocumentJpaRepository native SQL → Task 3 Step 3 ✅
- §5.4 PolicyDocumentRepositoryImpl → Task 3 Step 2 ✅
- §5.5 PolicyDocumentRepository (도메인 인터페이스) → Task 3 Step 1 ✅
- §5.6 RagSearchService → Task 4 Step 2~3 ✅
- §5.7 application.yml → Task 1 Step 4 ✅
- §6 Native SQL → Task 3 Step 3 (전문 그대로) ✅
- §7 데이터 흐름 → Task 3 Step 7 slice test 가 보장 ✅
- §8 에러/회귀 처리 → Task 3 Step 5~6 (회귀) + Task 4 Step 5 (flag false) ✅
- §8.1 영향 범위 → Task 3 시그니처가 한 메서드만 건드리므로 자동 보장 ✅
- §9 검증 query → Task 5 Step 3 ✅
- §10 테스트 전략 → Task 2 (KeywordExtractor 단위) + Task 3 Step 7 (slice) + Task 4 Step 5~6 (서비스 단위) ✅
- §11 위험 → Task 5 Step 4 의 fallback 절차로 대응 ✅

**2. 자가 검토 — placeholder 없음**: 모든 step 에 실제 코드/명령/예상 결과 포함.

**3. Type 일관성**: `findSimilarByEmbedding(Long, float[], List<String>, int)` 시그니처가 도메인 인터페이스 / impl / JPA / 서비스 / 테스트 전반에서 동일.

**4. Risk note**: Task 3 Step 9 에 `cast(:keywords AS text[])` 변환 실패 시 String[] 우회 절차 명시.

---

## Execution Handoff

플랜을 `docs/superpowers/plans/2026-05-11-rag-retrieval-keyword-boost.md` 에 저장 완료.

실행 옵션 두 가지:

1. **Subagent-Driven (추천)** — task 별 fresh subagent 디스패치, task 간 리뷰. 빠른 반복.
2. **Inline Execution** — 본 세션 내 batch 실행 + 체크포인트 리뷰.

어느 쪽으로 진행할까?
