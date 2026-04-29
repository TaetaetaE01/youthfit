# 가이드 정확도 강화 (소득 환산·그룹 분리·highlights) 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가이드 풀이에 환산 금액 표기·차상위 분류 분리·정책 특징(highlights) 출력을 도입하고, 그룹 분리·환산값 누락·highlights 부족을 자동 검증 + 1회 LLM 재시도로 강제한다.

**Architecture:** `policy` 모듈에 `IncomeBracketReference`(yaml 기반 reference 데이터) 도메인을 신설하고, `guide` 모듈의 `GuideContent` 출력 스키마에 `highlights`를 추가한다. LLM 시스템 프롬프트 / few-shot / user message 컨텍스트를 강화하고, `GuideValidator` 검증 4종 추가 + 1회 재시도 루프를 도입한다. 프론트는 `PolicyHighlightsCard`를 신규 추가하고 `SourceLinkedListCard`로 pitfalls·highlights 공통 컴포넌트를 추출한다. `Guide.sourceHash` 입력에 `referenceData.version` + `prompt.version`을 포함해 배포 시 모든 정책 가이드 자동 재생성.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA, PostgreSQL + pgvector, OpenAI Chat API, JUnit 5, snakeyaml. 프론트: React 19, TypeScript 5, Vite 6, TanStack Query, Tailwind v4, Vitest.

**Spec:** `docs/superpowers/specs/DONE_2026-04-28-guide-accuracy-income-bracket-design.md`

**PR 전략:** 단일 PR + 6 commit (Task 1 = commit 1, …, Task 6 = commit 6). 각 commit은 독립적으로 머지 안전한 상태이지만, e2e 가치는 Task 3·4 머지 이후 발현.

---

## 파일 구조 (신규/변경 매핑)

### 백엔드

**신규**:
- `backend/src/main/java/com/youthfit/policy/domain/model/IncomeBracketReference.java` — record + `findAmount()`
- `backend/src/main/java/com/youthfit/policy/domain/model/HouseholdSize.java` — enum (ONE, TWO)
- `backend/src/main/java/com/youthfit/policy/application/port/IncomeBracketReferenceLoader.java` — port
- `backend/src/main/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoader.java` — yaml impl
- `backend/src/main/resources/income-bracket/2025.yaml` — 25년 데이터 (commit 1)
- `backend/src/main/resources/income-bracket/2026.yaml` — 26년 데이터 (commit 6에서 갱신)
- `backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java` — record
- `backend/src/test/java/com/youthfit/policy/domain/model/IncomeBracketReferenceTest.java`
- `backend/src/test/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoaderTest.java`
- `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java`

**변경**:
- `backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java` — `highlights` 추가
- `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` — `referenceData` 추가
- `backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java` — `highlights` 매핑
- `backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java` — `highlights` 추가
- `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` — hash 입력 변경 + 재시도 루프
- `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java` — 검증 4종 추가
- `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` — 프롬프트 / user message / 스키마 / 재시도 호출
- `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java` — 재시도용 메서드 추가
- `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java` — 검증 테스트 추가
- `backend/src/test/java/com/youthfit/guide/infrastructure/external/OpenAiChatClientTest.java` — 응답 파싱 테스트

### 프론트

**신규**:
- `frontend/src/components/policy/SourceLinkedListCard.tsx` — pitfalls·highlights 공통 컴포넌트
- `frontend/src/components/policy/HighlightsCard.tsx` — highlights 카드 (SourceLinkedListCard 기반)
- `frontend/src/components/policy/__tests__/SourceLinkedListCard.test.tsx`
- `frontend/src/components/policy/__tests__/HighlightsCard.test.tsx`

**변경**:
- `frontend/src/types/policy.ts` — `GuideHighlight`, `GuideResponse.highlights` 추가
- `frontend/src/components/policy/PitfallsCard.tsx` — `SourceLinkedListCard` 호출로 재작성
- `frontend/src/pages/PolicyDetailPage.tsx` — `HighlightsCard` 렌더 (위치: 한 줄 요약 카드 다음)
- `frontend/src/components/policy/AttachmentSection.tsx` (or 해당) — id 부여 (`attachment-section`) — 카드의 `📎 원본 첨부` 버튼이 스크롤 타겟으로 사용

---

## Task 1 — `policy` 모듈: `IncomeBracketReference` 도메인 + yaml + loader

**목적**: reference 데이터 도메인을 신설하고 yaml 기반 loader를 부팅 시 1회 로드. 호출부는 다음 task에서 추가하므로 본 task는 단위 테스트로만 검증.

**Files:**
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/HouseholdSize.java`
- Create: `backend/src/main/java/com/youthfit/policy/domain/model/IncomeBracketReference.java`
- Create: `backend/src/main/java/com/youthfit/policy/application/port/IncomeBracketReferenceLoader.java`
- Create: `backend/src/main/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoader.java`
- Create: `backend/src/main/resources/income-bracket/2025.yaml`
- Create: `backend/src/test/java/com/youthfit/policy/domain/model/IncomeBracketReferenceTest.java`
- Create: `backend/src/test/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoaderTest.java`

### Steps

- [ ] **1.1 — `HouseholdSize` enum 작성**

```java
package com.youthfit.policy.domain.model;

public enum HouseholdSize {
    ONE, TWO
}
```

- [ ] **1.2 — `IncomeBracketReference` 실패 테스트 작성**

`backend/src/test/java/com/youthfit/policy/domain/model/IncomeBracketReferenceTest.java`:

```java
package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketReferenceTest {

    @Test
    void findAmount_가구원수와_퍼센트가_매칭되면_금액을_반환() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_000L)),
                Map.of(HouseholdSize.ONE, 1_196_000L));

        assertThat(ref.findAmount(HouseholdSize.ONE, 60)).contains(1_435_000L);
    }

    @Test
    void findAmount_미존재_퍼센트면_빈_옵셔널() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_000L)),
                Map.of());

        assertThat(ref.findAmount(HouseholdSize.ONE, 80)).isEmpty();
    }

    @Test
    void findAmount_미존재_가구원수면_빈_옵셔널() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1, Map.of(), Map.of());

        assertThat(ref.findAmount(HouseholdSize.ONE, 60)).isEmpty();
    }
}
```

- [ ] **1.3 — 테스트 fail 확인**

```
cd backend
./gradlew test --tests "com.youthfit.policy.domain.model.IncomeBracketReferenceTest"
```

Expected: 컴파일 실패 (record 미정의).

- [ ] **1.4 — `IncomeBracketReference` record 구현**

`backend/src/main/java/com/youthfit/policy/domain/model/IncomeBracketReference.java`:

```java
package com.youthfit.policy.domain.model;

import java.util.Map;
import java.util.Optional;

public record IncomeBracketReference(
        int year,
        int version,
        Map<HouseholdSize, Map<Integer, Long>> medianIncome,
        Map<HouseholdSize, Long> nearPoor
) {

    public IncomeBracketReference {
        medianIncome = medianIncome == null ? Map.of() : Map.copyOf(medianIncome);
        nearPoor = nearPoor == null ? Map.of() : Map.copyOf(nearPoor);
    }

    public Optional<Long> findAmount(HouseholdSize size, int percent) {
        Map<Integer, Long> bySize = medianIncome.get(size);
        if (bySize == null) return Optional.empty();
        return Optional.ofNullable(bySize.get(percent));
    }
}
```

- [ ] **1.5 — 테스트 pass 확인**

`./gradlew test --tests "com.youthfit.policy.domain.model.IncomeBracketReferenceTest"` → BUILD SUCCESSFUL.

- [ ] **1.6 — `IncomeBracketReferenceLoader` port 작성**

`backend/src/main/java/com/youthfit/policy/application/port/IncomeBracketReferenceLoader.java`:

```java
package com.youthfit.policy.application.port;

import com.youthfit.policy.domain.model.IncomeBracketReference;
import java.util.Optional;

public interface IncomeBracketReferenceLoader {
    Optional<IncomeBracketReference> findByYear(int year);
    IncomeBracketReference findLatest();
}
```

- [ ] **1.7 — 25년 yaml 파일 작성**

`backend/src/main/resources/income-bracket/2025.yaml` (값은 2025년 보건복지부 고시 기준중위소득. 단위 KRW/월):

```yaml
year: 2025
version: 1
unit: KRW_MONTH
note: "2024년 7월 보건복지부 고시 기준중위소득 (적용 연도 2025)"
medianIncome:
  "1":
    "10":  239201
    "20":  478403
    "30":  717604
    "40":  956805
    "47":  1124246
    "50":  1196007
    "60":  1435208
    "70":  1674409
    "80":  1913610
    "90":  2152812
    "100": 2392013
    "120": 2870416
    "130": 3109617
    "140": 3348818
    "150": 3588020
    "170": 4066422
    "180": 4305623
  "2":
    "10":  393266
    "20":  786532
    "30":  1179797
    "40":  1573063
    "47":  1848349
    "50":  1966329
    "60":  2359595
    "70":  2752861
    "80":  3146126
    "90":  3539392
    "100": 3932658
    "120": 4719190
    "130": 5112456
    "140": 5505721
    "150": 5898987
    "170": 6685519
    "180": 7078784
nearPoor:
  "1": 1196007
  "2": 1966329
```

> 26년 보건복지부 발표값은 task 6에서 `2026.yaml` 신규 추가로 갱신 (발표 전이면 25년 yaml 그대로 운영).

- [ ] **1.8 — `YamlIncomeBracketReferenceLoader` 실패 테스트 작성**

`backend/src/test/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoaderTest.java`:

```java
package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class YamlIncomeBracketReferenceLoaderTest {

    @Test
    void findByYear_2025_yaml_로드() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        Optional<IncomeBracketReference> ref = loader.findByYear(2025);

        assertThat(ref).isPresent();
        assertThat(ref.get().year()).isEqualTo(2025);
        assertThat(ref.get().version()).isEqualTo(1);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 60)).contains(1_435_208L);
        assertThat(ref.get().findAmount(HouseholdSize.TWO, 100)).contains(3_932_658L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.ONE)).isEqualTo(1_196_007L);
    }

    @Test
    void findByYear_미존재면_빈_옵셔널() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        assertThat(loader.findByYear(1999)).isEmpty();
    }

    @Test
    void findLatest_가장_최근_연도_반환() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        IncomeBracketReference latest = loader.findLatest();

        assertThat(latest.year()).isEqualTo(2025);
    }
}
```

- [ ] **1.9 — 테스트 fail 확인**

`./gradlew test --tests "com.youthfit.policy.infrastructure.external.YamlIncomeBracketReferenceLoaderTest"` → 컴파일 실패.

- [ ] **1.10 — `YamlIncomeBracketReferenceLoader` 구현**

`backend/src/main/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoader.java`:

```java
package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.application.port.IncomeBracketReferenceLoader;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Component
public class YamlIncomeBracketReferenceLoader implements IncomeBracketReferenceLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlIncomeBracketReferenceLoader.class);
    private static final String CLASSPATH_GLOB = "classpath:income-bracket/*.yaml";

    private final TreeMap<Integer, IncomeBracketReference> byYear = new TreeMap<>();

    @PostConstruct
    public void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(CLASSPATH_GLOB);
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    IncomeBracketReference ref = parse(new Yaml().load(in));
                    byYear.put(ref.year(), ref);
                    log.info("income-bracket reference 로드: year={}, version={}", ref.year(), ref.version());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("income-bracket yaml 로드 실패", e);
        }
        if (byYear.isEmpty()) {
            throw new IllegalStateException("income-bracket yaml이 한 개도 없음");
        }
    }

    @Override
    public Optional<IncomeBracketReference> findByYear(int year) {
        return Optional.ofNullable(byYear.get(year));
    }

    @Override
    public IncomeBracketReference findLatest() {
        return byYear.lastEntry().getValue();
    }

    @SuppressWarnings("unchecked")
    private IncomeBracketReference parse(Map<String, Object> raw) {
        int year = (int) raw.get("year");
        int version = (int) raw.get("version");
        Map<String, Map<String, Number>> medianRaw = (Map<String, Map<String, Number>>) raw.get("medianIncome");
        Map<String, Number> nearPoorRaw = (Map<String, Number>) raw.get("nearPoor");

        Map<HouseholdSize, Map<Integer, Long>> median = new HashMap<>();
        medianRaw.forEach((sizeKey, byPercent) -> {
            Map<Integer, Long> mapped = new HashMap<>();
            byPercent.forEach((p, v) -> mapped.put(Integer.parseInt(p), v.longValue()));
            median.put(toSize(sizeKey), mapped);
        });

        Map<HouseholdSize, Long> nearPoor = new HashMap<>();
        nearPoorRaw.forEach((k, v) -> nearPoor.put(toSize(k), v.longValue()));

        return new IncomeBracketReference(year, version, median, nearPoor);
    }

    private HouseholdSize toSize(String key) {
        return switch (key) {
            case "1" -> HouseholdSize.ONE;
            case "2" -> HouseholdSize.TWO;
            default -> throw new IllegalArgumentException("지원하지 않는 가구원 수: " + key);
        };
    }
}
```

- [ ] **1.11 — 테스트 pass 확인**

`./gradlew test --tests "com.youthfit.policy.infrastructure.external.YamlIncomeBracketReferenceLoaderTest"` → BUILD SUCCESSFUL.

- [ ] **1.12 — 전체 빌드 확인**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL. 기존 테스트 회귀 없음.

- [ ] **1.13 — Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/HouseholdSize.java \
        backend/src/main/java/com/youthfit/policy/domain/model/IncomeBracketReference.java \
        backend/src/main/java/com/youthfit/policy/application/port/IncomeBracketReferenceLoader.java \
        backend/src/main/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoader.java \
        backend/src/main/resources/income-bracket/2025.yaml \
        backend/src/test/java/com/youthfit/policy/domain/model/IncomeBracketReferenceTest.java \
        backend/src/test/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoaderTest.java
git commit -m "feat(policy): IncomeBracketReference 도메인 + yaml + loader 추가"
```

---

## Task 2 — `guide` 도메인 + 응답 DTO + `GuideGenerationInput` 시그니처

**목적**: `GuideContent.highlights`를 추가하고 응답까지 흐르는 경로를 잡는다. 프롬프트 변경 전이라 LLM이 `highlights`를 못 채울 수 있어 빈 배열 fallback. `GuideGenerationInput`에 `referenceData` 필드 추가 (loader 주입은 task 3).

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java`
- Modify: `backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java`
- Modify: `backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java`
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` (parseResponse만 — `highlights` 필드 처리)

### Steps

- [ ] **2.1 — `GuideHighlight` record 작성**

`backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java`:

```java
package com.youthfit.guide.domain.model;

public record GuideHighlight(String text, GuideSourceField sourceField) {

    public GuideHighlight {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text는 비어있을 수 없습니다");
        }
        if (sourceField == null) {
            throw new IllegalArgumentException("sourceField는 null일 수 없습니다");
        }
    }
}
```

- [ ] **2.2 — `GuideContent` 에 `highlights` 추가 (실패 테스트 작성)**

`backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java` (없으면 생성):

```java
package com.youthfit.guide.domain.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GuideContentTest {

    @Test
    void highlights_null이면_빈_리스트() {
        GuideContent c = new GuideContent("요약", List.of(), null, null, null, List.of());
        assertThat(c.highlights()).isEmpty();
    }

    @Test
    void highlights_정상_수용() {
        GuideHighlight h = new GuideHighlight("월 20만원 지원", GuideSourceField.SUPPORT_CONTENT);
        GuideContent c = new GuideContent("요약", List.of(h), null, null, null, List.of());
        assertThat(c.highlights()).hasSize(1);
        assertThat(c.highlights().get(0).text()).isEqualTo("월 20만원 지원");
    }
}
```

- [ ] **2.3 — 테스트 fail 확인**

`./gradlew test --tests "com.youthfit.guide.domain.model.GuideContentTest"` → 컴파일 실패 (시그니처 불일치).

- [ ] **2.4 — `GuideContent` record 변경**

`backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java`:

```java
package com.youthfit.guide.domain.model;

import java.util.List;

public record GuideContent(
        String oneLineSummary,
        List<GuideHighlight> highlights,
        GuidePairedSection target,
        GuidePairedSection criteria,
        GuidePairedSection content,
        List<GuidePitfall> pitfalls) {

    public GuideContent {
        if (oneLineSummary == null || oneLineSummary.isBlank()) {
            throw new IllegalArgumentException("oneLineSummary는 비어있을 수 없습니다");
        }
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
    }
}
```

- [ ] **2.5 — `OpenAiChatClient.parseResponse` 에 highlights 파싱 추가**

기존 `parseResponse(String json)` 메서드 (라인 ~166) 를 다음으로 변경:

```java
GuideContent parseResponse(String json) {
    try {
        JsonNode node = objectMapper.readTree(json);
        String oneLine = node.get("oneLineSummary").asText();
        List<GuideHighlight> highlights = parseHighlights(node.get("highlights"));
        GuidePairedSection target = parsePaired(node.get("target"));
        GuidePairedSection criteria = parsePaired(node.get("criteria"));
        GuidePairedSection content = parsePaired(node.get("content"));
        List<GuidePitfall> pitfalls = parsePitfalls(node.get("pitfalls"));
        return new GuideContent(oneLine, highlights, target, criteria, content, pitfalls);
    } catch (Exception e) {
        log.error("가이드 응답 JSON 파싱 실패: {}", json, e);
        throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 응답 파싱 실패");
    }
}

private List<GuideHighlight> parseHighlights(JsonNode node) {
    List<GuideHighlight> highlights = new ArrayList<>();
    if (node == null || !node.isArray()) return highlights;
    node.forEach(n -> {
        String text = n.get("text").asText();
        String sourceFieldStr = n.get("sourceField").asText();
        highlights.add(new GuideHighlight(text, GuideSourceField.valueOf(sourceFieldStr)));
    });
    return highlights;
}
```

- [ ] **2.6 — `GuideContent` 호출부 컴파일 통과 확인 (테스트 / 다른 파일)**

```
./gradlew compileJava compileTestJava
```

Expected: `GuideContent` 생성자를 호출하던 기존 테스트가 6-인자 시그니처로 깨질 수 있음. 모두 `List.of()` 인자 추가해 수정. 후보 파일 검색:

```bash
grep -rn "new GuideContent(" backend/src/test --include="*.java"
```

각 호출 지점에 `highlights` 자리(2번째 인자)에 `List.of()` 추가.

- [ ] **2.7 — `GuideContentTest` pass 확인**

`./gradlew test --tests "com.youthfit.guide.domain.model.GuideContentTest"` → PASS.

- [ ] **2.8 — `GuideGenerationInput` 에 `referenceData` 필드 추가**

`backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` 변경:

```java
package com.youthfit.guide.application.dto.command;

import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.rag.domain.model.PolicyDocument;

import java.util.List;
import java.util.stream.Collectors;

public record GuideGenerationInput(
        Long policyId,
        String title,
        Integer referenceYear,
        String summary,
        String body,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String contact,
        String organization,
        List<String> chunkContents,
        IncomeBracketReference referenceData
) {

    public GuideGenerationInput {
        if (policyId == null) throw new IllegalArgumentException("policyId는 null일 수 없습니다");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title은 비어있을 수 없습니다");
        chunkContents = chunkContents == null ? List.of() : List.copyOf(chunkContents);
        // referenceData는 nullable 허용 (yaml 누락 시 호출부에서 fallback 처리)
    }

    public static GuideGenerationInput of(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference referenceData) {
        List<String> chunkTexts = chunks == null
                ? List.of()
                : chunks.stream().map(PolicyDocument::getContent).collect(Collectors.toList());

        return new GuideGenerationInput(
                policy.getId(), policy.getTitle(), policy.getReferenceYear(),
                policy.getSummary(), policy.getBody(), policy.getSupportTarget(),
                policy.getSelectionCriteria(), policy.getSupportContent(),
                policy.getContact(), policy.getOrganization(),
                chunkTexts, referenceData);
    }

    public String combinedSourceText() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "summary", summary);
        appendSection(sb, "body", body);
        appendSection(sb, "supportTarget", supportTarget);
        appendSection(sb, "selectionCriteria", selectionCriteria);
        appendSection(sb, "supportContent", supportContent);
        if (referenceYear != null) sb.append("[referenceYear]\n").append(referenceYear).append("\n\n");
        for (int i = 0; i < chunkContents.size(); i++) {
            sb.append("[chunk-").append(i).append("]\n").append(chunkContents.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) sb.append("[").append(key).append("]\n").append(value).append("\n\n");
    }
}
```

- [ ] **2.9 — `GuideGenerationInput.of` 호출부 수정**

기존 `GuideGenerationService.generateGuide()` (라인 ~61) 에서 `GuideGenerationInput.of(policy, chunks)` → 임시로 `GuideGenerationInput.of(policy, chunks, null)`. 실제 reference 주입은 task 3.

```bash
grep -rn "GuideGenerationInput.of(" backend/src --include="*.java"
```

각 호출 지점에 3번째 인자로 `null` 임시 추가.

- [ ] **2.10 — `GuideResult` 에 `highlights` 매핑**

`backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java`:

```java
public record GuideResult(
        Long policyId,
        String oneLineSummary,
        List<GuideHighlightDto> highlights,
        GuidePairedSectionDto target,
        GuidePairedSectionDto criteria,
        GuidePairedSectionDto content,
        List<GuidePitfallDto> pitfalls,
        LocalDateTime updatedAt
) {

    public static GuideResult from(Guide guide) {
        GuideContent c = guide.getContent();
        return new GuideResult(
                guide.getPolicyId(),
                c.oneLineSummary(),
                c.highlights().stream().map(GuideHighlightDto::from).toList(),
                GuidePairedSectionDto.from(c.target()),
                GuidePairedSectionDto.from(c.criteria()),
                GuidePairedSectionDto.from(c.content()),
                c.pitfalls().stream().map(GuidePitfallDto::from).toList(),
                guide.getUpdatedAt());
    }

    public record GuideHighlightDto(String text, String sourceField) {
        static GuideHighlightDto from(GuideHighlight h) {
            return new GuideHighlightDto(h.text(), h.sourceField().name());
        }
    }
    // ... 기존 GuidePairedSectionDto, GuidePitfallDto 정의 유지
}
```

(기존 `GuideResult` 코드를 읽고 위 패턴에 맞춰 `highlights` 필드 + `GuideHighlightDto` 추가만 적용)

- [ ] **2.11 — `GuideResponse` 에 `highlights` 추가**

`backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java` 도 동일 패턴으로 `highlights: List<HighlightItem>` 필드 추가. `GuideResult` → `GuideResponse` 변환에 highlights 매핑 추가.

- [ ] **2.12 — 전체 빌드 + 테스트 통과 확인**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL. 모든 기존 테스트 회귀 없음. 새 GuideContentTest 통과.

- [ ] **2.13 — Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/domain/model/GuideHighlight.java \
        backend/src/main/java/com/youthfit/guide/domain/model/GuideContent.java \
        backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java \
        backend/src/main/java/com/youthfit/guide/application/dto/result/GuideResult.java \
        backend/src/main/java/com/youthfit/guide/presentation/dto/response/GuideResponse.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/test/java/com/youthfit/guide/domain/model/GuideContentTest.java
git commit -m "feat(guide): GuideContent.highlights 도메인 + 응답 DTO 추가"
```

---

## Task 3 — 시스템 프롬프트 / few-shot / user message / 스키마 / sourceHash

**목적**: LLM이 환산값·그룹 분리·highlights를 출력하도록 시스템 프롬프트를 강화하고, user message에 reference 환산표를 주입한다. JSON 스키마에 `highlights`를 `required` 로 추가. `Guide.sourceHash` 입력에 `referenceData.version` + `prompt.version` 포함 (배포 시 자동 재생성).

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/dto/command/GuideGenerationInput.java` (toReferenceText 헬퍼 추가)
- Test: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceHashTest.java`

### Steps

- [ ] **3.1 — `IncomeBracketReference.toContextText()` 헬퍼 추가**

LLM user message 주입용 텍스트 변환. `IncomeBracketReference.java` 에 메서드 추가:

```java
public String toContextText() {
    StringBuilder sb = new StringBuilder();
    sb.append("[참고 - 환산표 (").append(year).append("년 기준)]\n");
    appendMedian(sb, "1인 가구", HouseholdSize.ONE);
    appendMedian(sb, "2인 가구", HouseholdSize.TWO);
    sb.append("차상위계층 (기준중위소득 50%):\n  1인=")
      .append(formatManwon(nearPoor.get(HouseholdSize.ONE))).append(" / 2인=")
      .append(formatManwon(nearPoor.get(HouseholdSize.TWO))).append("\n");
    sb.append("주의: 위 값은 [원문 - 첨부]에 환산 금액이 명시되지 않은 경우에만 사용한다. 첨부에 명시된 값이 우선이다.\n");
    return sb.toString();
}

private void appendMedian(StringBuilder sb, String label, HouseholdSize size) {
    Map<Integer, Long> bySize = medianIncome.get(size);
    if (bySize == null || bySize.isEmpty()) return;
    sb.append("기준중위소득 (").append(label).append("):\n  ");
    bySize.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append(e.getKey()).append("%=").append(formatManwon(e.getValue())).append(" / "));
    sb.setLength(sb.length() - 3); // 마지막 " / " 제거
    sb.append("\n");
}

private String formatManwon(long won) {
    return String.format("%.1f만", won / 10000.0);
}
```

- [ ] **3.2 — `IncomeBracketReference.toContextText()` 단위 테스트 추가**

`IncomeBracketReferenceTest.java` 에:

```java
@Test
void toContextText_사람이_읽을_수_있는_포맷_생성() {
    IncomeBracketReference ref = new IncomeBracketReference(
            2025, 1,
            Map.of(HouseholdSize.ONE, Map.of(60, 1_435_208L, 100, 2_392_013L)),
            Map.of(HouseholdSize.ONE, 1_196_007L, HouseholdSize.TWO, 1_966_329L));

    String ctx = ref.toContextText();

    assertThat(ctx).contains("[참고 - 환산표 (2025년 기준)]");
    assertThat(ctx).contains("60%=143.5만");
    assertThat(ctx).contains("100%=239.2만");
    assertThat(ctx).contains("차상위계층");
    assertThat(ctx).contains("1인=119.6만");
}
```

`./gradlew test --tests "com.youthfit.policy.domain.model.IncomeBracketReferenceTest"` → PASS.

- [ ] **3.3 — `OpenAiChatClient.SYSTEM_PROMPT` 변경**

`backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java` 의 `SYSTEM_PROMPT` 상수를 다음으로 교체:

```java
private static final String SYSTEM_PROMPT = """
        너는 한국 청년 정책 가이드 작성 전문가다. 대상 독자는 정책 용어를 처음 접하는 20대 일반 청년이다.
        가장 중요한 임무는 복잡한 행정·법률 용어를 독자가 즉시 자신의 상황에 대입할 수 있는 직관적인 일상어로 번역하는 것이다.
        원문을 그대로 복사하여 붙여넣는 것은 실패로 간주한다.

        [작성 원칙]
        1. 정보 통제: 입력된 원문에만 근거하여 작성한다. 원문에 없는 조건, 금액, 기한, 자격을 절대 추가하지 않는다.
        2. 수치 보존: 원문에 명시된 모든 금액, 연령, 기간, 지역, 비율 수치는 누락 없이 보존한다.
        3. 용어 순화 (필수 매핑):
           - "무주택세대구성원" → "주민등록등본상 함께 올라가 있는 가족 전체가 집을 소유하지 않은 사람"
           - "차상위계층" → "기초생활수급자보다는 소득이 조금 높지만 일반 가구보다는 낮은 계층 (정부가 정한 기준 이하)"
           - "전년도 도시근로자 가구당 월평균소득 100% 이하" → "작년 도시 직장인 가구의 한 달 평균 소득 이하"
           - "임대의무기간" → "법으로 정해진 거주 의무 기간"
           - "입주자모집공고일" → "정부가 입주자를 모집한다고 공식 발표한 날"
        4. 문장 구조: 한 문장 당 한 가지의 조건만 담아, 짧고 능동적인 불릿 포인트 형태로 분할한다.
        5. 독립성 유지(중복 분리, 강화):
           - 분류 키워드(차상위 초과/이하, 일반공급/특별공급, 미혼/기혼·맞벌이, 1인/다인 가구 등)가 다르면 절대 한 group에 섞지 않는다.
           - 같은 group 안에 서로 다른 분류 키워드가 등장하면 group 분할 실패로 간주.
           - 각 group의 label에 분류명을 명시한다 (예: "차상위계층 이하 (소득 기준)").
        6. 어조: 객관적이고 명확한 단정형 어미("~다", "~함", 명사형 종결)를 사용한다. 친근한 어투("~해요", "~예요", "~드려요")는 절대 금지.
        7. 추정 금지: "~일 수 있다", "~로 추정" 등 가정 금지. 입력 [원문] 또는 [참고 - 환산표]에 명시되지 않은 환경값을 임의로 만들지 않는다.
        8. 완전한 재구성: 원문이 이미 쉬워 보이더라도 독자 관점에서 행동 지향적인 문장으로 100% 다시 쓴다.
        9. 환산값 표기 규칙:
           - "중위소득 N% 이하", "차상위계층" 같은 비율·분류 표기는 풀이에 환산 금액을 병기한다.
           - 우선순위:
             (a) [원문 - 첨부 청크]에 환산 금액이 명시되어 있으면 그 값을 그대로 인용. 가구원 수 범위가 명시되어 있으면 그 범위를 따른다.
             (b) (a)가 없으면 [참고 - 환산표]의 1·2인 가구 기준 금액을 사용한다.
           - 표기 형식: "중위소득 60% 이하 (2025년 기준 1인 가구 월 약 143만원, 2인 가구 월 약 233만원)"
           - 차상위계층 표기: "차상위계층 이하 (2025년 기준 1인 가구 월 약 119만원 이하)"
           - [참고 - 환산표]에도 없으면 비율만 표기 (만들어내지 않는다).
           - 같은 풀이 안에서 동일 비율이 반복 등장하면 환산값은 첫 등장에만 병기 (가독성).

        [출력 단위 — JSON]
        - oneLineSummary: 정책 정체를 누가/무엇을/얼마나 받는지 1~2문장.
        - highlights: 사용자가 PDF를 보지 않고도 정책의 핵심 특징을 파악할 수 있는 항목 3~6개.
          혜택의 강도, 차별점, 신청 시점/방법의 특이사항, 우대조건, 중복 수혜 가능 여부 등 긍정·중립 정보.
          각 항목 sourceField 라벨 필수 (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY).
        - target / criteria / content: 각각 supportTarget / selectionCriteria / supportContent 풀이 (groups 배열).
          입력이 비어있으면 null. groups 구조는 아래 [변환 예시] 참조.
        - pitfalls: 부정·함정·예외·제외 조건만 (자격 미달 트리거, 중복 수혜 제한, 사후 의무, 신청기한 외).
          긍정·중립 정보는 highlights로 보낸다. 각 항목 sourceField 라벨 필수.

        각 paired section의 groups 구조:
        - 분류가 명확히 구분되는 경우: 각 분류를 별도 group 객체로 만들어 label 필드에 분류명을 명시.
        - 분류가 없는 단순 나열: 단일 group 객체에 label은 null.
        - 한 group은 label(문자열 또는 null)과 items(불릿 문자열 배열)를 가진다. items는 절대 비울 수 없다.

        [변환 예시 1] supportTarget 원문:
        "입주자모집공고일 현재 해당 주택건설지역에 거주하는 무주택세대구성원으로서 해당 세대의 소득 및 보유자산이 국토교통부장관이 정하는 기준 이하인 자"
        → target output:
        {
          "groups": [
            { "label": null,
              "items": [
                "정부가 입주자를 모집한다고 공식 발표한 날 기준으로 해당 주택이 지어지는 지역에 거주 중일 것",
                "주민등록등본상 함께 올라가 있는 가족 전체가 집을 소유하지 않은 사람일 것",
                "가구의 소득과 자산(토지·건물·자동차)이 국토교통부가 정한 기준 이하일 것"
              ] }
          ]
        }

        [변환 예시 2] selectionCriteria 원문 (분류가 명확):
        "(일반공급) 전용면적 60㎡ 이하 공공분양 : 전년도 도시근로자 가구당 월평균소득 100% 이하 / (생애최초 특별공급) 전년도 도시근로자가구 평균소득 130% 이하"
        → criteria output:
        {
          "groups": [
            { "label": "일반공급 - 소득 기준",
              "items": ["전용면적 60㎡ 이하 공공분양 주택의 경우 작년 도시 직장인 가구의 한 달 평균 소득 이하일 때 신청 가능"] },
            { "label": "생애최초 특별공급 - 소득 기준",
              "items": ["작년 도시 직장인 가구의 한 달 평균 소득의 130% 이하일 때 신청 가능"] }
          ]
        }

        [변환 예시 3] 차상위 분류 분리 (정책 7번류):
        selectionCriteria 원문: "차상위계층 이하: 월 30만원 지원 / 차상위 초과 ~ 중위소득 60% 이하: 월 20만원 지원"
        → criteria output:
        {
          "groups": [
            { "label": "차상위계층 이하",
              "items": [
                "기초생활수급자보다는 소득이 조금 높지만 일반 가구보다는 낮은 계층 (2025년 기준 1인 가구 월 약 119만원 이하)에 해당",
                "월 30만원 지원"
              ] },
            { "label": "차상위 초과 ~ 중위소득 60% 이하",
              "items": [
                "차상위계층보다는 소득이 높지만 중위소득 60% 이하 (2025년 기준 1인 가구 월 약 143만원, 2인 가구 월 약 233만원) 까지 해당",
                "월 20만원 지원"
              ] }
          ]
        }

        [변환 예시 4] 환산값 PDF 우선:
        chunk 안에 "2025년 기준 1·2인 가구 월 138/230만원 이하" 가 명시되어 있다면, 풀이는 그 값을 그대로 인용한다.
        [참고 - 환산표]의 143만원 / 233만원 보다 우선.
        → 풀이 텍스트: "중위소득 60% 이하 (2025년 1인 가구 월 138만원, 2인 230만원 — 정책 본문 기준)"

        [변환 예시 5] highlights vs pitfalls 분리:
        같은 정책 입력에 대해
        - highlights (긍정·중립·차별점):
          { "text": "월 최대 20만원 월세 지원", "sourceField": "SUPPORT_CONTENT" }
          { "text": "다른 청년 주거 지원과 중복 수혜 가능", "sourceField": "SUPPORT_CONTENT" }
          { "text": "신청 후 평균 2주 내 지급 결정", "sourceField": "SUPPORT_CONTENT" }
        - pitfalls (부정·함정·예외):
          { "text": "월세 60만원 초과 주택은 대상 제외", "sourceField": "SUPPORT_TARGET" }
          { "text": "최초 신청일로부터 3개월 내 입주 확인 필요", "sourceField": "SUPPORT_CONTENT" }
        """;
```

- [ ] **3.4 — `OpenAiChatClient.buildUserMessage` 변경 — 환산표 주입**

```java
private String buildUserMessage(GuideGenerationInput input) {
    StringBuilder sb = new StringBuilder();
    sb.append("[정책 메타]\n");
    sb.append("title: ").append(input.title()).append("\n");
    if (input.referenceYear() != null) sb.append("referenceYear: ").append(input.referenceYear()).append("\n");
    if (input.organization() != null) sb.append("organization: ").append(input.organization()).append("\n");
    if (input.contact() != null) sb.append("contact: ").append(input.contact()).append("\n");
    sb.append("\n[원문]\n");
    sb.append(input.combinedSourceText());
    if (input.referenceData() != null) {
        sb.append("\n").append(input.referenceData().toContextText());
    }
    return sb.toString();
}
```

- [ ] **3.5 — `OpenAiChatClient.buildResponseFormat` 변경 — `highlights` 추가**

기존 `schema` Map 정의의 `required` 와 `properties` 에 `highlights` 추가. `pitfallSchema` 를 그대로 재사용:

```java
Map<String, Object> schema = Map.of(
        "type", "object",
        "additionalProperties", false,
        "required", List.of("oneLineSummary", "highlights", "target", "criteria", "content", "pitfalls"),
        "properties", Map.of(
                "oneLineSummary", Map.of("type", "string"),
                "highlights", Map.of("type", "array", "items", pitfallSchema),
                "target",   Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                "criteria", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                "content",  Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))),
                "pitfalls", Map.of("type", "array", "items", pitfallSchema)
        )
);
```

- [ ] **3.6 — `GuideGenerationService` 에 `IncomeBracketReferenceLoader` 주입 + `prompt.version` 상수**

```java
@Service
@RequiredArgsConstructor
public class GuideGenerationService {

    static final String PROMPT_VERSION = "v2";  // 프롬프트 / 스키마 변경 시 증분

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationService.class);

    private final GuideRepository guideRepository;
    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final GuideLlmProvider guideLlmProvider;
    private final GuideValidator guideValidator;
    private final IncomeBracketReferenceLoader referenceLoader;
    // ...
}
```

- [ ] **3.7 — `generateGuide` 본문에서 reference 주입 + computeHash 변경 (실패 테스트 작성)**

`GuideGenerationServiceHashTest.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.rag.domain.model.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuideGenerationServiceHashTest {

    @Test
    void hash_referenceData_version이_바뀌면_달라진다() {
        Policy policy = somePolicy();
        List<PolicyDocument> chunks = List.of();
        IncomeBracketReference v1 = ref(2025, 1);
        IncomeBracketReference v2 = ref(2025, 2);

        String h1 = GuideGenerationService.computeHashForTest(policy, chunks, v1);
        String h2 = GuideGenerationService.computeHashForTest(policy, chunks, v2);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hash_referenceData_연도가_바뀌면_달라진다() {
        Policy policy = somePolicy();
        IncomeBracketReference y2025 = ref(2025, 1);
        IncomeBracketReference y2026 = ref(2026, 1);

        String h1 = GuideGenerationService.computeHashForTest(policy, List.of(), y2025);
        String h2 = GuideGenerationService.computeHashForTest(policy, List.of(), y2026);

        assertThat(h1).isNotEqualTo(h2);
    }

    private IncomeBracketReference ref(int year, int version) {
        return new IncomeBracketReference(year, version,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_400_000L)),
                Map.of(HouseholdSize.ONE, 1_100_000L));
    }

    private Policy somePolicy() {
        // 기존 PolicyTest 패턴 따라 builder 사용. id=1L, title="X" 등 최소 필드.
        // (실제 fixture는 기존 테스트 helper 재사용)
        return Policy.builder()
                .id(1L).title("X").referenceYear(2025).build();
    }
}
```

- [ ] **3.8 — 테스트 fail 확인**

`./gradlew test --tests "com.youthfit.guide.application.service.GuideGenerationServiceHashTest"` → 컴파일 실패 (`computeHashForTest` 시그니처 불일치).

- [ ] **3.9 — `GuideGenerationService.computeHash` 변경 + reference 조회 추가**

```java
@Transactional
public GuideGenerationResult generateGuide(GenerateGuideCommand command) {
    Optional<Policy> policyOpt = policyRepository.findById(command.policyId());
    if (policyOpt.isEmpty()) {
        return new GuideGenerationResult(command.policyId(), false, "정책을 찾을 수 없습니다");
    }
    Policy policy = policyOpt.get();
    List<PolicyDocument> chunks = policyDocumentRepository.findByPolicyIdOrderByChunkIndex(command.policyId());

    IncomeBracketReference reference = resolveReference(policy.getReferenceYear());

    String hash = computeHash(policy, chunks, reference);
    Optional<Guide> existing = guideRepository.findByPolicyId(command.policyId());
    if (existing.isPresent() && !existing.get().hasChanged(hash)) {
        log.info("가이드 변경 없음, 재생성 스킵: policyId={}", command.policyId());
        return new GuideGenerationResult(command.policyId(), false, "변경 없음");
    }

    GuideGenerationInput input = GuideGenerationInput.of(policy, chunks, reference);
    GuideContent content = guideLlmProvider.generateGuide(input);

    // 기존 검증 로그
    List<String> missing = guideValidator.findMissingNumericTokens(input.combinedSourceText(), content);
    if (!missing.isEmpty()) {
        log.warn("가이드 풀이에 원문 숫자 토큰 누락: policyId={}, missing={}", command.policyId(), missing);
    }
    if (guideValidator.containsFriendlyTone(content)) {
        log.warn("가이드 풀이에 친근체 출현: policyId={}", command.policyId());
    }

    if (existing.isPresent()) {
        existing.get().regenerate(content, hash);
        guideRepository.save(existing.get());
    } else {
        guideRepository.save(Guide.builder()
                .policyId(command.policyId())
                .content(content)
                .sourceHash(hash)
                .build());
    }
    log.info("가이드 생성 완료: policyId={}", command.policyId());
    return new GuideGenerationResult(command.policyId(), true, "생성 완료");
}

private IncomeBracketReference resolveReference(Integer policyYear) {
    if (policyYear != null) {
        Optional<IncomeBracketReference> byYear = referenceLoader.findByYear(policyYear);
        if (byYear.isPresent()) return byYear.get();
        log.warn("referenceYear={} 에 매칭되는 yaml 없음 → findLatest() 사용", policyYear);
    }
    return referenceLoader.findLatest();
}

static String computeHashForTest(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
    return new GuideGenerationService(null, null, null, null, null, null)
            .computeHash(policy, chunks, reference);
}

private String computeHash(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
    StringBuilder sb = new StringBuilder();
    sb.append(safe(policy.getTitle()));
    sb.append(safe(policy.getSummary()));
    sb.append(safe(policy.getBody()));
    sb.append(safe(policy.getSupportTarget()));
    sb.append(safe(policy.getSelectionCriteria()));
    sb.append(safe(policy.getSupportContent()));
    sb.append(policy.getReferenceYear());
    chunks.forEach(c -> sb.append(c.getContent()));
    sb.append("|ref:").append(reference.year()).append(":").append(reference.version());
    sb.append("|prompt:").append(PROMPT_VERSION);
    return sha256(sb.toString());
}
```

(`computeHashForTest` 의 `null` 컨스트럭터 인자는 hash 계산에 dependencies 가 사용되지 않으므로 안전. 더 깔끔하게는 hash 계산을 별도 클래스로 분리할 수 있으나 현재 범위 외)

- [ ] **3.10 — 테스트 pass 확인**

`./gradlew test --tests "com.youthfit.guide.application.service.GuideGenerationServiceHashTest"` → PASS.

- [ ] **3.11 — 전체 빌드 + 회귀 테스트**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL. 기존 `GuideGenerationServiceTest` 등 회귀 통과 (Mock 의 LLM 응답에 `highlights` 필드를 비워도 `parseResponse` 가 빈 배열로 fallback 하므로 안전).

- [ ] **3.12 — Commit**

```bash
git add backend/src/main/java/com/youthfit/policy/domain/model/IncomeBracketReference.java \
        backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/policy/domain/model/IncomeBracketReferenceTest.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceHashTest.java
git commit -m "feat(guide): 시스템 프롬프트/few-shot/환산표 주입 + sourceHash 변경"
```

---

## Task 4 — `GuideValidator` 검증 4종 + 1회 LLM 재시도 루프

**목적**: 그룹 분리 위반 / 환산값 누락 / highlights 부족 / sourceField 유효성을 검증하고, 검증 1·2·3 위반 시 1회 LLM 재시도 (검증 피드백을 user message 끝에 추가).

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java`
- Modify: `backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java`
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java`
- Test: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java`

### Steps

- [ ] **4.1 — `GuideValidator` 검증 4종 실패 테스트 작성**

`GuideValidatorTest.java` (없으면 생성, 있으면 추가):

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GuideValidatorTest {

    private final GuideValidator validator = new GuideValidator();

    @Test
    void 그룹_분리_위반_차상위와_일반공급_섞임() {
        GuidePairedSection criteria = pairedSingleGroup(null, List.of(
                "차상위계층 이하인 경우",
                "일반공급 대상자인 경우"
        ));
        GuideContent content = content(criteria);

        ValidationReport report = validator.validate(content, "");

        assertThat(report.hasGroupMixViolation()).isTrue();
    }

    @Test
    void 환산값_누락_중위소득60_단독() {
        GuidePairedSection criteria = pairedSingleGroup(null, List.of(
                "중위소득 60% 이하 가구"
        ));
        GuideContent content = content(criteria);

        ValidationReport report = validator.validate(content, "");

        assertThat(report.hasMissingAmount()).isTrue();
    }

    @Test
    void 환산값_누락_없음_만원_병기() {
        GuidePairedSection criteria = pairedSingleGroup(null, List.of(
                "중위소득 60% 이하 (2025년 1인 가구 월 약 143만원) 가구"
        ));
        GuideContent content = content(criteria);

        ValidationReport report = validator.validate(content, "");

        assertThat(report.hasMissingAmount()).isFalse();
    }

    @Test
    void highlights_부족_2개() {
        GuideContent content = new GuideContent(
                "요약",
                List.of(
                        new GuideHighlight("a", GuideSourceField.BODY),
                        new GuideHighlight("b", GuideSourceField.BODY)
                ),
                null, null, null, List.of());

        ValidationReport report = validator.validate(content, "");

        assertThat(report.hasInsufficientHighlights()).isTrue();
    }

    @Test
    void highlights_충분_3개() {
        GuideContent content = new GuideContent(
                "요약",
                List.of(
                        new GuideHighlight("a", GuideSourceField.BODY),
                        new GuideHighlight("b", GuideSourceField.BODY),
                        new GuideHighlight("c", GuideSourceField.BODY)
                ),
                null, null, null, List.of());

        ValidationReport report = validator.validate(content, "");

        assertThat(report.hasInsufficientHighlights()).isFalse();
    }

    @Test
    void sourceField_유효성_빈_입력_가리키면_폐기() {
        // 입력 텍스트에서 selectionCriteria 가 비어있다는 정보 전달용 메서드 시그니처 설계 필요.
        // 본 테스트는 4-2 step 에서 시그니처와 함께 정의.
    }

    private GuidePairedSection pairedSingleGroup(String label, List<String> items) {
        return new GuidePairedSection(List.of(new GuideGroup(label, items)));
    }

    private GuideContent content(GuidePairedSection criteria) {
        return new GuideContent("요약",
                List.of(
                        new GuideHighlight("a", GuideSourceField.BODY),
                        new GuideHighlight("b", GuideSourceField.BODY),
                        new GuideHighlight("c", GuideSourceField.BODY)),
                null, criteria, null, List.of());
    }
}
```

- [ ] **4.2 — 테스트 fail 확인**

`./gradlew test --tests "com.youthfit.guide.application.service.GuideValidatorTest"` → 컴파일 실패 (`validate`, `ValidationReport` 미정의).

- [ ] **4.3 — `ValidationReport` record + `validate` 메서드 구현**

`GuideValidator.java` 에 추가:

```java
public record ValidationReport(
        boolean hasGroupMixViolation,
        boolean hasMissingAmount,
        boolean hasInsufficientHighlights,
        List<String> feedbackMessages
) {

    public boolean hasRetryTrigger() {
        return hasGroupMixViolation || hasMissingAmount || hasInsufficientHighlights;
    }

    public int violationCount() {
        int n = 0;
        if (hasGroupMixViolation) n++;
        if (hasMissingAmount) n++;
        if (hasInsufficientHighlights) n++;
        return n;
    }
}

private static final List<String> CATEGORY_KEYWORDS = List.of(
        "차상위", "일반공급", "특별공급", "신혼부부",
        "생애최초", "맞벌이", "다자녀", "기혼", "미혼"
);

private static final Pattern PERCENT_PATTERN = Pattern.compile("중위소득\\s*\\d+\\s*%|차상위");
private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+\\s*만원");

public ValidationReport validate(GuideContent content, String originalText) {
    boolean groupMix = checkGroupMix(content);
    boolean missingAmount = checkMissingAmount(content);
    boolean insufficientHighlights = content.highlights().size() < 3;

    List<String> feedback = new ArrayList<>();
    if (groupMix) feedback.add("일부 group의 items에 분류 키워드(차상위/일반공급/특별공급/신혼부부 등)가 2종 이상 섞여 있다. group을 분리하고 label에 분류명을 명시할 것.");
    if (missingAmount) feedback.add("'중위소득 N%' 또는 '차상위' 표기에 환산 금액(만원)이 병기되어 있지 않다. [참고 - 환산표]를 사용해 1·2인 가구 환산값을 병기할 것.");
    if (insufficientHighlights) feedback.add("highlights가 " + content.highlights().size() + "개. 최소 3개 이상 작성할 것 (긍정·중립·차별점).");

    return new ValidationReport(groupMix, missingAmount, insufficientHighlights, feedback);
}

private boolean checkGroupMix(GuideContent content) {
    return Stream.of(content.target(), content.criteria(), content.content())
            .filter(s -> s != null)
            .flatMap(s -> s.groups().stream())
            .anyMatch(g -> {
                String joined = String.join(" ", g.items());
                long count = CATEGORY_KEYWORDS.stream().filter(joined::contains).count();
                return count >= 2;
            });
}

private boolean checkMissingAmount(GuideContent content) {
    return Stream.of(content.target(), content.criteria(), content.content())
            .filter(s -> s != null)
            .flatMap(s -> s.groups().stream())
            .anyMatch(g -> {
                String joined = String.join(" ", g.items());
                if (g.label() != null) joined = g.label() + " " + joined;
                if (PERCENT_PATTERN.matcher(joined).find()) {
                    return !AMOUNT_PATTERN.matcher(joined).find();
                }
                return false;
            });
}
```

(`Stream` 등 import 추가 필요)

- [ ] **4.4 — 테스트 pass 확인**

`./gradlew test --tests "com.youthfit.guide.application.service.GuideValidatorTest"` → PASS (sourceField 유효성 테스트 제외, 다음 step 에서 추가).

- [ ] **4.5 — sourceField 유효성 검증 메서드 추가 (실패 테스트)**

`GuideValidatorTest.java` 의 `sourceField_유효성_빈_입력_가리키면_폐기` 케이스를 채움:

```java
@Test
void filterInvalidSourceFields_빈_입력_필드_가리키는_pitfall_제거() {
    GuidePitfall valid = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);
    GuidePitfall invalid = new GuidePitfall("...", GuideSourceField.SELECTION_CRITERIA);
    List<GuidePitfall> filtered = validator.filterInvalidSourceFields(
            List.of(valid, invalid),
            sourceFieldsSet("SUPPORT_TARGET")  // SELECTION_CRITERIA 는 비어있다고 가정
    );

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0)).isEqualTo(valid);
}

private Set<GuideSourceField> sourceFieldsSet(String... names) {
    return Arrays.stream(names).map(GuideSourceField::valueOf)
            .collect(Collectors.toSet());
}
```

- [ ] **4.6 — `filterInvalidSourceFields` 구현**

`GuideValidator.java` 에:

```java
public <T> List<T> filterInvalidSourceFields(
        List<T> items, Set<GuideSourceField> nonEmptyFields,
        Function<T, GuideSourceField> sourceFieldExtractor) {
    return items.stream()
            .filter(it -> nonEmptyFields.contains(sourceFieldExtractor.apply(it)))
            .toList();
}
```

(이를 highlights·pitfalls 양쪽에 사용)

호출 예 (`GuideGenerationService` 에서 적용 — 다음 step):

```java
Set<GuideSourceField> nonEmpty = new HashSet<>();
if (notBlank(policy.getSupportTarget())) nonEmpty.add(GuideSourceField.SUPPORT_TARGET);
if (notBlank(policy.getSelectionCriteria())) nonEmpty.add(GuideSourceField.SELECTION_CRITERIA);
if (notBlank(policy.getSupportContent())) nonEmpty.add(GuideSourceField.SUPPORT_CONTENT);
if (notBlank(policy.getBody())) nonEmpty.add(GuideSourceField.BODY);

List<GuideHighlight> filteredHighlights = guideValidator.filterInvalidSourceFields(
        content.highlights(), nonEmpty, GuideHighlight::sourceField);
List<GuidePitfall> filteredPitfalls = guideValidator.filterInvalidSourceFields(
        content.pitfalls(), nonEmpty, GuidePitfall::sourceField);

GuideContent finalContent = new GuideContent(
        content.oneLineSummary(), filteredHighlights,
        content.target(), content.criteria(), content.content(), filteredPitfalls);
```

- [ ] **4.7 — 검증 테스트 pass 확인**

`./gradlew test --tests "com.youthfit.guide.application.service.GuideValidatorTest"` → 모두 PASS.

- [ ] **4.8 — `GuideLlmProvider` 에 재시도용 메서드 추가**

`backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java`:

```java
package com.youthfit.guide.application.port;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;
import java.util.List;

public interface GuideLlmProvider {
    GuideContent generateGuide(GuideGenerationInput input);
    GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages);
}
```

- [ ] **4.9 — `OpenAiChatClient.regenerateWithFeedback` 구현**

`buildUserMessage` 끝에 피드백 블록을 추가하는 오버로드 메서드 작성:

```java
@Override
public GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages) {
    Map<String, Object> requestBody = Map.of(
            "model", properties.getModel(),
            "max_tokens", properties.getMaxTokens(),
            "messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", buildUserMessageWithFeedback(input, feedbackMessages))
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
        log.error("OpenAI Chat API 재시도 실패: policyId={}", input.policyId());
        throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 재생성 실패");
    }
    String json = response.get("choices").get(0).get("message").get("content").asText();
    return parseResponse(json);
}

private String buildUserMessageWithFeedback(GuideGenerationInput input, List<String> feedback) {
    String base = buildUserMessage(input);
    StringBuilder sb = new StringBuilder(base);
    sb.append("\n[이전 응답 검증 실패 — 다음을 고쳐서 다시 작성할 것]\n");
    feedback.forEach(f -> sb.append("- ").append(f).append("\n"));
    return sb.toString();
}
```

- [ ] **4.10 — `GuideGenerationService` 재시도 루프 (실패 테스트 작성)**

`GuideGenerationServiceRetryTest.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.application.dto.command.GenerateGuideCommand;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.*;
import com.youthfit.guide.domain.repository.GuideRepository;
import com.youthfit.policy.application.port.IncomeBracketReferenceLoader;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GuideGenerationServiceRetryTest {

    @Test
    void 1차_위반시_재시도_호출_2차_통과시_2차_저장() {
        GuideRepository guideRepo = mock(GuideRepository.class);
        PolicyRepository policyRepo = mock(PolicyRepository.class);
        PolicyDocumentRepository docRepo = mock(PolicyDocumentRepository.class);
        GuideLlmProvider llm = mock(GuideLlmProvider.class);
        IncomeBracketReferenceLoader refLoader = mock(IncomeBracketReferenceLoader.class);

        Policy policy = Policy.builder().id(1L).title("X").referenceYear(2025)
                .selectionCriteria("비어있지 않은 텍스트").build();
        when(policyRepo.findById(1L)).thenReturn(Optional.of(policy));
        when(docRepo.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(refLoader.findByYear(2025)).thenReturn(Optional.of(refOf()));
        when(guideRepo.findByPolicyId(1L)).thenReturn(Optional.empty());

        // 1차 응답: highlights 1개 (부족) + 정상
        GuideContent firstResponse = contentWithHighlights(1);
        // 2차 응답: highlights 3개 (통과)
        GuideContent secondResponse = contentWithHighlights(3);

        when(llm.generateGuide(any())).thenReturn(firstResponse);
        when(llm.regenerateWithFeedback(any(), any())).thenReturn(secondResponse);

        GuideGenerationService service = new GuideGenerationService(
                guideRepo, policyRepo, docRepo, llm, new GuideValidator(), refLoader);

        service.generateGuide(new GenerateGuideCommand(1L));

        verify(llm).generateGuide(any());
        verify(llm).regenerateWithFeedback(any(), any());
        ArgumentCaptor<Guide> savedCaptor = ArgumentCaptor.forClass(Guide.class);
        verify(guideRepo).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getContent().highlights()).hasSize(3);
    }

    @Test
    void 2차도_위반시_위반_적은_쪽_저장() {
        GuideRepository guideRepo = mock(GuideRepository.class);
        PolicyRepository policyRepo = mock(PolicyRepository.class);
        PolicyDocumentRepository docRepo = mock(PolicyDocumentRepository.class);
        GuideLlmProvider llm = mock(GuideLlmProvider.class);
        IncomeBracketReferenceLoader refLoader = mock(IncomeBracketReferenceLoader.class);

        Policy policy = Policy.builder().id(1L).title("X").referenceYear(2025).build();
        when(policyRepo.findById(1L)).thenReturn(Optional.of(policy));
        when(docRepo.findByPolicyIdOrderByChunkIndex(1L)).thenReturn(List.of());
        when(refLoader.findByYear(2025)).thenReturn(Optional.of(refOf()));
        when(guideRepo.findByPolicyId(1L)).thenReturn(Optional.empty());

        GuideContent first = contentWithHighlights(1);   // 위반 1
        GuideContent second = contentWithHighlights(0);  // 위반 1 (highlights 0)

        when(llm.generateGuide(any())).thenReturn(first);
        when(llm.regenerateWithFeedback(any(), any())).thenReturn(second);

        new GuideGenerationService(guideRepo, policyRepo, docRepo, llm, new GuideValidator(), refLoader)
                .generateGuide(new GenerateGuideCommand(1L));

        ArgumentCaptor<Guide> savedCaptor = ArgumentCaptor.forClass(Guide.class);
        verify(guideRepo).save(savedCaptor.capture());
        // 동률이거나 1차가 적으면 1차 우선 — first(highlights 1) 가 second(0) 보다 violation 적음
        assertThat(savedCaptor.getValue().getContent().highlights()).hasSize(1);
    }

    private IncomeBracketReference refOf() {
        return new IncomeBracketReference(2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_208L)),
                Map.of(HouseholdSize.ONE, 1_196_007L));
    }

    private GuideContent contentWithHighlights(int n) {
        List<GuideHighlight> hs = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) hs.add(new GuideHighlight("h" + i, GuideSourceField.BODY));
        return new GuideContent("요약", hs, null, null, null, List.of());
    }
}
```

- [ ] **4.11 — 테스트 fail 확인**

`./gradlew test --tests "com.youthfit.guide.application.service.GuideGenerationServiceRetryTest"` → 실패 (재시도 루프 미구현).

- [ ] **4.12 — `GuideGenerationService` 재시도 루프 구현**

`generateGuide` 본문에서 LLM 호출 부분을 다음으로 교체:

```java
GuideGenerationInput input = GuideGenerationInput.of(policy, chunks, reference);
GuideContent firstResponse = guideLlmProvider.generateGuide(input);
GuideValidator.ValidationReport firstReport = guideValidator.validate(firstResponse, input.combinedSourceText());

GuideContent finalResponse;
if (firstReport.hasRetryTrigger()) {
    log.info("가이드 검증 위반으로 재시도: policyId={}, violations={}", command.policyId(), firstReport.feedbackMessages());
    GuideContent secondResponse = guideLlmProvider.regenerateWithFeedback(input, firstReport.feedbackMessages());
    GuideValidator.ValidationReport secondReport = guideValidator.validate(secondResponse, input.combinedSourceText());

    if (secondReport.violationCount() < firstReport.violationCount()) {
        finalResponse = secondResponse;
        if (secondReport.hasRetryTrigger()) {
            log.warn("재시도 후에도 검증 위반 (감소): policyId={}, violations={}", command.policyId(), secondReport.feedbackMessages());
        }
    } else {
        finalResponse = firstResponse;
        log.warn("재시도가 개선되지 않음, 1차 응답 저장: policyId={}", command.policyId());
    }
} else {
    finalResponse = firstResponse;
}

// 검증 4: sourceField 유효성 (해당 항목 폐기)
finalResponse = filterInvalidSourceFields(finalResponse, policy);

// 기존 로깅 검증 (5·6)
List<String> missing = guideValidator.findMissingNumericTokens(input.combinedSourceText(), finalResponse);
if (!missing.isEmpty()) {
    log.warn("가이드 풀이에 원문 숫자 토큰 누락: policyId={}, missing={}", command.policyId(), missing);
}
if (guideValidator.containsFriendlyTone(finalResponse)) {
    log.warn("가이드 풀이에 친근체 출현: policyId={}", command.policyId());
}
```

`filterInvalidSourceFields` 헬퍼:

```java
private GuideContent filterInvalidSourceFields(GuideContent c, Policy p) {
    Set<GuideSourceField> nonEmpty = new HashSet<>();
    if (notBlank(p.getSupportTarget())) nonEmpty.add(GuideSourceField.SUPPORT_TARGET);
    if (notBlank(p.getSelectionCriteria())) nonEmpty.add(GuideSourceField.SELECTION_CRITERIA);
    if (notBlank(p.getSupportContent())) nonEmpty.add(GuideSourceField.SUPPORT_CONTENT);
    if (notBlank(p.getBody())) nonEmpty.add(GuideSourceField.BODY);

    List<GuideHighlight> hs = guideValidator.filterInvalidSourceFields(
            c.highlights(), nonEmpty, GuideHighlight::sourceField);
    List<GuidePitfall> ps = guideValidator.filterInvalidSourceFields(
            c.pitfalls(), nonEmpty, GuidePitfall::sourceField);

    return new GuideContent(c.oneLineSummary(), hs, c.target(), c.criteria(), c.content(), ps);
}

private boolean notBlank(String s) { return s != null && !s.isBlank(); }
```

- [ ] **4.13 — 테스트 pass 확인**

```
./gradlew test --tests "com.youthfit.guide.application.service.GuideGenerationServiceRetryTest"
./gradlew test --tests "com.youthfit.guide.application.service.GuideValidatorTest"
```

Expected: 모두 PASS.

- [ ] **4.14 — 전체 빌드**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL. 회귀 없음.

- [ ] **4.15 — Commit**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java \
        backend/src/main/java/com/youthfit/guide/application/port/GuideLlmProvider.java \
        backend/src/main/java/com/youthfit/guide/infrastructure/external/OpenAiChatClient.java \
        backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java
git commit -m "feat(guide): GuideValidator 검증 4종 + 1회 LLM 재시도 루프"
```

---

## Task 5 — 프론트: `SourceLinkedListCard` + `HighlightsCard` + 첨부 바로가기

**목적**: 기존 `PitfallsCard.tsx` 와 동일 구조의 `HighlightsCard` 를 추가하고, 두 카드의 공통 부분을 `SourceLinkedListCard` 로 추출. 두 카드 헤더 우측에 `📎 원본 첨부` 버튼 추가.

**Files:**
- Create: `frontend/src/components/policy/SourceLinkedListCard.tsx`
- Create: `frontend/src/components/policy/HighlightsCard.tsx`
- Modify: `frontend/src/components/policy/PitfallsCard.tsx`
- Modify: `frontend/src/types/policy.ts`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`
- Modify: 첨부 섹션 컴포넌트 (id 부여)
- Test: `frontend/src/components/policy/__tests__/SourceLinkedListCard.test.tsx`
- Test: `frontend/src/components/policy/__tests__/HighlightsCard.test.tsx`

### Steps

- [ ] **5.1 — `types/policy.ts` 에 `GuideHighlight` 타입 추가**

기존 파일에 다음 타입 추가:

```typescript
export type GuideHighlight = {
  text: string;
  sourceField: GuideSourceField;
};

// 기존 GuideResponse 타입에 highlights 필드 추가
export type GuideResponse = {
  policyId: number;
  oneLineSummary: string;
  highlights: GuideHighlight[];
  paired: { /* 기존 */ };
  pitfalls: GuidePitfall[];
  updatedAt: string;
};
```

(기존 타입 정의를 읽고 정확한 형태에 맞춰 추가)

- [ ] **5.2 — `SourceLinkedListCard` 실패 테스트 작성**

`frontend/src/components/policy/__tests__/SourceLinkedListCard.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SourceLinkedListCard } from '../SourceLinkedListCard';

describe('SourceLinkedListCard', () => {
  it('아이템이 비어있으면 null 렌더', () => {
    const { container } = render(
      <SourceLinkedListCard
        title="테스트"
        emoji="🌟"
        tone="indigo"
        items={[]}
        attachments={[]}
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('아이템과 출처 라벨 렌더', () => {
    render(
      <SourceLinkedListCard
        title="이 정책의 특징"
        emoji="🌟"
        tone="indigo"
        items={[{ text: '월 20만원 지원', sourceField: 'SUPPORT_CONTENT' }]}
        attachments={[]}
      />
    );

    expect(screen.getByText('이 정책의 특징')).toBeInTheDocument();
    expect(screen.getByText(/월 20만원 지원/)).toBeInTheDocument();
    expect(screen.getByText(/지원내용/)).toBeInTheDocument();
  });

  it('첨부 1개일 때 새 탭 링크', () => {
    render(
      <SourceLinkedListCard
        title="이 정책의 특징"
        emoji="🌟"
        tone="indigo"
        items={[{ text: 'a', sourceField: 'BODY' }]}
        attachments={[{ id: 1, name: 'pdf', url: 'https://example.com/a.pdf' }]}
      />
    );
    const link = screen.getByRole('link', { name: /원본 첨부/ });
    expect(link).toHaveAttribute('href', 'https://example.com/a.pdf');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('첨부 2개 이상일 때 버튼 + AttachmentSection 스크롤', () => {
    const scrollSpy = vi.fn();
    Element.prototype.scrollIntoView = scrollSpy;

    render(
      <SourceLinkedListCard
        title="t"
        emoji="🌟"
        tone="indigo"
        items={[{ text: 'a', sourceField: 'BODY' }]}
        attachments={[
          { id: 1, name: 'a', url: 'x' },
          { id: 2, name: 'b', url: 'y' },
        ]}
      />
    );
    const button = screen.getByRole('button', { name: /원본 첨부/ });
    expect(button).toBeInTheDocument();
  });
});
```

- [ ] **5.3 — 테스트 fail 확인**

```
cd frontend
npm test -- src/components/policy/__tests__/SourceLinkedListCard.test.tsx
```

Expected: 컴파일 실패 (`SourceLinkedListCard` 미정의).

- [ ] **5.4 — `SourceLinkedListCard` 구현**

`frontend/src/components/policy/SourceLinkedListCard.tsx`:

```tsx
import { scrollAndHighlight } from '@/lib/scrollHighlight';
import type { GuideSourceField } from '@/types/policy';

const SOURCE_LABELS: Record<GuideSourceField, string> = {
  SUPPORT_TARGET: '지원대상',
  SELECTION_CRITERIA: '선정기준',
  SUPPORT_CONTENT: '지원내용',
  BODY: '정책 본문',
};

const SCROLL_TARGETS: Record<GuideSourceField, string> = {
  SUPPORT_TARGET: 'paired-supportTarget',
  SELECTION_CRITERIA: 'paired-selectionCriteria',
  SUPPORT_CONTENT: 'paired-supportContent',
  BODY: 'policy-summary-section',
};

const TONE_CLASSES = {
  indigo: {
    container: 'border-indigo-200 bg-indigo-50/50',
    title: 'text-indigo-900',
    button: 'border-indigo-300 text-indigo-800 hover:bg-indigo-100',
  },
  amber: {
    container: 'border-amber-200 bg-amber-50/50',
    title: 'text-amber-900',
    button: 'border-amber-300 text-amber-800 hover:bg-amber-100',
  },
} as const;

interface Item {
  text: string;
  sourceField: GuideSourceField;
}

interface AttachmentRef {
  id: number;
  name: string;
  url: string;
}

interface Props {
  title: string;
  emoji: string;
  tone: keyof typeof TONE_CLASSES;
  items: Item[];
  attachments: AttachmentRef[];
}

export function SourceLinkedListCard({ title, emoji, tone, items, attachments }: Props) {
  if (!items.length) return null;
  const t = TONE_CLASSES[tone];

  const renderAttachmentTrigger = () => {
    if (attachments.length === 0) return null;
    if (attachments.length === 1) {
      return (
        <a
          href={attachments[0].url}
          target="_blank"
          rel="noopener noreferrer"
          className={`inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
        >
          📎 원본 첨부
        </a>
      );
    }
    return (
      <button
        type="button"
        onClick={() => scrollAndHighlight('attachment-section')}
        className={`inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
      >
        📎 원본 첨부
      </button>
    );
  };

  return (
    <section className={`mb-6 rounded-2xl border p-6 ${t.container}`}>
      <div className="mb-4 flex items-center justify-between">
        <h2 className={`flex items-center gap-2 text-base font-semibold ${t.title}`}>
          <span aria-hidden>{emoji}</span>
          {title}
        </h2>
        {renderAttachmentTrigger()}
      </div>
      <ul className="space-y-3">
        {items.map((it, i) => (
          <li key={i} className="text-sm text-neutral-800">
            <p className="mb-1">• {it.text}</p>
            <button
              type="button"
              onClick={() => scrollAndHighlight(SCROLL_TARGETS[it.sourceField])}
              className={`ml-3 inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
            >
              {SOURCE_LABELS[it.sourceField]} ↗
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
```

- [ ] **5.5 — 테스트 pass 확인**

```
npm test -- src/components/policy/__tests__/SourceLinkedListCard.test.tsx
```

Expected: PASS.

- [ ] **5.6 — `HighlightsCard` 작성**

`frontend/src/components/policy/HighlightsCard.tsx`:

```tsx
import type { GuideHighlight } from '@/types/policy';
import { SourceLinkedListCard } from './SourceLinkedListCard';

interface AttachmentRef {
  id: number;
  name: string;
  url: string;
}

interface Props {
  highlights: GuideHighlight[];
  attachments: AttachmentRef[];
}

export function HighlightsCard({ highlights, attachments }: Props) {
  return (
    <SourceLinkedListCard
      title="이 정책의 특징"
      emoji="🌟"
      tone="indigo"
      items={highlights}
      attachments={attachments}
    />
  );
}
```

- [ ] **5.7 — `HighlightsCard` 테스트 작성**

`frontend/src/components/policy/__tests__/HighlightsCard.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { HighlightsCard } from '../HighlightsCard';

describe('HighlightsCard', () => {
  it('비어있으면 null', () => {
    const { container } = render(<HighlightsCard highlights={[]} attachments={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('헤더와 항목 렌더', () => {
    render(
      <HighlightsCard
        highlights={[
          { text: '월 20만원', sourceField: 'SUPPORT_CONTENT' },
          { text: '중복 가능', sourceField: 'SUPPORT_CONTENT' },
          { text: '2주 내 지급', sourceField: 'SUPPORT_CONTENT' },
        ]}
        attachments={[]}
      />
    );
    expect(screen.getByText('이 정책의 특징')).toBeInTheDocument();
    expect(screen.getByText(/월 20만원/)).toBeInTheDocument();
  });
});
```

`npm test -- src/components/policy/__tests__/HighlightsCard.test.tsx` → PASS.

- [ ] **5.8 — `PitfallsCard` 를 `SourceLinkedListCard` 로 재작성**

`frontend/src/components/policy/PitfallsCard.tsx` 전체 교체:

```tsx
import type { GuidePitfall } from '@/types/policy';
import { SourceLinkedListCard } from './SourceLinkedListCard';

interface AttachmentRef {
  id: number;
  name: string;
  url: string;
}

interface Props {
  pitfalls: GuidePitfall[];
  attachments: AttachmentRef[];
}

export function PitfallsCard({ pitfalls, attachments }: Props) {
  return (
    <SourceLinkedListCard
      title="놓치기 쉬운 점"
      emoji="⚠️"
      tone="amber"
      items={pitfalls}
      attachments={attachments}
    />
  );
}
```

- [ ] **5.9 — `PolicyDetailPage` 변경 — `HighlightsCard` 추가 + `PitfallsCard` props 변경**

`PolicyDetailPage.tsx` 에서:
1. 한 줄 요약 카드 다음 위치에 `<HighlightsCard highlights={guide.highlights} attachments={policy.attachments ?? []} />` 추가
2. 기존 `<PitfallsCard pitfalls={guide.pitfalls} />` → `<PitfallsCard pitfalls={guide.pitfalls} attachments={policy.attachments ?? []} />`
3. `AttachmentSection` 컴포넌트 (또는 첨부 목록 영역) 의 외부 `<section>` 에 `id="attachment-section"` 부여

```bash
grep -n "PitfallsCard\|GuideSummaryCard\|AttachmentSection" frontend/src/pages/PolicyDetailPage.tsx
```

위 명령으로 변경 위치 확인 후 적용.

- [ ] **5.10 — 프론트 빌드 + 전체 테스트**

```
cd frontend
npm run build
npm test
```

Expected: 빌드 성공, 전체 테스트 PASS.

- [ ] **5.11 — 개발 서버 띄워 화면 확인 (수동)**

```
npm run dev
```

브라우저에서 `http://localhost:5173/policies/1` 같은 URL 진입 → 한 줄 요약 카드 ↓ HighlightsCard ↓ 페어드 섹션 ↓ PitfallsCard 렌더 확인. 첨부 1개 정책에서 헤더 우측 `📎 원본 첨부` 가 새 탭 링크. 첨부 2개+ 정책에서는 클릭 시 페이지 하단 첨부 섹션으로 스크롤.

이 단계는 task 6 e2e 검증과 결합 — 백엔드가 highlights 를 채워주는 시점 후에 의미 있는 내용 확인 가능.

- [ ] **5.12 — Commit**

```bash
git add frontend/src/components/policy/SourceLinkedListCard.tsx \
        frontend/src/components/policy/HighlightsCard.tsx \
        frontend/src/components/policy/PitfallsCard.tsx \
        frontend/src/components/policy/__tests__/SourceLinkedListCard.test.tsx \
        frontend/src/components/policy/__tests__/HighlightsCard.test.tsx \
        frontend/src/types/policy.ts \
        frontend/src/pages/PolicyDetailPage.tsx
# AttachmentSection 관련 파일이 따로 수정되었으면 추가
git commit -m "feat(frontend): SourceLinkedListCard 공통 추출 + HighlightsCard 신규 + 첨부 바로가기"
```

---

## Task 6 — 26년 yaml 데이터 + prompt.version 증분 + e2e 검증

**목적**: 26년 보건복지부 발표값을 확인하여 `2026.yaml` 추가 (발표 전이면 25년 yaml 그대로 운영). 모든 정책 가이드가 자동 재생성되도록 `PROMPT_VERSION` 증분. 통합 e2e로 정책 7번류 fixture 동작 확인.

**Files:**
- Create (조건부): `backend/src/main/resources/income-bracket/2026.yaml`
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` (PROMPT_VERSION = "v2.1" 등)
- Test: `backend/src/test/java/com/youthfit/guide/integration/GuideGenerationE2ETest.java` (선택)

### Steps

- [ ] **6.1 — 26년 보건복지부 고시값 검색**

다음 URL 또는 검색어로 2026년 적용 기준중위소득 발표 자료 확인:

- https://www.mohw.go.kr/board.es?mid=a10401000000&bid=0008 (보건복지부 보도자료)
- 검색어: "2026년 기준중위소득 보건복지부 고시"
- 통상 발표 시점: 매년 7~8월 (전년도 8월에 다음 연도 값 발표). 2026년 적용 값은 2025년 7~8월 발표.

발표값을 확인했다면 6.2로, 발표 전이거나 확인 불가하면 6.4로 건너뛴다.

- [ ] **6.2 — `2026.yaml` 작성**

`backend/src/main/resources/income-bracket/2026.yaml` (실제 발표값으로 채움. 25년 yaml 구조와 동일):

```yaml
year: 2026
version: 1
unit: KRW_MONTH
note: "20YY년 M월 보건복지부 고시 기준중위소득 (적용 연도 2026)"
medianIncome:
  "1":
    "10":  <발표값>
    "20":  <발표값>
    # ... 17개 비율 모두
  "2":
    # ...
nearPoor:
  "1": <발표값>
  "2": <발표값>
```

> 모든 17×2+2 = 36개 값을 채울 것. 누락 없이.

- [ ] **6.3 — `2026.yaml` 로드 테스트 추가**

`YamlIncomeBracketReferenceLoaderTest.java` 에:

```java
@Test
void findByYear_2026_yaml도_로드() {
    YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
    loader.load();

    Optional<IncomeBracketReference> ref = loader.findByYear(2026);
    assertThat(ref).isPresent();
    assertThat(ref.get().findAmount(HouseholdSize.ONE, 60)).isPresent();
}

@Test
void findLatest_2026이_최신() {
    YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
    loader.load();

    assertThat(loader.findLatest().year()).isEqualTo(2026);
}
```

- [ ] **6.4 — `PROMPT_VERSION` 증분**

`GuideGenerationService.java`:

```java
static final String PROMPT_VERSION = "v2";  // 본 사이클 = v2 (이전 시스템: v1, 본 commit 에서 증분)
```

(이미 task 3 에서 v2 로 설정. 6.4 는 yaml 추가 시 새 buy-in 을 위해 v2.1 로 한 번 더 증분 하지 않음 — yaml `version` 변경이 sourceHash invalidate 를 이미 트리거하므로 prompt.version 추가 증분은 불필요)

- [ ] **6.5 — 정책 7번 fixture e2e 통합 테스트 작성 (선택)**

`backend/src/test/java/com/youthfit/guide/integration/GuideGenerationE2ETest.java`:

```java
@SpringBootTest
@Transactional
class GuideGenerationE2ETest {

    @Autowired GuideGenerationService service;
    @Autowired PolicyRepository policyRepository;
    @MockBean GuideLlmProvider llm;

    @Test
    void 정책7번류_차상위_분류_분리_e2e() {
        // 차상위 초과/이하 selectionCriteria 가진 fixture 정책 저장
        Policy policy = somePolicyWithCriteria(
                "차상위계층 이하: 월 30만원 / 차상위 초과 ~ 중위소득 60% 이하: 월 20만원");
        policyRepository.save(policy);

        // LLM mock: 정상 응답 (group 분리 + 환산값 + highlights 3개)
        GuideContent normalResponse = correctResponseFor(policy);
        when(llm.generateGuide(any())).thenReturn(normalResponse);

        service.generateGuide(new GenerateGuideCommand(policy.getId()));

        // 가이드가 저장되었고 group 이 2개로 분리되어 있는지 검증
        Guide saved = guideRepository.findByPolicyId(policy.getId()).orElseThrow();
        assertThat(saved.getContent().criteria().groups()).hasSize(2);
    }
}
```

(테스트 헬퍼는 기존 `@SpringBootTest` 패턴 재사용)

- [ ] **6.6 — 전체 빌드 + 백엔드 통합 테스트**

```
cd backend
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **6.7 — 프론트 / 백엔드 동시 띄워 수동 검증**

```
# 터미널 1
cd backend && ./gradlew bootRun

# 터미널 2
cd frontend && npm run dev
```

브라우저에서 차상위 분류가 있는 정책 (정책 ID 7) 진입:
- 한 줄 요약 카드 ↓ 이 정책의 특징 ↓ 페어드 섹션 (criteria 가 group 2개로 분리: "차상위계층 이하" / "차상위 초과 ~ 중위소득 60% 이하") ↓ 놓치기 쉬운 점
- 풀이 텍스트에 "(2025년 기준 1인 가구 월 약 N만원)" 환산값 병기
- highlights 카드 헤더 우측 `📎 원본 첨부` 동작 확인

가이드가 자동 재생성되지 않았다면 `POST /api/v1/guides/generate` 로 수동 트리거.

- [ ] **6.8 — Commit (조건부 — 26년 yaml 추가했을 때만)**

```bash
# 26년 yaml 추가했을 경우
git add backend/src/main/resources/income-bracket/2026.yaml \
        backend/src/test/java/com/youthfit/policy/infrastructure/external/YamlIncomeBracketReferenceLoaderTest.java
git commit -m "chore(guide): 2026년 reference yaml 추가 + 통합 검증"
```

26년 발표값을 확인 못 했다면 본 commit 은 비워두고 task 6 종료. 발표 후 별도 PR 로 yaml 추가.

---

## 검증 / 자체 점검

- [ ] **모든 task 완료 후 전체 빌드**

```
cd backend && ./gradlew build && cd ../frontend && npm run build && npm test
```

Expected: 백엔드 BUILD SUCCESSFUL + 프론트 빌드 성공 + 전체 테스트 PASS.

- [ ] **PR 생성 전 git log 확인**

```
git log main..HEAD --oneline
```

기대 commit (6개):
1. `feat(policy): IncomeBracketReference 도메인 + yaml + loader 추가`
2. `feat(guide): GuideContent.highlights 도메인 + 응답 DTO 추가`
3. `feat(guide): 시스템 프롬프트/few-shot/환산표 주입 + sourceHash 변경`
4. `feat(guide): GuideValidator 검증 4종 + 1회 LLM 재시도 루프`
5. `feat(frontend): SourceLinkedListCard 공통 추출 + HighlightsCard 신규 + 첨부 바로가기`
6. (조건부) `chore(guide): 2026년 reference yaml 추가 + 통합 검증`

- [ ] **단일 PR 생성**

`create-pr` 스킬 사용. spec 링크와 결정 로그를 PR 본문에 포함.

---

## 자체 검토 (writing-plans 스킬 §Self-Review)

**1. Spec coverage**:
- §1 목표 (그룹 분리·환산값·highlights·재시도) → Task 3·4 모두 커버
- §5 도메인 모델 (`IncomeBracketReference`, `GuideHighlight`, `GuideContent`) → Task 1·2 커버
- §7 프롬프트 / user message / 스키마 → Task 3 커버
- §8 검증 4종 + 재시도 → Task 4 커버
- §9 UI (HighlightsCard + 첨부 바로가기) → Task 5 커버
- §11 sourceHash 입력 변경 → Task 3 step 3.9 커버
- §13 테스트 전략 (단위 + 통합 + 정책 7번류 fixture) → Task 1·2·3·4·6 분산 커버
- §16 PR 전략 (단일 PR + 6 commit) → 본 plan 의 task = commit 매핑

✅ 누락 없음.

**2. Placeholder scan**:
- yaml 25년 값 — 실제 보건복지부 고시값으로 박음. placeholder 아님.
- 26년 값 — task 6 에서 발표값 확인 후 채움. 발표 전이면 yaml 미생성 명시. plan 자체에 TBD 없음.
- "기존 코드 패턴 따라 ..." 같은 모호 지시 — task 2 step 2.6 (호출부 컴파일 통과), step 2.10 (GuideResult 변환), step 2.11 (GuideResponse 변환) 에서 "기존 코드를 읽고 패턴에 맞춰 추가" 라는 형태가 있음. 정확한 코드 박지 못한 이유: 해당 파일 현재 형태가 plan 작성 시점에 직접 검증되지 않음. 이 부분은 implementation 시 주의.

**3. Type consistency**:
- `IncomeBracketReference(int year, int version, ...)` — task 1 에서 정의, task 3 에서 `findAmount` / `toContextText` 호출 일관.
- `GuideContent(String, List<GuideHighlight>, GuidePairedSection×3, List<GuidePitfall>)` — task 2 에서 정의, task 4 에서 `content.highlights()` 호출 일관.
- `GuideValidator.ValidationReport` 시그니처 — task 4 에서 정의, `hasRetryTrigger()`, `violationCount()`, `feedbackMessages()` 호출 task 4·step 4.12 일관.
- `GuideLlmProvider.regenerateWithFeedback` — task 4 step 4.8 정의, step 4.12 에서 호출 일관.

✅ 타입 일관성 OK.

---

## 후속 / 미결

- 26년 보건복지부 고시값 발표 시점에 별도 PR 로 `2026.yaml` 추가.
- LLM 재시도 비율 / 검증 위반율 메트릭 노출 방식 — 운영 데이터 보고 결정.
- 분류 키워드 화이트리스트 점진 보강 (운영 중 누락 발견 시 PR).
- highlights·pitfalls 사이 동일 텍스트 중복 검증 — v0.x.
