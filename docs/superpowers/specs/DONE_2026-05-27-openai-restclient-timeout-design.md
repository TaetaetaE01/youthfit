# OpenAI RestClient Timeout 설계

> **상태**: spec (2026-05-27 작성)
> **선행**: [2026-05-26-openai-retry-resilience-design.md](./2026-05-26-openai-retry-resilience-design.md)
> **모듈**: `common.openai`, `guide.infrastructure.external`, `rag.infrastructure.external`, `qna.infrastructure.external`, `eligibility.infrastructure.external`, `ingestion.infrastructure.external`

## 1. 배경 / 동기

6개 OpenAI 클라이언트가 `RestClient.create()` 기본값을 사용 — connect / read timeout 미설정으로 OpenAI 가 응답하지 않으면 무한 wait. 2026-05-26 retry feature 도입으로 위험이 **악화됨**: retry 가 죽지 않고 timeout 없는 호출을 영원히 block 한 채 다시 시도하므로 thread pool 고갈 / 자원 누수 위험.

### 대상 클라이언트 (RestClient.create() 사용 = timeout 무한)
1. `OpenAiChatClient` (guide.infrastructure.external)
2. `OpenAiEmbeddingClient` (rag.infrastructure.external)
3. `OpenAiQnaClient` (qna.infrastructure.external)
4. `OpenAiEligibilityRuleClient` (eligibility.infrastructure.external)
5. `OpenAiPolicyPeriodExtractor` (ingestion.infrastructure.external)
6. `OpenAiPolicyPeriodDisambiguator` (ingestion.infrastructure.external)

### 범위 외
- `AttachmentHttpClient` — 이미 `attachment.download.connect-timeout-seconds` (10s) / `read-timeout-seconds` (60s)
- `OpenAiQueryRewriter` — 이미 `RAG_QUERY_REWRITE_TIMEOUT_MS` (5s / 5s)
- `N8nForceEnrichClient` — 자체 factory
- `KakaoOAuthClient`, `SnsMessageVerifier` — 별도 spec

### 목표
- **G1**: OpenAI 호출 6곳에 connect 10s / read 60s timeout 강제
- **G2**: timeout 발동 시 기존 retry 체인이 자동 트리거 — 추가 코드 없이 `OpenAiErrorClassifier` 의 `ResourceAccessException → RetryableOpenAiException` 변환이 작동
- **G3**: 단일 properties `openai.http.*` 로 6개 클라이언트 공통 제어
- **G4**: 최악 latency 예측 가능 (timeout × retry attempts = 정해진 상한)

### 비목표
- `KakaoOAuthClient`, `SnsMessageVerifier` timeout 적용 — 별도 spec
- `RestClient` 인스턴스 자체 공유 (현재는 builder 만 공유 — base URL / header 차별화 여지 유지)
- OpenAI 별 timeout 분리 (chat vs embedding) — 운영 데이터 누적 후 검토
- Connection pool 최적화 (`SimpleClientHttpRequestFactory` 는 풀 없음 — 별도 spec)
- `OpenAiQueryRewriter` 의 자체 timeout 을 공통 properties 로 통합 — 의미 차이 (5s 단축 응답이 의도) 라 별도 유지

## 2. 접근 비교

| 측면 | A. 공유 Builder 빈 (선택) | B. 클라이언트별 개별 | C. RestClient 인스턴스 직접 공유 |
|---|---|---|---|
| 정의 위치 | `@Configuration OpenAiHttpConfig` 에 `@Bean openAiRestClientBuilder` | 각 클라이언트 생성자에서 factory 직접 생성 | `@Bean openAiRestClient` 완성 인스턴스 |
| 6개 클라이언트 수정 | 필드 변경 + 생성자에서 `builder.build()` | 6번 동일 패턴 복붙 | 필드 변경 + 주입 |
| properties 위치 | `openai.http.*` 단일 | 각 client properties 에 분산 | `openai.http.*` 단일 |
| 단일 진실 소스 | ✅ | ❌ 6곳 분산 | ✅ |
| 사용자 정의 (헤더 / base URL) 유연성 | 빌더에서 각자 빌드 | 각자 가능 | 인스턴스 공유 → 차별화 어려움 |
| 신규 코드 | ~25줄 | 6 × ~6줄 = ~36줄 | ~20줄 |

**선택: A (공유 Builder 빈)** — 단일 properties + 단일 빌더로 6곳 일관 적용. 인스턴스 자체를 공유하지 않는 이유는 향후 클라이언트별 base URL / default header 다르게 가져갈 여지 유지.

### 정책 요약
| 항목 | 값 |
|---|---|
| connect timeout | 10s (env: `OPENAI_HTTP_CONNECT_TIMEOUT_SECONDS`) |
| read timeout | 60s (env: `OPENAI_HTTP_READ_TIMEOUT_SECONDS`) |
| factory | `SimpleClientHttpRequestFactory` (JDK HttpURLConnection 기반) |
| 적용 방식 | `@Configuration` 빈으로 `RestClient.Builder` 공유, 각 client 가 빌드 |
| timeout 발생 시 처리 | `ResourceAccessException → RetryableOpenAiException` (Wave 5 classifier 재사용) → @Retry 자동 트리거 |
| 최악 latency 상한 | 60s × 3 attempts + retry waits ≈ 186s (~3분) |

## 3. 컴포넌트

### C1. `OpenAiHttpProperties` (신규)
**위치**: `backend/src/main/java/com/youthfit/common/openai/OpenAiHttpProperties.java`
**책임**: `openai.http.*` yaml 바인딩
**시그니처**:
```java
@ConfigurationProperties(prefix = "openai.http")
public record OpenAiHttpProperties(
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public OpenAiHttpProperties {
        if (connectTimeoutSeconds <= 0) connectTimeoutSeconds = 10;
        if (readTimeoutSeconds <= 0) readTimeoutSeconds = 60;
    }
}
```
**등록**: `OpenAiHttpConfig` 에 `@EnableConfigurationProperties(OpenAiHttpProperties.class)`.

### C2. `OpenAiHttpConfig` (신규)
**위치**: `backend/src/main/java/com/youthfit/common/openai/OpenAiHttpConfig.java`
**책임**: 공유 `RestClient.Builder` 빈 등록
**시그니처**:
```java
@Configuration
@EnableConfigurationProperties(OpenAiHttpProperties.class)
@RequiredArgsConstructor
public class OpenAiHttpConfig {

    private final OpenAiHttpProperties properties;

    @Bean("openAiRestClientBuilder")
    public RestClient.Builder openAiRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        return RestClient.builder().requestFactory(factory);
    }
}
```
**빈 이름**: `openAiRestClientBuilder` 명시 — Spring 의 default `restClientBuilder` 와 충돌 방지.

### C3. 6개 OpenAI 클라이언트 수정 패턴

변경 전:
```java
@Component
@RequiredArgsConstructor
public class OpenAiXxxClient {
    private final SomeProperties properties;
    private final RestClient restClient = RestClient.create();  // ← 필드 초기화
    // ...
}
```

변경 후:
```java
@Component
public class OpenAiXxxClient {
    private final SomeProperties properties;
    private final RestClient restClient;
    // ...

    public OpenAiXxxClient(
            SomeProperties properties,
            // 기타 의존성 ...,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder builder) {
        this.properties = properties;
        // ...
        this.restClient = builder.build();
    }
}
```

**Lombok `@RequiredArgsConstructor` 제거 이유**: `restClient = builder.build()` 가 final 필드 표현식이 아닌 생성자 본문 호출이라 Lombok 자동 처리 불가. 6개 모두 명시적 생성자로 전환.

**대상 6곳**:
- `OpenAiChatClient` (현재 `@RequiredArgsConstructor` → 명시적 생성자)
- `OpenAiEmbeddingClient` (현재 `@RequiredArgsConstructor` → 명시적 생성자)
- `OpenAiQnaClient` (현재 `@RequiredArgsConstructor` → 명시적 생성자)
- `OpenAiEligibilityRuleClient` (현재 `@RequiredArgsConstructor` → 명시적 생성자)
- `OpenAiPolicyPeriodExtractor` (현재 `@RequiredArgsConstructor` → 명시적 생성자)
- `OpenAiPolicyPeriodDisambiguator` (현재 `@RequiredArgsConstructor` → 명시적 생성자)

### C4. `application.yml` 추가

default profile 의 `openai` 블록 끝에 (현재 마지막 항목은 `ingestion.period.max-body-chars`):
```yaml
openai:
  # ... 기존 ...
  http:
    connect-timeout-seconds: ${OPENAI_HTTP_CONNECT_TIMEOUT_SECONDS:10}
    read-timeout-seconds: ${OPENAI_HTTP_READ_TIMEOUT_SECONDS:60}
```

### C5. 테스트
- `OpenAiHttpPropertiesTest` — default 값 적용, 음수 / 0 입력 시 fallback
- 기존 6개 클라이언트의 단위 테스트 회귀: 생성자 시그니처 변경에 따라 `new OpenAiXxxClient(...)` 호출에 `RestClient.Builder` mock 추가. `RestClient.builder().build()` 결과를 mock 으로 주입.

## 4. 데이터 흐름

### 정상 경로 (timeout 미발동)
```
Caller → @Retry AOP → OpenAiChatClient.generatePartialGuide
  → restClient.post(...).body()  ← 5초 응답 OK
  → GuideContent 반환
```
이전과 동일. timeout 카운터만 동작, 발동 없음.

### Read timeout (60s 초과)
```
t=0    restClient.post() 시작
t=60   SocketTimeoutException → RestClient 가 ResourceAccessException 으로 래핑
t=60   catch (RestClientException e) → errorClassifier.classify(e)
       → ResourceAccessException 분기
       → RetryableOpenAiException(retryAfter=null)
t=60   @Retry AOP: retry-exceptions 매칭 → wait ~2s + jitter
t=62   restClient.post() 다시 시도
       (3 attempts 까지, 모두 timeout 이면 → partial skip / merge fail)
```
**핵심**: 새 catch / 분류 코드 없음. Wave 5 classifier + retry 가 그대로 동작.

### Connect timeout (10s 초과 — DNS / TCP handshake 실패)
```
t=0    restClient.post() 시작
t=10   SocketTimeoutException (connect) → ResourceAccessException
       (이하 동일)
```

### 최악 latency (모든 attempt read timeout)
| Attempt | Wait | Cum |
|---|---|---|
| 1 (60s read timeout) | — | 60s |
| Retry wait | ~2s | 62s |
| 2 (60s read timeout) | — | 122s |
| Retry wait | ~4s | 126s |
| 3 (60s read timeout) | — | 186s |
| throw | — | 186s ≈ 3분 |

이전 (timeout 무한) 대비 **명확한 상한**. partial 호출이면 `MapReduceGuideOrchestrator` 의 catch 가 skip, merge / single-call 이면 상위로 throw → `RetryableOpenAiException` → `GlobalExceptionHandler` → 503.

## 5. 회귀 안전

- **정상 경로**: timeout 미발동 시 동작 100% 동일.
- **timeout 분류**: 기존 classifier 의 `ResourceAccessException` 분기 재사용 — 코드 변경 없음.
- **catch 블록**: Wave 5 의 `catch (RestClientException e) → classify` 가 이미 SocketTimeoutException 의 래퍼인 ResourceAccessException 잡음. 추가 catch 불필요.
- **생성자 시그니처 변경**: 6개 client 의 `@RequiredArgsConstructor` 제거 → Lombok 자동 생성자가 없으므로 기존 테스트의 `new OpenAiChatClient(...)` 호출이 컴파일 에러. 각 테스트에 mock builder 인자 추가.
- **기존 timeout 보유 클라이언트** (`AttachmentHttpClient`, `OpenAiQueryRewriter`, `N8nForceEnrichClient`): 변경 없음. 이 spec 범위 외.

## 6. 비용 영향

- **신규 코드**: ~25줄 (`OpenAiHttpConfig` + `OpenAiHttpProperties`) + 6개 클라이언트 약 5줄씩 변경.
- **의존성**: 추가 없음. Spring `SimpleClientHttpRequestFactory` 는 standard.
- **런타임**: timeout 측정 자체는 무비용. 발동 시 thread interrupt + retry 1회 호출.
- **OpenAI 호출 비용**: 변동 없음. 단 timeout 으로 끊긴 호출은 OpenAI 가 응답 완료 전 끊은 것이라 부분 과금 가능성 — 그러나 OpenAI 는 streaming 외에는 응답 완료 시점 과금이라 사실상 무비용.

## 7. 성공 기준

1. 6개 OpenAI 클라이언트 모두 `openai.http.connect-timeout-seconds` (10s) / `read-timeout-seconds` (60s) 적용
2. timeout 발동 시 `ResourceAccessException → RetryableOpenAiException` 분류로 retry 자동 트리거 (기존 Wave 5 classifier 동작 확인)
3. `OpenAiHttpProperties` 가 default (10/60) 적용 + 음수 / 0 입력 시 default 로 fallback
4. 기존 6개 client 의 모든 단위 테스트 통과 (생성자 인자 조정 후)
5. 환경변수 `OPENAI_HTTP_CONNECT_TIMEOUT_SECONDS` / `OPENAI_HTTP_READ_TIMEOUT_SECONDS` 로 override 가능
6. bootRun startup 시 `OpenAiHttpConfig` 빈 등록 + timeout 값 로깅 확인 (옵션)

## 8. 후속 작업 (이 spec 외)

- `KakaoOAuthClient`, `SnsMessageVerifier` timeout 적용 — 별도 spec
- OpenAI 클라이언트별 timeout 분리 (chat 90s / embedding 15s 등) — 운영 메트릭 누적 후
- Connection pool 도입 — `HttpComponentsClientHttpRequestFactory` 또는 `JdkClientHttpRequestFactory` 로 교체
- 다른 4개 OpenAI 클라이언트 (qna / eligibility / ingestion × 2) 에 retry 확장 — 별도 spec (#3 후속)
- MockWebServer 기반 통합 retry / timeout 시나리오 테스트 — 별도 spec
