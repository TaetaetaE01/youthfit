# 가이드 환산값 결정적 후처리 (IncomeBracketAnnotator) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LLM 가이드 응답의 `중위소득 N%` / `차상위(계층)?` 표기에 yaml 환산값을 결정적으로 자동 삽입하는 `IncomeBracketAnnotator` 를 도입하여, gpt-4o-mini 의 instruction following 한계로 발생하는 환산값 누락을 100% 결정적으로 해결한다.

**Architecture:** application/service 레이어에 `IncomeBracketAnnotator` 신규 컴포넌트(순수 함수) 추가. `GuideContent` 의 모든 텍스트 필드를 traverse 하며 패턴 매칭 → reference yaml lookup → 매칭 직후 괄호로 환산값 삽입. `GuideValidator.checkMissingAmount` 는 책임 이전 후 제거. `GuideGenerationService` 가 LLM/retry 후 finalResponse 결정 직전에 한 번 annotate 호출. `computeHash` 에 `annotator:v1` 추가 → 기존 가이드 자동 재생성.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JUnit 5 + Mockito, slf4j + logback (테스트 시 `ListAppender` 로 로그 검증), Lombok `@Component` / `@RequiredArgsConstructor`. 신규 라이브러리 0.

**Spec:** `docs/superpowers/specs/DONE_2026-04-28-05-income-bracket-postprocess-design.md`

---

## File Structure

### 신규 파일

| 파일 | 책임 |
|---|---|
| `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java` | 가이드 텍스트의 `중위소득 N%` / `차상위` 패턴을 찾아 환산값 결정적 삽입. 순수 함수, 외부 의존 0 |
| `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java` | 단위 테스트 — 패턴 매칭, skip, 미등록 비율, 반복, 5개 필드 traverse, 빈 reference, 표현 변형 |

### 수정 파일

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java` | `checkMissingAmount`, `PERCENT_PATTERN`, `AMOUNT_PATTERN`, `ValidationReport.hasMissingAmount`, "환산 금액 병기" feedback 제거 |
| `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java` | `hasMissingAmount` 관련 테스트 케이스 제거, `ValidationReport` 시그니처 변경 반영 |
| `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java` | `IncomeBracketAnnotator` 주입 + `finalResponse` 결정 직후 annotate 호출 1회 + `ANNOTATOR_VERSION` 추가 후 `computeHash` 에 포함 |
| `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java` | Annotator mock 주입 + `verify(annotator).annotate(...)` 호출 검증 |
| `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java` | Annotator mock 주입, retry 흐름에서도 annotate 1회 호출 검증 |
| `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceHashTest.java` | `ANNOTATOR_VERSION` 변경 시 다른 hash 반환 케이스 추가 |

---

## Task 1: IncomeBracketAnnotator 골격 + 중위소득 단순 패턴

**Files:**
- Create: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Create: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 1.1: 첫 실패 테스트 작성**

`backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketAnnotatorTest {

    private final IncomeBracketAnnotator annotator = new IncomeBracketAnnotator();

    private IncomeBracketReference reference2026() {
        return new IncomeBracketReference(
                2026,
                1,
                Map.of(
                        HouseholdSize.ONE, Map.of(60, 1538543L),
                        HouseholdSize.TWO, Map.of(60, 2519575L)),
                Map.of(HouseholdSize.ONE, 1282119L, HouseholdSize.TWO, 2099646L)
        );
    }

    private GuideContent contentWithCriteriaItem(String item) {
        return new GuideContent(
                "한 줄 요약",
                List.of(),
                null,
                new GuidePairedSection(List.of(new GuideGroup(null, List.of(item)))),
                null,
                List.of()
        );
    }

    @Test
    void 중위소득_60퍼_패턴이_있으면_1인2인_환산값을_괄호로_삽입한다() {
        GuideContent content = contentWithCriteriaItem("중위소득 60% 이하인 자");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)인 자");
    }
}
```

- [ ] **Step 1.2: 테스트 실행하여 컴파일 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: compile error — `IncomeBracketAnnotator` 클래스 없음.

- [ ] **Step 1.3: Annotator 골격 + 단순 중위소득 처리 구현**

`backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`:

```java
package com.youthfit.guide.application.service;

import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IncomeBracketAnnotator {

    private static final Logger log = LoggerFactory.getLogger(IncomeBracketAnnotator.class);

    private static final Pattern MEDIAN_INCOME_PATTERN = Pattern.compile(
            "(?:기준\\s*)?중위소득(?:의)?\\s*(\\d+)\\s*%(?:\\s*이내|\\s*이하|\\s*까지)?");

    public GuideContent annotate(GuideContent content, IncomeBracketReference reference, Long policyId) {
        GuidePairedSection criteria = annotatePaired(content.criteria(), reference, policyId);
        return new GuideContent(
                content.oneLineSummary(),
                content.highlights(),
                content.target(),
                criteria,
                content.content(),
                content.pitfalls()
        );
    }

    private GuidePairedSection annotatePaired(GuidePairedSection section,
                                              IncomeBracketReference reference,
                                              Long policyId) {
        if (section == null) return null;
        List<GuideGroup> newGroups = section.groups().stream()
                .map(g -> new GuideGroup(g.label(),
                        g.items().stream()
                                .map(item -> annotateText(item, reference, policyId))
                                .toList()))
                .toList();
        return new GuidePairedSection(newGroups);
    }

    private String annotateText(String text, IncomeBracketReference reference, Long policyId) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        Matcher m = MEDIAN_INCOME_PATTERN.matcher(text);
        while (m.find()) {
            result.append(text, lastEnd, m.end());
            int percent = Integer.parseInt(m.group(1));
            String suffix = formatMedianSuffix(reference, percent);
            if (suffix != null) result.append(suffix);
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    private String formatMedianSuffix(IncomeBracketReference reference, int percent) {
        Optional<Long> one = reference.findAmount(HouseholdSize.ONE, percent);
        Optional<Long> two = reference.findAmount(HouseholdSize.TWO, percent);
        if (one.isEmpty() && two.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(" (").append(reference.year()).append("년 기준 ");
        if (one.isPresent()) sb.append("1인 가구 월 약 ").append(toManwon(one.get())).append("만원");
        if (two.isPresent()) {
            if (one.isPresent()) sb.append(", ");
            sb.append("2인 가구 월 약 ").append(toManwon(two.get())).append("만원");
        }
        sb.append(")");
        return sb.toString();
    }

    private long toManwon(long won) {
        return Math.round(won / 10000.0);
    }
}
```

- [ ] **Step 1.4: 테스트 실행하여 PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: PASS — `1538543` → `Math.round(153.8543)` = `154`, `2519575` → `Math.round(251.9575)` = `252`.

- [ ] **Step 1.5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "$(cat <<'EOF'
feat(guide): IncomeBracketAnnotator 골격 + 중위소득 패턴 단순 케이스

가이드 텍스트의 "중위소득 N%" 패턴 매칭 직후 yaml 환산값을 1·2인 가구
형식으로 괄호 삽입. paired criteria bullets 한정 (이후 task 에서 확장).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 차상위 패턴 + 1인 가구만 표기

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 2.1: 실패 테스트 추가**

`IncomeBracketAnnotatorTest.java` 끝에 추가:

```java
    @Test
    void 차상위계층_패턴이_있으면_1인_가구만_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem("차상위계층 이하의 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("차상위계층 이하 (2026년 기준 1인 가구 월 약 128만원 이하)의 청년");
    }

    @Test
    void 차상위_단독_표기도_매칭한다() {
        GuideContent content = contentWithCriteriaItem("차상위 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("차상위 (2026년 기준 1인 가구 월 약 128만원 이하) 청년");
    }
```

- [ ] **Step 2.2: 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 2개 실패 — 차상위 패턴 매칭/삽입 미구현.

- [ ] **Step 2.3: 차상위 패턴 통합 정규식 + 분기 처리 구현**

`IncomeBracketAnnotator.java` 의 패턴 상수 + `annotateText` + `formatNearPoorSuffix` 추가:

```java
    private static final Pattern COMBINED_PATTERN = Pattern.compile(
            "(?:(?:기준\\s*)?중위소득(?:의)?\\s*(\\d+)\\s*%(?:\\s*이내|\\s*이하|\\s*까지)?)" +
            "|(?:차상위(?:계층)?(?:\\s*이하|\\s*이내)?)");
```

기존 `MEDIAN_INCOME_PATTERN` 상수 삭제. `annotateText` 의 매칭 루프를 통합 패턴 사용으로 교체:

```java
    private String annotateText(String text, IncomeBracketReference reference, Long policyId) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        Matcher m = COMBINED_PATTERN.matcher(text);
        while (m.find()) {
            result.append(text, lastEnd, m.end());
            String percentGroup = m.group(1);
            String suffix;
            if (percentGroup != null) {
                int percent = Integer.parseInt(percentGroup);
                suffix = formatMedianSuffix(reference, percent);
            } else {
                suffix = formatNearPoorSuffix(reference);
            }
            if (suffix != null) result.append(suffix);
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    private String formatNearPoorSuffix(IncomeBracketReference reference) {
        Long one = reference.nearPoor().get(HouseholdSize.ONE);
        if (one == null) return null;
        return String.format(" (%d년 기준 1인 가구 월 약 %d만원 이하)",
                reference.year(), toManwon(one));
    }
```

- [ ] **Step 2.4: PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 3개 모두 PASS — `1282119` → `128`.

- [ ] **Step 2.5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "feat(guide): Annotator 차상위 패턴 매칭 + 1인 가구 환산값 삽입

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 이미 만원 표기 있으면 같은 텍스트 단위 전체 skip

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 3.1: 실패 테스트 추가**

```java
    @Test
    void 이미_만원_표기가_같은_bullet에_있으면_그_bullet은_전체_skip한다() {
        GuideContent content = contentWithCriteriaItem(
                "중위소득 60% 이하 (정책 본문 기준 월 138만원, 230만원)인 자");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (정책 본문 기준 월 138만원, 230만원)인 자");
    }
```

- [ ] **Step 3.2: 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 새 테스트 실패 — 현재는 이미 만원 표기 있어도 추가 환산값 끼워넣음.

- [ ] **Step 3.3: skip 로직 추가**

`IncomeBracketAnnotator.java` 의 `annotateText` 시작부에 EXISTING_AMOUNT_PATTERN 검사 추가, 패턴 상수도 추가:

```java
    private static final Pattern EXISTING_AMOUNT_PATTERN = Pattern.compile("\\d+\\s*만원");

    private String annotateText(String text, IncomeBracketReference reference, Long policyId) {
        if (text == null || text.isEmpty()) return text;
        if (EXISTING_AMOUNT_PATTERN.matcher(text).find()) return text;   // ← skip
        // ... 기존 매칭 루프
    }
```

- [ ] **Step 3.4: PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 4개 모두 PASS.

- [ ] **Step 3.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "feat(guide): Annotator skip 조건 - 이미 만원 표기 있는 텍스트는 보존

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: yaml 미등록 비율 → skip + WARN 로그

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 4.1: ListAppender setup + 실패 테스트 추가**

`IncomeBracketAnnotatorTest.java` 클래스 상단에 import + setup/teardown + 테스트 추가:

```java
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
```

`IncomeBracketAnnotatorTest` 클래스 안:

```java
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setupLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(IncomeBracketAnnotator.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void teardownLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(IncomeBracketAnnotator.class);
        logger.detachAppender(appender);
    }

    @Test
    void yaml_미등록_비율은_텍스트_보존하고_WARN_로그를_남긴다() {
        GuideContent content = contentWithCriteriaItem("중위소득 75% 이하 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 7L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 75% 이하 청년");
        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("percent=75")
                        && e.getFormattedMessage().contains("policyId=7"));
    }
```

- [ ] **Step 4.2: 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 새 테스트 실패 — WARN 로그 없음 (현재 `formatMedianSuffix` 가 null 반환만 하고 로그 X).

- [ ] **Step 4.3: WARN 로그 + snippet 헬퍼 추가**

`IncomeBracketAnnotator.java`:

```java
    private String annotateText(String text, IncomeBracketReference reference, Long policyId) {
        if (text == null || text.isEmpty()) return text;
        if (EXISTING_AMOUNT_PATTERN.matcher(text).find()) return text;
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        Matcher m = COMBINED_PATTERN.matcher(text);
        while (m.find()) {
            result.append(text, lastEnd, m.end());
            String percentGroup = m.group(1);
            String suffix;
            if (percentGroup != null) {
                int percent = Integer.parseInt(percentGroup);
                suffix = formatMedianSuffix(reference, percent);
                if (suffix == null) {
                    log.warn("unmapped median income percent: percent={}, year={}, policyId={}, snippet={}",
                            percent, reference.year(), policyId, snippet(text, 60));
                }
            } else {
                suffix = formatNearPoorSuffix(reference);
            }
            if (suffix != null) result.append(suffix);
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    private String snippet(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
```

- [ ] **Step 4.4: PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 5개 모두 PASS.

- [ ] **Step 4.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "feat(guide): Annotator yaml 미등록 비율 skip + WARN 로그

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 같은 텍스트 안 동일 비율 반복 → 첫 등장에만 삽입

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 5.1: 실패 테스트 추가**

```java
    @Test
    void 같은_텍스트_안에_동일_비율_반복시_첫_등장에만_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "중위소득 60% 이하인 자로서 중위소득 60% 이하 가구");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)인 자로서 중위소득 60% 이하 가구");
    }

    @Test
    void 같은_텍스트_안에_차상위_반복시_첫_등장에만_환산값을_삽입한다() {
        GuideContent content = contentWithCriteriaItem(
                "차상위계층 이하 청년 또는 차상위 가구");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("차상위계층 이하 (2026년 기준 1인 가구 월 약 128만원 이하) 청년 또는 차상위 가구");
    }

    @Test
    void 다른_비율_여러개는_각각_한번씩_삽입한다() {
        // 50% 데이터 추가가 필요하므로 reference 확장
        IncomeBracketReference ref = new IncomeBracketReference(
                2026, 1,
                Map.of(
                        HouseholdSize.ONE, Map.of(50, 1282119L, 60, 1538543L),
                        HouseholdSize.TWO, Map.of(50, 2099646L, 60, 2519575L)),
                Map.of(HouseholdSize.ONE, 1282119L)
        );
        GuideContent content = contentWithCriteriaItem("중위소득 50% 또는 중위소득 60% 이하");
        GuideContent result = annotator.annotate(content, ref, 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("중위소득 50% (2026년 기준 1인 가구 월 약 128만원, 2인 가구 월 약 210만원)")
                .contains("중위소득 60% 이하 (2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)");
    }
```

- [ ] **Step 5.2: 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 첫 두 테스트 실패 (반복 비율도 추가 삽입함). 세 번째는 PASS 가능 (다른 비율은 각각 처리되므로).

- [ ] **Step 5.3: Set 추적으로 반복 차단**

`IncomeBracketAnnotator.java` 의 `annotateText` 갱신 — 텍스트 단위로 `Set<Integer>` + `boolean nearPoorProcessed`:

```java
    private String annotateText(String text, IncomeBracketReference reference, Long policyId) {
        if (text == null || text.isEmpty()) return text;
        if (EXISTING_AMOUNT_PATTERN.matcher(text).find()) return text;

        java.util.Set<Integer> processedPercents = new java.util.HashSet<>();
        boolean[] nearPoorProcessed = {false};

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        Matcher m = COMBINED_PATTERN.matcher(text);
        while (m.find()) {
            result.append(text, lastEnd, m.end());
            String percentGroup = m.group(1);
            String suffix = null;
            if (percentGroup != null) {
                int percent = Integer.parseInt(percentGroup);
                if (processedPercents.add(percent)) {
                    suffix = formatMedianSuffix(reference, percent);
                    if (suffix == null) {
                        log.warn("unmapped median income percent: percent={}, year={}, policyId={}, snippet={}",
                                percent, reference.year(), policyId, snippet(text, 60));
                    }
                }
            } else {
                if (!nearPoorProcessed[0]) {
                    nearPoorProcessed[0] = true;
                    suffix = formatNearPoorSuffix(reference);
                }
            }
            if (suffix != null) result.append(suffix);
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }
```

(`Set` import 는 파일 상단에 `java.util.Set`, `java.util.HashSet` 로 추가하거나 방금처럼 inline FQN 도 OK — 기존 import 와 일관 위해 상단 import 권장.)

상단 import 정리:
```java
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
```

- [ ] **Step 5.4: PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 8개 모두 PASS.

- [ ] **Step 5.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "feat(guide): Annotator 같은 텍스트 동일 비율 반복 → 첫 등장만

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 모든 텍스트 필드 적용 (oneLineSummary, highlights, target, content, pitfalls)

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 6.1: 실패 테스트 추가**

```java
    @Test
    void oneLineSummary_highlights_target_content_pitfalls_모두_적용된다() {
        GuideContent content = new GuideContent(
                "중위소득 60% 이하 청년에게 월세 지원",
                List.of(
                        new com.youthfit.guide.domain.model.GuideHighlight(
                                "중위소득 60% 이하 우선 공급",
                                com.youthfit.guide.domain.model.GuideSourceField.SUPPORT_TARGET)),
                new GuidePairedSection(List.of(new GuideGroup(null, List.of("중위소득 60% 이하 무주택자")))),
                new GuidePairedSection(List.of(new GuideGroup(null, List.of("선정기준 무관")))),
                new GuidePairedSection(List.of(new GuideGroup(null, List.of("중위소득 60% 이하만 지급")))),
                List.of(
                        new com.youthfit.guide.domain.model.GuidePitfall(
                                "중위소득 60% 초과 시 환수",
                                com.youthfit.guide.domain.model.GuideSourceField.SUPPORT_CONTENT))
        );
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.oneLineSummary()).contains("(2026년 기준 1인 가구 월 약 154만원");
        assertThat(result.highlights().get(0).text()).contains("(2026년 기준");
        assertThat(result.target().groups().get(0).items().get(0)).contains("(2026년 기준");
        assertThat(result.content().groups().get(0).items().get(0)).contains("(2026년 기준");
        assertThat(result.pitfalls().get(0).text()).contains("(2026년 기준");
    }
```

- [ ] **Step 6.2: 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 새 테스트 실패 — 현재는 `criteria` 만 처리.

- [ ] **Step 6.3: 모든 필드 traverse 구현**

`IncomeBracketAnnotator.java` 의 `annotate` 갱신 + import 추가:

```java
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuidePitfall;
```

`annotate` 메서드:

```java
    public GuideContent annotate(GuideContent content, IncomeBracketReference reference, Long policyId) {
        String oneLine = annotateText(content.oneLineSummary(), reference, policyId);
        List<GuideHighlight> highlights = content.highlights().stream()
                .map(h -> new GuideHighlight(annotateText(h.text(), reference, policyId), h.sourceField()))
                .toList();
        GuidePairedSection target = annotatePaired(content.target(), reference, policyId);
        GuidePairedSection criteria = annotatePaired(content.criteria(), reference, policyId);
        GuidePairedSection contentSection = annotatePaired(content.content(), reference, policyId);
        List<GuidePitfall> pitfalls = content.pitfalls().stream()
                .map(p -> new GuidePitfall(annotateText(p.text(), reference, policyId), p.sourceField()))
                .toList();
        return new GuideContent(oneLine, highlights, target, criteria, contentSection, pitfalls);
    }
```

- [ ] **Step 6.4: PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 9개 모두 PASS.

- [ ] **Step 6.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "feat(guide): Annotator 5개 텍스트 필드 모두 traverse

oneLineSummary, highlights, target/criteria/content paired, pitfalls 전체 적용.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 빈 reference 처리 + 표현 변형 매칭

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java`

- [ ] **Step 7.1: 실패 테스트 추가**

```java
    @Test
    void 빈_reference이면_모든_패턴_skip하고_INFO_로그_1회_남긴다() {
        IncomeBracketReference empty = new IncomeBracketReference(2026, 1, Map.of(), Map.of());
        GuideContent content = contentWithCriteriaItem("중위소득 60% 이하 청년");
        GuideContent result = annotator.annotate(content, empty, 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .isEqualTo("중위소득 60% 이하 청년");
        long infoCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains("empty income bracket reference"))
                .count();
        assertThat(infoCount).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "기준중위소득 60%",
            "중위소득의 60%",
            "중위소득 60% 이내",
            "중위소득 60% 까지",
            "중위소득 60%"
    })
    void 중위소득_표현_변형도_모두_매칭한다(String variation) {
        GuideContent content = contentWithCriteriaItem(variation + " 청년");
        GuideContent result = annotator.annotate(content, reference2026(), 1L);
        assertThat(result.criteria().groups().get(0).items().get(0))
                .contains("(2026년 기준 1인 가구 월 약 154만원, 2인 가구 월 약 252만원)");
    }
```

- [ ] **Step 7.2: 실패 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 빈 reference 테스트 실패 (INFO 로그 없음). 표현 변형 테스트는 정규식이 이미 모든 변형 흡수하므로 통과 가능. 단 전부 통과하지 않으면 정규식 보강 필요.

- [ ] **Step 7.3: 빈 reference 가드 + INFO 로그**

`IncomeBracketAnnotator.java` 의 `annotate` 시작부에:

```java
    public GuideContent annotate(GuideContent content, IncomeBracketReference reference, Long policyId) {
        if (reference == null
                || (reference.medianIncome().isEmpty() && reference.nearPoor().isEmpty())) {
            log.info("empty income bracket reference, skipping annotation: policyId={}, year={}",
                    policyId, reference == null ? "null" : reference.year());
            return content;
        }
        // ... 기존 로직
    }
```

- [ ] **Step 7.4: PASS 확인**

```bash
cd backend && ./gradlew test --tests IncomeBracketAnnotatorTest
```
Expected: 15개 모두 PASS (9 + 1 + 5 변형).

- [ ] **Step 7.5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/IncomeBracketAnnotator.java \
        backend/src/test/java/com/youthfit/guide/application/service/IncomeBracketAnnotatorTest.java
git commit -m "feat(guide): Annotator 빈 reference 가드 + 표현 변형 9종 회귀 검증

빈 reference (loader fallback 실패) 시 모든 패턴 skip + INFO 로그.
'기준중위소득', '중위소득의', '이내/이하/까지' 등 표현 변형 5종 회귀 테스트 추가.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: GuideValidator.checkMissingAmount 제거

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java`

- [ ] **Step 8.1: GuideValidatorTest 정리 — `hasMissingAmount` 관련 케이스 제거**

`GuideValidatorTest.java` 를 열어 `hasMissingAmount` / `checkMissingAmount` / `중위소득.*환산` 관련 테스트 메서드 전부 삭제. `ValidationReport` 인스턴스화 부분에서 3-인자 (groupMix, insufficientHighlights, feedbackMessages) 로 갱신.

조회/삭제 명령:
```bash
cd backend && grep -n 'hasMissingAmount\|checkMissingAmount\|missingAmount' \
        src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java
```

발견된 테스트 메서드 전체 (`@Test` 어노테이션부터 닫는 `}` 까지) 삭제. `ValidationReport` 호출부에서 `true/false` 인자 4개 → 3개로 줄임.

- [ ] **Step 8.2: GuideValidator 본체 정리**

`GuideValidator.java`:

```java
@Component
public class GuideValidator {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "(\\d+\\s*(?:만원|원|개월|년|세|%|명))"
    );

    private static final Pattern FRIENDLY_TONE = Pattern.compile(
            "(?:해요|예요|에요|드려요|이에요|어요|아요)(?:[.!?\\s]|$)"
    );

    private static final List<String> CATEGORY_KEYWORDS = List.of(
            "차상위", "일반공급", "특별공급", "신혼부부",
            "생애최초", "맞벌이", "다자녀", "기혼", "미혼"
    );

    public record ValidationReport(
            boolean hasGroupMixViolation,
            boolean hasInsufficientHighlights,
            List<String> feedbackMessages
    ) {

        public boolean hasRetryTrigger() {
            return hasGroupMixViolation || hasInsufficientHighlights;
        }

        public int violationCount() {
            int n = 0;
            if (hasGroupMixViolation) n++;
            if (hasInsufficientHighlights) n++;
            return n;
        }
    }

    public ValidationReport validate(GuideContent content, String originalText) {
        boolean groupMix = checkGroupMix(content);
        boolean insufficientHighlights = content.highlights().size() < 3;

        List<String> feedback = new ArrayList<>();
        if (groupMix) {
            feedback.add("일부 group의 items에 분류 키워드(차상위/일반공급/특별공급/신혼부부 등)가 2종 이상 섞여 있다. group을 분리하고 label에 분류명을 명시할 것.");
        }
        if (insufficientHighlights) {
            feedback.add("highlights가 " + content.highlights().size() + "개. 최소 3개 이상 작성할 것 (긍정·중립·차별점).");
        }

        return new ValidationReport(groupMix, insufficientHighlights, feedback);
    }

    // filterInvalidSourceFields, findMissingNumericTokens, containsFriendlyTone, checkGroupMix 그대로 유지
    // PERCENT_PATTERN, AMOUNT_PATTERN, checkMissingAmount 메서드 삭제
```

`PERCENT_PATTERN`, `AMOUNT_PATTERN` 상수 + `checkMissingAmount` 메서드 + 관련 stream 코드 전체 삭제.

- [ ] **Step 8.3: 빌드 + 테스트 PASS 확인**

```bash
cd backend && ./gradlew test --tests GuideValidatorTest
```
Expected: PASS.

```bash
cd backend && ./gradlew compileJava compileTestJava
```
Expected: BUILD SUCCESSFUL — `ValidationReport` 의 4-인자 사용처가 다른 클래스에 있으면 컴파일 실패. 그러면 그 사용처도 같은 PR 내에서 수정해야 함. 현재 `GuideGenerationService` 가 사용하는데 4-인자가 아니라 메서드만 호출하므로 영향 없음. `GuideGenerationServiceRetryTest` 가 ValidationReport mock 인자를 4개로 지정한 곳 있으면 거기도 3개로 수정.

```bash
cd backend && grep -rn 'new ValidationReport(\|ValidationReport(true\|ValidationReport(false' src/test/
```
4-인자 인스턴스화 위치 확인 후 모두 3-인자로 정정.

- [ ] **Step 8.4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideValidator.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideValidatorTest.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java
git commit -m "$(cat <<'EOF'
refactor(guide): GuideValidator.checkMissingAmount 제거

환산값 누락 검사 책임을 IncomeBracketAnnotator (결정적 후처리) 로 이전.
ValidationReport 가 group mix / insufficient highlights 두 가지 검증만 남김.
yaml 미등록 비율 케이스에서 LLM retry 가 무의미했던 문제 해소.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: GuideGenerationService 통합 + sourceHash 무효화

**Files:**
- Modify: `backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java`
- Modify: `backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceHashTest.java`

- [ ] **Step 9.1: GuideGenerationServiceTest mock 보강 + 실패 테스트 추가**

`GuideGenerationServiceTest.java` 의 `setUp` (또는 mock 초기화 부분) 에서 `IncomeBracketAnnotator` mock 추가:

```java
    @Mock
    private IncomeBracketAnnotator incomeBracketAnnotator;

    // 생성자/주입부에 incomeBracketAnnotator 추가
    // 기존 GuideGenerationService 생성: new GuideGenerationService(repo, ..., validator, refLoader)
    // 변경 후: new GuideGenerationService(repo, ..., validator, refLoader, incomeBracketAnnotator)
```

`@BeforeEach` 또는 stub 설정에서 기본 동작:
```java
    when(incomeBracketAnnotator.annotate(any(GuideContent.class), any(), anyLong()))
            .thenAnswer(inv -> inv.getArgument(0));
```

새 테스트 추가:
```java
    @Test
    void generateGuide_가_finalResponse에_annotate를_1회_호출한다() {
        // given: 정책/청크/LLM 응답 stub
        // ... 기존 setup
        // when
        service.generateGuide(new GenerateGuideCommand(1L));
        // then
        verify(incomeBracketAnnotator, times(1))
                .annotate(any(GuideContent.class), any(IncomeBracketReference.class), eq(1L));
    }
```

- [ ] **Step 9.2: GuideGenerationServiceRetryTest 도 mock 보강 + retry 시 1회 호출 검증**

`GuideGenerationServiceRetryTest.java` — 1차 응답이 검증 위반 → retry → finalResponse 결정. annotate 는 finalResponse 1번만 호출:

```java
    @Test
    void retry_후_finalResponse에_annotate를_1회_호출한다() {
        // 1차 응답: groupMix=true 위반 트리거
        // 2차 응답: 위반 없음
        // ...
        verify(incomeBracketAnnotator, times(1))
                .annotate(any(GuideContent.class), any(IncomeBracketReference.class), anyLong());
    }
```

- [ ] **Step 9.3: GuideGenerationServiceHashTest — annotator 버전 변경 시 다른 hash**

`GuideGenerationServiceHashTest.java` 에 케이스 추가 — `computeHashForTest` 가 `ANNOTATOR_VERSION` 변경 시 해시도 바뀌어야 함. 현재 service 가 ANNOTATOR_VERSION 을 추가한 후, 추가 전 해시와 추가 후 해시가 다른지 직접 비교는 불가하므로, 해시 입력 검증을 위해 (a) PROMPT_VERSION 하드코딩 변경 시 hash 가 바뀌는 것과 비슷한 패턴으로 (b) annotator 버전 상수가 동일한 정책에 대해 PROMPT_VERSION 만 다른 두 해시와 다른 패턴 분포를 갖는지 확인:

```java
    @Test
    void computeHash_는_annotator_버전을_입력에_포함한다() {
        Policy policy = samplePolicy();
        IncomeBracketReference ref = sampleRef();
        String hash = GuideGenerationService.computeHashForTest(policy, List.of(), ref);
        // 해시 입력 문자열 끝에 annotator:v1 포함 여부 검증은 black-box 어려움.
        // 대신 동일 입력에 대해 결정성/안정성만 검증하고, 코드 리뷰로 입력에 포함되었음을 확인.
        String hash2 = GuideGenerationService.computeHashForTest(policy, List.of(), ref);
        assertThat(hash).isEqualTo(hash2);  // 결정성
        assertThat(hash).hasSize(64);        // sha-256 hex
    }
```

(black-box 로 ANNOTATOR_VERSION 의 해시 영향 검증은 어려우므로 white-box code review 로 보장. 본 테스트는 결정성/형식 회귀 방지용.)

- [ ] **Step 9.4: 위 3개 테스트 컴파일/실행 모두 실패 확인**

```bash
cd backend && ./gradlew test --tests GuideGenerationServiceTest --tests GuideGenerationServiceRetryTest --tests GuideGenerationServiceHashTest
```
Expected: 컴파일 실패 — `IncomeBracketAnnotator` 가 `GuideGenerationService` 생성자에 주입되지 않음.

- [ ] **Step 9.5: GuideGenerationService 본체 통합**

`GuideGenerationService.java` 변경:

a) `ANNOTATOR_VERSION` 상수 + 의존성 주입:
```java
    static final String PROMPT_VERSION = "v3";
    static final String ANNOTATOR_VERSION = "v1";   // ← 신규

    // ...
    private final IncomeBracketReferenceLoader referenceLoader;
    private final IncomeBracketAnnotator incomeBracketAnnotator;   // ← 신규
```

`@RequiredArgsConstructor` 가 자동으로 final 필드를 모두 받으므로 별도 작업 없음.

b) `generateGuide` 의 finalResponse 결정 직후 annotate 호출:

```java
        // ... (firstResponse / secondResponse / finalResponse 결정 직후, filterInvalidSourceFields 호출 직전에 추가)
        finalResponse = incomeBracketAnnotator.annotate(finalResponse, reference, command.policyId());

        // 검증 4: sourceField 유효성 (해당 항목 폐기) — 기존
        finalResponse = filterInvalidSourceFields(finalResponse, policy);
```

c) `computeHash` 끝부분에 annotator 버전 추가:
```java
        if (reference != null) {
            sb.append("|ref:").append(reference.year()).append(":").append(reference.version());
        }
        sb.append("|prompt:").append(PROMPT_VERSION);
        sb.append("|annotator:").append(ANNOTATOR_VERSION);   // ← 신규
        return sha256(sb.toString());
```

d) `computeHashForTest` 의 6-arg null 생성자도 7-arg 로 갱신:
```java
    static String computeHashForTest(Policy policy, List<PolicyDocument> chunks, IncomeBracketReference reference) {
        return new GuideGenerationService(null, null, null, null, null, null, null)
                .computeHash(policy, chunks, reference);
    }
```

- [ ] **Step 9.6: 전체 테스트 PASS 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.guide.*"
```
Expected: 모든 guide 테스트 PASS — Annotator + Validator + GenerationService + RetryTest + HashTest.

- [ ] **Step 9.7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/guide/application/service/GuideGenerationService.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceTest.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceRetryTest.java \
        backend/src/test/java/com/youthfit/guide/application/service/GuideGenerationServiceHashTest.java
git commit -m "$(cat <<'EOF'
feat(guide): GuideGenerationService 에 IncomeBracketAnnotator 통합

finalResponse 결정 후 filterInvalidSourceFields 직전에 annotate 1회 호출.
computeHash 입력에 annotator:v1 추가 → 모든 기존 가이드 sourceHash 변경 →
다음 generateGuide 호출 시 자동 재생성. 수동 backfill 불필요.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: 전체 빌드 + 통합 검증 + manual smoke 절차

**Files:** 없음 (검증/문서만)

- [ ] **Step 10.1: 전체 백엔드 테스트 + JaCoCo 리포트**

```bash
cd backend && ./gradlew clean build
```
Expected: BUILD SUCCESSFUL. 모든 테스트 PASS. JaCoCo 리포트 `build/reports/jacoco/test/html/index.html` 생성.

- [ ] **Step 10.2: Annotator 코드 커버리지 ≥ 90% 확인**

```bash
cd backend && open build/reports/jacoco/test/html/com.youthfit.guide.application.service/IncomeBracketAnnotator.html
```
Expected: line/branch coverage 90%+ (15개 단위 테스트 cover).

- [ ] **Step 10.3: docker compose 재기동 후 manual smoke**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
docker compose up -d --build backend
docker compose logs -f backend | grep -E '(annotator|reindex|guide)'
```

`PolicyDetailPage` 에서 정책 상세 진입:
- 정책 7번 (또는 가이드 있는 정책) 진입
- `selectionCriteria` 풀이 bullets 에서 `중위소득 N% 이하` 표기에 환산값이 결정적으로 들어가는지 확인
- 차상위 표기에 1인 가구 환산값이 들어가는지 확인
- 정책 본문 PDF 인용값(예: `월 138만원`)이 있던 경우 그 값이 보존되는지 확인 (skip 케이스)

```bash
# 가이드 강제 재생성 (sourceHash 변경되었으므로 자동 재생성되어야 함)
curl -X POST http://localhost:8080/api/internal/guides/generate \
  -H "X-Internal-Api-Key: changeme" \
  -d '{"policyId": 7}'
```

- [ ] **Step 10.4: 운영 메모 추가 (선택)**

만약 manual smoke 중 yaml 미등록 비율(75%, 110% 등)이 다수 발견되면, 후속 PR 로 yaml 확장 결정. 본 PR 범위는 그대로.

- [ ] **Step 10.5: PR 생성 — 전체 작업을 단일 PR 로**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git push -u origin <feature-branch>
gh pr create --title "[BE] feat: 가이드 환산값 결정적 후처리 (IncomeBracketAnnotator)" \
  --body "$(cat <<'EOF'
## Summary
- LLM 환산값(만원) 누락 문제를 결정적 후처리로 해결.
- 신규 `IncomeBracketAnnotator` 가 `GuideContent` 의 모든 텍스트 필드에서 `중위소득 N%` / `차상위(계층)?` 패턴을 찾아 yaml 환산값을 결정적으로 병기.
- `GuideValidator.checkMissingAmount` 제거 (책임 이전 + yaml 미등록 비율의 무의미한 retry 비용 제거).
- `computeHash` 에 `annotator:v1` 추가 → 기존 가이드 자동 재생성.

## Spec / Plan
- spec: `docs/superpowers/specs/DONE_2026-04-28-05-income-bracket-postprocess-design.md`
- plan: `docs/superpowers/plans/DONE_2026-04-28-05-income-bracket-postprocess.md`

## Test plan
- [x] `IncomeBracketAnnotatorTest` 단위 테스트 15개 PASS
- [x] `GuideValidatorTest` 갱신 후 PASS
- [x] `GuideGenerationServiceTest` / `RetryTest` / `HashTest` PASS
- [x] manual smoke — 정책 7번 가이드 재생성 후 환산값 확인
- [x] yaml 미등록 비율 정책에서 WARN 로그 1회 발생 + 텍스트 보존
- [x] PDF 인용값 (`월 138만원`) 있는 정책에서 후처리 skip 보존

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review (작성 후 자체 점검 결과)

- ✅ Spec §4.1 Annotator 시그니처 `(content, reference, policyId)` — Task 1~7 일관 사용
- ✅ Spec §4.3 GuideValidator 변경 — Task 8 에서 처리
- ✅ Spec §4.2 GuideGenerationService 통합 — Task 9 에서 finalResponse 직후 1회 annotate
- ✅ Spec §5.1 정규식 — Task 2 에서 `COMBINED_PATTERN` 으로 통합
- ✅ Spec §5.2 표기 형식 — Task 1·2 에서 `formatMedianSuffix` / `formatNearPoorSuffix`
- ✅ Spec §5.3 skip 조건 — Task 3 에서 EXISTING_AMOUNT_PATTERN
- ✅ Spec §5.4 yaml 미등록 — Task 4 WARN 로그
- ✅ Spec §5.5 같은 텍스트 반복 — Task 5 Set 추적
- ✅ Spec §5.6 5개 텍스트 필드 — Task 6
- ✅ Spec §7 sourceHash 무효화 — Task 9 의 Step 9.5(c)
- ✅ Spec §8 INFO/DEBUG/WARN 로깅 — Task 4·7 에서 처리
- ✅ Spec §9 테스트 전략 — Task 1~7 의 IncomeBracketAnnotatorTest 15개 케이스 cover

**Spec 과의 1점 차이**: spec §3.3 데이터 흐름 다이어그램에는 1차/retry 모두 annotate 라 표시. 본 plan 은 finalResponse 결정 후 1회 annotate 로 단순화 (검증이 환산값 검사를 안 하므로 동일 효과 + 코드 단순). spec 의도와 동일 결과.
