# 임베딩 모델 실험 1단계 (3-large) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** eval 러너에 전 정책 재인덱싱 모드를 추가하고 임베딩 캐시 라벨을 실제 호출 모델로 바꿔, `OPENAI_EMBEDDING_MODEL` env 교체만으로 text-embedding-3-large 를 같은 평가셋으로 측정 가능하게 만든다.

**Architecture:** ingestion 의 `AttachmentReindexService` 에 이벤트 미발행 변형(`reindexWithoutEvents`)을 추출-오버로드로 추가(가이드·룰 LLM 리스너 각성 차단), eval 에 `EvalReindexService`(@Profile("eval"), 정책당 @Transactional delete→reindex)와 `--eval.mode=reindex`(dry-run 기본) 를 신설. 스펙: `docs/superpowers/specs/2026-07-03-embedding-model-experiment-design.md`.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JUnit 5 + Mockito.

## Global Constraints

- eval 신규 빈은 `@Profile("eval")` 필수. 의존 방향: eval → rag, qna, policy, **ingestion(신규 허용 — reindexWithoutEvents 소비)** 단방향.
- `AttachmentReindexService.reindex(Long)` 의 기존 동작(이벤트 발행 포함)은 절대 변경 금지 — 리팩토링은 동작 불변 추출만.
- 비용 방어: reindex 모드는 dry-run 기본, `--eval.confirm=true` 일 때만 실행 (generate 모드와 동일 패턴).
- 커밋 메시지: `feat(be): …` 형식. 작업 브랜치: `feat/be-eval-reindex-mode` (main 분기).
- 테스트: `cd backend && ./gradlew test --tests "<FQN>"`. 전체 빌드는 마지막 태스크에서 1회.
- 기존 시그니처 (그대로 사용):
  - `AttachmentReindexService.reindex(Long policyId)` — costGuard 체크 → policy 조회 → 첨부 선별(`selectForEmbedding`, LLM 게이트는 판정 캐시 재사용) → `mergeContent` → `ragIndexingService.indexPolicyDocument(cmd)` → `result.updated()` 면 `PolicyAttachmentReindexedEvent` 발행
  - `PolicyDocumentRepository.deleteByPolicyId(Long)`, `findByPolicyIdOrderByChunkIndex(Long)` (rag.domain.repository)
  - `PolicyRepository.findAllForStats() : List<Policy>`, `findAllById(Iterable<Long>)` (policy.domain.repository)
  - `OpenAiEmbeddingProperties.getModel() : String` (rag.infrastructure.external, @Getter)
  - `EvalRunner` 디스패치: `EvalRunner.java:84-92` switch (generate/run), `firstOption(args, "eval.confirm")` 패턴, 캐시 생성부 `EvalRunner.java:106-107` (`new QueryEmbeddingFileCache(Path.of(evalProperties.cacheDir()), dataset.embeddingModel())`)
- `EvalCaseGenerateServiceTest` 류에서 Mockito nested-stubbing(`given(...).willReturn(List.of(mockFactory(...)))` 인라인)은 `UnfinishedStubbingException` 유발 — 스텁 인자는 로컬 변수로 호이스팅.

---

### Task 1: `AttachmentReindexService.reindexWithoutEvents` — 이벤트 미발행 변형

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/AttachmentReindexService.java:51-76`
- Test: 기존 `backend/src/test/java/com/youthfit/ingestion/application/service/AttachmentReindexServiceTest.java` 가 있으면 확장, 없으면 신규 생성 (먼저 `ls` 로 확인)

**Interfaces:**
- Consumes: 기존 `reindex(Long)` 본문
- Produces: `public IndexingResult reindexWithoutEvents(Long policyId)` — 이벤트 발행 없이 동일 재인덱싱. `IndexingResult` 는 `rag.application.dto.result` 의 record `(Long policyId, int chunkCount, boolean updated)` (RagIndexingService 사용부에서 확인됨). policy 미존재·costGuard 차단 시 null 반환.

- [ ] **Step 1: 브랜치 생성**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit && git switch -c feat/be-eval-reindex-mode main
```

- [ ] **Step 2: 실패하는 테스트 작성**

기존 테스트 클래스가 있으면 그 셋업(mock 필드) 재사용. 없으면 Mockito 단위 테스트 신규:

```java
@Test
@DisplayName("reindexWithoutEvents 는 인덱싱 결과가 updated 여도 이벤트를 발행하지 않는다")
void reindexWithoutEvents_neverPublishesEvent() {
    Policy policy = mock(Policy.class);
    given(policy.getId()).willReturn(1L);
    given(policy.getBody()).willReturn("본문");
    given(costGuard.allows(1L)).willReturn(true);
    given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
    given(attachmentRepository.findExtractedByPolicyId(1L)).willReturn(List.of());
    IndexingResult updated = new IndexingResult(1L, 5, true);
    given(ragIndexingService.indexPolicyDocument(any())).willReturn(updated);

    IndexingResult result = service.reindexWithoutEvents(1L);

    assertThat(result.updated()).isTrue();
    verify(eventPublisher, never()).publishEvent(any());
}

@Test
@DisplayName("기존 reindex 는 updated 시 이벤트를 발행한다 (동작 불변 확인)")
void reindex_stillPublishesEvent() {
    // 위와 동일 셋업 후
    service.reindex(1L);

    verify(eventPublisher).publishEvent(any(PolicyAttachmentReindexedEvent.class));
}
```

주의: `maxContentKb` 는 `@Value` 필드라 테스트에서 `service.setMaxContentKb(200)` (@Setter 존재) 설정 필요.

- [ ] **Step 3: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.application.service.AttachmentReindexServiceTest"
```
Expected: 컴파일 에러 (`reindexWithoutEvents` 미존재) — FAIL

- [ ] **Step 4: 구현 — 동작 불변 추출 + 오버로드**

`reindex(Long)` 본문을 `doReindex(Long policyId, boolean publishEvents)` 로 추출:

```java
public void reindex(Long policyId) {
    doReindex(policyId, true);
}

/**
 * 이벤트 미발행 재인덱싱 — 임베딩 모델 실험(#167)용.
 * PolicyAttachmentReindexedEvent 를 발행하지 않아 가이드·룰 LLM 재생성 리스너를 깨우지 않는다.
 *
 * @return 인덱싱 결과. costGuard 차단·정책 미존재 시 null.
 */
public IndexingResult reindexWithoutEvents(Long policyId) {
    return doReindex(policyId, false);
}

private IndexingResult doReindex(Long policyId, boolean publishEvents) {
    if (!costGuard.allows(policyId)) {
        costGuard.logSkip("attachment-reindex", policyId);
        return null;
    }
    Optional<Policy> policyOpt = policyRepository.findById(policyId);
    if (policyOpt.isEmpty()) {
        log.warn("policy not found for reindex: {}", policyId);
        return null;
    }
    Policy policy = policyOpt.get();
    Long resolvedId = policy.getId();

    List<PolicyAttachment> attachments = attachmentRepository.findExtractedByPolicyId(resolvedId);
    List<PolicyAttachment> selected = selectForEmbedding(policy, attachments);
    String merged = mergeContent(policy, selected);

    IndexPolicyDocumentCommand cmd = new IndexPolicyDocumentCommand(resolvedId, merged, policy.getEnrichment());
    IndexingResult result = ragIndexingService.indexPolicyDocument(cmd);
    log.info("reindex policyId={} chunks={} updated={} publishEvents={}",
            resolvedId, result.chunkCount(), result.updated(), publishEvents);

    if (publishEvents && result.updated()) {
        eventPublisher.publishEvent(new PolicyAttachmentReindexedEvent(resolvedId));
        log.info("attachment reindex event published: policyId={}", resolvedId);
    }
    return result;
}
```

`import com.youthfit.rag.application.dto.result.IndexingResult;` 추가 (이미 있으면 생략).

- [ ] **Step 5: 테스트 통과 + ingestion 회귀 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.ingestion.*"
```
Expected: PASS (기존 reindex 동작 불변 포함)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion backend/src/test/java/com/youthfit/ingestion
git commit -m "feat(be): AttachmentReindexService 이벤트 미발행 변형 — 임베딩 실험용 (#167)"
```

---

### Task 2: `EvalReindexService` + `--eval.mode=reindex`

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/reindex/EvalReindexService.java`
- Modify: `backend/src/main/java/com/youthfit/eval/EvalRunner.java` (switch 에 `reindex` 케이스 + `runReindex` 메서드 + 클래스 javadoc 사용법 1줄)
- Test: `backend/src/test/java/com/youthfit/eval/reindex/EvalReindexServiceTest.java` (신규), `backend/src/test/java/com/youthfit/eval/EvalRunnerTest.java` (확장)

**Interfaces:**
- Consumes: Task 1 의 `reindexWithoutEvents(Long) : IndexingResult`, `PolicyDocumentRepository.deleteByPolicyId/findByPolicyIdOrderByChunkIndex`, `PolicyRepository.findAllForStats/findAllById`
- Produces:
  - `EvalReindexService.findTargets(List<Long> policyIds) : List<Policy>` — policyIds null/빈이면 청크 보유 전 정책, 아니면 해당 id 중 청크 보유 정책
  - `EvalReindexService.reindexPolicy(Long policyId) : boolean` — `@Transactional`, delete→reindexWithoutEvents, 결과 null 이면 false
  - `EvalRunner` args: `--eval.mode=reindex [--eval.confirm=true] [--eval.policy-ids=1,2]`

- [ ] **Step 1: 실패하는 테스트 작성**

`EvalReindexServiceTest.java`:

```java
package com.youthfit.eval.reindex;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@DisplayName("EvalReindexService")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalReindexServiceTest {

    @InjectMocks
    private EvalReindexService service;

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;
    @Mock private AttachmentReindexService attachmentReindexService;

    @Test
    @DisplayName("reindexPolicy 는 삭제 → 무이벤트 재인덱싱 순서로 실행한다")
    void reindexPolicy_deletesThenReindexes() {
        given(attachmentReindexService.reindexWithoutEvents(1L))
                .willReturn(new IndexingResult(1L, 5, true));

        boolean result = service.reindexPolicy(1L);

        assertThat(result).isTrue();
        InOrder order = inOrder(policyDocumentRepository, attachmentReindexService);
        order.verify(policyDocumentRepository).deleteByPolicyId(1L);
        order.verify(attachmentReindexService).reindexWithoutEvents(1L);
    }

    @Test
    @DisplayName("reindexWithoutEvents 가 null(스킵)이면 false")
    void reindexPolicy_returnsFalseOnSkip() {
        given(attachmentReindexService.reindexWithoutEvents(1L)).willReturn(null);

        assertThat(service.reindexPolicy(1L)).isFalse();
    }

    @Test
    @DisplayName("findTargets: policyIds 미지정이면 청크 보유 전 정책")
    void findTargets_allPoliciesWithChunks() {
        Policy withChunks = mock(Policy.class);
        given(withChunks.getId()).willReturn(1L);
        Policy withoutChunks = mock(Policy.class);
        given(withoutChunks.getId()).willReturn(2L);
        List<Policy> all = List.of(withChunks, withoutChunks);
        given(policyRepository.findAllForStats()).willReturn(all);
        List<PolicyDocument> chunks = List.of(mock(PolicyDocument.class));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(chunks);
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(2L)).willReturn(List.of());

        List<Policy> targets = service.findTargets(null);

        assertThat(targets).containsExactly(withChunks);
    }

    @Test
    @DisplayName("findTargets: policyIds 지정 시 해당 정책만 (청크 보유 필터 동일)")
    void findTargets_specificIds() {
        Policy p = mock(Policy.class);
        given(p.getId()).willReturn(7L);
        List<Policy> found = List.of(p);
        given(policyRepository.findAllById(List.of(7L))).willReturn(found);
        List<PolicyDocument> chunks = List.of(mock(PolicyDocument.class));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(7L)).willReturn(chunks);

        List<Policy> targets = service.findTargets(List.of(7L));

        assertThat(targets).containsExactly(p);
    }
}
```

`EvalRunnerTest.java` 에 추가 (기존 @Mock 목록에 `@Mock private EvalReindexService evalReindexService;` 추가 — @InjectMocks 생성자 주입 갱신):

```java
@Test
@DisplayName("--eval.mode=reindex 는 confirm 없으면 dry-run — reindexPolicy 를 호출하지 않는다")
void dispatchesReindexDryRun() throws Exception {
    Policy p = org.mockito.Mockito.mock(Policy.class);
    given(p.getId()).willReturn(1L);
    given(p.getTitle()).willReturn("정책");
    List<Policy> targets = List.of(p);
    given(evalReindexService.findTargets(null)).willReturn(targets);

    runner.dispatch(new DefaultApplicationArguments("--eval.mode=reindex"));

    verify(evalReindexService, never()).reindexPolicy(anyLong());
}

@Test
@DisplayName("--eval.mode=reindex --eval.confirm=true 는 대상마다 reindexPolicy 호출")
void dispatchesReindexConfirmed() throws Exception {
    Policy p = org.mockito.Mockito.mock(Policy.class);
    given(p.getId()).willReturn(1L);
    given(p.getTitle()).willReturn("정책");
    List<Policy> targets = List.of(p);
    given(evalReindexService.findTargets(null)).willReturn(targets);
    given(evalReindexService.reindexPolicy(1L)).willReturn(true);

    runner.dispatch(new DefaultApplicationArguments("--eval.mode=reindex", "--eval.confirm=true"));

    verify(evalReindexService).reindexPolicy(1L);
}
```

(import 추가: `com.youthfit.eval.reindex.EvalReindexService`, `com.youthfit.policy.domain.model.Policy`, `java.util.List`, `org.mockito.ArgumentMatchers.anyLong`, `org.mockito.Mockito.never`, `org.mockito.Mockito.verify`)

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.reindex.*" --tests "com.youthfit.eval.EvalRunnerTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`EvalReindexService.java`:

```java
package com.youthfit.eval.reindex;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 임베딩 모델 실험용 전 정책 재인덱싱(#167).
 * 삭제 후 재인덱싱이므로 source_hash 게이트(내용 기반)를 우회해
 * 현재 OPENAI_EMBEDDING_MODEL 로 임베딩을 새로 생성한다.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
public class EvalReindexService {

    private static final Logger log = LoggerFactory.getLogger(EvalReindexService.class);

    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final AttachmentReindexService attachmentReindexService;

    /** policyIds 가 null/빈이면 청크 보유 전 정책. */
    public List<Policy> findTargets(List<Long> policyIds) {
        List<Policy> candidates = (policyIds == null || policyIds.isEmpty())
                ? policyRepository.findAllForStats()
                : policyRepository.findAllById(policyIds);
        return candidates.stream()
                .filter(p -> !policyDocumentRepository
                        .findByPolicyIdOrderByChunkIndex(p.getId()).isEmpty())
                .toList();
    }

    /**
     * 정책 1건 재인덱싱 — 삭제와 재인덱싱을 한 트랜잭션으로 묶는다
     * (실패 시 롤백돼 기존 청크가 유실되지 않음).
     */
    @Transactional
    public boolean reindexPolicy(Long policyId) {
        policyDocumentRepository.deleteByPolicyId(policyId);
        IndexingResult result = attachmentReindexService.reindexWithoutEvents(policyId);
        if (result == null) {
            log.warn("재인덱싱 스킵(costGuard 또는 정책 미존재): policyId={}", policyId);
            return false;
        }
        return true;
    }
}
```

`EvalRunner.java` 수정:
1. 필드·생성자 주입에 `private final EvalReindexService evalReindexService;` 추가 (`import com.youthfit.eval.reindex.EvalReindexService;`, `import com.youthfit.policy.domain.model.Policy;`)
2. 클래스 javadoc 에 한 줄 추가: `* reindex:  SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=reindex --eval.confirm=true'`
3. switch 에 케이스 추가:

```java
case "reindex" -> runReindex(args);
```

4. 메서드 추가:

```java
private void runReindex(ApplicationArguments args) {
    boolean confirm = Boolean.parseBoolean(firstOption(args, "eval.confirm"));
    String idsArg = firstOption(args, "eval.policy-ids");
    List<Long> policyIds = idsArg == null ? null
            : java.util.Arrays.stream(idsArg.split(",")).map(String::trim).map(Long::parseLong).toList();

    List<Policy> targets = evalReindexService.findTargets(policyIds);
    log.info("reindex 대상: 정책 {}건 (현재 임베딩 모델로 delete→재인덱싱, 첨부 LLM 게이트는 판정 캐시 재사용)",
            targets.size());
    if (!confirm) {
        targets.forEach(p -> log.info("  - id={}, title={}", p.getId(), p.getTitle()));
        log.info("dry-run 종료. 실제 재인덱싱하려면 --eval.confirm=true 를 추가하세요.");
        return;
    }

    long start = System.currentTimeMillis();
    List<Long> failed = new java.util.ArrayList<>();
    int done = 0;
    for (Policy policy : targets) {
        try {
            if (evalReindexService.reindexPolicy(policy.getId())) {
                done++;
            } else {
                failed.add(policy.getId());
            }
        } catch (Exception e) {
            log.warn("재인덱싱 실패, 계속 진행: policyId={}, error={}", policy.getId(), e.toString());
            failed.add(policy.getId());
        }
    }
    log.info("reindex 완료: 성공 {}건 / 실패 {}건 {}, 소요 {}ms",
            done, failed.size(), failed.isEmpty() ? "" : failed, System.currentTimeMillis() - start);
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.*"
```
Expected: PASS (eval 전체)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval backend/src/test/java/com/youthfit/eval
git commit -m "feat(be): eval reindex 모드 — 임베딩 모델 교체 실험용 전 정책 재인덱싱 (#167)"
```

---

### Task 3: 임베딩 캐시 라벨을 실제 호출 모델로

**Files:**
- Modify: `backend/src/main/java/com/youthfit/eval/EvalRunner.java` (캐시 생성부 + resolveCacheLabel)
- Test: `backend/src/test/java/com/youthfit/eval/EvalRunnerTest.java` (확장)

**Interfaces:**
- Consumes: `OpenAiEmbeddingProperties.getModel()` (rag.infrastructure.external — eval→rag infra 참조는 QnaProperties 전례와 동일한 의도적 예외, javadoc 한 줄로 명시)
- Produces: `static String resolveCacheLabel(String actualModel, String datasetModel)` (package-private, 테스트용)

- [ ] **Step 1: 실패하는 테스트 작성**

`EvalRunnerTest.java` 에 추가:

```java
@Test
@DisplayName("캐시 라벨은 실제 호출 모델 — evalset 라벨과 불일치해도 실제 모델 사용")
void resolveCacheLabel_usesActualModel() {
    assertThat(EvalRunner.resolveCacheLabel("text-embedding-3-large", "text-embedding-3-small"))
            .isEqualTo("text-embedding-3-large");
    assertThat(EvalRunner.resolveCacheLabel("text-embedding-3-small", "text-embedding-3-small"))
            .isEqualTo("text-embedding-3-small");
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.EvalRunnerTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`EvalRunner` 에 `OpenAiEmbeddingProperties` 주입 (`import com.youthfit.rag.infrastructure.external.OpenAiEmbeddingProperties;` — 필드 `private final OpenAiEmbeddingProperties embeddingProperties;` 주석: `// eval→rag infra 의도적 참조: 실제 호출 모델을 캐시 라벨의 단일 소스로 사용 (#167)`).

캐시 생성부(runEvaluation 내) 교체:

```java
String cacheLabel = resolveCacheLabel(embeddingProperties.getModel(), dataset.embeddingModel());
QueryEmbeddingFileCache cache = new QueryEmbeddingFileCache(
        Path.of(evalProperties.cacheDir()), cacheLabel);
```

메서드 추가:

```java
/** 캐시 라벨은 실제 호출 모델. evalset 라벨과 다르면 경고(평가셋 제작 기준 모델 추적용). */
static String resolveCacheLabel(String actualModel, String datasetModel) {
    if (datasetModel != null && !datasetModel.equals(actualModel)) {
        LoggerFactory.getLogger(EvalRunner.class).warn(
                "evalset embeddingModel({}) 과 실제 호출 모델({}) 불일치 — 캐시는 실제 모델 기준으로 분리됩니다.",
                datasetModel, actualModel);
    }
    return actualModel;
}
```

기존 `EvalRunnerTest` 의 @Mock 목록에 `@Mock private OpenAiEmbeddingProperties embeddingProperties;` 추가.

- [ ] **Step 4: eval 전체 + 전체 빌드**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.*"
cd /Users/taetaetae/IdeaProjects/youthfit && set -a && source .env && set +a && cd backend && ./gradlew build
```
Expected: 둘 다 SUCCESS (compose postgres·redis 기동 상태)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/EvalRunner.java backend/src/test/java/com/youthfit/eval/EvalRunnerTest.java
git commit -m "feat(be): eval 임베딩 캐시 라벨을 실제 호출 모델로 — env 전환 시 자동 분리 (#167)"
```

---

## 구현 뒤 실험 절차 (플랜 범위 밖 — 컨트롤러/사용자 실행, 스펙 §4)

1. PR·머지 후 main 에서: `OPENAI_EMBEDDING_MODEL=text-embedding-3-large` + reindex dry-run → confirm (~1,178청크)
2. `docker compose exec -T postgres psql -U youthfit -d youthfit -c "TRUNCATE qna_question_cache;"`
3. `--eval.mode=run --eval.scenarios=baseline --eval.label=3large-experiment` → 3-small baseline 과 비교
4. 판정 (스펙 §5): 갭 ≥ 0.10 & negFP ≤ 0.40 & recall@1·MRR 열화 ≤ 0.02
5. 되돌리기(미달 시): env 원복 → reindex confirm → truncate 재실행
