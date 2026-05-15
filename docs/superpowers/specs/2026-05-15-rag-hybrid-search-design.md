# RAG 하이브리드 검색 (벡터 + Trigram) 디자인

> 작성일: 2026-05-15
> 작성자: TaetaetaE01 (with Claude)
> 모듈: `rag`
> 상태: design

## 1. 배경

현재 RAG QnA 의 retrieval 은 OpenAI `text-embedding-3-small` 기반 코사인 거리 단일 검색이다. 다음 케이스에서 정답 청크를 회수하지 못하는 문제가 보고됨:

- **고유명사·약어·코드 매칭 실패**: "희망저축계좌Ⅰ", "법 제49조", "기준 중위소득 40%" 같이 정확 토큰 매칭이 중요한 질의에서 임베딩 거리가 멀게 측정됨
- **표/숫자 청크 회수 누락**: 표 평탄화로 셀이 공백으로만 구분된 청크 (`2,564,2384,199,292...`) 가 자연어 질문과 임베딩상 멀어 top-K 안에 못 들어옴
- **distance threshold 0.78 컷에 걸림**: top-10 결과의 모든 distance > 0.78 이면 `(본문에서 관련 청크를 찾지 못했습니다.)` 컨텍스트로 LLM 호출 → fallback 답변

임베딩 모델을 키우거나 한국어 특화 모델로 바꾸는 안도 있으나, **추가 비용 거의 없이 키워드 매칭 보완을 얻을 수 있는 BM25-계열(여기서는 pg_trgm) 하이브리드 검색을 먼저 도입**한다.

## 2. 목표 / 비목표

### 목표
- 벡터 검색 + trigram 검색을 **RRF (Reciprocal Rank Fusion)** 로 결합
- Feature flag (`rag.hybrid.enabled`) 로 점진 출시·즉시 롤백 가능
- 기존 벡터 검색 동작·테스트는 변경 없이 보존
- 청크 재생성·재임베딩 불필요

### 비목표 (out of scope)
- 임베딩 모델 교체 (`text-embedding-3-small` 유지)
- 청크 사이즈 변경 (별도 작업 후보)
- OCR / 표 추출기 도입
- 한국어 형태소 분석기 (MeCab/nori) 도입 (인프라 변경 폭이 큼)
- BM25 정밀 알고리즘 (TF saturation, IDF weighting) — `pg_trgm.similarity()` 가 BM25 와 다른 ranking 함수임을 명시. 한국어 trigram 매칭 점수 기반 ranking 으로 사용

## 3. 핵심 결정사항

| 결정 항목 | 선택 | 근거 |
|---------|------|------|
| 토큰화 | **pg_trgm (트라이그램)** | Postgres 17 contrib 기본 탑재 (별도 설치 불필요), 한국어 부분 매칭 강함, GIN 인덱스 지원 |
| 점수 결합 | **RRF (Reciprocal Rank Fusion)** | 점수 스케일 무관, 튜닝 노브 `k` 하나, 산업 표준 |
| 결합 위치 | **애플리케이션 레이어 (`RagSearchService`)** | DDD 원칙 부합, 단위 테스트·디버깅 용이, round trip 1→2 증가는 동일 VPC 라 무시 가능 |
| 롤아웃 | **Feature flag (`rag.hybrid.enabled`)** | 점진 출시·즉시 롤백, CLAUDE.md "작고 되돌리기 쉬운 변경" 원칙 |
| 기존 keyword boost | **유지** | RRF 입력 품질 향상에 기여, 향후 측정 후 제거 여부 결정 |

## 4. 아키텍처

### 4.1 호출 흐름

```
QnaService.processQuestion()
   └─ RagSearchService.searchRelevantChunks(query, embedding)
         ├─ if hybrid.enabled = false  →  [기존] vector-only path (변경 없음)
         └─ if hybrid.enabled = true   →  [신규] hybrid path:
              ├─ PolicyDocumentRepository.findTopByVector(policyId, embedding, topN, keywords)
              ├─ PolicyDocumentRepository.findTopByTrigram(policyId, query, topN, threshold)
              └─ ReciprocalRankFusion.merge(vectorRanks, trigramRanks, k) → top-10
```

### 4.2 컴포넌트 분담

| 컴포넌트 | 책임 |
|---------|------|
| `RagSearchService` (application) | feature flag 분기, 두 검색 호출, RRF 결합, fallback 처리 |
| `PolicyDocumentRepository` (domain) | 인터페이스에 `findTopByTrigram` 메서드 추가 |
| `PolicyDocumentRepositoryImpl` (infra) | trigram 쿼리 native SQL 구현 |
| `PolicyDocumentJpaRepository` (infra) | `@Query nativeQuery` 로 trigram 쿼리 정의 |
| `ReciprocalRankFusion` (domain/service) | RRF 점수 계산 — 순수 함수, DB 의존 없음 |
| `HybridSearchProperties` (infra/config) | `rag.hybrid.*` 설정 바인딩 |

## 5. DB 스키마 변경

### 5.1 마이그레이션 SQL

파일: `backend/src/main/resources/sql/2026-05-16-policy-document-trigram-index.sql` — 운영자가 수동 적용 (Flyway 미사용)

```sql
-- Postgres 17 contrib 기본 탑재
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- content 컬럼 trigram GIN 인덱스
CREATE INDEX IF NOT EXISTS policy_document_content_trgm_idx
    ON policy_document USING GIN (content gin_trgm_ops);
```

- **테이블 스키마 변경 없음**: `content` 컬럼은 이미 존재. 인덱스만 추가
- **청크 재생성 불필요**: `CHUNKER_VERSION` / 임베딩 모델 변경 없음
- **인덱스 빌드 시간**: 현재 데이터 규모(정책 수 × 평균 청크 수) 에서 분 단위로 추정. 무중단 적용이 필요하면 `CREATE INDEX CONCURRENTLY` 로 별도 실행

### 5.2 Testcontainers Postgres 이미지

`pg_trgm` 은 표준 `postgres:17` 이미지에 포함된 contrib 모듈. 별도 이미지 변경 불필요.

## 6. 검색 알고리즘

### 6.1 Vector top-N 쿼리 (기존 재사용)

`PolicyDocumentJpaRepository.findSimilarByEmbedding()` 의 `LIMIT` 만 `top-n-per-search` (기본 20) 로 호출. keyword boost 는 그대로 적용.

### 6.2 Trigram top-N 쿼리 (신규)

GIN trigram 인덱스 (`gin_trgm_ops`) 는 `<->`, `%`, `<%>` 같은 operator 형태에서 가속된다. 그래서 `<->` distance operator + LIMIT 형태로 KNN 조회하고, threshold 컷은 application 레이어에서 후처리한다.

```java
@Query(value = """
        SELECT id, policy_id AS policyId, chunk_index AS chunkIndex, content,
               attachment_id AS attachmentId, page_start AS pageStart, page_end AS pageEnd,
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

- `<->` operator 가 GIN 인덱스 KNN 스캔을 통해 정렬되고 LIMIT 으로 컷됨
- threshold 컷은 `PolicyDocumentRepositoryImpl` 의 stream filter 단계에서 수행

### 6.3 RRF 결합

```java
public class ReciprocalRankFusion {
    /**
     * 두 ranked list 를 RRF 점수로 결합한다.
     * RRF_score(chunk) = Σ 1 / (k + rank_in_each_search)
     *
     * @param vectorRanks   벡터 검색 결과 (rank 0 = 가장 가까움)
     * @param trigramRanks  trigram 검색 결과 (rank 0 = 가장 유사)
     * @param k             RRF 상수 (산업 표준 60)
     * @param topK          최종 반환 청크 수
     * @return RRF 점수 내림차순 정렬된 top-K 청크
     */
    public List<PolicyDocumentChunkResult> merge(
            List<PolicyDocumentChunkResult> vectorRanks,
            List<PolicyDocumentChunkResult> trigramRanks,
            int k,
            int topK
    ) { ... }
}
```

- 청크 식별자: `policy_document.id` (Long)
- 같은 청크가 양쪽에 들어가면 두 점수 합산
- 한쪽에만 들어가면 한쪽 점수만
- 최종 `topK = 10` (기존 `DEFAULT_TOP_K` 와 동일하게 유지하여 LLM 컨텍스트 부담 변동 없음)

### 6.4 distance 호환성

`PolicyDocumentChunkResult.distance` 필드는 후속 QnA 의 `relevanceDistanceThreshold` 컷에 사용됨 (`QnaService.java:184`). RRF 점수는 distance 와 단위가 다르므로 다음 규칙으로 채운다:

- **vector top-N 에 들어온 청크**: vector 쿼리의 원래 distance 값 그대로 사용
- **trigram 단독 매칭 청크** (vector top-N 에 없음): `distance = 1.0 - similarity` 로 변환해 채움 (similarity 0~1 → distance 0~1 의 동일 스케일로 정렬). 결과적으로 trigram similarity 가 매우 높으면 (예: 0.9) distance ≈ 0.1 로 채워져 후속 threshold 0.78 컷을 통과
- **양쪽 모두 매칭**: vector distance 우선 사용 (벡터 기준 거리가 더 의미적으로 안정)

이 규칙은 후속 QnA threshold 로직을 건드리지 않으면서 trigram 단독 매칭 청크도 컷에 살아남게 해준다. 향후 hybrid mode 전용 threshold (예: RRF 점수 기반) 로 컷 로직 자체를 교체하는 것은 이번 작업 범위 외.

## 7. 설정

`application.yml`:

```yaml
rag:
  hybrid:
    enabled: ${RAG_HYBRID_ENABLED:false}
    top-n-per-search: ${RAG_HYBRID_TOP_N:20}
    rrf-k: ${RAG_HYBRID_RRF_K:60}
    trigram-threshold: ${RAG_HYBRID_TRIGRAM_THRESHOLD:0.1}
```

`HybridSearchProperties` (Java record, `@ConfigurationProperties("rag.hybrid")`)

## 8. 기존 keyword boost 와의 관계

`PolicyDocumentJpaRepository.findSimilarByEmbedding()` SQL 안의 `hit_count` 기반 distance boost (`× 0.78~0.92`) 는 **유지**한다.

근거:
- BM25(trigram) 검색이 별도 path 로 들어가므로 중복 동작이 아님
- vector 검색 안에서도 keyword 일치 청크가 우대되면 RRF 입력 품질이 좋아짐
- 향후 `keyword boost on/off × hybrid on/off` 4가지 조합 비교로 정량 평가 후 제거 여부 결정

## 9. 에러 처리 / 폴백

| 케이스 | 동작 |
|--------|------|
| `hybrid.enabled = false` | 기존 vector-only path (코드 분기로 안전 가드) |
| trigram 쿼리 예외 발생 | warning 로그 + vector 결과만으로 반환 (graceful degradation) |
| trigram 결과 0건 | vector 결과만 RRF 입력으로 사용 (정상 흐름) |
| vector + trigram 모두 0건 | 기존 `fallbackKeywordSearch` 동작 (변경 없음) |

## 10. 테스트 전략

### 10.1 단위 테스트
- `ReciprocalRankFusionTest`
  - 양쪽 모두 같은 청크 → 점수 합산 확인
  - 한쪽에만 있는 청크 → 한쪽 점수만
  - 정렬 순서 검증 (RRF 점수 내림차순)
  - `topK` 컷 동작
  - 빈 list / null 입력 방어
- `RagSearchServiceTest` 보강
  - `hybrid.enabled = false` → 기존 동작 그대로 (회귀 방지)
  - `hybrid.enabled = true` → repository 양쪽 호출 + RRF merge 호출 확인 (mock)
  - trigram 쿼리 예외 발생 → vector 결과로 폴백

### 10.2 슬라이스 테스트
- `PolicyDocumentRepositoryTrigramTest` (`@DataJpaTest` + testcontainers Postgres + pg_trgm)
  - `findTopByTrigram` 이 trigram threshold 이상 매칭 청크만 반환
  - similarity 내림차순 정렬
  - GIN 인덱스 활용 (explain 검증은 선택)

### 10.3 통합 테스트
- 기존 RAG/QnA 통합 테스트 슈트가 `hybrid.enabled = false` (기본값) 에서 그대로 통과해야 함
- 별도 `hybrid.enabled = true` 케이스 1~2개 추가

## 11. 메트릭 / 관측

`RagSearchService` 에 다음 로그 추가:

```
hybrid 검색: policyId=X, query="...",
  vector_top20=[distance0=0.21, ...],
  trigram_top20=[sim0=0.45, ...],
  merged_top10=[rrf=0.0345, rrf=0.0289, ...]
```

향후 A/B 비교용으로 vector-only 결과도 debug 레벨로 병행 출력 (선택).

LLM 비용 메트릭은 영향 없음 (임베딩 1회 호출 동일).

## 12. 마이그레이션 / 배포 순서

1. **PR 1 (이 spec 기반)**: 코드 + Flyway 마이그레이션, `hybrid.enabled` 기본값 `false`
2. **로컬·스테이징 검증**: `hybrid.enabled=true` 환경변수 설정, 평가 셋(자주 묻는 질문 10~20개)으로 retrieval@10 측정
3. **운영 출시**: 환경변수로 `RAG_HYBRID_ENABLED=true` 전환 (코드 배포 없이)
4. **회귀 발견 시**: 환경변수 `false` 로 즉시 롤백, 다음 작업 사이클에서 원인 분석

## 13. 향후 작업 후보 (이번 범위 외)

- 평가 셋 기반 retrieval@10 정량 측정 인프라
- keyword boost 제거 비교 측정
- BM25 정밀 알고리즘 (`pg_search` extension 또는 자체 구현) 도입 검토
- 한국어 형태소 분석기 도입 (인프라 변경 필요)
- 임베딩 모델 교체 (Solar / BGE-M3 등)
- 청크 사이즈 튜닝 (500 → 800)
