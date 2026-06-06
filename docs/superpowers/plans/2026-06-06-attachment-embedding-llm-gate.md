# 첨부 임베딩 LLM 선별 게이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책에 첨부가 2개 이상일 때 LLM 이 각 첨부의 RAG 임베딩 가치를 판단하여, 가치 있는 첨부만 임베딩 대상에 포함시킨다.

**Architecture:** 게이트는 RAG 인덱싱 직전 단계인 `AttachmentReindexService.reindex()`(ingestion 모듈, 첨부 텍스트가 본문에 머지되는 유일한 지점)에 삽입한다. 첨부 ≥2개일 때만 LLM 포트(`AttachmentEmbeddingJudge`)를 호출하고, 판정 결과를 `PolicyAttachment` 에 영속화해 캐시로 재사용한다. LLM 실패 시 fail-open(포함)으로 폴백한다.

**Tech Stack:** Java 21, Spring Boot 4, JPA/PostgreSQL, OpenAI Chat API(`gpt-4o-mini`, `response_format: json_object`), 기존 `OpenAiPolicyPeriodExtractor` 어댑터 패턴 복제.

**관련 스펙:** `docs/superpowers/specs/2026-06-06-attachment-embedding-llm-gate-design.md`

---

## 설계 결정 (구현 전 확정)

스펙의 "미해결" 항목을 다음과 같이 확정한다:

1. **통합 지점**: `AttachmentReindexService.reindex()`. 첨부 텍스트가 본문에 머지되는 곳은 `RagIndexingService`(이미 머지된 문자열만 받음)가 아니라 `AttachmentReindexService.mergeContent()` 직전이다. `findExtractedByPolicyId()` 로 받은 첨부 리스트를 게이트로 거른 뒤 `mergeContent()` 에 넘긴다.
2. **판정 입력**: 각 첨부 `extractedText` 의 앞 `maxPreviewChars`(기본 1500자) + 정책 제목/요약. 전체 텍스트를 보내지 않아 토큰 비용을 억제한다.
3. **판정 결과 저장**: `policy_attachment` 에 `embedding_included`(Boolean, nullable=미판정) + `embedding_decision_reason`(varchar 500) 컬럼 추가. 별도 캐시 테이블을 만들지 않는다.
4. **캐시/변경감지**: 이미 판정된 첨부(`embeddingIncluded != null`)는 LLM 재호출하지 않는다. 첨부가 재추출되면(`markExtracted`) 판정을 `null` 로 리셋해 다음 인덱싱에서 재판정한다. (스케줄러는 60초마다 도므로 캐시가 없으면 동일 첨부에 LLM 이 반복 호출됨)
5. **임계 기준**: 보수적(애매하면 포함). 실질 정책내용(대상·금액·일정·절차) = 포함, 단순 서식·동의서·개인정보동의·반복 안내문 = 제외.
6. **fail-open**: LLM 호출/파싱 실패 시 미판정 첨부를 모두 `included=true` 로 저장(콘텐츠 손실 방지 + 재호출 방지).
7. **모듈**: 게이트 포트/구현 모두 `ingestion` 모듈에 둔다. `AttachmentReindexService` 가 ingestion 에 있고 이미 `rag` 를 의존하므로 새 모듈 의존을 만들지 않는다.

## File Structure

**생성:**
- `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentEmbeddingJudge.java` — 게이트 포트 인터페이스
- `backend/src/main/java/com/youthfit/ingestion/application/dto/command/AttachmentEmbeddingJudgeCommand.java` — 게이트 입력 record
- `backend/src/main/java/com/youthfit/ingestion/application/dto/result/AttachmentEmbeddingResult.java` — 게이트 출력 record
- `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentEmbeddingJudge.java` — OpenAI 어댑터(포트 구현)
- `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentGateProperties.java` — 게이트 LLM 설정
- `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceGateTest.java` — 게이트 분기 단위 테스트
- `backend/src/test/java/com/youthfit/policy/domain/model/PolicyAttachmentEmbeddingDecisionTest.java` — 엔티티 도메인 메서드 테스트

**수정:**
- `backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java` — `ATTACHMENT_GATE` enum 추가
- `backend/src/main/java/com/youthfit/policy/domain/model/PolicyAttachment.java` — 판정 필드 + `decideEmbedding()` + `markExtracted` 리셋
- `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java` — 게이트 통합
- `backend/src/main/resources/application.yml` — `openai.ingestion.attachment-gate.*` 설정
- `backend/src/main/resources/db/` 초기화 SQL(또는 마이그레이션) — 신규 컬럼 ALTER

---

## Task 1: LlmModule 에 ATTACHMENT_GATE 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java`

- [ ] **Step 1: enum 값 추가**

`LlmModule.java` 를 다음으로 교체한다:

```java
package com.youthfit.metrics.domain.model;

public enum LlmModule {
    QNA,
    GUIDE,
    EMBEDDING,
    INGESTION,
    ELIGIBILITY,
    QUERY_REWRITE,
    ATTACHMENT_GATE
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/youthfit/metrics/domain/model/LlmModule.java
git commit -m "feat: LlmModule 에 ATTACHMENT_GATE 추가"
```

---

## Task 2: PolicyAttachment 에 임베딩 판정 상태 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/PolicyAttachment.java`
- Test: `backend/src/test/java/com/youthfit/policy/domain/model/PolicyAttachmentEmbeddingDecisionTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`PolicyAttachmentEmbeddingDecisionTest.java` 생성:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyAttachment 임베딩 판정")
class PolicyAttachmentEmbeddingDecisionTest {

    private PolicyAttachment extractedAttachment(String text) {
        PolicyAttachment a = PolicyAttachment.builder()
                .name("붙임1.hwp").url("http://x/a.hwp").mediaType("application/x-hwp")
                .build();
        a.markDownloading();
        a.markDownloaded("key", "hash");
        a.markExtracting();
        a.markExtracted(text);
        return a;
    }

    @Nested
    @DisplayName("decideEmbedding")
    class DecideEmbedding {

        @Test
        @DisplayName("포함 판정을 저장한다")
        void includes() {
            PolicyAttachment a = extractedAttachment("내용");
            a.decideEmbedding(true, "실질 정책내용 포함");
            assertThat(a.getEmbeddingIncluded()).isTrue();
            assertThat(a.getEmbeddingDecisionReason()).isEqualTo("실질 정책내용 포함");
        }

        @Test
        @DisplayName("제외 판정을 저장한다")
        void excludes() {
            PolicyAttachment a = extractedAttachment("내용");
            a.decideEmbedding(false, "단순 동의서 양식");
            assertThat(a.getEmbeddingIncluded()).isFalse();
            assertThat(a.getEmbeddingDecisionReason()).isEqualTo("단순 동의서 양식");
        }

        @Test
        @DisplayName("사유가 500자를 넘으면 잘라 저장한다")
        void truncatesReason() {
            PolicyAttachment a = extractedAttachment("내용");
            a.decideEmbedding(true, "가".repeat(600));
            assertThat(a.getEmbeddingDecisionReason()).hasSize(500);
        }
    }

    @Nested
    @DisplayName("판정 리셋")
    class Reset {

        @Test
        @DisplayName("재추출(markExtracted)되면 판정이 null 로 리셋된다")
        void resetsOnReextraction() {
            PolicyAttachment a = extractedAttachment("v1");
            a.decideEmbedding(false, "제외");

            a.markPendingReextraction();
            a.markDownloading();
            a.markDownloaded("key2", "hash2");
            a.markExtracting();
            a.markExtracted("v2");

            assertThat(a.getEmbeddingIncluded()).isNull();
            assertThat(a.getEmbeddingDecisionReason()).isNull();
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.model.PolicyAttachmentEmbeddingDecisionTest"`
Expected: 컴파일 실패 — `getEmbeddingIncluded()`, `decideEmbedding()` 미정의

- [ ] **Step 3: 필드와 도메인 메서드 추가**

`PolicyAttachment.java` 의 `skipReason` 필드(L57-59) 바로 아래에 컬럼 추가:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 30)
    private SkipReason skipReason;

    @Column(name = "embedding_included")
    private Boolean embeddingIncluded;

    @Column(name = "embedding_decision_reason", length = 500)
    private String embeddingDecisionReason;
```

`markExtracted` 메서드(L93-98)에 리셋을 추가:

```java
    public void markExtracted(String text) {
        require(extractionStatus == AttachmentStatus.EXTRACTING, AttachmentStatus.EXTRACTED);
        this.extractionStatus = AttachmentStatus.EXTRACTED;
        this.extractedText = text;
        this.extractionError = null;
        // 재추출 시 임베딩 가치 판정을 무효화하여 다음 인덱싱에서 재판정한다.
        this.embeddingIncluded = null;
        this.embeddingDecisionReason = null;
    }
```

`resetFailedToPending()` 메서드(L130-133) 아래에 `decideEmbedding` 추가:

```java
    public void decideEmbedding(boolean included, String reason) {
        this.embeddingIncluded = included;
        this.embeddingDecisionReason = truncate(reason);
    }
```

> `truncate(String)` 는 이미 존재하는 private static helper(L144-147, 최대 500자)이며 `ERROR_MAX_LENGTH = 500` 과 일치한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.policy.domain.model.PolicyAttachmentEmbeddingDecisionTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/PolicyAttachment.java \
        backend/src/test/java/com/youthfit/policy/domain/model/PolicyAttachmentEmbeddingDecisionTest.java
git commit -m "feat: PolicyAttachment 에 임베딩 가치 판정 상태 추가"
```

---

## Task 3: DB 컬럼 마이그레이션 SQL

**Files:**
- Modify: `backend/src/main/resources/db/` 초기화 SQL (프로젝트의 init/migration SQL 위치)

> **배경(메모리 db-schema-init-order):** JPA `ddl-auto` 가 nullable 컬럼을 자동 생성하므로 로컬은 backend 부팅만으로 컬럼이 생긴다. prod 는 init SQL 의 ALTER 로 base table 위에 컬럼을 얹는 구조이므로, 운영 반영을 위해 ALTER 문을 추가한다.

- [ ] **Step 1: 초기화 SQL 위치 확인**

Run: `cd backend && grep -rl "policy_attachment" src/main/resources/ 2>/dev/null; ls src/main/resources/db 2>/dev/null`
Expected: `policy_attachment` 관련 ALTER 가 있는 SQL 파일 경로 확인. 없으면 init SQL 한 곳에 추가.

- [ ] **Step 2: ALTER 문 추가**

해당 SQL 파일(예: `src/main/resources/db/init.sql` 의 `policy_attachment` ALTER 블록)에 멱등 추가:

```sql
ALTER TABLE policy_attachment
    ADD COLUMN IF NOT EXISTS embedding_included BOOLEAN,
    ADD COLUMN IF NOT EXISTS embedding_decision_reason VARCHAR(500);
```

- [ ] **Step 3: 로컬 부팅으로 스키마 검증**

Run: `cd backend && ./gradlew bootRun` (별도 터미널에서 기동 후 종료) 또는 기존 통합 테스트로 스키마 생성 확인:
`./gradlew test --tests "*PolicyAttachment*"`
Expected: 스키마 생성 오류 없음

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db
git commit -m "chore: policy_attachment 임베딩 판정 컬럼 마이그레이션 SQL 추가"
```

---

## Task 4: 게이트 포트와 입출력 DTO

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/dto/command/AttachmentEmbeddingJudgeCommand.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/dto/result/AttachmentEmbeddingResult.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentEmbeddingJudge.java`

- [ ] **Step 1: 입력 Command record 작성**

`AttachmentEmbeddingJudgeCommand.java`:

```java
package com.youthfit.ingestion.application.dto.command;

import java.util.List;

/**
 * 첨부 임베딩 가치 판정 입력.
 * attachments 는 게이트 판정 대상(아직 미판정인 첨부)만 담는다.
 */
public record AttachmentEmbeddingJudgeCommand(
        String policyTitle,
        String policySummary,
        List<AttachmentItem> attachments
) {
    public record AttachmentItem(
            Long attachmentId,
            String name,
            String contentPreview
    ) {}
}
```

- [ ] **Step 2: 출력 Result record 작성**

`AttachmentEmbeddingResult.java`:

```java
package com.youthfit.ingestion.application.dto.result;

import java.util.List;
import java.util.Optional;

/**
 * 첨부 임베딩 가치 판정 결과. decisions 는 첨부별 포함 여부.
 */
public record AttachmentEmbeddingResult(
        List<AttachmentDecision> decisions
) {
    public record AttachmentDecision(
            Long attachmentId,
            boolean embed,
            String reason
    ) {}

    public Optional<AttachmentDecision> findByAttachmentId(Long attachmentId) {
        return decisions.stream()
                .filter(d -> attachmentId.equals(d.attachmentId()))
                .findFirst();
    }
}
```

- [ ] **Step 3: 포트 인터페이스 작성**

`AttachmentEmbeddingJudge.java`:

```java
package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;

/**
 * 정책 첨부가 RAG 임베딩에 가치 있는지 LLM 으로 판정하는 포트.
 * 구현은 infrastructure 의 OpenAI 어댑터.
 */
public interface AttachmentEmbeddingJudge {

    /**
     * 첨부별 임베딩 포함 여부를 판정한다.
     * @throws RuntimeException LLM 호출/파싱 실패 시. 호출자가 fail-open 으로 폴백한다.
     */
    AttachmentEmbeddingResult judge(AttachmentEmbeddingJudgeCommand command);
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/dto/command/AttachmentEmbeddingJudgeCommand.java \
        backend/src/main/java/com/youthfit/ingestion/application/dto/result/AttachmentEmbeddingResult.java \
        backend/src/main/java/com/youthfit/ingestion/application/port/AttachmentEmbeddingJudge.java
git commit -m "feat: 첨부 임베딩 게이트 포트와 입출력 DTO 추가"
```

---

## Task 5: OpenAI 게이트 어댑터

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentGateProperties.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentEmbeddingJudge.java`
- Modify: `backend/src/main/resources/application.yml`

> `OpenAiPolicyPeriodExtractor`(`.../ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java`) 패턴을 그대로 복제한다: `@Qualifier("openAiRestClientBuilder")`, `response_format: json_object`, `publishCostEvent`, 실패 시 예외/폴백.

- [ ] **Step 1: Properties 작성**

`OpenAiAttachmentGateProperties.java` (`OpenAiPolicyPeriodProperties` 와 동일 형태):

```java
package com.youthfit.ingestion.infrastructure.external;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "openai.ingestion.attachment-gate")
public class OpenAiAttachmentGateProperties {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final int maxPreviewChars;
}
```

> `@ConfigurationProperties` 빈 등록 방식은 기존 `OpenAiPolicyPeriodProperties` 와 동일해야 한다. Run: `grep -rn "OpenAiPolicyPeriodProperties" backend/src/main/java --include=*.java | grep -iE "EnableConfigurationProperties|@Component"` 로 등록 위치를 찾아 `OpenAiAttachmentGateProperties` 도 같은 곳(`@EnableConfigurationProperties` 목록)에 추가한다.

- [ ] **Step 2: 어댑터 작성**

`OpenAiAttachmentEmbeddingJudge.java`:

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult.AttachmentDecision;
import com.youthfit.ingestion.application.port.AttachmentEmbeddingJudge;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiAttachmentEmbeddingJudge implements AttachmentEmbeddingJudge {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAttachmentEmbeddingJudge.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 첨부파일이 RAG 검색·Q&A 에 가치 있는지 판정하는 분류기입니다.
            각 첨부에 대해 임베딩 포함 여부를 판정하고, 반드시 아래 JSON 스키마로만 응답하세요.
            {"decisions": [{"attachmentId": <number>, "embed": <true|false>, "reason": "<한 줄 사유>"}]}

            판정 기준:
            - 포함(embed=true): 지원 대상·금액·일정·신청 절차·자격 요건 등 실질 정책 내용을 담은 첨부.
            - 제외(embed=false): 단순 서식, 신청서 양식, 동의서, 개인정보 수집·이용 동의, 반복되는 안내문·boilerplate.
            - 애매하면 포함(embed=true) 으로 판정하세요. (보수적)
            - 입력에 주어진 모든 attachmentId 에 대해 정확히 하나씩 판정을 출력하세요.
            - JSON 외의 텍스트를 출력하지 마세요.
            """;

    private final OpenAiAttachmentGateProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient;

    public OpenAiAttachmentEmbeddingJudge(
            OpenAiAttachmentGateProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("openAiRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public AttachmentEmbeddingResult judge(AttachmentEmbeddingJudgeCommand command) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("attachment-gate apiKey 미설정");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserMessage(command))));

        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            throw new IllegalStateException("attachment-gate 응답이 비어 있음");
        }
        publishCostEvent(response);
        String content = response.get("choices").get(0).get("message").get("content").asText();
        return parseContent(content);
    }

    String buildUserMessage(AttachmentEmbeddingJudgeCommand command) {
        int limit = properties.getMaxPreviewChars() > 0 ? properties.getMaxPreviewChars() : Integer.MAX_VALUE;
        StringBuilder sb = new StringBuilder();
        sb.append("정책 제목: ").append(command.policyTitle() == null ? "" : command.policyTitle()).append('\n');
        if (command.policySummary() != null && !command.policySummary().isBlank()) {
            sb.append("정책 요약: ").append(command.policySummary()).append('\n');
        }
        sb.append("\n아래 첨부들을 판정하세요:\n");
        for (var item : command.attachments()) {
            String preview = item.contentPreview() == null ? "" : item.contentPreview();
            if (preview.length() > limit) {
                preview = preview.substring(0, limit);
            }
            sb.append("\n--- attachmentId=").append(item.attachmentId())
                    .append(" name=\"").append(item.name()).append("\" ---\n")
                    .append(preview).append('\n');
        }
        return sb.toString();
    }

    private AttachmentEmbeddingResult parseContent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode decisions = node.get("decisions");
            List<AttachmentDecision> out = new ArrayList<>();
            if (decisions != null && decisions.isArray()) {
                for (JsonNode d : decisions) {
                    if (!d.has("attachmentId")) continue;
                    out.add(new AttachmentDecision(
                            d.get("attachmentId").asLong(),
                            d.path("embed").asBoolean(true),
                            d.path("reason").asText("")));
                }
            }
            return new AttachmentEmbeddingResult(out);
        } catch (Exception e) {
            throw new IllegalStateException("attachment-gate JSON 파싱 실패: " + json, e);
        }
    }

    private void publishCostEvent(JsonNode response) {
        try {
            JsonNode usage = response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.ATTACHMENT_GATE, properties.getModel(), prompt, completion, Instant.now()));
        } catch (Exception e) {
            log.warn("attachment-gate LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }
    }
}
```

- [ ] **Step 3: application.yml 설정 추가**

기존 `openai.ingestion.period.*` 블록 근처에 추가:

```yaml
openai:
  ingestion:
    attachment-gate:
      api-key: ${OPENAI_API_KEY:}
      model: gpt-4o-mini
      max-tokens: 800
      max-preview-chars: 1500
```

> `openai.ingestion.period` 의 api-key 표현식과 동일한 env 변수를 사용한다. Run: `grep -n "openai:" -A 30 backend/src/main/resources/application.yml` 로 정확한 들여쓰기/키 이름을 확인하고 맞춘다.

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentGateProperties.java \
        backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentEmbeddingJudge.java \
        backend/src/main/resources/application.yml
git commit -m "feat: OpenAI 첨부 임베딩 게이트 어댑터 추가"
```

---

## Task 6: AttachmentReindexService 에 게이트 통합

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceGateTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`AttachmentReindexServiceGateTest.java` 생성. (기존 `AttachmentReindexService` 테스트가 있으면 그 mock 셋업을 참고)

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.common.config.CostGuardProperties;
import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult.AttachmentDecision;
import com.youthfit.ingestion.application.port.AttachmentEmbeddingJudge;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttachmentReindexService 임베딩 게이트")
class AttachmentReindexServiceGateTest {

    @Mock PolicyRepository policyRepository;
    @Mock PolicyAttachmentRepository attachmentRepository;
    @Mock RagIndexingService ragIndexingService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AttachmentEmbeddingJudge embeddingJudge;

    CostGuard costGuard = new CostGuard(new CostGuardProperties(""));
    AttachmentReindexService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentReindexService(
                policyRepository, attachmentRepository, ragIndexingService,
                eventPublisher, costGuard, embeddingJudge);
        ReflectionTestUtils.setField(service, "maxContentKb", 200);
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy()));
        given(ragIndexingService.indexPolicyDocument(any()))
                .willReturn(new IndexingResult(0, false));
    }

    private Policy policy() {
        Policy p = Policy.builder().title("청년월세").body("본문").build();
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    private PolicyAttachment extracted(long id, String name, String text) {
        PolicyAttachment a = PolicyAttachment.builder()
                .name(name).url("http://x/" + id).mediaType("application/x-hwp").build();
        a.markDownloading();
        a.markDownloaded("k" + id, "h" + id);
        a.markExtracting();
        a.markExtracted(text);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Nested
    @DisplayName("첨부 개수 분기")
    class CountBranch {

        @Test
        @DisplayName("첨부가 1개면 게이트를 호출하지 않는다")
        void singleAttachmentSkipsGate() {
            given(attachmentRepository.findExtractedByPolicyId(1L))
                    .willReturn(List.of(extracted(10L, "안내.hwp", "내용")));

            service.reindex(1L);

            verify(embeddingJudge, never()).judge(any());
        }

        @Test
        @DisplayName("첨부가 2개 이상이고 미판정이면 게이트를 호출한다")
        void multipleUndecidedCallsGate() {
            given(attachmentRepository.findExtractedByPolicyId(1L))
                    .willReturn(List.of(
                            extracted(10L, "사업안내.hwp", "지원대상 금액 일정"),
                            extracted(11L, "동의서.hwp", "개인정보 수집 동의")));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"),
                    new AttachmentDecision(11L, false, "단순 동의서"))));

            service.reindex(1L);

            verify(embeddingJudge).judge(any());
        }
    }

    @Nested
    @DisplayName("선별·머지")
    class SelectAndMerge {

        @Test
        @DisplayName("제외 판정된 첨부는 머지 content 에서 빠진다")
        void excludedAttachmentNotMerged() {
            given(attachmentRepository.findExtractedByPolicyId(1L))
                    .willReturn(List.of(
                            extracted(10L, "사업안내.hwp", "지원대상-금액-일정-본문"),
                            extracted(11L, "동의서.hwp", "개인정보수집동의-양식-본문")));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"),
                    new AttachmentDecision(11L, false, "단순 동의서"))));

            service.reindex(1L);

            ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                    ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
            verify(ragIndexingService).indexPolicyDocument(captor.capture());
            String content = captor.getValue().content();
            assertThat(content).contains("attachment-id=10");
            assertThat(content).doesNotContain("attachment-id=11");
        }

        @Test
        @DisplayName("판정 결과가 첨부에 영속화된다")
        void persistsDecision() {
            PolicyAttachment a10 = extracted(10L, "사업안내.hwp", "지원대상");
            PolicyAttachment a11 = extracted(11L, "동의서.hwp", "동의");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));
            given(embeddingJudge.judge(any())).willReturn(new AttachmentEmbeddingResult(List.of(
                    new AttachmentDecision(10L, true, "실질 내용"),
                    new AttachmentDecision(11L, false, "단순 동의서"))));

            service.reindex(1L);

            assertThat(a10.getEmbeddingIncluded()).isTrue();
            assertThat(a11.getEmbeddingIncluded()).isFalse();
            verify(attachmentRepository).save(a10);
            verify(attachmentRepository).save(a11);
        }
    }

    @Nested
    @DisplayName("캐시")
    class Cache {

        @Test
        @DisplayName("이미 판정된 첨부만 있으면 게이트를 재호출하지 않는다")
        void allDecidedSkipsGate() {
            PolicyAttachment a10 = extracted(10L, "a.hwp", "내용1");
            PolicyAttachment a11 = extracted(11L, "b.hwp", "내용2");
            a10.decideEmbedding(true, "이전 판정");
            a11.decideEmbedding(false, "이전 판정");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));

            service.reindex(1L);

            verify(embeddingJudge, never()).judge(any());
        }
    }

    @Nested
    @DisplayName("fail-open")
    class FailOpen {

        @Test
        @DisplayName("게이트 예외 시 미판정 첨부를 모두 포함으로 저장한다")
        void gateFailureIncludesAll() {
            PolicyAttachment a10 = extracted(10L, "a.hwp", "내용1");
            PolicyAttachment a11 = extracted(11L, "b.hwp", "내용2");
            given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of(a10, a11));
            given(embeddingJudge.judge(any())).willThrow(new RuntimeException("timeout"));

            service.reindex(1L);

            assertThat(a10.getEmbeddingIncluded()).isTrue();
            assertThat(a11.getEmbeddingIncluded()).isTrue();
            ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                    ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
            verify(ragIndexingService).indexPolicyDocument(captor.capture());
            assertThat(captor.getValue().content())
                    .contains("attachment-id=10").contains("attachment-id=11");
        }
    }
}
```

> **주의:** `Policy.builder().title(...).body(...)`, `IndexingResult(chunkCount, updated)`, `IndexPolicyDocumentCommand.content()` 의 실제 시그니처를 Run: `grep -n "record IndexingResult\|record IndexPolicyDocumentCommand" backend/src/main/java -r` 와 `Policy` 빌더로 확인하고, 다르면 테스트의 팩토리 메서드를 맞춘다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.AttachmentReindexServiceGateTest"`
Expected: 컴파일 실패 — 생성자에 `AttachmentEmbeddingJudge` 파라미터 없음, `selectForEmbedding` 미구현

- [ ] **Step 3: 게이트 통합 구현**

`AttachmentReindexService.java` 를 수정한다.

import 추가:

```java
import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;
import com.youthfit.ingestion.application.port.AttachmentEmbeddingJudge;
import java.util.ArrayList;
```

필드 추가 (`private final CostGuard costGuard;` 아래, L37):

```java
    private final CostGuard costGuard;
    private final AttachmentEmbeddingJudge embeddingJudge;

    private static final int MIN_ATTACHMENTS_FOR_GATE = 2;
    private static final int PREVIEW_CHARS = 1500;
```

> `@RequiredArgsConstructor` 가 final 필드를 생성자에 자동 포함하므로 테스트의 6-인자 생성자와 일치한다.

`reindex` 메서드의 머지 라인(L56-57)을 교체:

```java
        List<PolicyAttachment> attachments = attachmentRepository.findExtractedByPolicyId(resolvedId);
        List<PolicyAttachment> selected = selectForEmbedding(policy, attachments);
        String merged = mergeContent(policy, selected);
```

`mergeContent` 메서드 위에 게이트 로직 추가:

```java
    /**
     * 첨부 ≥2개일 때 LLM 게이트로 임베딩 가치를 판정하고,
     * 포함 판정(또는 미판정·1개 이하)인 첨부만 반환한다.
     */
    List<PolicyAttachment> selectForEmbedding(Policy policy, List<PolicyAttachment> attachments) {
        if (attachments.size() < MIN_ATTACHMENTS_FOR_GATE) {
            return attachments;
        }
        List<PolicyAttachment> undecided = attachments.stream()
                .filter(a -> a.getEmbeddingIncluded() == null)
                .toList();
        if (!undecided.isEmpty()) {
            judgeAndPersist(policy, undecided);
        }
        // embeddingIncluded == false 인 것만 제외. null(판정 안 됨)·true 는 포함.
        return attachments.stream()
                .filter(a -> !Boolean.FALSE.equals(a.getEmbeddingIncluded()))
                .toList();
    }

    private void judgeAndPersist(Policy policy, List<PolicyAttachment> undecided) {
        try {
            AttachmentEmbeddingResult result = embeddingJudge.judge(toCommand(policy, undecided));
            for (PolicyAttachment a : undecided) {
                AttachmentEmbeddingResult.AttachmentDecision d =
                        result.findByAttachmentId(a.getId()).orElse(null);
                if (d == null) {
                    a.decideEmbedding(true, "gate-no-decision"); // 누락 → 보수적 포함
                } else {
                    a.decideEmbedding(d.embed(), d.reason());
                }
                attachmentRepository.save(a);
            }
        } catch (Exception e) {
            log.warn("attachment embedding gate 실패, fail-open 으로 전체 포함: policyId={} err={}",
                    policy.getId(), e.toString());
            for (PolicyAttachment a : undecided) {
                a.decideEmbedding(true, "gate-failed: " + e.getClass().getSimpleName());
                attachmentRepository.save(a);
            }
        }
    }

    private AttachmentEmbeddingJudgeCommand toCommand(Policy policy, List<PolicyAttachment> undecided) {
        List<AttachmentEmbeddingJudgeCommand.AttachmentItem> items = new ArrayList<>();
        for (PolicyAttachment a : undecided) {
            String text = a.getExtractedText() == null ? "" : a.getExtractedText();
            String preview = text.length() > PREVIEW_CHARS ? text.substring(0, PREVIEW_CHARS) : text;
            items.add(new AttachmentEmbeddingJudgeCommand.AttachmentItem(a.getId(), a.getName(), preview));
        }
        String summary = policy.getBody() == null ? "" :
                (policy.getBody().length() > 300 ? policy.getBody().substring(0, 300) : policy.getBody());
        return new AttachmentEmbeddingJudgeCommand(policy.getTitle(), summary, items);
    }
```

> **확인:** `Policy` 에 `getTitle()`, `getBody()` 가 있는지 Run: `grep -n "getTitle\|getBody" backend/src/main/java/com/youthfit/policy/domain/model/Policy.java`. `@Getter` 가 붙어 있으면 자동 생성됨. 없으면 `mergeContent` 가 이미 `policy.getBody()` 를 쓰므로 `getBody()` 는 확실히 존재한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.AttachmentReindexServiceGateTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 기존 AttachmentReindexService 테스트 회귀 확인**

Run: `cd backend && ./gradlew test --tests "*AttachmentReindex*"`
Expected: 기존 테스트도 PASS (생성자 인자 추가로 깨졌다면 기존 테스트의 생성자 호출에 `embeddingJudge` mock 추가)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceGateTest.java
git commit -m "feat: AttachmentReindexService 에 첨부 임베딩 게이트 통합"
```

---

## Task 7: 어댑터 단위 테스트 (JSON 파싱·프리뷰 컷)

**Files:**
- Test: `backend/src/test/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentEmbeddingJudgeTest.java`

> 어댑터는 RestClient HTTP 호출을 포함하므로, 순수 단위로 검증 가능한 `buildUserMessage`(프리뷰 컷)와 `parseContent`(JSON→Result) 로직만 테스트한다. `parseContent` 는 private 이므로 `ReflectionTestUtils.invokeMethod` 로 호출하거나, 테스트 가능성을 위해 package-private 으로 둔다.

- [ ] **Step 1: 실패하는 테스트 작성**

`OpenAiAttachmentEmbeddingJudgeTest.java`:

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand.AttachmentItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("OpenAiAttachmentEmbeddingJudge buildUserMessage")
class OpenAiAttachmentEmbeddingJudgeTest {

    private OpenAiAttachmentEmbeddingJudge judge(int maxPreviewChars) {
        OpenAiAttachmentGateProperties props =
                new OpenAiAttachmentGateProperties("key", "gpt-4o-mini", 800, maxPreviewChars);
        return new OpenAiAttachmentEmbeddingJudge(
                props, new ObjectMapper(), mock(ApplicationEventPublisher.class),
                RestClient.builder());
    }

    @Test
    @DisplayName("프리뷰가 maxPreviewChars 로 잘린다")
    void truncatesPreview() {
        var cmd = new AttachmentEmbeddingJudgeCommand("제목", "요약",
                List.of(new AttachmentItem(10L, "a.hwp", "가".repeat(5000))));
        String msg = judge(100).buildUserMessage(cmd);
        // attachmentId 헤더 뒤의 본문 프리뷰가 100자 이내
        assertThat(msg).contains("attachmentId=10");
        assertThat(msg.length()).isLessThan(400); // 헤더 + 100자 수준
    }

    @Test
    @DisplayName("모든 첨부의 attachmentId 가 메시지에 포함된다")
    void includesAllIds() {
        var cmd = new AttachmentEmbeddingJudgeCommand("제목", null,
                List.of(new AttachmentItem(10L, "a.hwp", "x"),
                        new AttachmentItem(11L, "b.hwp", "y")));
        String msg = judge(1500).buildUserMessage(cmd);
        assertThat(msg).contains("attachmentId=10").contains("attachmentId=11");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.infrastructure.external.OpenAiAttachmentEmbeddingJudgeTest"`
Expected: FAIL 또는 컴파일 에러 (`buildUserMessage` 가 package-private 인지 확인). Task 5 에서 이미 package-private(`String buildUserMessage`)으로 작성했으므로 통과해야 한다.

- [ ] **Step 3: 통과 확인 (구현은 Task 5 에 이미 있음)**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.infrastructure.external.OpenAiAttachmentEmbeddingJudgeTest"`
Expected: PASS (2 tests)

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/youthfit/ingestion/infrastructure/external/OpenAiAttachmentEmbeddingJudgeTest.java
git commit -m "test: 첨부 임베딩 게이트 어댑터 메시지 빌드 테스트"
```

---

## Task 8: 전체 빌드·회귀 검증

- [ ] **Step 1: 전체 테스트 실행**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL, 전체 테스트 통과

- [ ] **Step 2: 게이트 비활성(apiKey 빈 값) 시 안전성 확인**

`OPENAI_API_KEY` 미설정 시 `judge()` 가 `IllegalStateException` 을 던지고 `judgeAndPersist` 의 catch 가 fail-open(전체 포함)으로 폴백하는지 코드 경로를 재확인한다. (Task 6 `FailOpen` 테스트가 이를 커버)

- [ ] **Step 3: 문서 갱신**

`backend/docs/ENTITIES.md` §2.2 PolicyAttachment 에 `embedding_included`, `embedding_decision_reason` 컬럼과 의미(임베딩 가치 판정 결과, 재추출 시 리셋)를 1-2줄 추가한다.
`backend/docs/CONTENT_GENERATION_FLOW.md` 의 RAG 인덱싱 흐름에 "첨부 ≥2개 시 LLM 게이트 선별" 단계를 1줄 추가한다.

- [ ] **Step 4: Commit**

```bash
git add backend/docs/ENTITIES.md backend/docs/CONTENT_GENERATION_FLOW.md
git commit -m "docs: 첨부 임베딩 게이트 컬럼·흐름 문서화"
```

---

## 완료 기준 (스펙 대조)

- [x] 첨부 0~1개면 LLM 미호출, 그대로 포함 (Task 6 `singleAttachmentSkipsGate`)
- [x] 첨부 ≥2개일 때만 LLM 판단 (Task 6 `multipleUndecidedCallsGate`)
- [x] 추출 텍스트 내용 기반 판단, 프리뷰 N자 컷 (Task 5 `buildUserMessage`, Task 7)
- [x] 첨부별 `{embed, reason}` 출력·저장 (Task 4 Result, Task 6 `persistsDecision`)
- [x] 캐시: 이미 판정된 첨부 재호출 안 함 (Task 6 `allDecidedSkipsGate`)
- [x] 변경감지: 재추출 시 판정 리셋 (Task 2 `resetsOnReextraction`)
- [x] 경량 모델(gpt-4o-mini) (Task 5 application.yml)
- [x] 비용 추적: `LlmCallRecorded(ATTACHMENT_GATE)` 발행 (Task 1, Task 5)
- [x] 결정 영속화: `policy_attachment.embedding_included/reason` (Task 2, 3)
- [x] LLM 실패 시 fail-open (Task 6 `gateFailureIncludesAll`)
- [x] 저가치 첨부가 머지 content 에서 제외 (Task 6 `excludedAttachmentNotMerged`)
- [x] 비로그인 핫패스 무관: 스케줄러/이벤트 기반 인덱싱 단계 (설계상)

## 알려진 한계 / 후속

- n8n 파일명 화이트리스트(`attachments 승격`)는 1차 필터로 유지(본 게이트는 2차). 변경 없음.
- 어드민 검토 UI(판정 결과 노출)는 본 플랜 범위 밖. `embedding_decision_reason` 컬럼이 이미 있으므로 후속으로 admin 조회만 추가하면 됨.
