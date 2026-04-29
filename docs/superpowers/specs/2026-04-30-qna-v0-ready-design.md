# Q&A v0 출시 품질 마무리 — Design

> **날짜**: 2026-04-30
> **모듈**: `com.youthfit.qna` (메인), `com.youthfit.rag` (인터페이스 확장)
> **상태**: Brainstorming 종료, 구현 계획 작성 대기

---

## 1. 배경

`com.youthfit.qna`는 PRD `docs/prd/05-qna.md`에 "미구현"으로 표기되어 있으나 실제로는 SSE 기반 핵심 흐름이 이미 구현되어 있다.

**구현된 것**

- `POST /api/v1/qna/ask` (SSE, 인증 필수)
- RAG 벡터 검색 → 컨텍스트 빌드 → OpenAI 스트리밍
- `CHUNK` / `SOURCES` / `DONE` / `ERROR` 이벤트
- `QnaHistory` 답변 완료 후 일괄 저장 (실패 swallow)
- 출처 기반·불확실성 인정 시스템 프롬프트

**PRD 대비 갭**

1. 출처 메타데이터 빈약 (`"청크 12"`만 노출, PRD 예시는 `"section": "자격 요건"`)
2. 비용 방어 미적용 (PRD 명시 항목인 동일 질문 캐시 없음, 최근 도입 `CostGuard` 정책 ID allowlist 미적용)
3. 답변 신뢰성 가드 부재 (유사도 임계값 없음 — top-K=5를 무조건 컨텍스트로 사용)
4. 저장 신뢰성 미흡 (`saveHistory` try-catch swallow, SSE 도중 끊기면 질문 기록도 유실)
5. 입력 검증 부재 (질문 길이 제한 없음)

본 작업은 v0 출시 직전 위 갭을 한 번에 해소하는 "출시 품질 묶음"이다.

---

## 2. 목표 / 비목표

### 목표

| # | 항목 | 결과 |
|---|------|------|
| 1 | 비용 방어 | `CostGuard` 정책 ID allowlist 적용, Redis 정확 매칭 캐시(TTL 24h) |
| 2 | 출처 표현 강화 | `"청크 N"` → `"<첨부 라벨> · p.<start>-<end>"` (페이지·첨부 메타 노출) |
| 3 | 답변 신뢰성 | zero-relevance 거절 — 모든 청크 distance가 임계값 초과면 LLM 호출 skip |
| 4 | 저장 신뢰성 | 질문 즉시 저장 + `status` 컬럼(IN_PROGRESS/COMPLETED/FAILED) |
| 5 | 입력 검증 | 질문 길이 2~500자 |

### 비목표 (스코프 외)

- **사용자별 rate limit, 프롬프트 인젝션 방어, SSE 타임아웃 분류**: 운영 견고성 묶음(v0+) 별도 사이클
- **인덱싱 시점 섹션 헤딩 추출**: 출처 옵션 C — `policy_document`에 `section_title` 컬럼 도입 + ingestion 헤딩 파싱. v0+ 별도 사이클
- **히스토리 조회 API**: PRD 미명시
- **의미적(semantic) 캐시**: §9 후속 작업 참조

---

## 3. 결정사항 요약

| # | 결정 항목 | 선택 |
|---|-----------|------|
| D1 | 출처 표현 깊이 | A — 페이지+첨부 라벨 (LLM이 추출하는 비결정적 섹션 라벨 미채택) |
| D2 | 답변 신뢰성 가드 | A — zero-relevance 거절 (전수 컷이 아니라 "전부 무관"만 컷) |
| D3 | 비용 방어 구성 | A — CostGuard + Redis 정확 매칭 캐시 (rate limit은 v0+) |
| D4 | 저장 신뢰성 | A — 질문 즉시 저장 + `status` 컬럼 (한방 저장 유지가 아님) |
| D5 | 캐시 매칭 방식 | D — 정확 매칭만 (의미적 유사도 캐시는 v0+ 후속) |

---

## 4. 데이터 모델 변경

### 4.1 `QnaHistory` (`qna_history`)

추가 컬럼:

```
+ status         VARCHAR(20) NOT NULL    -- IN_PROGRESS | COMPLETED | FAILED
+ failed_reason  VARCHAR(50)             -- nullable, FAILED일 때만
```

`failed_reason` 분류:

- `NO_INDEXED_DOCUMENT` — 정책에 인덱싱된 청크 0건
- `NO_RELEVANT_CHUNK` — 청크는 있으나 모두 임계값 초과
- `LLM_ERROR` — OpenAI 호출 실패·스트리밍 도중 끊김
- `COST_GUARD_BLOCKED` — CostGuard 차단(저장 정책은 §5.1 참조)
- `INTERNAL_ERROR` — 그 외 예외

도메인 메서드:

- `markCompleted(answer, sources)` — 기존 `completeAnswer` 이름 변경 (CONVENTIONS의 `change.../update...` 명명 준수). `status=COMPLETED`.
- `markFailed(reason)` — `status=FAILED`, `failed_reason` 설정. `answer/sources`는 nullable 유지.

상태 전이 invariant:

- `IN_PROGRESS → COMPLETED` (`markCompleted`)·`IN_PROGRESS → FAILED` (`markFailed`)만 허용
- 이미 `COMPLETED`/`FAILED` 인 history에 대한 `markCompleted`/`markFailed` 재호출은 `IllegalStateException`
- 동일 질문 재시도는 새 history row를 만든다 (재사용·갱신 X)

마이그레이션:

- 기존 row는 `status='COMPLETED'`로 백필
- 새 컬럼은 `NOT NULL`(status)·`NULL`(failed_reason)

### 4.2 `PolicyDocumentChunkResult` (rag 모듈)

```java
public record PolicyDocumentChunkResult(
    Long id,
    Long policyId,
    int chunkIndex,
    String content,
    double distance,         // pgvector cosine distance
    Long attachmentId,       // nullable
    Integer pageStart,       // nullable
    Integer pageEnd          // nullable
) { ... }
```

**`distance` 채움 규칙**

- 벡터 검색 결과: pgvector 코사인 거리 그대로
- 키워드 폴백 결과: `0.0`(=가장 가까운 것으로 간주). 폴백은 키워드 일치를 보장하므로 임계값 가드가 통과시켜야 한다.

**리포지토리 시그니처**

```java
record SimilarChunk(PolicyDocument document, double distance) { }

List<SimilarChunk> findSimilarByEmbedding(Long policyId, float[] queryEmbedding, int limit);
```

application 레이어에서 `SimilarChunk` → `PolicyDocumentChunkResult` 변환.

### 4.3 `QnaSourceResult` (qna 모듈, SOURCES 이벤트 페이로드)

```java
public record QnaSourceResult(
    Long policyId,
    Long attachmentId,       // nullable
    String attachmentLabel,  // 예: "1차_공고.pdf" (Attachment 도메인의 표시 이름)
    Integer pageStart,
    Integer pageEnd,
    String excerpt
) { ... }
```

- 기존 `section` 필드는 제거 — 프론트가 `attachmentLabel + " · p." + pageStart + "-" + pageEnd` 형식으로 라벨 조립
- `attachmentId == null` 인 청크(첨부 무관 인덱싱)면 `attachmentLabel/pageStart/pageEnd`도 `null` — 프론트는 `excerpt`만 표시

### 4.4 `QnaAnswerCache` 포트 + Redis 어댑터

```java
public interface QnaAnswerCache {
    Optional<CachedAnswer> get(Long policyId, String normalizedQuestion);
    void put(Long policyId, String normalizedQuestion, CachedAnswer value);
}

public record CachedAnswer(String answer, List<QnaSourceResult> sources, Instant cachedAt) { }
```

- 키: `qna:answer:{policyId}:{sha256(normalizedQuestion)}`
- 정규화: `question.trim().toLowerCase().replaceAll("\\s+", " ")`
- TTL: 24h
- 어댑터: `infrastructure/external/RedisQnaAnswerCache`
- 직렬화: Jackson (이미 가진 `ObjectMapper` 재사용)

### 4.5 `AskQuestionRequest`

```java
public record AskQuestionRequest(
    @NotNull Long policyId,
    @NotBlank @Size(min = 2, max = 500) String question
) { ... }
```

### 4.6 `QnaProperties` (configuration)

```yaml
youthfit:
  qna:
    cache:
      ttl: 24h
    relevance:
      distance-threshold: 0.4   # 코사인 거리, 보수적 초기값. 운영 데이터로 조정.
    sse-timeout: 120s
```

`relevance.distance-threshold`의 초기값은 **운영 데이터를 본 뒤 조정**한다. 본 설계에서 정확한 수치를 확정하지 않으며, 0.4는 "전혀 무관한 청크만 차단"하는 보수적 출발점으로 둔다.

---

## 5. 처리 흐름

### 5.1 메인 시퀀스

```
[Controller] POST /api/v1/qna/ask  (인증 필수, @Valid)
    │
    ▼
[QnaService.askQuestion]
    │
    1. CostGuard.checkPolicyAllowed(policyId)
    │     ├─ 차단(dev) → ERROR 이벤트 + complete, history 미저장
    │     └─ 통과
    │
    2. policyRepository.findById(policyId)
    │     ├─ 없음 → YouthFitException(NOT_FOUND), history 미저장
    │     └─ 통과
    │
    3. QnaHistoryWriter.startInProgress(userId, policyId, question)
    │     → status=IN_PROGRESS, id 반환  (별도 트랜잭션 빈)
    │
    4. QnaAnswerCache.get(policyId, normalizedQuestion)
    │     ├─ 히트 →
    │     │     ▸ CHUNK(answer 전체) 1회
    │     │     ▸ SOURCES(cached.sources)
    │     │     ▸ DONE
    │     │     ▸ historyWriter.markCompleted(historyId, answer, sources)
    │     │     ▸ return
    │     └─ 미스
    │
    5. RagSearchService.searchRelevantChunks(...)  // distance 포함
    │     ├─ 결과 비어있음 → §5.2 거절 흐름 (NO_INDEXED_DOCUMENT)
    │     └─ 통과
    │
    6. 임계값 가드: chunks.stream().allMatch(c -> c.distance() > THRESHOLD)
    │     ├─ true → §5.2 거절 흐름 (NO_RELEVANT_CHUNK)
    │     └─ false → 임계값 통과 청크만 컨텍스트로 사용
    │
    7. context = buildContext(passingChunks)
    │   sources = buildSources(passingChunks)   // attachmentLabel/page
    │
    8. QnaLlmProvider.generateAnswer(policyTitle, context, question, chunkConsumer)
    │     ├─ 예외 → §5.3 LLM 에러 흐름
    │     └─ 통과
    │
    9. SOURCES(sources) + DONE
    │
   10. QnaAnswerCache.put(policyId, normalizedQuestion, CachedAnswer(answer, sources, now()))
    │     실패 → 무시 + WARN 로깅 (메인 흐름 차단 X)
    │
   11. historyWriter.markCompleted(historyId, answer, sources)
```

### 5.2 거절 흐름 (NO_INDEXED_DOCUMENT / NO_RELEVANT_CHUNK)

```
▸ CHUNK(거절 메시지)
▸ SOURCES([])
▸ DONE
▸ historyWriter.markFailed(historyId, reason)
```

거절 메시지:

- `NO_INDEXED_DOCUMENT`: "이 정책은 아직 본문 인덱싱이 되어 있지 않아 답변을 만들 수 없습니다. 정책 상세 페이지에서 원문을 확인해 주세요."
- `NO_RELEVANT_CHUNK`: "해당 정책 원문에서 관련 내용을 찾지 못했습니다. 공식 문의처에서 확인하시는 것을 권장합니다."

### 5.3 LLM 에러 흐름

```
▸ ERROR 이벤트 (sendErrorEvent)
▸ emitter.completeWithError(e)
▸ historyWriter.markFailed(historyId, LLM_ERROR)
```

스트리밍 도중 일부 chunk가 이미 전송된 상태에서 LLM 에러가 나도 동일 시퀀스. 사용자는 받은 chunk + ERROR 이벤트로 부분 응답을 인지한다.

### 5.4 트랜잭션 경계

- `QnaService`: `@Transactional` 미사용. 파일 I/O·LLM 호출 등 외부 호출이 길어 트랜잭션을 잡지 않는다.
- `QnaHistoryWriter`: 별도 빈. `startInProgress`, `markCompleted`, `markFailed` 각각 짧은 `@Transactional`. self-invocation을 피해 AOP가 정상 작동하게 한다.
- 캐시 read/write·LLM 스트리밍은 트랜잭션 밖.

### 5.5 거절·에러 이벤트 시퀀스의 일관성

- **CostGuard 차단만 ERROR 이벤트** (사용자 책임 외 시스템 차단)
- **그 외 거절(NO_INDEXED_DOCUMENT, NO_RELEVANT_CHUNK)은 모두 정상 SSE 시퀀스(`CHUNK → SOURCES → DONE`)** — 프론트는 거절을 별도 분기 처리할 필요 없이 답변 본문에 사유가 표시된다.

---

## 6. 에러·엣지 케이스 매트릭스

| 케이스 | SSE 응답 | history 상태 | 사용자 메시지 |
|---|---|---|---|
| CostGuard 차단 (dev) | ERROR + complete | 미저장 | "현재 환경에서 이 정책은 Q&A를 지원하지 않습니다." |
| Policy 없음 | 일반 4xx (SSE 시작 전) | 미저장 | NOT_FOUND 표준 응답 |
| 입력 검증 실패 (질문 < 2자, > 500자) | 일반 4xx | 미저장 | 표준 validation 응답 |
| 정책 인덱싱 0건 | CHUNK + SOURCES([]) + DONE | FAILED · `NO_INDEXED_DOCUMENT` | (§5.2 거절 메시지) |
| 모든 청크 distance > 임계값 | CHUNK + SOURCES([]) + DONE | FAILED · `NO_RELEVANT_CHUNK` | (§5.2 거절 메시지) |
| 캐시 히트 | CHUNK(answer 전체) + SOURCES + DONE | COMPLETED | 캐시된 답변 |
| LLM 호출 실패 (HTTP 4xx/5xx, 네트워크) | ERROR + completeWithError | FAILED · `LLM_ERROR` | "답변 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." |
| LLM 스트리밍 도중 끊김 | 받은 chunk까지 + ERROR | FAILED · `LLM_ERROR` | (위와 동일) |
| Redis 캐시 read 실패 | 무시 (메인 흐름 진행) | 영향 없음 | 비가시 |
| Redis 캐시 write 실패 | 무시 (사용자 응답 정상 종료 후) | 영향 없음 | 비가시 |
| history 저장 실패 (DB 장애) | 사용자 응답에 영향 없음 | 저장 자체 실패 → ERROR 로그 + Micrometer counter (`qna_history_save_failure_total`) | 비가시 |
| SSE 타임아웃(120s) | onTimeout 콜백 → 잔존 | IN_PROGRESS 잔존 (의도) | (v0+ 운영 견고성에서 정리) |

### 운영 메트릭

- `qna_request_total{outcome=completed|cache_hit|no_indexed|no_relevant|llm_error|cost_blocked}`
- `qna_history_save_failure_total`
- `qna_cache_hit_total` / `qna_cache_miss_total`
- `qna_relevance_distance{quantile=0.5,0.9,0.99}` — 임계값 운영 튜닝의 근거 데이터

---

## 7. 테스트 전략

### 7.1 도메인 단위 (`QnaHistoryTest`)

- `markCompleted(answer, sources)` → status=COMPLETED, answer/sources 저장
- `markFailed(reason)` → status=FAILED, failedReason 저장, answer는 null 유지
- 상태 전이 invariant: `IN_PROGRESS`가 아닌 상태에서 `markCompleted`·`markFailed` 호출 시 `IllegalStateException` (§4.1)

### 7.2 Application 단위 (`QnaServiceTest`, MockitoExtension)

SseEmitter 검증은 `Consumer<String>` 캡처(LLM chunk callback) + `SseEmitter` 이벤트 캡처 헬퍼. 시나리오:

| 시나리오 | 핵심 검증 |
|---|---|
| 정상 (캐시 미스 + 청크 충분) | RAG 1회·LLM 1회·캐시 put 1회, CHUNK·SOURCES·DONE 순서, history COMPLETED |
| 캐시 히트 | RAG·LLM 0회, CHUNK 1회(전체 답변), SOURCES, DONE, history COMPLETED |
| 인덱싱 0건 | LLM 0회, CHUNK(거절), history FAILED·NO_INDEXED_DOCUMENT |
| 모든 청크 distance > 임계값 | LLM 0회, history FAILED·NO_RELEVANT_CHUNK |
| 임계값 통과 청크 일부만 있음 | 통과 청크만 컨텍스트, LLM 1회 |
| LLM 에러 | ERROR 이벤트, history FAILED·LLM_ERROR |
| CostGuard 차단 | CostGuard 1회, ERROR, history 미저장(또는 BLOCKED 분류) |
| 캐시 read 실패 | 정상 흐름 진행, WARN 로그 |
| 캐시 write 실패 | 사용자 응답 정상, WARN 로그, history COMPLETED |
| 정책 없음 | NOT_FOUND, history 미저장 |

### 7.3 Infrastructure 슬라이스

- `PolicyDocumentRepositoryImplTest` (DataJpaTest + Testcontainers postgres+pgvector — 기존 패턴 따라): `findSimilarByEmbedding`이 `SimilarChunk(document, distance)` 반환·distance 오름차순 정렬·동일 정책 스코프 검증
- `QnaHistoryRepositoryImplTest`: `status`/`failed_reason` 컬럼 매핑, 마이그레이션 후 기존 데이터의 status 디폴트 백필 동작
- `RedisQnaAnswerCacheTest`: Redis 컨테이너 또는 `@DataRedisTest`. put → get round-trip, TTL 만료, 정규화(공백/대소문자 다른 질문이 같은 키)

### 7.4 Presentation (선택, 가벼움)

- `@WebMvcTest QnaControllerTest`: 입력 validation(`@Size`, `@NotNull`), 인증 게이팅(`@AuthenticationPrincipal`)만 검증. SSE 본문 흐름은 application 단위가 책임.

### 7.5 명시적으로 안 하는 것

- 실제 OpenAI API e2e — staging 수동 검증
- SSE 풀-체인 통합 테스트 — v0+ 운영 견고성 작업과 함께

---

## 8. 변경 파일 인벤토리 (참고용)

| 파일 | 변경 |
|---|---|
| `qna/domain/model/QnaHistory.java` | status/failed_reason 컬럼·도메인 메서드 |
| `qna/domain/repository/QnaHistoryRepository.java` | startInProgress·markCompleted·markFailed 메서드 |
| `qna/infrastructure/persistence/QnaHistoryJpaRepository.java` | 동일 반영 |
| `qna/infrastructure/persistence/QnaHistoryRepositoryImpl.java` | 동일 반영 |
| `qna/application/service/QnaService.java` | 흐름 재구성 (§5) |
| `qna/application/service/QnaHistoryWriter.java` (신규) | 트랜잭션 분리된 저장 헬퍼 |
| `qna/application/dto/result/QnaSourceResult.java` | 출처 메타 컬럼 |
| `qna/application/port/QnaAnswerCache.java` (신규) | 캐시 포트 |
| `qna/infrastructure/external/RedisQnaAnswerCache.java` (신규) | Redis 어댑터 |
| `qna/infrastructure/config/QnaConfig.java` / `QnaProperties.java` | 임계값·TTL 설정 |
| `qna/presentation/dto/request/AskQuestionRequest.java` | `@Size` 추가 |
| `rag/application/dto/result/PolicyDocumentChunkResult.java` | distance·attachmentId·page 추가 |
| `rag/domain/repository/PolicyDocumentRepository.java` | `SimilarChunk` 반환 시그니처 |
| `rag/domain/model/SimilarChunk.java` (신규) | 거리 포함 결과형 |
| `rag/application/service/RagSearchService.java` | 새 결과형 변환 |
| `rag/infrastructure/persistence/PolicyDocumentRepositoryImpl.java` | distance SELECT |
| `common/cost-guard/...` | qna 정책 ID allowlist 항목 추가 (기존 메커니즘 재사용) |
| `db/migration/Vxxx__qna_history_status.sql` | status·failed_reason 컬럼 추가, 백필 |

---

## 9. 후속 작업 / v0+

본 작업 직후 별도 사이클로 다룰 항목:

### 9.1 Q&A semantic cache (의미적 유사도 기반 답변 재사용)

- **동기**: 정확 매칭만으론 캐시 히트율이 낮아 PRD 비용 방어 효과가 제한적.
- **보류 사유**: false positive 위험("재학생도 가능?" vs "재학생만 가능?")이 PRD 핵심 원칙(거짓말 안 하기)과 직접 충돌. 임계값 안전 설정에 운영 데이터 필요.
- **도입 조건**: v0 운영 1~2개월 후 (a) 반복 질문 패턴, (b) 질문 임베딩 cosine distance 분포를 분석해 false-positive 0%에 가까운 보수적 임계값을 결정 가능할 때.
- **구현 메모**: `QnaHistory.question_embedding vector(1536)` 컬럼 + `findSimilarCachedAnswer(policyId, embedding, threshold)` 리포지토리. 정책 원문 변경 시 캐시 무효화는 `policy_document.sourceHash` 또는 `Guide.sourceHash` 기반.

### 9.2 운영 견고성 묶음

- 사용자별 rate limit (분당/시간당 질문 횟수)
- 프롬프트 인젝션 방어 (질문 sanitization, system 메시지 분리 강화)
- SSE 타임아웃·abort 분류 — IN_PROGRESS 잔존 history 클린업 잡 또는 추가 상태(`ABANDONED`)
- LLM 에러 분류 세분화 (rate-limit / quota / network / parse)
- history 저장 실패 알림 채널 연동

### 9.3 출처 표현 강화 (인덱싱 시점 헤딩 추출)

- `policy_document`에 `section_title` 컬럼 추가
- ingestion 단계에서 헤딩 파싱 (PDF·HTML 별도 전략)
- `QnaSourceResult`에 `sectionTitle` 노출 → PRD 예시(`"section": "자격 요건"`) 정확 충족

### 9.4 히스토리 조회 API (요구 발생 시)

- 사용자 본인의 Q&A 히스토리 페이징 조회
- PRD 미명시 — 프론트 요구사항이 들어오면 별도 사이클
