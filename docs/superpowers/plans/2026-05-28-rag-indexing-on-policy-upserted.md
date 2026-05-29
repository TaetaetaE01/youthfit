# PolicyUpsertedEvent 에 RAG 인덱싱 리스너 추가 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책 ingest 직후 발행되는 `PolicyUpsertedEvent` 에 RAG 인덱싱 리스너를 추가해, 첨부가 0건이거나 첨부 추출이 종결되지 못한 정책도 본문 + enrichment 만으로 1차 RAG 인덱싱이 즉시 트리거되도록 한다.

**Architecture:** `rag/application/listener/RagIndexingEventListener` 를 신설. `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` + `@Async("llmExecutor")` 패턴 (`GuideGenerationEventListener` 와 동일) 으로 이벤트를 받아 `PolicyRepository` 로 Policy 를 로드하고 `RagIndexingService.indexPolicyDocument(new IndexPolicyDocumentCommand(policyId, body, enrichment))` 호출. 첨부 추출 완료 시점의 `AttachmentReindexService.reindex()` 는 그대로 두면 source_hash 가 달라져 자동으로 본문 → 본문+첨부 로 업그레이드되는 2단계 fan-out 이 자연스럽게 성립.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring Events (`@TransactionalEventListener`), Spring Async (`@Async`), JUnit 5 + Mockito, AssertJ

---

## 현재 결함과 수정 효과

### 결함
- `PolicyUpsertedEvent` 의 기존 리스너는 `GuideGenerationEventListener`, `EligibilityRuleGenerationEventListener` 둘뿐. RAG 인덱싱 리스너 없음.
- `RagIndexingService.indexPolicyDocument()` 의 유일한 caller 는 `AttachmentReindexService.reindex()`.
- `AttachmentReindexService.reindex()` 트리거는 `AttachmentExtractionScheduler.runCycle()` 의 `reindexCandidates` 처리 — `extractOne()` 안에서만 정책이 후보로 추가됨 (`AttachmentExtractionScheduler.java:102-104`).
- ⇒ **첨부 0건 정책 / 첨부 다운로드 영구 실패 정책 / 첨부가 DOWNLOADED 까지 못 간 정책** 은 영영 RAG 인덱싱이 트리거되지 않음. Q&A 호출 시 `chunks.isEmpty()` 분기로 빠져 `NO_INDEXED_MESSAGE` ("이 정책은 아직 본문 인덱싱이 되어 있지 않아…") 반환.

### 수정 후
- ingest 직후 `PolicyUpsertedEvent` 가 발행되면 RAG 1차 인덱싱이 본문 + enrichment 만으로 즉시 일어남.
- 첨부 추출이 모두 종결된 정책은 기존 `AttachmentReindexService.reindex()` 가 본문 + 첨부 텍스트를 merge 해 2차 재인덱싱 (source_hash 변경으로 자동 dedup 통과).
- Q&A 는 본문 메타데이터 fallback (passing.isEmpty 분기, `QnaService.java:211-217`) 까지 도달 가능해짐.

### 비목표 (Out of scope)
- 기존 인덱싱이 안 된 정책의 일괄 백필: 운영 절차나 별도 admin endpoint 로 처리. 이 plan 은 ongoing 트리거만 수정.
- RAG 인덱싱 자체의 로직 변경: `RagIndexingService` / `DocumentChunker` 무수정.

---

## File Structure

**Create:**
- `backend/src/main/java/com/youthfit/rag/application/listener/RagIndexingEventListener.java`
- `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerTest.java`

**Modify:**
- `backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java` (Javadoc 의 구독자 목록에 `RagIndexingEventListener` 추가)
- `backend/docs/CONTENT_GENERATION_FLOW.md` (Section 3.1 "트리거 — 첨부 트랙" 을 1차/2차 fan-out 으로 갱신)
- `backend/docs/INGESTION_PIPELINE.md` (Section 2-2 의 PolicyUpsertedEvent 후속 트리거 목록에 RAG 인덱싱 명시)

**Do NOT modify:**
- `RagIndexingService.java` — source_hash dedup 로직이 이미 충분 (line 41-48: 동일 hash 면 skip).
- `AttachmentReindexService.java` — 2차 재인덱싱 트리거로 그대로 유지.
- `AttachmentExtractionScheduler.java` — 첨부 트랙 로직 변경 없음.

---

## 사전 확인 사항 (구현 시작 전 반드시 읽기)

1. **`GuideGenerationEventListener.java`** (`backend/src/main/java/com/youthfit/guide/application/listener/GuideGenerationEventListener.java`) — 이 plan 의 새 리스너는 이 파일의 `onPolicyUpserted` 메서드 패턴을 그대로 복사한다 (`@Async("llmExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` + try/catch 로 예외 swallow + warn 로그).
2. **`GuideGenerationEventListenerTest.java`** (`backend/src/test/java/com/youthfit/guide/application/listener/GuideGenerationEventListenerTest.java`) — 테스트도 이 파일 패턴 그대로 (`@ExtendWith(MockitoExtension.class)`, `@InjectMocks`, `ArgumentCaptor`, `assertThatCode(...).doesNotThrowAnyException()`).
3. **`IndexPolicyDocumentCommand.java`** — 3-arg 생성자: `(Long policyId, String content, PolicyEnrichment enrichment)`.
4. **`Policy` 엔티티** — `getBody()` (String), `getEnrichment()` (PolicyEnrichment) lombok `@Getter`. body 가 null/blank 일 수 있음 (이 경우 RagIndexingService 가 DocumentChunker.chunk 빈 입력 처리로 List.of() 반환).
5. **`llmExecutor` 빈** — `guide`, `eligibility` 모듈이 이미 사용 중. 이미 등록된 공유 `TaskExecutor` 빈 (`grep -rn 'llmExecutor' backend/src/main/java` 로 확인 가능).

---

## Task 1: 새 리스너의 정상 경로 테스트 작성 (RED)

**Files:**
- Create: `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerTest.java`

- [ ] **Step 1: 테스트 파일 작성 — PolicyUpsertedEvent 수신 시 indexPolicyDocument 호출**

```java
package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("RagIndexingEventListener")
@ExtendWith(MockitoExtension.class)
class RagIndexingEventListenerTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RagIndexingService ragIndexingService;

    @InjectMocks
    private RagIndexingEventListener listener;

    @Test
    @DisplayName("PolicyUpsertedEvent 수신 시 policyId/body/enrichment 로 indexPolicyDocument 를 호출한다")
    void onPolicyUpserted_callsIndexing() {
        // given
        Long policyId = 42L;
        PolicyEnrichment enrichment = mock(PolicyEnrichment.class);
        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(policyId);
        given(policy.getBody()).willReturn("정책 본문입니다.");
        given(policy.getEnrichment()).willReturn(enrichment);
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));
        given(ragIndexingService.indexPolicyDocument(any()))
                .willReturn(new IndexingResult(policyId, 3, true));

        // when
        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "청년월세 지원"));

        // then
        ArgumentCaptor<IndexPolicyDocumentCommand> captor =
                ArgumentCaptor.forClass(IndexPolicyDocumentCommand.class);
        then(ragIndexingService).should().indexPolicyDocument(captor.capture());
        assertThat(captor.getValue().policyId()).isEqualTo(policyId);
        assertThat(captor.getValue().content()).isEqualTo("정책 본문입니다.");
        assertThat(captor.getValue().enrichment()).isSameAs(enrichment);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingEventListenerTest.onPolicyUpserted_callsIndexing" 2>&1 | tail -20
```

Expected: `RagIndexingEventListener` 심볼 resolve 실패로 컴파일 에러. (cannot find symbol class RagIndexingEventListener)

---

## Task 2: 새 리스너 최소 구현 (GREEN)

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/application/listener/RagIndexingEventListener.java`

- [ ] **Step 1: 리스너 클래스 작성**

```java
package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.service.RagIndexingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RagIndexingEventListener {

    private static final Logger log = LoggerFactory.getLogger(RagIndexingEventListener.class);

    private final PolicyRepository policyRepository;
    private final RagIndexingService ragIndexingService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyUpserted(PolicyUpsertedEvent event) {
        try {
            Optional<Policy> policyOpt = policyRepository.findById(event.policyId());
            if (policyOpt.isEmpty()) {
                log.warn("정책 미존재 — RAG 1차 인덱싱 스킵: policyId={}", event.policyId());
                return;
            }
            Policy policy = policyOpt.get();
            String body = policy.getBody();
            if (body == null || body.isBlank()) {
                log.info("정책 본문 비어 있음 — RAG 1차 인덱싱 스킵: policyId={}", event.policyId());
                return;
            }
            ragIndexingService.indexPolicyDocument(
                    new IndexPolicyDocumentCommand(event.policyId(), body, policy.getEnrichment()));
        } catch (Exception e) {
            log.warn("RAG 1차 인덱싱 실패 (event=PolicyUpsertedEvent): policyId={}",
                    event.policyId(), e);
        }
    }
}
```

- [ ] **Step 2: 테스트 재실행 — 통과 확인**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingEventListenerTest.onPolicyUpserted_callsIndexing" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 1 passed.

---

## Task 3: body 비어있을 때 스킵 테스트 추가

**Files:**
- Modify: `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerTest.java`

- [ ] **Step 1: body=null 테스트 추가**

기존 테스트 클래스 안 (마지막 `}` 직전) 에 추가:

```java
    @Test
    @DisplayName("정책의 body 가 null 이면 indexPolicyDocument 를 호출하지 않는다")
    void onPolicyUpserted_skipsWhenBodyIsNull() {
        // given
        Long policyId = 11L;
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn(null);
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));

        // when
        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t"));

        // then
        then(ragIndexingService).should(never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("정책의 body 가 공백만 있으면 indexPolicyDocument 를 호출하지 않는다")
    void onPolicyUpserted_skipsWhenBodyIsBlank() {
        // given
        Long policyId = 12L;
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn("   \n\t  ");
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));

        // when
        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t"));

        // then
        then(ragIndexingService).should(never()).indexPolicyDocument(any());
    }
```

- [ ] **Step 2: 테스트 실행**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingEventListenerTest" 2>&1 | tail -20
```

Expected: 3 tests passed.

---

## Task 4: 정책 미존재 케이스 + 예외 swallow 테스트 추가

**Files:**
- Modify: `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerTest.java`

- [ ] **Step 1: 두 가지 테스트 추가**

기존 테스트 클래스 안에 추가:

```java
    @Test
    @DisplayName("정책이 존재하지 않으면 indexPolicyDocument 를 호출하지 않는다")
    void onPolicyUpserted_skipsWhenPolicyNotFound() {
        // given
        Long policyId = 99L;
        given(policyRepository.findById(policyId)).willReturn(Optional.empty());

        // when
        listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t"));

        // then
        then(ragIndexingService).should(never()).indexPolicyDocument(any());
    }

    @Test
    @DisplayName("RAG 인덱싱에서 예외가 던져져도 리스너는 예외를 전파하지 않는다")
    void onPolicyUpserted_swallowsException() {
        // given
        Long policyId = 1L;
        Policy policy = mock(Policy.class);
        given(policy.getBody()).willReturn("본문");
        given(policy.getEnrichment()).willReturn(null);
        given(policyRepository.findById(policyId)).willReturn(Optional.of(policy));
        given(ragIndexingService.indexPolicyDocument(any()))
                .willThrow(new RuntimeException("OpenAI 임베딩 장애"));

        // when & then
        assertThatCode(() -> listener.onPolicyUpserted(new PolicyUpsertedEvent(policyId, "t")))
                .doesNotThrowAnyException();
    }
```

- [ ] **Step 2: 테스트 실행**

Run:
```bash
cd backend && ./gradlew test --tests "com.youthfit.rag.application.listener.RagIndexingEventListenerTest" 2>&1 | tail -20
```

Expected: 5 tests passed.

---

## Task 5: 통합 검증 — Spring 컨텍스트가 리스너를 정상 발견하는지 확인

**Files:**
- (변경 없음 — 기존 backend 전체 빌드/테스트 수트를 돌려 회귀 여부 확인)

- [ ] **Step 1: 전체 테스트 + 빌드**

Run:
```bash
cd backend && ./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. 기존 통합 테스트(`@SpringBootTest` 가 있는 것들)도 모두 통과. 만약 새 리스너의 `PolicyRepository` 의존성이 컨텍스트 와이어링에 문제를 일으키면 여기서 실패함 — 그 경우는 `policy` 모듈을 이미 의존하고 있는지 (`grep -rn 'com.youthfit.policy' backend/src/main/java/com/youthfit/rag/` 로 확인. `DocumentChunker` 가 `PolicyEnrichment` 를 import 하므로 이미 의존성 존재) 다시 확인하고, 실패 케이스의 스택트레이스에 맞춰 fix.

- [ ] **Step 2: build (compile + test) 명시적 실행**

Run:
```bash
cd backend && ./gradlew build 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

---

## Task 6: PolicyUpsertedEvent Javadoc 갱신

**Files:**
- Modify: `backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java`

- [ ] **Step 1: Javadoc 구독자 목록에 새 리스너 추가**

`PolicyUpsertedEvent.java` 안의 기존 6번째 줄 (`* 구독자: GuideGenerationEventListener, EligibilityRuleGenerationEventListener`) 을 다음과 같이 교체:

```java
 * 구독자: GuideGenerationEventListener, EligibilityRuleGenerationEventListener, RagIndexingEventListener
```

- [ ] **Step 2: 컴파일 확인**

Run:
```bash
cd backend && ./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

---

## Task 7: 문서 — INGESTION_PIPELINE.md 의 후속 트리거 목록 갱신

**Files:**
- Modify: `backend/docs/INGESTION_PIPELINE.md`

- [ ] **Step 1: Section 2-2 의 step 8 (PolicyUpsertedEvent 설명) 을 교체**

기존 line 131:

```markdown
8. **이벤트 발행** — `PolicyUpsertedEvent` 발행 → 가이드 생성·RAG 인덱싱 등 후속 트리거
```

다음과 같이 교체 (가이드 생성 / 적합도 룰 / RAG 1차 인덱싱 명시):

```markdown
8. **이벤트 발행** — `PolicyUpsertedEvent` 발행 → 가이드 생성 (`GuideGenerationEventListener`), 적합도 룰 추출 (`EligibilityRuleGenerationEventListener`), RAG 1차 인덱싱 (`RagIndexingEventListener`) 트리거. RAG 는 본문+enrichment 만으로 즉시 인덱싱하고, 첨부 추출이 종결되면 `AttachmentReindexService` 가 본문+첨부 merged content 로 2차 재인덱싱한다 (`source_hash` 변경으로 자동 갱신).
```

- [ ] **Step 2: 변경 확인**

Run:
```bash
grep -n "이벤트 발행" backend/docs/INGESTION_PIPELINE.md
```

Expected: 위 새 문장이 한 줄에 표시됨.

---

## Task 8: 문서 — CONTENT_GENERATION_FLOW.md 의 RAG 인덱싱 트리거 절 갱신

**Files:**
- Modify: `backend/docs/CONTENT_GENERATION_FLOW.md`

- [ ] **Step 1: Section 3 "RAG 인덱싱" 도입부 갱신**

기존 line 291:

```markdown
가이드와 Q&A 가 의존하는 임베딩 인덱스. 첨부 추출이 끝난 뒤에만 한 번에 만들어진다.
```

다음과 같이 교체 (1차/2차 fan-out 명시):

```markdown
가이드와 Q&A 가 의존하는 임베딩 인덱스. **2단계 fan-out** 으로 만들어진다.

- **1차 (PolicyUpsertedEvent)**: 정책 ingest 직후 `RagIndexingEventListener` 가 본문 + enrichment 만으로 즉시 인덱싱. 첨부가 0건이거나 첨부 추출이 아직 안 끝난 정책도 이 단계에서 Q&A 가능 상태가 된다.
- **2차 (첨부 추출 완료 후)**: 모든 첨부가 종결 상태가 되면 `AttachmentReindexService` 가 본문 + 첨부 텍스트를 merge 해 재인덱싱. `source_hash` 가 바뀌어 1차 청크가 모두 교체된다.
```

- [ ] **Step 2: Section 3.1 "트리거 — 첨부 트랙" 헤더의 보강 (2차 트랙임을 명시)**

기존 헤더 (line 293 부근):

```markdown
### 3-1. 트리거 — 첨부 트랙
```

다음과 같이 교체:

```markdown
### 3-1. 1차 트리거 — PolicyUpsertedEvent

`IngestionService.receivePolicy()` 가 정책을 정상 commit 한 직후 `PolicyUpsertedEvent` 가 발행되면 `RagIndexingEventListener.onPolicyUpserted()` 가 `@Async("llmExecutor")` 로 실행되어 본문 + enrichment 만으로 `RagIndexingService.indexPolicyDocument()` 호출. 본문이 비어있는 정책은 스킵.

### 3-2. 2차 트리거 — 첨부 트랙
```

- [ ] **Step 3: 그 아래 기존 다이어그램의 번호 매김 (3-2, 3-3, 3-4 …) 도 한 칸씩 밀어 갱신**

기존 `### 3-2.` → `### 3-3.`, `### 3-3.` → `### 3-4.` 등으로 한 단계씩 시프트. (현재 파일을 열어 정확한 헤더 번호를 확인하고 sed 가 아닌 정확 매치로 Edit 도구를 사용한다.)

Run (확인용):
```bash
grep -n "^### 3-" backend/docs/CONTENT_GENERATION_FLOW.md
```

Expected: 1차/2차 trigger + 이후 절 번호가 1씩 밀려 표시됨.

- [ ] **Step 4: 컴파일·테스트 영향 없음을 확인**

Run:
```bash
cd backend && ./gradlew compileJava 2>&1 | tail -3
```

Expected: BUILD SUCCESSFUL (문서만 변경했지만 안전 확인).

---

## Task 9: 커밋

**Files:** 위 모든 변경 파일

- [ ] **Step 1: 변경 파일 확인**

Run:
```bash
git status
git diff --stat
```

Expected:
- new file: `backend/src/main/java/com/youthfit/rag/application/listener/RagIndexingEventListener.java`
- new file: `backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerTest.java`
- modified: `backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java`
- modified: `backend/docs/INGESTION_PIPELINE.md`
- modified: `backend/docs/CONTENT_GENERATION_FLOW.md`

- [ ] **Step 2: 전체 테스트 한 번 더**

Run:
```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 명시 add + commit**

Run:
```bash
git add backend/src/main/java/com/youthfit/rag/application/listener/RagIndexingEventListener.java \
        backend/src/test/java/com/youthfit/rag/application/listener/RagIndexingEventListenerTest.java \
        backend/src/main/java/com/youthfit/common/event/PolicyUpsertedEvent.java \
        backend/docs/INGESTION_PIPELINE.md \
        backend/docs/CONTENT_GENERATION_FLOW.md

git commit -m "$(cat <<'EOF'
feat(rag): index policy on PolicyUpsertedEvent (1차 인덱싱)

기존 RAG 인덱싱은 AttachmentReindexService.reindex() 한 곳에서만 호출되어,
AttachmentExtractionScheduler 가 첨부 추출을 종결시킨 정책만 인덱싱됐다.
→ 첨부 0건 / 첨부 다운로드 영구실패 / DOWNLOADED 미도달 정책은
   policy_document 가 영영 비어 Q&A 가 NO_INDEXED_MESSAGE 로 빠짐.

PolicyUpsertedEvent 에 RAG 1차 인덱싱 리스너를 추가해 본문+enrichment 만으로
즉시 인덱싱하도록 한다. 첨부 추출 종결 후 AttachmentReindexService 는 그대로
2차 재인덱싱을 수행 (source_hash 변경으로 자동 갱신).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: 커밋 생성 성공. pre-commit hook 통과.

---

## 검증 체크리스트 (수동)

- [ ] 새 리스너가 `@Async("llmExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` 패턴을 모두 사용함 (가이드 리스너와 일치)
- [ ] 본문(null/blank) 스킵, 정책 미존재 스킵, 예외 swallow 가 모두 테스트로 커버됨
- [ ] 새 리스너의 의존성(`PolicyRepository`, `RagIndexingService`) 이 기존 의존 방향 (rag → policy 는 이미 `DocumentChunker` 에서 사용 중) 안에 들어감
- [ ] `RagIndexingService.indexPolicyDocument()` 는 무수정 — source_hash dedup 으로 동일 hash 재호출은 no-op
- [ ] `AttachmentReindexService` 는 무수정 — 2차 트랙 그대로 유지
- [ ] 문서 두 파일에 1차/2차 fan-out 반영됨

---

## Self-Review

- **Spec coverage**: 결함 → 9개 태스크로 모두 커버. 1~4 구현/테스트, 5 회귀, 6 javadoc, 7-8 문서, 9 커밋.
- **Placeholder scan**: 모든 step 에 실제 코드/명령/예상 출력 포함. "appropriate handling" 류 없음.
- **Type consistency**: 모든 클래스명 `RagIndexingEventListener`, 메서드명 `onPolicyUpserted`, 의존성 `PolicyRepository`/`RagIndexingService`, command 시그니처 `IndexPolicyDocumentCommand(Long, String, PolicyEnrichment)` 로 plan 전체 일관.
