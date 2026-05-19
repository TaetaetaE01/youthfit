# RAG Query Rewriting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자 질문을 LLM 으로 정책 표준 용어로 재작성한 뒤 그 결과로 임베딩·hybrid 검색을 수행해 retrieval 의미 갭을 줄인다. 답변 LLM 컨텍스트는 원래 질문만 사용하고, 환경변수만으로 출시·롤백 가능한 feature flag 로 보호한다.

**Architecture:** `qna` 모듈에 `QueryRewriter` 포트와 `OpenAiQueryRewriter` 구현체를 추가한다. `QnaService.processQuestion` 의 ④ RAG 단계 직전에 `rag.query-rewrite.enabled` 분기를 두어 (a) rewrite 호출 → (b) 재임베딩 → (c) hybrid/vector 검색 호출 흐름으로 교체한다. 캐시 키·답변 LLM 입력은 변경하지 않으며, rewrite 실패·timeout 시 원래 질문으로 graceful fallback.

**Tech Stack:** Spring Boot 4.0.5, Java 21, OpenAI Chat Completions API (gpt-4o-mini), JUnit 5 + Mockito + AssertJ

**Spec:** [`docs/superpowers/specs/2026-05-16-rag-query-rewriting-design.md`](../specs/2026-05-16-rag-query-rewriting-design.md)

---

## 파일 구조

### 신규
- `backend/src/main/java/com/youthfit/qna/application/port/QueryRewriter.java` — LLM 기반 query 재작성 도메인 포트
- `backend/src/main/java/com/youthfit/qna/infrastructure/config/QueryRewriteProperties.java` — `rag.query-rewrite.*` 설정 record
- `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQueryRewriter.java` — OpenAI Chat API 호출 구현체
- `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQueryRewriterTest.java` — 구현체 단위 테스트
- `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceQueryRewriteTest.java` — `QnaService` rewrite 분기 통합 단위 테스트

### 수정
- `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` — rewrite 분기 + 재임베딩 + 검색 호출 query 교체
- `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java` — `QueryRewriteProperties` 등록
- `backend/src/main/resources/application.yml` — `rag.query-rewrite.*` 블록 추가
- `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java` — 신규 의존성 mock 필드 추가 (rewriter / props)
- `backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java` — `QUERY_REWRITE` 엔트리 추가 (메트릭 분리)

---

## Task 1: LlmModule 에 QUERY_REWRITE 엔트리 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java`

- [ ] **Step 1: enum 항목 추가**

기존 파일 전체를 다음으로 교체:

```java
package com.youthfit.metrics.domain.model;

public enum LlmModule {
    QNA,
    GUIDE,
    EMBEDDING,
    INGESTION,
    ELIGIBILITY,
    QUERY_REWRITE
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java
git commit -m "feat(metrics): LlmModule.QUERY_REWRITE 엔트리 추가"
```

---

## Task 2: QueryRewriteProperties record 작성

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/config/QueryRewriteProperties.java`
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: QueryRewriteProperties record 작성**

```java
package com.youthfit.qna.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.query-rewrite")
public record QueryRewriteProperties(
        boolean enabled,
        String model,
        int maxTokens,
        double temperature,
        int timeoutMs
) {
    public QueryRewriteProperties {
        if (model == null || model.isBlank()) model = "gpt-4o-mini";
        if (maxTokens <= 0) maxTokens = 80;
        if (temperature < 0) temperature = 0.3;
        if (timeoutMs <= 0) timeoutMs = 5000;
    }
}
```

- [ ] **Step 2: QnaConfig 에 등록**

기존 `QnaConfig.java` 의 `@EnableConfigurationProperties` 라인을 다음으로 교체:

```java
package com.youthfit.qna.infrastructure.config;

import com.youthfit.qna.infrastructure.external.OpenAiQnaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        OpenAiQnaProperties.class,
        QnaProperties.class,
        QueryRewriteProperties.class
})
public class QnaConfig {
}
```

- [ ] **Step 3: application.yml 에 설정 추가**

기존 `rag:` 블록의 `hybrid:` 항목 다음에 추가 (line 55 부근, `openai:` 직전):

```yaml
rag:
  hybrid:
    enabled: ${RAG_HYBRID_ENABLED:false}
    top-n-per-search: ${RAG_HYBRID_TOP_N:20}
    rrf-k: ${RAG_HYBRID_RRF_K:60}
    trigram-threshold: ${RAG_HYBRID_TRIGRAM_THRESHOLD:0.1}
  query-rewrite:
    enabled: ${RAG_QUERY_REWRITE_ENABLED:false}
    model: ${RAG_QUERY_REWRITE_MODEL:gpt-4o-mini}
    max-tokens: ${RAG_QUERY_REWRITE_MAX_TOKENS:80}
    temperature: ${RAG_QUERY_REWRITE_TEMPERATURE:0.3}
    timeout-ms: ${RAG_QUERY_REWRITE_TIMEOUT_MS:5000}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Properties 바인딩 검증 (애플리케이션 컨텍스트 로딩)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.YouthfitApplicationTests"`
Expected: PASS — 컨텍스트 로딩 시 `QueryRewriteProperties` 바인딩 성공

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/config/QueryRewriteProperties.java \
        backend/src/main/java/com/youthfit/qna/infrastructure/config/QnaConfig.java \
        backend/src/main/resources/application.yml
git commit -m "feat(qna): QueryRewriteProperties + application.yml rag.query-rewrite 설정 추가"
```

---

## Task 3: QueryRewriter 포트 인터페이스 작성

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/port/QueryRewriter.java`

- [ ] **Step 1: 포트 인터페이스 작성**

```java
package com.youthfit.qna.application.port;

import java.util.Optional;

/**
 * 사용자 질문을 정책 도메인 표준 용어로 재작성하는 포트.
 *
 * <p>구현체는 LLM 호출 실패·timeout·검증 실패 시 {@link Optional#empty()} 를 반환해야 한다.
 * 호출자는 empty 일 때 원래 질문으로 fallback 한다.
 */
public interface QueryRewriter {

    /**
     * @param policyTitle 정책명 (rewrite context 에 포함)
     * @param userQuestion 사용자 원래 질문
     * @return 재작성된 검색 query, 또는 빈 결과면 {@link Optional#empty()}
     */
    Optional<String> rewrite(String policyTitle, String userQuestion);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/application/port/QueryRewriter.java
git commit -m "feat(qna): QueryRewriter 포트 인터페이스 추가"
```

---

## Task 4: OpenAiQueryRewriter 구현 (TDD)

**Files:**
- Create: `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQueryRewriterTest.java`
- Create: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQueryRewriter.java`

> 정적 메서드 단위 테스트 패턴은 기존 `OpenAiQnaClient.parseFollowUps` / `buildUserMessage` 와 동일하게 외부 호출을 우회하고 입출력 변환만 검증한다. RestClient mocking 은 복잡도가 크므로 본 plan 에서는 입출력 변환 메서드(`parseRewritten`, `truncate`, `buildUserMessage`)를 패키지-private static 으로 노출해 단위 테스트한다. 실제 HTTP 호출 통합 검증은 운영 검증 단계에서 수행.

- [ ] **Step 1: 테스트 먼저 작성**

```java
package com.youthfit.qna.infrastructure.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiQueryRewriter 입출력 변환")
class OpenAiQueryRewriterTest {

    @Nested
    @DisplayName("parseRewritten")
    class ParseRewritten {

        @Test
        @DisplayName("정상 응답은 trim 후 그대로 반환한다")
        void normal() {
            Optional<String> result = OpenAiQueryRewriter.parseRewritten(
                    "  청년내일저축계좌 최근 3개월 평균 근로사업소득 기준  "
            );
            assertThat(result).contains("청년내일저축계좌 최근 3개월 평균 근로사업소득 기준");
        }

        @Test
        @DisplayName("null 입력은 empty 반환")
        void nullInput() {
            assertThat(OpenAiQueryRewriter.parseRewritten(null)).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열은 empty 반환")
        void blank() {
            assertThat(OpenAiQueryRewriter.parseRewritten("   ")).isEmpty();
        }

        @Test
        @DisplayName("5자 미만은 empty 반환 (의미 있는 검색 query 가 아님)")
        void tooShort() {
            assertThat(OpenAiQueryRewriter.parseRewritten("질문")).isEmpty();
            assertThat(OpenAiQueryRewriter.parseRewritten("abc"))
                    .as("5자 미만이면 fallback 으로 분류")
                    .isEmpty();
        }

        @Test
        @DisplayName("200자 초과는 200자로 truncate 후 반환")
        void truncatesLong() {
            String longText = "가".repeat(250);
            Optional<String> result = OpenAiQueryRewriter.parseRewritten(longText);
            assertThat(result).isPresent();
            assertThat(result.get()).hasSize(200);
        }
    }

    @Nested
    @DisplayName("buildUserMessage")
    class BuildUserMessage {

        @Test
        @DisplayName("정책명·질문이 정해진 포맷으로 들어간다")
        void format() {
            String message = OpenAiQueryRewriter.buildUserMessage(
                    "청년내일저축계좌", "근로사업소득이 작년이 기준이야 올해가 기준이야?"
            );
            assertThat(message).contains("정책: 청년내일저축계좌");
            assertThat(message).contains("질문: 근로사업소득이 작년이 기준이야 올해가 기준이야?");
            assertThat(message).contains("재작성된 검색 query:");
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "OpenAiQueryRewriterTest" -i`
Expected: FAIL — `OpenAiQueryRewriter` 클래스 없음

- [ ] **Step 3: OpenAiQueryRewriter 구현**

> spec §7 의 "rewrite LLM 호출 timeout" fallback 이 실효성을 가지려면 `properties.timeoutMs()` 를 RestClient 의 connect/read 타임아웃에 실제 적용해야 한다. 기본 `RestClient.create()` 는 JDK 기본값 (사실상 무한) 을 쓰므로 hang 위험. `SimpleClientHttpRequestFactory` 로 명시 설정한다.

```java
package com.youthfit.qna.infrastructure.external;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenAiQueryRewriter implements QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQueryRewriter.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 200;

    private static final String SYSTEM_PROMPT = """
            당신은 한국 청년 정책 문서 검색을 돕는 query 재작성 어시스턴트입니다.

            규칙:
            1. 사용자 질문을 정책 표준 용어로 변환하세요.
               예: "작년/올해" → "직전 N개월/최근/당해연도"
               예: "받을 수 있어?" → "지원 자격 / 신청 조건"
            2. 의미를 추측·확장하지 마세요. 동의어·표준 용어 변환만 허용.
            3. 정책명을 query 에 포함하세요.
            4. 100자 이내, 검색용 키워드 중심.
            5. 결과만 출력. 부가 설명 금지.
            """;

    private final QueryRewriteProperties properties;
    private final OpenAiQnaProperties qnaProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiQueryRewriter(
            QueryRewriteProperties properties,
            OpenAiQnaProperties qnaProperties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.qnaProperties = qnaProperties;
        this.eventPublisher = eventPublisher;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<String> rewrite(String policyTitle, String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return Optional.empty();
        }

        long startNanos = System.nanoTime();
        Map<String, Object> requestBody = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "temperature", properties.temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserMessage(policyTitle, userQuestion))
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + qnaProperties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            String content = (choices != null && !choices.isEmpty())
                    ? choices.get(0).path("message").path("content").asText("")
                    : "";

            JsonNode usage = root.get("usage");
            int promptTokens = usage != null ? usage.path("prompt_tokens").asInt(0) : 0;
            int completionTokens = usage != null ? usage.path("completion_tokens").asInt(0) : 0;
            try {
                eventPublisher.publishEvent(new LlmCallRecorded(
                        LlmModule.QUERY_REWRITE, properties.model(),
                        promptTokens, completionTokens, Instant.now()
                ));
            } catch (Exception e) {
                log.warn("query-rewrite LLM 비용 이벤트 발행 실패", e);
            }

            Optional<String> rewritten = parseRewritten(content);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (rewritten.isPresent()) {
                log.info("query rewrite: original=\"{}\", rewritten=\"{}\", duration={}ms",
                        userQuestion, rewritten.get(), durationMs);
            } else {
                log.info("query rewrite fallback: reason=too-short-or-empty, original=\"{}\", duration={}ms",
                        userQuestion, durationMs);
            }
            return rewritten;
        } catch (Exception e) {
            log.warn("query rewrite fallback: reason=exception, original=\"{}\", error={}",
                    userQuestion, e.toString());
            return Optional.empty();
        }
    }

    static Optional<String> parseRewritten(String content) {
        if (content == null) return Optional.empty();
        String trimmed = content.trim();
        if (trimmed.length() < MIN_LENGTH) return Optional.empty();
        if (trimmed.length() > MAX_LENGTH) trimmed = trimmed.substring(0, MAX_LENGTH);
        return Optional.of(trimmed);
    }

    static String buildUserMessage(String policyTitle, String userQuestion) {
        return "정책: " + policyTitle
                + "\n질문: " + userQuestion
                + "\n\n재작성된 검색 query:";
    }
}
```

- [ ] **Step 4: 테스트 실행 → 성공 확인**

Run: `cd backend && ./gradlew test --tests "OpenAiQueryRewriterTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQueryRewriter.java \
        backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQueryRewriterTest.java
git commit -m "feat(qna): OpenAiQueryRewriter 구현 + parseRewritten 단위 테스트"
```

---

## Task 5: 기존 QnaServiceTest 가 새 의존성 mock 으로 컴파일되도록 보강

**Files:**
- Modify: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`

> Task 6 에서 `QnaService` 가 `QueryRewriter` 와 `QueryRewriteProperties` 를 새 의존성으로 받게 된다. 기존 테스트가 컴파일 깨지지 않도록 먼저 mock 필드를 추가하고, 기본 동작(`enabled=false`)을 셋업한다. 본 task 만으로는 회귀가 발생하지 않아야 함.

- [ ] **Step 1: 새 mock 필드와 import 추가**

`QnaServiceTest.java` 의 import 구역에 추가:
```java
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
```

`@Mock` 필드 묶음(라인 76 부근, `private ApplicationEventPublisher eventPublisher;` 다음)에 추가:
```java
@Mock private QueryRewriter queryRewriter;
@Mock private QueryRewriteProperties queryRewriteProperties;
```

`setUp()` 메서드 (라인 80~85) 안에 다음 라인 추가 (`given(qnaProperties.semanticDistanceThreshold()).willReturn(0.15);` 뒤):
```java
given(queryRewriteProperties.enabled()).willReturn(false);
```

- [ ] **Step 2: 컴파일 확인 (Task 6 전이라 RED 정상)**

Run: `cd backend && ./gradlew compileTestJava`
Expected: 컴파일 SUCCESS — `QnaService` 가 아직 새 의존성을 받지 않으면 unused mock 경고만 발생.

> 만약 strictness 설정으로 unused stubbing 에러가 나면 다음 task 에서 해결되므로 일단 무시.

- [ ] **Step 3: Commit (스킵 가능 — Task 6 와 묶어 commit 해도 무방)**

본 step 은 Task 6 와 함께 atomic 하게 묶을 수 있으니, 별도 커밋이 어색하면 다음 task 끝에서 함께 commit. **이 plan 에서는 별도 커밋하지 않고 Task 6 step 6 의 commit 에 함께 포함**.

---

## Task 6: QnaService 에 query-rewrite 분기 + 재임베딩 추가 (TDD)

**Files:**
- Create: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceQueryRewriteTest.java`
- Modify: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`
- (Task 5 의 `QnaServiceTest.java` 변경분 함께 commit)

- [ ] **Step 1: 새 테스트 파일 작성 (RED)**

```java
package com.youthfit.qna.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.dto.command.PolicyMetadata;
import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.port.QnaAnswerCache;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.application.port.SemanticQnaCache;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("QnaService — query-rewrite 분기")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QnaServiceQueryRewriteTest {

    @InjectMocks
    private QnaService qnaService;

    @Mock private CostGuard costGuard;
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyAttachmentRepository policyAttachmentRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;
    @Mock private RagSearchService ragSearchService;
    @Mock private QnaLlmProvider qnaLlmProvider;
    @Mock private QnaAnswerCache qnaAnswerCache;
    @Mock private SemanticQnaCache semanticQnaCache;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private QnaHistoryWriter historyWriter;
    @Mock private QnaProperties qnaProperties;
    @Mock private ObjectMapper objectMapper;
    @Mock private QnaCacheLookupClassifier lookupClassifier;
    @Mock private QuestionNormalizer questionNormalizer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private QueryRewriter queryRewriter;
    @Mock private QueryRewriteProperties queryRewriteProperties;

    private Policy policy;

    @BeforeEach
    void setUp() {
        policy = mockPolicy();
        given(qnaProperties.relevanceDistanceThreshold()).willReturn(0.4);
        given(qnaProperties.semanticDistanceThreshold()).willReturn(0.15);

        given(costGuard.allows(10L)).willReturn(true);
        given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
        given(historyWriter.startInProgress(anyLong(), anyLong(), anyString())).willReturn(99L);
        given(qnaAnswerCache.get(anyLong(), anyString())).willReturn(Optional.empty());
        given(questionNormalizer.normalize(anyString())).willReturn("normalized");
        given(semanticQnaCache.findSimilar(anyLong(), anyString(), any())).willReturn(SemanticLookupResult.miss());
        given(lookupClassifier.classify(any())).willReturn(LookupResultType.MISS);
        given(policyDocumentRepository.findSourceHashByPolicyId(anyLong())).willReturn(Optional.of("hash-abc"));
    }

    @Test
    @DisplayName("enabled=false 면 rewriter 호출 없음, 원래 질문으로 임베딩·검색")
    void disabled_skipsRewriter() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(false);
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("작년 기준이야?")).willReturn(originalEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(originalEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "작년 기준이야?", 1L));
        Thread.sleep(200);

        verify(queryRewriter, never()).rewrite(anyString(), anyString());
        verify(embeddingProvider, times(1)).embed("작년 기준이야?");
    }

    @Test
    @DisplayName("enabled=true + rewriter 정상 → rewritten query 로 임베딩·검색")
    void enabled_usesRewrittenQuery() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        given(queryRewriter.rewrite(eq("청년내일저축계좌"), eq("작년 기준이야?")))
                .willReturn(Optional.of("청년내일저축계좌 최근 3개월 평균 근로사업소득"));
        float[] rewrittenEmbedding = new float[]{0.7f, 0.8f};
        given(embeddingProvider.embed("청년내일저축계좌 최근 3개월 평균 근로사업소득"))
                .willReturn(rewrittenEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(rewrittenEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "작년 기준이야?", 1L));
        Thread.sleep(200);

        // 원래 질문으로 1회(semantic 캐시 lookup용), rewritten 으로 1회 (RAG 용) — 총 2회
        verify(embeddingProvider).embed("작년 기준이야?");
        verify(embeddingProvider).embed("청년내일저축계좌 최근 3개월 평균 근로사업소득");

        // 답변 LLM 에는 원래 질문이 전달되어야 함
        ArgumentCaptor<String> questionCaptor = ArgumentCaptor.forClass(String.class);
        verify(qnaLlmProvider).generateAnswer(
                anyString(), any(PolicyMetadata.class), anyString(), questionCaptor.capture(), any());
        assertThat(questionCaptor.getValue()).isEqualTo("작년 기준이야?");
    }

    @Test
    @DisplayName("enabled=true + rewriter empty → 원래 질문으로 fallback")
    void enabled_emptyRewrite_fallsBackToOriginal() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        given(queryRewriter.rewrite(anyString(), anyString())).willReturn(Optional.empty());
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("질문")).willReturn(originalEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(originalEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(200);

        verify(queryRewriter).rewrite(anyString(), eq("질문"));
        verify(embeddingProvider, times(1)).embed("질문");
    }

    @Test
    @DisplayName("enabled=true + 정확 캐시 hit → rewriter 호출 없음")
    void exactCacheHit_skipsRewriter() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        CachedAnswer cached = new CachedAnswer(
                "이전 답변", List.of(), List.of(), java.time.Instant.now()
        );
        given(qnaAnswerCache.get(10L, "질문")).willReturn(Optional.of(cached));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(100);

        verify(queryRewriter, never()).rewrite(anyString(), anyString());
        verify(embeddingProvider, never()).embed(anyString());
    }

    @Test
    @DisplayName("enabled=true + 의미 캐시 hit → rewriter 호출 없음")
    void semanticCacheHit_skipsRewriter() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("질문")).willReturn(originalEmbedding);
        CachedAnswer cached = new CachedAnswer(
                "의미 일치 답변", List.of(), List.of(), java.time.Instant.now()
        );
        var match = new com.youthfit.qna.application.port.dto.SemanticLookupMatch(
                1L,
                java.math.BigDecimal.valueOf(0.92),
                java.math.BigDecimal.valueOf(0.08)
        );
        given(semanticQnaCache.findSimilar(anyLong(), anyString(), any()))
                .willReturn(SemanticLookupResult.hit(match, cached));
        given(lookupClassifier.classify(any())).willReturn(LookupResultType.HIT);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(100);

        verify(queryRewriter, never()).rewrite(anyString(), anyString());
        // semantic lookup 용 임베딩 1회만 — rewritten 임베딩 호출 없음
        verify(embeddingProvider, times(1)).embed(anyString());
    }

    @Test
    @DisplayName("enabled=true + rewriter 예외 → 원래 질문으로 fallback (예외 전파 안함)")
    void enabled_rewriterException_fallsBack() throws Exception {
        given(queryRewriteProperties.enabled()).willReturn(true);
        given(queryRewriter.rewrite(anyString(), anyString()))
                .willThrow(new RuntimeException("rewriter down"));
        float[] originalEmbedding = new float[]{0.1f};
        given(embeddingProvider.embed("질문")).willReturn(originalEmbedding);
        given(ragSearchService.searchRelevantChunks(any(), eq(originalEmbedding)))
                .willReturn(List.of(chunk(0.2)));
        given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
                .willReturn("LLM 답변");
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        qnaService.askQuestion(new AskQuestionCommand(10L, "질문", 1L));
        Thread.sleep(200);

        verify(qnaLlmProvider, times(1)).generateAnswer(
                anyString(), any(PolicyMetadata.class), anyString(), anyString(), any());
    }

    private static PolicyDocumentChunkResult chunk(double distance) {
        return new PolicyDocumentChunkResult(
                1L, 10L, 0, "내용", distance, null, null, null
        );
    }

    private static Policy mockPolicy() {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getTitle()).willReturn("청년내일저축계좌");
        given(p.getCategory()).willReturn(Category.WELFARE);
        given(p.getSummary()).willReturn("저소득 청년 자산형성 지원");
        given(p.getSupportTarget()).willReturn("만 19~34세, 근로소득자");
        given(p.getSupportContent()).willReturn("월 30만원 매칭");
        given(p.getOrganization()).willReturn("보건복지부");
        given(p.getContact()).willReturn("02-123-4567");
        given(p.getApplyStart()).willReturn(LocalDate.of(2026, 5, 1));
        given(p.getApplyEnd()).willReturn(LocalDate.of(2026, 5, 31));
        given(p.getProvideType()).willReturn("현금");
        return p;
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd backend && ./gradlew test --tests "QnaServiceQueryRewriteTest" -i`
Expected: FAIL — `QnaService` 가 아직 `QueryRewriter` / `QueryRewriteProperties` 를 받지 않아 컴파일 실패

- [ ] **Step 3: QnaService 수정 — 새 의존성 + rewrite 분기**

`QnaService.java` import 구역 (라인 28 부근, `import lombok.RequiredArgsConstructor;` 아래)에 추가:
```java
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.qna.infrastructure.config.QueryRewriteProperties;
```

필드 묶음 (라인 78 부근, `private final ApplicationEventPublisher eventPublisher;` 아래)에 추가:
```java
private final QueryRewriter queryRewriter;
private final QueryRewriteProperties queryRewriteProperties;
```

`processQuestion` 메서드의 ④ RAG 단계 (현재 라인 173~175) 를 다음으로 교체:

```java
        // ④ Query rewriting (옵션) → RAG 검색
        float[] searchEmbedding = queryEmbedding;
        String searchQuery = command.question();

        if (queryRewriteProperties.enabled()) {
            Optional<String> rewritten = safeRewrite(policy.getTitle(), command.question());
            if (rewritten.isPresent()) {
                searchQuery = rewritten.get();
                try {
                    searchEmbedding = embeddingProvider.embed(searchQuery);
                } catch (Exception e) {
                    log.warn("query-rewrite 재임베딩 실패, 원래 임베딩으로 fallback: policyId={}",
                            command.policyId(), e);
                    searchEmbedding = queryEmbedding;
                    searchQuery = command.question();
                }
            }
        }

        List<PolicyDocumentChunkResult> chunks = ragSearchService.searchRelevantChunks(
                new SearchChunksCommand(command.policyId(), searchQuery), searchEmbedding);
```

`processQuestion` 메서드 다음에 (`sendCachedAnswer` 메서드 위) 새 private helper 추가:

```java
    private Optional<String> safeRewrite(String policyTitle, String question) {
        try {
            return queryRewriter.rewrite(policyTitle, question);
        } catch (Exception e) {
            log.warn("query rewriter 호출 예외, 원래 질문으로 fallback: title={}, error={}",
                    policyTitle, e.toString());
            return Optional.empty();
        }
    }
```

> 주의: ⑤ LLM 답변 생성 단계의 `command.question()` 인자는 **변경하지 않는다** (spec §3 — 답변은 원래 질문으로). `searchQuery` 는 RAG 호출에만 사용.

- [ ] **Step 4: 새 테스트 실행 → 성공 확인**

Run: `cd backend && ./gradlew test --tests "QnaServiceQueryRewriteTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 기존 QnaServiceTest 회귀 확인**

Run: `cd backend && ./gradlew test --tests "QnaServiceTest"`
Expected: PASS — Task 5 의 mock 셋업으로 `enabled=false` 가 기본 동작이라 회귀 없음

- [ ] **Step 6: Commit (Task 5 변경 + Task 6 변경 함께)**

```bash
git add backend/src/main/java/com/youthfit/qna/application/service/QnaService.java \
        backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java \
        backend/src/test/java/com/youthfit/qna/application/service/QnaServiceQueryRewriteTest.java
git commit -m "feat(qna): QnaService 에 query-rewrite 분기 + 재임베딩 + safe fallback"
```

---

## Task 7: 전체 빌드·테스트 검증

- [ ] **Step 1: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 모든 테스트 PASS

- [ ] **Step 2: 전체 빌드**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 핵심 회귀 슈트 별도 실행**

Run: `cd backend && ./gradlew test --tests "QnaServiceTest" --tests "QnaServiceQueryRewriteTest" --tests "OpenAiQueryRewriterTest" --tests "OpenAiQnaClient*" --tests "RagSearchServiceTest"`
Expected: 모든 테스트 PASS

---

## 운영 출시 가이드 (구현 외)

이 plan 이 모두 머지되어도 **query rewriting 은 기본 OFF** 입니다. DB 변경·마이그레이션 없음.

1. **스테이징 ON** — `RAG_QUERY_REWRITE_ENABLED=true` 환경변수 설정 후 컨테이너 재기동
2. **검증** — spec §1 의 진단 케이스 ("근로사업소득이 작년이 기준이야 올해가 기준이야?" 등) 로 retrieval 품질·답변 비교. 로그 (`query rewrite: original=..., rewritten=...`) 확인
3. **운영 출시** — `RAG_QUERY_REWRITE_ENABLED=true` 로 환경변수 설정 후 재기동
4. **롤백** — `RAG_QUERY_REWRITE_ENABLED=false` 로 즉시 복귀 (코드 배포 불필요)

LLM 비용은 cache miss 질문당 ~$0.0001 추가 (gpt-4o-mini, ~150 prompt + 50 completion 토큰). 일 1,000 cache miss 가정 시 ~$3/월.
