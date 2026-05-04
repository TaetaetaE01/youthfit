# 의미 기반 Q&A 캐시 (Semantic Cache) 설계

- **작성일**: 2026-05-01
- **대상 모듈**: `backend/qna`, `backend/rag`
- **범위**: 정책 Q&A에서 글자 단위 정확 일치 캐시(Redis) 외에 **의미 기반 캐시(pgvector)**를 추가해 의미적으로 동일한 질문에 LLM 호출 없이 응답한다.

## 1. 배경 / 문제

현재 `QnaService.processQuestion`은 정확 일치 캐시만 사용한다.

- 캐시 키: `qna:answer:{policyId}:{SHA-256(normalize(question))}`
- `normalize`는 trim + lowercase + 공백 정리만 수행
- "신청 가능?" / "신청할 수 있나요?" 처럼 **의미는 같지만 글자가 다른 질문은 모두 LLM 호출이 발생**

이로 인해

- LLM 호출 비용이 의미 중복만큼 낭비된다.
- 사용자 입장에서 비슷한 질문에 매번 수 초의 스트리밍 대기가 발생한다.

## 2. 목표 / 비목표

### 2.1 목표
- 의미가 충분히 가까운 (코사인 거리 ≤ 0.15) 이전 질문이 있으면 **저장된 답변과 sources를 그대로 스트림 응답**한다.
- 추가 OpenAI 비용은 **사실상 0**이어야 한다 (RAG가 어차피 호출하던 임베딩을 재사용).
- **잘못된 답 재사용 위험 최소화** — 정책 본문이 갱신되면 그 정책의 의미 캐시는 즉시 비운다.
- 캐시 장애가 사용자 응답을 깨지 않는다 (실패 시 LLM 흐름으로 폴백).

### 2.2 비목표
- 정확 일치 캐시(Redis)는 그대로 둔다. 무효화 정책도 변경하지 않는다 (TTL 24h 유지).
- semantic cache용 별도 임베딩 모델·차원 변경은 하지 않는다 (RAG와 동일한 1536차원 OpenAI 임베딩 사용).
- 사용자 컨텍스트(프로필, 적합도)에 따라 답이 달라지는 케이스는 다루지 않는다 — 현재 답변은 정책 원문 청크 기반으로 사용자 무관.
- 이벤트 드리븐 아키텍처는 도입하지 않는다 (루트 `CLAUDE.md` v0 제외 항목).

## 3. 결정된 설계 옵션

| 결정 | 선택 | 비고 |
|---|---|---|
| 저장소 | **pgvector + 새 테이블 `qna_question_cache`** | Redis Stack 도입 부담 회피, 정책 본문 갱신과 트랜잭션 묶기 쉬움 |
| 매칭 임계값 | **코사인 거리 ≤ 0.15** (튜닝 가능) | `QnaProperties.semanticDistanceThreshold`로 노출 |
| 무효화 | **의미 캐시는 정책 인덱싱 갱신 시 즉시 무효화 + TTL 24h 안전망** | 정확 캐시는 v0에선 TTL에만 의존 |
| 모듈 경계 | **`QnaCacheInvalidator` application port** + 같은 트랜잭션 호출 | rag → qna application 의존. 이벤트 미사용 |
| 임베딩 | **질문 임베딩 1회 호출 후 의미 캐시·RAG 검색에 모두 재사용** | 추가 토큰 비용 사실상 0 |

## 4. 흐름 (After)

```
질문 도착
  ├─ ① 정확 일치 캐시 조회 (Redis, 기존)  ──── 히트 → 스트림 응답, 종료
  │
  ├─ ② 임베딩 1회 호출 (EmbeddingProvider.embed)
  │
  ├─ ③ 의미 캐시 조회 (pgvector, 신규)
  │     SELECT ... WHERE policy_id = ? AND created_at >= now() - 24h
  │       ORDER BY embedding <=> :q LIMIT 1
  │     distance ≤ 0.15 ──── 히트 → 스트림 응답, 종료
  │
  ├─ ④ RAG 청크 검색 (같은 임베딩 재사용)
  │
  ├─ ⑤ LLM 호출 (스트리밍)
  │
  └─ ⑥ 결과 저장
        - Redis (정확 캐시, 기존)
        - pgvector (의미 캐시, 신규)
```

핵심 불변 조건:
- **정확 캐시 히트 시에는 임베딩을 호출하지 않는다** (현재 동작과 동일, 추가 비용 없음).
- **임베딩은 정확 캐시 미스 후 정확히 1번만 호출**되며, 의미 캐시 조회와 RAG 청크 검색에 모두 재사용된다.

## 5. 컴포넌트 분해

### 5.1 신규 — `qna` 모듈

**domain**
- `QnaQuestionCache` (Entity, `BaseTimeEntity` 상속)
  - 필드: `id`, `policyId`, `questionText`, `embedding` (`vector(1536)`), `answer`, `sourcesJson`
  - public setter 없음. `@Builder`만 노출
- `QnaQuestionCacheRepository` (도메인 인터페이스)
  - `Optional<SimilarCachedAnswer> findClosestByPolicyId(Long policyId, float[] embedding, Duration ttl)`
  - `void save(QnaQuestionCache entity)`
  - `void deleteByPolicyId(Long policyId)`
- `SimilarCachedAnswer` (값 객체) — `CachedAnswer` + `double distance`

**application/port**
- `SemanticQnaCache`
  - `Optional<CachedAnswer> findSimilar(Long policyId, float[] embedding)` — 임계값/TTL 적용 후 결과만 반환
  - `void put(Long policyId, String question, float[] embedding, CachedAnswer answer)`
- `QnaCacheInvalidator`
  - `void invalidatePolicy(Long policyId)` — 의미 캐시만 비움 (정확 캐시는 TTL 유지)

**application/service**
- `DefaultQnaCacheInvalidator implements QnaCacheInvalidator`
  - `QnaQuestionCacheRepository.deleteByPolicyId(policyId)` 호출

**infrastructure**
- `PgVectorSemanticQnaCache implements SemanticQnaCache`
  - `QnaQuestionCacheRepository`와 `QnaProperties.semanticDistanceThreshold`, `QnaProperties.cacheTtlHours`를 사용
  - `findClosest`로 받은 `distance`가 임계값 이하일 때만 `Optional.of(...)` 반환
- `QnaQuestionCacheJpaRepository`
- `QnaQuestionCacheRepositoryImpl`
  - 네이티브 쿼리로 코사인 거리(`<=>`) 정렬 + LIMIT 1
  - `created_at >= now() - (:ttlHours || ' hours')::interval` 필터로 TTL 적용

**config**
- `QnaProperties`에 필드 추가
  - `double semanticDistanceThreshold` (기본 0.15)
  - 기존 `cacheTtlHours` 재사용

### 5.2 변경 — `qna` 모듈

**`QnaService.processQuestion`**
- `EmbeddingProvider`, `SemanticQnaCache` 의존 추가
- 흐름:
  1. `qnaAnswerCache.get(...)` — 기존
  2. 미스 시 `float[] embedding = embeddingProvider.embed(question)`
  3. `semanticQnaCache.findSimilar(policyId, embedding)` → 히트면 `sendCachedAnswer(...)` 후 종료
  4. 미스 시 `ragSearchService.searchRelevantChunks(command, embedding)` (오버로드 — 사전 계산된 임베딩 전달)
  5. 임계값(`relevanceDistanceThreshold`) 통과 청크가 없으면 기존 `NO_RELEVANT_MESSAGE` 흐름
  6. LLM 호출 → 스트림
  7. 정상 종료 시 `qnaAnswerCache.put(...)` + `semanticQnaCache.put(policyId, question, embedding, answer)`

### 5.3 변경 — `rag` 모듈

**`RagSearchService`**
- 오버로드 메서드 추가: `List<PolicyDocumentChunkResult> searchRelevantChunks(SearchChunksCommand command, float[] precomputedEmbedding)`
  - 기존 단일 인자 메서드는 내부에서 `embeddingProvider.embed`를 부른 뒤 신규 오버로드를 호출하도록 리팩터링
  - `precomputedEmbedding`이 주어지면 임베딩 호출을 생략하고 그대로 검색
  - 키워드 폴백 동작은 그대로 유지 (`SearchChunksCommand.query`가 그대로 들어오므로)
  - 내부 변경만 있고 기존 호출자(`QnaService` 외 사용처)에는 영향 없음

**`RagIndexingService.indexPolicyDocument`**
- `QnaCacheInvalidator` 주입 추가
- `sourceHash`가 변경되어 실제로 재인덱싱하는 분기에서 `qnaCacheInvalidator.invalidatePolicy(policyId)` 호출
- 같은 `@Transactional` 안에서 실행 → 인덱싱 트랜잭션 롤백 시 캐시 비움도 롤백되어 정합성 유지

## 6. 데이터 모델 / 스키마

### 6.1 새 테이블

```sql
CREATE TABLE qna_question_cache (
    id            BIGSERIAL PRIMARY KEY,
    policy_id     BIGINT       NOT NULL,
    question_text TEXT         NOT NULL,
    embedding     vector(1536) NOT NULL,
    answer        TEXT         NOT NULL,
    sources_json  JSONB        NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_qna_question_cache_policy
    ON qna_question_cache (policy_id);

CREATE INDEX idx_qna_question_cache_embedding
    ON qna_question_cache
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

운영 환경(`ddl-auto: validate`)이라 위 DDL은 수동 적용 필요. 프로젝트에 Flyway가 없으므로 기존 정책과 동일하게 운영자가 PG에 직접 적용한다 (배포 노트에 명시).

### 6.2 JPA 매핑

`PolicyDocument`와 동일 패턴 사용:
```java
@JdbcTypeCode(SqlTypes.VECTOR)
@Array(length = 1536)
@Column(name = "embedding", columnDefinition = "vector(1536)")
private float[] embedding;
```

### 6.3 조회 쿼리 (네이티브)

```sql
SELECT id, answer, sources_json,
       embedding <=> :q AS distance
FROM qna_question_cache
WHERE policy_id = :policyId
  AND created_at >= now() - make_interval(hours => :ttlHours)
ORDER BY embedding <=> :q
LIMIT 1;
```

호출자에서 `distance ≤ semanticDistanceThreshold`만 히트로 채택.

## 7. 에러 처리

| 단계 | 실패 시 동작 |
|---|---|
| 의미 캐시 `findSimilar` 실패 (DB 장애 등) | `log.warn` 후 RAG → LLM 정상 흐름. 사용자 응답 영향 없음 |
| 의미 캐시 `put` 실패 | `log.warn`. 이미 스트림 응답은 완료된 상태 |
| `EmbeddingProvider.embed` 실패 | 기존 RAG 흐름과 동일하게 LLM 호출 단계에서 실패 처리 (`LLM_ERROR`) |
| `RagIndexingService` 트랜잭션 안에서 `invalidatePolicy` 실패 | 트랜잭션 롤백 → 인덱싱 자체 실패. 본문/캐시 불일치 위험 차단 |

원칙: **캐시는 최적화일 뿐, 정확성에 의존하지 않는다.** 캐시 부재 = 옛 시스템과 동일 동작.

## 8. 테스트 전략

### 8.1 단위
- `QnaServiceTest`
  - 정확 캐시 히트 시 임베딩 호출 **0회**, 의미 캐시 조회 0회, LLM 호출 0회
  - 정확 캐시 미스 → 의미 캐시 히트 시 임베딩 호출 1회, LLM 호출 0회
  - 정확 캐시 미스 → 의미 캐시 미스 시 임베딩 호출 1회, LLM 호출 1회 (RAG 호출에도 동일한 벡터가 전달되는지 검증)
  - 의미 캐시 `findSimilar` 예외 시 RAG 흐름으로 폴백
- `DefaultQnaCacheInvalidatorTest`
  - `invalidatePolicy(policyId)` 호출 시 `deleteByPolicyId` 호출
- `QnaProperties`
  - `semanticDistanceThreshold` 기본값 0.15

### 8.2 슬라이스 / 통합
- `@DataJpaTest` + Testcontainers(pgvector)
  - 코사인 거리 정렬과 `policy_id` 스코프 격리 (정책 A 임베딩이 정책 B 검색에 끌리지 않음)
  - TTL 필터: `created_at`이 24h 이전인 행은 결과에서 제외
- `RagIndexingServiceTest`
  - 정책 본문이 갱신되면 (`sourceHash` 변경) 같은 트랜잭션에서 `qna_question_cache` 행이 비워짐
  - 인덱싱 트랜잭션이 롤백되면 캐시도 비워지지 않음

### 8.3 E2E (선택)
- `QnaController` SSE 응답에서 의미 캐시 히트 시 `QnaLlmProvider.generateAnswer` 호출 0회

## 9. 관찰성

운영 튜닝을 위해 최소한의 로그만 추가한다 (별도 메트릭 인프라는 v0 범위 밖).

- 의미 캐시 히트 시: `log.info("Q&A 의미 캐시 히트: policyId={}, distance={}", policyId, distance)`
- 의미 캐시 미스 시 (가장 가까운 후보의 distance가 존재할 때): `log.info("Q&A 의미 캐시 미스: policyId={}, closestDistance={}", policyId, closestDistance)`

→ 운영 후 distance 분포를 보고 임계값을 조정한다.

## 10. 영향 / 리스크

- **정확성 리스크**: 임계값(0.15)이 느슨해서 의미가 살짝 다른 질문에 같은 답을 재사용할 가능성 — 운영 로그로 모니터링하고 보수적으로 시작 (필요 시 0.10~0.12로 조정).
- **DDL 적용 누락 리스크**: Flyway가 없으므로 운영 환경 DDL을 수동 적용해야 함. 배포 체크리스트에 명시.
- **임베딩 차원 변경 리스크**: RAG가 사용하는 임베딩 모델 차원이 바뀌면 `qna_question_cache.embedding` 차원도 함께 바꿔야 함. 임시로는 테이블 truncate 후 마이그레이션. (현재는 1536으로 고정)
- **성능**: 의미 캐시 행이 정책당 수십~수백 건 미만이면 ivfflat 없이도 빠름. 인덱스는 안전망.

## 11. 작업 분해 (구현 단계용 시드)

세부 단계는 `writing-plans` 단계에서 확정. 대략적인 슬라이스:

1. `QnaQuestionCache` Entity + Repository + JPA 매핑 + 마이그레이션 SQL 작성
2. `SemanticQnaCache` port + `PgVectorSemanticQnaCache` 구현 + `QnaProperties.semanticDistanceThreshold` 추가
3. `QnaCacheInvalidator` port + 구현
4. `RagSearchService.searchByEmbedding` (또는 오버로드) 추가
5. `QnaService.processQuestion` 흐름 변경 (임베딩 1회 + 의미 캐시 단계 삽입)
6. `RagIndexingService.indexPolicyDocument`에 invalidate 호출 추가
7. 단위/슬라이스 테스트
8. 운영 DDL 적용 노트 작성 (`docs/OPS.md`)
