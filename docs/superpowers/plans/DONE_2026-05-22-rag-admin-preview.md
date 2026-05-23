# RAG Admin Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 한 화면에서 운영 yml(baseline) vs 후보 설정(candidate)의 RAG 하이브리드 검색 결과를 비교할 수 있는 read-only 튜닝 도구를 추가한다.

**Architecture:** `rag.application.service.RagSearchService` 에 `searchRelevantChunksWithTrace(...)` 오버로드를 추가하고, 신규 `admin/rag` 패키지의 `RagPreviewService` 가 baseline/candidate 두 번을 같은 임베딩으로 순차 호출. 운영/어드민이 동일 코드 경로를 타도록 강제(drift 0). 어드민 전용 UI 는 React `/admin/rag-preview` 라우트에서 좌·우 분할 비교.

**Tech Stack:** Spring Boot 4 / Java 21 (백엔드), Mockito + JUnit 5, testcontainers(Postgres+pgvector, Redis), React 19 + TanStack Query + RHF + Zod (프론트), Vitest + MSW + RTL.

**Spec:** `docs/superpowers/specs/2026-05-22-rag-admin-preview-design.md`

**API Path:** `POST /api/v1/admin/rag/preview` (프로젝트 컨벤션 따름 — 스펙의 `/api/admin/...` 표기에서 변경)

---

## File Structure

### 신규 (backend)
```
backend/src/main/java/com/youthfit/rag/application/dto/
├── command/HybridSearchOverrides.java          # nullable 필드 record
└── result/
    ├── EffectiveConfig.java                    # baseline + overrides 병합 결과
    ├── MergedChunk.java                        # rrfScore + rank + distance + preview
    └── RagSearchTrace.java                     # vector/trigram/merged + tookMs + effective

backend/src/main/java/com/youthfit/admin/rag/
├── application/
│   ├── service/
│   │   ├── RagPreviewService.java
│   │   ├── RagPreviewRateLimiter.java          # Redis fixed-window 60s
│   │   └── RankChangeCalculator.java
│   └── dto/
│       ├── command/
│       │   ├── RagPreviewCommand.java
│       │   └── HybridOverrideCommand.java
│       └── result/
│           ├── RagPreviewResult.java
│           ├── PreviewSideResult.java
│           └── RankChangeResult.java
└── presentation/
    ├── controller/
    │   ├── AdminRagPreviewApi.java
    │   └── AdminRagPreviewController.java
    └── dto/
        ├── request/
        │   ├── RagPreviewRequest.java
        │   └── HybridOverrideRequest.java
        └── response/
            ├── RagPreviewResponse.java
            ├── PreviewSideResponse.java
            ├── ChunkSummaryResponse.java
            ├── MergedChunkResponse.java
            ├── EffectiveConfigResponse.java
            └── RankChangeResponse.java
```

### 신규 (backend tests)
```
backend/src/test/java/com/youthfit/rag/application/dto/result/EffectiveConfigTest.java
backend/src/test/java/com/youthfit/admin/rag/application/service/RankChangeCalculatorTest.java
backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewServiceTest.java
backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiterTest.java
backend/src/test/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewControllerTest.java   # @WebMvcTest
backend/src/test/java/com/youthfit/admin/rag/AdminRagPreviewIntegrationTest.java                          # @SpringBootTest + testcontainers
```

### 수정 (backend)
```
backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java
  - searchRelevantChunksWithTrace(SearchChunksCommand, float[], HybridSearchOverrides) 신규
  - 내부 hybridSearch(...) 를 EffectiveConfig 인자 받도록 분해
backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java
  - WithTrace 메서드 시나리오 5개 추가
```

### 신규 (frontend)
```
frontend/src/apis/adminRag.api.ts
frontend/src/hooks/mutations/useRagPreview.ts
frontend/src/components/admin/rag-preview/
├── RagPreviewControls.tsx
├── CandidateConfigForm.tsx
├── BaselineConfigPanel.tsx
├── ResultTabs.tsx
├── ChunkRow.tsx
└── RankDeltaBadge.tsx
frontend/src/pages/admin/AdminRagPreviewPage.tsx
```

### 신규 (frontend tests)
```
frontend/src/components/admin/rag-preview/__tests__/
├── CandidateConfigForm.test.tsx
├── RankDeltaBadge.test.tsx
├── ResultTabs.test.tsx
└── ChunkRow.test.tsx
frontend/src/hooks/mutations/__tests__/useRagPreview.test.ts
frontend/src/pages/admin/__tests__/AdminRagPreviewPage.test.tsx
```

### 수정 (frontend)
```
frontend/src/App.tsx (혹은 routes 정의 파일) — /admin/rag-preview 라우트 추가
frontend/src/components/admin/AdminControls.tsx (혹은 admin 사이드바) — 메뉴 항목 추가
```

---

## Task 1: rag 도메인 — EffectiveConfig record + 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/application/dto/result/EffectiveConfig.java`
- Create: `backend/src/main/java/com/youthfit/rag/application/dto/command/HybridSearchOverrides.java`
- Create: `backend/src/test/java/com/youthfit/rag/application/dto/result/EffectiveConfigTest.java`

- [ ] **Step 1: HybridSearchOverrides record 작성**

```java
// backend/src/main/java/com/youthfit/rag/application/dto/command/HybridSearchOverrides.java
package com.youthfit.rag.application.dto.command;

/**
 * 어드민 미리보기 도구에서 baseline (yml) 위에 부분 덮어쓰기할 값.
 * 모든 필드 nullable — null 이면 baseline 값 사용.
 */
public record HybridSearchOverrides(
        Boolean hybridEnabled,
        Integer topNPerSearch,
        Integer rrfK,
        Double trigramThreshold,
        Boolean keywordBoostEnabled,
        Integer maxKeywords
) {}
```

- [ ] **Step 2: EffectiveConfig 실패 테스트 작성**

```java
// backend/src/test/java/com/youthfit/rag/application/dto/result/EffectiveConfigTest.java
package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectiveConfig")
class EffectiveConfigTest {

    private final HybridSearchProperties baseHybrid =
            new HybridSearchProperties(true, 20, 60, 0.10);
    private final KeywordBoostProperties baseKeyword =
            new KeywordBoostProperties(true, 5, List.of());

    @Test
    @DisplayName("overrides 가 null 이면 baseline 값을 그대로 반영한다")
    void nullOverrides_usesBaseline() {
        EffectiveConfig cfg = EffectiveConfig.from(baseHybrid, baseKeyword, null);

        assertThat(cfg.hybridEnabled()).isTrue();
        assertThat(cfg.topNPerSearch()).isEqualTo(20);
        assertThat(cfg.rrfK()).isEqualTo(60);
        assertThat(cfg.trigramThreshold()).isEqualTo(0.10);
        assertThat(cfg.keywordBoostEnabled()).isTrue();
        assertThat(cfg.maxKeywords()).isEqualTo(5);
    }

    @Test
    @DisplayName("overrides 일부 필드만 지정되면 해당 필드만 덮어쓴다")
    void partialOverrides_replacesOnlySpecifiedFields() {
        HybridSearchOverrides ov = new HybridSearchOverrides(
                null, null, 30, null, null, 7);

        EffectiveConfig cfg = EffectiveConfig.from(baseHybrid, baseKeyword, ov);

        assertThat(cfg.rrfK()).isEqualTo(30);
        assertThat(cfg.maxKeywords()).isEqualTo(7);
        assertThat(cfg.topNPerSearch()).isEqualTo(20);   // baseline 그대로
        assertThat(cfg.hybridEnabled()).isTrue();         // baseline 그대로
    }

    @Test
    @DisplayName("overrides 모든 필드 지정 시 baseline 무관하게 override")
    void fullOverrides_replacesAll() {
        HybridSearchOverrides ov = new HybridSearchOverrides(
                false, 30, 30, 0.15, false, 7);

        EffectiveConfig cfg = EffectiveConfig.from(baseHybrid, baseKeyword, ov);

        assertThat(cfg.hybridEnabled()).isFalse();
        assertThat(cfg.topNPerSearch()).isEqualTo(30);
        assertThat(cfg.rrfK()).isEqualTo(30);
        assertThat(cfg.trigramThreshold()).isEqualTo(0.15);
        assertThat(cfg.keywordBoostEnabled()).isFalse();
        assertThat(cfg.maxKeywords()).isEqualTo(7);
    }
}
```

- [ ] **Step 3: 테스트 실행 (FAIL)**

```bash
cd backend
./gradlew test --tests "com.youthfit.rag.application.dto.result.EffectiveConfigTest"
```
Expected: 컴파일 에러 (EffectiveConfig 미존재).

- [ ] **Step 4: EffectiveConfig record 구현**

```java
// backend/src/main/java/com/youthfit/rag/application/dto/result/EffectiveConfig.java
package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.infrastructure.config.HybridSearchProperties;
import com.youthfit.rag.infrastructure.config.KeywordBoostProperties;

public record EffectiveConfig(
        boolean hybridEnabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold,
        boolean keywordBoostEnabled,
        int maxKeywords
) {
    public static EffectiveConfig from(HybridSearchProperties h, KeywordBoostProperties k) {
        return from(h, k, null);
    }

    public static EffectiveConfig from(HybridSearchProperties h,
                                       KeywordBoostProperties k,
                                       HybridSearchOverrides ov) {
        boolean hybrid       = pickBool(ov == null ? null : ov.hybridEnabled(),       h.enabled());
        int     topN         = pickInt (ov == null ? null : ov.topNPerSearch(),       h.topNPerSearch());
        int     rrfK         = pickInt (ov == null ? null : ov.rrfK(),                h.rrfK());
        double  trigramTh    = pickDouble(ov == null ? null : ov.trigramThreshold(),  h.trigramThreshold());
        boolean kwBoost      = pickBool(ov == null ? null : ov.keywordBoostEnabled(), k.enabled());
        int     maxKw        = pickInt (ov == null ? null : ov.maxKeywords(),         k.maxKeywords());
        return new EffectiveConfig(hybrid, topN, rrfK, trigramTh, kwBoost, maxKw);
    }

    private static boolean pickBool(Boolean ov, boolean base) { return ov == null ? base : ov; }
    private static int     pickInt (Integer ov, int base)     { return ov == null ? base : ov; }
    private static double  pickDouble(Double ov, double base) { return ov == null ? base : ov; }
}
```

- [ ] **Step 5: 테스트 실행 (PASS)**

```bash
./gradlew test --tests "com.youthfit.rag.application.dto.result.EffectiveConfigTest"
```
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/application/dto/command/HybridSearchOverrides.java \
        backend/src/main/java/com/youthfit/rag/application/dto/result/EffectiveConfig.java \
        backend/src/test/java/com/youthfit/rag/application/dto/result/EffectiveConfigTest.java
git commit -m "feat(rag): add EffectiveConfig + HybridSearchOverrides for admin preview tool"
```

---

## Task 2: rag 도메인 — MergedChunk + RagSearchTrace record

**Files:**
- Create: `backend/src/main/java/com/youthfit/rag/application/dto/result/MergedChunk.java`
- Create: `backend/src/main/java/com/youthfit/rag/application/dto/result/RagSearchTrace.java`

- [ ] **Step 1: MergedChunk record 작성**

```java
// backend/src/main/java/com/youthfit/rag/application/dto/result/MergedChunk.java
package com.youthfit.rag.application.dto.result;

/**
 * RRF 머지 후 한 청크의 표시 정보.
 * distance 는 ReciprocalRankFusion 이 유지한 SimilarChunk.distance 를 그대로 보존 (vector 우선).
 */
public record MergedChunk(
        long chunkId,
        int chunkIndex,
        double distance,
        double rrfScore,
        int rank,
        String preview
) {}
```

- [ ] **Step 2: RagSearchTrace record 작성**

```java
// backend/src/main/java/com/youthfit/rag/application/dto/result/RagSearchTrace.java
package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.domain.model.SimilarChunk;

import java.util.List;

public record RagSearchTrace(
        EffectiveConfig effective,
        List<SimilarChunk> vectorTopN,
        List<SimilarChunk> trigramTopN,
        List<MergedChunk> merged,
        List<String> usedKeywords,
        long tookMs
) {}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/application/dto/result/MergedChunk.java \
        backend/src/main/java/com/youthfit/rag/application/dto/result/RagSearchTrace.java
git commit -m "feat(rag): add MergedChunk + RagSearchTrace DTOs"
```

---

## Task 3: RagSearchService 에 searchRelevantChunksWithTrace 추가

**Files:**
- Modify: `backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java`
- Modify: `backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java`

### Step 1: 신규 테스트 시나리오 작성 (FAIL)

- [ ] **Step 1: RagSearchServiceTest 에 WithTrace nested 클래스 추가**

기존 파일의 마지막 `@Nested class HybridPath` 뒤, helper 메서드 앞에 다음을 추가:

```java
    @Nested
    @DisplayName("searchRelevantChunksWithTrace - 어드민 미리보기")
    class WithTrace {

        @Test
        @DisplayName("overrides null 이면 운영 properties 값으로 검색한다")
        void nullOverrides_usesBaselineProperties() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.10);
            given(keywordBoostProperties.enabled()).willReturn(false);
            given(keywordBoostProperties.maxKeywords()).willReturn(5);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(20)))
                    .willReturn(vec);
            given(policyDocumentRepository.findTopByTrigram(eq(1L), eq("주거"), eq(0.10), eq(20)))
                    .willReturn(List.of());
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec);

            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, null);

            assertThat(trace.effective().rrfK()).isEqualTo(60);
            assertThat(trace.effective().topNPerSearch()).isEqualTo(20);
            assertThat(trace.vectorTopN()).hasSize(1);
            assertThat(trace.merged()).hasSize(1);
            assertThat(trace.merged().get(0).rank()).isEqualTo(1);
            assertThat(trace.tookMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("overrides 의 rrfK 만 지정하면 그 값으로 RRF 가 호출된다")
        void overridesRrfK_passedToRrf() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.10);
            given(keywordBoostProperties.enabled()).willReturn(false);
            given(keywordBoostProperties.maxKeywords()).willReturn(5);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(20)))
                    .willReturn(vec);
            given(policyDocumentRepository.findTopByTrigram(eq(1L), eq("주거"), eq(0.10), eq(20)))
                    .willReturn(List.of());
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(30), eq(10)))
                    .willReturn(vec);

            HybridSearchOverrides ov = new HybridSearchOverrides(
                    null, null, 30, null, null, null);
            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, ov);

            assertThat(trace.effective().rrfK()).isEqualTo(30);
            verify(reciprocalRankFusion).merge(eq(vec), eq(List.of()), eq(30), eq(10));
        }

        @Test
        @DisplayName("overrides.hybridEnabled=false 면 vector-only 경로, trigramTopN 은 빈 리스트")
        void hybridDisabledOverride_returnsVectorOnly() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            // baseline 은 hybrid=true 인데 override 로 끔
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.10);
            given(keywordBoostProperties.enabled()).willReturn(false);
            given(keywordBoostProperties.maxKeywords()).willReturn(5);

            List<SimilarChunk> vec = List.of(
                    new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(10)))
                    .willReturn(vec);

            HybridSearchOverrides ov = new HybridSearchOverrides(
                    false, null, null, null, null, null);
            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, ov);

            assertThat(trace.effective().hybridEnabled()).isFalse();
            assertThat(trace.trigramTopN()).isEmpty();
            assertThat(trace.merged()).hasSize(1);
            verify(policyDocumentRepository, never()).findTopByTrigram(any(), any(), anyDouble(), anyInt());
            verify(reciprocalRankFusion, never()).merge(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("overrides.keywordBoostEnabled=false 면 keywords 빈 리스트로 호출")
        void keywordBoostDisabledOverride_passesEmptyKeywords() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거 지원");
            float[] emb = new float[]{0.1f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.10);
            given(keywordBoostProperties.enabled()).willReturn(true);   // baseline 은 켜져있음
            given(keywordBoostProperties.maxKeywords()).willReturn(5);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), eq(List.of()), eq(20)))
                    .willReturn(vec);
            given(policyDocumentRepository.findTopByTrigram(eq(1L), eq("주거 지원"), eq(0.10), eq(20)))
                    .willReturn(List.of());
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec);

            HybridSearchOverrides ov = new HybridSearchOverrides(
                    null, null, null, null, false, null);
            ragSearchService.searchRelevantChunksWithTrace(cmd, emb, ov);

            verify(keywordExtractor, never()).extract(any());
            verify(policyDocumentRepository).findSimilarByEmbedding(
                    eq(1L), eq(emb), eq(List.of()), eq(20));
        }

        @Test
        @DisplayName("trigram 예외 발생 시에도 trace.trigramTopN 은 빈 리스트로 반환")
        void trigramFailure_returnsEmptyTrigramTopN() {
            SearchChunksCommand cmd = new SearchChunksCommand(1L, "주거");
            float[] emb = new float[]{0.1f};
            given(hybridSearchProperties.enabled()).willReturn(true);
            given(hybridSearchProperties.topNPerSearch()).willReturn(20);
            given(hybridSearchProperties.rrfK()).willReturn(60);
            given(hybridSearchProperties.trigramThreshold()).willReturn(0.10);
            given(keywordBoostProperties.enabled()).willReturn(false);
            given(keywordBoostProperties.maxKeywords()).willReturn(5);

            List<SimilarChunk> vec = List.of(new SimilarChunk(10L, 1L, 0, "v", null, null, null, 0.2));
            given(policyDocumentRepository.findSimilarByEmbedding(eq(1L), eq(emb), any(), eq(20)))
                    .willReturn(vec);
            given(policyDocumentRepository.findTopByTrigram(eq(1L), eq("주거"), eq(0.10), eq(20)))
                    .willThrow(new RuntimeException("trigram down"));
            given(reciprocalRankFusion.merge(eq(vec), eq(List.of()), eq(60), eq(10)))
                    .willReturn(vec);

            RagSearchTrace trace =
                    ragSearchService.searchRelevantChunksWithTrace(cmd, emb, null);

            assertThat(trace.trigramTopN()).isEmpty();
            assertThat(trace.vectorTopN()).hasSize(1);
        }
    }
```

또한 파일 상단 import 에 다음 추가:
```java
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
```

- [ ] **Step 2: 테스트 실행 (FAIL)**

```bash
./gradlew test --tests "com.youthfit.rag.application.service.RagSearchServiceTest"
```
Expected: 컴파일 에러 — `searchRelevantChunksWithTrace` 미존재.

### Step 2: RagSearchService 구현

- [ ] **Step 3: RagSearchService 에 신규 메서드 추가 (기존 유지)**

`RagSearchService.java` 의 기존 `hybridSearch(...)` 를 다음과 같이 분해하고, public 메서드를 추가한다.

파일 상단 import 추가:
```java
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
```

기존 `private List<PolicyDocumentChunkResult> hybridSearch(...)` 를 다음으로 **교체**:

```java
    @Transactional(readOnly = true)
    public RagSearchTrace searchRelevantChunksWithTrace(
            SearchChunksCommand command,
            float[] precomputedEmbedding,
            @Nullable HybridSearchOverrides overrides
    ) {
        long start = System.currentTimeMillis();
        EffectiveConfig effective =
                EffectiveConfig.from(hybridSearchProperties, keywordBoostProperties, overrides);

        List<String> keywords = effective.keywordBoostEnabled()
                ? keywordExtractor.extract(command.query())
                : List.of();

        if (!effective.hybridEnabled()) {
            // vector-only 경로 — DEFAULT_TOP_K 컷
            List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
                    command.policyId(), precomputedEmbedding, keywords, DEFAULT_TOP_K);
            List<MergedChunk> merged = toMergedChunksFromVectorOnly(vec);
            return new RagSearchTrace(effective, vec, List.of(), merged, keywords,
                    System.currentTimeMillis() - start);
        }

        int topN = effective.topNPerSearch();
        int k = effective.rrfK();
        double threshold = effective.trigramThreshold();

        List<SimilarChunk> vec = policyDocumentRepository.findSimilarByEmbedding(
                command.policyId(), precomputedEmbedding, keywords, topN);

        List<SimilarChunk> tri;
        try {
            tri = policyDocumentRepository.findTopByTrigram(
                    command.policyId(), command.query(), threshold, topN);
        } catch (RuntimeException e) {
            log.warn("trigram 쿼리 실패, vector 결과로 폴백: policyId={}, error={}",
                    command.policyId(), e.toString());
            tri = List.of();
        }

        List<SimilarChunk> mergedSimilar =
                reciprocalRankFusion.merge(vec, tri, k, DEFAULT_TOP_K);
        List<MergedChunk> merged = toMergedChunks(mergedSimilar);

        return new RagSearchTrace(effective, vec, tri, merged, keywords,
                System.currentTimeMillis() - start);
    }

    private List<MergedChunk> toMergedChunks(List<SimilarChunk> chunks) {
        List<MergedChunk> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            SimilarChunk c = chunks.get(i);
            // V1: rrfScore 는 RRF 결과에서 별도 전달 안 되므로 0.0 으로 둠 (UI 는 distance 위주).
            // 추후 ReciprocalRankFusion 이 score 도 함께 반환하도록 확장 가능.
            out.add(new MergedChunk(c.id(), c.index(), c.distance(), 0.0, i + 1,
                    truncate(c.content(), 500)));
        }
        return out;
    }

    private List<MergedChunk> toMergedChunksFromVectorOnly(List<SimilarChunk> chunks) {
        return toMergedChunks(chunks);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
```

기존 운영용 `hybridSearch(...)` 메서드는 그대로 두되, 내부 로직이 위 신규 메서드와 동일하므로 다음과 같이 **위임**으로 변경:

```java
    private List<PolicyDocumentChunkResult> hybridSearch(
            SearchChunksCommand command, float[] embedding, List<String> keywords
    ) {
        RagSearchTrace trace = searchRelevantChunksWithTrace(command, embedding, null);
        if (trace.merged().isEmpty()) {
            log.info("hybrid 검색 결과 없음, 키워드 폴백 수행: policyId={}", command.policyId());
            return fallbackKeywordSearch(command);
        }
        // PolicyDocumentChunkResult 는 MergedChunk 가 아닌 SimilarChunk 기반이므로
        // RRF 결과에서 chunkId 로 SimilarChunk 를 다시 매핑해야 한다 → 비효율 방지를 위해
        // 운영 경로는 기존 로직을 유지하고, WithTrace 만 신규 path 사용.
        // ↑ 따라서 이 위임 방식은 사용하지 않고, 기존 hybridSearch 를 그대로 둔다.
        throw new UnsupportedOperationException("위 위임 방식은 적용하지 않음 — 아래 주석 참조");
    }
```

> **결정**: 위임으로 합치면 SimilarChunk → MergedChunk → SimilarChunk 왕복이 생긴다. drift 위험을 막는 핵심은 "**같은 알고리즘** 이 양쪽에 적용되는 것" 이지, "동일 메서드 호출" 이 아니다. 따라서 기존 `hybridSearch(...)` 는 **그대로 두고**, `searchRelevantChunksWithTrace(...)` 는 같은 알고리즘을 별도 메서드로 구현한다. 알고리즘이 한 곳에 모여 있도록 **헬퍼**(아래) 로 추출한다.

기존 `hybridSearch(...)` 를 **삭제하지 말고 유지**한 채, 두 곳이 공유하는 헬퍼는 만들지 않는다 (코드 16줄 중복 vs drift 안전성 트레이드오프 — 후자 선택). 단, 알고리즘 변경 시 두 곳을 함께 갱신해야 한다는 주석을 양쪽에 단다:

`searchRelevantChunksWithTrace` 의 hybrid 분기 위에:
```java
        // ⚠ 알고리즘 동기화: 아래 로직은 hybridSearch(...) 와 1:1 대응해야 한다.
        //   둘을 합치지 않는 이유는 운영은 SimilarChunk 컷, 어드민은 MergedChunk 트레이스가 필요하기 때문.
```

`hybridSearch` 위에도 동일 주석:
```java
        // ⚠ 알고리즘 동기화: searchRelevantChunksWithTrace 의 hybrid 분기와 1:1 대응해야 한다.
```

- [ ] **Step 4: 테스트 실행 (PASS)**

```bash
./gradlew test --tests "com.youthfit.rag.application.service.RagSearchServiceTest"
```
Expected: 기존 + 신규 시나리오 모두 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/rag/application/service/RagSearchService.java \
        backend/src/test/java/com/youthfit/rag/application/service/RagSearchServiceTest.java
git commit -m "feat(rag): expose searchRelevantChunksWithTrace for admin preview"
```

---

## Task 4: RankChangeCalculator + 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/service/RankChangeCalculator.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/dto/result/RankChangeResult.java`
- Create: `backend/src/test/java/com/youthfit/admin/rag/application/service/RankChangeCalculatorTest.java`

- [ ] **Step 1: RankChangeResult record 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/dto/result/RankChangeResult.java
package com.youthfit.admin.rag.application.dto.result;

/**
 * baselineRank/candidateRank 중 한쪽이 null 이면 NEW/DROPPED.
 * delta: candidateRank - baselineRank (음수=상승, 양수=하락). 한쪽 null 이면 null.
 */
public record RankChangeResult(
        long chunkId,
        Integer baselineRank,
        Integer candidateRank,
        Integer delta
) {
    public static RankChangeResult newcomer(long chunkId, int candidateRank) {
        return new RankChangeResult(chunkId, null, candidateRank, null);
    }
    public static RankChangeResult dropped(long chunkId, int baselineRank) {
        return new RankChangeResult(chunkId, baselineRank, null, null);
    }
    public static RankChangeResult moved(long chunkId, int baselineRank, int candidateRank) {
        return new RankChangeResult(chunkId, baselineRank, candidateRank,
                candidateRank - baselineRank);
    }
}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// backend/src/test/java/com/youthfit/admin/rag/application/service/RankChangeCalculatorTest.java
package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.result.RankChangeResult;
import com.youthfit.rag.application.dto.result.MergedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankChangeCalculator")
class RankChangeCalculatorTest {

    private final RankChangeCalculator calc = new RankChangeCalculator();

    private MergedChunk chunk(long id, int rank) {
        return new MergedChunk(id, rank - 1, 0.1, 0.0, rank, "preview");
    }

    @Test
    @DisplayName("동일 결과면 빈 리스트")
    void identical_returnsEmpty() {
        List<MergedChunk> same = List.of(chunk(1, 1), chunk(2, 2));
        assertThat(calc.compute(same, same)).isEmpty();
    }

    @Test
    @DisplayName("candidate 에만 있는 chunk 는 NEW (baselineRank null)")
    void candidateOnly_marksNew() {
        List<MergedChunk> baseline = List.of(chunk(1, 1));
        List<MergedChunk> candidate = List.of(chunk(1, 1), chunk(2, 2));
        List<RankChangeResult> changes = calc.compute(baseline, candidate);
        assertThat(changes)
                .extracting(RankChangeResult::chunkId, RankChangeResult::baselineRank,
                            RankChangeResult::candidateRank)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(2L, null, 2));
    }

    @Test
    @DisplayName("baseline 에만 있는 chunk 는 DROPPED (candidateRank null)")
    void baselineOnly_marksDropped() {
        List<MergedChunk> baseline = List.of(chunk(1, 1), chunk(2, 2));
        List<MergedChunk> candidate = List.of(chunk(1, 1));
        List<RankChangeResult> changes = calc.compute(baseline, candidate);
        assertThat(changes)
                .extracting(RankChangeResult::chunkId, RankChangeResult::candidateRank)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(2L, null));
    }

    @Test
    @DisplayName("양쪽에 있고 순위가 바뀌면 delta = candidate - baseline")
    void bothSidesDifferentRank_computesDelta() {
        List<MergedChunk> baseline = List.of(chunk(1, 1), chunk(2, 2));
        List<MergedChunk> candidate = List.of(chunk(2, 1), chunk(1, 2));
        List<RankChangeResult> changes = calc.compute(baseline, candidate);
        assertThat(changes)
                .extracting(RankChangeResult::chunkId, RankChangeResult::delta)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 1),
                        org.assertj.core.groups.Tuple.tuple(2L, -1));
    }

    @Test
    @DisplayName("양쪽 빈 결과 → 빈 리스트")
    void bothEmpty_returnsEmpty() {
        assertThat(calc.compute(List.of(), List.of())).isEmpty();
    }
}
```

- [ ] **Step 3: 테스트 실행 (FAIL)**

```bash
./gradlew test --tests "com.youthfit.admin.rag.application.service.RankChangeCalculatorTest"
```
Expected: 컴파일 에러 — RankChangeCalculator 미존재.

- [ ] **Step 4: RankChangeCalculator 구현**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/service/RankChangeCalculator.java
package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.result.RankChangeResult;
import com.youthfit.rag.application.dto.result.MergedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RankChangeCalculator {

    public List<RankChangeResult> compute(List<MergedChunk> baseline, List<MergedChunk> candidate) {
        Map<Long, Integer> baseRank = new HashMap<>();
        for (MergedChunk c : baseline) baseRank.put(c.chunkId(), c.rank());
        Map<Long, Integer> candRank = new HashMap<>();
        for (MergedChunk c : candidate) candRank.put(c.chunkId(), c.rank());

        List<RankChangeResult> out = new ArrayList<>();
        // moved + dropped
        for (Map.Entry<Long, Integer> e : baseRank.entrySet()) {
            Integer cand = candRank.get(e.getKey());
            if (cand == null) {
                out.add(RankChangeResult.dropped(e.getKey(), e.getValue()));
            } else if (!cand.equals(e.getValue())) {
                out.add(RankChangeResult.moved(e.getKey(), e.getValue(), cand));
            }
        }
        // new
        for (Map.Entry<Long, Integer> e : candRank.entrySet()) {
            if (!baseRank.containsKey(e.getKey())) {
                out.add(RankChangeResult.newcomer(e.getKey(), e.getValue()));
            }
        }
        return out;
    }
}
```

- [ ] **Step 5: 테스트 실행 (PASS)**

```bash
./gradlew test --tests "com.youthfit.admin.rag.application.service.RankChangeCalculatorTest"
```
Expected: 5 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/rag/
git commit -m "feat(admin/rag): add RankChangeCalculator for baseline/candidate diff"
```

---

## Task 5: RagPreviewRateLimiter + 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiter.java`
- Create: `backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiterTest.java`

- [ ] **Step 1: 인터페이스 + Redis 구현 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiter.java
package com.youthfit.admin.rag.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window 60초 / admin user. 한도 초과 시 false 반환.
 * 키 형식: rate-limit:admin-rag-preview:{userId}
 */
@Component
@RequiredArgsConstructor
public class RagPreviewRateLimiter {

    static final int LIMIT_PER_MINUTE = 30;
    static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public boolean tryAcquire(long userId) {
        String key = "rate-limit:admin-rag-preview:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        return count != null && count <= LIMIT_PER_MINUTE;
    }
}
```

- [ ] **Step 2: 단위 테스트 작성**

```java
// backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiterTest.java
package com.youthfit.admin.rag.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RagPreviewRateLimiter")
@ExtendWith(MockitoExtension.class)
class RagPreviewRateLimiterTest {

    @InjectMocks private RagPreviewRateLimiter limiter;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;

    @Test
    @DisplayName("첫 호출 시 INCR=1 + EXPIRE 호출, true 반환")
    void firstCall_setsExpiry_andAllows() {
        given(redis.opsForValue()).willReturn(ops);
        given(ops.increment("rate-limit:admin-rag-preview:42")).willReturn(1L);

        assertThat(limiter.tryAcquire(42L)).isTrue();
        verify(redis).expire(eq("rate-limit:admin-rag-preview:42"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("count <= 30 이면 true, expire 재호출 없음")
    void underLimit_allowsWithoutExpire() {
        given(redis.opsForValue()).willReturn(ops);
        given(ops.increment(any())).willReturn(30L);

        assertThat(limiter.tryAcquire(42L)).isTrue();
        verify(redis, never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("count > 30 이면 false")
    void overLimit_denies() {
        given(redis.opsForValue()).willReturn(ops);
        given(ops.increment(any())).willReturn(31L);

        assertThat(limiter.tryAcquire(42L)).isFalse();
    }
}
```

- [ ] **Step 3: 테스트 실행 (PASS)**

```bash
./gradlew test --tests "com.youthfit.admin.rag.application.service.RagPreviewRateLimiterTest"
```
Expected: 3 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiter.java \
        backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimiterTest.java
git commit -m "feat(admin/rag): add Redis fixed-window rate limiter (30/min/user)"
```

---

## Task 6: application DTO (Command/Result) 추가

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/dto/command/HybridOverrideCommand.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/dto/command/RagPreviewCommand.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/dto/result/PreviewSideResult.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/dto/result/RagPreviewResult.java`

- [ ] **Step 1: HybridOverrideCommand 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/dto/command/HybridOverrideCommand.java
package com.youthfit.admin.rag.application.dto.command;

public record HybridOverrideCommand(
        Boolean hybridEnabled,
        Integer topNPerSearch,
        Integer rrfK,
        Double trigramThreshold,
        Boolean keywordBoostEnabled,
        Integer maxKeywords
) {}
```

- [ ] **Step 2: RagPreviewCommand 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/dto/command/RagPreviewCommand.java
package com.youthfit.admin.rag.application.dto.command;

public record RagPreviewCommand(
        long userId,
        long policyId,
        String query,
        HybridOverrideCommand candidate
) {}
```

- [ ] **Step 3: PreviewSideResult 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/dto/result/PreviewSideResult.java
package com.youthfit.admin.rag.application.dto.result;

import com.youthfit.rag.application.dto.result.RagSearchTrace;

/**
 * baseline / candidate 한 쪽의 trace 를 그대로 노출.
 */
public record PreviewSideResult(RagSearchTrace trace) {}
```

- [ ] **Step 4: RagPreviewResult 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/dto/result/RagPreviewResult.java
package com.youthfit.admin.rag.application.dto.result;

import java.util.List;

public record RagPreviewResult(
        long policyId,
        String query,
        List<String> extractedKeywords,
        PreviewSideResult baseline,
        PreviewSideResult candidate,
        List<RankChangeResult> rankChanges
) {}
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/rag/application/dto/
git commit -m "feat(admin/rag): add Command/Result DTOs for preview service"
```

---

## Task 7: RagPreviewService 구현 + 테스트

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewService.java`
- Create: `backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewServiceTest.java`

- [ ] **Step 1: 도메인 예외 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimitException.java
package com.youthfit.admin.rag.application.service;

public class RagPreviewRateLimitException extends RuntimeException {
    public RagPreviewRateLimitException() {
        super("RAG preview rate limit exceeded (30/min)");
    }
}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewServiceTest.java
package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.command.HybridOverrideCommand;
import com.youthfit.admin.rag.application.dto.command.RagPreviewCommand;
import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.MergedChunk;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("RagPreviewService")
@ExtendWith(MockitoExtension.class)
class RagPreviewServiceTest {

    @InjectMocks private RagPreviewService service;
    @Mock private RagSearchService ragSearchService;
    @Mock private EmbeddingProvider embeddingProvider;
    @Mock private RankChangeCalculator rankChangeCalculator;
    @Mock private RagPreviewRateLimiter rateLimiter;

    private RagSearchTrace traceWith(List<MergedChunk> merged) {
        return new RagSearchTrace(
                new EffectiveConfig(true, 20, 60, 0.10, true, 5),
                List.of(), List.of(), merged, List.of("주거"), 100L);
    }

    @Test
    @DisplayName("embedding 은 한 번만 호출되고 baseline/candidate 양쪽 trace 가 반환된다")
    void embedsOnce_returnsBothTraces() {
        given(rateLimiter.tryAcquire(42L)).willReturn(true);
        float[] emb = new float[]{0.1f};
        given(embeddingProvider.embed("주거")).willReturn(emb);

        RagSearchTrace baseline = traceWith(List.of(new MergedChunk(1L, 0, 0.1, 0.0, 1, "a")));
        RagSearchTrace candidate = traceWith(List.of(new MergedChunk(2L, 1, 0.2, 0.0, 1, "b")));
        given(ragSearchService.searchRelevantChunksWithTrace(any(), eq(emb), any()))
                .willReturn(baseline, candidate);
        given(rankChangeCalculator.compute(any(), any())).willReturn(List.of());

        RagPreviewCommand cmd = new RagPreviewCommand(42L, 1L, "주거",
                new HybridOverrideCommand(null, null, 30, null, null, null));

        RagPreviewResult result = service.preview(cmd);

        verify(embeddingProvider, times(1)).embed("주거");
        verify(ragSearchService, times(2)).searchRelevantChunksWithTrace(any(), eq(emb), any());
        assertThat(result.baseline().trace()).isSameAs(baseline);
        assertThat(result.candidate().trace()).isSameAs(candidate);
    }

    @Test
    @DisplayName("baseline 호출은 overrides=null, candidate 는 변환된 HybridSearchOverrides 로 호출된다")
    void passesNullForBaseline_andOverridesForCandidate() {
        given(rateLimiter.tryAcquire(42L)).willReturn(true);
        given(embeddingProvider.embed(any())).willReturn(new float[]{0.1f});
        given(ragSearchService.searchRelevantChunksWithTrace(any(), any(), any()))
                .willReturn(traceWith(List.of()), traceWith(List.of()));
        given(rankChangeCalculator.compute(any(), any())).willReturn(List.of());

        HybridOverrideCommand candidate = new HybridOverrideCommand(null, null, 30, null, null, 7);
        service.preview(new RagPreviewCommand(42L, 1L, "주거", candidate));

        // baseline → null
        verify(ragSearchService).searchRelevantChunksWithTrace(any(), any(), eq((HybridSearchOverrides) null));
        // candidate → 변환된 record
        verify(ragSearchService).searchRelevantChunksWithTrace(any(), any(),
                eq(new HybridSearchOverrides(null, null, 30, null, null, 7)));
    }

    @Test
    @DisplayName("rate limit 초과 시 RagPreviewRateLimitException 발생, search 호출 없음")
    void overRateLimit_throws() {
        given(rateLimiter.tryAcquire(42L)).willReturn(false);

        RagPreviewCommand cmd = new RagPreviewCommand(42L, 1L, "주거",
                new HybridOverrideCommand(null, null, null, null, null, null));

        assertThatThrownBy(() -> service.preview(cmd))
                .isInstanceOf(RagPreviewRateLimitException.class);
        verify(embeddingProvider, times(0)).embed(any());
    }
}
```

- [ ] **Step 3: 테스트 실행 (FAIL)**

```bash
./gradlew test --tests "com.youthfit.admin.rag.application.service.RagPreviewServiceTest"
```
Expected: 컴파일 에러.

- [ ] **Step 4: RagPreviewService 구현**

```java
// backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewService.java
package com.youthfit.admin.rag.application.service;

import com.youthfit.admin.rag.application.dto.command.HybridOverrideCommand;
import com.youthfit.admin.rag.application.dto.command.RagPreviewCommand;
import com.youthfit.admin.rag.application.dto.result.PreviewSideResult;
import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;
import com.youthfit.admin.rag.application.dto.result.RankChangeResult;
import com.youthfit.rag.application.dto.command.HybridSearchOverrides;
import com.youthfit.rag.application.dto.command.SearchChunksCommand;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import com.youthfit.rag.application.port.EmbeddingProvider;
import com.youthfit.rag.application.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagPreviewService {

    private static final Logger log = LoggerFactory.getLogger(RagPreviewService.class);

    private final RagSearchService ragSearchService;
    private final EmbeddingProvider embeddingProvider;
    private final RankChangeCalculator rankChangeCalculator;
    private final RagPreviewRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public RagPreviewResult preview(RagPreviewCommand cmd) {
        if (!rateLimiter.tryAcquire(cmd.userId())) {
            throw new RagPreviewRateLimitException();
        }

        SearchChunksCommand searchCmd = new SearchChunksCommand(cmd.policyId(), cmd.query());
        float[] embedding = embeddingProvider.embed(cmd.query());

        RagSearchTrace baseline = ragSearchService.searchRelevantChunksWithTrace(
                searchCmd, embedding, null);
        HybridSearchOverrides overrides = toOverrides(cmd.candidate());
        RagSearchTrace candidate = ragSearchService.searchRelevantChunksWithTrace(
                searchCmd, embedding, overrides);

        List<RankChangeResult> changes =
                rankChangeCalculator.compute(baseline.merged(), candidate.merged());

        logAudit(cmd, baseline, candidate, changes);

        return new RagPreviewResult(
                cmd.policyId(),
                cmd.query(),
                baseline.usedKeywords(),
                new PreviewSideResult(baseline),
                new PreviewSideResult(candidate),
                changes);
    }

    private HybridSearchOverrides toOverrides(HybridOverrideCommand c) {
        if (c == null) return null;
        return new HybridSearchOverrides(
                c.hybridEnabled(),
                c.topNPerSearch(),
                c.rrfK(),
                c.trigramThreshold(),
                c.keywordBoostEnabled(),
                c.maxKeywords());
    }

    private void logAudit(RagPreviewCommand cmd, RagSearchTrace baseline,
                          RagSearchTrace candidate, List<RankChangeResult> changes) {
        String q = cmd.query() == null ? "" : cmd.query();
        if (q.length() > 200) q = q.substring(0, 200);
        log.info("admin.rag.preview userId={} policyId={} query=\"{}\" "
                        + "baseline={} candidate={} baselineMs={} candidateMs={} "
                        + "baselineHits={} candidateHits={} rankChanges={}",
                cmd.userId(), cmd.policyId(), q,
                baseline.effective(), candidate.effective(),
                baseline.tookMs(), candidate.tookMs(),
                baseline.merged().size(), candidate.merged().size(),
                changes.size());
    }
}
```

- [ ] **Step 5: 테스트 실행 (PASS)**

```bash
./gradlew test --tests "com.youthfit.admin.rag.application.service.RagPreviewServiceTest"
```
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewService.java \
        backend/src/main/java/com/youthfit/admin/rag/application/service/RagPreviewRateLimitException.java \
        backend/src/test/java/com/youthfit/admin/rag/application/service/RagPreviewServiceTest.java
git commit -m "feat(admin/rag): RagPreviewService — baseline+candidate sequential trace"
```

---

## Task 8: Presentation DTO (Request/Response) + 검증

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/request/HybridOverrideRequest.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/request/RagPreviewRequest.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/EffectiveConfigResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/ChunkSummaryResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/MergedChunkResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/PreviewSideResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/RankChangeResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/RagPreviewResponse.java`

- [ ] **Step 1: HybridOverrideRequest 작성 (검증 포함)**

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/request/HybridOverrideRequest.java
package com.youthfit.admin.rag.presentation.dto.request;

import com.youthfit.admin.rag.application.dto.command.HybridOverrideCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record HybridOverrideRequest(
        Boolean hybridEnabled,
        @Min(1) @Max(100) Integer topNPerSearch,
        @Min(1) @Max(500) Integer rrfK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double trigramThreshold,
        Boolean keywordBoostEnabled,
        @Min(0) @Max(20) Integer maxKeywords
) {
    public HybridOverrideCommand toCommand() {
        return new HybridOverrideCommand(
                hybridEnabled, topNPerSearch, rrfK,
                trigramThreshold, keywordBoostEnabled, maxKeywords);
    }
}
```

- [ ] **Step 2: RagPreviewRequest 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/request/RagPreviewRequest.java
package com.youthfit.admin.rag.presentation.dto.request;

import com.youthfit.admin.rag.application.dto.command.RagPreviewCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RagPreviewRequest(
        @NotNull @Positive Long policyId,
        @NotBlank @Size(min = 1, max = 500) String query,
        @Valid HybridOverrideRequest candidate
) {
    public RagPreviewCommand toCommand(long userId) {
        return new RagPreviewCommand(
                userId, policyId, query,
                candidate == null ? null : candidate.toCommand());
    }
}
```

- [ ] **Step 3: Response DTO 작성 (한 번에)**

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/EffectiveConfigResponse.java
package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.rag.application.dto.result.EffectiveConfig;

public record EffectiveConfigResponse(
        boolean hybridEnabled,
        int topNPerSearch,
        int rrfK,
        double trigramThreshold,
        boolean keywordBoostEnabled,
        int maxKeywords
) {
    public static EffectiveConfigResponse from(EffectiveConfig c) {
        return new EffectiveConfigResponse(
                c.hybridEnabled(), c.topNPerSearch(), c.rrfK(),
                c.trigramThreshold(), c.keywordBoostEnabled(), c.maxKeywords());
    }
}
```

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/ChunkSummaryResponse.java
package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.rag.domain.model.SimilarChunk;

public record ChunkSummaryResponse(
        long chunkId,
        int chunkIndex,
        double distance,
        String preview
) {
    public static ChunkSummaryResponse from(SimilarChunk c) {
        String content = c.content();
        String preview = content == null ? ""
                : content.length() <= 500 ? content : content.substring(0, 500);
        return new ChunkSummaryResponse(c.id(), c.index(), c.distance(), preview);
    }
}
```

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/MergedChunkResponse.java
package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.rag.application.dto.result.MergedChunk;

public record MergedChunkResponse(
        long chunkId,
        int chunkIndex,
        double distance,
        double rrfScore,
        int rank,
        String preview
) {
    public static MergedChunkResponse from(MergedChunk c) {
        return new MergedChunkResponse(
                c.chunkId(), c.chunkIndex(), c.distance(), c.rrfScore(), c.rank(), c.preview());
    }
}
```

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/PreviewSideResponse.java
package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.admin.rag.application.dto.result.PreviewSideResult;

import java.util.List;

public record PreviewSideResponse(
        EffectiveConfigResponse config,
        List<ChunkSummaryResponse> vectorTopN,
        List<ChunkSummaryResponse> trigramTopN,
        List<MergedChunkResponse> merged,
        long tookMs
) {
    public static PreviewSideResponse from(PreviewSideResult side) {
        return new PreviewSideResponse(
                EffectiveConfigResponse.from(side.trace().effective()),
                side.trace().vectorTopN().stream().map(ChunkSummaryResponse::from).toList(),
                side.trace().trigramTopN().stream().map(ChunkSummaryResponse::from).toList(),
                side.trace().merged().stream().map(MergedChunkResponse::from).toList(),
                side.trace().tookMs());
    }
}
```

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/RankChangeResponse.java
package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.admin.rag.application.dto.result.RankChangeResult;

public record RankChangeResponse(
        long chunkId,
        Integer baselineRank,
        Integer candidateRank,
        String delta   // "NEW" | "DROPPED" | 정수("+N"/"-N"/"0")
) {
    public static RankChangeResponse from(RankChangeResult r) {
        String d;
        if (r.baselineRank() == null) d = "NEW";
        else if (r.candidateRank() == null) d = "DROPPED";
        else d = (r.delta() > 0 ? "+" : "") + r.delta();
        return new RankChangeResponse(r.chunkId(), r.baselineRank(), r.candidateRank(), d);
    }
}
```

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/dto/response/RagPreviewResponse.java
package com.youthfit.admin.rag.presentation.dto.response;

import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;

import java.util.List;

public record RagPreviewResponse(
        long policyId,
        String query,
        List<String> extractedKeywords,
        PreviewSideResponse baseline,
        PreviewSideResponse candidate,
        DiffResponse diff
) {
    public record DiffResponse(List<RankChangeResponse> rankChanges) {}

    public static RagPreviewResponse from(RagPreviewResult r) {
        return new RagPreviewResponse(
                r.policyId(),
                r.query(),
                r.extractedKeywords(),
                PreviewSideResponse.from(r.baseline()),
                PreviewSideResponse.from(r.candidate()),
                new DiffResponse(r.rankChanges().stream()
                        .map(RankChangeResponse::from).toList()));
    }
}
```

- [ ] **Step 4: 컴파일**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/rag/presentation/dto/
git commit -m "feat(admin/rag): add Request/Response DTOs with bean validation"
```

---

## Task 9: Controller + Api interface

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewApi.java`
- Create: `backend/src/main/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewController.java`

> 인증된 admin 의 userId 를 얻기 위해 기존 프로젝트의 `@AuthenticationPrincipal` 또는 SecurityContext 접근 패턴을 사용한다. 다른 admin 컨트롤러(예: `AdminLlmCostController`)에서 사용자 식별이 어떻게 되는지 검토 후 동일 방식 사용. 본 plan 은 placeholder 로 `SecurityContextHolder` 직접 사용. **실제 구현 시 프로젝트 컨벤션 확인 필수**.

- [ ] **Step 1: AdminRagPreviewApi 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewApi.java
package com.youthfit.admin.rag.presentation.controller;

import com.youthfit.admin.rag.presentation.dto.request.RagPreviewRequest;
import com.youthfit.admin.rag.presentation.dto.response.RagPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Admin RAG Preview",
        description = "어드민 — 하이브리드 검색 튜닝 미리보기 (baseline vs candidate)")
public interface AdminRagPreviewApi {

    @Operation(summary = "baseline(yml) vs candidate 검색 결과 비교")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "비교 성공"),
            @ApiResponse(responseCode = "400", description = "검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족"),
            @ApiResponse(responseCode = "404", description = "정책 미존재"),
            @ApiResponse(responseCode = "429", description = "rate limit 초과 (30/min)")
    })
    ResponseEntity<RagPreviewResponse> preview(@Valid @RequestBody RagPreviewRequest request);
}
```

- [ ] **Step 2: AdminRagPreviewController 작성**

```java
// backend/src/main/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewController.java
package com.youthfit.admin.rag.presentation.controller;

import com.youthfit.admin.rag.application.service.RagPreviewService;
import com.youthfit.admin.rag.presentation.dto.request.RagPreviewRequest;
import com.youthfit.admin.rag.presentation.dto.response.RagPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rag")
@RequiredArgsConstructor
public class AdminRagPreviewController implements AdminRagPreviewApi {

    private final RagPreviewService service;

    @Override
    @PostMapping("/preview")
    public ResponseEntity<RagPreviewResponse> preview(@RequestBody RagPreviewRequest request) {
        long userId = currentUserId();
        return ResponseEntity.ok(RagPreviewResponse.from(service.preview(request.toCommand(userId))));
    }

    private long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // TODO 구현자 주의: 프로젝트의 표준 user-id 추출 방식이 있다면 그것으로 교체.
        //   예: ((CustomUserPrincipal) auth.getPrincipal()).getUserId()
        //   현재는 auth.getName() 이 user id 문자열이라고 가정.
        return Long.parseLong(auth.getName());
    }
}
```

- [ ] **Step 3: 글로벌 예외 핸들러 매핑 추가**

기존 `common` 패키지의 `GlobalExceptionHandler` (이름은 프로젝트에 맞게) 에 다음 핸들러를 추가:

```java
// backend/src/main/java/com/youthfit/common/.../GlobalExceptionHandler.java 에 추가
@ExceptionHandler(com.youthfit.admin.rag.application.service.RagPreviewRateLimitException.class)
public ResponseEntity<?> handleRagPreviewRateLimit(
        com.youthfit.admin.rag.application.service.RagPreviewRateLimitException ex) {
    return ResponseEntity.status(429)
            .header("Retry-After", "60")
            .body(java.util.Map.of("message", ex.getMessage()));
}
```

> 구현자 주의: 기존 핸들러 시그니처/응답 포맷에 맞춰 조정.

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/youthfit/admin/rag/presentation/controller/ \
        backend/src/main/java/com/youthfit/common/
git commit -m "feat(admin/rag): add /api/v1/admin/rag/preview endpoint + 429 handler"
```

---

## Task 10: Controller WebMvc 슬라이스 테스트

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewControllerTest.java`

- [ ] **Step 1: 슬라이스 테스트 작성**

```java
// backend/src/test/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewControllerTest.java
package com.youthfit.admin.rag.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.admin.rag.application.dto.result.PreviewSideResult;
import com.youthfit.admin.rag.application.dto.result.RagPreviewResult;
import com.youthfit.admin.rag.application.service.RagPreviewRateLimitException;
import com.youthfit.admin.rag.application.service.RagPreviewService;
import com.youthfit.rag.application.dto.result.EffectiveConfig;
import com.youthfit.rag.application.dto.result.RagSearchTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AdminRagPreviewController")
@WebMvcTest(AdminRagPreviewController.class)
class AdminRagPreviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @MockBean private RagPreviewService previewService;

    private RagPreviewResult okResult() {
        RagSearchTrace trace = new RagSearchTrace(
                new EffectiveConfig(true, 20, 60, 0.10, true, 5),
                List.of(), List.of(), List.of(), List.of("주거"), 100L);
        return new RagPreviewResult(1L, "주거",
                List.of("주거"),
                new PreviewSideResult(trace),
                new PreviewSideResult(trace),
                List.of());
    }

    @Test
    @DisplayName("admin 정상 요청 → 200")
    @WithMockUser(roles = "ADMIN", username = "42")
    void admin_ok() throws Exception {
        given(previewService.preview(any())).willReturn(okResult());

        String body = om.writeValueAsString(Map.of(
                "policyId", 1,
                "query", "주거",
                "candidate", Map.of("rrfK", 30)
        ));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.baseline").exists())
                .andExpect(jsonPath("$.candidate").exists())
                .andExpect(jsonPath("$.diff.rankChanges").isArray());
    }

    @Test
    @DisplayName("비-admin → 403")
    @WithMockUser(roles = "USER", username = "42")
    void nonAdmin_forbidden() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거", "candidate", Map.of()));
        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없음 → 401")
    void unauthenticated_unauthorized() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거", "candidate", Map.of()));
        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("query blank → 400")
    @WithMockUser(roles = "ADMIN", username = "42")
    void blankQuery_badRequest() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "", "candidate", Map.of()));
        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rrfK = 0 → 400")
    @WithMockUser(roles = "ADMIN", username = "42")
    void invalidRrfK_badRequest() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거",
                "candidate", Map.of("rrfK", 0)));
        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("topNPerSearch = 1000 → 400 (상한 100)")
    @WithMockUser(roles = "ADMIN", username = "42")
    void topNTooLarge_badRequest() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거",
                "candidate", Map.of("topNPerSearch", 1000)));
        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rate limit 초과 → 429 + Retry-After")
    @WithMockUser(roles = "ADMIN", username = "42")
    void rateLimitExceeded_429() throws Exception {
        given(previewService.preview(any())).willThrow(new RagPreviewRateLimitException());

        String body = om.writeValueAsString(Map.of(
                "policyId", 1, "query", "주거", "candidate", Map.of()));
        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.youthfit.admin.rag.presentation.controller.AdminRagPreviewControllerTest"
```
Expected: 7 tests PASS.

> 만약 401/403 이 의도와 다르게 떨어지면 프로젝트의 SecurityConfig 가 `/api/v1/admin/**` 를 보호하도록 되어 있는지 확인하고, `WithMockUser(roles="ADMIN")` 이 인식되는지 점검. 필요 시 `@WithMockUser(roles = "ADMIN")` 의 권한 prefix 조정.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/admin/rag/presentation/controller/AdminRagPreviewControllerTest.java
git commit -m "test(admin/rag): WebMvc slice tests for preview endpoint"
```

---

## Task 11: testcontainers 통합 테스트 (1개)

**Files:**
- Create: `backend/src/test/java/com/youthfit/admin/rag/AdminRagPreviewIntegrationTest.java`

> 기존 통합 테스트 중 testcontainers Postgres(pgvector) + Redis 를 띄우는 베이스 클래스가 있다면 그것을 상속/재사용. 없다면 본 테스트가 직접 컨테이너를 띄움. **실제 작성 시 기존 통합 테스트의 부트스트랩을 확인하고 그 패턴을 따른다.**

- [ ] **Step 1: 통합 테스트 골격 작성**

```java
// backend/src/test/java/com/youthfit/admin/rag/AdminRagPreviewIntegrationTest.java
package com.youthfit.admin.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 컨트롤러 → 서비스 → repository → pgvector 까지 wire 검증.
 * 기존 testcontainers 베이스가 있다면 extends 해서 재사용.
 */
@DisplayName("AdminRagPreview 통합")
@SpringBootTest
@AutoConfigureMockMvc
class AdminRagPreviewIntegrationTest /* extends YourTestcontainersBase */ {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;

    // TODO 시드 데이터: 정책 1개 + chunk 5개 (embedding 포함) 를 setup 메서드에서 미리 insert.
    //      기존 RAG 통합 테스트(예: PolicyDocumentRepositoryTrigramTest 와 유사한 헬퍼) 참조.
    //      embedding 은 dummy(예: float[1536]{0.0}) 사용 — 본 테스트의 목적은 wire 검증.
    //      OpenAI embedding 호출은 @MockBean 으로 EmbeddingProvider 를 모킹해 dummy 반환.

    @Test
    @DisplayName("baseline vs candidate(rrfK=30) 호출 시 양쪽 결과 + diff 반환")
    @WithMockUser(roles = "ADMIN", username = "1")
    void baselineVsCandidate_returnsBothSidesAndDiff() throws Exception {
        Long seededPolicyId = 1L;  // setup 에서 seed 한 정책 ID 로 교체
        String body = om.writeValueAsString(Map.of(
                "policyId", seededPolicyId,
                "query", "주거",
                "candidate", Map.of("rrfK", 30)
        ));

        mockMvc.perform(post("/api/v1/admin/rag/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseline.config.rrfK").value(60))   // baseline = yml default
                .andExpect(jsonPath("$.candidate.config.rrfK").value(30))
                .andExpect(jsonPath("$.baseline.merged").isArray())
                .andExpect(jsonPath("$.candidate.merged").isArray());
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.youthfit.admin.rag.AdminRagPreviewIntegrationTest"
```
Expected: PASS (시드/EmbeddingProvider 모킹 후).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/youthfit/admin/rag/AdminRagPreviewIntegrationTest.java
git commit -m "test(admin/rag): integration test for end-to-end preview wire"
```

---

## Task 12: 프론트엔드 — API 클라이언트 + 훅

**Files:**
- Create: `frontend/src/apis/adminRag.api.ts`
- Create: `frontend/src/hooks/mutations/useRagPreview.ts`
- Create: `frontend/src/hooks/mutations/__tests__/useRagPreview.test.ts`

- [ ] **Step 1: API 클라이언트 작성**

```typescript
// frontend/src/apis/adminRag.api.ts
import { client } from './client';

export interface HybridOverride {
  hybridEnabled?: boolean | null;
  topNPerSearch?: number | null;
  rrfK?: number | null;
  trigramThreshold?: number | null;
  keywordBoostEnabled?: boolean | null;
  maxKeywords?: number | null;
}

export interface EffectiveConfig {
  hybridEnabled: boolean;
  topNPerSearch: number;
  rrfK: number;
  trigramThreshold: number;
  keywordBoostEnabled: boolean;
  maxKeywords: number;
}

export interface ChunkSummary {
  chunkId: number;
  chunkIndex: number;
  distance: number;
  preview: string;
}

export interface MergedChunk extends ChunkSummary {
  rrfScore: number;
  rank: number;
}

export interface PreviewSide {
  config: EffectiveConfig;
  vectorTopN: ChunkSummary[];
  trigramTopN: ChunkSummary[];
  merged: MergedChunk[];
  tookMs: number;
}

export interface RankChange {
  chunkId: number;
  baselineRank: number | null;
  candidateRank: number | null;
  delta: string; // "NEW" | "DROPPED" | "+N" | "-N" | "0"
}

export interface RagPreviewRequest {
  policyId: number;
  query: string;
  candidate: HybridOverride;
}

export interface RagPreviewResponse {
  policyId: number;
  query: string;
  extractedKeywords: string[];
  baseline: PreviewSide;
  candidate: PreviewSide;
  diff: { rankChanges: RankChange[] };
}

export async function ragPreview(req: RagPreviewRequest): Promise<RagPreviewResponse> {
  return client
    .post('api/v1/admin/rag/preview', { json: req })
    .json<RagPreviewResponse>();
}
```

- [ ] **Step 2: useRagPreview 훅 작성**

```typescript
// frontend/src/hooks/mutations/useRagPreview.ts
import { useMutation } from '@tanstack/react-query';
import { ragPreview, type RagPreviewRequest, type RagPreviewResponse } from '@/apis/adminRag.api';

export function useRagPreview() {
  return useMutation<RagPreviewResponse, Error, RagPreviewRequest>({
    mutationFn: ragPreview,
  });
}
```

- [ ] **Step 3: useRagPreview 테스트 작성**

```typescript
// frontend/src/hooks/mutations/__tests__/useRagPreview.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useRagPreview } from '../useRagPreview';
import * as api from '@/apis/adminRag.api';

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('useRagPreview', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('수동 호출 전엔 fetch 가 발생하지 않는다', () => {
    const spy = vi.spyOn(api, 'ragPreview').mockResolvedValue({} as any);
    renderHook(() => useRagPreview(), { wrapper });
    expect(spy).not.toHaveBeenCalled();
  });

  it('성공 시 데이터를 반환한다', async () => {
    const resp = {
      policyId: 1, query: 'q', extractedKeywords: [],
      baseline: {} as any, candidate: {} as any, diff: { rankChanges: [] },
    };
    vi.spyOn(api, 'ragPreview').mockResolvedValue(resp);

    const { result } = renderHook(() => useRagPreview(), { wrapper });
    result.current.mutate({ policyId: 1, query: 'q', candidate: {} });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(resp);
  });

  it('실패 시 에러를 노출한다', async () => {
    vi.spyOn(api, 'ragPreview').mockRejectedValue(new Error('boom'));

    const { result } = renderHook(() => useRagPreview(), { wrapper });
    result.current.mutate({ policyId: 1, query: 'q', candidate: {} });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('boom');
  });
});
```

- [ ] **Step 4: 테스트 실행**

```bash
cd frontend
npm run test -- useRagPreview
```
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/apis/adminRag.api.ts \
        frontend/src/hooks/mutations/useRagPreview.ts \
        frontend/src/hooks/mutations/__tests__/useRagPreview.test.ts
git commit -m "feat(fe/admin/rag): ragPreview API client + useRagPreview mutation"
```

---

## Task 13: 프론트엔드 — 단순 컴포넌트 (RankDeltaBadge, ChunkRow, BaselineConfigPanel)

**Files:**
- Create: `frontend/src/components/admin/rag-preview/RankDeltaBadge.tsx`
- Create: `frontend/src/components/admin/rag-preview/ChunkRow.tsx`
- Create: `frontend/src/components/admin/rag-preview/BaselineConfigPanel.tsx`
- Create: `frontend/src/components/admin/rag-preview/__tests__/RankDeltaBadge.test.tsx`
- Create: `frontend/src/components/admin/rag-preview/__tests__/ChunkRow.test.tsx`

- [ ] **Step 1: RankDeltaBadge + 테스트**

```tsx
// frontend/src/components/admin/rag-preview/RankDeltaBadge.tsx
import { cn } from '@/lib/cn';

export function RankDeltaBadge({ delta }: { delta: string }) {
  if (delta === '0') return null;
  const isNew = delta === 'NEW';
  const isDropped = delta === 'DROPPED';
  const isUp = delta.startsWith('-');   // -2 = 순위 상승 (작을수록 위)
  const isDown = delta.startsWith('+');

  const color = cn(
    isNew && 'bg-emerald-100 text-emerald-700',
    isDropped && 'bg-red-100 text-red-700',
    isUp && 'bg-blue-100 text-blue-700',
    isDown && 'bg-amber-100 text-amber-700',
  );

  const label = isUp ? `↑${delta.slice(1)}` : isDown ? `↓${delta.slice(1)}` : delta;
  return (
    <span className={cn('rounded px-1.5 py-0.5 text-xs font-medium', color)}>
      {label}
    </span>
  );
}
```

```tsx
// frontend/src/components/admin/rag-preview/__tests__/RankDeltaBadge.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RankDeltaBadge } from '../RankDeltaBadge';

describe('RankDeltaBadge', () => {
  it('NEW 표시', () => {
    render(<RankDeltaBadge delta="NEW" />);
    expect(screen.getByText('NEW')).toBeInTheDocument();
  });
  it('DROPPED 표시', () => {
    render(<RankDeltaBadge delta="DROPPED" />);
    expect(screen.getByText('DROPPED')).toBeInTheDocument();
  });
  it('+2 → ↓2 (하락)', () => {
    render(<RankDeltaBadge delta="+2" />);
    expect(screen.getByText('↓2')).toBeInTheDocument();
  });
  it('-1 → ↑1 (상승)', () => {
    render(<RankDeltaBadge delta="-1" />);
    expect(screen.getByText('↑1')).toBeInTheDocument();
  });
  it('0 → 렌더링 안 함', () => {
    const { container } = render(<RankDeltaBadge delta="0" />);
    expect(container).toBeEmptyDOMElement();
  });
});
```

- [ ] **Step 2: ChunkRow + 테스트**

```tsx
// frontend/src/components/admin/rag-preview/ChunkRow.tsx
import { useState } from 'react';
import { cn } from '@/lib/cn';
import { RankDeltaBadge } from './RankDeltaBadge';

interface Props {
  rank: number;
  chunkId: number;
  distance?: number;
  rrfScore?: number;
  preview: string;
  delta?: string;
}

export function ChunkRow({ rank, chunkId, distance, rrfScore, preview, delta }: Props) {
  const [expanded, setExpanded] = useState(false);
  const truncated = preview.length > 120 ? preview.slice(0, 120) + '…' : preview;
  return (
    <div className="border-b border-neutral-100 py-2 text-sm">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-medium text-neutral-700">{rank}.</span>
        <span className="text-neutral-500">chunk#{chunkId}</span>
        {distance !== undefined && (
          <span className="text-xs text-neutral-400">d={distance.toFixed(3)}</span>
        )}
        {rrfScore !== undefined && rrfScore > 0 && (
          <span className="text-xs text-neutral-400">rrf={rrfScore.toFixed(4)}</span>
        )}
        {delta && <RankDeltaBadge delta={delta} />}
      </div>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className={cn('text-left text-neutral-700', expanded ? '' : 'line-clamp-3')}
      >
        {expanded ? preview : truncated}
      </button>
    </div>
  );
}
```

```tsx
// frontend/src/components/admin/rag-preview/__tests__/ChunkRow.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ChunkRow } from '../ChunkRow';

describe('ChunkRow', () => {
  it('truncated preview 표시 + 클릭 시 expand', () => {
    const long = 'a'.repeat(200);
    render(<ChunkRow rank={1} chunkId={7} distance={0.21} preview={long} />);
    expect(screen.getByText(/a/).textContent?.length).toBeLessThan(200);
    fireEvent.click(screen.getByText(/a/));
    expect(screen.getByText(/a/).textContent?.length).toBe(200);
  });

  it('distance 와 rrfScore 표시', () => {
    render(<ChunkRow rank={2} chunkId={9} distance={0.5} rrfScore={0.0321} preview="x" />);
    expect(screen.getByText('d=0.500')).toBeInTheDocument();
    expect(screen.getByText('rrf=0.0321')).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: BaselineConfigPanel**

```tsx
// frontend/src/components/admin/rag-preview/BaselineConfigPanel.tsx
import type { EffectiveConfig } from '@/apis/adminRag.api';

export function BaselineConfigPanel({ config }: { config?: EffectiveConfig }) {
  if (!config) return <div className="text-sm text-neutral-400">아직 비교 안 됨</div>;
  const rows: [string, string | number | boolean][] = [
    ['hybridEnabled', config.hybridEnabled],
    ['topNPerSearch', config.topNPerSearch],
    ['rrfK', config.rrfK],
    ['trigramThreshold', config.trigramThreshold],
    ['keywordBoostEnabled', config.keywordBoostEnabled],
    ['maxKeywords', config.maxKeywords],
  ];
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
      {rows.map(([k, v]) => (
        <span key={k} className="contents">
          <dt className="text-neutral-500">{k}</dt>
          <dd className="font-mono text-neutral-800">{String(v)}</dd>
        </span>
      ))}
    </dl>
  );
}
```

- [ ] **Step 4: 테스트 실행**

```bash
npm run test -- RankDeltaBadge ChunkRow
```
Expected: 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/admin/rag-preview/RankDeltaBadge.tsx \
        frontend/src/components/admin/rag-preview/ChunkRow.tsx \
        frontend/src/components/admin/rag-preview/BaselineConfigPanel.tsx \
        frontend/src/components/admin/rag-preview/__tests__/RankDeltaBadge.test.tsx \
        frontend/src/components/admin/rag-preview/__tests__/ChunkRow.test.tsx
git commit -m "feat(fe/admin/rag): RankDeltaBadge + ChunkRow + BaselineConfigPanel"
```

---

## Task 14: 프론트엔드 — CandidateConfigForm + ResultTabs

**Files:**
- Create: `frontend/src/components/admin/rag-preview/CandidateConfigForm.tsx`
- Create: `frontend/src/components/admin/rag-preview/ResultTabs.tsx`
- Create: `frontend/src/components/admin/rag-preview/__tests__/CandidateConfigForm.test.tsx`
- Create: `frontend/src/components/admin/rag-preview/__tests__/ResultTabs.test.tsx`

- [ ] **Step 1: CandidateConfigForm**

```tsx
// frontend/src/components/admin/rag-preview/CandidateConfigForm.tsx
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { EffectiveConfig, HybridOverride } from '@/apis/adminRag.api';

const schema = z.object({
  hybridEnabled: z.boolean(),
  topNPerSearch: z.coerce.number().int().min(1).max(100),
  rrfK: z.coerce.number().int().min(1).max(500),
  trigramThreshold: z.coerce.number().min(0).max(1),
  keywordBoostEnabled: z.boolean(),
  maxKeywords: z.coerce.number().int().min(0).max(20),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  baseline?: EffectiveConfig;
  onChange: (overrides: HybridOverride) => void;
}

export function CandidateConfigForm({ baseline, onChange }: Props) {
  const { register, watch, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: baseline ?? undefined,
  });

  useEffect(() => {
    if (baseline) reset(baseline);
  }, [baseline, reset]);

  const values = watch();
  useEffect(() => {
    if (!baseline) return;
    // baseline 과 다른 필드만 overrides 로 전달
    const diff: HybridOverride = {};
    (Object.keys(values) as (keyof FormValues)[]).forEach((k) => {
      if (values[k] !== baseline[k]) (diff as any)[k] = values[k];
    });
    onChange(diff);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(values), baseline]);

  if (!baseline) return <div className="text-sm text-neutral-400">baseline 로딩 대기</div>;

  return (
    <form className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-sm">
      <label className="text-neutral-500">hybridEnabled</label>
      <input type="checkbox" {...register('hybridEnabled')} />

      <label className="text-neutral-500">topNPerSearch</label>
      <input type="number" {...register('topNPerSearch')} className="w-20 rounded border px-1" />
      {errors.topNPerSearch && <span className="col-span-2 text-xs text-red-600">1~100</span>}

      <label className="text-neutral-500">rrfK</label>
      <input type="number" {...register('rrfK')} className="w-20 rounded border px-1" />
      {errors.rrfK && <span className="col-span-2 text-xs text-red-600">1~500</span>}

      <label className="text-neutral-500">trigramThreshold</label>
      <input type="number" step="0.01" {...register('trigramThreshold')}
             className="w-24 rounded border px-1" />
      {errors.trigramThreshold && <span className="col-span-2 text-xs text-red-600">0.0~1.0</span>}

      <label className="text-neutral-500">keywordBoostEnabled</label>
      <input type="checkbox" {...register('keywordBoostEnabled')} />

      <label className="text-neutral-500">maxKeywords</label>
      <input type="number" {...register('maxKeywords')} className="w-20 rounded border px-1" />
      {errors.maxKeywords && <span className="col-span-2 text-xs text-red-600">0~20</span>}
    </form>
  );
}
```

- [ ] **Step 2: CandidateConfigForm 테스트**

```tsx
// frontend/src/components/admin/rag-preview/__tests__/CandidateConfigForm.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CandidateConfigForm } from '../CandidateConfigForm';

const baseline = {
  hybridEnabled: true, topNPerSearch: 20, rrfK: 60,
  trigramThreshold: 0.1, keywordBoostEnabled: true, maxKeywords: 5,
};

describe('CandidateConfigForm', () => {
  it('baseline 으로 prefill 된다', () => {
    render(<CandidateConfigForm baseline={baseline} onChange={() => {}} />);
    expect(screen.getByDisplayValue('20')).toBeInTheDocument();   // topNPerSearch
    expect(screen.getByDisplayValue('60')).toBeInTheDocument();   // rrfK
  });

  it('rrfK 변경 시 onChange 가 그 필드만 포함해 호출된다', async () => {
    const onChange = vi.fn();
    render(<CandidateConfigForm baseline={baseline} onChange={onChange} />);
    const rrfK = screen.getByDisplayValue('60') as HTMLInputElement;
    fireEvent.change(rrfK, { target: { value: '30' } });

    await waitFor(() => {
      const last = onChange.mock.calls.at(-1)?.[0];
      expect(last).toEqual({ rrfK: 30 });
    });
  });
});
```

- [ ] **Step 3: ResultTabs**

```tsx
// frontend/src/components/admin/rag-preview/ResultTabs.tsx
import { useState } from 'react';
import type { ChunkSummary, MergedChunk, PreviewSide } from '@/apis/adminRag.api';
import { ChunkRow } from './ChunkRow';

type Tab = 'merged' | 'vector' | 'trigram';

interface Props {
  side?: PreviewSide;
  rankByChunkId?: Map<number, string>;   // merged 탭에서 사용할 delta
}

export function ResultTabs({ side, rankByChunkId }: Props) {
  const [tab, setTab] = useState<Tab>('merged');

  if (!side) return <div className="text-sm text-neutral-400">결과 없음</div>;

  const items =
    tab === 'merged' ? side.merged
    : tab === 'vector' ? side.vectorTopN
    : side.trigramTopN;

  return (
    <div>
      <div className="mb-2 flex gap-1 border-b">
        {(['merged', 'vector', 'trigram'] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`px-3 py-1 text-sm ${tab === t ? 'border-b-2 border-brand-800 font-medium' : 'text-neutral-500'}`}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 'trigram' && !side.config.hybridEnabled && (
        <div className="text-sm text-neutral-400">hybrid 비활성 — trigram 검색이 실행되지 않음</div>
      )}
      {items.length === 0 ? (
        <div className="py-4 text-sm text-neutral-400">결과 없음</div>
      ) : (
        items.map((c: ChunkSummary | MergedChunk, idx: number) => {
          const isMerged = 'rank' in c;
          return (
            <ChunkRow
              key={c.chunkId}
              rank={isMerged ? (c as MergedChunk).rank : idx + 1}
              chunkId={c.chunkId}
              distance={c.distance}
              rrfScore={isMerged ? (c as MergedChunk).rrfScore : undefined}
              preview={c.preview}
              delta={tab === 'merged' ? rankByChunkId?.get(c.chunkId) : undefined}
            />
          );
        })
      )}
    </div>
  );
}
```

- [ ] **Step 4: ResultTabs 테스트**

```tsx
// frontend/src/components/admin/rag-preview/__tests__/ResultTabs.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ResultTabs } from '../ResultTabs';
import type { PreviewSide } from '@/apis/adminRag.api';

const side: PreviewSide = {
  config: {
    hybridEnabled: true, topNPerSearch: 20, rrfK: 60,
    trigramThreshold: 0.1, keywordBoostEnabled: true, maxKeywords: 5,
  },
  vectorTopN: [{ chunkId: 1, chunkIndex: 0, distance: 0.2, preview: 'v1' }],
  trigramTopN: [{ chunkId: 2, chunkIndex: 1, distance: 0.5, preview: 't1' }],
  merged: [{ chunkId: 1, chunkIndex: 0, distance: 0.2, rrfScore: 0.03, rank: 1, preview: 'm1' }],
  tookMs: 100,
};

describe('ResultTabs', () => {
  it('탭 전환', () => {
    render(<ResultTabs side={side} />);
    expect(screen.getByText(/m1/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('vector'));
    expect(screen.getByText(/v1/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('trigram'));
    expect(screen.getByText(/t1/)).toBeInTheDocument();
  });

  it('빈 결과 시 empty state', () => {
    const empty: PreviewSide = { ...side, merged: [], vectorTopN: [], trigramTopN: [] };
    render(<ResultTabs side={empty} />);
    expect(screen.getByText('결과 없음')).toBeInTheDocument();
  });

  it('hybrid 비활성 시 trigram 탭 안내', () => {
    const off: PreviewSide = { ...side, config: { ...side.config, hybridEnabled: false } };
    render(<ResultTabs side={off} />);
    fireEvent.click(screen.getByText('trigram'));
    expect(screen.getByText(/hybrid 비활성/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 5: 테스트 실행**

```bash
npm run test -- CandidateConfigForm ResultTabs
```
Expected: 5 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/admin/rag-preview/CandidateConfigForm.tsx \
        frontend/src/components/admin/rag-preview/ResultTabs.tsx \
        frontend/src/components/admin/rag-preview/__tests__/CandidateConfigForm.test.tsx \
        frontend/src/components/admin/rag-preview/__tests__/ResultTabs.test.tsx
git commit -m "feat(fe/admin/rag): CandidateConfigForm (RHF+Zod) + ResultTabs"
```

---

## Task 15: 프론트엔드 — Controls + Page + 라우트 + 사이드바

**Files:**
- Create: `frontend/src/components/admin/rag-preview/RagPreviewControls.tsx`
- Create: `frontend/src/pages/admin/AdminRagPreviewPage.tsx`
- Create: `frontend/src/pages/admin/__tests__/AdminRagPreviewPage.test.tsx`
- Modify: `frontend/src/App.tsx` (또는 라우트 정의 파일) — `/admin/rag-preview` 추가
- Modify: `frontend/src/components/admin/AdminControls.tsx` (또는 사이드바 메뉴 정의) — 항목 추가

- [ ] **Step 1: RagPreviewControls**

```tsx
// frontend/src/components/admin/rag-preview/RagPreviewControls.tsx
import { useState } from 'react';

interface Props {
  onSubmit: (policyId: number, query: string) => void;
  isPending?: boolean;
}

export function RagPreviewControls({ onSubmit, isPending }: Props) {
  const [policyId, setPolicyId] = useState('');
  const [query, setQuery] = useState('');
  const canSubmit = policyId && query.trim() && !isPending;
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) onSubmit(Number(policyId), query.trim());
      }}
      className="flex flex-wrap items-end gap-3"
    >
      <label className="flex flex-col text-sm">
        <span className="text-neutral-500">정책 ID</span>
        <input
          type="number"
          value={policyId}
          onChange={(e) => setPolicyId(e.target.value)}
          className="w-32 rounded border px-2 py-1"
        />
      </label>
      <label className="flex flex-1 flex-col text-sm">
        <span className="text-neutral-500">쿼리</span>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          maxLength={500}
          className="rounded border px-2 py-1"
        />
      </label>
      <button
        type="submit"
        disabled={!canSubmit}
        className="rounded bg-brand-800 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {isPending ? '실행 중…' : '▶ 비교 실행'}
      </button>
    </form>
  );
}
```

- [ ] **Step 2: AdminRagPreviewPage**

```tsx
// frontend/src/pages/admin/AdminRagPreviewPage.tsx
import { useState } from 'react';
import { useRagPreview } from '@/hooks/mutations/useRagPreview';
import type { HybridOverride, RankChange } from '@/apis/adminRag.api';
import { RagPreviewControls } from '@/components/admin/rag-preview/RagPreviewControls';
import { BaselineConfigPanel } from '@/components/admin/rag-preview/BaselineConfigPanel';
import { CandidateConfigForm } from '@/components/admin/rag-preview/CandidateConfigForm';
import { ResultTabs } from '@/components/admin/rag-preview/ResultTabs';

function deltaMap(changes: RankChange[]): Map<number, string> {
  const m = new Map<number, string>();
  for (const c of changes) m.set(c.chunkId, c.delta);
  return m;
}

export default function AdminRagPreviewPage() {
  const mutation = useRagPreview();
  const [candidate, setCandidate] = useState<HybridOverride>({});

  const onSubmit = (policyId: number, query: string) => {
    mutation.mutate({ policyId, query, candidate });
  };

  const data = mutation.data;
  const baselineConfig = data?.baseline.config;
  const candidateConfig = data?.candidate.config;
  const deltas = data ? deltaMap(data.diff.rankChanges) : new Map<number, string>();

  return (
    <div className="hidden p-6 md:block">
      <h1 className="mb-4 text-xl font-semibold">RAG 검색 미리보기</h1>

      <RagPreviewControls onSubmit={onSubmit} isPending={mutation.isPending} />

      {mutation.isError && (
        <div className="mt-3 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {(mutation.error as Error).message}
        </div>
      )}

      {data && (
        <>
          <div className="mt-4 text-sm">
            <span className="text-neutral-500">추출 키워드: </span>
            {data.extractedKeywords.map((kw) => (
              <span key={kw} className="ml-1 rounded bg-neutral-100 px-1.5 py-0.5">{kw}</span>
            ))}
          </div>

          <div className="mt-4 grid grid-cols-2 gap-4">
            <section className="rounded border p-4">
              <h2 className="mb-2 font-medium">Baseline (yml) — {data.baseline.tookMs} ms</h2>
              <BaselineConfigPanel config={baselineConfig} />
              <div className="mt-4">
                <ResultTabs side={data.baseline} rankByChunkId={deltas} />
              </div>
            </section>

            <section className="rounded border p-4">
              <h2 className="mb-2 font-medium">Candidate — {data.candidate.tookMs} ms</h2>
              <CandidateConfigForm
                baseline={baselineConfig}
                onChange={setCandidate}
              />
              <div className="mt-4">
                <ResultTabs side={data.candidate} rankByChunkId={deltas} />
              </div>
            </section>
          </div>
        </>
      )}

      <div className="mt-8 text-xs text-neutral-400 md:hidden">
        데스크톱 화면에서 사용해주세요.
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 라우트 추가**

`frontend/src/App.tsx` (또는 라우트 정의 파일) 에 다음 추가:

```tsx
import AdminRagPreviewPage from '@/pages/admin/AdminRagPreviewPage';
// ...
// admin 라우트 그룹 내부 (RequireAdmin 가드 안):
<Route path="/admin/rag-preview" element={<AdminRagPreviewPage />} />
```

> 구현자 주의: 기존 admin 라우트(예: `/admin/llm-cost`) 가 어떻게 등록되어 있는지 그 패턴을 그대로 따른다.

- [ ] **Step 4: 사이드바/메뉴 항목 추가**

`frontend/src/components/admin/AdminControls.tsx` 또는 admin 메뉴 정의 파일에서 기존 admin 메뉴 배열에 다음 추가:

```ts
{ to: '/admin/rag-preview', label: 'RAG 미리보기' }
```

> 구현자 주의: 기존 메뉴 항목 형식을 그대로 따른다.

- [ ] **Step 5: 페이지 통합 테스트**

```tsx
// frontend/src/pages/admin/__tests__/AdminRagPreviewPage.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import AdminRagPreviewPage from '../AdminRagPreviewPage';
import * as api from '@/apis/adminRag.api';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{ui}</QueryClientProvider>;
}

const mockResp: api.RagPreviewResponse = {
  policyId: 1, query: '주거',
  extractedKeywords: ['주거'],
  baseline: {
    config: { hybridEnabled: true, topNPerSearch: 20, rrfK: 60, trigramThreshold: 0.1,
              keywordBoostEnabled: true, maxKeywords: 5 },
    vectorTopN: [], trigramTopN: [],
    merged: [{ chunkId: 1, chunkIndex: 0, distance: 0.2, rrfScore: 0.03, rank: 1, preview: 'b1' }],
    tookMs: 142,
  },
  candidate: {
    config: { hybridEnabled: true, topNPerSearch: 30, rrfK: 30, trigramThreshold: 0.15,
              keywordBoostEnabled: true, maxKeywords: 7 },
    vectorTopN: [], trigramTopN: [],
    merged: [{ chunkId: 2, chunkIndex: 1, distance: 0.18, rrfScore: 0.04, rank: 1, preview: 'c1' }],
    tookMs: 167,
  },
  diff: { rankChanges: [{ chunkId: 2, baselineRank: null, candidateRank: 1, delta: 'NEW' }] },
};

describe('AdminRagPreviewPage', () => {
  it('실행 → 양쪽 패널 렌더 + diff 배지', async () => {
    vi.spyOn(api, 'ragPreview').mockResolvedValue(mockResp);

    render(wrap(<AdminRagPreviewPage />));
    fireEvent.change(screen.getByLabelText('정책 ID'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('쿼리'), { target: { value: '주거' } });
    fireEvent.click(screen.getByText(/비교 실행/));

    await waitFor(() => expect(screen.getByText('b1')).toBeInTheDocument());
    expect(screen.getByText('c1')).toBeInTheDocument();
    expect(screen.getByText('NEW')).toBeInTheDocument();
  });

  it('500 응답 시 에러 노출', async () => {
    vi.spyOn(api, 'ragPreview').mockRejectedValue(new Error('서버 오류'));

    render(wrap(<AdminRagPreviewPage />));
    fireEvent.change(screen.getByLabelText('정책 ID'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('쿼리'), { target: { value: '주거' } });
    fireEvent.click(screen.getByText(/비교 실행/));

    await waitFor(() => expect(screen.getByText('서버 오류')).toBeInTheDocument());
  });
});
```

- [ ] **Step 6: 테스트 실행**

```bash
npm run test -- AdminRagPreviewPage
```
Expected: 2 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/admin/rag-preview/RagPreviewControls.tsx \
        frontend/src/pages/admin/AdminRagPreviewPage.tsx \
        frontend/src/pages/admin/__tests__/AdminRagPreviewPage.test.tsx \
        frontend/src/App.tsx \
        frontend/src/components/admin/AdminControls.tsx
git commit -m "feat(fe/admin/rag): /admin/rag-preview page + route + sidebar entry"
```

---

## Task 16: 최종 검증 (수동 스모크 + 전체 빌드)

**Files:** 없음 (검증만)

- [ ] **Step 1: 백엔드 전체 빌드 + 테스트**

```bash
cd backend
./gradlew clean build
```
Expected: BUILD SUCCESSFUL. (테스트 + Jacoco 포함)

- [ ] **Step 2: 프론트엔드 전체 빌드 + 테스트**

```bash
cd ../frontend
npm run test -- --run
npm run build
```
Expected: 모든 테스트 PASS, vite build 성공.

- [ ] **Step 3: 로컬 수동 스모크**

1. `docker-compose up -d postgres redis` (또는 기존 부트스트랩)
2. `cd backend && ./gradlew bootRun` (다른 터미널)
3. `cd frontend && npm run dev` (또 다른 터미널)
4. 브라우저에서 admin 로그인 → `/admin/rag-preview` 접근
5. 시드된 정책 ID 1개 + 쿼리 입력 → "비교 실행"
6. 확인:
   - [ ] 좌측 Baseline 에 yml 값 표시
   - [ ] 우측 Candidate 폼은 baseline 으로 prefill
   - [ ] rrfK 30 으로 변경 → 다시 실행 → merged 결과의 순위/배지가 달라짐
   - [ ] candidate.hybridEnabled=false 체크 → trigram 탭에 "비활성" 안내
   - [ ] 31번째 호출 → 429 (네트워크 탭에서 확인)

- [ ] **Step 4: 스펙 §9 성공 기준 체크**

| 기준 | 확인 |
|---|---|
| 30초 안에 결과 본다 | ✅ / ❌ |
| candidate 결과가 yml 변경+재배포와 동일 | ✅ / ❌ (드리프트 0 — 같은 코드 경로) |
| 어느 chunk 가 NEW/DROPPED/이동인지 한눈에 보인다 | ✅ / ❌ |
| 분당 30회 초과 시 429 | ✅ / ❌ |
| 비-admin 토큰 → 403 | ✅ / ❌ |
| 백엔드 신규 라인 커버리지 80%+ | ✅ / ❌ (`./gradlew jacocoTestReport` 결과 확인) |

- [ ] **Step 5: 머지 준비**

스모크 성공 + 빌드 성공 + 6개 성공 기준 모두 충족 시 PR 생성:

```bash
git push -u origin <branch>
# /create-pr 스킬 사용 권장
```

---

## Self-Review 결과 (작성자 노트)

**Spec coverage:** 스펙 모든 섹션이 태스크에 대응됨.
- §4 API → T8 (DTO/검증) + T9 (Controller) + T10 (슬라이스 테스트) + T11 (통합 테스트)
- §5 도메인 변경 → T1 (EffectiveConfig/Overrides) + T2 (Trace/MergedChunk) + T3 (RagSearchService)
- §5 admin 신규 → T4 (RankCalculator) + T5 (RateLimiter) + T6 (DTO) + T7 (PreviewService)
- §6 프론트 → T12 (api/hook) + T13~14 (컴포넌트) + T15 (페이지/라우트)
- §7 권한/감사/가드 → T5 (rate limit) + T7 (감사 로그) + T8 (검증) + T9 (Retry-After)
- §8 테스트 → T1~T15 의 각 테스트 + T16 검증
- §9 성공 기준 → T16 Step 4

**Placeholder scan:** 의도적 placeholder 2곳 — T9 의 `currentUserId()` 사용자 추출 방식 (프로젝트 컨벤션 확인 필요), T11 통합 테스트의 시드 데이터 (기존 RAG 통합 테스트 패턴 차용 필요). 둘 다 "구현자 주의" 명시.

**Type consistency:** 검증함.
- `HybridSearchOverrides` (rag.application) ↔ `HybridOverrideCommand` (admin.application) ↔ `HybridOverrideRequest` (admin.presentation) 의 6개 필드 모두 동일 이름·순서.
- `EffectiveConfig` ↔ `EffectiveConfigResponse` 필드 일치.
- `MergedChunk` (distance, rrfScore, rank, preview) ↔ `MergedChunkResponse` 일치.
- `RankChangeResult.delta` (Integer) ↔ `RankChangeResponse.delta` (String) — 의도적 변환 (UI 용).

**Scope:** 단일 구현 계획. 16개 태스크, 각 2~10 step. 백엔드 11개 / 프론트 4개 / 검증 1개.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-22-rag-admin-preview.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
