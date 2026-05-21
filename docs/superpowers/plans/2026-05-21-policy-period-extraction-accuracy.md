# 정책 신청 기간 추출 정확도 개선 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ingestion 단계에서 본문/첨부에 명시된 정책 신청 기간을 누락 없이, 사업기간 등과 혼동 없이 추출한다.

**Architecture:** 4개 `PeriodCandidateSource` 가 본문/첨부/n8n에서 후보를 만들고, `PeriodResolver` 가 라벨/네거티브 마스킹 + 점수표 + 다중 소스 보너스로 최적 후보를 고른다. 모호하면 LLM disambiguator, 후보가 0개면 LLM direct extractor. 첨부 추출 완료 후엔 기존 `PolicyAttachmentReindexedEvent` 를 구독해 비동기 보강.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA + Hibernate, PostgreSQL (JSONB), Spring `@Async` + `@TransactionalEventListener`, JUnit 5 + AssertJ, OpenAI Chat Completions API, Testcontainers (PostgreSQL).

**Spec:** `docs/superpowers/specs/2026-05-21-policy-period-extraction-accuracy-design.md`

**Spec 대비 plan 단계 조정**:
- 새 이벤트 `PolicyAttachmentsExtracted` 만들지 않고 기존 `PolicyAttachmentReindexedEvent` 재사용 (이미 `AttachmentReindexService.reindex()` 가 발행)
- 마이그레이션은 Flyway 아닌 `backend/src/main/resources/sql/YYYY-MM-DD-*.sql` 수동 SQL 컨벤션

---

## File Structure

### 신규 파일 (production)

| 파일 | 책임 |
|---|---|
| `common/domain/PeriodSource.java` | 추출 출처 enum 6종 (Policy 가 참조하므로 common 위치) |
| `ingestion/domain/model/PeriodCandidate.java` | 후보 값 객체 (start/end/source/confidence/evidence) |
| `ingestion/domain/model/ResolvedPeriod.java` | 최종 결과 값 객체 |
| `ingestion/application/port/PeriodExtractionContext.java` | 추출 입력 (본문/n8n값/첨부텍스트) |
| `ingestion/application/port/PeriodCandidateSource.java` | 소스 포트 |
| `ingestion/application/port/PeriodLlmDirectExtractor.java` | LLM 직접 추출 포트 (경로 ①) |
| `ingestion/application/port/PeriodLlmDisambiguator.java` | LLM 모호 분기 포트 (경로 ②) |
| `ingestion/domain/service/PeriodLabels.java` | 양성/네거티브 라벨 사전 |
| `ingestion/domain/service/PeriodRegexPatterns.java` | 5개 정규식 패턴 |
| `ingestion/domain/service/LabeledRegexExtractor.java` | 라벨 윈도우 + 패턴 적용 공용 유틸 |
| `ingestion/domain/service/source/N8nApplyFieldsSource.java` | n8n 값 후보 |
| `ingestion/domain/service/source/BodyLabeledRegexSource.java` | 본문 라벨 윈도우 후보 |
| `ingestion/domain/service/source/BodyGenericRegexSource.java` | 본문 전체 (네거티브 마스킹) |
| `ingestion/domain/service/source/AttachmentLabeledRegexSource.java` | 첨부 텍스트 라벨 윈도우 |
| `ingestion/domain/service/PeriodResolver.java` | 후보 통합·점수·선택 도메인 서비스 |
| `ingestion/infrastructure/external/OpenAiPolicyPeriodDisambiguator.java` | 경로 ② 구현체 |
| `ingestion/application/service/PeriodBackfillService.java` | 첨부 보강 리스너 |
| `common/event/PolicyPeriodUpdated.java` | 보강 시 발행 이벤트 |

### 수정 파일

| 파일 | 변경 |
|---|---|
| `ingestion/application/service/IngestionService.java:224-239` | `resolvePeriod()` → `PeriodResolver` 호출로 교체, 메타 전파 |
| `ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java` | `PolicyPeriodLlmProvider` → `PeriodLlmDirectExtractor` 인터페이스 변경, 시스템 프롬프트 네거티브 라벨 추가, 반환 타입 `Optional<PeriodCandidate>` |
| `ingestion/application/port/PolicyPeriodLlmProvider.java` | **삭제** (PeriodLlmDirectExtractor 로 흡수) |
| `ingestion/infrastructure/config/AttachmentAsyncConfig.java` | `periodBackfillExecutor` Bean 추가 |
| `policy/domain/model/Policy.java` | 컬럼 3개 + `updateApplyPeriod()` 메서드 추가 |
| `policy/application/dto/command/RegisterPolicyCommand.java` | `applyPeriodSource/Confidence/Evidence` 필드 추가 |
| `policy/application/service/PolicyIngestionService.java` | RegisterPolicyCommand → Policy 변환 시 메타 전파 |
| `ingestion/domain/model/IngestionRunLog.java` | `period_resolve_meta` JSONB 컬럼 + factory 확장 |
| `ingestion/application/service/IngestionService.java` (finally) | runLog 저장 시 ResolvedPeriod 메타 직렬화해서 함께 적재 |

### 신규 SQL 스크립트

| 파일 | 내용 |
|---|---|
| `backend/src/main/resources/sql/2026-05-21-policy-period-meta.sql` | Policy 컬럼 3개 추가 |
| `backend/src/main/resources/sql/2026-05-21-ingestion-run-log-period-meta.sql` | IngestionRunLog `period_resolve_meta` JSONB 컬럼 추가 |

### 신규 테스트

| 파일 | 대상 |
|---|---|
| `ingestion/domain/service/PeriodLabelsTest.java` | 라벨 매치 |
| `ingestion/domain/service/PeriodRegexPatternsTest.java` | 5개 패턴 각각 |
| `ingestion/domain/service/LabeledRegexExtractorTest.java` | 윈도우 + 마스킹 |
| `ingestion/domain/service/source/N8nApplyFieldsSourceTest.java` | n8n 양쪽/한쪽 |
| `ingestion/domain/service/source/BodyLabeledRegexSourceTest.java` | 양성 라벨 + 네거티브 제외 |
| `ingestion/domain/service/source/BodyGenericRegexSourceTest.java` | 네거티브 마스킹 후 스캔 |
| `ingestion/domain/service/source/AttachmentLabeledRegexSourceTest.java` | 여러 첨부 결합 |
| `ingestion/domain/service/PeriodResolverTest.java` | 점수/병합/모호 분기/LLM 호출 |
| `ingestion/application/service/PeriodBackfillServiceTest.java` | 트리거·필터·덮어쓰기 |
| `ingestion/application/service/IngestionServiceTest.java` (수정) | 신청기간 메타 전파 |

---

## Phase 1 — 도메인 모델 + 라벨·정규식 추출 인프라

### Task 1: 핵심 값 객체 3종 추가

**Files:**
- Create: `backend/src/main/java/com/youthfit/common/domain/PeriodSource.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/model/PeriodCandidate.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/model/ResolvedPeriod.java`

> **PeriodSource 의 위치**: `policy` 도메인이 신청기간 메타로 참조해야 하므로 `common.domain` 에 둔다. `ingestion.domain` 에 두면 `policy` 가 `ingestion` 을 참조하게 되어 모듈 의존 방향이 어긋난다.

- [ ] **Step 1: PeriodSource enum 작성**

```java
package com.youthfit.common.domain;

public enum PeriodSource {
    N8N,
    BODY_LABELED,
    BODY_GENERIC,
    ATTACHMENT_LABELED,
    LLM_DIRECT,
    LLM_DISAMBIGUATED
}
```

- [ ] **Step 2: PeriodCandidate record 작성**

```java
package com.youthfit.ingestion.domain.model;

import com.youthfit.common.domain.PeriodSource;

import java.time.LocalDate;

public record PeriodCandidate(
        LocalDate start,
        LocalDate end,
        PeriodSource source,
        double confidence,
        String evidence
) {
    public boolean hasSameRange(PeriodCandidate other) {
        return java.util.Objects.equals(this.start, other.start)
                && java.util.Objects.equals(this.end, other.end);
    }
}
```

- [ ] **Step 3: ResolvedPeriod record 작성**

```java
package com.youthfit.ingestion.domain.model;

import com.youthfit.common.domain.PeriodSource;

import java.time.LocalDate;

public record ResolvedPeriod(
        LocalDate start,
        LocalDate end,
        PeriodSource source,
        double confidence,
        String evidence
) {
    private static final ResolvedPeriod EMPTY =
            new ResolvedPeriod(null, null, null, 0.0, null);

    public static ResolvedPeriod empty() { return EMPTY; }
    public boolean isEmpty() { return start == null && end == null; }

    public static ResolvedPeriod from(PeriodCandidate c) {
        return new ResolvedPeriod(c.start(), c.end(), c.source(), c.confidence(), c.evidence());
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/domain/PeriodSource.java \
        backend/src/main/java/com/youthfit/ingestion/domain/model/PeriodCandidate.java \
        backend/src/main/java/com/youthfit/ingestion/domain/model/ResolvedPeriod.java
git commit -m "feat(ingestion): PeriodSource/PeriodCandidate/ResolvedPeriod 값 객체 추가"
```

---

### Task 2: 포트 인터페이스 4종 추가

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/PeriodExtractionContext.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/PeriodCandidateSource.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/PeriodLlmDirectExtractor.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/application/port/PeriodLlmDisambiguator.java`

- [ ] **Step 1: PeriodExtractionContext 작성**

```java
package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;
import java.util.List;

public record PeriodExtractionContext(
        String title,
        String body,
        LocalDate n8nApplyStart,
        LocalDate n8nApplyEnd,
        String externalId,
        List<String> attachmentTexts
) {
    public static PeriodExtractionContext forIngest(IngestPolicyCommand c) {
        return new PeriodExtractionContext(
                c.title(), c.body(),
                c.applyStart(), c.applyEnd(),
                c.externalId(),
                List.of()
        );
    }

    public static PeriodExtractionContext forBackfill(Policy p, List<String> attachmentTexts) {
        return new PeriodExtractionContext(
                p.getTitle(),
                p.getBody(),
                p.getApplyStart(),
                p.getApplyEnd(),
                p.getExternalId(),
                attachmentTexts == null ? List.of() : attachmentTexts
        );
    }
}
```

- [ ] **Step 2: PeriodCandidateSource 인터페이스**

```java
package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.domain.model.PeriodCandidate;
import java.util.List;

public interface PeriodCandidateSource {
    List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx);
}
```

- [ ] **Step 3: PeriodLlmDirectExtractor 인터페이스**

```java
package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.domain.model.PeriodCandidate;
import java.util.Optional;

public interface PeriodLlmDirectExtractor {
    Optional<PeriodCandidate> extract(String title, String body);
}
```

- [ ] **Step 4: PeriodLlmDisambiguator 인터페이스**

```java
package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.domain.model.PeriodCandidate;
import java.util.List;
import java.util.Optional;

public interface PeriodLlmDisambiguator {
    Optional<PeriodCandidate> choose(String bodySnippet, List<PeriodCandidate> candidates);
}
```

- [ ] **Step 5: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL — `Policy.getApplyStart/End/Title/Body/ExternalId` 게터가 이미 있으면 통과. 게터 누락 시 `Policy.java` 확인 필요.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/port/PeriodExtractionContext.java \
        backend/src/main/java/com/youthfit/ingestion/application/port/PeriodCandidateSource.java \
        backend/src/main/java/com/youthfit/ingestion/application/port/PeriodLlmDirectExtractor.java \
        backend/src/main/java/com/youthfit/ingestion/application/port/PeriodLlmDisambiguator.java
git commit -m "feat(ingestion): period extraction 포트·컨텍스트 추가"
```

---

### Task 3: PeriodLabels 사전

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/PeriodLabels.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/domain/service/PeriodLabelsTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.youthfit.ingestion.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PeriodLabels")
class PeriodLabelsTest {

    @Test
    @DisplayName("양성 라벨로 신청기간/접수기간/모집기간/공모기간/사업신청기간을 인식한다")
    void recognizesPositiveLabels() {
        assertThat(PeriodLabels.matchAll("신청기간: 2026.3.1~4.30"))
                .extracting(PeriodLabels.LabelMatch::label, PeriodLabels.LabelMatch::negative)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("신청기간", false));
    }

    @Test
    @DisplayName("네거티브 라벨로 사업기간/운영기간을 인식한다")
    void recognizesNegativeLabels() {
        List<PeriodLabels.LabelMatch> ms = PeriodLabels.matchAll("[사업기간] 2025-01-01 ~ 2025-12-31");
        assertThat(ms).hasSize(1);
        assertThat(ms.get(0).negative()).isTrue();
    }

    @Test
    @DisplayName("양성과 네거티브가 같이 있으면 둘 다 인식한다 (위치 순)")
    void recognizesBothInOrder() {
        String body = "사업기간 2025.1.1~12.31\n신청기간 2026.3.1~4.30";
        List<PeriodLabels.LabelMatch> ms = PeriodLabels.matchAll(body);
        assertThat(ms).hasSize(2);
        assertThat(ms.get(0).negative()).isTrue();   // 사업기간
        assertThat(ms.get(1).negative()).isFalse();  // 신청기간
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.PeriodLabelsTest`
Expected: FAIL — `PeriodLabels` 미정의

- [ ] **Step 3: PeriodLabels 구현**

```java
package com.youthfit.ingestion.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodLabels {

    public static final List<String> POSITIVE = List.of(
            "신청기간", "신청 기간", "접수기간", "접수 기간",
            "모집기간", "모집 기간", "공모기간", "공모 기간",
            "사업신청기간", "사업 신청 기간", "신청일정", "신청 일정"
    );

    public static final List<String> NEGATIVE = List.of(
            "사업기간", "사업 기간", "운영기간", "운영 기간",
            "수행기간", "수행 기간", "교육기간", "교육 기간",
            "지원기간", "지원 기간"
    );

    private static final Pattern POSITIVE_PATTERN = buildPattern(POSITIVE);
    private static final Pattern NEGATIVE_PATTERN = buildPattern(NEGATIVE);

    private static Pattern buildPattern(List<String> labels) {
        String alt = labels.stream()
                .map(s -> s.replace(" ", "\\s*"))
                .reduce((a, b) -> a + "|" + b).orElseThrow();
        return Pattern.compile("(" + alt + ")\\s*[:：]?", Pattern.UNICODE_CHARACTER_CLASS);
    }

    public record LabelMatch(String label, int start, int end, boolean negative) {}

    public static List<LabelMatch> matchAll(String body) {
        if (body == null || body.isBlank()) return List.of();
        List<LabelMatch> out = new ArrayList<>();
        scan(body, POSITIVE_PATTERN, false, out);
        scan(body, NEGATIVE_PATTERN, true, out);
        out.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return out;
    }

    private static void scan(String body, Pattern p, boolean negative, List<LabelMatch> out) {
        Matcher m = p.matcher(body);
        while (m.find()) {
            out.add(new LabelMatch(m.group(1), m.start(), m.end(), negative));
        }
    }

    private PeriodLabels() {}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.PeriodLabelsTest`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/service/PeriodLabels.java \
        backend/src/test/java/com/youthfit/ingestion/domain/service/PeriodLabelsTest.java
git commit -m "feat(ingestion): PeriodLabels 양성/네거티브 라벨 사전 추가"
```

---

### Task 4: 정규식 패턴 셋 (5개)

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/PeriodRegexPatterns.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/domain/service/PeriodRegexPatternsTest.java`

- [ ] **Step 1: 5개 패턴 명세 테스트 작성**

```java
package com.youthfit.ingestion.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PeriodRegexPatterns")
class PeriodRegexPatternsTest {

    @Test
    @DisplayName("FULL_RANGE: 2026.03.01 ~ 2026.04.30")
    void fullRange() {
        var hits = PeriodRegexPatterns.findAll("신청기간 2026.03.01 ~ 2026.04.30");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("YEAR_INHERIT: 2026.3.1 ~ 4.30 (종료 연도 상속)")
    void yearInherit() {
        var hits = PeriodRegexPatterns.findAll("2026.3.1 ~ 4.30");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("SAME_MONTH: 2026.3.1 ~ 31 (종료 연/월 상속)")
    void sameMonth() {
        var hits = PeriodRegexPatterns.findAll("2026.3.1 ~ 31");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("DEADLINE_ONLY: 2026.6.30 까지 (start=null)")
    void deadlineOnly() {
        var hits = PeriodRegexPatterns.findAll("신청 마감 2026.6.30 까지");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isNull();
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("요일/시간이 끼어든 FULL_RANGE")
    void fullRangeWithDayOfWeek() {
        var hits = PeriodRegexPatterns.findAll("2026.03.01(월) 09:00 ~ 2026.04.30(금) 18:00");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(hits.get(0).end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("연도 없는 자연어는 매치하지 않는다")
    void noMatchOnNaturalLanguage() {
        assertThat(PeriodRegexPatterns.findAll("매년 3월~4월")).isEmpty();
    }

    @Test
    @DisplayName("종료가 시작보다 빠르면 제외")
    void rejectReversed() {
        assertThat(PeriodRegexPatterns.findAll("2026.04.30 ~ 2026.03.01")).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.PeriodRegexPatternsTest`
Expected: FAIL — 클래스 미정의

- [ ] **Step 3: PeriodRegexPatterns 구현**

```java
package com.youthfit.ingestion.domain.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodRegexPatterns {

    public record Hit(LocalDate start, LocalDate end, PatternKind kind, String matchedText) {}

    public enum PatternKind { FULL_RANGE, YEAR_INHERIT, SAME_MONTH, DEADLINE_ONLY, START_ONLY }

    private static final String SEP = "(?:\\s*[.\\-/]\\s*|\\s*년\\s*|\\s*월\\s*)";
    private static final String Y4 = "(20\\d{2})";
    private static final String M2 = "(\\d{1,2})";
    private static final String D2 = "(\\d{1,2})";
    private static final String TAIL = "(?:\\s*일\\.?|\\s*\\([월화수목금토일]\\)|\\s*\\d{1,2}:\\d{2}|\\s*오[전후])*";
    private static final String ARROW = "\\s*[~〜∼\\-]\\s*";

    private static final Pattern FULL_RANGE = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + TAIL + ARROW + Y4 + SEP + M2 + SEP + D2 + TAIL);

    // 연도 상속: 종료에 4자리 연도가 없음 (앞에 4자리 연도가 또 오면 FULL_RANGE 가 먹음)
    private static final Pattern YEAR_INHERIT = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + TAIL + ARROW + "(?!20\\d{2})" + M2 + SEP + D2 + TAIL);

    // 동월 단축형: 종료가 D만
    private static final Pattern SAME_MONTH = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + TAIL + ARROW + "(?!20\\d{2})(?!\\d{1,2}\\s*[.\\-/월])" + D2 + "(?:\\s*일\\.?)?");

    // 단일 마감: ~/마감일 + 날짜 + 까지/마감/이내
    private static final Pattern DEADLINE_ONLY = Pattern.compile(
            "(?:[~〜∼\\-]\\s*|마감(?:일)?\\s*[:：]?\\s*)" + Y4 + SEP + M2 + SEP + D2 + "\\s*(?:까지|마감|이내)?");

    // 단일 시작: 날짜 + 부터
    private static final Pattern START_ONLY = Pattern.compile(
            Y4 + SEP + M2 + SEP + D2 + "\\s*부터");

    public static List<Hit> findAll(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Hit> hits = new ArrayList<>();
        scanFullRange(text, hits);
        scanYearInherit(text, hits);
        scanSameMonth(text, hits);
        scanDeadlineOnly(text, hits);
        scanStartOnly(text, hits);
        return hits;
    }

    private static void scanFullRange(String text, List<Hit> out) {
        Matcher m = FULL_RANGE.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            LocalDate e = toDate(m.group(4), m.group(5), m.group(6));
            if (s != null && e != null && !e.isBefore(s)) {
                out.add(new Hit(s, e, PatternKind.FULL_RANGE, m.group()));
            }
        }
    }

    private static void scanYearInherit(String text, List<Hit> out) {
        Matcher m = YEAR_INHERIT.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            LocalDate e = toDate(m.group(1), m.group(4), m.group(5));  // 연도 상속
            if (s != null && e != null && !e.isBefore(s)) {
                out.add(new Hit(s, e, PatternKind.YEAR_INHERIT, m.group()));
            }
        }
    }

    private static void scanSameMonth(String text, List<Hit> out) {
        Matcher m = SAME_MONTH.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            LocalDate e = toDate(m.group(1), m.group(2), m.group(4));  // 연·월 상속
            if (s != null && e != null && !e.isBefore(s)) {
                out.add(new Hit(s, e, PatternKind.SAME_MONTH, m.group()));
            }
        }
    }

    private static void scanDeadlineOnly(String text, List<Hit> out) {
        Matcher m = DEADLINE_ONLY.matcher(text);
        while (m.find()) {
            LocalDate e = toDate(m.group(1), m.group(2), m.group(3));
            if (e != null) out.add(new Hit(null, e, PatternKind.DEADLINE_ONLY, m.group()));
        }
    }

    private static void scanStartOnly(String text, List<Hit> out) {
        Matcher m = START_ONLY.matcher(text);
        while (m.find()) {
            LocalDate s = toDate(m.group(1), m.group(2), m.group(3));
            if (s != null) out.add(new Hit(s, null, PatternKind.START_ONLY, m.group()));
        }
    }

    private static LocalDate toDate(String y, String mo, String d) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private PeriodRegexPatterns() {}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.PeriodRegexPatternsTest`
Expected: PASS (7 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/service/PeriodRegexPatterns.java \
        backend/src/test/java/com/youthfit/ingestion/domain/service/PeriodRegexPatternsTest.java
git commit -m "feat(ingestion): PeriodRegexPatterns 5종 패턴 추가"
```

---

### Task 5: LabeledRegexExtractor (윈도우 + 마스킹 공용 유틸)

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/LabeledRegexExtractor.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/domain/service/LabeledRegexExtractorTest.java`

- [ ] **Step 1: 핵심 동작 테스트 작성**

```java
package com.youthfit.ingestion.domain.service;

import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LabeledRegexExtractor")
class LabeledRegexExtractorTest {

    private final LabeledRegexExtractor extractor = new LabeledRegexExtractor();

    @Test
    @DisplayName("양성 라벨 윈도우 안의 완전 범위를 BODY_LABELED 후보로 만든다 (confidence 0.85)")
    void labeledFullRange() {
        List<PeriodCandidate> cs = extractor.candidatesInLabeledWindows(
                "신청기간: 2026.03.01 ~ 2026.04.30", PeriodSource.BODY_LABELED);

        assertThat(cs).hasSize(1);
        assertThat(cs.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(cs.get(0).confidence()).isEqualTo(0.85);
        assertThat(cs.get(0).source()).isEqualTo(PeriodSource.BODY_LABELED);
    }

    @Test
    @DisplayName("네거티브 라벨 윈도우의 매치는 라벨 후보에서 제외된다")
    void negativeLabelWindowExcluded() {
        List<PeriodCandidate> cs = extractor.candidatesInLabeledWindows(
                "[사업기간] 2025.1.1 ~ 2025.12.31", PeriodSource.BODY_LABELED);

        assertThat(cs).isEmpty();
    }

    @Test
    @DisplayName("네거티브 윈도우를 마스킹한 본문으로 GENERIC 스캔을 한다")
    void genericScanWithNegativeMasking() {
        List<PeriodCandidate> cs = extractor.candidatesInBodyMasked(
                "사업기간 2025.1.1~12.31\n공지 2026.3.1~4.30",
                PeriodSource.BODY_GENERIC);

        assertThat(cs).hasSize(1);
        assertThat(cs.get(0).start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(cs.get(0).confidence()).isEqualTo(0.45);
    }

    @Test
    @DisplayName("단일 마감일(DEADLINE_ONLY)은 confidence 0.65 (라벨) / 0.35 (제네릭)")
    void deadlineOnlyConfidence() {
        var labeled = extractor.candidatesInLabeledWindows(
                "신청 마감 2026.6.30 까지", PeriodSource.BODY_LABELED);
        assertThat(labeled).singleElement().satisfies(c -> {
            assertThat(c.start()).isNull();
            assertThat(c.end()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(c.confidence()).isEqualTo(0.65);
        });

        var generic = extractor.candidatesInBodyMasked(
                "공지 2026.6.30 까지", PeriodSource.BODY_GENERIC);
        assertThat(generic).singleElement().satisfies(c -> {
            assertThat(c.confidence()).isEqualTo(0.35);
        });
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.LabeledRegexExtractorTest`
Expected: FAIL — 클래스 미정의

- [ ] **Step 3: LabeledRegexExtractor 구현**

```java
package com.youthfit.ingestion.domain.service;

import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LabeledRegexExtractor {

    private static final int WINDOW_MAX_CHARS = 200;
    private static final int EVIDENCE_MAX = 200;

    public List<PeriodCandidate> candidatesInLabeledWindows(String body, PeriodSource source) {
        if (body == null || body.isBlank()) return List.of();
        List<PeriodLabels.LabelMatch> labels = PeriodLabels.matchAll(body);
        if (labels.isEmpty()) return List.of();

        List<PeriodCandidate> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            PeriodLabels.LabelMatch lm = labels.get(i);
            if (lm.negative()) continue;

            int wStart = lm.end();
            int wEnd = computeWindowEnd(body, wStart, i + 1 < labels.size() ? labels.get(i + 1).start() : body.length());
            String window = body.substring(wStart, wEnd);

            for (PeriodRegexPatterns.Hit hit : PeriodRegexPatterns.findAll(window)) {
                double conf = labeledConfidence(hit.kind());
                out.add(new PeriodCandidate(
                        hit.start(), hit.end(), source, conf,
                        clipEvidence(lm.label() + ": " + hit.matchedText())));
            }
        }
        return out;
    }

    public List<PeriodCandidate> candidatesInBodyMasked(String body, PeriodSource source) {
        if (body == null || body.isBlank()) return List.of();
        String masked = maskNegativeWindows(body);
        List<PeriodCandidate> out = new ArrayList<>();
        for (PeriodRegexPatterns.Hit hit : PeriodRegexPatterns.findAll(masked)) {
            double conf = genericConfidence(hit.kind());
            out.add(new PeriodCandidate(
                    hit.start(), hit.end(), source, conf,
                    clipEvidence(hit.matchedText())));
        }
        return out;
    }

    private String maskNegativeWindows(String body) {
        List<PeriodLabels.LabelMatch> labels = PeriodLabels.matchAll(body);
        StringBuilder sb = new StringBuilder(body);
        for (int i = 0; i < labels.size(); i++) {
            PeriodLabels.LabelMatch lm = labels.get(i);
            if (!lm.negative()) continue;
            int wStart = lm.end();
            int wEnd = computeWindowEnd(body, wStart,
                    i + 1 < labels.size() ? labels.get(i + 1).start() : body.length());
            for (int p = wStart; p < wEnd; p++) {
                sb.setCharAt(p, ' ');
            }
        }
        return sb.toString();
    }

    private int computeWindowEnd(String body, int start, int nextLabelStart) {
        int blank = findBlankLine(body, start);
        int hard = Math.min(body.length(), start + WINDOW_MAX_CHARS);
        return Math.min(Math.min(nextLabelStart, blank == -1 ? body.length() : blank), hard);
    }

    private int findBlankLine(String body, int from) {
        int i = body.indexOf("\n\n", from);
        return i == -1 ? body.indexOf("\n \n", from) : i;
    }

    private double labeledConfidence(PeriodRegexPatterns.PatternKind k) {
        return switch (k) {
            case FULL_RANGE -> 0.85;
            case YEAR_INHERIT, SAME_MONTH -> 0.80;
            case DEADLINE_ONLY, START_ONLY -> 0.65;
        };
    }

    private double genericConfidence(PeriodRegexPatterns.PatternKind k) {
        return switch (k) {
            case FULL_RANGE, YEAR_INHERIT, SAME_MONTH -> 0.45;
            case DEADLINE_ONLY, START_ONLY -> 0.35;
        };
    }

    private String clipEvidence(String s) {
        if (s == null) return null;
        String trimmed = s.replaceAll("\\s+", " ").trim();
        return trimmed.length() > EVIDENCE_MAX ? trimmed.substring(0, EVIDENCE_MAX) : trimmed;
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.LabeledRegexExtractorTest`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/service/LabeledRegexExtractor.java \
        backend/src/test/java/com/youthfit/ingestion/domain/service/LabeledRegexExtractorTest.java
git commit -m "feat(ingestion): LabeledRegexExtractor (라벨 윈도우 + 네거티브 마스킹) 추가"
```

---

## Phase 2 — 4개 CandidateSource + PeriodResolver

### Task 6: 4개 PeriodCandidateSource 구현체

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/source/N8nApplyFieldsSource.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/source/BodyLabeledRegexSource.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/source/BodyGenericRegexSource.java`
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/source/AttachmentLabeledRegexSource.java`
- Test: 같은 경로 아래 4개 `*Test.java`

- [ ] **Step 1: 4개 소스의 동작 테스트 작성** (네 파일을 한 번에 작성)

각 테스트는 단순한 input → expected candidates 만 검증.

```java
// N8nApplyFieldsSourceTest
@Test @DisplayName("양쪽 모두 존재 → confidence 0.60")
void bothPresent() {
    var ctx = new PeriodExtractionContext("t", "b",
            LocalDate.of(2026,3,1), LocalDate.of(2026,4,30), "ext", List.of());
    var cs = new N8nApplyFieldsSource().findCandidates(ctx);
    assertThat(cs).singleElement().satisfies(c -> {
        assertThat(c.confidence()).isEqualTo(0.60);
        assertThat(c.source()).isEqualTo(PeriodSource.N8N);
    });
}

@Test @DisplayName("한쪽만 존재 → confidence 0.40")
void onlyEndPresent() {
    var ctx = new PeriodExtractionContext("t", "b",
            null, LocalDate.of(2026,4,30), "ext", List.of());
    assertThat(new N8nApplyFieldsSource().findCandidates(ctx))
            .singleElement().satisfies(c -> assertThat(c.confidence()).isEqualTo(0.40));
}

@Test @DisplayName("양쪽 모두 null → 빈 리스트")
void bothNull() {
    var ctx = new PeriodExtractionContext("t", "b", null, null, "ext", List.of());
    assertThat(new N8nApplyFieldsSource().findCandidates(ctx)).isEmpty();
}
```

```java
// BodyLabeledRegexSourceTest
@Test @DisplayName("신청기간 라벨 윈도우의 완전 범위를 후보로 만든다")
void labeled() {
    var src = new BodyLabeledRegexSource(new LabeledRegexExtractor());
    var ctx = new PeriodExtractionContext("t",
            "신청기간 2026.3.1 ~ 2026.4.30", null, null, "ext", List.of());
    assertThat(src.findCandidates(ctx)).singleElement()
            .satisfies(c -> assertThat(c.source()).isEqualTo(PeriodSource.BODY_LABELED));
}

@Test @DisplayName("사업기간 안의 범위는 후보가 되지 않는다")
void negativeLabelExcluded() {
    var src = new BodyLabeledRegexSource(new LabeledRegexExtractor());
    var ctx = new PeriodExtractionContext("t",
            "사업기간 2025.1.1 ~ 2025.12.31", null, null, "ext", List.of());
    assertThat(src.findCandidates(ctx)).isEmpty();
}
```

```java
// BodyGenericRegexSourceTest
@Test @DisplayName("사업기간 윈도우는 마스킹되어 후보에서 제외된다")
void negativeMasked() {
    var src = new BodyGenericRegexSource(new LabeledRegexExtractor());
    var ctx = new PeriodExtractionContext("t",
            "[사업기간] 2025.1.1 ~ 2025.12.31\n\n공지 2026.3.1 ~ 4.30",
            null, null, "ext", List.of());
    assertThat(src.findCandidates(ctx)).singleElement()
            .satisfies(c -> assertThat(c.start()).isEqualTo(LocalDate.of(2026,3,1)));
}
```

```java
// AttachmentLabeledRegexSourceTest
@Test @DisplayName("여러 첨부 텍스트의 라벨 후보를 모두 모은다")
void multiAttachments() {
    var src = new AttachmentLabeledRegexSource(new LabeledRegexExtractor());
    var ctx = new PeriodExtractionContext("t", "", null, null, "ext", List.of(
            "신청기간 2026.3.1 ~ 4.30",
            "신청기간 2026.5.1 ~ 5.31"
    ));
    assertThat(src.findCandidates(ctx)).hasSize(2)
            .allSatisfy(c -> assertThat(c.source()).isEqualTo(PeriodSource.ATTACHMENT_LABELED));
}

@Test @DisplayName("첨부 LABELED 의 confidence 는 BODY_LABELED 보다 0.10 낮다 (0.75)")
void attachmentConfidence() {
    var src = new AttachmentLabeledRegexSource(new LabeledRegexExtractor());
    var ctx = new PeriodExtractionContext("t", "", null, null, "ext",
            List.of("신청기간 2026.3.1 ~ 2026.4.30"));
    assertThat(src.findCandidates(ctx)).singleElement()
            .satisfies(c -> assertThat(c.confidence()).isEqualTo(0.75));
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.domain.service.source.*"`
Expected: FAIL — 4개 클래스 미정의

- [ ] **Step 3: 4개 소스 구현**

```java
// N8nApplyFieldsSource.java
package com.youthfit.ingestion.domain.service.source;

import com.youthfit.ingestion.application.port.PeriodCandidateSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class N8nApplyFieldsSource implements PeriodCandidateSource {
    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        if (ctx.n8nApplyStart() == null && ctx.n8nApplyEnd() == null) return List.of();
        boolean both = ctx.n8nApplyStart() != null && ctx.n8nApplyEnd() != null;
        return List.of(new PeriodCandidate(
                ctx.n8nApplyStart(), ctx.n8nApplyEnd(),
                PeriodSource.N8N,
                both ? 0.60 : 0.40,
                "n8n applyStart/applyEnd"
        ));
    }
}
```

```java
// BodyLabeledRegexSource.java
package com.youthfit.ingestion.domain.service.source;

import com.youthfit.ingestion.application.port.PeriodCandidateSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BodyLabeledRegexSource implements PeriodCandidateSource {
    private final LabeledRegexExtractor extractor;

    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        return extractor.candidatesInLabeledWindows(ctx.body(), PeriodSource.BODY_LABELED);
    }
}
```

```java
// BodyGenericRegexSource.java
package com.youthfit.ingestion.domain.service.source;

import com.youthfit.ingestion.application.port.PeriodCandidateSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BodyGenericRegexSource implements PeriodCandidateSource {
    private final LabeledRegexExtractor extractor;

    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        return extractor.candidatesInBodyMasked(ctx.body(), PeriodSource.BODY_GENERIC);
    }
}
```

```java
// AttachmentLabeledRegexSource.java
package com.youthfit.ingestion.domain.service.source;

import com.youthfit.ingestion.application.port.PeriodCandidateSource;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import com.youthfit.ingestion.domain.service.LabeledRegexExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttachmentLabeledRegexSource implements PeriodCandidateSource {
    private final LabeledRegexExtractor extractor;

    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        if (ctx.attachmentTexts() == null || ctx.attachmentTexts().isEmpty()) return List.of();
        return ctx.attachmentTexts().stream()
                .flatMap(t -> extractor.candidatesInLabeledWindows(t, PeriodSource.ATTACHMENT_LABELED).stream())
                .map(c -> new PeriodCandidate(
                        c.start(), c.end(), c.source(),
                        c.confidence() - 0.10,  // 첨부 페널티
                        c.evidence()))
                .toList();
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.domain.service.source.*"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/service/source/ \
        backend/src/test/java/com/youthfit/ingestion/domain/service/source/
git commit -m "feat(ingestion): PeriodCandidateSource 4종 구현 추가"
```

---

### Task 7: PeriodResolver — 통합 알고리즘

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/domain/service/PeriodResolver.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/domain/service/PeriodResolverTest.java`

- [ ] **Step 1: PeriodResolver 행동 시나리오 테스트 작성**

```java
package com.youthfit.ingestion.domain.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.ingestion.application.port.*;
import com.youthfit.ingestion.domain.model.*;
import com.youthfit.ingestion.domain.service.source.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("PeriodResolver")
class PeriodResolverTest {

    private final LabeledRegexExtractor extractor = new LabeledRegexExtractor();
    private final PeriodLlmDirectExtractor llmDirect = mock(PeriodLlmDirectExtractor.class);
    private final PeriodLlmDisambiguator llmDisamb = mock(PeriodLlmDisambiguator.class);
    private final CostGuard costGuard = mock(CostGuard.class);

    private PeriodResolver newResolver() {
        return new PeriodResolver(
                List.of(
                        new N8nApplyFieldsSource(),
                        new BodyLabeledRegexSource(extractor),
                        new BodyGenericRegexSource(extractor),
                        new AttachmentLabeledRegexSource(extractor)),
                llmDirect, llmDisamb, costGuard);
    }

    @Test
    @DisplayName("BODY_LABELED 0.85 가 N8N 0.60 을 이긴다")
    void labeledBeatsN8n() {
        when(costGuard.enabled()).thenReturn(false);
        var ctx = new PeriodExtractionContext("t",
                "신청기간 2026.3.1 ~ 2026.4.30",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31),
                "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);

        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(r.source()).isEqualTo(PeriodSource.BODY_LABELED);
        verifyNoInteractions(llmDirect, llmDisamb);
    }

    @Test
    @DisplayName("후보 0개 + !CostGuard → LLM direct 호출")
    void noCandidatesCallsLlmDirect() {
        when(costGuard.enabled()).thenReturn(false);
        when(llmDirect.extract(any(), any())).thenReturn(Optional.of(new PeriodCandidate(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                PeriodSource.LLM_DIRECT, 0.60, "llm")));

        var ctx = new PeriodExtractionContext("t",
                "이번 분기 한 달간 모집", null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.source()).isEqualTo(PeriodSource.LLM_DIRECT);
        verify(llmDirect).extract(any(), any());
        verifyNoInteractions(llmDisamb);
    }

    @Test
    @DisplayName("후보 ≥ 2 + 최고점 < 0.70 → disambiguator 호출")
    void ambiguousCallsDisambiguator() {
        when(costGuard.enabled()).thenReturn(false);
        when(llmDisamb.choose(any(), any())).thenAnswer(inv -> {
            List<PeriodCandidate> cs = inv.getArgument(1);
            return Optional.of(new PeriodCandidate(
                    cs.get(0).start(), cs.get(0).end(),
                    PeriodSource.LLM_DISAMBIGUATED, 0.85, "chosen"));
        });

        // GENERIC 0.45 두 개 (마스킹 없음 — 라벨 없는 두 범위)
        var ctx = new PeriodExtractionContext("t",
                "2026.3.1 ~ 4.30 안내 / 추가 2026.5.1 ~ 5.31 가능",
                null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.source()).isEqualTo(PeriodSource.LLM_DISAMBIGUATED);
        verify(llmDisamb).choose(any(), any());
        verifyNoInteractions(llmDirect);
    }

    @Test
    @DisplayName("CostGuard 활성 → LLM 두 경로 모두 차단")
    void costGuardBlocksLlm() {
        when(costGuard.enabled()).thenReturn(true);
        var ctx = new PeriodExtractionContext("t",
                "이번 분기 한 달간 모집", null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.isEmpty()).isTrue();
        verifyNoInteractions(llmDirect, llmDisamb);
    }

    @Test
    @DisplayName("최종 confidence < 0.55 → empty 반환")
    void belowFloorReturnsEmpty() {
        when(costGuard.enabled()).thenReturn(false);
        // GENERIC 단독 0.45 → 임계값 미만
        var ctx = new PeriodExtractionContext("t",
                "공지 2026.3.1 ~ 2026.4.30", null, null, "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("동일 (start,end) 두 소스가 일치하면 보너스로 점수 상승")
    void multiSourceBonus() {
        when(costGuard.enabled()).thenReturn(false);
        // N8N(0.60) + BODY_LABELED(0.85) 가 같은 범위 → max(0.85) + 0.05 = 0.90
        var ctx = new PeriodExtractionContext("t",
                "신청기간 2026.3.1 ~ 2026.4.30",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30),
                "ext", List.of());

        ResolvedPeriod r = newResolver().resolve(ctx);
        assertThat(r.confidence()).isEqualTo(0.90);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.PeriodResolverTest`
Expected: FAIL

- [ ] **Step 3: PeriodResolver 구현**

```java
package com.youthfit.ingestion.domain.service;

import com.youthfit.common.config.CostGuard;
import com.youthfit.ingestion.application.port.*;
import com.youthfit.ingestion.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeriodResolver {

    private static final Logger log = LoggerFactory.getLogger(PeriodResolver.class);

    private static final double AMBIGUOUS_BELOW = 0.70;
    private static final double FINAL_FLOOR = 0.55;
    private static final double GROUP_BONUS_PER_EXTRA = 0.05;
    private static final double GROUP_BONUS_MAX = 0.15;

    private final List<PeriodCandidateSource> sources;
    private final PeriodLlmDirectExtractor llmDirect;
    private final PeriodLlmDisambiguator llmDisambiguator;
    private final CostGuard costGuard;

    public ResolvedPeriod resolve(PeriodExtractionContext ctx) {
        List<PeriodCandidate> candidates = collect(ctx);

        if (candidates.isEmpty() && !costGuard.enabled()) {
            Optional<PeriodCandidate> direct = llmDirect.extract(ctx.title(), ctx.body());
            direct.ifPresent(candidates::add);
        }

        if (candidates.isEmpty()) {
            log.info("period-resolve externalId={} result=empty (no candidates)", ctx.externalId());
            return ResolvedPeriod.empty();
        }

        List<PeriodCandidate> grouped = mergeByRange(candidates);
        PeriodCandidate best = grouped.stream()
                .max(Comparator.comparingDouble(PeriodCandidate::confidence))
                .orElseThrow();

        if (best.confidence() < AMBIGUOUS_BELOW && grouped.size() >= 2 && !costGuard.enabled()) {
            String snippet = buildSnippet(grouped);
            Optional<PeriodCandidate> chosen = llmDisambiguator.choose(snippet, grouped);
            if (chosen.isPresent()) best = chosen.get();
        }

        if (best.confidence() < FINAL_FLOOR) {
            log.info("period-resolve externalId={} result=empty (below floor) candidates={} best_conf={}",
                    ctx.externalId(), grouped.size(), best.confidence());
            return ResolvedPeriod.empty();
        }

        log.info("period-resolve externalId={} source={} confidence={} start={} end={} evidence=\"{}\"",
                ctx.externalId(), best.source(), best.confidence(), best.start(), best.end(), best.evidence());
        return ResolvedPeriod.from(best);
    }

    private List<PeriodCandidate> collect(PeriodExtractionContext ctx) {
        return sources.stream()
                .flatMap(s -> s.findCandidates(ctx).stream())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<PeriodCandidate> mergeByRange(List<PeriodCandidate> cs) {
        Map<String, List<PeriodCandidate>> grouped = cs.stream()
                .collect(Collectors.groupingBy(c -> String.valueOf(c.start()) + "_" + c.end()));
        List<PeriodCandidate> out = new ArrayList<>();
        for (List<PeriodCandidate> group : grouped.values()) {
            PeriodCandidate max = group.stream()
                    .max(Comparator.comparingDouble(PeriodCandidate::confidence))
                    .orElseThrow();
            double bonus = Math.min(GROUP_BONUS_MAX, GROUP_BONUS_PER_EXTRA * (group.size() - 1));
            out.add(new PeriodCandidate(
                    max.start(), max.end(), max.source(),
                    Math.min(1.0, max.confidence() + bonus),
                    max.evidence()
            ));
        }
        return out;
    }

    private String buildSnippet(List<PeriodCandidate> cs) {
        StringBuilder sb = new StringBuilder();
        for (PeriodCandidate c : cs) {
            sb.append("[").append(c.source()).append("] ").append(c.evidence()).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.domain.service.PeriodResolverTest`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/domain/service/PeriodResolver.java \
        backend/src/test/java/com/youthfit/ingestion/domain/service/PeriodResolverTest.java
git commit -m "feat(ingestion): PeriodResolver — 후보 통합/점수/모호 분기/LLM 두 경로"
```

---

## Phase 3 — LLM 두 경로 + IngestionService 통합

### Task 8: 기존 OpenAiPolicyPeriodExtractor 를 PeriodLlmDirectExtractor 로 마이그레이션

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodExtractor.java`
- Delete: `backend/src/main/java/com/youthfit/ingestion/application/port/PolicyPeriodLlmProvider.java`

- [ ] **Step 1: 사용처 확인**

```bash
cd backend && grep -rn "PolicyPeriodLlmProvider" src/main src/test
```
Expected: `IngestionService` 와 `OpenAiPolicyPeriodExtractor` 에서만 참조. 다른 곳 없음 확인.

- [ ] **Step 2: OpenAiPolicyPeriodExtractor 인터페이스/시그니처 변경**

기존 `implements PolicyPeriodLlmProvider`, `PolicyPeriod extractPeriod(...)` →
`implements PeriodLlmDirectExtractor`, `Optional<PeriodCandidate> extract(...)`.

시스템 프롬프트에 네거티브 라벨 명시 한 줄 추가:
```
- "사업기간", "운영기간", "수행기간", "교육기간", "지원기간"은 신청기간이 아니므로 무시합니다.
```

반환: 성공 시 `Optional.of(new PeriodCandidate(start, end, PeriodSource.LLM_DIRECT, 0.60, "llm-direct"))`, 실패 시 `Optional.empty()`.

(전체 코드 — 기존 파일을 다음 내용으로 교체)

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.PeriodLlmDirectExtractor;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OpenAiPolicyPeriodExtractor implements PeriodLlmDirectExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPolicyPeriodExtractor.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 청년 정책 본문에서 신청 기간만 추출하는 파서입니다.
            반드시 아래 JSON 스키마로만 응답하세요.
            {"applyStart": "YYYY-MM-DD" | null, "applyEnd": "YYYY-MM-DD" | null}

            규칙:
            - 정확한 연/월/일이 확인될 때만 값을 채웁니다.
            - "사업기간", "운영기간", "수행기간", "교육기간", "지원기간"은
              신청기간이 아니므로 무시합니다.
            - "연중수시", "상시접수", "공고 시 별도 안내", "추후 공지" 등은 null.
            - 연도가 없는 기간("매년 3월~4월")은 null.
            - 본문에 없는 정보를 지어내지 마세요.
            - JSON 외의 텍스트를 출력하지 마세요.
            """;

    private final OpenAiPolicyPeriodProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<PeriodCandidate> extract(String title, String body) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) return Optional.empty();
        if (body == null || body.isBlank()) return Optional.empty();

        int limit = properties.getMaxBodyChars() > 0 ? properties.getMaxBodyChars() : body.length();
        String truncatedBody = body.length() > limit ? body.substring(0, limit) : body;
        String userMessage = "제목: " + (title == null ? "" : title) + "\n\n본문:\n" + truncatedBody;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)));

        try {
            JsonNode response = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                log.warn("기간 추출 응답이 비어 있습니다: title={}", title);
                return Optional.empty();
            }

            publishCostEvent(response);
            String content = response.get("choices").get(0).get("message").get("content").asText();
            return parseContent(content);
        } catch (RuntimeException e) {
            log.warn("LLM 기반 기간 추출 실패: title={}, cause={}", title, e.getMessage());
            return Optional.empty();
        }
    }

    private void publishCostEvent(JsonNode response) {
        try {
            JsonNode usage = response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.INGESTION, properties.getModel(), prompt, completion, Instant.now()));
        } catch (Exception e) {
            log.warn("ingestion LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }
    }

    private Optional<PeriodCandidate> parseContent(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonNode node = objectMapper.readTree(json);
            LocalDate start = parseDate(node.get("applyStart"));
            LocalDate end = parseDate(node.get("applyEnd"));
            if (start == null && end == null) return Optional.empty();
            if (start != null && end != null && end.isBefore(start)) return Optional.empty();
            return Optional.of(new PeriodCandidate(start, end, PeriodSource.LLM_DIRECT, 0.60, "llm-direct"));
        } catch (JacksonException e) {
            log.warn("기간 JSON 파싱 실패: payload={}", json);
            return Optional.empty();
        }
    }

    private LocalDate parseDate(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = node.asText();
        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) return null;
        try { return LocalDate.parse(text); } catch (DateTimeException e) { return null; }
    }
}
```

- [ ] **Step 3: PolicyPeriodLlmProvider 포트 삭제**

```bash
rm backend/src/main/java/com/youthfit/ingestion/application/port/PolicyPeriodLlmProvider.java
```

- [ ] **Step 4: IngestionService 의 임포트 정리 (다음 task 7 에서 본격 교체)** — 이 step에서는 `PolicyPeriodLlmProvider` 사용 코드를 일단 주석 또는 임시 처리. **그러나 컴파일 안정성을 위해 다음 task와 한 커밋으로 합치는 것을 권장.** 본 task 는 빌드 깨지면 다음 task 까지 push 보류.

이 task의 step 4 는 다음 task에 의존하므로, **task 9 까지 끝낸 뒤 한 번에 빌드 확인 + 커밋**.

---

### Task 9: PeriodLlmDisambiguator OpenAI 구현체

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/infrastructure/external/OpenAiPolicyPeriodDisambiguator.java`

- [ ] **Step 1: 구현 작성**

```java
package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.PeriodLlmDisambiguator;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.PeriodSource;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OpenAiPolicyPeriodDisambiguator implements PeriodLlmDisambiguator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPolicyPeriodDisambiguator.class);
    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            당신은 정책 본문에서 추출된 여러 신청기간 후보 중 정답을 선택하는 검수자입니다.
            본문 스니펫과 후보 목록을 보고, 어느 후보가 진짜 신청기간인지 ID로 응답하세요.

            반드시 아래 JSON 스키마로만 응답하세요.
            {"chosenId": <정수|null>, "confidence": <0.0-1.0>, "reasoning": "<한 줄>"}

            규칙:
            - "사업기간", "운영기간", "수행기간"으로 보이는 후보는 무시합니다.
            - 정답 후보가 없다면 chosenId = null.
            - confidence 는 본문 근거가 명확할수록 높게.
            - JSON 외의 텍스트를 출력하지 마세요.
            """;

    private final OpenAiPolicyPeriodProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<PeriodCandidate> choose(String bodySnippet, List<PeriodCandidate> candidates) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) return Optional.empty();
        if (candidates == null || candidates.size() < 2) return Optional.empty();

        String userMessage = "본문 스니펫:\n" + bodySnippet + "\n\n후보:\n" + serializeCandidates(candidates);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)));

        try {
            JsonNode resp = restClient.post().uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body).retrieve().body(JsonNode.class);
            if (resp == null || !resp.has("choices") || resp.get("choices").isEmpty()) return Optional.empty();
            publishCostEvent(resp);
            String content = resp.get("choices").get(0).get("message").get("content").asText();
            return parse(content, candidates);
        } catch (RuntimeException e) {
            log.warn("disambiguator 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String serializeCandidates(List<PeriodCandidate> cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.size(); i++) {
            PeriodCandidate c = cs.get(i);
            sb.append(i).append(". start=").append(c.start())
                    .append(" end=").append(c.end())
                    .append(" source=").append(c.source())
                    .append(" evidence=\"").append(c.evidence()).append("\"\n");
        }
        return sb.toString();
    }

    private Optional<PeriodCandidate> parse(String json, List<PeriodCandidate> cs) {
        try {
            JsonNode n = objectMapper.readTree(json);
            JsonNode idNode = n.get("chosenId");
            if (idNode == null || idNode.isNull()) return Optional.empty();
            int id = idNode.asInt(-1);
            if (id < 0 || id >= cs.size()) return Optional.empty();
            double conf = n.has("confidence") ? n.get("confidence").asDouble(0.7) : 0.7;
            PeriodCandidate chosen = cs.get(id);
            return Optional.of(new PeriodCandidate(
                    chosen.start(), chosen.end(),
                    PeriodSource.LLM_DISAMBIGUATED, conf,
                    chosen.evidence()));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private void publishCostEvent(JsonNode resp) {
        try {
            JsonNode u = resp.get("usage");
            int p = u == null || !u.has("prompt_tokens") ? 0 : u.get("prompt_tokens").asInt();
            int c = u == null || !u.has("completion_tokens") ? 0 : u.get("completion_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(LlmModule.INGESTION, properties.getModel(), p, c, Instant.now()));
        } catch (Exception ignored) {}
    }
}
```

- [ ] **Step 2: 빌드 확인 (task 8 + 9 합쳐서)**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL — 단, `IngestionService` 에서 `PolicyPeriodLlmProvider` 참조가 남아 있어 실패 가능. 그러면 task 10 까지 진행 후 한 번에 통과.

- [ ] **Step 3: 커밋 (task 10 까지 합쳐서)** — 본 task 단독 커밋 보류

---

### Task 10: IngestionService.resolvePeriod() → PeriodResolver 호출로 교체

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`
- Modify: `backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java`

- [ ] **Step 1: IngestionService 의존 교체**

`IngestionService` 의 필드:
```java
// 삭제
private final PolicyPeriodExtractor policyPeriodExtractor;
private final PolicyPeriodLlmProvider policyPeriodLlmProvider;

// 추가
private final PeriodResolver periodResolver;
```

`resolvePeriod()` 전체를 다음으로 교체:
```java
private ResolvedPeriod resolvePeriod(IngestPolicyCommand command) {
    PeriodExtractionContext ctx = PeriodExtractionContext.forIngest(command);
    return periodResolver.resolve(ctx);
}
```

호출처 (`receivePolicy` 내부) 수정:
```java
// 기존: PolicyPeriod period = resolvePeriod(command);
ResolvedPeriod period = resolvePeriod(command);
// ...
period.start(), period.end()  // PolicyPeriod 와 동일한 시그니처
```

- [ ] **Step 2: IngestionServiceTest 의 mock 의존 변경**

기존 `policyPeriodLlmProvider`, `policyPeriodExtractor` 목 → `periodResolver` 목으로 교체.

핵심 시나리오 테스트 한 개 유지 + 다음 추가:
```java
@Test
@DisplayName("PeriodResolver 의 결과가 정책 등록 커맨드의 applyStart/End 로 전달된다")
void resolverResultPropagatedToRegisterCommand() {
    when(periodResolver.resolve(any())).thenReturn(new ResolvedPeriod(
            LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
            PeriodSource.BODY_LABELED, 0.85, "신청기간..."));
    // ... receivePolicy 호출
    verify(policyIngestionService).registerPolicy(argThat(cmd ->
            cmd.applyStart().equals(LocalDate.of(2026,3,1))
                && cmd.applyEnd().equals(LocalDate.of(2026,4,30))));
}
```

- [ ] **Step 3: 기존 PolicyPeriodExtractor / PolicyPeriodExtractorTest 삭제**

```bash
rm backend/src/main/java/com/youthfit/ingestion/domain/service/PolicyPeriodExtractor.java
rm backend/src/test/java/com/youthfit/ingestion/domain/service/PolicyPeriodExtractorTest.java
```

(기능은 `BodyLabeledRegexSource` + `BodyGenericRegexSource` 로 흡수됨)

- [ ] **Step 4: 전체 빌드 + 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋 (task 8/9/10 합쳐서)**

```bash
git add backend/src/main/java/com/youthfit/ingestion/infrastructure/external/ \
        backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/IngestionServiceTest.java
# 삭제도 add
git add -u backend/src/main/java/com/youthfit/ingestion/application/port/ \
        backend/src/main/java/com/youthfit/ingestion/domain/service/PolicyPeriodExtractor.java \
        backend/src/test/java/com/youthfit/ingestion/domain/service/PolicyPeriodExtractorTest.java
git commit -m "feat(ingestion): IngestionService 가 PeriodResolver 사용, LLM 두 경로 분리"
```

---

## Phase 4 — Policy 메타 컬럼

### Task 11: Policy 메타 컬럼 추가 (SQL + 엔티티)

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-21-policy-period-meta.sql`
- Modify: `backend/src/main/java/com/youthfit/policy/domain/model/Policy.java`

- [ ] **Step 1: SQL 스크립트 작성**

```sql
-- 2026-05-21-policy-period-meta.sql
-- 신청기간 추출 메타 (source/confidence/evidence) — nullable, 기존 행은 NULL
ALTER TABLE policy
    ADD COLUMN apply_period_source     VARCHAR(32),
    ADD COLUMN apply_period_confidence DOUBLE PRECISION,
    ADD COLUMN apply_period_evidence   VARCHAR(200);
```

- [ ] **Step 2: Policy 엔티티 필드 + 도메인 메서드 추가**

`Policy.java` import 추가:
```java
import com.youthfit.common.domain.PeriodSource;
```

필드 영역에 추가:
```java
@Column(name = "apply_period_source", length = 32)
@Enumerated(EnumType.STRING)
private PeriodSource applyPeriodSource;

@Column(name = "apply_period_confidence")
private Double applyPeriodConfidence;

@Column(name = "apply_period_evidence", length = 200)
private String applyPeriodEvidence;
```

도메인 메서드 추가 (`ResolvedPeriod` 직접 참조하지 않고 4파라미터로 — 모듈 결합도 낮춤):
```java
public void updateApplyPeriod(
        LocalDate start, LocalDate end,
        PeriodSource source, Double confidence, String evidence) {
    this.applyStart = start;
    this.applyEnd = end;
    this.applyPeriodSource = source;
    this.applyPeriodConfidence = confidence;
    this.applyPeriodEvidence = evidence;
}
```

`Policy.builder()` 가 새 필드들을 자동으로 받도록 Lombok `@Builder` 가 클래스에 적용되어 있는지 확인 — 이미 builder 패턴 사용 중이므로 새 필드도 자동으로 builder 메서드에 포함된다.

- [ ] **Step 3: SQL 적용 (운영 절차 안내)**

테스트는 `ddl-auto: update` 환경에서 자동 반영. 운영은 별도로 SQL 적용 필요 (`backend/src/main/resources/sql/` 디렉터리의 다른 SQL 들이 어떻게 적용되는지는 `docs/OPS.md` 참고).

- [ ] **Step 4: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-21-policy-period-meta.sql \
        backend/src/main/java/com/youthfit/policy/domain/model/Policy.java
git commit -m "feat(policy): apply_period_source/confidence/evidence 컬럼 추가"
```

---

### Task 12: RegisterPolicyCommand 확장 + IngestionService 메타 전파

**Files:**
- Modify: `backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java`
- Modify: `backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: RegisterPolicyCommand 끝에 메타 3개 필드 추가**

기존 record 의 마지막 필드 뒤에 추가:
```java
// ... 기존 필드들 ...
com.youthfit.common.domain.PeriodSource applyPeriodSource,
Double applyPeriodConfidence,
String applyPeriodEvidence
```

- [ ] **Step 2: PolicyIngestionService.registerPolicy() 의 두 분기에 메타 전파**

`PolicyIngestionService.java:89-117` 의 신규 등록 분기 — `Policy.builder()` 체인 끝에 다음 3개 메서드 추가 (`.applyEnd(...)` 다음):

```java
Policy policy = Policy.builder()
        .title(command.title())
        .summary(command.summary())
        // ... 기존 체인 ...
        .applyStart(command.applyStart())
        .applyEnd(command.applyEnd())
        .applyPeriodSource(command.applyPeriodSource())       // 신규
        .applyPeriodConfidence(command.applyPeriodConfidence()) // 신규
        .applyPeriodEvidence(command.applyPeriodEvidence())   // 신규
        .referenceYear(command.referenceYear())
        // ... 나머지 체인 ...
        .build();
```

업데이트 분기 (`PolicyIngestionService.java:71-86` 영역, `policy.update*` 도메인 메서드 호출 부근) — 기존 정책 업데이트 시점에 신청기간 메타가 갱신되도록 `policy.updateApplyPeriod()` 호출 추가:

```java
// 기존 update 분기 안에서 enrichment 갱신 직전에
if (command.applyPeriodConfidence() != null) {
    policy.updateApplyPeriod(
            command.applyStart(), command.applyEnd(),
            command.applyPeriodSource(),
            command.applyPeriodConfidence(),
            command.applyPeriodEvidence());
}
```

> **검증 포인트**: 정확한 update 분기 위치는 파일을 열어 `policy.replace*` / `applyStart` 갱신 코드가 어디 있는지 확인 후 그 옆에 추가. 신규 등록 분기는 line 89-117 의 builder 체인.

- [ ] **Step 3: IngestionService.receivePolicy() 의 RegisterPolicyCommand 생성 시 메타 추가**

기존:
```java
RegisterPolicyCommand registerCommand = new RegisterPolicyCommand(
        command.title(), summary, command.body(),
        ..., period.start(), period.end(), ...);
```

수정:
```java
ResolvedPeriod period = resolvePeriod(command);
RegisterPolicyCommand registerCommand = new RegisterPolicyCommand(
        command.title(), summary, command.body(),
        ..., period.start(), period.end(), ...,
        period.isEmpty() ? null : period.source(),
        period.isEmpty() ? null : period.confidence(),
        period.isEmpty() ? null : period.evidence()
);
```

- [ ] **Step 4: 전체 빌드 + 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/policy/application/dto/command/RegisterPolicyCommand.java \
        backend/src/main/java/com/youthfit/policy/application/service/PolicyIngestionService.java \
        backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java
git commit -m "feat(policy): RegisterPolicyCommand 에 신청기간 메타 전파"
```

---

## Phase 5 — 첨부 비동기 보강

### Task 13: periodBackfillExecutor + PolicyPeriodUpdated 이벤트

**Files:**
- Modify: `backend/src/main/java/com/youthfit/ingestion/infrastructure/config/AttachmentAsyncConfig.java`
- Create: `backend/src/main/java/com/youthfit/common/event/PolicyPeriodUpdated.java`

- [ ] **Step 1: PolicyPeriodUpdated 이벤트 작성**

```java
package com.youthfit.common.event;

/**
 * 보강(PeriodBackfillService)으로 정책의 신청기간이 갱신되었을 때 발행.
 * 구독자: (현재 없음 — 향후 캐시 무효화, 알림 등 확장)
 */
public record PolicyPeriodUpdated(Long policyId) {}
```

- [ ] **Step 2: AttachmentAsyncConfig 에 periodBackfillExecutor Bean 추가**

```java
@Bean(name = "periodBackfillExecutor")
public Executor periodBackfillExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("period-bf-");
    executor.initialize();
    return executor;
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/event/PolicyPeriodUpdated.java \
        backend/src/main/java/com/youthfit/ingestion/infrastructure/config/AttachmentAsyncConfig.java
git commit -m "feat(ingestion): PolicyPeriodUpdated 이벤트 + periodBackfillExecutor"
```

---

### Task 14: PeriodBackfillService — PolicyAttachmentReindexedEvent 구독

**Files:**
- Create: `backend/src/main/java/com/youthfit/ingestion/application/service/PeriodBackfillService.java`
- Test: `backend/src/test/java/com/youthfit/ingestion/application/service/PeriodBackfillServiceTest.java`

- [ ] **Step 1: 동작 테스트 작성**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyPeriodUpdated;
import com.youthfit.ingestion.domain.model.PeriodCandidate;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PeriodBackfillService")
class PeriodBackfillServiceTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyAttachmentRepository attachmentRepository = mock(PolicyAttachmentRepository.class);
    private final PeriodResolver resolver = mock(PeriodResolver.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final PeriodBackfillService service =
            new PeriodBackfillService(policyRepository, attachmentRepository, resolver, publisher);

    @Test
    @DisplayName("기존 confidence ≥ 0.70 이면 보강 안 함")
    void skipsWhenHighConfidence() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(0.85);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verifyNoInteractions(resolver);
        verify(publisher, never()).publishEvent(any(PolicyPeriodUpdated.class));
    }

    @Test
    @DisplayName("기존 NULL + 새 결과 > 0.55 → 업데이트 + 이벤트 발행")
    void updatesWhenBetter() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(null);
        when(p.getId()).thenReturn(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of(
                attachmentWithText("신청기간 2026.3.1 ~ 2026.4.30")));
        when(resolver.resolve(any())).thenReturn(new ResolvedPeriod(
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
                PeriodSource.ATTACHMENT_LABELED, 0.75, "..."));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p).updateApplyPeriod(
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
                PeriodSource.ATTACHMENT_LABELED, 0.75, "...");
        verify(publisher).publishEvent(any(PolicyPeriodUpdated.class));
    }

    @Test
    @DisplayName("새 confidence 가 기존 + 0.05 마진을 못 넘으면 업데이트 안 함")
    void skipsBelowMargin() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(0.60);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(resolver.resolve(any())).thenReturn(new ResolvedPeriod(
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
                PeriodSource.ATTACHMENT_LABELED, 0.62, "..."));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p, never()).updateApplyPeriod(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("새 결과가 empty 면 덮어쓰지 않는다")
    void skipsWhenEmpty() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(resolver.resolve(any())).thenReturn(ResolvedPeriod.empty());

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p, never()).updateApplyPeriod(any(), any(), any(), any(), any());
        verifyNoInteractions(publisher);
    }

    private PolicyAttachment attachmentWithText(String text) {
        PolicyAttachment a = mock(PolicyAttachment.class);
        when(a.getExtractedText()).thenReturn(text);
        return a;
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.application.service.PeriodBackfillServiceTest`
Expected: FAIL

- [ ] **Step 3: PeriodBackfillService 구현**

```java
package com.youthfit.ingestion.application.service;

import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyPeriodUpdated;
import com.youthfit.ingestion.application.port.PeriodExtractionContext;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PeriodBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PeriodBackfillService.class);
    private static final double BACKFILL_THRESHOLD = 0.70;
    private static final double OVERWRITE_MARGIN = 0.05;

    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final PeriodResolver periodResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Async("periodBackfillExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttachmentsReindexed(PolicyAttachmentReindexedEvent event) {
        Optional<Policy> policyOpt = policyRepository.findById(event.policyId());
        if (policyOpt.isEmpty()) return;
        Policy policy = policyOpt.get();

        Double current = policy.getApplyPeriodConfidence();
        if (current != null && current >= BACKFILL_THRESHOLD) {
            log.debug("period-backfill skipped: policyId={} confidence={}", event.policyId(), current);
            return;
        }

        List<String> attachmentTexts = attachmentRepository.findExtractedByPolicyId(event.policyId()).stream()
                .map(PolicyAttachment::getExtractedText)
                .filter(t -> t != null && !t.isBlank())
                .toList();

        ResolvedPeriod result = periodResolver.resolve(
                PeriodExtractionContext.forBackfill(policy, attachmentTexts));

        if (!shouldOverwrite(current, result)) {
            log.info("period-backfill no improvement: policyId={} current={} new={}",
                    event.policyId(), current, result.confidence());
            return;
        }

        policy.updateApplyPeriod(result.start(), result.end(),
                result.source(), result.confidence(), result.evidence());
        log.info("period-backfill updated: policyId={} source={} confidence={}",
                event.policyId(), result.source(), result.confidence());
        eventPublisher.publishEvent(new PolicyPeriodUpdated(policy.getId()));
    }

    private boolean shouldOverwrite(Double current, ResolvedPeriod r) {
        if (r.isEmpty()) return false;
        if (current == null) return true;
        return r.confidence() > current + OVERWRITE_MARGIN;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests com.youthfit.ingestion.application.service.PeriodBackfillServiceTest`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/ingestion/application/service/PeriodBackfillService.java \
        backend/src/test/java/com/youthfit/ingestion/application/service/PeriodBackfillServiceTest.java
git commit -m "feat(ingestion): PeriodBackfillService 추가 (첨부 추출 후 신청기간 보강)"
```

---

## Phase 6 — 운영 메타 (IngestionRunLog JSONB)

### Task 15: IngestionRunLog 에 period_resolve_meta JSONB 컬럼

**Files:**
- Create: `backend/src/main/resources/sql/2026-05-21-ingestion-run-log-period-meta.sql`
- Modify: `backend/src/main/java/com/youthfit/ingestion/domain/model/IngestionRunLog.java`
- Modify: `backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java`

- [ ] **Step 1: SQL 스크립트**

```sql
-- 2026-05-21-ingestion-run-log-period-meta.sql
ALTER TABLE ingestion_run_log
    ADD COLUMN period_resolve_meta JSONB;
```

- [ ] **Step 2: IngestionRunLog 엔티티에 컬럼 + factory 확장**

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// ... 기존 필드들 ...

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "period_resolve_meta", columnDefinition = "jsonb")
private String periodResolveMeta;

public static IngestionRunLog success(String source, Instant start, Instant end,
                                      boolean isDuplicate, String periodResolveMeta) {
    IngestionRunLog log = success(source, start, end, isDuplicate);
    log.periodResolveMeta = periodResolveMeta;
    return log;
}
```

- [ ] **Step 3: IngestionService.finally 블록에서 메타 직렬화 + 적재**

receivePolicy 시작 부분에 `ResolvedPeriod resolvedPeriod` 변수를 try 바깥에서 선언, 안에서 할당:
```java
Instant runStart = Instant.now();
String sourceLabel = resolveSourceLabel(command);
boolean failed = false;
boolean duplicate = false;
ResolvedPeriod resolvedPeriod = ResolvedPeriod.empty();

try {
    // ...
    resolvedPeriod = resolvePeriod(command);
    // ...
}
// ...
finally {
    Instant runEnd = Instant.now();
    String meta = serializeResolveMeta(resolvedPeriod);
    IngestionRunLog runLog = failed
            ? IngestionRunLog.failure(sourceLabel, runStart, runEnd)
            : IngestionRunLog.success(sourceLabel, runStart, runEnd, duplicate, meta);
    // ...
}
```

직렬화 헬퍼:
```java
private String serializeResolveMeta(ResolvedPeriod r) {
    if (r == null || r.isEmpty()) {
        return "{\"source\":null,\"confidence\":null}";
    }
    try {
        return objectMapper.writeValueAsString(Map.of(
                "source", r.source().name(),
                "confidence", r.confidence(),
                "evidence", r.evidence() == null ? "" : r.evidence()
        ));
    } catch (Exception e) {
        return "{\"source\":\"" + r.source() + "\"}";
    }
}
```

- [ ] **Step 4: 전체 빌드 + 테스트**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/sql/2026-05-21-ingestion-run-log-period-meta.sql \
        backend/src/main/java/com/youthfit/ingestion/domain/model/IngestionRunLog.java \
        backend/src/main/java/com/youthfit/ingestion/application/service/IngestionService.java
git commit -m "feat(ingestion): IngestionRunLog period_resolve_meta JSONB 추가"
```

---

## 자가 검증 (전체 빌드 + 테스트)

- [ ] **Step 1: 전체 테스트**

Run: `cd backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL — 모든 테스트 통과

- [ ] **Step 2: 회귀 케이스 수동 확인**

Run: `cd backend && ./gradlew test --tests "com.youthfit.ingestion.*" --info`
- 본문 라벨링 정상 케이스: BODY_LABELED + FULL_RANGE → confidence 0.85, source 정확
- 사업기간만 있는 본문: empty 반환
- 사업기간 + 신청기간 같이: 신청기간 선택, confidence 0.85
- n8n + 본문 일치: confidence 0.90 (보너스)

- [ ] **Step 3: 운영 절차 메모**

다음 시점에 운영 DB 적용 필요 — 배포 절차의 일부로 진행:
- `2026-05-21-policy-period-meta.sql`
- `2026-05-21-ingestion-run-log-period-meta.sql`

---

## 작업 완료 후 Follow-up (별도 스펙)

- admin 대시보드 "신청기간 추출 품질" 카드 — IngestionRunLog.period_resolve_meta 집계
- NULL 신뢰도 정책 일괄 백필 배치 — 운영 배치 형태
- "기간 미확정" 정책 UX 노출 (frontend 스펙)
- 다회차 정책 회차별 모델링 (현 스펙은 첫 회차만)
- 회귀 데이터셋 + 자동 평가 파이프라인
