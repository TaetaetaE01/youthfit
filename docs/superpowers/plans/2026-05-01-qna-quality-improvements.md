# Q&A 품질 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Q&A 두 가지 품질 이슈 — (1) "관련 내용 없음" 답변에 출처가 표시되는 모순, (2) "이 정책 뭐야?" 같은 메타 질문 처리 불가 — 를 한 사이클로 해결한다.

**Architecture:** RAG `relevance-distance-threshold` 를 0.7 → 0.5 로 조여 미관련 청크의 LLM 도달 차단. `Policy` 엔티티 9필드를 `PolicyMetadata` record 로 묶어 LLM user message 에 포함하고, 시스템 프롬프트를 "본문 우선, 메타 보강" 명시적 우선순위로 변경.

**Tech Stack:** Spring Boot 4.0.5 (Java 21), JUnit 5 + Mockito + AssertJ, Spring Web (`RestClient`), pgvector (RAG), OpenAI Chat Completions (`gpt-4o-mini`)

**Spec:** `docs/superpowers/specs/2026-05-01-qna-quality-improvements-design.md`

---

## File Structure

### Created files

| 경로 | 책임 |
|---|---|
| `backend/src/main/java/com/youthfit/qna/application/dto/command/PolicyMetadata.java` | LLM 에 넘길 정책 메타 9필드 record + `from(Policy)` 정적 팩토리 |
| `backend/src/test/java/com/youthfit/qna/application/dto/command/PolicyMetadataTest.java` | `from(Policy)` 매핑 단위 테스트 (null 처리, enum.name() 변환) |
| `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientTest.java` | user message 포맷 + system prompt 검증 (외부 호출 없는 단위 테스트) |

### Modified files

| 경로 | 변경 내용 |
|---|---|
| `backend/src/main/resources/application.yml` | `qna.relevance-distance-threshold` 기본값 0.7 → 0.5 |
| `backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java` | `generateAnswer` 시그니처에 `PolicyMetadata` 매개변수 추가 |
| `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java` | 시그니처 매칭, user message 빌더에 메타 블록 추가, 시스템 프롬프트 명시적 우선순위로 교체 |
| `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` | `PolicyMetadata.from(policy)` 매핑 후 LLM 호출 시 전달 |
| `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java` | 기존 `verify(qnaLlmProvider).generateAnswer(...)` 4인자 → 5인자 갱신, 메타 질문 시나리오 추가, `mockPolicy()` 메타 필드 stub |

---

## Task 1: `PolicyMetadata` record 신설

`Policy` 엔티티 9필드를 LLM 에 넘길 record 와 정적 팩토리. 다른 코드에 영향 없는 독립 작업.

**Files:**
- Create: `backend/src/main/java/com/youthfit/qna/application/dto/command/PolicyMetadata.java`
- Test: `backend/src/test/java/com/youthfit/qna/application/dto/command/PolicyMetadataTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/youthfit/qna/application/dto/command/PolicyMetadataTest.java`:

```java
package com.youthfit.qna.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyMetadata.from")
class PolicyMetadataTest {

    @Nested
    @DisplayName("Policy 9필드를 record 로 매핑")
    class HappyPath {

        @Test
        @DisplayName("모든 필드가 채워진 Policy 는 모든 필드가 매핑된다")
        void allFieldsMapped() {
            Policy policy = Policy.builder()
                    .title("청년내일저축계좌")
                    .summary("저소득 청년 자산형성 지원")
                    .body("본문")
                    .supportTarget("만 19~34세, 근로소득자")
                    .selectionCriteria("선정 기준")
                    .supportContent("월 30만원 매칭")
                    .organization("보건복지부")
                    .contact("02-123-4567")
                    .category(Category.WELFARE)
                    .regionCode("00")
                    .applyStart(LocalDate.of(2026, 5, 1))
                    .applyEnd(LocalDate.of(2026, 5, 31))
                    .referenceYear(2026)
                    .supportCycle("매월")
                    .provideType("현금")
                    .build();

            PolicyMetadata metadata = PolicyMetadata.from(policy);

            assertThat(metadata.category()).isEqualTo("WELFARE");
            assertThat(metadata.summary()).isEqualTo("저소득 청년 자산형성 지원");
            assertThat(metadata.supportTarget()).isEqualTo("만 19~34세, 근로소득자");
            assertThat(metadata.supportContent()).isEqualTo("월 30만원 매칭");
            assertThat(metadata.organization()).isEqualTo("보건복지부");
            assertThat(metadata.contact()).isEqualTo("02-123-4567");
            assertThat(metadata.applyStart()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(metadata.applyEnd()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(metadata.provideType()).isEqualTo("현금");
        }
    }

    @Nested
    @DisplayName("null 필드 처리")
    class NullFields {

        @Test
        @DisplayName("nullable 필드가 null 이면 record 필드도 null")
        void nullableFieldsRemainNull() {
            Policy policy = Policy.builder()
                    .title("최소 정보 정책")
                    .category(Category.JOBS)
                    .build();

            PolicyMetadata metadata = PolicyMetadata.from(policy);

            assertThat(metadata.category()).isEqualTo("JOBS");
            assertThat(metadata.summary()).isNull();
            assertThat(metadata.supportTarget()).isNull();
            assertThat(metadata.supportContent()).isNull();
            assertThat(metadata.organization()).isNull();
            assertThat(metadata.contact()).isNull();
            assertThat(metadata.applyStart()).isNull();
            assertThat(metadata.applyEnd()).isNull();
            assertThat(metadata.provideType()).isNull();
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd backend && ./gradlew test --tests PolicyMetadataTest
```

Expected: 컴파일 에러 — `PolicyMetadata` 가 존재하지 않음.

- [ ] **Step 3: `PolicyMetadata` record 구현**

`backend/src/main/java/com/youthfit/qna/application/dto/command/PolicyMetadata.java`:

```java
package com.youthfit.qna.application.dto.command;

import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;

public record PolicyMetadata(
        String category,
        String summary,
        String supportTarget,
        String supportContent,
        String organization,
        String contact,
        LocalDate applyStart,
        LocalDate applyEnd,
        String provideType
) {

    public static PolicyMetadata from(Policy policy) {
        return new PolicyMetadata(
                policy.getCategory() == null ? null : policy.getCategory().name(),
                policy.getSummary(),
                policy.getSupportTarget(),
                policy.getSupportContent(),
                policy.getOrganization(),
                policy.getContact(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                policy.getProvideType()
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests PolicyMetadataTest
```

Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/application/dto/command/PolicyMetadata.java \
        backend/src/test/java/com/youthfit/qna/application/dto/command/PolicyMetadataTest.java
git commit -m "feat(qna): PolicyMetadata record 신설 + Policy 9필드 매핑"
```

---

## Task 2: `QnaLlmProvider` 시그니처 변경 + 호출자/구현체/기존 테스트 일괄 갱신

`generateAnswer` 시그니처에 `PolicyMetadata` 매개변수를 추가한다. 인터페이스·구현체·호출자·기존 테스트의 시그니처가 동시에 안 맞으면 전체 빌드가 깨지므로 한 step 으로 일괄 변경. 이 task 에서는 `PolicyMetadata` 인자를 **받기만** 하고 실제로 user message 에 사용하는 변경은 Task 3 에서.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java`
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java`
- Modify: `backend/src/main/java/com/youthfit/qna/application/service/QnaService.java`
- Modify: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`

- [ ] **Step 1: `QnaLlmProvider` 인터페이스 시그니처 변경**

`backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java`:

```java
package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.command.PolicyMetadata;

import java.util.function.Consumer;

public interface QnaLlmProvider {

    String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer);
}
```

- [ ] **Step 2: `OpenAiQnaClient` 시그니처 매칭 (메타 인자 받지만 사용은 Task 3 에서)**

`backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java` 의 `generateAnswer` 메서드 시그니처와 첫 번째 줄(user message 빌딩) 만 변경. 나머지 본문은 그대로 둔다.

```java
@Override
public String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer) {
    String userMessage = "정책명: " + policyTitle + "\n\n정책 원문 컨텍스트:\n" + context + "\n\n질문: " + question;
    // ... (기존 본문 그대로)
```

`import com.youthfit.qna.application.dto.command.PolicyMetadata;` 를 import 섹션에 추가.

> 주의: 이 step 에서는 `metadata` 인자를 의도적으로 사용하지 않는다. Task 3 에서 user message 빌더 변경 시 함께 처리해야 TDD 흐름이 명확해진다.

- [ ] **Step 3: `QnaService.processQuestion` 호출 라인 변경**

`backend/src/main/java/com/youthfit/qna/application/service/QnaService.java` 의 LLM 호출 부분 (현재 라인 165~169 근처):

기존:
```java
fullAnswer = qnaLlmProvider.generateAnswer(
        policy.getTitle(), context, command.question(),
        chunk -> sendChunkEvent(emitter, chunk)
);
```

변경 후:
```java
PolicyMetadata metadata = PolicyMetadata.from(policy);
fullAnswer = qnaLlmProvider.generateAnswer(
        policy.getTitle(), metadata, context, command.question(),
        chunk -> sendChunkEvent(emitter, chunk)
);
```

`import com.youthfit.qna.application.dto.command.PolicyMetadata;` 를 import 섹션에 추가.

- [ ] **Step 4: `QnaServiceTest` 의 `verify(...).generateAnswer(...)` 패턴 5인자로 갱신**

`backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java` 에서 다음 두 패턴을 일괄 치환:

치환 전 (9곳):
```java
qnaLlmProvider.generateAnswer(anyString(), anyString(), anyString(), any())
```

치환 후:
```java
qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any())
```

또한 `Happy.threshold_passesAndCallsLlm()` 테스트의 `consumer` 인덱스 변경 (3 → 4):

치환 전:
```java
.willAnswer(inv -> {
    Consumer<String> consumer = inv.getArgument(3);
    consumer.accept("답변 ");
    consumer.accept("일부.");
    return "답변 일부.";
});
```

치환 후:
```java
.willAnswer(inv -> {
    Consumer<String> consumer = inv.getArgument(4);
    consumer.accept("답변 ");
    consumer.accept("일부.");
    return "답변 일부.";
});
```

`import com.youthfit.qna.application.dto.command.PolicyMetadata;` 추가.

- [ ] **Step 5: 빌드 + 기존 테스트 통과 확인**

```bash
cd backend && ./gradlew compileJava compileTestJava
cd backend && ./gradlew test --tests QnaServiceTest --tests PolicyMetadataTest
```

Expected: 컴파일 PASS. 기존 테스트 모두 그대로 PASS (verify 패턴만 갱신, 행동 변경 없음).

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/application/port/QnaLlmProvider.java \
        backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java \
        backend/src/main/java/com/youthfit/qna/application/service/QnaService.java \
        backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java
git commit -m "refactor(qna): QnaLlmProvider 시그니처에 PolicyMetadata 매개변수 추가 (이번 단계는 미사용 매개변수)"
```

---

## Task 3: `OpenAiQnaClient` user message 빌더 + 시스템 프롬프트 변경

이번 task 가 본 사이클의 핵심. `OpenAiQnaClientTest` 를 신설하여 user message 포맷·system prompt·`null` 라인 생략을 검증.

**Files:**
- Modify: `backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java`
- Test: `backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientTest.java`

> `OpenAiQnaClient` 의 OpenAI HTTP 호출 부분은 외부 의존이라 단위 테스트에서 검증하기 어렵다. user message 빌더 로직만 별도 메서드(`buildUserMessage`)로 추출해 검증한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientTest.java`:

```java
package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.dto.command.PolicyMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiQnaClient.buildUserMessage")
class OpenAiQnaClientTest {

    @Nested
    @DisplayName("9필드 모두 채워진 메타데이터")
    class FullMetadata {

        @Test
        @DisplayName("정책명·메타 9라인·본문 컨텍스트·질문이 모두 포함된다")
        void allFieldsRendered() {
            PolicyMetadata metadata = new PolicyMetadata(
                    "WELFARE",
                    "저소득 청년 자산형성 지원",
                    "만 19~34세, 근로소득자",
                    "월 30만원 매칭",
                    "보건복지부",
                    "02-123-4567",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 31),
                    "현금"
            );
            String context = "[청크 0]\n본문 내용\n\n";
            String question = "재학생도 가능?";

            String userMessage = OpenAiQnaClient.buildUserMessage("청년내일저축계좌", metadata, context, question);

            assertThat(userMessage).contains("정책명: 청년내일저축계좌");
            assertThat(userMessage).contains("정책 메타데이터:");
            assertThat(userMessage).contains("- 분야: WELFARE");
            assertThat(userMessage).contains("- 요약: 저소득 청년 자산형성 지원");
            assertThat(userMessage).contains("- 지원 대상: 만 19~34세, 근로소득자");
            assertThat(userMessage).contains("- 지원 내용: 월 30만원 매칭");
            assertThat(userMessage).contains("- 운영 기관: 보건복지부");
            assertThat(userMessage).contains("- 문의처: 02-123-4567");
            assertThat(userMessage).contains("- 신청 기간: 2026-05-01 ~ 2026-05-31");
            assertThat(userMessage).contains("- 지급 방식: 현금");
            assertThat(userMessage).contains("정책 본문 컨텍스트:\n[청크 0]\n본문 내용");
            assertThat(userMessage).contains("질문: 재학생도 가능?");
        }
    }

    @Nested
    @DisplayName("null 필드 처리")
    class NullFields {

        @Test
        @DisplayName("category 만 채워진 메타데이터는 다른 라인이 생략된다")
        void onlyCategoryRendered() {
            PolicyMetadata metadata = new PolicyMetadata(
                    "JOBS", null, null, null, null, null, null, null, null
            );

            String userMessage = OpenAiQnaClient.buildUserMessage("정책", metadata, "context", "질문");

            assertThat(userMessage).contains("- 분야: JOBS");
            assertThat(userMessage).doesNotContain("- 요약:");
            assertThat(userMessage).doesNotContain("- 지원 대상:");
            assertThat(userMessage).doesNotContain("- 지원 내용:");
            assertThat(userMessage).doesNotContain("- 운영 기관:");
            assertThat(userMessage).doesNotContain("- 문의처:");
            assertThat(userMessage).doesNotContain("- 신청 기간:");
            assertThat(userMessage).doesNotContain("- 지급 방식:");
        }

        @Test
        @DisplayName("applyStart 만 있고 applyEnd 가 null 이면 신청 기간 라인 생략")
        void partialApplyDates_skipsLine() {
            PolicyMetadata metadata = new PolicyMetadata(
                    null, null, null, null, null, null,
                    LocalDate.of(2026, 5, 1), null, null
            );

            String userMessage = OpenAiQnaClient.buildUserMessage("정책", metadata, "context", "질문");

            assertThat(userMessage).doesNotContain("- 신청 기간:");
        }

        @Test
        @DisplayName("모든 메타 필드가 null 이면 메타 블록 헤더만 남기지 않는다")
        void allNull_skipsBlock() {
            PolicyMetadata metadata = new PolicyMetadata(
                    null, null, null, null, null, null, null, null, null
            );

            String userMessage = OpenAiQnaClient.buildUserMessage("정책", metadata, "context", "질문");

            assertThat(userMessage).doesNotContain("정책 메타데이터:");
            assertThat(userMessage).contains("정책명: 정책");
            assertThat(userMessage).contains("정책 본문 컨텍스트:");
            assertThat(userMessage).contains("질문: 질문");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd backend && ./gradlew test --tests OpenAiQnaClientTest
```

Expected: 컴파일 에러 — `OpenAiQnaClient.buildUserMessage` 정적 메서드가 존재하지 않음.

- [ ] **Step 3: `OpenAiQnaClient` 의 user message 빌더와 시스템 프롬프트 구현**

`backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java` 전체 수정. 핵심: ① `SYSTEM_PROMPT` 교체, ② `buildUserMessage` 정적 메서드 추가, ③ `generateAnswer` 가 `buildUserMessage` 사용.

```java
package com.youthfit.qna.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.qna.application.dto.command.PolicyMetadata;
import com.youthfit.qna.application.port.QnaLlmProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class OpenAiQnaClient implements QnaLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQnaClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 Q&A 전문가입니다.
            사용자가 특정 정책에 대해 질문하면, 제공된 정책 메타데이터와 본문 컨텍스트에 근거하여 답변하세요.

            규칙:
            - 본문 컨텍스트에 답이 있으면 본문을 우선 사용하세요.
            - 본문에 답이 없으면 정책 메타데이터로 보강하세요.
            - 메타데이터와 본문 어느 쪽에도 없는 내용을 지어내지 마세요.
            - 메타데이터와 본문 모두에 답이 없으면 "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다."라고 답변하세요.
            - 쉬운 한국어로 답변하세요.
            - 답변은 간결하고 핵심적으로 작성하세요.
            """;

    private final OpenAiQnaProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer) {
        String userMessage = buildUserMessage(policyTitle, metadata, context, question);

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            InputStream inputStream = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(InputStream.class);

            if (inputStream == null) {
                throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "Q&A 답변 생성에 실패했습니다");
            }

            return readStreamResponse(inputStream, chunkConsumer);
        } catch (YouthFitException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI Q&A 스트리밍 호출 실패: policyTitle={}", policyTitle, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "Q&A 답변 생성에 실패했습니다");
        }
    }

    static String buildUserMessage(String policyTitle, PolicyMetadata metadata, String context, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("정책명: ").append(policyTitle).append("\n\n");

        List<String> metaLines = new ArrayList<>();
        if (metadata != null) {
            if (metadata.category() != null)       metaLines.add("- 분야: " + metadata.category());
            if (metadata.summary() != null)        metaLines.add("- 요약: " + metadata.summary());
            if (metadata.supportTarget() != null)  metaLines.add("- 지원 대상: " + metadata.supportTarget());
            if (metadata.supportContent() != null) metaLines.add("- 지원 내용: " + metadata.supportContent());
            if (metadata.organization() != null)   metaLines.add("- 운영 기관: " + metadata.organization());
            if (metadata.contact() != null)        metaLines.add("- 문의처: " + metadata.contact());
            if (metadata.applyStart() != null && metadata.applyEnd() != null) {
                metaLines.add("- 신청 기간: " + metadata.applyStart() + " ~ " + metadata.applyEnd());
            }
            if (metadata.provideType() != null)    metaLines.add("- 지급 방식: " + metadata.provideType());
        }
        if (!metaLines.isEmpty()) {
            sb.append("정책 메타데이터:\n");
            for (String line : metaLines) sb.append(line).append("\n");
            sb.append("\n");
        }

        sb.append("정책 본문 컨텍스트:\n").append(context).append("\n\n");
        sb.append("질문: ").append(question);
        return sb.toString();
    }

    private String readStreamResponse(InputStream inputStream, Consumer<String> chunkConsumer) throws Exception {
        StringBuilder fullAnswer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode node = objectMapper.readTree(data);
                JsonNode choices = node.get("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null || !delta.has("content")) {
                    continue;
                }
                String content = delta.get("content").asText();
                if (content != null && !content.isEmpty()) {
                    fullAnswer.append(content);
                    chunkConsumer.accept(content);
                }
            }
        }

        return fullAnswer.toString();
    }
}
```

> 주석: `buildUserMessage` 는 `package-private static` — 외부 호출은 막되 같은 패키지 테스트에서 접근 가능. `applyStart` 와 `applyEnd` 가 둘 다 채워진 경우만 신청 기간 라인을 출력 (한쪽만 채워진 케이스는 의미가 모호하므로 생략).

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests OpenAiQnaClientTest
```

Expected: PASS (4 tests).

- [ ] **Step 5: 전체 단위 테스트 회귀 확인**

```bash
cd backend && ./gradlew test --tests QnaServiceTest --tests OpenAiQnaClientTest --tests PolicyMetadataTest
```

Expected: 모두 PASS. `QnaServiceTest` 가 Task 2 에서 시그니처 갱신만 했으므로 행동 회귀 없음.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClient.java \
        backend/src/test/java/com/youthfit/qna/infrastructure/external/OpenAiQnaClientTest.java
git commit -m "feat(qna): user message 에 정책 메타 9필드 포함 + system prompt 우선순위 명시"
```

---

## Task 4: `QnaServiceTest` 메타 질문 시나리오 추가

`mockPolicy()` 에 메타 필드 stub 을 추가하고, "이 정책 뭐야?" 메타 질문 입력 → LLM 호출 시 `PolicyMetadata` 인자가 9필드 매핑된 채로 전달됨을 검증.

**Files:**
- Modify: `backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java`

- [ ] **Step 1: `mockPolicy()` 헬퍼에 메타 필드 stub 추가**

`QnaServiceTest.java` 의 `mockPolicy(...)` 정적 메서드를 다음과 같이 확장:

기존:
```java
private static Policy mockPolicy(Long id, String title) {
    Policy p = org.mockito.Mockito.mock(Policy.class);
    given(p.getTitle()).willReturn(title);
    return p;
}
```

변경 후:
```java
private static Policy mockPolicy(Long id, String title) {
    Policy p = org.mockito.Mockito.mock(Policy.class);
    given(p.getTitle()).willReturn(title);
    given(p.getCategory()).willReturn(com.youthfit.policy.domain.model.Category.WELFARE);
    given(p.getSummary()).willReturn("저소득 청년 자산형성 지원");
    given(p.getSupportTarget()).willReturn("만 19~34세, 근로소득자");
    given(p.getSupportContent()).willReturn("월 30만원 매칭");
    given(p.getOrganization()).willReturn("보건복지부");
    given(p.getContact()).willReturn("02-123-4567");
    given(p.getApplyStart()).willReturn(java.time.LocalDate.of(2026, 5, 1));
    given(p.getApplyEnd()).willReturn(java.time.LocalDate.of(2026, 5, 31));
    given(p.getProvideType()).willReturn("현금");
    return p;
}
```

> 주석: 클래스 레벨에 `@MockitoSettings(strictness = Strictness.LENIENT)` 가 이미 있어서 사용 안 된 stub 도 OK.

- [ ] **Step 2: 메타 질문 시나리오 테스트 추가 (실패 테스트 작성)**

`QnaServiceTest.Happy` 중첩 클래스 안에 새 테스트 추가. `import org.mockito.ArgumentCaptor;` 와 `import com.youthfit.qna.application.dto.command.PolicyMetadata;` 가 import 섹션에 추가되어야 함 (Task 2 에서 이미 추가했다면 중복 무시).

```java
@Test
@DisplayName("LLM 호출 시 PolicyMetadata 9필드가 매핑되어 전달된다")
void llmReceivesMappedPolicyMetadata() throws Exception {
    cacheMissDefaults();
    given(ragSearchService.searchRelevantChunks(any(), any())).willReturn(List.of(chunk(0.2)));
    given(qnaLlmProvider.generateAnswer(anyString(), any(PolicyMetadata.class), anyString(), anyString(), any()))
            .willReturn("LLM 답변");
    given(objectMapper.writeValueAsString(any())).willReturn("[]");

    AskQuestionCommand command = new AskQuestionCommand(10L, "이 정책 뭐야?", 1L);
    qnaService.askQuestion(command);
    Thread.sleep(200);

    ArgumentCaptor<PolicyMetadata> captor = ArgumentCaptor.forClass(PolicyMetadata.class);
    verify(qnaLlmProvider).generateAnswer(
            anyString(), captor.capture(), anyString(), anyString(), any());

    PolicyMetadata captured = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(captured.category()).isEqualTo("WELFARE");
    org.assertj.core.api.Assertions.assertThat(captured.summary()).isEqualTo("저소득 청년 자산형성 지원");
    org.assertj.core.api.Assertions.assertThat(captured.supportTarget()).isEqualTo("만 19~34세, 근로소득자");
    org.assertj.core.api.Assertions.assertThat(captured.organization()).isEqualTo("보건복지부");
    org.assertj.core.api.Assertions.assertThat(captured.contact()).isEqualTo("02-123-4567");
    org.assertj.core.api.Assertions.assertThat(captured.applyStart()).isEqualTo(java.time.LocalDate.of(2026, 5, 1));
    org.assertj.core.api.Assertions.assertThat(captured.applyEnd()).isEqualTo(java.time.LocalDate.of(2026, 5, 31));
    org.assertj.core.api.Assertions.assertThat(captured.provideType()).isEqualTo("현금");
}
```

- [ ] **Step 3: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests QnaServiceTest
```

Expected: 모든 테스트 PASS — Task 2 에서 이미 `QnaService` 가 `PolicyMetadata.from(policy)` 를 호출하도록 변경되었기 때문에 새 테스트도 바로 통과.

> 만약 FAIL 한다면 `QnaService.processQuestion` 의 `PolicyMetadata.from(policy)` 호출이 누락되었거나 `mockPolicy()` 의 stub 이 빠진 것. Task 2 / Task 4-Step 1 을 다시 확인.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/test/java/com/youthfit/qna/application/service/QnaServiceTest.java
git commit -m "test(qna): LLM 호출 시 PolicyMetadata 9필드 매핑 검증 테스트 추가"
```

---

## Task 5: `application.yml` threshold 0.7 → 0.5

가장 단순한 변경. 환경변수 fallback 만 변경.

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: `application.yml` 수정**

기존:
```yaml
  qna:
    cache-ttl-hours: ${QNA_CACHE_TTL_HOURS:24}
    # 한국어 임베딩 cosine distance. 더 엄격: 0.5~0.6, 더 관대: 0.8. RagSearchService 로그로 운영값 조정.
    relevance-distance-threshold: ${QNA_RELEVANCE_DISTANCE_THRESHOLD:0.7}
```

변경 후:
```yaml
  qna:
    cache-ttl-hours: ${QNA_CACHE_TTL_HOURS:24}
    # 한국어 임베딩 cosine distance. 더 엄격: 0.45~0.5, 더 관대: 0.55~0.6. RagSearchService 로그로 운영값 조정.
    relevance-distance-threshold: ${QNA_RELEVANCE_DISTANCE_THRESHOLD:0.5}
```

- [ ] **Step 2: 빌드 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: PASS (yml 검증은 Spring Boot context 로드 시점이라 컴파일에선 안 잡힘. 다음 step 의 통합 빌드에서 검증).

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/application.yml
git commit -m "fix(qna): relevance-distance-threshold 기본값 0.7 → 0.5 (미관련 청크 차단 강화)"
```

---

## Task 6: 전체 빌드 + 수동 검증 + PR 분할 제안 / PR 생성

모든 변경이 함께 정상 동작하는지 도커 환경에서 확인.

**Files:**
- 코드 변경 없음 (검증만)

- [ ] **Step 1: 전체 빌드 + 테스트**

```bash
cd backend && ./gradlew build
```

Expected: BUILD SUCCESSFUL. 모든 테스트 PASS.

- [ ] **Step 2: 도커로 백엔드 재시작**

프로젝트 루트에서:

```bash
docker compose up -d --build backend
docker compose logs -f backend | head -100
```

Expected: 백엔드가 정상 부팅. `relevance-distance-threshold: 0.5` 로 로드된 로그 확인 (디버그 로그가 없으면 직접 검증 어려우므로 다음 step 으로 진행).

- [ ] **Step 3: 메타 질문 수동 검증 (5~8개 변형)**

프론트엔드(`http://localhost:5173`) 에서 가이드가 생성된 정책(예: 정책 30번) 을 열고 Q&A 패널에 다음 질문들을 차례로 입력:

1. "이 정책 뭐야?"
2. "어디 신청해?"
3. "언제까지?"
4. "누가 받을 수 있어?"
5. "얼마 받아?"
6. "어떤 기관이 운영해?"
7. "지급 방식은?"

기대 결과:
- 정책 분야·요약·운영 정보 기반으로 합리적 답변
- "관련 내용이 명시되어 있지 않" 패턴이 거의 안 나옴
- sources 박스: 본문 청크 일부가 표시되거나 비어 있을 수 있음 (둘 다 정상)

- [ ] **Step 4: 무관 질문 검증 (출처 정합성)**

같은 정책 페이지에서:

1. "오늘 점심 뭐 먹지?"
2. "이 정책 만든 사람 누구야?"

기대 결과:
- 답변: "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다..."
- sources 박스: **비어 있음** (이전 동작에서는 5개가 표시됐던 것이 빈 배열로 변경됨)

- [ ] **Step 5: 세부 질문 회귀 확인**

기존에 잘 동작하던 질문 2~3 개를 다시 던져 답변 품질 회귀 없음 확인:

1. "신청 자격은 뭐야?"
2. "지원 금액은 얼마야?"
3. "신청 절차는 어떻게 돼?"

기대 결과:
- 본문 청크 기반 답변 (기존과 동일)
- sources 박스: 본문 청크 표시

- [ ] **Step 6: PR 분할 제안 검토**

본 사이클은 spec 결정 1번에 따라 단일 PR. 다음 항목을 PR 본문에 정리:

- threshold 변경 (1개 커밋)
- PolicyMetadata + 시그니처 리팩터링 + 기존 테스트 갱신 (3 커밋)
- user message 빌더 + system prompt 변경 (1 커밋)
- 메타 질문 검증 테스트 (1 커밋)
- 합계 5~6 커밋 / 7~9 파일 변경

- [ ] **Step 7: PR 생성**

```bash
# 현재 브랜치 확인 후 push (워크트리 사용 시 이미 브랜치가 잡혀 있음)
git rev-parse --abbrev-ref HEAD
git log --oneline origin/main..HEAD
git push -u origin "$(git rev-parse --abbrev-ref HEAD)"
gh pr create --title "feat(qna): Q&A 품질 개선 (출처 정합성 + 메타 질문 대응)" --body "$(cat <<'EOF'
## Summary
- RAG `relevance-distance-threshold` 0.7 → 0.5 로 조여 미관련 청크가 LLM 까지 도달하지 못하도록 차단 (이슈 1: "관련 내용 없음" + 출처 5개 모순 해소)
- `Policy` 9필드를 `PolicyMetadata` record 로 묶어 user message 에 포함, 시스템 프롬프트를 "본문 우선, 메타 보강" 명시적 우선순위로 교체 (이슈 2: 메타 질문 처리)
- spec: docs/superpowers/specs/2026-05-01-qna-quality-improvements-design.md
- plan: docs/superpowers/plans/2026-05-01-qna-quality-improvements.md

## Test plan
- [x] `./gradlew build` 전체 PASS
- [x] `PolicyMetadataTest` 신규 (2 tests)
- [x] `OpenAiQnaClientTest` 신규 (4 tests, user message 포맷 + null 처리)
- [x] `QnaServiceTest` 메타 질문 시나리오 추가 + 기존 verify 패턴 5인자로 갱신
- [x] 도커 환경 메타 질문 5~8개 변형 정상 응답
- [x] 무관 질문에서 sources 비어 있음 확인
- [x] 세부 질문 회귀 없음 확인

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 후속 / 미결 (Out of scope)

본 PR 머지 후 운영 데이터 1~2주 누적하여 spec § 12 의 KPI 측정. 결과에 따라:

- LLM 답변 "관련 내용 없" 패턴 빈도가 여전히 높음 → 후속 사이클(B 패턴 매칭 또는 C NO_ANSWER 토큰)
- 정상 질문 거절률이 너무 높음 → env var `QNA_RELEVANCE_DISTANCE_THRESHOLD=0.55` 로 즉시 완화 (PR 불필요)
- 정밀 인용 ROI 명확 → v1+ 사이클로 분리
