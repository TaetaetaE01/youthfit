# OpenAI Retry / Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OpenAI 호출 (chat + embedding) 의 transient error (429 / 5xx / IOException) 를 Resilience4j 로 자동 재시도하여 partial 호출 skip 및 가이드 생성 실패를 줄인다.

**Architecture:** `RetryableOpenAiException` 도메인 예외 + `OpenAiErrorClassifier` 단일 진입점 분류 + `RetryAfterIntervalFunction` 으로 OpenAI `Retry-After` 헤더 존중 + `@Retry` annotation 적용. partial 호출은 retry 소진 후에도 catch + skip 동작 유지, merge / single-call / embedding 은 상위로 throw.

**Tech Stack:** Spring Boot 4.0.5, Java 21, Resilience4j 2.3.x, Micrometer (자동), JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-05-26-openai-retry-resilience-design.md`

---

## File Structure

### 신규 파일
- `backend/src/main/java/com/youthfit/common/exception/RetryableOpenAiException.java` — transient 실패 마커 예외
- `backend/src/main/java/com/youthfit/common/openai/OpenAiErrorClassifier.java` — raw 예외 분류기 + Retry-After 파싱
- `backend/src/main/java/com/youthfit/common/openai/RetryAfterIntervalFunction.java` — Resilience4j 의 intervalBiFunction 빈
- `backend/src/main/java/com/youthfit/common/openai/OpenAiRetryConfig.java` — RetryRegistry customizer
- `backend/src/test/java/com/youthfit/common/openai/OpenAiErrorClassifierTest.java` — 분류기 단위 테스트
- `backend/src/test/java/com/youthfit/common/openai/RetryAfterIntervalFunctionTest.java` — 인터벌 함수 단위 테스트

### 수정 파일
- `backend/build.gradle` — Resilience4j 의존성 추가
- `backend/src/main/resources/application.yml` — `resilience4j.retry.instances.*` 설정 + `management.endpoints.web.exposure.include` 에 `metrics` 추가
- `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` — `@Retry` annotation + try/catch + classifier 적용
- `backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java` — `@Retry` annotation + try/catch + classifier 적용
- `backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java:49-52` — catch 로그 메시지 보강

---

## Task 1: Gradle 의존성 추가

**Files:**
- Modify: `backend/build.gradle`

- [ ] **Step 1: Resilience4j 의존성 추가**

`backend/build.gradle` 의 `dependencies { ... }` 블록에 아래 3줄 추가 (44행 `implementation 'com.knuddels:jtokkit:1.1.0'` 다음 줄):

```groovy
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.3.0'
    implementation 'io.github.resilience4j:resilience4j-micrometer:2.3.0'
    implementation 'io.github.resilience4j:resilience4j-reactor:2.3.0'
```

- [ ] **Step 2: 빌드 검증**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/build.gradle
git commit -m "$(cat <<'EOF'
chore(deps): add Resilience4j for OpenAI retry

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: RetryableOpenAiException 도메인 예외 추가

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/exception/RetryableOpenAiException.java`

- [ ] **Step 1: 예외 클래스 작성**

```java
package com.youthfit.common.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * OpenAI 호출의 transient 실패 (429 / 5xx / IOException) 를 마킹하는 예외.
 * Resilience4j 는 이 타입에만 retry 한다.
 *
 * Why: 4xx (non-429) 같은 영구 실패와 transient 실패를 분리해야 비용/시간 낭비 없이
 * 의미있는 retry 만 수행할 수 있다.
 */
public class RetryableOpenAiException extends RuntimeException {

    private final Duration retryAfter;

    public RetryableOpenAiException(String message, Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
```

- [ ] **Step 2: 빌드 검증**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/exception/RetryableOpenAiException.java
git commit -m "$(cat <<'EOF'
feat(common): add RetryableOpenAiException marker

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: OpenAiErrorClassifier — 테스트 먼저 작성

**Files:**
- Test: `backend/src/test/java/com/youthfit/common/openai/OpenAiErrorClassifierTest.java`

- [ ] **Step 1: 분류기 단위 테스트 작성 (실패하는 테스트)**

```java
package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiErrorClassifierTest {

    private final OpenAiErrorClassifier classifier = new OpenAiErrorClassifier();

    @Test
    @DisplayName("429 with Retry-After 정수 초 헤더 → RetryableOpenAiException(retryAfter=5s)")
    void classifies429WithRetryAfterSeconds() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "5");
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                headers, "body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
        assertThat(((RetryableOpenAiException) classified).retryAfter())
                .contains(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("429 without Retry-After 헤더 → RetryableOpenAiException(retryAfter=null)")
    void classifies429WithoutRetryAfter() {
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
        assertThat(((RetryableOpenAiException) classified).retryAfter()).isEmpty();
    }

    @Test
    @DisplayName("503 Service Unavailable → RetryableOpenAiException(retryAfter=null)")
    void classifies5xx() {
        HttpServerErrorException raw = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
        assertThat(((RetryableOpenAiException) classified).retryAfter()).isEmpty();
    }

    @Test
    @DisplayName("401 Unauthorized → 원본 예외 그대로 re-throw")
    void rethrows4xxNon429() {
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isSameAs(raw);
    }

    @Test
    @DisplayName("ResourceAccessException (network timeout) → RetryableOpenAiException")
    void classifiesResourceAccessException() {
        ResourceAccessException raw = new ResourceAccessException(
                "I/O error", new SocketTimeoutException("read timeout"));

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
    }

    @Test
    @DisplayName("raw IOException → RetryableOpenAiException")
    void classifiesRawIOException() {
        IOException raw = new IOException("broken pipe");

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
    }

    @Test
    @DisplayName("NullPointerException 같은 비-HTTP 예외 → 원본 그대로 (RuntimeException 으로 래핑)")
    void rethrowsUnknownRuntimeException() {
        NullPointerException raw = new NullPointerException("npe");

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isSameAs(raw);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests OpenAiErrorClassifierTest`
Expected: 컴파일 에러 (OpenAiErrorClassifier 클래스 미존재)

- [ ] **Step 3: 다음 task 에서 구현 진행 (커밋 없음)**

---

## Task 4: OpenAiErrorClassifier 구현

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/openai/OpenAiErrorClassifier.java`

- [ ] **Step 1: 분류기 구현**

```java
package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * OpenAI raw 예외를 retry 가능한 transient 실패와 영구 실패로 분류한다.
 *
 * Why: 4xx (non-429) 영구 실패는 retry 해도 동일 응답을 받는다.
 * 비용/시간 낭비를 피하려면 transient 만 RetryableOpenAiException 으로 래핑한다.
 */
@Component
public class OpenAiErrorClassifier {

    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    public RuntimeException classify(Throwable raw) {
        if (raw instanceof HttpClientErrorException httpClient) {
            if (httpClient.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                Duration retryAfter = parseRetryAfter(httpClient).orElse(null);
                return new RetryableOpenAiException(
                        "OpenAI 429 Too Many Requests", httpClient, retryAfter);
            }
            return httpClient;
        }
        if (raw instanceof HttpServerErrorException httpServer) {
            return new RetryableOpenAiException(
                    "OpenAI 5xx " + httpServer.getStatusCode(), httpServer, null);
        }
        if (raw instanceof ResourceAccessException resourceAccess) {
            return new RetryableOpenAiException(
                    "OpenAI 네트워크 I/O 실패", resourceAccess, null);
        }
        if (raw instanceof IOException) {
            return new RetryableOpenAiException(
                    "OpenAI I/O 실패", raw, null);
        }
        if (raw instanceof RuntimeException rt) {
            return rt;
        }
        return new RuntimeException("알 수 없는 OpenAI 호출 실패", raw);
    }

    private Optional<Duration> parseRetryAfter(HttpClientErrorException e) {
        String value = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        // 정수 초 우선
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 0) return Optional.empty();
            return Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException ignored) {
            // HTTP-date fallback
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(value.trim(), HTTP_DATE);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
            Duration diff = Duration.between(now, when);
            if (diff.isNegative()) return Optional.empty();
            return Optional.of(diff);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 모두 통과 확인**

Run: `cd backend && ./gradlew test --tests OpenAiErrorClassifierTest`
Expected: 7 tests passed (429-with-header, 429-no-header, 5xx, 4xx, ResourceAccessException, IOException, NPE)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/openai/OpenAiErrorClassifier.java \
        backend/src/test/java/com/youthfit/common/openai/OpenAiErrorClassifierTest.java
git commit -m "$(cat <<'EOF'
feat(common): add OpenAiErrorClassifier with Retry-After parsing

429 / 5xx / IOException 만 RetryableOpenAiException 으로 래핑.
4xx non-429 는 원본 그대로 re-throw 하여 retry 대상에서 제외.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: RetryAfterIntervalFunction — 테스트 먼저 작성

**Files:**
- Test: `backend/src/test/java/com/youthfit/common/openai/RetryAfterIntervalFunctionTest.java`

- [ ] **Step 1: 인터벌 함수 단위 테스트 작성**

```java
package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterIntervalFunctionTest {

    private final RetryAfterIntervalFunction fn = new RetryAfterIntervalFunction();

    @Test
    @DisplayName("RetryableOpenAiException with retryAfter=5s → 5000ms 그대로")
    void retryAfterHeaderTakesPrecedence() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "429", new RuntimeException(), Duration.ofSeconds(5));

        long waitMs = fn.apply(1, ex);

        assertThat(waitMs).isEqualTo(5000L);
    }

    @Test
    @DisplayName("RetryableOpenAiException with retryAfter=null → exponential fallback (attempt 1: 1000-3000ms)")
    void fallbackExponentialAttempt1() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "5xx", new RuntimeException(), null);

        for (int i = 0; i < 20; i++) {
            long waitMs = fn.apply(1, ex);
            assertThat(waitMs).isBetween(1000L, 3000L);
        }
    }

    @Test
    @DisplayName("attempt 2 fallback → 2000-6000ms (2배 backoff)")
    void fallbackExponentialAttempt2() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "5xx", new RuntimeException(), null);

        for (int i = 0; i < 20; i++) {
            long waitMs = fn.apply(2, ex);
            assertThat(waitMs).isBetween(2000L, 6000L);
        }
    }

    @Test
    @DisplayName("retryAfter=60s 는 30s 로 clamp")
    void clampsToThirtySeconds() {
        RetryableOpenAiException ex = new RetryableOpenAiException(
                "429", new RuntimeException(), Duration.ofSeconds(60));

        long waitMs = fn.apply(1, ex);

        assertThat(waitMs).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("비-RetryableOpenAiException → exponential fallback")
    void unknownExceptionUsesFallback() {
        RuntimeException ex = new RuntimeException("unknown");

        long waitMs = fn.apply(1, ex);

        assertThat(waitMs).isBetween(1000L, 3000L);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests RetryAfterIntervalFunctionTest`
Expected: 컴파일 에러 (RetryAfterIntervalFunction 미존재)

- [ ] **Step 3: 다음 task 에서 구현**

---

## Task 6: RetryAfterIntervalFunction 구현

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/openai/RetryAfterIntervalFunction.java`

- [ ] **Step 1: 인터벌 함수 구현**

```java
package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

/**
 * Resilience4j RetryConfig 의 intervalBiFunction 으로 주입할 대기 시간 결정 함수.
 *
 * Why: OpenAI 429 응답에 Retry-After 헤더가 있으면 그 값을 우선 사용해
 * thundering herd 와 즉시 재요청을 방지한다. 헤더가 없거나 5xx/IO 인 경우
 * exponential backoff + jitter 로 fallback.
 *
 * Note: 단일 wait 는 30초로 clamp 한다.
 */
@Component
public class RetryAfterIntervalFunction implements BiFunction<Integer, Throwable, Long> {

    private static final long BASE_MS = 2000L;
    private static final double MULTIPLIER = 2.0;
    private static final double JITTER = 0.5;
    private static final long MAX_WAIT_MS = 30_000L;

    @Override
    public Long apply(Integer attempt, Throwable lastException) {
        if (lastException instanceof RetryableOpenAiException ex) {
            Duration override = ex.getRetryAfter().orElse(null);
            if (override != null) {
                return clamp(override.toMillis());
            }
        }
        return clamp(exponentialWithJitter(attempt));
    }

    private long exponentialWithJitter(int attempt) {
        double base = BASE_MS * Math.pow(MULTIPLIER, attempt - 1.0);
        double jitterFactor = 1.0 - JITTER + ThreadLocalRandom.current().nextDouble() * 2 * JITTER;
        return (long) (base * jitterFactor);
    }

    private long clamp(long waitMs) {
        return Math.min(Math.max(waitMs, 0L), MAX_WAIT_MS);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 모두 통과 확인**

Run: `cd backend && ./gradlew test --tests RetryAfterIntervalFunctionTest`
Expected: 5 tests passed

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/openai/RetryAfterIntervalFunction.java \
        backend/src/test/java/com/youthfit/common/openai/RetryAfterIntervalFunctionTest.java
git commit -m "$(cat <<'EOF'
feat(common): add RetryAfterIntervalFunction respecting Retry-After

Retry-After 헤더가 있으면 우선 사용, 없으면 exponential + jitter fallback.
모든 wait 는 30s 로 clamp.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Resilience4j Retry 설정 (yaml + config customizer)

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/youthfit/common/openai/OpenAiRetryConfig.java`

- [ ] **Step 1: application.yml 설정 추가**

`backend/src/main/resources/application.yml` 28-37 행의 `management:` 블록을 다음으로 교체:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  metrics:
    distribution:
      percentiles-histogram:
        resilience4j.retry.calls: true
```

또한 같은 파일 끝 (244 행 이후) 에 `resilience4j` 블록 추가:

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
```

- [ ] **Step 2: RetryRegistry customizer 빈 생성**

```java
package com.youthfit.common.openai;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * yaml 으로 정의한 openai-chat / openai-embedding instance 에 RetryAfterIntervalFunction 을 주입한다.
 *
 * Why: yaml 만으로는 BiFunction<Integer, Throwable, Long> 동적 인터벌을 표현할 수 없다.
 * Resilience4j 의 RetryConfigCustomizer 로 instance 별 커스터마이즈.
 */
@Configuration
public class OpenAiRetryConfig {

    @Bean
    public RetryConfigCustomizer openAiChatRetryCustomizer(RetryAfterIntervalFunction fn) {
        return RetryConfigCustomizer.of("openai-chat", builder -> builder.intervalBiFunction(
                (attempt, eitherResultOrException) -> {
                    Throwable t = eitherResultOrException.getLeft();
                    return fn.apply(attempt, t);
                }
        ));
    }

    @Bean
    public RetryConfigCustomizer openAiEmbeddingRetryCustomizer(RetryAfterIntervalFunction fn) {
        return RetryConfigCustomizer.of("openai-embedding", builder -> builder.intervalBiFunction(
                (attempt, eitherResultOrException) -> {
                    Throwable t = eitherResultOrException.getLeft();
                    return fn.apply(attempt, t);
                }
        ));
    }
}
```

- [ ] **Step 3: 빌드 검증**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: bootRun 으로 context 부팅 확인**

Run (background): `cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`
Expected: "Started YouthfitApplication" 로그가 나타나고 startup error 없음. 확인 후 종료.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/application.yml \
        backend/src/main/java/com/youthfit/common/openai/OpenAiRetryConfig.java
git commit -m "$(cat <<'EOF'
feat(common): wire Resilience4j retry config for openai-chat/embedding

yaml 에 두 instance 정의 + RetryConfigCustomizer 로 intervalBiFunction 주입.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: OpenAiChatClient 에 @Retry + classifier 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`

- [ ] **Step 1: import 추가**

`backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` 의 26 행 (`import tools.jackson.databind.ObjectMapper;`) 다음에 추가:

```java
import com.youthfit.common.openai.OpenAiErrorClassifier;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
```

- [ ] **Step 2: OpenAiErrorClassifier 필드 주입**

294 행 `private final TokenCounter tokenCounter;` 다음 줄에 추가:

```java
    private final OpenAiErrorClassifier errorClassifier;
```

`@RequiredArgsConstructor` 를 사용하므로 생성자 수정 불필요. Lombok 이 자동 처리.

- [ ] **Step 3: 4개 public method 에 @Retry 적용**

302 행 `public GuideContent generateGuide(...)` 직전에 `@Retry(name = "openai-chat")` 추가. 다음 메서드들에도 동일하게:

```java
    @Override
    @Retry(name = "openai-chat")
    public GuideContent generateGuide(GuideGenerationInput input) {
        return callChatCompletion(SYSTEM_PROMPT, buildUserMessage(input), input.policyId());
    }

    @Override
    @Retry(name = "openai-chat")
    public GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages) {
        return callChatCompletion(SYSTEM_PROMPT, buildUserMessageWithFeedback(input, feedbackMessages), input.policyId());
    }

    @Override
    @Retry(name = "openai-chat")
    public GuideContent generatePartialGuide(GuideGenerationInput input) {
        return callChatCompletion(
                PARTIAL_SYSTEM_PROMPT_PREFIX + SYSTEM_PROMPT,
                buildUserMessage(input),
                input.policyId()
        );
    }

    @Override
    @Retry(name = "openai-chat")
    public GuideContent mergePartialGuides(GuideGenerationInput input, List<GuideContent> partials) {
        String userMessage = buildMergeUserMessage(input, partials);
        int total = tokenCounter.countTokens(MERGE_SYSTEM_PROMPT + userMessage, properties.getModel());
        if (total > mergeCapTokens) {
            log.error("merge 호출 prompt 한도 초과: policyId={}, tokens={}, cap={}, partials={}",
                    input.policyId(), total, mergeCapTokens, partials.size());
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR,
                    "merge 호출 prompt 가 token cap 을 초과: policyId=" + input.policyId() + ", tokens=" + total);
        }
        return callChatCompletion(MERGE_SYSTEM_PROMPT, userMessage, input.policyId());
    }
```

- [ ] **Step 4: callChatCompletion 의 restClient 호출을 try/catch 로 감싸 classifier 적용**

333-362 행의 `private GuideContent callChatCompletion(...)` 메서드 본문에서 `restClient.post()` 블록을 try/catch 로 감싼다. 변경 전:

```java
        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
```

변경 후:

```java
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            log.warn("OpenAI Chat API 호출 실패 (분류 → retry 판정): policyId={}, status={}, msg={}",
                    policyId,
                    e instanceof org.springframework.web.client.RestClientResponseException rce
                            ? rce.getStatusCode() : "n/a",
                    e.getMessage());
            throw errorClassifier.classify(e);
        }
```

- [ ] **Step 5: 빌드 + 기존 테스트 회귀 확인**

Run: `cd backend && ./gradlew compileJava test --tests 'com.youthfit.guide.*'`
Expected: BUILD SUCCESSFUL, guide 모듈 테스트 모두 통과 (기존 동작 보존)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java
git commit -m "$(cat <<'EOF'
feat(guide): wire @Retry + classifier on OpenAiChatClient

generateGuide / regenerateWithFeedback / generatePartialGuide / mergePartialGuides 4개 진입점에 @Retry(name="openai-chat") 적용. restClient.post() 호출은 try/catch 로 감싸 OpenAiErrorClassifier 로 분류 후 throw.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: OpenAiEmbeddingClient 에 @Retry + classifier 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java`

- [ ] **Step 1: import 추가**

`backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java` 의 15 행 (`import org.springframework.web.client.RestClient;`) 다음에 추가:

```java
import com.youthfit.common.openai.OpenAiErrorClassifier;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
```

- [ ] **Step 2: OpenAiErrorClassifier 필드 주입**

30 행 `private final ApplicationEventPublisher eventPublisher;` 다음 줄에 추가:

```java
    private final OpenAiErrorClassifier errorClassifier;
```

`@RequiredArgsConstructor` 자동 처리.

- [ ] **Step 3: embedBatch 에 @Retry 추가**

39 행 `public List<float[]> embedBatch(...)` 직전에:

```java
    @Override
    @Retry(name = "openai-embedding")
    public List<float[]> embedBatch(List<String> texts) {
```

(`embed` 는 내부적으로 `embedBatch` 호출이라 AOP self-invocation 문제. `embed` 도 외부 진입점이라면 별도 annotation 도 추가 — 호출자가 `EmbeddingProvider` 인터페이스로 주입받아 외부 호출하면 정상 동작. `embed → embedBatch` 는 self-invocation 이라 retry 적용 안 되지만 `embedBatch` 자체가 retry 되므로 결과적으로 보호됨.)

- [ ] **Step 4: restClient 호출을 try/catch 로 감싸기**

51-57 행의 `restClient.post()` 블록 변경:

변경 전:
```java
        JsonNode response = restClient.post()
                .uri(EMBEDDINGS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
```

변경 후:
```java
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(EMBEDDINGS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            log.warn("OpenAI Embedding API 호출 실패 (분류 → retry 판정): status={}, msg={}",
                    e instanceof org.springframework.web.client.RestClientResponseException rce
                            ? rce.getStatusCode() : "n/a",
                    e.getMessage());
            throw errorClassifier.classify(e);
        }
```

- [ ] **Step 5: 빌드 + rag 모듈 테스트 회귀 확인**

Run: `cd backend && ./gradlew test --tests 'com.youthfit.rag.*'`
Expected: BUILD SUCCESSFUL, 기존 rag 테스트 모두 통과

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java
git commit -m "$(cat <<'EOF'
feat(rag): wire @Retry + classifier on OpenAiEmbeddingClient

embedBatch 진입점에 @Retry(name="openai-embedding") 적용. restClient.post() 호출은 try/catch 로 감싸 OpenAiErrorClassifier 로 분류 후 throw.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: MapReduceGuideOrchestrator 로그 메시지 보강 + 전체 검증

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java`

- [ ] **Step 1: catch 로그 메시지 보강**

49-52 행 변경 전:
```java
            } catch (Exception e) {
                log.warn("부분 가이드 호출 실패 (skip): policyId={}, groupIndex={}, err={}",
                        input.policyId(), i, e.getMessage());
            }
```

변경 후:
```java
            } catch (Exception e) {
                log.warn("부분 가이드 호출 실패 (retry 소진 또는 영구 실패 - skip): policyId={}, groupIndex={}, err={}",
                        input.policyId(), i, e.getMessage());
            }
```

- [ ] **Step 2: 전체 빌드 + 회귀 테스트**

Run: `cd backend && ./gradlew clean build -x integrationTest`
Expected: BUILD SUCCESSFUL, 모든 단위 테스트 통과

(integrationTest 가 별도 task 인지 unknown - 없으면 `./gradlew clean build` 그대로. postgres 의존 통합 테스트 8개 실패는 기존 환경 문제로 무관.)

- [ ] **Step 3: bootRun 으로 startup 검증**

Run: `cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`
Expected: "Started YouthfitApplication" + retry instance 등록 로그 (`Retry 'openai-chat'`, `Retry 'openai-embedding'` 가 보임)

- [ ] **Step 4: actuator metrics 엔드포인트 확인 (옵션)**

`curl http://localhost:8080/actuator/metrics/resilience4j.retry.calls` 호출하여 4개 kind 가 0 카운트로 노출되는지 확인.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java
git commit -m "$(cat <<'EOF'
chore(guide): clarify partial skip log after retry exhaustion

retry 가 client 레이어에서 끝나므로 여기 catch 에 도달한 건 retry 소진 또는 영구 실패다. 로그 메시지로 명시.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Verification Checklist

- [ ] `OpenAiErrorClassifierTest` 7 tests 통과
- [ ] `RetryAfterIntervalFunctionTest` 5 tests 통과
- [ ] `./gradlew compileJava` 통과
- [ ] `./gradlew test --tests 'com.youthfit.guide.*'` 통과 (기존 회귀 없음)
- [ ] `./gradlew test --tests 'com.youthfit.rag.*'` 통과 (기존 회귀 없음)
- [ ] `./gradlew bootRun` startup 성공, `Retry 'openai-chat'` / `Retry 'openai-embedding'` 등록 로그 확인
- [ ] `/actuator/metrics/resilience4j.retry.calls` 노출 확인
- [ ] 커밋 10개: build.gradle / RetryableOpenAiException / classifier / interval function / config (yaml+customizer) / chat client / embedding client / orchestrator log

## Self-Review

**Spec coverage:**
- G1 transient retry: Task 7, 8, 9 ✓
- G2 Retry-After 존중: Task 5, 6 (interval function) ✓
- G3 메트릭 노출: Task 7 (yaml `management.metrics.distribution`) ✓
- G4 4xx 즉시 실패: Task 3, 4 (classifier 가 원본 re-throw) ✓
- 컴포넌트 C1 RetryableOpenAiException: Task 2 ✓
- 컴포넌트 C2 OpenAiErrorClassifier: Task 3, 4 ✓
- 컴포넌트 C3 application.yml: Task 7 ✓
- 컴포넌트 C4 RetryAfterIntervalFunction: Task 5, 6 ✓
- 컴포넌트 C5 클라이언트 수정: Task 8, 9 ✓
- 컴포넌트 C6 Orchestrator 로그: Task 10 ✓
- 컴포넌트 C7 메트릭 노출: Task 7 ✓
- 컴포넌트 C8 테스트: Task 3, 5 (단위 — MockWebServer 통합테스트는 RestClient builder 리팩토링 필요해 별도 작업으로 분리)

**Placeholder scan:** 없음. 모든 step 에 실제 코드/명령어 포함.

**Type consistency:**
- `RetryableOpenAiException(String, Throwable, Duration)` 시그니처 — Task 2 / 3 / 4 / 5 / 6 모두 일치
- `OpenAiErrorClassifier.classify(Throwable)` → `RuntimeException` — Task 4 / 8 / 9 일치
- `RetryAfterIntervalFunction.apply(Integer, Throwable)` → `Long` — Task 6 / 7 일치
- `@Retry(name = "openai-chat" | "openai-embedding")` — yaml instance 이름과 Task 8 / 9 일치

---

## 후속 작업 (이 plan 외)

- MockWebServer 기반 통합 retry 테스트 (RestClient builder 리팩토링 필요)
- CircuitBreaker / RateLimiter 도입
- partial 호출 병렬화
- 영구 실패 정책 deferred retry (n8n force-enrich workflow 결합)
