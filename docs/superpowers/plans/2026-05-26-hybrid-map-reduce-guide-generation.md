# Hybrid Map-Reduce 가이드 생성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 첨부 텍스트가 큰 정책에서 단일 LLM 호출이 OpenAI context window(128K) / TPM(200K) 한도를 초과해 가이드 생성이 silent fail 하는 문제를 해결한다. 작은 정책은 현재 단일 호출 흐름 유지, 큰 정책은 청크를 token budget 별로 그룹화 → partial 가이드 N개 → merge 통합 호출 1개의 map-reduce 패턴으로 처리.

**Architecture:** `GuideGenerationService` 진입점에서 `GuideStrategySelector` 가 input token 수를 측정하여 `SINGLE_CALL` (현행 그대로) 또는 `MAP_REDUCE` 분기. `MAP_REDUCE` 는 `MapReduceGuideOrchestrator` 가 청크 그룹화 + partial 호출 + merge 호출을 조율. `GuideLlmProvider` 인터페이스에 `generatePartialGuide`, `mergePartialGuides` 두 메서드 추가. 새 컴포넌트 5개 (TokenCounter, enum, selector, orchestrator, provider 확장), 기존 single-call 경로 무변경 — 51개 기존 가이드 회귀 위험 0.

**Tech Stack:** Java 21, Spring Boot 4.x, jtokkit 1.1.0 (OpenAI 토큰 카운팅), JUnit 5 + Mockito, 기존 RestClient/Jackson.

**Spec:** `docs/superpowers/specs/2026-05-26-hybrid-map-reduce-guide-generation-design.md`

---

## File Structure

| 작업 | 경로 | 책임 |
|---|---|---|
| Modify | `backend/build.gradle` | jtokkit 의존성 추가 |
| Create | `backend/src/main/java/com/youthfit/common/util/TokenCounter.java` | jtokkit 래퍼, 모델→encoder 매핑, `int countTokens(text, model)` |
| Create | `backend/src/test/java/com/youthfit/common/util/TokenCounterTest.java` | 알려진 텍스트 → 알려진 토큰 수 검증 |
| Create | `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationStrategy.java` | enum `SINGLE_CALL`, `MAP_REDUCE` |
| Modify | `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` | `withChunks(List<ChunkInput>)` 헬퍼 추가 |
| Create | `backend/src/main/java/com/youthfit/guide/application/service/GuideStrategySelector.java` | input → strategy. 임계값 (default 80K input tokens) |
| Create | `backend/src/test/java/com/youthfit/guide/application/service/GuideStrategySelectorTest.java` | 임계값 경계 (79K → SINGLE, 80K → MAP_REDUCE) |
| Modify | `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java` | `generatePartialGuide`, `mergePartialGuides` 메서드 추가 |
| Modify | `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` | 두 신규 메서드 구현 + partial/merge system prompt prefix 정의 |
| Create | `backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java` | 청크 그룹화 + partial 호출 N번 + merge 호출 1번 조율 |
| Create | `backend/src/test/java/com/youthfit/guide/application/service/MapReduceGuideOrchestratorTest.java` | mocked LlmProvider — 그룹화 + 호출 횟수 + 부분 실패 시나리오 |
| Modify | `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` | strategy 분기 + orchestrator 위임 |
| Modify | `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java` | SINGLE_CALL 경로 회귀 + MAP_REDUCE 경로 분기 위임 검증 |

---

## Task 1: jtokkit 의존성 + TokenCounter

**Files:**
- Modify: `backend/build.gradle:22-50` (dependencies 블록)
- Create: `backend/src/main/java/com/youthfit/common/util/TokenCounter.java`
- Test: `backend/src/test/java/com/youthfit/common/util/TokenCounterTest.java`

- [ ] **Step 1.1: jtokkit 의존성 추가**

`backend/build.gradle` 의 `dependencies {` 블록 내 적절한 위치 (다른 외부 라이브러리 옆) 에 한 줄 추가:

```gradle
implementation 'com.knuddels:jtokkit:1.1.0'
```

- [ ] **Step 1.2: gradle sync 확인**

Run: `cd backend && ./gradlew dependencies --configuration compileClasspath | grep jtokkit`
Expected: `com.knuddels:jtokkit:1.1.0` 한 줄 출력

- [ ] **Step 1.3: 실패 테스트 작성**

Create `backend/src/test/java/com/youthfit/common/util/TokenCounterTest.java`:

```java
package com.youthfit.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCounterTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    @DisplayName("빈 문자열은 0 토큰")
    void emptyStringZeroTokens() {
        assertThat(counter.countTokens("", "gpt-4o-mini")).isZero();
    }

    @Test
    @DisplayName("gpt-4o-mini 는 o200k_base encoder 로 짧은 영어 텍스트를 토큰화한다")
    void shortEnglishTextHasExpectedTokens() {
        // "hello world" → o200k_base 기준 2 토큰 (jtokkit 공식 값)
        int tokens = counter.countTokens("hello world", "gpt-4o-mini");
        assertThat(tokens).isEqualTo(2);
    }

    @Test
    @DisplayName("한글 텍스트는 영어보다 토큰 밀도가 높다 (실제 사용 패턴 확인)")
    void koreanTextHasMoreTokensThanEnglish() {
        int en = counter.countTokens("Hello world this is a test sentence.", "gpt-4o-mini");
        int ko = counter.countTokens("안녕하세요 이것은 테스트 문장입니다.", "gpt-4o-mini");
        assertThat(ko).isGreaterThan(en);
    }

    @Test
    @DisplayName("text-embedding-3-small 도 cl100k_base 로 토큰화 가능")
    void embeddingModelTokenization() {
        int tokens = counter.countTokens("test text", "text-embedding-3-small");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    @DisplayName("알 수 없는 모델은 IllegalArgumentException")
    void unknownModelThrows() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> counter.countTokens("text", "unknown-model"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 1.4: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.common.util.TokenCounterTest"`
Expected: FAIL (TokenCounter 클래스 없음 — compile error)

- [ ] **Step 1.5: TokenCounter 구현**

Create `backend/src/main/java/com/youthfit/common/util/TokenCounter.java`:

```java
package com.youthfit.common.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * OpenAI 모델용 토큰 카운터.
 * jtokkit 의 encoder 를 모델 이름별로 매핑한다.
 */
@Component
public class TokenCounter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /** o200k_base 를 쓰는 모델들 (gpt-4o 계열 + mini). */
    private static final Set<String> O200K_MODELS = Set.of(
            "gpt-4o", "gpt-4o-mini", "gpt-4o-2024-08-06"
    );

    /** cl100k_base 를 쓰는 모델들 (legacy gpt-4-turbo, gpt-3.5, embeddings). */
    private static final Set<String> CL100K_MODELS = Set.of(
            "gpt-4-turbo", "gpt-3.5-turbo",
            "text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002"
    );

    private static final Map<EncodingType, Encoding> ENCODER_CACHE = Map.of(
            EncodingType.O200K_BASE, REGISTRY.getEncoding(EncodingType.O200K_BASE),
            EncodingType.CL100K_BASE, REGISTRY.getEncoding(EncodingType.CL100K_BASE)
    );

    public int countTokens(String text, String model) {
        if (text == null || text.isEmpty()) return 0;
        Encoding encoding = encoderFor(model);
        return encoding.countTokens(text);
    }

    private Encoding encoderFor(String model) {
        if (O200K_MODELS.contains(model)) {
            return ENCODER_CACHE.get(EncodingType.O200K_BASE);
        }
        if (CL100K_MODELS.contains(model)) {
            return ENCODER_CACHE.get(EncodingType.CL100K_BASE);
        }
        throw new IllegalArgumentException("Unsupported model for token counting: " + model);
    }
}
```

- [ ] **Step 1.6: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.common.util.TokenCounterTest"`
Expected: 5 tests passed

만약 Step 1.3 의 "hello world" → 2 토큰 가정이 실제와 다르면 (jtokkit 버전 차이로 1 또는 3 가능), 실제 결과를 assertThat 의 isEqualTo 값으로 업데이트.

- [ ] **Step 1.7: 커밋**

```bash
git add backend/build.gradle backend/src/main/java/com/youthfit/common/util/TokenCounter.java backend/src/test/java/com/youthfit/common/util/TokenCounterTest.java
git commit -m "feat(common): add TokenCounter wrapping jtokkit for OpenAI models"
```

---

## Task 2: GuideGenerationStrategy enum + GuideStrategySelector

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationStrategy.java`
- Create: `backend/src/main/java/com/youthfit/guide/application/service/GuideStrategySelector.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/service/GuideStrategySelectorTest.java`

- [ ] **Step 2.1: enum 생성**

Create `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationStrategy.java`:

```java
package com.youthfit.guide.application.dto.command;

public enum GuideGenerationStrategy {
    SINGLE_CALL,
    MAP_REDUCE
}
```

- [ ] **Step 2.2: 실패 테스트 작성**

Create `backend/src/test/java/com/youthfit/guide/application/service/GuideStrategySelectorTest.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.command.GuideGenerationInput.ChunkInput;
import com.youthfit.guide.application.dto.command.GuideGenerationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideStrategySelectorTest {

    private final TokenCounter tokenCounter = new TokenCounter();
    private final GuideStrategySelector selector =
            new GuideStrategySelector(tokenCounter, "gpt-4o-mini", 80_000);

    @Test
    @DisplayName("input tokens 가 임계값 미만이면 SINGLE_CALL")
    void belowThresholdSingleCall() {
        GuideGenerationInput input = buildInput("짧은 본문", List.of());
        assertThat(selector.select(input)).isEqualTo(GuideGenerationStrategy.SINGLE_CALL);
    }

    @Test
    @DisplayName("input tokens 가 임계값 이상이면 MAP_REDUCE")
    void atOrAboveThresholdMapReduce() {
        // 임계값 80K 를 확실히 넘기는 큰 청크 생성
        String big = "가".repeat(200_000);   // 한글 200K chars ≈ 100K+ tokens
        GuideGenerationInput input = buildInput("본문", List.of(
                new ChunkInput(big, null, null, null, "BODY")
        ));
        assertThat(selector.select(input)).isEqualTo(GuideGenerationStrategy.MAP_REDUCE);
    }

    private GuideGenerationInput buildInput(String body, List<ChunkInput> chunks) {
        return new GuideGenerationInput(
                1L, "title", 2025, "summary", body, null, null, null, null, null,
                null, chunks, null);
    }
}
```

- [ ] **Step 2.3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.service.GuideStrategySelectorTest"`
Expected: FAIL (`GuideStrategySelector` 없음 — compile error)

- [ ] **Step 2.4: GuideStrategySelector 구현**

Create `backend/src/main/java/com/youthfit/guide/application/service/GuideStrategySelector.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.command.GuideGenerationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GuideGenerationInput 의 토큰 크기에 따라 단일 호출 vs map-reduce 분기.
 *
 * 임계값(default 80K) 은 gpt-4o-mini context window 128K 의 60% 여유.
 * system prompt(~2K) + meta(~3K) + 응답 max_tokens(~10K) 를 더해도 128K 이하 보장.
 */
@Component
public class GuideStrategySelector {

    private final TokenCounter tokenCounter;
    private final String model;
    private final int thresholdTokens;

    public GuideStrategySelector(
            TokenCounter tokenCounter,
            @Value("${openai.chat.model:gpt-4o-mini}") String model,
            @Value("${guide.map-reduce.threshold-tokens:80000}") int thresholdTokens) {
        this.tokenCounter = tokenCounter;
        this.model = model;
        this.thresholdTokens = thresholdTokens;
    }

    public GuideGenerationStrategy select(GuideGenerationInput input) {
        int tokens = tokenCounter.countTokens(input.combinedSourceText(), model);
        return tokens >= thresholdTokens
                ? GuideGenerationStrategy.MAP_REDUCE
                : GuideGenerationStrategy.SINGLE_CALL;
    }
}
```

- [ ] **Step 2.5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.service.GuideStrategySelectorTest"`
Expected: 2 tests passed

- [ ] **Step 2.6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationStrategy.java backend/src/main/java/com/youthfit/guide/application/service/GuideStrategySelector.java backend/src/test/java/com/youthfit/guide/application/service/GuideStrategySelectorTest.java
git commit -m "feat(guide): add GuideStrategySelector with token-based map-reduce threshold"
```

---

## Task 3: GuideGenerationInput.withChunks 헬퍼

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`
- Test: 기존 `GuideGenerationInputTest.java` 가 있으면 추가, 없으면 신규 생성

- [ ] **Step 3.1: 기존 테스트 위치 확인**

Run: `find backend/src/test -name "GuideGenerationInput*.java"`
Expected: 0 또는 1개

기존 없으면 신규: `backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java`

- [ ] **Step 3.2: 실패 테스트 작성**

Append (또는 신규 파일에) 다음 테스트:

```java
package com.youthfit.guide.application.dto.command;

import com.youthfit.guide.application.dto.command.GuideGenerationInput.ChunkInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationInputTest {

    @Test
    @DisplayName("withChunks 는 다른 모든 필드 보존하고 chunks 만 교체한다")
    void withChunksReplacesOnlyChunks() {
        ChunkInput c1 = new ChunkInput("c1", null, null, null, "BODY");
        ChunkInput c2 = new ChunkInput("c2", null, null, null, "BODY");
        GuideGenerationInput original = new GuideGenerationInput(
                7L, "title", 2025, "summary", "body",
                "target", "criteria", "content", "010-0", "org",
                null, List.of(c1, c2), null
        );

        ChunkInput c3 = new ChunkInput("c3", null, null, null, "ATTACHMENT");
        GuideGenerationInput replaced = original.withChunks(List.of(c3));

        assertThat(replaced.policyId()).isEqualTo(7L);
        assertThat(replaced.title()).isEqualTo("title");
        assertThat(replaced.body()).isEqualTo("body");
        assertThat(replaced.chunks()).containsExactly(c3);
        // 원본은 불변
        assertThat(original.chunks()).containsExactly(c1, c2);
    }
}
```

- [ ] **Step 3.3: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.dto.command.GuideGenerationInputTest"`
Expected: FAIL (`withChunks` 메서드 없음 — compile error)

- [ ] **Step 3.4: withChunks 메서드 추가**

Edit `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` — `combinedSourceText()` 메서드 위에 추가:

```java
public GuideGenerationInput withChunks(List<ChunkInput> newChunks) {
    return new GuideGenerationInput(
            policyId, title, referenceYear, summary, body,
            supportTarget, selectionCriteria, supportContent, contact, organization,
            enrichment, newChunks, referenceData
    );
}
```

- [ ] **Step 3.5: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.dto.command.GuideGenerationInputTest"`
Expected: 1 test passed

- [ ] **Step 3.6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java backend/src/test/java/com/youthfit/guide/application/dto/command/GuideGenerationInputTest.java
git commit -m "feat(guide): add GuideGenerationInput.withChunks helper for chunk replacement"
```

---

## Task 4: GuideLlmProvider 인터페이스 확장

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java`

- [ ] **Step 4.1: 인터페이스에 두 메서드 추가**

Edit `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java`:

```java
package com.youthfit.guide.application.port;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;

import java.util.List;

public interface GuideLlmProvider {

    GuideContent generateGuide(GuideGenerationInput input);

    GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages);

    /**
     * 큰 정책의 청크 그룹 하나에 대해 부분 가이드를 생성한다.
     * 응답은 풀 GuideContent 형식 (validator/schema 재사용).
     * 입력 input.chunks() 는 한 그룹의 청크들만 들어 있어야 한다.
     */
    GuideContent generatePartialGuide(GuideGenerationInput input);

    /**
     * 여러 부분 가이드를 통합해 최종 가이드를 생성한다.
     * 입력 input 의 chunks 는 비어 있어도 되며, partials 가 주된 입력이다.
     */
    GuideContent mergePartialGuides(GuideGenerationInput input, List<GuideContent> partials);
}
```

- [ ] **Step 4.2: 컴파일 확인 (구현 추가 전이라 OpenAiChatClient 컴파일 에러 발생 예상)**

Run: `cd backend && ./gradlew compileJava`
Expected: FAIL — `OpenAiChatClient` 가 `GuideLlmProvider` 의 새 메서드 구현 안 함

이 단계는 의도된 실패. 다음 Task 에서 구현.

- [ ] **Step 4.3: 임시 커밋 보류**

이 변경은 Task 5/6 의 구현과 함께 커밋. 지금은 stage 만 (또는 그대로 두고 다음 Task 진행 — 한 커밋으로 묶음).

---

## Task 5: OpenAiChatClient.generatePartialGuide 구현

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`

- [ ] **Step 5.1: partial 용 system prompt prefix 정의**

Edit `OpenAiChatClient.java` — 기존 `SYSTEM_PROMPT` 상수 아래에 추가:

```java
static final String PARTIAL_SYSTEM_PROMPT_PREFIX = """
        [부분 가이드 호출 안내]
        이 호출은 정책 전체가 아니라 청크 그룹 하나만으로 작성하는 부분 가이드다.
        다른 부분 가이드들과 추후 통합 호출에서 병합된다.
        보이는 청크 내용 + 정책 메타에 한해서만 응답하라.
        누락된 정보를 추측하지 마라. 다른 청크 그룹에 있을 법한 내용을 가정하지 마라.

        """;
```

`PARTIAL_SYSTEM_PROMPT_PREFIX + SYSTEM_PROMPT` 를 partial 호출의 system prompt 로 사용한다.

- [ ] **Step 5.2: generatePartialGuide 구현**

같은 파일에 메서드 추가 (기존 `generateGuide` 메서드 옆):

```java
@Override
public GuideContent generatePartialGuide(GuideGenerationInput input) {
    return callChatCompletion(
            PARTIAL_SYSTEM_PROMPT_PREFIX + SYSTEM_PROMPT,
            buildUserMessage(input),
            input.policyId()
    );
}
```

`callChatCompletion` 가 아직 없다면 기존 `generateGuide` 의 RestClient 호출 + 응답 파싱 + emitMetric 로직을 다음 시그니처로 추출 (Step 5.3):

```java
private GuideContent callChatCompletion(String systemPrompt, String userMessage, Long policyId);
```

- [ ] **Step 5.3: callChatCompletion private 메서드로 추출**

기존 `generateGuide` 의 본문을 다음으로 교체 (시그니처는 그대로 유지):

```java
@Override
public GuideContent generateGuide(GuideGenerationInput input) {
    return callChatCompletion(SYSTEM_PROMPT, buildUserMessage(input), input.policyId());
}
```

그리고 새 private 메서드:

```java
private GuideContent callChatCompletion(String systemPrompt, String userMessage, Long policyId) {
    Map<String, Object> requestBody = Map.of(
            "model", properties.getModel(),
            "max_tokens", properties.getMaxTokens(),
            "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ),
            "response_format", buildResponseFormat()
    );

    JsonNode response = restClient.post()
            .uri(CHAT_COMPLETIONS_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + properties.getApiKey())
            .body(requestBody)
            .retrieve()
            .body(JsonNode.class);

    if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
        log.error("OpenAI Chat API 호출 실패: policyId={}", policyId);
        throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 생성에 실패했습니다");
    }

    emitMetric(response);

    String json = response.get("choices").get(0).get("message").get("content").asText();
    return parseGuideContent(json);
}
```

`parseGuideContent(json)` 가 기존에 어떻게 정의되어 있는지에 따라 — 기존 `generateGuide` 의 응답 → `GuideContent` 변환 부분도 동일하게 분리. 기존 코드에 이미 helper 가 있으면 재사용.

`regenerateWithFeedback` 도 동일한 패턴으로 `callChatCompletion(SYSTEM_PROMPT + feedbackBlock, ...)` 형태로 리팩토링 — 단, 기존 동작 보존이 우선이라 시그니처/내부 처리만 정합시키고 외부 동작은 동일.

- [ ] **Step 5.4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (이때까지 mergePartialGuides 도 인터페이스 구현 요구 — Task 6 에서 채움 전까지는 stub 으로 두자)

Task 6 가 완료될 때까지 임시 stub 추가:

```java
@Override
public GuideContent mergePartialGuides(GuideGenerationInput input, List<GuideContent> partials) {
    throw new UnsupportedOperationException("not yet implemented — Task 6");
}
```

- [ ] **Step 5.5: 회귀 테스트 실행 (기존 가이드 테스트 깨지지 않는지)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.*"`
Expected: 기존 테스트 모두 통과

---

## Task 6: OpenAiChatClient.mergePartialGuides 구현

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`

- [ ] **Step 6.1: merge 용 system prompt 정의**

`OpenAiChatClient.java` 상수 추가:

```java
static final String MERGE_SYSTEM_PROMPT = """
        너는 한국 청년 정책 가이드 통합 편집자다. 입력으로 정책 메타와 여러 부분 가이드(JSON) 들이 주어진다.
        목표는 부분들을 합쳐 중복을 제거하고 우선순위가 높은 항목을 보존한 최종 가이드를 작성하는 것이다.

        [통합 원칙]
        1. 정보 통제: 부분 가이드와 정책 메타에 명시된 내용만 사용한다. 추가 정보를 만들어내지 마라.
        2. 중복 제거: 의미가 동일한 highlight/pitfall 은 하나로 합치되 더 구체적인 표현을 우선한다.
        3. 출처 보존: sourceField 와 attachmentRef 는 가능한 보존한다.
        4. 어조/품질: 기존 가이드 작성 원칙(단정형 어미, 친근체 금지, 수치 보존) 을 동일하게 적용한다.
        5. 분류 키워드 분리: 차상위/일반공급/특별공급 등 분류가 다른 항목은 다른 group 으로 유지한다.

        출력 schema 는 단일 호출 가이드와 동일하다.
        """;
```

- [ ] **Step 6.2: mergePartialGuides 본 구현**

`OpenAiChatClient.java` 의 stub 을 다음으로 교체:

```java
@Override
public GuideContent mergePartialGuides(GuideGenerationInput input, List<GuideContent> partials) {
    String userMessage = buildMergeUserMessage(input, partials);
    return callChatCompletion(MERGE_SYSTEM_PROMPT, userMessage, input.policyId());
}

private String buildMergeUserMessage(GuideGenerationInput input, List<GuideContent> partials) {
    StringBuilder sb = new StringBuilder();
    sb.append("[정책 메타]\n");
    sb.append("title=").append(input.title()).append("\n");
    if (input.summary() != null) sb.append("summary=").append(input.summary()).append("\n");
    if (input.supportTarget() != null) sb.append("supportTarget=").append(input.supportTarget()).append("\n");
    if (input.supportContent() != null) sb.append("supportContent=").append(input.supportContent()).append("\n");
    if (input.organization() != null) sb.append("organization=").append(input.organization()).append("\n");
    if (input.contact() != null) sb.append("contact=").append(input.contact()).append("\n");
    sb.append("\n");

    for (int i = 0; i < partials.size(); i++) {
        sb.append("[부분 가이드 ").append(i + 1).append(" / ").append(partials.size()).append("]\n");
        try {
            sb.append(objectMapper.writeValueAsString(partials.get(i))).append("\n\n");
        } catch (Exception e) {
            log.warn("부분 가이드 직렬화 실패 (skip): policyId={}, partialIndex={}", input.policyId(), i, e);
        }
    }
    return sb.toString();
}
```

`objectMapper.writeValueAsString` 의 exception 타입은 기존 ObjectMapper 사용 패턴에 맞춤 (tools.jackson 의 JacksonException 등).

- [ ] **Step 6.3: 컴파일 + 회귀 테스트**

Run: `cd backend && ./gradlew compileJava test --tests "com.youthfit.guide.*"`
Expected: BUILD SUCCESSFUL, 기존 가이드 테스트 모두 통과

- [ ] **Step 6.4: 커밋 (Task 4-6 묶음)**

```bash
git add backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java
git commit -m "feat(guide): add generatePartialGuide + mergePartialGuides to LlmProvider"
```

---

## Task 7: MapReduceGuideOrchestrator

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/service/MapReduceGuideOrchestratorTest.java`

- [ ] **Step 7.1: 실패 테스트 작성**

Create `backend/src/test/java/com/youthfit/guide/application/service/MapReduceGuideOrchestratorTest.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.command.GuideGenerationInput.ChunkInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MapReduceGuideOrchestratorTest {

    @Mock private GuideLlmProvider llmProvider;

    private MapReduceGuideOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        TokenCounter tokenCounter = new TokenCounter();
        // budget 100 tokens 로 작게 잡아 분할이 쉽게 일어나게 함
        orchestrator = new MapReduceGuideOrchestrator(llmProvider, tokenCounter,
                "gpt-4o-mini", 100);
    }

    @Test
    @DisplayName("청크 합이 budget 을 넘으면 여러 그룹으로 분할되고 partial 가 N 번 호출된다")
    void splitsByBudgetAndCallsPartialMultipleTimes() {
        // 각 청크 약 50 토큰 (한국어 100자 정도) → 3개면 약 150 토큰, budget 100 으로 2 그룹
        ChunkInput c1 = new ChunkInput("가".repeat(100), null, null, null, "BODY");
        ChunkInput c2 = new ChunkInput("나".repeat(100), null, null, null, "BODY");
        ChunkInput c3 = new ChunkInput("다".repeat(100), null, null, null, "BODY");
        GuideGenerationInput input = buildInput(List.of(c1, c2, c3));

        GuideContent dummy = dummyContent();
        given(llmProvider.generatePartialGuide(any())).willReturn(dummy);
        given(llmProvider.mergePartialGuides(any(), any())).willReturn(dummy);

        GuideContent result = orchestrator.generate(input);

        assertThat(result).isEqualTo(dummy);
        // 적어도 2번 호출 (정확한 수는 토큰 측정에 따라 다를 수 있으나 2+ 보장)
        verify(llmProvider, times(2)).generatePartialGuide(any());
        verify(llmProvider, times(1)).mergePartialGuides(any(), any());
    }

    @Test
    @DisplayName("partial 일부 실패 시 성공한 것만으로 merge 진행")
    void partialFailureFallsThroughToMerge() {
        ChunkInput c1 = new ChunkInput("가".repeat(100), null, null, null, "BODY");
        ChunkInput c2 = new ChunkInput("나".repeat(100), null, null, null, "BODY");
        GuideGenerationInput input = buildInput(List.of(c1, c2));

        GuideContent ok = dummyContent();
        given(llmProvider.generatePartialGuide(any()))
                .willReturn(ok)
                .willThrow(new RuntimeException("rate limit"));
        given(llmProvider.mergePartialGuides(any(), any())).willReturn(ok);

        GuideContent result = orchestrator.generate(input);

        assertThat(result).isEqualTo(ok);
        // partial 은 2번 호출 시도, 1번 성공 → merge 에 1개 partial 전달
        verify(llmProvider, times(2)).generatePartialGuide(any());
        verify(llmProvider, times(1)).mergePartialGuides(any(),
                argThat(list -> ((List<?>) list).size() == 1));
    }

    @Test
    @DisplayName("partial 전부 실패 시 RuntimeException")
    void allPartialFailureThrows() {
        ChunkInput c1 = new ChunkInput("가".repeat(100), null, null, null, "BODY");
        GuideGenerationInput input = buildInput(List.of(c1));

        given(llmProvider.generatePartialGuide(any()))
                .willThrow(new RuntimeException("rate limit"));

        assertThatThrownBy(() -> orchestrator.generate(input))
                .isInstanceOf(RuntimeException.class);
        verify(llmProvider, times(0)).mergePartialGuides(any(), any());
    }

    private GuideGenerationInput buildInput(List<ChunkInput> chunks) {
        return new GuideGenerationInput(
                1L, "title", 2025, "summary", "body",
                null, null, null, null, null, null, chunks, null);
    }

    private GuideContent dummyContent() {
        // GuideContent 의 정확한 생성자 시그니처에 맞춰 채움.
        // 기존 GuideContent record 의 모든 필드를 null/빈 값으로 초기화.
        return new GuideContent(
                "한 줄 요약",
                new ArrayList<>(),  // highlights
                null, null, null,
                null, null, null, null,
                new ArrayList<>()   // pitfalls
        );
    }

    private static org.mockito.ArgumentMatcher<List<GuideContent>> argThat(
            java.util.function.Predicate<List<GuideContent>> predicate) {
        return predicate::test;
    }
}
```

> Note: `GuideContent` record 의 정확한 생성자 시그니처는 코드 기준으로 확인 후 dummyContent() 조정.

- [ ] **Step 7.2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.service.MapReduceGuideOrchestratorTest"`
Expected: FAIL (`MapReduceGuideOrchestrator` 없음)

- [ ] **Step 7.3: MapReduceGuideOrchestrator 구현**

Create `backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.dto.command.GuideGenerationInput.ChunkInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MapReduceGuideOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MapReduceGuideOrchestrator.class);

    private final GuideLlmProvider llmProvider;
    private final TokenCounter tokenCounter;
    private final String model;
    private final int groupBudgetTokens;

    public MapReduceGuideOrchestrator(
            GuideLlmProvider llmProvider,
            TokenCounter tokenCounter,
            @Value("${openai.chat.model:gpt-4o-mini}") String model,
            @Value("${guide.map-reduce.group-budget-tokens:60000}") int groupBudgetTokens) {
        this.llmProvider = llmProvider;
        this.tokenCounter = tokenCounter;
        this.model = model;
        this.groupBudgetTokens = groupBudgetTokens;
    }

    public GuideContent generate(GuideGenerationInput input) {
        List<List<ChunkInput>> groups = groupChunksByTokenBudget(input.chunks(), groupBudgetTokens);
        log.info("map-reduce 진입: policyId={}, groups={}, totalChunks={}",
                input.policyId(), groups.size(), input.chunks().size());

        List<GuideContent> partials = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            try {
                GuideContent partial = llmProvider.generatePartialGuide(input.withChunks(groups.get(i)));
                partials.add(partial);
            } catch (Exception e) {
                log.warn("부분 가이드 호출 실패 (skip): policyId={}, groupIndex={}, err={}",
                        input.policyId(), i, e.getMessage());
            }
        }

        if (partials.isEmpty()) {
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR,
                    "모든 부분 가이드 호출이 실패하여 가이드를 생성할 수 없습니다: policyId=" + input.policyId());
        }

        log.info("map-reduce merge 시작: policyId={}, successPartials={}/{}",
                input.policyId(), partials.size(), groups.size());
        return llmProvider.mergePartialGuides(input.withChunks(List.of()), partials);
    }

    private List<List<ChunkInput>> groupChunksByTokenBudget(List<ChunkInput> chunks, int budget) {
        List<List<ChunkInput>> groups = new ArrayList<>();
        List<ChunkInput> current = new ArrayList<>();
        int currentTokens = 0;
        for (ChunkInput c : chunks) {
            int t = tokenCounter.countTokens(c.content(), model);
            if (currentTokens + t > budget && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(c);
            currentTokens += t;
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }
}
```

- [ ] **Step 7.4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.service.MapReduceGuideOrchestratorTest"`
Expected: 3 tests passed

만약 `dummyContent()` 의 GuideContent 생성자 시그니처가 안 맞으면 실제 `GuideContent` record 정의에 맞춰 필드 갯수/순서 조정.

- [ ] **Step 7.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/MapReduceGuideOrchestrator.java backend/src/test/java/com/youthfit/guide/application/service/MapReduceGuideOrchestratorTest.java
git commit -m "feat(guide): add MapReduceGuideOrchestrator for large policy chunk handling"
```

---

## Task 8: GuideGenerationService 분기 통합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java`

- [ ] **Step 8.1: 의존성 주입 + 분기 로직 추가**

Edit `GuideGenerationService.java`:

(a) 필드/생성자에 `GuideStrategySelector`, `MapReduceGuideOrchestrator` 추가:

```java
private final GuideStrategySelector strategySelector;
private final MapReduceGuideOrchestrator mapReduceOrchestrator;
```

(b) `generateGuide(command)` 메서드의 LLM 호출 부분 (현재 line 89 `guideLlmProvider.generateGuide(input)`) 을 분기로 교체:

```java
// 기존: GuideContent firstResponse = guideLlmProvider.generateGuide(input);
// 변경:
GuideContent firstResponse;
GuideGenerationStrategy strategy = strategySelector.select(input);
if (strategy == GuideGenerationStrategy.MAP_REDUCE) {
    log.info("map-reduce 분기 진입: policyId={}", command.policyId());
    firstResponse = mapReduceOrchestrator.generate(input);
} else {
    firstResponse = guideLlmProvider.generateGuide(input);
}
GuideValidator.ValidationReport firstReport = guideValidator.validate(firstResponse, validAttachmentIds);
```

(c) 재시도 분기 (`firstReport.hasRetryTrigger()`) 의 `guideLlmProvider.regenerateWithFeedback(...)` 호출은 **SINGLE_CALL 일 때만 적용** (MAP_REDUCE 결과는 부분 가이드들을 다시 모아 재시도하는 흐름이 없음 — 1차 결과 사용):

```java
GuideContent finalResponse;
if (firstReport.hasRetryTrigger() && strategy == GuideGenerationStrategy.SINGLE_CALL) {
    // 기존 재시도 블록 그대로
    ...
} else {
    finalResponse = firstResponse;
}
```

- [ ] **Step 8.2: import 추가**

`GuideGenerationService.java` 상단:

```java
import com.youthfit.guide.application.dto.command.GuideGenerationStrategy;
```

- [ ] **Step 8.3: 테스트 추가 (MAP_REDUCE 분기 위임 검증)**

`GuideGenerationServiceTest.java` 에 추가 (또는 신규 nested class):

```java
@Test
@DisplayName("strategy 가 MAP_REDUCE 이면 orchestrator.generate 위임, 단일 LLM 호출 안 함")
void mapReduceDelegates() {
    // 기존 테스트가 mocking 패턴을 갖고 있다면 따라가기.
    // policyRepository, guideRepository 등 mock 설정 후
    given(strategySelector.select(any())).willReturn(GuideGenerationStrategy.MAP_REDUCE);
    given(mapReduceOrchestrator.generate(any())).willReturn(dummyGuideContent);
    given(guideValidator.validate(any(), any())).willReturn(noViolationReport);

    service.generateGuide(new GenerateGuideCommand(7L, "title", "content"));

    verify(mapReduceOrchestrator, times(1)).generate(any());
    verify(guideLlmProvider, times(0)).generateGuide(any());
    verify(guideLlmProvider, times(0)).regenerateWithFeedback(any(), any());
}

@Test
@DisplayName("strategy 가 SINGLE_CALL 이면 기존 단일 호출 흐름 (회귀)")
void singleCallPreserved() {
    given(strategySelector.select(any())).willReturn(GuideGenerationStrategy.SINGLE_CALL);
    given(guideLlmProvider.generateGuide(any())).willReturn(dummyGuideContent);
    given(guideValidator.validate(any(), any())).willReturn(noViolationReport);

    service.generateGuide(new GenerateGuideCommand(7L, "title", "content"));

    verify(guideLlmProvider, times(1)).generateGuide(any());
    verify(mapReduceOrchestrator, times(0)).generate(any());
}
```

> 기존 테스트의 mock 셋업 패턴 (BDDMockito given, `@Mock`, `@InjectMocks`) 을 동일하게 따라간다. `noViolationReport` 와 `dummyGuideContent` 는 테스트 헬퍼에서 만들어 둠.

- [ ] **Step 8.4: 테스트 실행**

Run: `cd backend && ./gradlew test --tests "com.youthfit.guide.application.service.GuideGenerationServiceTest"`
Expected: 신규 2 테스트 + 기존 테스트 모두 통과

기존 테스트가 깨지면 셋업에 `strategySelector`/`mapReduceOrchestrator` mock 추가 + default `select(any())` → `SINGLE_CALL` lenient stub.

- [ ] **Step 8.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java
git commit -m "feat(guide): wire map-reduce strategy branch into GuideGenerationService"
```

---

## Task 9: 전체 회귀 + 빌드

**Files:** 변경 없음

- [ ] **Step 9.1: 전체 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 모든 모듈의 기존 + 신규 테스트 통과

- [ ] **Step 9.2: 전체 빌드**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9.3: 커밋 (불필요하면 skip)**

테스트만 돌렸으니 코드 변경 없으면 commit 없음.

---

## Task 10: 실제 누락 9개 가이드 복구 검증

**Files:** 변경 없음 (운영 검증)

- [ ] **Step 10.1: backend recreate**

Run:
```bash
cd /Users/taetaetae/IdeaProjects/youthfit && docker compose up -d --force-recreate --build backend
```

backend health UP 까지 대기 (`curl http://localhost:8080/actuator/health` → `"status":"UP"`).

- [ ] **Step 10.2: 누락 9개 가이드 강제 재생성**

```bash
API_KEY=$(docker exec youthfit-backend printenv INTERNAL_API_KEY)
for id in 36 37 47 49 52 53 55 56 58; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/internal/guides/generate \
    -H "Content-Type: application/json" \
    -H "X-Internal-Api-Key: $API_KEY" \
    -d "{\"policyId\":$id,\"policyTitle\":\"regen\",\"documentContent\":\"regen\"}")
  echo "policy=$id http=$code"
  sleep 5
done
```

Expected: 모든 9개 `http=200`

- [ ] **Step 10.3: 가이드 카운트 확인**

```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -tAc "SELECT COUNT(*) FROM guide;"
```

Expected: `60` (이전 51 + 신규 9)

- [ ] **Step 10.4: backend 로그에서 map-reduce 진입 확인**

```bash
docker logs youthfit-backend 2>&1 | grep "map-reduce" | tail -10
```

Expected: `map-reduce 분기 진입: policyId=49`, `map-reduce 진입: policyId=49, groups=...` 등 9개 정책 모두 로그 보임

- [ ] **Step 10.5: 가이드 1건 API 응답 확인 (정책 56 — 가장 큰 정책)**

```bash
curl -s http://localhost:8080/api/v1/guides/56 | head -c 1500
```

Expected: `oneLineSummary`, `highlights` 등 정상 구조 응답

- [ ] **Step 10.6: 누적 LLM 비용 확인 (map-reduce 호출 분 추가됐는지)**

```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -c \
  "SELECT module, model, SUM(call_count) calls, ROUND(SUM(estimated_cost_usd)::numeric, 4) cost FROM llm_cost_bucket GROUP BY module, model ORDER BY calls DESC;"
```

Expected: GUIDE module 의 calls 가 9 + N (partial+merge 합산) 만큼 증가

- [ ] **Step 10.7: 회귀 sanity — 기존 51개 가이드는 그대로**

```bash
docker exec youthfit-postgres psql -U youthfit -d youthfit -tAc \
  "SELECT COUNT(DISTINCT policy_id) FROM guide WHERE policy_id NOT IN (36,37,47,49,52,53,55,56,58);"
```

Expected: `51` (기존 51개 그대로)

- [ ] **Step 10.8: 최종 main push (이전 task 들의 commit 누적분)**

```bash
git push origin main
```

만약 별도 브랜치/PR 흐름이면 그쪽으로.

---

## Verification Checklist

- [ ] 가이드 60/60 생성
- [ ] backend 로그에 `map-reduce 진입` 로그 보임 (큰 정책에 한해)
- [ ] 기존 51개 가이드는 hash 동일 (회귀 없음)
- [ ] `./gradlew build` 통과
- [ ] LLM 누적 비용 누적량이 합리적 (큰 정책당 ~3-7배)
- [ ] (선택) 가이드 56번/49번 응답 품질 spot-check — 첨부 PDF 모든 청크 정보가 일부라도 반영되었는지

---

## Notes / 후속 작업 (이 plan 밖)

- `GuideGenerationEventListener` 의 429 retry + exponential backoff (Resilience4j) — 별도 spec
- OpenAI tier 상향 검토
- `MapReduceGuideOrchestrator` 의 partial 호출 병렬화 (현재는 순차)
- 가이드 품질 모니터링 — SINGLE_CALL vs MAP_REDUCE 별도 메트릭
