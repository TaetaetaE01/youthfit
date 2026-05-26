# OpenAI RestClient Timeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 6개 OpenAI 클라이언트에 connect 10s / read 60s timeout 적용 — `RestClient.create()` 기본값(무한 wait) 으로 인한 hang + Wave 5 retry 의 영원한 block 위험 차단.

**Architecture:** 공유 `RestClient.Builder` 빈 (`@Configuration OpenAiHttpConfig`) 에서 `SimpleClientHttpRequestFactory` 의 connect/read timeout 설정. 6개 client 각각 `@RequiredArgsConstructor` 제거 후 명시적 생성자로 빌더 주입해 `build()`. timeout 발동 시 `SocketTimeoutException → ResourceAccessException → RetryableOpenAiException` 변환은 기존 `OpenAiErrorClassifier` 가 이미 처리 → 추가 코드 없이 retry 자동 트리거.

**Tech Stack:** Spring Boot 4.0.5, Java 21, Spring `RestClient` + `SimpleClientHttpRequestFactory`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-05-27-openai-restclient-timeout-design.md`

---

## File Structure

### 신규 파일
- `backend/src/main/java/com/youthfit/common/openai/OpenAiHttpProperties.java` — `openai.http.*` ConfigurationProperties record
- `backend/src/main/java/com/youthfit/common/openai/OpenAiHttpConfig.java` — `openAiRestClientBuilder` 빈 등록 (timeout 주입된 factory)
- `backend/src/test/java/com/youthfit/common/openai/OpenAiHttpPropertiesTest.java` — default / 음수 / 0 입력 fallback 단위 테스트

### 수정 파일
- `backend/src/main/resources/application.yml` — default profile `openai` 블록에 `http:` 하위 추가
- `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` — `@RequiredArgsConstructor` 제거 + 명시적 생성자
- `backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java` — 동일 패턴
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java` — 동일 패턴
- `backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java` — 동일 패턴
- `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java` — 동일 패턴
- `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodDisambiguator.java` — 동일 패턴
- 각 client 의 기존 테스트 — 생성자 인자 추가 (Builder mock)

---

## Task 1: OpenAiHttpProperties — TDD

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/openai/OpenAiHttpProperties.java`
- Test: `backend/src/test/java/com/youthfit/common/openai/OpenAiHttpPropertiesTest.java`

- [ ] **Step 1: Write failing test first**

`backend/src/test/java/com/youthfit/common/openai/OpenAiHttpPropertiesTest.java`:

```java
package com.youthfit.common.openai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiHttpPropertiesTest {

    @Test
    @DisplayName("양수 입력은 그대로 적용")
    void positiveValuesApply() {
        OpenAiHttpProperties p = new OpenAiHttpProperties(5, 30);

        assertThat(p.connectTimeoutSeconds()).isEqualTo(5);
        assertThat(p.readTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("0 이하 connect 는 default 10 으로 fallback")
    void zeroOrNegativeConnectFallsBack() {
        OpenAiHttpProperties zero = new OpenAiHttpProperties(0, 60);
        OpenAiHttpProperties negative = new OpenAiHttpProperties(-5, 60);

        assertThat(zero.connectTimeoutSeconds()).isEqualTo(10);
        assertThat(negative.connectTimeoutSeconds()).isEqualTo(10);
    }

    @Test
    @DisplayName("0 이하 read 는 default 60 으로 fallback")
    void zeroOrNegativeReadFallsBack() {
        OpenAiHttpProperties zero = new OpenAiHttpProperties(10, 0);
        OpenAiHttpProperties negative = new OpenAiHttpProperties(10, -1);

        assertThat(zero.readTimeoutSeconds()).isEqualTo(60);
        assertThat(negative.readTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("둘 다 0 이면 둘 다 default")
    void bothZeroFallBack() {
        OpenAiHttpProperties p = new OpenAiHttpProperties(0, 0);

        assertThat(p.connectTimeoutSeconds()).isEqualTo(10);
        assertThat(p.readTimeoutSeconds()).isEqualTo(60);
    }
}
```

- [ ] **Step 2: Verify compile fails**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew test --tests OpenAiHttpPropertiesTest`
Expected: compile error (OpenAiHttpProperties missing).

- [ ] **Step 3: Implement record**

`backend/src/main/java/com/youthfit/common/openai/OpenAiHttpProperties.java`:

```java
package com.youthfit.common.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 호출에 공통 적용할 HTTP timeout 설정.
 *
 * Why: RestClient.create() 기본값은 timeout 무한. retry 와 결합 시 thread 영원히 block 위험.
 * default 는 connect 10s / read 60s — gpt-4o-mini 평균 1-5s, 긴 partial 호출 ~30-40s 기준.
 */
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

- [ ] **Step 4: Verify tests pass**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew test --tests OpenAiHttpPropertiesTest`
Expected: 4 tests passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/common/openai/OpenAiHttpProperties.java \
        backend/src/test/java/com/youthfit/common/openai/OpenAiHttpPropertiesTest.java
git commit -m "$(cat <<'EOF'
feat(common): add OpenAiHttpProperties for shared timeout config

connectTimeoutSeconds / readTimeoutSeconds default 10 / 60.
음수 / 0 입력은 default 로 fallback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: OpenAiHttpConfig + application.yml

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/openai/OpenAiHttpConfig.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Create OpenAiHttpConfig**

`backend/src/main/java/com/youthfit/common/openai/OpenAiHttpConfig.java`:

```java
package com.youthfit.common.openai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 6개 OpenAI 클라이언트가 공유할 RestClient.Builder 빈.
 *
 * Why: RestClient.create() 기본 timeout 은 무한. Wave 5 retry 와 결합하면 thread 가
 * OpenAI 응답을 영원히 기다리는 동안 retry 가 다시 호출해 thread pool 고갈 위험.
 * 공유 빌더 + 단일 properties 로 6곳 일관 적용.
 */
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

- [ ] **Step 2: application.yml 에 openai.http 추가**

Read `backend/src/main/resources/application.yml` 의 `openai:` 블록 위치 (default profile, 약 63-85 행).

`openai.ingestion.period.max-body-chars` (85 행) 다음에 다음 4줄 추가:

```yaml
  http:
    connect-timeout-seconds: ${OPENAI_HTTP_CONNECT_TIMEOUT_SECONDS:10}
    read-timeout-seconds: ${OPENAI_HTTP_READ_TIMEOUT_SECONDS:60}
```

들여쓰기는 `openai:` 의 하위 항목 (`embedding:`, `chat:` 등) 과 동일 수준.

- [ ] **Step 3: 빌드 검증**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/common/openai/OpenAiHttpConfig.java \
        backend/src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
feat(common): add OpenAiHttpConfig sharing RestClient.Builder

@Bean openAiRestClientBuilder 가 SimpleClientHttpRequestFactory 에
OpenAiHttpProperties 의 connect/read timeout 을 주입. 6개 OpenAI 클라이언트가 이 빌더로 build.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: OpenAiChatClient 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- Modify: `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java` (생성자 인자 추가)
- Modify: `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientMetricTest.java` (생성자 인자 추가)

- [ ] **Step 1: import 추가**

`backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` 의 import 영역에 추가:

```java
import org.springframework.beans.factory.annotation.Qualifier;
```

(기존 imports 영역 적당한 위치)

- [ ] **Step 2: @RequiredArgsConstructor 제거 + 명시적 생성자**

클래스 선언 부분에서 `@RequiredArgsConstructor` 어노테이션 삭제 (라인 35 부근, `@Component` 와 `public class OpenAiChatClient` 사이).

`private final RestClient restClient = RestClient.create();` 라인 (약 300 행) 을 다음으로 변경:

```java
    private final RestClient restClient;
```

`private final ObjectMapper objectMapper = new ObjectMapper();` (약 301 행) 는 그대로 유지 (Lombok 자동 처리 대상 아님).

클래스 본문 시작부 (필드 선언 직후, `@Override` 들 직전) 에 명시적 생성자 추가:

```java
    public OpenAiChatClient(
            OpenAiChatProperties properties,
            ApplicationEventPublisher eventPublisher,
            TokenCounter tokenCounter,
            OpenAiErrorClassifier errorClassifier,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.tokenCounter = tokenCounter;
        this.errorClassifier = errorClassifier;
        this.restClient = restClientBuilder.build();
    }
```

`@Value("${guide.map-reduce.merge-cap-tokens:100000}") private int mergeCapTokens;` 는 필드 주입이라 그대로 유지 (생성자 인자 아님).

- [ ] **Step 3: 테스트 생성자 인자 추가**

`backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java` 에서 `new OpenAiChatClient(...)` 호출 위치마다 마지막 인자로 mock builder 추가. 다음 pattern 으로:

```java
import org.springframework.web.client.RestClient;

// ... 테스트 메서드 안 ...
RestClient.Builder builderMock = mock(RestClient.Builder.class);
RestClient restClientMock = mock(RestClient.class);
when(builderMock.build()).thenReturn(restClientMock);

OpenAiChatClient client = new OpenAiChatClient(
        properties, eventPublisher, tokenCounter, errorClassifier, builderMock);
```

또는 helper:
```java
private static RestClient.Builder mockBuilder() {
    RestClient.Builder builder = mock(RestClient.Builder.class);
    RestClient restClient = mock(RestClient.class);
    when(builder.build()).thenReturn(restClient);
    return builder;
}
```

각 `new OpenAiChatClient(...)` 호출 위치를 read 로 확인하고 동일 패턴 적용.

`OpenAiChatClientMetricTest.java` 도 동일 변경.

- [ ] **Step 4: 컴파일 + 테스트 회귀**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava test --tests 'com.youthfit.guide.infrastructure.external.*'`
Expected: BUILD SUCCESSFUL, 모든 guide infrastructure 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java \
        backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientMetricTest.java
git commit -m "$(cat <<'EOF'
feat(guide): inject openAiRestClientBuilder into OpenAiChatClient

@RequiredArgsConstructor 제거 후 명시적 생성자로 RestClient.Builder 주입.
이제 OpenAI chat 호출에 connect 10s / read 60s timeout 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: OpenAiEmbeddingClient 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java`
- Modify: `backend/src/test/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClientMetricTest.java`

- [ ] **Step 1: import 추가**

`backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java` 에 추가:

```java
import org.springframework.beans.factory.annotation.Qualifier;
```

- [ ] **Step 2: @RequiredArgsConstructor 제거 + 명시적 생성자**

클래스 어노테이션에서 `@RequiredArgsConstructor` 삭제.

`private final RestClient restClient = RestClient.create();` 라인 변경:

```java
    private final RestClient restClient;
```

필드 선언 직후 명시적 생성자 추가:

```java
    public OpenAiEmbeddingClient(
            OpenAiEmbeddingProperties properties,
            ApplicationEventPublisher eventPublisher,
            OpenAiErrorClassifier errorClassifier,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.errorClassifier = errorClassifier;
        this.restClient = restClientBuilder.build();
    }
```

- [ ] **Step 3: 테스트 생성자 인자 추가**

`OpenAiEmbeddingClientMetricTest.java` 에서 `new OpenAiEmbeddingClient(...)` 호출에 builder mock 추가 (Task 3 와 동일 패턴).

- [ ] **Step 4: 컴파일 + 테스트 회귀**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava test --tests 'com.youthfit.rag.infrastructure.external.*'`
Expected: BUILD SUCCESSFUL, rag infrastructure 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClient.java \
        backend/src/test/java/com/youthfit/rag/infrastructure/external/OpenAiEmbeddingClientMetricTest.java
git commit -m "$(cat <<'EOF'
feat(rag): inject openAiRestClientBuilder into OpenAiEmbeddingClient

OpenAI embedding 호출에 connect 10s / read 60s timeout 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: OpenAiQnaClient 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java`
- Modify: 기존 OpenAiQnaClient 관련 단위/통합 테스트 (있다면)

- [ ] **Step 1: 기존 테스트 검색**

Run: `find /Users/taetaetae/IdeaProjects/youthfit/backend/src/test -name "OpenAiQnaClient*.java"`
파일 발견 시 각 `new OpenAiQnaClient(...)` 호출 라인 확인 후 Step 3 에서 mock builder 추가.

- [ ] **Step 2: @RequiredArgsConstructor 제거 + 명시적 생성자**

`backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java`:

import 영역에 `import org.springframework.beans.factory.annotation.Qualifier;` 추가.

클래스 어노테이션에서 `@RequiredArgsConstructor` 삭제.

`private final RestClient restClient = RestClient.create();` 라인 변경:

```java
    private final RestClient restClient;
```

`private final ObjectMapper objectMapper = new ObjectMapper();` 는 그대로 유지.

필드 선언 직후 명시적 생성자 추가:

```java
    public OpenAiQnaClient(
            OpenAiQnaProperties properties,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.restClient = restClientBuilder.build();
    }
```

- [ ] **Step 3: 기존 테스트 인자 추가 (있다면)**

Step 1 에서 발견된 각 테스트 파일에서 `new OpenAiQnaClient(...)` 호출에 mock builder 인자 추가. Task 3 의 mockBuilder() helper 패턴 적용.

- [ ] **Step 4: 컴파일 + 테스트 회귀**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava test --tests 'com.youthfit.qna.infrastructure.external.*'`
Expected: BUILD SUCCESSFUL, qna infrastructure 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java
# 테스트 파일 있으면 함께 add
git commit -m "$(cat <<'EOF'
feat(qna): inject openAiRestClientBuilder into OpenAiQnaClient

OpenAI QnA 호출에 connect 10s / read 60s timeout 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: OpenAiEligibilityRuleClient 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java`
- Modify: 기존 OpenAiEligibilityRuleClient 관련 테스트 (있다면)

- [ ] **Step 1: 기존 테스트 검색**

Run: `find /Users/taetaetae/IdeaProjects/youthfit/backend/src/test -name "OpenAiEligibilityRuleClient*.java"`

- [ ] **Step 2: @RequiredArgsConstructor 제거 + 명시적 생성자**

`backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java`:

import 추가:
```java
import org.springframework.beans.factory.annotation.Qualifier;
```

클래스 어노테이션에서 `@RequiredArgsConstructor` 삭제.

`private final RestClient restClient = RestClient.create();` 변경:
```java
    private final RestClient restClient;
```

`private final ObjectMapper objectMapper = new ObjectMapper();` 그대로 유지.

명시적 생성자:
```java
    public OpenAiEligibilityRuleClient(
            OpenAiEligibilityRuleProperties properties,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.restClient = restClientBuilder.build();
    }
```

- [ ] **Step 3: 기존 테스트 인자 추가**

발견된 테스트 파일의 `new OpenAiEligibilityRuleClient(...)` 호출에 mock builder 추가.

- [ ] **Step 4: 컴파일 + 테스트 회귀**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava test --tests 'com.youthfit.eligibility.infrastructure.external.*'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/eligibility/infrastructure/external/OpenAiEligibilityRuleClient.java
git commit -m "$(cat <<'EOF'
feat(eligibility): inject openAiRestClientBuilder into OpenAiEligibilityRuleClient

OpenAI eligibility rule 추출 호출에 connect 10s / read 60s timeout 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: OpenAiPolicyPeriodExtractor 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java`
- Modify: 기존 테스트 (있다면)

- [ ] **Step 1: 기존 테스트 검색**

Run: `find /Users/taetaetae/IdeaProjects/youthfit/backend/src/test -name "OpenAiPolicyPeriodExtractor*.java"`

- [ ] **Step 2: @RequiredArgsConstructor 제거 + 명시적 생성자**

`backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java`:

import 추가:
```java
import org.springframework.beans.factory.annotation.Qualifier;
```

클래스 어노테이션에서 `@RequiredArgsConstructor` 삭제.

`private final RestClient restClient = RestClient.create();` 변경:
```java
    private final RestClient restClient;
```

명시적 생성자 (필드 순서: properties, objectMapper, eventPublisher 가 기존 final 순서):
```java
    public OpenAiPolicyPeriodExtractor(
            OpenAiPolicyPeriodProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.restClient = restClientBuilder.build();
    }
```

- [ ] **Step 3: 기존 테스트 인자 추가**

발견된 테스트 파일의 `new OpenAiPolicyPeriodExtractor(...)` 호출에 mock builder 추가.

- [ ] **Step 4: 컴파일 + 테스트 회귀**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava test --tests 'com.youthfit.ingestion.infrastructure.external.OpenAiPolicyPeriodExtractor*'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java
git commit -m "$(cat <<'EOF'
feat(ingestion): inject openAiRestClientBuilder into OpenAiPolicyPeriodExtractor

OpenAI policy period 추출 호출에 connect 10s / read 60s timeout 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: OpenAiPolicyPeriodDisambiguator 적용

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodDisambiguator.java`
- Modify: 기존 테스트 (있다면)

- [ ] **Step 1: 기존 테스트 검색**

Run: `find /Users/taetaetae/IdeaProjects/youthfit/backend/src/test -name "OpenAiPolicyPeriodDisambiguator*.java"`

- [ ] **Step 2: @RequiredArgsConstructor 제거 + 명시적 생성자**

`backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodDisambiguator.java`:

import 추가:
```java
import org.springframework.beans.factory.annotation.Qualifier;
```

클래스 어노테이션에서 `@RequiredArgsConstructor` 삭제.

`private final RestClient restClient = RestClient.create();` 변경:
```java
    private final RestClient restClient;
```

명시적 생성자 (필드 순서: properties, objectMapper, eventPublisher):
```java
    public OpenAiPolicyPeriodDisambiguator(
            OpenAiPolicyPeriodProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.restClient = restClientBuilder.build();
    }
```

- [ ] **Step 3: 기존 테스트 인자 추가**

발견된 테스트 파일의 `new OpenAiPolicyPeriodDisambiguator(...)` 호출에 mock builder 추가.

- [ ] **Step 4: 컴파일 + 테스트 회귀**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew compileJava test --tests 'com.youthfit.ingestion.infrastructure.external.OpenAiPolicyPeriodDisambiguator*'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodDisambiguator.java
git commit -m "$(cat <<'EOF'
feat(ingestion): inject openAiRestClientBuilder into OpenAiPolicyPeriodDisambiguator

OpenAI policy period disambiguator 호출에 connect 10s / read 60s timeout 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: 전체 검증 + bootRun startup

**Files:** (변경 없음, 검증만)

- [ ] **Step 1: 전체 빌드 + 단위 테스트**

Run: `cd /Users/taetaetae/IdeaProjects/youthfit/backend && ./gradlew clean compileJava test --tests 'com.youthfit.common.openai.*' --tests 'com.youthfit.guide.infrastructure.external.*' --tests 'com.youthfit.rag.infrastructure.external.*' --tests 'com.youthfit.qna.infrastructure.external.*' --tests 'com.youthfit.eligibility.infrastructure.external.*' --tests 'com.youthfit.ingestion.infrastructure.external.*'`

Expected: BUILD SUCCESSFUL, 모든 변경 영역 테스트 통과.

(postgres testcontainer 의존 통합 테스트가 환경 의존으로 실패할 수 있음 — 무관.)

- [ ] **Step 2: bootRun startup**

Run (background, 60초 limit): `cd /Users/taetaetae/IdeaProjects/youthfit/backend && SPRING_PROFILES_ACTIVE=local timeout 60 ./gradlew bootRun 2>&1 | tee /tmp/bootrun.log | head -300`

확인할 것:
- `Started YouthfitApplication` 로그 또는 의존성 에러 (DB 등) 직전까지 정상 진행
- `OpenAiHttpProperties` 바인딩 에러 없음
- `openAiRestClientBuilder` 빈 등록 관련 에러 없음
- 6개 OpenAI 클라이언트 빈 등록 에러 없음

DB 연결 실패는 무관.

- [ ] **Step 3: 종료 + 검증 결과만 기록**

bootRun 종료 후 결과:
- 빌드 / 컴파일 / 단위 테스트 PASS
- bootRun 어디서 멈췄는지 (정상 startup 또는 DB connection failure 등)
- 6개 OpenAI 클라이언트가 모두 `openAiRestClientBuilder` 빈 주입 받았는지 (BeanCreationException 없으면 OK)

- [ ] **Step 4: (커밋 없음 — 검증 task)**

검증만 수행하므로 별도 커밋 불필요.

---

## Verification Checklist

- [ ] `OpenAiHttpPropertiesTest` 4 tests 통과
- [ ] `OpenAiHttpConfig` 빈 등록 + bootRun startup 성공 (또는 DB 외 다른 에러 없음)
- [ ] 6개 OpenAI 클라이언트 명시적 생성자 + builder 주입 적용
- [ ] 각 client 의 기존 단위 테스트 회귀 없이 통과
- [ ] application.yml 의 `openai.http.connect-timeout-seconds` / `read-timeout-seconds` 환경변수 override 가능
- [ ] 커밋 8개 — Task 1, 2, 3, 4, 5, 6, 7, 8 각각 1개

## Self-Review

**Spec coverage:**
- G1 connect 10s / read 60s 적용 (6개 클라이언트) — Tasks 3-8 ✓
- G2 timeout → ResourceAccessException → RetryableOpenAiException 자동 retry — 기존 Wave 5 classifier 재사용, 추가 코드 없음 (회귀 안전 §5) ✓
- G3 단일 properties `openai.http.*` — Task 1+2 ✓
- G4 최악 latency 예측 가능 — spec §4 데이터 흐름에서 186s 명시 ✓
- C1 OpenAiHttpProperties record — Task 1 ✓
- C2 OpenAiHttpConfig + openAiRestClientBuilder 빈 — Task 2 ✓
- C3 6개 클라이언트 패턴 — Tasks 3-8 ✓
- C4 yaml `openai.http` 블록 — Task 2 ✓
- C5 properties 테스트 + 기존 client 테스트 회귀 — Task 1 + Tasks 3-8 의 Step 3 ✓

**Placeholder scan:**
- "있다면" 표현이 Task 5-8 에 등장 — 의도된 표현 (테스트 파일이 존재하지 않을 수 있는 client 들). Step 1 의 `find` 명령으로 실제 존재 여부 확인 후 Step 3 진행.
- 다른 모호한 표현 없음.

**Type consistency:**
- `@Qualifier("openAiRestClientBuilder")` 이름이 Task 2 의 `@Bean("openAiRestClientBuilder")` 와 정확히 일치
- `RestClient.Builder` 타입 일관 사용
- 각 client 생성자의 기존 필드 (properties, eventPublisher, errorClassifier, tokenCounter, objectMapper) 순서가 현재 코드와 일치
- `OpenAiHttpProperties` 의 메서드명 `connectTimeoutSeconds()` / `readTimeoutSeconds()` Task 1 정의와 Task 2 사용 일치

---

## 후속 작업 (이 plan 외)

- `KakaoOAuthClient`, `SnsMessageVerifier` timeout 적용 (별도 spec)
- 다른 4개 OpenAI 클라이언트 (qna / eligibility / ingestion × 2) 에 retry 확장 — #3 후속
- MockWebServer 기반 통합 retry + timeout 테스트
- OpenAI 별 timeout 분리 (chat 90s / embedding 15s)
- Connection pool 도입 (`HttpComponentsClientHttpRequestFactory` 또는 `JdkClientHttpRequestFactory`)
