# RAG Retrieval Keyword Boost Design

> **궁극 목표**: 키워드가 정확히 들어있는 청크를 retrieval 우선순위에서 잡는다.
> 직전 입력 spec(`2026-05-11-rag-retrieval-improvements-design.md`)에서 좁힌 옵션 R5 (Keyword-aware boost) 를 구체 설계로 확정한다.

## 1. 배경

### 1.1 직전 사이클 결과

PR #91 (rag-table-aware-chunking) 로 chunking 단의 의미 단위 보존은 완료. 그러나 retrieval 단에서 표 청크가 `text-embedding-3-small` 자연어 query 와 cosine distance 0.6+ 로 멀어 top-10 진입 실패.

| Query | 결과 | 원인 |
|---|---|---|
| 디딤씨앗통장 중복 가능? | ❌ 환각 | chunk #85 (정답) top-10 밖 |
| 꿈나래통장 중복 가능? | ❌ fallback | 동일 |
| 중복수혜 안되는 통장 리스트 | ❌ fallback | chunk #82~83 top-10 밖 |
| 지원 금액은 얼마야? | ✅ 정답 | 자연어 매칭, 회귀 없음 |

### 1.2 현재 retrieval 구조 (변경 대상)

`RagSearchService.searchRelevantChunks()` → `PolicyDocumentJpaRepository.findSimilarByEmbedding` native query → pgvector `<=>` cosine distance 기반 top-10. fallback 은 "vector 결과 0건일 때만" 동작 → 정책 7번처럼 자연어 청크 10개 잡히면 표 청크는 영원히 진입 불가.

### 1.3 결정 요약 (입력 spec §5 → 본 spec)

| 결정 항목 | 채택 | 비고 |
|---|---|---|
| 메인 옵션 | R5 Keyword boost | 입력 spec §4 R1~R6 중 |
| 한국어 토큰화 | 정규식 `[가-힣A-Za-z0-9]{2,}` | mecab/stemmer 미사용 |
| 점수 결합 | Multiplicative distance boost | RRF/over-fetch 미채택 |
| 구현 위치 | SQL native query 안 | over-fetch 회피 |
| Stopword | 소형 리스트 (10~15개) | application.yml expose |
| Boost factor | 0/1/2/3+ hit 단계 함수 | 1.0/0.92/0.85/0.78 |
| Rollout | feature flag (기본 enabled) | 회귀 시 즉시 토글 |
| 검증 | 수동 (직전 사이클 query 5+5) | 자동 벤치 미구축 |

## 2. 목표

- 정책 7번 검증 query 5개 (§9.1) 중 ≥4개 정답
- 자연어 query 5개 (§9.2) 모두 회귀 없음
- 키워드 추출/boost 로직이 비활성화 시 (feature flag false 또는 keywords 빈 리스트) 기존 retrieval 결과와 100% 동일

## 3. 비범위

- 한국어 형태소 분석기 도입 (mecab-ko, Lucene Korean Analyzer 등)
- BM25 / tsvector 인덱스 마이그레이션 (R1 옵션 — 후속 사이클 후보)
- LLM 기반 query rewriting / multi-query (R2/R3)
- Cross-encoder re-ranking
- 자동화 retrieval 벤치마크 인프라

## 4. 아키텍처

```
QnaService (변경 없음)
  └─> RagSearchService.searchRelevantChunks(command, embedding)
        ├─> KeywordExtractor.extract(query)         [신규]
        │     - 정규식 토큰 추출
        │     - stopword 필터
        │     - max-keywords 상한
        └─> PolicyDocumentRepository.findSimilarByEmbedding(
              policyId, embedding, keywords, top-K)
              └─> native SQL                           [수정]
                    - hit_count = unnest(:keywords) ILIKE 매칭 수
                    - boosted_distance = distance × CASE(hit_count)
                    - ORDER BY boosted_distance LIMIT :limit
```

## 5. 변경 컴포넌트

### 5.1 신규: `rag/domain/service/KeywordExtractor`

순수 도메인 서비스 (Spring 의존 없음).

```java
public class KeywordExtractor {
    private final Pattern tokenPattern = Pattern.compile("[가-힣A-Za-z0-9]{2,}");
    private final Set<String> stopwords;
    private final int maxKeywords;

    public List<String> extract(String query) { ... }
}
```

- 입력: query 문자열
- 출력: 길이 ≥2 토큰 → stopword 제거 → 중복 제거 → 최대 `maxKeywords` 개
- query null/blank → 빈 리스트
- 단위 테스트로 한글/영문/숫자/혼용/stopword/길이 상한 케이스 검증

### 5.2 신규: `rag/infrastructure/config/KeywordBoostProperties`

`@ConfigurationProperties("qna.keyword-boost")` 로 stopword/max-keywords/enabled 바인딩.

### 5.3 수정: `PolicyDocumentJpaRepository.findSimilarByEmbedding`

시그니처에 `List<String> keywords` 추가. native SQL 본문은 §6 참고. 기존 호출부 (없음 — `RagSearchService` 만 호출) 호환 깨지지 않게 단일 메서드 시그니처 변경.

### 5.4 수정: `PolicyDocumentRepositoryImpl.findSimilarByEmbedding`

`keywords` 인자 그대로 전달. 빈 리스트면 `Collections.emptyList()` 그대로 — SQL 단계에서 `:keywordCount = 0` 분기로 회귀 zero 보장.

### 5.5 수정: `PolicyDocumentRepository` (도메인 인터페이스)

```java
List<SimilarChunk> findSimilarByEmbedding(
    Long policyId,
    float[] queryEmbedding,
    List<String> keywords,
    int limit
);
```

기존 시그니처 (keywords 없음) 는 deprecate 하지 않고 교체 — `RagSearchService` 외 호출처 없음.

### 5.6 수정: `RagSearchService.searchRelevantChunks(command, precomputedEmbedding)`

```java
List<String> keywords = boostEnabled
        ? keywordExtractor.extract(command.query())
        : List.of();

List<SimilarChunk> similar = policyDocumentRepository.findSimilarByEmbedding(
        command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);
```

로그에 `keywords=[...]`, `boosted top-10 distances=[...]` 추가하여 운영 가시성 확보. `boostEnabled` 는 `KeywordBoostProperties.isEnabled()` 조회.

### 5.7 신규 설정: `application.yml`

```yaml
qna:
  keyword-boost:
    enabled: ${QNA_KEYWORD_BOOST_ENABLED:true}
    max-keywords: 5
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

Boost factor 자체 (1.0/0.92/0.85/0.78) 는 SQL CASE 안에 상수. 운영 튜닝 필요해지면 후속 사이클에서 properties 로 이전.

## 6. Native SQL

```sql
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
```

설계 포인트:
- `base` CTE 에서 pgvector `<=>` 연산 1회만 수행 (cost 보존)
- `keywords` 가 빈 배열이면 `unnest` 가 0 row 반환 → `hit_count = 0` → CASE ELSE 분기 → boosted == raw distance 로 회귀 zero. 별도 `:keywordCount` 파라미터 불필요
- `unnest + ILIKE` 는 GIN/btree 인덱스 못 타지만, top-K 산출 직전 base CTE 이미 row 수 = 정책 1건의 청크 수 (보통 < 200) 라 비용 작음
- 반환 컬럼명 `distance` 는 boosted 값. 호출 측 (`PolicyDocumentRepositoryImpl.toSimilarChunk`) 시그니처 호환
- JPA native query 에서 `List<String>` → PostgreSQL `text[]` 변환은 명시 캐스트 (`cast(:keywords AS text[])`) 필요. Hibernate 6 기준 `org.hibernate.type.SqlTypes.ARRAY` 또는 `unnest(?)` 위치에서 직접 풀어 전달하는 패턴 검토 (구현 시 결정)

## 7. 데이터 흐름 (정책 7번 검증 시나리오)

```
query: "디딤씨앗통장 중복 가능?"
  └─> KeywordExtractor → ["디딤씨앗통장", "중복"]   (가능 = stopword)
  └─> embedding(query) → vec
  └─> SQL:
        chunk #80 raw=0.42, hit("중복")=1     → boosted=0.42×0.92=0.386
        chunk #82 raw=0.58, hit=2              → boosted=0.493
        chunk #85 raw=0.62, hit=2              → boosted=0.527
        chunk #93 raw=0.55, hit=0              → boosted=0.55
        ...
        → top-10 안에 chunk #85 진입 보장
```

자연어 query (`"신청 자격이 뭐야?"`) 는 `["신청", "자격"]` 정도 추출 (`뭐` stopword). 정책 본문 청크들이 모두 동일 정도로 hit 잡혀 ranking 의 상대 순서 거의 유지. 회귀 위험 작음.

## 8. 에러 / 회귀 처리

- `KeywordExtractor.extract` 결과 빈 리스트 → SQL 의 `unnest` 0 row → `hit_count = 0` → CASE ELSE → 자연어 query 동작 동일
- feature flag `false` → 빈 keywords 전달 → 동일하게 회귀 zero
- 기존 fallback (`fallbackKeywordSearch` — vector 결과 0건 시 substring 매칭) 은 그대로 유지
- SQL 호환성: PostgreSQL 13+ 의 `unnest(text[])` + `ILIKE` 는 표준. pgvector 와 무관

## 8.1 영향 범위 (다른 임베딩 사용처 안전성)

이번 변경은 `policy_document` 테이블의 RAG 청크 retrieval 한 경로만 수정. 임베딩을 사용하는 다른 시스템은 무영향.

**영향 받는 호출 체인**:
- `QnaService.handleQuestion` → `RagSearchService.searchRelevantChunks(command, embedding)` → `PolicyDocumentRepository.findSimilarByEmbedding(policyId, embedding, keywords, top-K)`

**무영향 (그대로 동작)**:
- **임베딩 생성** — `OpenAiEmbeddingClient`, `EmbeddingProvider.embed()` 모델/차원/호출 방식 변경 없음
- **Indexing** — `RagIndexingService` 가 청크 저장 시 `embedding` 컬럼 채우는 로직 변경 없음
- **Semantic Q&A 캐시** — `PgVectorSemanticQnaCache` 는 별도 테이블 `qna_question_cache` 에 자체 native query (`QnaQuestionCacheJpaRepository:20-21` 의 `<=>`) 사용. 본 변경은 `policy_document` 만 건드리므로 캐시 lookup/put 흐름 영향 zero
- **QnaService 의 query 임베딩 단일 생성** — `embeddingProvider.embed(question)` 1회 호출 후 RAG retrieval 과 semantic cache 양쪽 재사용하는 기존 패턴 유지

**테스트 손볼 범위**:
- `RagSearchServiceTest` — `findSimilarByEmbedding` mock 시그니처 변경 반영
- `QnaServiceTest` — `RagSearchService` mock 사용. 메서드 시그니처는 application 레이어에서 안 바뀌므로 verify 호출만 영향
- `RagIndexingServiceTest` — indexing 전용. 무관

## 9. 검증 query (직전 사이클과 동일)

### 9.1 표 관련 (정답 있어야)

- "중복수혜 안되는 통장 리스트 알려줘"
- "어떤 통장이 중복수혜인지 리스트 알려줘"
- "디딤씨앗통장 중복 가능한가요?"
- "꿈나래통장 중복 가능한가요?"
- "안되는 통장 리스트"

### 9.2 자연어 (회귀 없어야)

- "신청 자격이 뭐야?"
- "지원 금액은 얼마야?"
- "신청 기간은 언제야?"
- "어디서 신청해?"
- "지원 대상은?"

**판정 기준**: 표 5개 중 ≥4개 정답 + 자연어 5개 회귀 없음.

## 10. 테스트 전략

- **단위**: `KeywordExtractorTest`
  - 한글/영문/숫자 혼용 토큰 추출 ("30만원", "디딤씨앗통장", "GPT-4")
  - stopword 필터링
  - max-keywords 상한
  - null/blank query → 빈 리스트
  - 중복 제거
- **Slice (Repository)**: `PolicyDocumentRepositoryImplTest` — Testcontainers PostgreSQL + pgvector
  - keywords 빈 리스트 시 기존 ranking 100% 동일 (회귀 검증)
  - 키워드 hit 청크가 distance 우위 청크보다 boost 후 위로 오는 시나리오
- **수동 검증**: 정책 7번 reindex 후 §9 query 10개 직접 채팅. 결과 markdown 표로 PR 본문에 첨부

## 11. 위험 / 사전 검토

- **stopword 누락**: 운영 중 새 stopword 발견 시 `application.yml` 수정 + 재배포 필요. 후속 사이클에서 admin DB 로 이관 검토 가능
- **합성어 경계**: "디딤씨앗통장" 처럼 띄어쓰기 없이 들어오는 도메인 합성어는 정규식 한 토큰으로 잡힘 (다행). 그러나 "디딤 씨앗 통장" 처럼 띄어 입력하면 `["디딤", "씨앗", "통장"]` 로 분리 → 청크 본문 표기와 불일치 가능. 한국어 query 운영 패턴에서 빈도 낮을 것으로 가정
- **noisy hit (일반어)**: "지원", "금액", "대상" 같은 일반어가 거의 모든 청크에 있어 boost 효과 희석. 단 모든 청크가 동일하게 boost 되면 상대 ranking 유지 → 자연어 query 회귀 위험은 낮음
- **boost factor 튜닝**: 초기값 (0.92/0.85/0.78) 은 정책 7번 distance 분포 기준 추정. 운영 로그에서 distance 분포 확인 후 조정 필요할 수 있음

## 12. 관련 코드 / 파일

**변경**:
- `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java`
- `backend/src/main/java/com/youthfit/rag/domain/repository/PolicyDocumentRepository.java`
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java`
- `backend/src/main/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentJpaRepository.java`
- `backend/src/main/resources/application.yml`

**신규**:
- `backend/src/main/java/com/youthfit/rag/domain/service/KeywordExtractor.java`
- `backend/src/main/java/com/youthfit/rag/infrastructure/config/KeywordBoostProperties.java`
- `backend/src/test/java/com/youthfit/rag/domain/service/KeywordExtractorTest.java`
- `backend/src/test/java/com/youthfit/rag/infrastructure/persistence/PolicyDocumentRepositoryImplTest.java` (slice)

**무변경 / 참조**:
- `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`
- `backend/src/main/java/com/youthfit/rag/domain/service/DocumentChunker.java`

## 13. 참고

- 입력 spec: `docs/superpowers/specs/2026-05-11-rag-retrieval-improvements-design.md`
- 직전 chunking 사이클: `docs/superpowers/specs/DONE_2026-05-11-rag-table-aware-chunking-design.md`
- 직전 사이클 PR: #91 (rag-table-aware-chunking)
- 모체 사이클: `docs/superpowers/specs/DONE_2026-05-11-qna-rich-answer-design.md`
