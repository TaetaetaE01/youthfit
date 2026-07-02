# RAG Retrieval 평가셋 구축 (#162) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RAG retrieval 품질을 반복 측정하는 `eval` 모듈 — 평가셋 JSON, generate(LLM 역질문)/run(지표 측정) 모드 러너, recall@k·MRR·distance 갭·NEGATIVE 오탐률 산출, JSON 리포트.

**Architecture:** `com.youthfit.eval` 최상위 패키지 신설, 모든 빈은 `@Profile("eval")` 가드 (prod 영향 0). run 모드는 `RagSearchService.searchRelevantChunksWithTrace(command, embedding, overrides)` 를 직접 호출해 검색 로직 복제 없이 측정. 정답 판정은 "기대 근거 스니펫 포함" 방식(재인덱싱·청킹 변경에 생존). 스펙: `docs/superpowers/specs/2026-07-02-rag-retrieval-evalset-design.md`.

**Tech Stack:** Java 21, Spring Boot 4.0.5, tools.jackson (Jackson 3) ObjectMapper, JUnit 5 + Mockito + AssertJ, Testcontainers(pgvector).

## Global Constraints

- 의존 방향: `eval → rag, qna, policy` 단방향만 허용. rag/qna/policy 가 eval 을 import 하면 안 된다.
- eval 의 모든 Spring 빈에 `@Profile("eval")` — 기본/prod 프로파일에서 빈이 뜨지 않아야 한다.
- DTO 는 Java record (`.claude/rules/backend/dto.md`). Lombok 은 `@Getter/@Builder/@RequiredArgsConstructor` 만 (`lombok.md`).
- JSON 은 `tools.jackson.databind.ObjectMapper` (Jackson 3 — `com.fasterxml` 아님).
- Properties record 는 `@ConfigurationProperties` 만 붙이면 됨 (`YouthfitApplication` 에 `@ConfigurationPropertiesScan` 존재).
- 커밋 메시지: `feat(be): …` / `test(be): …` (create-pr 스킬 영역 태그 규칙).
- 작업 브랜치: `feat/be-eval-retrieval-evalset` (main 에서 분기).
- 테스트 실행은 `cd backend && ./gradlew test --tests "<클래스 FQN>"`. 전체 빌드는 `./gradlew build` (docker compose postgres·redis 기동 + 리포 루트 `.env` 를 `set -a && source .env && set +a` 로 주입해야 전부 그린).
- 소비하는 기존 시그니처 (변경 금지, 그대로 사용):
  - `RagSearchService.searchRelevantChunksWithTrace(SearchChunksCommand, float[], @Nullable HybridSearchOverrides) : RagSearchTrace`
  - `SearchChunksCommand(Long policyId, String query)` — record
  - `HybridSearchOverrides(Boolean hybridEnabled, Integer topNPerSearch, Integer rrfK, Double trigramThreshold, Boolean keywordBoostEnabled, Integer maxKeywords)` — 전 필드 nullable, null=baseline
  - `RagSearchTrace(EffectiveConfig effective, List<SimilarChunk> vectorTopN, List<SimilarChunk> trigramTopN, List<MergedChunk> merged, List<String> usedKeywords, long tookMs)`
  - `SimilarChunk(Long id, Long policyId, int chunkIndex, String content, Long attachmentId, Integer pageStart, Integer pageEnd, double distance)`
  - `MergedChunk(long chunkId, int chunkIndex, double distance, double rrfScore, int rank, String preview)` — **preview 는 500자 truncate — 스니펫 매칭에 쓰면 안 됨. content 는 vectorTopN/trigramTopN 에서 chunkId 로 찾는다.**
  - `EmbeddingProvider.embed(String) : float[]` (rag.application.port)
  - `QueryRewriter.rewrite(String policyTitle, String userQuestion) : Optional<String>` (qna.application.port)
  - `PolicyRepository.findById(Long) : Optional<Policy>`, `PolicyRepository.findAllByFilters(null, null, null, SourceType, Pageable) : Page<Policy>` (null 필터는 무시됨 — PolicySpecification 확인 완료)
  - `PolicyDocumentRepository.findByPolicyIdOrderByChunkIndex(Long) : List<PolicyDocument>` (rag.domain.repository)
  - `Policy.getTitle()`, `PolicyDocument.getContent()`, `PolicyDocument.getChunkIndex()`
  - `QnaProperties.relevanceDistanceThreshold() : double` (qna.infrastructure.config — 현행 0.78)
  - `SourceType` enum: `YOUTH_SEOUL_CRAWL, BOKJIRO_CENTRAL, YOUTH_CENTER` (policy.domain.model)

---

### Task 1: dataset 패키지 — EvalCase 모델·SnippetMatcher·로더

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/dataset/EvalQuestionType.java`
- Create: `backend/src/main/java/com/youthfit/eval/dataset/EvalCase.java`
- Create: `backend/src/main/java/com/youthfit/eval/dataset/EvalDataset.java`
- Create: `backend/src/main/java/com/youthfit/eval/dataset/SnippetMatcher.java`
- Create: `backend/src/main/java/com/youthfit/eval/dataset/EvalDatasetLoader.java`
- Test: `backend/src/test/java/com/youthfit/eval/dataset/SnippetMatcherTest.java`
- Test: `backend/src/test/java/com/youthfit/eval/dataset/EvalDatasetLoaderTest.java`

**Interfaces:**
- Consumes: 없음 (기반 레이어)
- Produces:
  - `enum EvalQuestionType { KEYWORD, COLLOQUIAL, NEGATIVE }`
  - `record EvalCase(String id, Long policyId, String policyTitle, String question, EvalQuestionType questionType, List<String> expectedSnippets, String notes)`
  - `record EvalDataset(int version, String embeddingModel, List<EvalCase> cases)`
  - `SnippetMatcher.normalize(String) : String`, `SnippetMatcher.containsSnippet(String content, String snippet) : boolean`
  - `EvalDatasetLoader.load(Path) : EvalDataset` (인스턴스 메서드, `new EvalDatasetLoader()` 로 생성)

- [ ] **Step 1: 브랜치 생성**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit && git switch -c feat/be-eval-retrieval-evalset main
```

- [ ] **Step 2: 실패하는 테스트 작성**

`SnippetMatcherTest.java`:

```java
package com.youthfit.eval.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SnippetMatcher")
class SnippetMatcherTest {

    @Test
    @DisplayName("개행·연속 공백이 달라도 스니펫 포함으로 판정한다")
    void matchesAcrossWhitespaceDifferences() {
        String chunk = "지원 대상은  만 19세~34세\n청년입니다.\n대학 재학생은   신청 대상에서 제외됩니다.";
        String snippet = "대학 재학생은 신청 대상에서\n제외됩니다.";

        assertThat(SnippetMatcher.containsSnippet(chunk, snippet)).isTrue();
    }

    @Test
    @DisplayName("내용이 다르면 불일치")
    void rejectsDifferentContent() {
        assertThat(SnippetMatcher.containsSnippet("전세 보증금 지원", "월세 지원")).isFalse();
    }

    @Test
    @DisplayName("null·빈 입력은 불일치")
    void rejectsNullOrBlank() {
        assertThat(SnippetMatcher.containsSnippet(null, "스니펫")).isFalse();
        assertThat(SnippetMatcher.containsSnippet("본문", null)).isFalse();
        assertThat(SnippetMatcher.containsSnippet("본문", "  ")).isFalse();
    }
}
```

`EvalDatasetLoaderTest.java`:

```java
package com.youthfit.eval.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvalDatasetLoader")
class EvalDatasetLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("평가셋 JSON 을 로드한다")
    void loadsDataset() throws Exception {
        Path file = tempDir.resolve("evalset.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "embeddingModel": "text-embedding-3-small",
                  "cases": [
                    {
                      "id": "p1-q1",
                      "policyId": 1,
                      "policyTitle": "청년 월세 지원",
                      "question": "재학생도 신청 가능한가요?",
                      "questionType": "KEYWORD",
                      "expectedSnippets": ["대학 재학생은 신청 대상에서 제외"],
                      "notes": null
                    }
                  ]
                }
                """);

        EvalDataset dataset = new EvalDatasetLoader().load(file);

        assertThat(dataset.version()).isEqualTo(1);
        assertThat(dataset.cases()).hasSize(1);
        EvalCase c = dataset.cases().get(0);
        assertThat(c.questionType()).isEqualTo(EvalQuestionType.KEYWORD);
        assertThat(c.expectedSnippets()).isEqualTo(List.of("대학 재학생은 신청 대상에서 제외"));
    }

    @Test
    @DisplayName("파일이 없으면 명확한 예외")
    void failsOnMissingFile() {
        assertThatThrownBy(() -> new EvalDatasetLoader().load(tempDir.resolve("없는파일.json")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평가셋 파일을 찾을 수 없습니다");
    }
}
```

- [ ] **Step 3: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.dataset.*"
```
Expected: 컴파일 에러 (클래스 미존재) — FAIL

- [ ] **Step 4: 구현**

`EvalQuestionType.java`:

```java
package com.youthfit.eval.dataset;

public enum EvalQuestionType {
    KEYWORD,
    COLLOQUIAL,
    NEGATIVE
}
```

`EvalCase.java`:

```java
package com.youthfit.eval.dataset;

import java.util.List;

public record EvalCase(
        String id,
        Long policyId,
        String policyTitle,
        String question,
        EvalQuestionType questionType,
        List<String> expectedSnippets,
        String notes
) {}
```

`EvalDataset.java`:

```java
package com.youthfit.eval.dataset;

import java.util.List;

public record EvalDataset(
        int version,
        String embeddingModel,
        List<EvalCase> cases
) {}
```

`SnippetMatcher.java`:

```java
package com.youthfit.eval.dataset;

/**
 * 기대 근거 스니펫 포함 판정. 청크 PK 는 재인덱싱마다 바뀌므로
 * 정답 앵커는 원문 발췌 스니펫의 정규화 포함 매칭으로 한다.
 */
public final class SnippetMatcher {

    private SnippetMatcher() {
    }

    /** 연속 공백·개행을 단일 공백으로 접고 trim. */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    public static boolean containsSnippet(String content, String snippet) {
        String normalizedSnippet = normalize(snippet);
        if (normalizedSnippet.isEmpty()) return false;
        return normalize(content).contains(normalizedSnippet);
    }
}
```

`EvalDatasetLoader.java`:

```java
package com.youthfit.eval.dataset;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class EvalDatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvalDataset load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("평가셋 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }
        try {
            return objectMapper.readValue(Files.readString(path), EvalDataset.class);
        } catch (Exception e) {
            throw new IllegalStateException("평가셋 파싱 실패: " + path.toAbsolutePath(), e);
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.dataset.*"
```
Expected: PASS (5 tests)

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/dataset backend/src/test/java/com/youthfit/eval/dataset
git commit -m "feat(be): eval 평가셋 모델·스니펫 매처·로더 (#162)"
```

---

### Task 2: EvalMetricsCalculator — recall@k·MRR·distance 갭·NEGATIVE 오탐률

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/run/CaseStatus.java`
- Create: `backend/src/main/java/com/youthfit/eval/run/RankedChunk.java`
- Create: `backend/src/main/java/com/youthfit/eval/run/CaseResult.java`
- Create: `backend/src/main/java/com/youthfit/eval/run/TypeMetrics.java`
- Create: `backend/src/main/java/com/youthfit/eval/run/ScenarioMetrics.java`
- Create: `backend/src/main/java/com/youthfit/eval/run/EvalMetricsCalculator.java`
- Test: `backend/src/test/java/com/youthfit/eval/run/EvalMetricsCalculatorTest.java`

**Interfaces:**
- Consumes: `EvalCase`, `EvalQuestionType` (Task 1), `EffectiveConfig` (rag 기존)
- Produces:
  - `enum CaseStatus { OK, SKIPPED, STALE, NO_CHUNKS }`
  - `record RankedChunk(long chunkId, int rank, double distance, boolean relevant)`
  - `record CaseResult(EvalCase evalCase, CaseStatus status, List<RankedChunk> ranked, Integer firstRelevantRank, long tookMs, String effectiveQuestion, EffectiveConfig effective)` — `effectiveQuestion` 은 rewrite 적용 후 실제 검색에 쓴 질문
  - `record TypeMetrics(int evaluated, Map<Integer, Double> recallAtK, double mrrAt10)`
  - `record ScenarioMetrics(String scenario, int totalCases, int okCases, TypeMetrics overall, Map<EvalQuestionType, TypeMetrics> byType, Double relevantDistanceAvg, Double irrelevantDistanceAvg, Double negativeFalsePositiveRate, double avgTookMs)`
  - `EvalMetricsCalculator.calculate(String scenario, List<CaseResult> results, double negativeThreshold) : ScenarioMetrics` (인스턴스 메서드)

- [ ] **Step 1: 실패하는 테스트 작성**

`EvalMetricsCalculatorTest.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalQuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EvalMetricsCalculator")
class EvalMetricsCalculatorTest {

    private final EvalMetricsCalculator calculator = new EvalMetricsCalculator();

    private EvalCase evalCase(String id, EvalQuestionType type) {
        return new EvalCase(id, 1L, "정책", "질문", type,
                type == EvalQuestionType.NEGATIVE ? List.of() : List.of("스니펫"), null);
    }

    private CaseResult ok(EvalCase c, Integer firstRelevantRank, List<RankedChunk> ranked) {
        return new CaseResult(c, CaseStatus.OK, ranked, firstRelevantRank, 100L, c.question(), null);
    }

    @Test
    @DisplayName("recall@k 와 MRR@10 을 계산한다")
    void calculatesRecallAndMrr() {
        // case1: 1위 정답, case2: 4위 정답, case3: 정답 없음
        CaseResult r1 = ok(evalCase("c1", EvalQuestionType.KEYWORD), 1,
                List.of(new RankedChunk(11L, 1, 0.30, true)));
        CaseResult r2 = ok(evalCase("c2", EvalQuestionType.COLLOQUIAL), 4,
                List.of(new RankedChunk(21L, 1, 0.50, false),
                        new RankedChunk(22L, 2, 0.55, false),
                        new RankedChunk(23L, 3, 0.60, false),
                        new RankedChunk(24L, 4, 0.65, true)));
        CaseResult r3 = ok(evalCase("c3", EvalQuestionType.KEYWORD), null,
                List.of(new RankedChunk(31L, 1, 0.70, false)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(r1, r2, r3), 0.78);

        assertThat(m.overall().evaluated()).isEqualTo(3);
        assertThat(m.overall().recallAtK().get(1)).isEqualTo(1.0 / 3, within(1e-9));
        assertThat(m.overall().recallAtK().get(3)).isEqualTo(1.0 / 3, within(1e-9));
        assertThat(m.overall().recallAtK().get(5)).isEqualTo(2.0 / 3, within(1e-9));
        assertThat(m.overall().recallAtK().get(10)).isEqualTo(2.0 / 3, within(1e-9));
        // MRR = (1/1 + 1/4 + 0) / 3
        assertThat(m.overall().mrrAt10()).isEqualTo((1.0 + 0.25) / 3, within(1e-9));
        assertThat(m.byType().get(EvalQuestionType.KEYWORD).evaluated()).isEqualTo(2);
        assertThat(m.byType().get(EvalQuestionType.COLLOQUIAL).evaluated()).isEqualTo(1);
    }

    @Test
    @DisplayName("NEGATIVE 오탐률 — top-1 distance 가 threshold 이하면 오탐")
    void calculatesNegativeFalsePositiveRate() {
        EvalCase neg1 = evalCase("n1", EvalQuestionType.NEGATIVE);
        EvalCase neg2 = evalCase("n2", EvalQuestionType.NEGATIVE);
        CaseResult fp = ok(neg1, null, List.of(new RankedChunk(1L, 1, 0.60, false))); // 0.60 <= 0.78 오탐
        CaseResult tn = ok(neg2, null, List.of(new RankedChunk(2L, 1, 0.90, false)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(fp, tn), 0.78);

        assertThat(m.negativeFalsePositiveRate()).isEqualTo(0.5, within(1e-9));
        assertThat(m.overall().evaluated()).isZero(); // NEGATIVE 는 recall 집계 제외
    }

    @Test
    @DisplayName("distance 갭 — 정답 청크 평균 vs 비정답 top-5 평균")
    void calculatesDistanceGap() {
        CaseResult r = ok(evalCase("c1", EvalQuestionType.KEYWORD), 1,
                List.of(new RankedChunk(1L, 1, 0.40, true),
                        new RankedChunk(2L, 2, 0.70, false),
                        new RankedChunk(3L, 3, 0.80, false)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(r), 0.78);

        assertThat(m.relevantDistanceAvg()).isEqualTo(0.40, within(1e-9));
        assertThat(m.irrelevantDistanceAvg()).isEqualTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("OK 아닌 케이스는 모든 지표에서 제외한다")
    void excludesNonOkCases() {
        CaseResult stale = new CaseResult(evalCase("s1", EvalQuestionType.KEYWORD),
                CaseStatus.STALE, List.of(), null, 0L, "질문", null);
        CaseResult okCase = ok(evalCase("c1", EvalQuestionType.KEYWORD), 1,
                List.of(new RankedChunk(1L, 1, 0.40, true)));

        ScenarioMetrics m = calculator.calculate("baseline", List.of(stale, okCase), 0.78);

        assertThat(m.totalCases()).isEqualTo(2);
        assertThat(m.okCases()).isEqualTo(1);
        assertThat(m.overall().evaluated()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.EvalMetricsCalculatorTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`CaseStatus.java`:

```java
package com.youthfit.eval.run;

public enum CaseStatus {
    OK,
    SKIPPED,     // 임베딩·검색 예외
    STALE,       // policyId-title 불일치 (시드 재구축 감지)
    NO_CHUNKS    // 대상 정책에 인덱싱된 청크 없음
}
```

`RankedChunk.java`:

```java
package com.youthfit.eval.run;

public record RankedChunk(long chunkId, int rank, double distance, boolean relevant) {}
```

`CaseResult.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.rag.application.dto.result.EffectiveConfig;

import java.util.List;

public record CaseResult(
        EvalCase evalCase,
        CaseStatus status,
        List<RankedChunk> ranked,
        Integer firstRelevantRank,
        long tookMs,
        String effectiveQuestion,
        EffectiveConfig effective
) {}
```

`TypeMetrics.java`:

```java
package com.youthfit.eval.run;

import java.util.Map;

public record TypeMetrics(int evaluated, Map<Integer, Double> recallAtK, double mrrAt10) {}
```

`ScenarioMetrics.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalQuestionType;

import java.util.Map;

public record ScenarioMetrics(
        String scenario,
        int totalCases,
        int okCases,
        TypeMetrics overall,
        Map<EvalQuestionType, TypeMetrics> byType,
        Double relevantDistanceAvg,
        Double irrelevantDistanceAvg,
        Double negativeFalsePositiveRate,
        double avgTookMs
) {}
```

`EvalMetricsCalculator.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalQuestionType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시나리오 하나의 CaseResult 리스트에서 집계 지표를 계산한다.
 * recall@k·MRR 은 KEYWORD+COLLOQUIAL(OK) 만, NEGATIVE 는 오탐률 전용.
 */
public class EvalMetricsCalculator {

    private static final List<Integer> KS = List.of(1, 3, 5, 10);

    public ScenarioMetrics calculate(String scenario, List<CaseResult> results, double negativeThreshold) {
        List<CaseResult> ok = results.stream().filter(r -> r.status() == CaseStatus.OK).toList();

        List<CaseResult> positives = ok.stream()
                .filter(r -> r.evalCase().questionType() != EvalQuestionType.NEGATIVE)
                .toList();
        List<CaseResult> negatives = ok.stream()
                .filter(r -> r.evalCase().questionType() == EvalQuestionType.NEGATIVE)
                .toList();

        Map<EvalQuestionType, TypeMetrics> byType = new EnumMap<>(EvalQuestionType.class);
        for (EvalQuestionType type : List.of(EvalQuestionType.KEYWORD, EvalQuestionType.COLLOQUIAL)) {
            List<CaseResult> ofType = positives.stream()
                    .filter(r -> r.evalCase().questionType() == type)
                    .toList();
            if (!ofType.isEmpty()) {
                byType.put(type, typeMetrics(ofType));
            }
        }

        Double negativeFpRate = negatives.isEmpty() ? null
                : negatives.stream()
                        .filter(r -> !r.ranked().isEmpty()
                                && r.ranked().get(0).distance() <= negativeThreshold)
                        .count() / (double) negatives.size();

        List<Double> relevantDistances = positives.stream()
                .flatMap(r -> r.ranked().stream())
                .filter(RankedChunk::relevant)
                .map(RankedChunk::distance)
                .toList();
        List<Double> irrelevantDistances = positives.stream()
                .flatMap(r -> r.ranked().stream().filter(c -> !c.relevant() && c.rank() <= 5))
                .map(RankedChunk::distance)
                .toList();

        double avgTookMs = ok.isEmpty() ? 0.0
                : ok.stream().mapToLong(CaseResult::tookMs).average().orElse(0.0);

        return new ScenarioMetrics(
                scenario,
                results.size(),
                ok.size(),
                typeMetrics(positives),
                byType,
                average(relevantDistances),
                average(irrelevantDistances),
                negativeFpRate,
                avgTookMs
        );
    }

    private TypeMetrics typeMetrics(List<CaseResult> results) {
        Map<Integer, Double> recallAtK = new LinkedHashMap<>();
        for (int k : KS) {
            final int kk = k;
            double recall = results.isEmpty() ? 0.0
                    : results.stream()
                            .filter(r -> r.firstRelevantRank() != null && r.firstRelevantRank() <= kk)
                            .count() / (double) results.size();
            recallAtK.put(k, recall);
        }
        double mrr = results.isEmpty() ? 0.0
                : results.stream()
                        .mapToDouble(r -> r.firstRelevantRank() == null ? 0.0 : 1.0 / r.firstRelevantRank())
                        .average().orElse(0.0);
        return new TypeMetrics(results.size(), recallAtK, mrr);
    }

    private Double average(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.EvalMetricsCalculatorTest"
```
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/run backend/src/test/java/com/youthfit/eval/run
git commit -m "feat(be): eval 지표 계산기 — recall@k·MRR·distance 갭·NEGATIVE 오탐률 (#162)"
```

---

### Task 3: EvalScenario — 시나리오명 → HybridSearchOverrides 매핑

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/run/EvalScenario.java`
- Test: `backend/src/test/java/com/youthfit/eval/run/EvalScenarioTest.java`

**Interfaces:**
- Consumes: `HybridSearchOverrides` (rag 기존)
- Produces: `record EvalScenario(String name, HybridSearchOverrides overrides, boolean queryRewrite)` + `static EvalScenario of(String name)` — 지원: `baseline`, `hybrid-on`, `boost-off`, `rewrite-on`. 미지원 이름은 `IllegalArgumentException`.

- [ ] **Step 1: 실패하는 테스트 작성**

`EvalScenarioTest.java`:

```java
package com.youthfit.eval.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvalScenario")
class EvalScenarioTest {

    @Test
    @DisplayName("baseline 은 overrides 없음(null) — 운영 기본값 그대로")
    void baselineHasNoOverrides() {
        EvalScenario s = EvalScenario.of("baseline");
        assertThat(s.overrides()).isNull();
        assertThat(s.queryRewrite()).isFalse();
    }

    @Test
    @DisplayName("hybrid-on 은 hybridEnabled=true 만 덮어쓴다")
    void hybridOn() {
        EvalScenario s = EvalScenario.of("hybrid-on");
        assertThat(s.overrides().hybridEnabled()).isTrue();
        assertThat(s.overrides().keywordBoostEnabled()).isNull();
    }

    @Test
    @DisplayName("boost-off 는 keywordBoostEnabled=false 만 덮어쓴다")
    void boostOff() {
        EvalScenario s = EvalScenario.of("boost-off");
        assertThat(s.overrides().keywordBoostEnabled()).isFalse();
        assertThat(s.overrides().hybridEnabled()).isNull();
    }

    @Test
    @DisplayName("rewrite-on 은 쿼리 재작성 플래그만 켠다")
    void rewriteOn() {
        EvalScenario s = EvalScenario.of("rewrite-on");
        assertThat(s.queryRewrite()).isTrue();
        assertThat(s.overrides()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 시나리오명은 예외")
    void unknownNameThrows() {
        assertThatThrownBy(() -> EvalScenario.of("없는시나리오"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는시나리오");
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.EvalScenarioTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`EvalScenario.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;

/**
 * 평가 시나리오. overrides == null 이면 운영 baseline(yml) 그대로.
 * queryRewrite == true 면 검색 전에 QueryRewriter 로 질문을 재작성한다.
 */
public record EvalScenario(String name, HybridSearchOverrides overrides, boolean queryRewrite) {

    public static EvalScenario of(String name) {
        return switch (name) {
            case "baseline" -> new EvalScenario("baseline", null, false);
            case "hybrid-on" -> new EvalScenario("hybrid-on",
                    new HybridSearchOverrides(true, null, null, null, null, null), false);
            case "boost-off" -> new EvalScenario("boost-off",
                    new HybridSearchOverrides(null, null, null, null, false, null), false);
            case "rewrite-on" -> new EvalScenario("rewrite-on", null, true);
            default -> throw new IllegalArgumentException("알 수 없는 시나리오: " + name
                    + " (지원: baseline, hybrid-on, boost-off, rewrite-on)");
        };
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.EvalScenarioTest"
```
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/run/EvalScenario.java backend/src/test/java/com/youthfit/eval/run/EvalScenarioTest.java
git commit -m "feat(be): eval 시나리오 — HybridSearchOverrides 매핑 (#162)"
```

---

### Task 4: QueryEmbeddingFileCache — 질문 임베딩 파일 캐시

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/run/QueryEmbeddingFileCache.java`
- Test: `backend/src/test/java/com/youthfit/eval/run/QueryEmbeddingFileCacheTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `QueryEmbeddingFileCache(Path cacheDir, String model)` 생성자 + `getOrCompute(String question, Function<String, float[]> embedFn) : float[]` + `save()`. 파일: `<cacheDir>/embeddings-<model>.json` (`sha256(질문) → float[]` 맵).

- [ ] **Step 1: 실패하는 테스트 작성**

`QueryEmbeddingFileCacheTest.java`:

```java
package com.youthfit.eval.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueryEmbeddingFileCache")
class QueryEmbeddingFileCacheTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("최초 호출은 embedFn 실행, 재호출은 캐시 히트로 호출 0회")
    void cachesAcrossInstances() {
        AtomicInteger calls = new AtomicInteger();
        float[] vector = {0.1f, 0.2f, 0.3f};

        QueryEmbeddingFileCache cache1 = new QueryEmbeddingFileCache(tempDir, "test-model");
        float[] first = cache1.getOrCompute("재학생도 되나요?", q -> {
            calls.incrementAndGet();
            return vector;
        });
        cache1.save();

        QueryEmbeddingFileCache cache2 = new QueryEmbeddingFileCache(tempDir, "test-model");
        float[] second = cache2.getOrCompute("재학생도 되나요?", q -> {
            calls.incrementAndGet();
            return new float[]{9f};
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(first).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(second).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(Files.exists(tempDir.resolve("embeddings-test-model.json"))).isTrue();
    }

    @Test
    @DisplayName("모델이 다르면 캐시를 공유하지 않는다")
    void separateFilePerModel() {
        QueryEmbeddingFileCache cacheA = new QueryEmbeddingFileCache(tempDir, "model-a");
        cacheA.getOrCompute("질문", q -> new float[]{1f});
        cacheA.save();

        AtomicInteger calls = new AtomicInteger();
        QueryEmbeddingFileCache cacheB = new QueryEmbeddingFileCache(tempDir, "model-b");
        cacheB.getOrCompute("질문", q -> {
            calls.incrementAndGet();
            return new float[]{2f};
        });

        assertThat(calls.get()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.QueryEmbeddingFileCacheTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`QueryEmbeddingFileCache.java`:

```java
package com.youthfit.eval.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 질문 임베딩 파일 캐시. 첫 run 만 임베딩 API 를 호출하고 이후 run 은 비용 0.
 * 파일은 git 커밋하지 않는다 (backend/eval/.gitignore).
 */
public class QueryEmbeddingFileCache {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingFileCache.class);

    private final Path file;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, float[]> entries;

    public QueryEmbeddingFileCache(Path cacheDir, String model) {
        this.file = cacheDir.resolve("embeddings-" + model + ".json");
        this.entries = loadExisting();
    }

    public float[] getOrCompute(String question, Function<String, float[]> embedFn) {
        return entries.computeIfAbsent(sha256(question), key -> embedFn.apply(question));
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, objectMapper.writeValueAsString(entries));
        } catch (Exception e) {
            log.warn("임베딩 캐시 저장 실패 (다음 run 에서 재호출됨): {}", file, e);
        }
    }

    private Map<String, float[]> loadExisting() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(Files.readString(file), new TypeReference<HashMap<String, float[]>>() {});
        } catch (Exception e) {
            log.warn("임베딩 캐시 로드 실패, 빈 캐시로 시작: {}", file, e);
            return new HashMap<>();
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.QueryEmbeddingFileCacheTest"
```
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/run/QueryEmbeddingFileCache.java backend/src/test/java/com/youthfit/eval/run/QueryEmbeddingFileCacheTest.java
git commit -m "feat(be): eval 질문 임베딩 파일 캐시 (#162)"
```

---

### Task 5: RetrievalEvaluator — 케이스 실행·판정 오케스트레이션

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/run/RetrievalEvaluator.java`
- Test: `backend/src/test/java/com/youthfit/eval/run/RetrievalEvaluatorTest.java`

**Interfaces:**
- Consumes: Task 1~4 산출물 + `RagSearchService`, `EmbeddingProvider`, `QueryRewriter`, `PolicyRepository`, `PolicyDocumentRepository` (기존)
- Produces: `RetrievalEvaluator` Spring 빈(`@Profile("eval") @Component`) — `evaluate(EvalCase c, EvalScenario scenario, QueryEmbeddingFileCache cache) : CaseResult`

- [ ] **Step 1: 실패하는 테스트 작성**

`RetrievalEvaluatorTest.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@DisplayName("RetrievalEvaluator")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrievalEvaluatorTest {

    @InjectMocks
    private RetrievalEvaluator evaluator;

    @Mock private RagSearchService ragSearchService;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private QueryRewriter queryRewriter;
    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;

    @TempDir
    Path tempDir;

    private QueryEmbeddingFileCache cache;
    private final EvalCase evalCase = new EvalCase("p1-q1", 1L, "청년 월세 지원",
            "재학생도 되나요?", EvalQuestionType.KEYWORD,
            List.of("대학 재학생은 신청 대상에서 제외"), null);

    private Policy policy(String title) {
        Policy p = org.mockito.Mockito.mock(Policy.class);
        given(p.getTitle()).willReturn(title);
        return p;
    }

    private RagSearchTrace trace(List<SimilarChunk> vec, List<MergedChunk> merged) {
        EffectiveConfig effective = new EffectiveConfig(false, 20, 60, 0.1, true, 5);
        return new RagSearchTrace(effective, vec, List.of(), merged, List.of(), 42L);
    }

    @BeforeEach
    void setUp() {
        cache = new QueryEmbeddingFileCache(tempDir, "test-model");
        given(embeddingProvider.embed(anyString())).willReturn(new float[]{0.1f});
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy("청년 월세 지원")));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                .willReturn(List.of(org.mockito.Mockito.mock(PolicyDocument.class)));
    }

    @Test
    @DisplayName("스니펫 포함 청크를 정답으로 마킹하고 첫 정답 순위를 계산한다")
    void marksRelevantChunks() {
        SimilarChunk c1 = new SimilarChunk(11L, 1L, 0, "보증금 관련 내용", null, null, null, 0.5);
        SimilarChunk c2 = new SimilarChunk(12L, 1L, 1,
                "지원 대상: 대학 재학생은 신청 대상에서 제외됩니다.", null, null, null, 0.6);
        List<MergedChunk> merged = List.of(
                new MergedChunk(11L, 0, 0.5, 0.0, 1, "보증금"),
                new MergedChunk(12L, 1, 0.6, 0.0, 2, "지원 대상"));
        given(ragSearchService.searchRelevantChunksWithTrace(any(), any(), any()))
                .willReturn(trace(List.of(c1, c2), merged));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.firstRelevantRank()).isEqualTo(2);
        assertThat(result.ranked()).extracting(RankedChunk::relevant)
                .containsExactly(false, true);
        assertThat(result.tookMs()).isEqualTo(42L);
    }

    @Test
    @DisplayName("정책 title 불일치는 STALE")
    void detectsStaleCase() {
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy("전혀 다른 정책")));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.STALE);
    }

    @Test
    @DisplayName("청크 0건이면 NO_CHUNKS")
    void detectsNoChunks() {
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(List.of());

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.NO_CHUNKS);
    }

    @Test
    @DisplayName("rewrite-on 시나리오는 재작성 질문으로 검색하고 effectiveQuestion 에 기록")
    void appliesQueryRewrite() {
        given(queryRewriter.rewrite("청년 월세 지원", "재학생도 되나요?"))
                .willReturn(Optional.of("청년 월세 지원 대학 재학생 신청 자격"));
        given(ragSearchService.searchRelevantChunksWithTrace(any(), any(), any()))
                .willReturn(trace(List.of(), List.of()));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("rewrite-on"), cache);

        assertThat(result.effectiveQuestion()).isEqualTo("청년 월세 지원 대학 재학생 신청 자격");
    }

    @Test
    @DisplayName("임베딩 예외는 SKIPPED")
    void skipsOnEmbeddingFailure() {
        given(embeddingProvider.embed(anyString())).willThrow(new RuntimeException("API 오류"));

        CaseResult result = evaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.SKIPPED);
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.RetrievalEvaluatorTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`RetrievalEvaluator.java`:

```java
package com.youthfit.eval.run;

import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.SnippetMatcher;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.port.QueryRewriter;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 평가 케이스 1건을 시나리오 설정으로 실행해 판정한다.
 * 스니펫 매칭은 MergedChunk.preview(500자 truncate)가 아니라
 * vectorTopN/trigramTopN 의 전체 content 로 수행한다.
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
public class RetrievalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluator.class);

    private final RagSearchService ragSearchService;
    private final EmbeddingProvider embeddingProvider;
    private final QueryRewriter queryRewriter;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;

    public CaseResult evaluate(EvalCase c, EvalScenario scenario, QueryEmbeddingFileCache cache) {
        Optional<Policy> policy = policyRepository.findById(c.policyId());
        if (policy.isEmpty()
                || !SnippetMatcher.normalize(policy.get().getTitle())
                        .equals(SnippetMatcher.normalize(c.policyTitle()))) {
            log.warn("STALE 케이스: id={}, 기대 title=\"{}\", 실제={}",
                    c.id(), c.policyTitle(), policy.map(Policy::getTitle).orElse("(정책 없음)"));
            return new CaseResult(c, CaseStatus.STALE, List.of(), null, 0L, c.question(), null);
        }

        if (policyDocumentRepository.findByPolicyIdOrderByChunkIndex(c.policyId()).isEmpty()) {
            return new CaseResult(c, CaseStatus.NO_CHUNKS, List.of(), null, 0L, c.question(), null);
        }

        try {
            String question = scenario.queryRewrite()
                    ? queryRewriter.rewrite(policy.get().getTitle(), c.question()).orElse(c.question())
                    : c.question();

            float[] embedding = cache.getOrCompute(question, embeddingProvider::embed);
            RagSearchTrace trace = ragSearchService.searchRelevantChunksWithTrace(
                    new SearchChunksCommand(c.policyId(), question), embedding, scenario.overrides());

            Map<Long, String> contentById = new HashMap<>();
            for (SimilarChunk chunk : trace.vectorTopN()) {
                contentById.put(chunk.id(), chunk.content());
            }
            for (SimilarChunk chunk : trace.trigramTopN()) {
                contentById.putIfAbsent(chunk.id(), chunk.content());
            }

            List<RankedChunk> ranked = new ArrayList<>();
            Integer firstRelevantRank = null;
            for (MergedChunk merged : trace.merged()) {
                String content = contentById.getOrDefault(merged.chunkId(), merged.preview());
                boolean relevant = c.expectedSnippets().stream()
                        .anyMatch(snippet -> SnippetMatcher.containsSnippet(content, snippet));
                ranked.add(new RankedChunk(merged.chunkId(), merged.rank(), merged.distance(), relevant));
                if (relevant && firstRelevantRank == null) {
                    firstRelevantRank = merged.rank();
                }
            }

            return new CaseResult(c, CaseStatus.OK, ranked, firstRelevantRank,
                    trace.tookMs(), question, trace.effective());
        } catch (Exception e) {
            log.warn("케이스 실행 실패 SKIPPED: id={}, error={}", c.id(), e.toString());
            return new CaseResult(c, CaseStatus.SKIPPED, List.of(), null, 0L, c.question(), null);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.run.RetrievalEvaluatorTest"
```
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/run/RetrievalEvaluator.java backend/src/test/java/com/youthfit/eval/run/RetrievalEvaluatorTest.java
git commit -m "feat(be): eval 케이스 실행기 — trace 기반 스니펫 판정 (#162)"
```

---

### Task 6: report 패키지 — JSON 리포트 작성 + 콘솔 요약

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/report/CaseResultRow.java`
- Create: `backend/src/main/java/com/youthfit/eval/report/ScenarioReport.java`
- Create: `backend/src/main/java/com/youthfit/eval/report/EvalRunReport.java`
- Create: `backend/src/main/java/com/youthfit/eval/report/EvalReportWriter.java`
- Test: `backend/src/test/java/com/youthfit/eval/report/EvalReportWriterTest.java`

**Interfaces:**
- Consumes: `CaseResult`, `ScenarioMetrics`, `CaseStatus` (Task 2), `EffectiveConfig` (rag 기존)
- Produces:
  - `record CaseResultRow(String caseId, String status, String questionType, String effectiveQuestion, Integer firstRelevantRank, Double top1Distance, long tookMs)` + `static CaseResultRow from(CaseResult r)`
  - `record ScenarioReport(String scenario, EffectiveConfig effectiveConfig, ScenarioMetrics metrics, List<CaseResultRow> cases)`
  - `record EvalRunReport(String label, String executedAt, String datasetPath, int datasetVersion, List<ScenarioReport> scenarios)`
  - `EvalReportWriter.write(EvalRunReport report, Path reportDir) : Path` — `<reportDir>/<yyyyMMdd-HHmmss>-<label>.json` 저장, pretty print
  - `EvalReportWriter.printSummary(EvalRunReport report)` — 콘솔(logger) 요약 테이블

- [ ] **Step 1: 실패하는 테스트 작성**

`EvalReportWriterTest.java`:

```java
package com.youthfit.eval.report;

import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.eval.run.ScenarioMetrics;
import com.youthfit.eval.run.TypeMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalReportWriter")
class EvalReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("리포트를 <timestamp>-<label>.json 으로 저장한다")
    void writesReportFile() throws Exception {
        TypeMetrics overall = new TypeMetrics(2, Map.of(1, 0.5, 3, 1.0, 5, 1.0, 10, 1.0), 0.75);
        ScenarioMetrics metrics = new ScenarioMetrics("baseline", 3, 2, overall,
                Map.of(EvalQuestionType.KEYWORD, overall), 0.4, 0.7, null, 42.0);
        EvalRunReport report = new EvalRunReport("test-run", "20260702-120000",
                "eval/retrieval-evalset.json", 1,
                List.of(new ScenarioReport("baseline", null, metrics, List.of())));

        Path written = new EvalReportWriter().write(report, tempDir);

        assertThat(written.getFileName().toString()).isEqualTo("20260702-120000-test-run.json");
        String json = Files.readString(written);
        assertThat(json).contains("\"scenario\"");
        assertThat(json).contains("baseline");
        assertThat(json).contains("0.75");
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.report.EvalReportWriterTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`CaseResultRow.java`:

```java
package com.youthfit.eval.report;

import com.youthfit.eval.run.CaseResult;

public record CaseResultRow(
        String caseId,
        String status,
        String questionType,
        String effectiveQuestion,
        Integer firstRelevantRank,
        Double top1Distance,
        long tookMs
) {
    public static CaseResultRow from(CaseResult r) {
        return new CaseResultRow(
                r.evalCase().id(),
                r.status().name(),
                r.evalCase().questionType().name(),
                r.effectiveQuestion(),
                r.firstRelevantRank(),
                r.ranked().isEmpty() ? null : r.ranked().get(0).distance(),
                r.tookMs()
        );
    }
}
```

`ScenarioReport.java`:

```java
package com.youthfit.eval.report;

import com.youthfit.eval.run.ScenarioMetrics;
import com.youthfit.rag.application.dto.result.EffectiveConfig;

import java.util.List;

public record ScenarioReport(
        String scenario,
        EffectiveConfig effectiveConfig,
        ScenarioMetrics metrics,
        List<CaseResultRow> cases
) {}
```

`EvalRunReport.java`:

```java
package com.youthfit.eval.report;

import java.util.List;

public record EvalRunReport(
        String label,
        String executedAt,
        String datasetPath,
        int datasetVersion,
        List<ScenarioReport> scenarios
) {}
```

`EvalReportWriter.java`:

```java
package com.youthfit.eval.report;

import com.youthfit.eval.run.ScenarioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;

public class EvalReportWriter {

    private static final Logger log = LoggerFactory.getLogger(EvalReportWriter.class);

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public Path write(EvalRunReport report, Path reportDir) {
        try {
            Files.createDirectories(reportDir);
            Path target = reportDir.resolve(report.executedAt() + "-" + report.label() + ".json");
            Files.writeString(target, objectMapper.writeValueAsString(report));
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("리포트 저장 실패", e);
        }
    }

    public void printSummary(EvalRunReport report) {
        StringBuilder sb = new StringBuilder("\n=== RAG retrieval 평가 결과: ").append(report.label()).append(" ===\n");
        sb.append(String.format("%-12s %6s %6s %8s %8s %8s %8s %8s %10s %8s%n",
                "scenario", "total", "ok", "R@1", "R@3", "R@5", "R@10", "MRR", "negFP", "avgMs"));
        for (ScenarioReport s : report.scenarios()) {
            ScenarioMetrics m = s.metrics();
            sb.append(String.format("%-12s %6d %6d %8.3f %8.3f %8.3f %8.3f %8.3f %10s %8.1f%n",
                    m.scenario(), m.totalCases(), m.okCases(),
                    m.overall().recallAtK().getOrDefault(1, 0.0),
                    m.overall().recallAtK().getOrDefault(3, 0.0),
                    m.overall().recallAtK().getOrDefault(5, 0.0),
                    m.overall().recallAtK().getOrDefault(10, 0.0),
                    m.overall().mrrAt10(),
                    m.negativeFalsePositiveRate() == null ? "-" : String.format("%.3f", m.negativeFalsePositiveRate()),
                    m.avgTookMs()));
        }
        log.info(sb.toString());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.report.EvalReportWriterTest"
```
Expected: PASS (1 test)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/report backend/src/test/java/com/youthfit/eval/report
git commit -m "feat(be): eval JSON 리포트·콘솔 요약 (#162)"
```

---

### Task 7: generate 모드 — LLM 역질문 생성·스니펫 검증·NEGATIVE 풀

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/config/EvalProperties.java`
- Create: `backend/src/main/java/com/youthfit/eval/generate/GeneratedEvalQuestion.java`
- Create: `backend/src/main/java/com/youthfit/eval/generate/EvalQuestionLlm.java`
- Create: `backend/src/main/java/com/youthfit/eval/generate/OpenAiEvalQuestionClient.java`
- Create: `backend/src/main/java/com/youthfit/eval/generate/NegativeQuestionPool.java`
- Create: `backend/src/main/java/com/youthfit/eval/generate/EvalCaseGenerateService.java`
- Test: `backend/src/test/java/com/youthfit/eval/generate/OpenAiEvalQuestionClientParseTest.java`
- Test: `backend/src/test/java/com/youthfit/eval/generate/EvalCaseGenerateServiceTest.java`

**Interfaces:**
- Consumes: Task 1 dataset 모델, `PolicyRepository.findAllByFilters(...)`, `PolicyDocumentRepository.findByPolicyIdOrderByChunkIndex(...)`, `SourceType`
- Produces:
  - `record EvalProperties(String datasetPath, String candidatePath, String cacheDir, String reportDir, Boolean runnerEnabled, Generate generate)` + nested `record Generate(String model, int maxTokens, String apiKey, int maxPerSource)` — `@ConfigurationProperties(prefix = "youthfit.eval")`. `runnerEnabled` 는 통합 테스트에서 EvalRunner 자동 실행을 끄는 스위치 (null 은 true 취급)
  - `record GeneratedEvalQuestion(String question, EvalQuestionType questionType, String snippet)`
  - `interface EvalQuestionLlm { List<GeneratedEvalQuestion> generateQuestions(String policyTitle, List<String> chunkContents); }`
  - `OpenAiEvalQuestionClient implements EvalQuestionLlm` (`@Profile("eval") @Component`) + `static List<GeneratedEvalQuestion> parseQuestions(String content)` (테스트용 package-private 아님 — public static)
  - `NegativeQuestionPool.pick(Long policyId) : String` (static)
  - `EvalCaseGenerateService` (`@Profile("eval") @Service`) — `generateCandidates(boolean confirm, Integer maxPerSourceOverride) : Path` (candidate JSON 경로 반환. confirm=false 면 dry-run: 대상 목록만 로그로 출력하고 파일 미작성, null 반환)

- [ ] **Step 1: 실패하는 테스트 작성**

`OpenAiEvalQuestionClientParseTest.java`:

```java
package com.youthfit.eval.generate;

import com.youthfit.eval.dataset.EvalQuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiEvalQuestionClient.parseQuestions")
class OpenAiEvalQuestionClientParseTest {

    @Test
    @DisplayName("JSON 배열 응답을 파싱한다 (코드펜스 허용)")
    void parsesJsonArrayWithCodeFence() {
        String content = """
                ```json
                [
                  {"question": "지원 금액은 얼마인가요?", "questionType": "KEYWORD", "snippet": "월 20만원을 지원"},
                  {"question": "나도 받을 수 있어?", "questionType": "COLLOQUIAL", "snippet": "만 19세~34세 청년"}
                ]
                ```
                """;

        List<GeneratedEvalQuestion> result = OpenAiEvalQuestionClient.parseQuestions(content);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).questionType()).isEqualTo(EvalQuestionType.KEYWORD);
        assertThat(result.get(1).question()).isEqualTo("나도 받을 수 있어?");
    }

    @Test
    @DisplayName("파싱 불가·빈 응답은 빈 리스트")
    void returnsEmptyOnGarbage() {
        assertThat(OpenAiEvalQuestionClient.parseQuestions("응답이 JSON 이 아님")).isEmpty();
        assertThat(OpenAiEvalQuestionClient.parseQuestions(null)).isEmpty();
    }
}
```

`EvalCaseGenerateServiceTest.java`:

```java
package com.youthfit.eval.generate;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalDataset;
import com.youthfit.eval.dataset.EvalDatasetLoader;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("EvalCaseGenerateService")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalCaseGenerateServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;
    @Mock private EvalQuestionLlm evalQuestionLlm;

    @TempDir
    Path tempDir;

    private EvalCaseGenerateService service;

    private Policy policy(Long id, String title) {
        Policy p = mock(Policy.class);
        given(p.getId()).willReturn(id);
        given(p.getTitle()).willReturn(title);
        return p;
    }

    private PolicyDocument chunk(int index, String content) {
        PolicyDocument d = mock(PolicyDocument.class);
        given(d.getChunkIndex()).willReturn(index);
        given(d.getContent()).willReturn(content);
        return d;
    }

    @BeforeEach
    void setUp() {
        EvalProperties props = new EvalProperties(
                tempDir.resolve("evalset.json").toString(),
                tempDir.resolve("candidate.json").toString(),
                tempDir.resolve("cache").toString(),
                tempDir.resolve("reports").toString(),
                true,
                new EvalProperties.Generate("gpt-4o-mini", 1200, "test-key", 10));
        service = new EvalCaseGenerateService(policyRepository, policyDocumentRepository,
                evalQuestionLlm, props);

        Policy p = policy(1L, "청년 월세 지원");
        given(policyRepository.findAllByFilters(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(policyRepository.findAllByFilters(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(com.youthfit.policy.domain.model.SourceType.YOUTH_CENTER), any()))
                .willReturn(new PageImpl<>(List.of(p)));
        given(policyRepository.findById(1L)).willReturn(Optional.of(p));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L))
                .willReturn(List.of(chunk(0, "지원 대상: 만 19세~34세 청년. 월 20만원을 지원합니다.")));
    }

    @Test
    @DisplayName("dry-run(confirm=false)은 LLM 을 호출하지 않는다")
    void dryRunDoesNotCallLlm() {
        Path result = service.generateCandidates(false, null);

        assertThat(result).isNull();
        verify(evalQuestionLlm, never()).generateQuestions(anyString(), anyList());
    }

    @Test
    @DisplayName("스니펫이 청크 원문에 없으면 후보에서 제외한다 (환각 방지)")
    void dropsHallucinatedSnippets() throws Exception {
        given(evalQuestionLlm.generateQuestions(anyString(), anyList())).willReturn(List.of(
                new GeneratedEvalQuestion("지원 금액은?", EvalQuestionType.KEYWORD, "월 20만원을 지원"),
                new GeneratedEvalQuestion("환각 질문?", EvalQuestionType.KEYWORD, "원문에 없는 문장")));

        Path candidatePath = service.generateCandidates(true, null);

        EvalDataset candidate = new EvalDatasetLoader().load(candidatePath);
        List<String> questions = candidate.cases().stream()
                .map(c -> c.question()).toList();
        assertThat(questions).contains("지원 금액은?");
        assertThat(questions).doesNotContain("환각 질문?");
    }

    @Test
    @DisplayName("정책마다 NEGATIVE 케이스 1건이 배정된다 (expectedSnippets 빈 배열)")
    void assignsNegativeCasePerPolicy() throws Exception {
        given(evalQuestionLlm.generateQuestions(anyString(), anyList())).willReturn(List.of());

        Path candidatePath = service.generateCandidates(true, null);

        EvalDataset candidate = new EvalDatasetLoader().load(candidatePath);
        List<com.youthfit.eval.dataset.EvalCase> negatives = candidate.cases().stream()
                .filter(c -> c.questionType() == EvalQuestionType.NEGATIVE)
                .toList();
        assertThat(negatives).hasSize(1);
        assertThat(negatives.get(0).expectedSnippets()).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.generate.*"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`EvalProperties.java`:

```java
package com.youthfit.eval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youthfit.eval")
public record EvalProperties(
        String datasetPath,
        String candidatePath,
        String cacheDir,
        String reportDir,
        Boolean runnerEnabled,
        Generate generate
) {
    /** 통합 테스트에서 EvalRunner 자동 실행을 끄는 스위치. 미설정(null)은 true 취급. */
    public boolean isRunnerEnabled() {
        return runnerEnabled == null || runnerEnabled;
    }

    public record Generate(String model, int maxTokens, String apiKey, int maxPerSource) {}
}
```

`GeneratedEvalQuestion.java`:

```java
package com.youthfit.eval.generate;

import com.youthfit.eval.dataset.EvalQuestionType;

public record GeneratedEvalQuestion(String question, EvalQuestionType questionType, String snippet) {}
```

`EvalQuestionLlm.java`:

```java
package com.youthfit.eval.generate;

import java.util.List;

/**
 * 청크 내용으로 답할 수 있는 평가용 질문을 역생성하는 포트.
 * 실패 시 빈 리스트를 반환한다 (호출자는 해당 정책을 스킵).
 */
public interface EvalQuestionLlm {

    List<GeneratedEvalQuestion> generateQuestions(String policyTitle, List<String> chunkContents);
}
```

`OpenAiEvalQuestionClient.java` (OpenAiQueryRewriter 패턴을 따른다 — RestClient 직접 생성, 실패 시 빈 결과):

```java
package com.youthfit.eval.generate;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("eval")
public class OpenAiEvalQuestionClient implements EvalQuestionLlm {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEvalQuestionClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper PARSE_MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            당신은 한국 청년 정책 검색 시스템의 평가 데이터를 만드는 어시스턴트입니다.
            주어진 정책 본문 청크들로 "답할 수 있는" 질문을 만드세요.

            규칙:
            1. 질문 3개: KEYWORD 2개(정책명·금액·기관 등 정확 용어 포함), COLLOQUIAL 1개(구어체·짧은 표현).
            2. 각 질문에 snippet 을 붙이세요. snippet 은 반드시 주어진 청크 원문에서
               "한 글자도 바꾸지 않고 그대로" 복사한 1~2문장이어야 합니다. 새로 쓰지 마세요.
            3. 출력은 JSON 배열만 — 다른 텍스트·설명 금지.
               [{"question": "...", "questionType": "KEYWORD", "snippet": "..."}]
            """;

    private final EvalProperties properties;
    private final RestClient restClient;

    public OpenAiEvalQuestionClient(EvalProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public List<GeneratedEvalQuestion> generateQuestions(String policyTitle, List<String> chunkContents) {
        String userMessage = "정책명: " + policyTitle + "\n\n청크:\n" + String.join("\n---\n", chunkContents);
        Map<String, Object> requestBody = Map.of(
                "model", properties.generate().model(),
                "max_tokens", properties.generate().maxTokens(),
                "temperature", 0.5,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.generate().apiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = PARSE_MAPPER.readTree(responseBody);
            JsonNode choices = root.get("choices");
            String content = (choices != null && !choices.isEmpty())
                    ? choices.get(0).path("message").path("content").asText("")
                    : "";
            return parseQuestions(content);
        } catch (Exception e) {
            log.warn("역질문 생성 실패, 정책 스킵: title=\"{}\", error={}", policyTitle, e.toString());
            return List.of();
        }
    }

    /** 코드펜스를 벗기고 JSON 배열을 파싱한다. 실패 시 빈 리스트. */
    public static List<GeneratedEvalQuestion> parseQuestions(String content) {
        if (content == null || content.isBlank()) return List.of();
        String stripped = content.strip()
                .replaceAll("^```(json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        try {
            JsonNode array = PARSE_MAPPER.readTree(stripped);
            if (!array.isArray()) return List.of();
            List<GeneratedEvalQuestion> out = new ArrayList<>();
            for (JsonNode node : array) {
                String question = node.path("question").asText("");
                String typeName = node.path("questionType").asText("");
                String snippet = node.path("snippet").asText("");
                if (question.isBlank() || snippet.isBlank()) continue;
                EvalQuestionType type;
                try {
                    type = EvalQuestionType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                out.add(new GeneratedEvalQuestion(question, type, snippet));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

`NegativeQuestionPool.java`:

```java
package com.youthfit.eval.generate;

import java.util.List;

/**
 * 대부분의 정책 원문에 근거가 없는 공용 NEGATIVE 질문 풀.
 * LLM 호출 없이 정책당 1개를 결정적으로 배정한다 (재실행 시 동일 배정).
 */
public final class NegativeQuestionPool {

    private static final List<String> QUESTIONS = List.of(
            "신청하면 며칠 만에 지급되나요?",
            "탈락하면 재심사를 요청할 수 있나요?",
            "외국인 배우자도 같이 신청 가능한가요?",
            "지원금을 받으면 세금 신고를 해야 하나요?",
            "작년에 받았으면 올해 또 받을 수 있나요?",
            "대리인이 대신 신청해도 되나요?"
    );

    private NegativeQuestionPool() {
    }

    public static String pick(Long policyId) {
        return QUESTIONS.get(Math.floorMod(policyId.intValue(), QUESTIONS.size()));
    }
}
```

`EvalCaseGenerateService.java`:

```java
package com.youthfit.eval.generate;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalDataset;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.eval.dataset.SnippetMatcher;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * generate 모드: 소스별 정책을 샘플링해 LLM 역질문 후보를 만들고
 * candidate JSON 으로 출력한다. 사람 검수 후 retrieval-evalset.json 으로 확정.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
public class EvalCaseGenerateService {

    private static final Logger log = LoggerFactory.getLogger(EvalCaseGenerateService.class);
    private static final int CHUNKS_PER_POLICY = 3;

    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final EvalQuestionLlm evalQuestionLlm;
    private final EvalProperties properties;

    /**
     * @param confirm false 면 dry-run — 대상 정책·예상 LLM 호출 수만 출력하고 종료 (null 반환)
     * @param maxPerSourceOverride null 이면 properties 값 사용
     * @return 작성된 candidate JSON 경로 (dry-run 은 null)
     */
    public Path generateCandidates(boolean confirm, Integer maxPerSourceOverride) {
        int maxPerSource = maxPerSourceOverride != null
                ? maxPerSourceOverride
                : properties.generate().maxPerSource();

        // 증분 생성: 기존 candidate 에 이미 있는 정책은 스킵 (재실행 시 중복 호출 방지)
        List<EvalCase> existingCases = loadExistingCandidateCases();
        java.util.Set<Long> coveredPolicyIds = existingCases.stream()
                .map(EvalCase::policyId)
                .collect(java.util.stream.Collectors.toSet());

        List<Policy> targets = sampleTargets(maxPerSource).stream()
                .filter(p -> !coveredPolicyIds.contains(p.getId()))
                .toList();
        log.info("generate 대상: 신규 정책 {}건 (기존 candidate {}건 스킵), 예상 LLM 호출 {}회 (모델 {})",
                targets.size(), coveredPolicyIds.size(), targets.size(), properties.generate().model());

        if (!confirm) {
            targets.forEach(p -> log.info("  - id={}, title={}", p.getId(), p.getTitle()));
            log.info("dry-run 종료. 실제 생성하려면 --eval.confirm=true 를 추가하세요.");
            return null;
        }

        List<EvalCase> cases = new ArrayList<>();
        List<String> failedPolicies = new ArrayList<>();
        for (Policy policy : targets) {
            List<PolicyDocument> chunks =
                    policyDocumentRepository.findByPolicyIdOrderByChunkIndex(policy.getId());
            List<String> contents = chunks.stream()
                    .limit(CHUNKS_PER_POLICY)
                    .map(PolicyDocument::getContent)
                    .toList();

            List<GeneratedEvalQuestion> generated =
                    evalQuestionLlm.generateQuestions(policy.getTitle(), contents);
            if (generated.isEmpty()) {
                failedPolicies.add(policy.getId() + ":" + policy.getTitle());
            }

            int q = 1;
            for (GeneratedEvalQuestion g : generated) {
                boolean snippetVerified = contents.stream()
                        .anyMatch(content -> SnippetMatcher.containsSnippet(content, g.snippet()));
                if (!snippetVerified) {
                    log.warn("스니펫 원문 불일치로 제외 (환각 의심): policyId={}, question=\"{}\"",
                            policy.getId(), g.question());
                    continue;
                }
                cases.add(new EvalCase(
                        "p" + policy.getId() + "-q" + q++,
                        policy.getId(), policy.getTitle(), g.question(), g.questionType(),
                        List.of(g.snippet()), null));
            }

            cases.add(new EvalCase(
                    "p" + policy.getId() + "-neg",
                    policy.getId(), policy.getTitle(),
                    NegativeQuestionPool.pick(policy.getId()),
                    EvalQuestionType.NEGATIVE, List.of(), null));
        }

        if (!failedPolicies.isEmpty()) {
            log.warn("생성 실패(스킵) 정책 {}건: {}", failedPolicies.size(), failedPolicies);
        }

        List<EvalCase> merged = new ArrayList<>(existingCases);
        merged.addAll(cases);
        return writeCandidate(new EvalDataset(1, "text-embedding-3-small", merged));
    }

    private List<EvalCase> loadExistingCandidateCases() {
        Path path = Path.of(properties.candidatePath());
        if (!Files.exists(path)) {
            return List.of();
        }
        return new com.youthfit.eval.dataset.EvalDatasetLoader().load(path).cases();
    }

    private List<Policy> sampleTargets(int maxPerSource) {
        List<Policy> targets = new ArrayList<>();
        for (SourceType source : SourceType.values()) {
            List<Policy> withChunks = policyRepository
                    .findAllByFilters(null, null, null, source, PageRequest.of(0, maxPerSource * 3))
                    .getContent().stream()
                    .filter(p -> !policyDocumentRepository
                            .findByPolicyIdOrderByChunkIndex(p.getId()).isEmpty())
                    .limit(maxPerSource)
                    .toList();
            targets.addAll(withChunks);
        }
        return targets;
    }

    private Path writeCandidate(EvalDataset dataset) {
        Path path = Path.of(properties.candidatePath());
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .build();
            Files.writeString(path, mapper.writeValueAsString(dataset));
            log.info("candidate 작성 완료: {} ({}케이스). 검수 후 {} 로 확정하세요.",
                    path.toAbsolutePath(), dataset.cases().size(), properties.datasetPath());
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("candidate 저장 실패: " + path, e);
        }
    }
}
```

참고: `Policy.getId()` 는 `@Getter` 로 존재 (Entity 규칙). `JsonMapper.builder()` 가 없으면 Task 6 의 대체 표기와 동일하게 처리.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.generate.*"
```
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/config backend/src/main/java/com/youthfit/eval/generate backend/src/test/java/com/youthfit/eval/generate
git commit -m "feat(be): eval generate 모드 — LLM 역질문 생성·스니펫 검증·NEGATIVE 풀 (#162)"
```

---

### Task 8: EvalRunner + application-eval.yml — 모드 디스패치·와이어링

**Files:**
- Create: `backend/src/main/java/com/youthfit/eval/EvalRunner.java`
- Create: `backend/src/main/resources/application-eval.yml`
- Create: `backend/eval/.gitignore`
- Test: `backend/src/test/java/com/youthfit/eval/EvalRunnerTest.java`

**Interfaces:**
- Consumes: Task 1~7 전부 + `QnaProperties.relevanceDistanceThreshold()`, `ConfigurableApplicationContext`
- Produces: `EvalRunner` (`@Profile("eval") @Component implements ApplicationRunner`) — args 파싱: `--eval.mode=generate|run`, `--eval.scenarios=a,b`, `--eval.label=...`, `--eval.confirm=true`, `--eval.max-per-source=N`

- [ ] **Step 1: 실패하는 테스트 작성**

`EvalRunnerTest.java` (모드 디스패치만 검증 — 실행 로직은 Task 5~7 에서 검증됨):

```java
package com.youthfit.eval;

import com.youthfit.eval.generate.EvalCaseGenerateService;
import com.youthfit.eval.run.RetrievalEvaluator;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@DisplayName("EvalRunner")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalRunnerTest {

    @InjectMocks
    private EvalRunner runner;

    @Mock private EvalCaseGenerateService generateService;
    @Mock private RetrievalEvaluator retrievalEvaluator;
    @Mock private com.youthfit.eval.config.EvalProperties evalProperties;
    @Mock private QnaProperties qnaProperties;

    @Test
    @DisplayName("--eval.mode=generate 는 generate 서비스로 디스패치 (confirm 기본 false)")
    void dispatchesGenerate() throws Exception {
        runner.dispatch(new DefaultApplicationArguments("--eval.mode=generate"));

        verify(generateService).generateCandidates(false, null);
    }

    @Test
    @DisplayName("--eval.mode 누락은 명확한 예외")
    void failsOnMissingMode() {
        assertThatThrownBy(() -> runner.dispatch(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--eval.mode");
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.EvalRunnerTest"
```
Expected: 컴파일 에러 — FAIL

- [ ] **Step 3: 구현**

`EvalRunner.java`:

```java
package com.youthfit.eval;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalDataset;
import com.youthfit.eval.dataset.EvalDatasetLoader;
import com.youthfit.eval.generate.EvalCaseGenerateService;
import com.youthfit.eval.report.CaseResultRow;
import com.youthfit.eval.report.EvalReportWriter;
import com.youthfit.eval.report.EvalRunReport;
import com.youthfit.eval.report.ScenarioReport;
import com.youthfit.eval.run.CaseResult;
import com.youthfit.eval.run.CaseStatus;
import com.youthfit.eval.run.EvalMetricsCalculator;
import com.youthfit.eval.run.EvalScenario;
import com.youthfit.eval.run.QueryEmbeddingFileCache;
import com.youthfit.eval.run.RetrievalEvaluator;
import com.youthfit.eval.run.ScenarioMetrics;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * eval 프로파일 진입점.
 *
 * generate: SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=generate --eval.confirm=true'
 * run:      SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=run --eval.scenarios=baseline,hybrid-on'
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final EvalCaseGenerateService generateService;
    private final RetrievalEvaluator retrievalEvaluator;
    private final EvalProperties evalProperties;
    private final QnaProperties qnaProperties;
    private ConfigurableApplicationContext context;

    @org.springframework.beans.factory.annotation.Autowired
    public void setContext(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!evalProperties.isRunnerEnabled()) {
            return; // 통합 테스트 컨텍스트 — 자동 실행·System.exit 방지
        }
        int exitCode = 0;
        try {
            dispatch(args);
        } catch (Exception e) {
            log.error("eval 실행 실패", e);
            exitCode = 1;
        } finally {
            if (context != null) {
                int springCode = SpringApplication.exit(context, () -> 0);
                System.exit(exitCode != 0 ? exitCode : springCode);
            }
        }
    }

    void dispatch(ApplicationArguments args) {
        String mode = firstOption(args, "eval.mode");
        if (mode == null) {
            throw new IllegalArgumentException("--eval.mode=generate|run 을 지정하세요.");
        }
        switch (mode) {
            case "generate" -> {
                boolean confirm = Boolean.parseBoolean(firstOption(args, "eval.confirm"));
                String maxPerSource = firstOption(args, "eval.max-per-source");
                generateService.generateCandidates(confirm,
                        maxPerSource == null ? null : Integer.parseInt(maxPerSource));
            }
            case "run" -> runEvaluation(args);
            default -> throw new IllegalArgumentException("알 수 없는 --eval.mode: " + mode);
        }
    }

    private void runEvaluation(ApplicationArguments args) {
        String scenariosArg = firstOption(args, "eval.scenarios");
        List<EvalScenario> scenarios = (scenariosArg == null ? List.of("baseline") : List.of(scenariosArg.split(",")))
                .stream().map(String::trim).map(EvalScenario::of).toList();
        String label = firstOption(args, "eval.label");
        if (label == null) {
            label = scenarios.stream().map(EvalScenario::name).reduce((a, b) -> a + "+" + b).orElse("run");
        }

        EvalDataset dataset = new EvalDatasetLoader().load(Path.of(evalProperties.datasetPath()));
        QueryEmbeddingFileCache cache = new QueryEmbeddingFileCache(
                Path.of(evalProperties.cacheDir()), dataset.embeddingModel());
        EvalMetricsCalculator calculator = new EvalMetricsCalculator();
        double negativeThreshold = qnaProperties.relevanceDistanceThreshold();

        List<ScenarioReport> scenarioReports = new ArrayList<>();
        for (EvalScenario scenario : scenarios) {
            log.info("시나리오 실행: {} ({}케이스)", scenario.name(), dataset.cases().size());
            List<CaseResult> results = dataset.cases().stream()
                    .map(c -> retrievalEvaluator.evaluate(c, scenario, cache))
                    .toList();
            cache.save(); // 시나리오마다 저장 — 중간 실패해도 캐시 보존

            ScenarioMetrics metrics = calculator.calculate(scenario.name(), results, negativeThreshold);
            double okRatio = results.isEmpty() ? 0.0 : metrics.okCases() / (double) results.size();
            if (okRatio < 0.9) {
                log.warn("성공 케이스 {}% (< 90%) — 평가셋 정비가 필요할 수 있습니다. STALE/NO_CHUNKS 확인.",
                        Math.round(okRatio * 100));
            }

            scenarioReports.add(new ScenarioReport(
                    scenario.name(),
                    results.stream().filter(r -> r.status() == CaseStatus.OK)
                            .findFirst().map(CaseResult::effective).orElse(null),
                    metrics,
                    results.stream().map(CaseResultRow::from).toList()));
        }

        EvalRunReport report = new EvalRunReport(label, LocalDateTime.now().format(TS),
                evalProperties.datasetPath(), dataset.version(), scenarioReports);
        EvalReportWriter writer = new EvalReportWriter();
        Path written = writer.write(report, Path.of(evalProperties.reportDir()));
        writer.printSummary(report);
        log.info("리포트 저장: {}", written.toAbsolutePath());
    }

    private String firstOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
```

`application-eval.yml`:

```yaml
# eval 프로파일 — RAG retrieval 평가 러너 전용 (#162)
# 웹서버를 띄우지 않아 로컬 compose backend(8080)와 포트 충돌 없음.
spring:
  main:
    web-application-type: none

youthfit:
  eval:
    dataset-path: eval/retrieval-evalset.json          # cwd = backend/
    candidate-path: eval/retrieval-evalset.candidate.json
    cache-dir: eval/cache
    report-dir: eval/reports
    runner-enabled: true                               # 통합 테스트에서 false 로 끔
    generate:
      model: gpt-4o-mini
      max-tokens: 1200
      api-key: ${OPENAI_API_KEY:}
      max-per-source: 10
```

`backend/eval/.gitignore`:

```
cache/
```

- [ ] **Step 4: 테스트 통과 + 전체 빌드 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.*"
```
Expected: PASS (eval 전체)

```bash
cd /Users/taetaetae/IdeaProjects/youthfit && set -a && source .env && set +a && cd backend && ./gradlew build
```
Expected: BUILD SUCCESSFUL (docker compose postgres·redis 필요)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/eval/EvalRunner.java backend/src/main/resources/application-eval.yml backend/eval/.gitignore backend/src/test/java/com/youthfit/eval/EvalRunnerTest.java
git commit -m "feat(be): eval 러너 — generate/run 모드 디스패치·eval 프로파일 (#162)"
```

---

### Task 9: Testcontainers 통합 스모크 — run 파이프라인 end-to-end

**Files:**
- Test: `backend/src/test/java/com/youthfit/eval/EvalRunIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1~8 전부. 시딩 패턴은 `backend/src/test/java/com/youthfit/admin/rag/AdminRagPreviewIntegrationTest.java` 참고 (pgvector Testcontainers + `@MockBean EmbeddingProvider` + 1536차원 더미 벡터 + `policyDocumentJpaRepository.saveAll`).
- Produces: 없음 (검증 전용)

- [ ] **Step 1: 통합 테스트 작성**

`EvalRunIntegrationTest.java` — `AdminRagPreviewIntegrationTest` 의 컨테이너·시딩 셋업을 그대로 복사해 시작한다 (`@SpringBootTest` + `@ActiveProfiles("eval")` + `@ServiceConnection` PostgreSQLContainer(`pgvector/pgvector:pg17`) + `CREATE EXTENSION vector` + `ddl-auto=create-drop`). 다른 점만 아래에 명시:

```java
package com.youthfit.eval;

// imports 는 AdminRagPreviewIntegrationTest 를 따르고 아래 사용 클래스를 추가

@DisplayName("eval run 파이프라인 통합 스모크")
@SpringBootTest
@ActiveProfiles("eval")
class EvalRunIntegrationTest {

    // AdminRagPreviewIntegrationTest 와 동일한 컨테이너·EmbeddingProvider MockBean 셋업

    @Autowired
    private RetrievalEvaluator retrievalEvaluator;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("시딩된 정책·청크로 baseline 케이스가 OK 판정된다")
    void evaluatesSeededCase() {
        // 1) Policy 1건 저장 (title = "청년 월세 지원") + PolicyDocument 청크 2건 저장
        //    (내용 중 하나에 "대학 재학생은 신청 대상에서 제외됩니다." 포함, 1536차원 더미 임베딩)
        // 2) EvalCase: policyId=저장된 id, policyTitle="청년 월세 지원",
        //    question="재학생도 되나요?", KEYWORD, expectedSnippets=["대학 재학생은 신청 대상에서 제외"]
        // 3) QueryEmbeddingFileCache(tempDir, "text-embedding-3-small") 생성
        // 4) retrievalEvaluator.evaluate(case, EvalScenario.of("baseline"), cache)

        CaseResult result = retrievalEvaluator.evaluate(evalCase, EvalScenario.of("baseline"), cache);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.firstRelevantRank()).isNotNull();
        assertThat(result.effective()).isNotNull();
    }

    @Test
    @DisplayName("hybrid-on 시나리오도 예외 없이 실행된다 (pg_trgm 경로)")
    void hybridScenarioRuns() {
        CaseResult result = retrievalEvaluator.evaluate(evalCase, EvalScenario.of("hybrid-on"), cache);

        assertThat(result.status()).isIn(CaseStatus.OK, CaseStatus.SKIPPED);
        // pg_trgm extension 이 없으면 RagSearchService 가 vector 폴백하므로 OK 가 기대값.
        // extension 생성이 필요하면 컨테이너 init 에 CREATE EXTENSION pg_trgm 추가.
        assertThat(result.status()).isEqualTo(CaseStatus.OK);
    }
}
```

구현 시 `AdminRagPreviewIntegrationTest` 를 열어 셋업(컨테이너, `@DynamicPropertySource`, 시딩 헬퍼 `document(...)`, MockBean)을 재사용한다. **`@SpringBootTest` 는 ApplicationRunner 를 실행하므로** 반드시 `@SpringBootTest(properties = "youthfit.eval.runner-enabled=false")` 로 선언한다 — Task 8 의 `EvalRunner.run()` 첫 줄 가드(`EvalProperties.isRunnerEnabled()`)가 이 프로퍼티를 읽어 자동 실행과 `System.exit` 를 막는다.

- [ ] **Step 2: 테스트 실행**

```bash
cd backend && ./gradlew test --tests "com.youthfit.eval.EvalRunIntegrationTest"
```
Expected: PASS (2 tests, Docker 데몬 필요)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/test/java/com/youthfit/eval/EvalRunIntegrationTest.java
git commit -m "test(be): eval run 파이프라인 Testcontainers 통합 스모크 (#162)"
```

---

### Task 10: 문서·마무리 — ARCHITECTURE.md·전체 빌드·PR 준비

**Files:**
- Modify: `docs/ARCHITECTURE.md` (모듈 목록 섹션에 eval 항목 추가)
- Modify: `CLAUDE.md` (루트 — "백엔드 모듈" 목록에 eval 한 줄 추가)
- Modify: `docs/superpowers/specs/2026-07-02-rag-retrieval-evalset-design.md` → `DONE_` 접두사 (PR 시점)

**Interfaces:**
- Consumes: 없음
- Produces: 없음 (문서)

- [ ] **Step 1: ARCHITECTURE.md 에 eval 모듈 추가**

`docs/ARCHITECTURE.md` 의 백엔드 모듈 목록에 다음 한 줄 추가 (형식은 기존 항목과 동일하게):

```
- `eval` RAG retrieval 평가 도구 (dev 전용, @Profile("eval") — prod 미기동). 평가셋 generate/run 러너. #162
```

루트 `CLAUDE.md` 의 "백엔드 모듈" 목록에도 같은 취지로 추가:

```
- `eval` RAG retrieval 평가 러너 (dev 전용, eval 프로파일)
```

- [ ] **Step 2: 전체 빌드 최종 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit && set -a && source .env && set +a && cd backend && ./gradlew build
```
Expected: BUILD SUCCESSFUL (docker compose postgres·redis 기동 상태)

- [ ] **Step 3: 커밋**

```bash
git add docs/ARCHITECTURE.md CLAUDE.md
git commit -m "docs: eval 모듈 추가 — 아키텍처·모듈 목록 반영 (#162)"
```

- [ ] **Step 4: PR 생성은 create-pr 스킬로**

create-pr 스킬 절차를 따른다 (`/cr` 셀프 리뷰 → 스펙 `DONE_` 처리 → `[BE]` 태그 PR). PR 본문에 "평가셋 데이터 자체(retrieval-evalset.json)는 이 PR 에 포함되지 않음 — 머지 후 generate 모드 실행·검수로 별도 커밋" 을 명시한다.

---

## 구현 뒤 수동 후속 작업 (코드 밖 — 플랜 범위 외, 참고용)

1. 로컬 compose 기동 + 시드 데이터 확인 (`policy_document` 채워져 있어야 함)
2. `SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=generate'` (dry-run 확인) → `--eval.confirm=true` 로 실제 생성
3. candidate JSON 사람 검수 → `backend/eval/retrieval-evalset.json` 확정 커밋
4. `--eval.mode=run --eval.scenarios=baseline` 첫 리포트 생성 → 커밋 → #163 실험 시작 가능
