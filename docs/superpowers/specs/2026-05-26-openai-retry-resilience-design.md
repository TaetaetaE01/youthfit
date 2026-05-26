# OpenAI 호출 재시도 / 복원력 설계

> **상태**: spec (2026-05-26 작성)
> **선행**: [2026-05-26-hybrid-map-reduce-guide-generation-design.md](./2026-05-26-hybrid-map-reduce-guide-generation-design.md)
> **모듈**: `common`, `guide.infrastructure.external`, `rag.infrastructure.external`

## 1. 배경 / 동기

Hybrid Map-Reduce 도입 (2026-05-26) 으로 `MapReduceGuideOrchestrator` 가 청크 그룹별 partial 호출 N회 + merge 호출 1회 패턴으로 동작한다. 현재 partial 호출이 OpenAI 측 transient error (429 rate limit, 5xx, 일시적 네트워크 오류) 로 실패하면 그대로 catch + log.warn + skip 한다. 잠시 기다리면 성공할 호출인데 정보를 잃는다.

2026-05-26 운영 직전 테스트에서 22개 가이드 누락 / 9개 정책 영구 실패가 발생했고 근본 원인의 하나가 OpenAI 429 였다. Map-Reduce 도입으로 9개 영구 실패는 해소됐지만 transient retry 가 없으면 운영 트래픽 / OpenAI tier 변화 시 동일 문제가 재발할 수 있다.

### 현재 영향
- partial 호출: skip → 가이드에 해당 그룹 정보 누락
- merge / single-call: 즉시 `YouthFitException(INTERNAL_ERROR)` → 가이드 자체 미생성
- embedding: RAG 청크 적재 실패 → Q&A 품질 저하
- `GuideGenerationService` 의 기존 retry 는 content-level (LLM 응답 누락 필드 재요청) 이지 transport-level 이 아님

### 목표
- **G1**: transient error (429, 5xx, IOException) 에 대해 OpenAI 호출 자동 재시도 — partial / merge / single-call / embedding 모두
- **G2**: OpenAI `Retry-After` 응답 헤더를 존중하여 thundering herd 방지
- **G3**: 운영 메트릭으로 retry 빈도 / 실패율 가시화
- **G4**: 영구 실패 (4xx non-429) 는 retry 안 함 — 비용 / 시간 낭비 방지

### 비목표
- partial 호출 병렬화 — 별도 spec
- CircuitBreaker / RateLimiter — retry 로 충분, 추후 burst 패턴 관찰 후 검토
- 영구 실패 정책의 deferred retry (n8n force-enrich 와 결합) — 별도
- merge fallback 전략 (single-call 으로 폴백 등) — 복잡도 대비 가치 낮음
- OpenAI tier 상향 / 자체 rate limit

## 2. 접근 비교

| 측면 | A. Resilience4j (선택) | B. Spring Retry | C. 수동 |
|---|---|---|---|
| 의존성 | `resilience4j-spring-boot3`, `resilience4j-micrometer` | `spring-retry` + `spring-aspects` | 없음 |
| 선언 방식 | `@Retry(name = "openai-chat")` annotation | `@Retryable(value = {...})` annotation | for-loop + sleep |
| 설정 위치 | `application.yml` `resilience4j.retry.instances.*` | `@Retryable` parameters | 코드 내 상수 |
| Retry-After 존중 | `RetryConfig.intervalBiFunction` 으로 동적 결정 | ExceptionClassifier 우회 필요 | 직접 파싱 |
| 메트릭 | Micrometer 자동 노출 (`resilience4j.retry.calls`) | 별도 빌드 필요 | 직접 구현 |
| 재사용성 | name 만 지정해 client 간 공유 | annotation 분산 | wrapper 별도 작성 |

**선택: Resilience4j**. 메트릭 자동 노출 + CircuitBreaker / RateLimiter 와 자연스러운 결합 + Spring Boot 표준. 의존성 1.5MB 정도지만 그만한 가치 있음.

### 정책 요약
| 항목 | 값 |
|---|---|
| max attempts | 3 (초기 호출 + 2회 재시도) |
| 대기 전략 | exponential backoff (2s × 2.0) + jitter ±50% |
| Retry-After 헤더 | 있으면 우선 사용 |
| 개별 wait 상한 | 30초 (clamp) |
| 재시도 트리거 | 429, 5xx, IOException, ResourceAccessException, SocketTimeoutException |
| 재시도 제외 | 4xx (non-429), 직렬화 실패, NPE 등 |
| 최종 실패 처리 | partial → catch + skip (기존 유지) / merge / single-call / embedding → 상위로 throw |

## 3. 컴포넌트

### C1. `RetryableOpenAiException` (신규)
**위치**: `backend/src/main/java/com/youthfit/common/exception/RetryableOpenAiException.java`
**책임**: OpenAI 호출의 transient 실패만 마킹. Resilience4j 가 이 타입에만 retry.
**시그니처**:
```java
public class RetryableOpenAiException extends RuntimeException {
    private final Duration retryAfter; // nullable
    public RetryableOpenAiException(String message, Throwable cause, Duration retryAfter) { ... }
    public Optional<Duration> retryAfter() { ... }
}
```

### C2. `OpenAiErrorClassifier` (신규)
**위치**: `backend/src/main/java/com/youthfit/common/openai/OpenAiErrorClassifier.java`
**책임**: Spring `RestClient` / OpenAI SDK 가 던진 raw 예외를 분류 + `Retry-After` 헤더 파싱.
**분류 규칙**:
| Raw 예외 | 분류 결과 |
|---|---|
| `HttpClientErrorException.TooManyRequests` (429) | `RetryableOpenAiException(retryAfter = 헤더 파싱)` |
| `HttpServerErrorException` (5xx) | `RetryableOpenAiException(retryAfter = null)` |
| `ResourceAccessException` (timeout / I/O) | `RetryableOpenAiException(retryAfter = null)` |
| `SocketTimeoutException`, `IOException` (raw) | `RetryableOpenAiException(retryAfter = null)` |
| `HttpClientErrorException` (400, 401, 403, 404) | 원본 예외 그대로 re-throw |
| 기타 (직렬화 실패, NPE 등) | 원본 예외 그대로 re-throw |

**시그니처**:
```java
@Component
public class OpenAiErrorClassifier {
    public RuntimeException classify(Throwable raw); // 항상 throw 가능한 형태
}
```

**Retry-After 파싱**:
- 정수 초 (`"5"`) → `Duration.ofSeconds(5)`
- HTTP-date (RFC 7231) → 현재 시각과 차이
- 파싱 실패 / 헤더 없음 → `null`

### C3. `application.yml` — Resilience4j 설정 (신규)
```yaml
resilience4j:
  retry:
    instances:
      openai-chat:
        max-attempts: 3
        wait-duration: 2s
        exponential-backoff-multiplier: 2.0
        randomized-wait-factor: 0.5
        retry-exceptions:
          - com.youthfit.common.exception.RetryableOpenAiException
      openai-embedding:
        max-attempts: 3
        wait-duration: 2s
        exponential-backoff-multiplier: 2.0
        randomized-wait-factor: 0.5
        retry-exceptions:
          - com.youthfit.common.exception.RetryableOpenAiException

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus  # metrics 추가 확인
  metrics:
    distribution:
      percentiles-histogram:
        resilience4j.retry.calls: true
```

### C4. `RetryAfterIntervalFunction` (신규 빈)
**위치**: `backend/src/main/java/com/youthfit/common/openai/RetryAfterIntervalFunction.java`
**책임**: Resilience4j `RetryConfig.intervalBiFunction` 으로 주입. 마지막 throwable 이 `RetryableOpenAiException` 이고 `retryAfter` 가 non-null 이면 그 값 우선, 아니면 exponential + jitter fallback.
**로직**:
```
input: attempt (1..max-1), lastException
if lastException instanceof RetryableOpenAiException && retryAfter != null:
    waitMs = min(retryAfter.toMillis(), 30_000)
else:
    base = 2000 * Math.pow(2.0, attempt - 1)
    jitter = 0.5
    waitMs = (long) (base * (1 - jitter + Math.random() * 2 * jitter))
    waitMs = min(waitMs, 30_000)
return waitMs
```
**적용**: `@Configuration` 의 `RetryRegistry` 커스터마이저로 `openai-chat` / `openai-embedding` 두 instance 모두에 적용.

### C5. 클라이언트 수정
**위치**:
- `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- `backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java`

**변경**:
- 각 OpenAI 호출 메서드에 `@Retry(name = "openai-chat" | "openai-embedding")` 추가
- raw 호출은 try / catch 로 감싸 `OpenAiErrorClassifier.classify(e)` 결과를 throw
- `@Retry` annotation 은 Spring AOP 기반이라 self-invocation 시 안 먹음. 따라서 외부 진입점 public 메서드에 적용 (예: `OpenAiChatClient.generatePartialGuide`, `mergePartialGuides`, `generateGuide`, `OpenAiEmbeddingClient.embed`). private helper 인 `callChatCompletion` 같은 메서드에는 적용 안 함. 외부 호출자가 인터페이스를 통해 빈을 호출하므로 AOP 가 정상 동작.

### C6. `MapReduceGuideOrchestrator` (메시지 보강만)
**위치**: `backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java:49-53`
**변경**: catch 블록은 그대로. 로그 메시지에 "retry 모두 소진 후 skip" 명시.
```java
log.warn("부분 가이드 호출 실패 (retry 소진, skip): policyId={}, groupIndex={}, err={}",
        input.policyId(), i, e.getMessage());
```

### C7. 메트릭 노출
Resilience4j 가 자동으로 다음 메트릭을 노출:
```
resilience4j.retry.calls{name="openai-chat", kind="successful_without_retry"}
resilience4j.retry.calls{name="openai-chat", kind="successful_with_retry"}
resilience4j.retry.calls{name="openai-chat", kind="failed_with_retry"}
resilience4j.retry.calls{name="openai-chat", kind="failed_without_retry"}
```
기존 `metrics` 도메인의 `LlmUsageRecord` (비용 / 토큰) 와는 별도. 호출 비용은 그대로, retry 빈도는 Micrometer 로 가시화.

### C8. 테스트 (신규)
- `OpenAiErrorClassifierTest` — 429 / 5xx / 4xx / IOException / 직렬화 실패 분류
- `RetryAfterIntervalFunctionTest` — retryAfter 우선 / null fallback / 30s clamp / jitter 범위
- `OpenAiChatClientRetryTest` — MockWebServer 로:
  - 429 → 200 (1회 retry 성공)
  - 429 × 3 → throw (소진)
  - 401 → 즉시 throw (retry 없음)
  - 503 → 200 (exponential backoff)
- 임베딩 클라이언트 retry 테스트 — 동일 패턴
- `MapReduceGuideOrchestratorRetryTest` — `GuideLlmProvider` mock 으로:
  - partial 1번 실패 (RetryableOpenAiException) → retry 후 성공 → merge 진행
  - partial 모두 retry 소진 → skip → 다음 그룹 정상 처리
  - 모든 partial retry 소진 → `YouthFitException` throw

## 4. 데이터 흐름

### 정상 경로 (retry 없음)
```
OpenAiChatClient.generatePartialGuide()
  ↓ @Retry("openai-chat")
RestClient.post(...) → 200 OK → GuideContent 반환
```
메트릭: `resilience4j.retry.calls{kind="successful_without_retry"}` +1

### 429 + Retry-After 헤더
```
t=0   RestClient.post() → 429, header "retry-after: 5"
t=0   catch → OpenAiErrorClassifier.classify(e)
      → RetryableOpenAiException(retryAfter=5s)
t=0   Resilience4j: RetryAfterIntervalFunction(attempt=1, lastException)
      → 5000ms (헤더 우선)
t=5   RestClient.post() → 200 OK → GuideContent 반환
```
메트릭: `successful_with_retry` +1
로그: `WARN openai-chat retry attempt=1 waitMs=5000 cause=429 TooManyRequests`

### 5xx + exponential backoff (Retry-After 없음)
```
t=0     503 → RetryableOpenAiException(retryAfter=null)
        RetryAfterIntervalFunction → fallback: 2000ms × jitter±50% = 1000-3000ms
t=~2s   502 → 동일 분류
        fallback: 2000 × 2.0 × jitter = 2000-6000ms
t=~6s   200 OK
```
메트릭: `successful_with_retry` +1

### 모두 실패 (3 attempts 소진)
```
t=0    429 → wait 5s
t=5    429 → wait 5s
t=10   429 → wait 5s     ← max-attempts 도달
t=15   throw RetryableOpenAiException
```
- partial: `MapReduceGuideOrchestrator` catch → log.warn → skip → 다음 그룹
- merge / single-call: 상위로 전파 → `YouthFitException(INTERNAL_ERROR)`
- embedding: 상위로 전파 → 청크 적재 실패 이벤트

메트릭: `failed_with_retry` +1

### 영구 실패 (4xx non-429)
```
t=0   401 Unauthorized
      OpenAiErrorClassifier: 분류 안 함 → 원본 HttpClientErrorException 그대로
      Resilience4j: 등록된 retry-exception 아님 → 즉시 throw
```
메트릭: `failed_without_retry` +1

### 전체 대기 상한
`RetryAfterIntervalFunction` 이 개별 wait 를 30초로 clamp. 이론상 3 attempts × 30초 = 90초까지 가능하나 실측 OpenAI Retry-After 는 보통 5-20초, 평균 1회 retry 로 해결.

## 5. 회귀 안전

- **변경 없는 부분**: `MapReduceGuideOrchestrator.generate()` 메인 루프, `GuideStrategySelector` 임계값, `GuideGenerationEventListener` 의 single-call 분기. retry 가 client 레이어에서 끝나므로 상위 로직 영향 없음.
- **single-call 기존 retry 분기**: `GuideGenerationService` 의 `firstReport.hasRetryTrigger()` 블록 유지. 새 retry 는 **transport-level**, 기존 retry 는 **content-level** (LLM 응답 누락 필드 재요청). 레이어가 다름.
- **이미 통과 중인 60/60 정책**: retry 는 실패 시에만 활성화. 정상 경로 동일.
- **catch-all swallow 방지**: `OpenAiErrorClassifier` 가 분류 못 하는 예외는 원본 그대로 re-throw. 직렬화 실패 / NPE 는 retry 안 되고 즉시 실패.
- **테스트 회귀**: 기존 `GuideGenerationServiceTest`, `MapReduceGuideOrchestratorTest`, `OpenAiChatClientTest` (있다면) 모두 그대로 통과해야 함.

## 6. 비용 영향

- **추가 retry 호출**: 429 / 5xx 발생 시에만. 정상 시 비용 동일.
- **메트릭**: Micrometer 메트릭 수십 series 추가. Spring Boot Actuator 가 이미 노출이면 무비용.
- **의존성**: `resilience4j-spring-boot3` + `resilience4j-micrometer` ~1.5MB. Spring Boot 4.0.5 호환 버전은 Resilience4j 2.3.x 이상 (구현 시 BOM 확인).

## 7. 성공 기준

1. 429 / 5xx / IOException 발생 시 최대 3회 시도 후 transient error 자동 회복
2. 4xx (non-429) 는 retry 없이 즉시 실패 (비용 낭비 없음)
3. `resilience4j.retry.calls` 메트릭이 4가지 kind 로 노출되고 Prometheus / Actuator 에서 조회 가능
4. WARN 로그에 attempt / waitMs / cause / policyId 명시
5. 기존 60/60 정책 가이드 생성 회귀 없음 (모든 기존 테스트 통과)
6. MockWebServer 통합 테스트 4개 시나리오 (429-200, 429×3, 401 즉시, 503-200) 모두 통과

## 8. 후속 작업 (이 spec 외)

- partial 호출 병렬화 (현재 순차)
- CircuitBreaker 도입 (OpenAI 장애 구간 fast-fail)
- RateLimiter 도입 (자체 burst 제어)
- n8n force-enrich workflow 와 결합한 deferred retry (영구 실패 정책 대상)
- OpenAI tier 상향 검토 (메트릭 데이터 누적 후 판단)
