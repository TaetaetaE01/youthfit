# 정책 신청 기간 추출 정확도 개선 — 설계 문서

- **작성일**: 2026-05-21
- **상태**: Ready for review — 섹션 A·B·C 합의 완료, 사용자 최종 리뷰 대기
- **대상 모듈**: `backend/src/main/java/com/youthfit/ingestion`
- **관련 코드**:
  - `IngestionService.resolvePeriod()` — 현재 추출 진입점
  - `PolicyPeriodExtractor` — 정규식 기반 본문 추출기
  - `OpenAiPolicyPeriodExtractor` — LLM 폴백 (CostGuard로 차단 가능)
  - `AttachmentExtractionScheduler` / `AttachmentReindexService` — 첨부 추출 파이프라인 (현재 신청기간 추출과 미연동)

## 1. 배경 & 문제 정의

ingestion 단계에서 본문에 신청 기간이 **명시되어 있음에도** 정책 도메인의 `applyStart`/`applyEnd` 가 비어 있는 사례가 다수 발생한다. 원인은 단일 정규식 + 단일 LLM 폴백이라는 보수적 구조이며, 다음 패턴들이 전부 누락된다.

### 1.1 현재 흐름 (`IngestionService.resolvePeriod`)

```java
if (command.applyStart != null || command.applyEnd != null) {
    return PolicyPeriod.of(applyStart, applyEnd);   // n8n 값 무조건 신뢰
}
PolicyPeriod regex = policyPeriodExtractor.extract(command.body());
if (!regex.isEmpty()) return regex;                  // 정규식 1개만 시도
if (costGuard.enabled()) return PolicyPeriod.empty();// 비용 가드 시 종료
return policyPeriodLlmProvider.extractPeriod(...);   // 최종 LLM 폴백
```

### 1.2 현재 정규식 (`PolicyPeriodExtractor`)

```
(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})\s*[일]?\.?
   \s*[~〜∼\-]\s*
(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})\s*[일]?\.?
```

→ `YYYY[.-/]MM[.-/]DD ~ YYYY[.-/]MM[.-/]DD` 한 가지 형태만 인식.

### 1.3 누락 패턴 목록

| # | 패턴 | 예시 | 누락 원인 |
|---|---|---|---|
| 1 | 시작 연도 생략 | `2026.3.1 ~ 4.30` | 종료에 연도가 없으면 매치 실패 |
| 2 | 종료에서 년/월 생략 | `2026.3.1 ~ 31` | 동월 단축형 미지원 |
| 3 | 단일 마감일 | `신청 마감: 2026.6.30까지` | `~` 가 없으면 매치 실패 |
| 4 | 요일/시간 끼어듦 | `2026.03.01(월) 09:00 ~ 04.30(금) 18:00` | 사이 토큰 미허용 |
| 5 | 자연어 | `2026년 6월 한 달간` | 숫자 범위 외 미지원 |
| 6 | **라벨 오인** | `사업기간 2025.1.1~12.31, 신청기간 2026.3.1~4.30` | 첫 매치(`사업기간`)를 신청기간으로 채택 |
| 7 | 첨부에만 있음 | HWP/PDF 첨부 내부에만 명시 | 첨부 텍스트는 prompt/regex 입력에 미포함 |
| 8 | 다회차 | `1차: 3.1~3.15, 2차: 4.1~4.15` | 첫 회차만 채택, 회차 메타 손실 |

### 1.4 LLM 폴백의 한계

- `OpenAiPolicyPeriodProperties.maxBodyChars` 로 본문이 truncate → 후반부 신청기간 미발견
- `CostGuard.enabled()` 활성화 시 호출 자체 차단 → 정규식이 못 잡으면 손실 확정
- 사업기간 vs 신청기간 혼동 시 LLM도 잘못된 후보를 골라줄 수 있음
- 첨부 텍스트는 prompt 입력에 포함되지 않음

### 1.5 본문 구조 관찰 (n8n 워크플로우 기반)

| 소스 | applyStart/End 채워짐 | 본문 라벨 형태 | 위험 |
|---|---|---|---|
| `youth-center-seoul.json` | 대부분 채워짐 (`aplyYmd`) | `[개요] [지원대상] [선정기준] [지원내용] [사업기간] [기타]` 섹션 헤더 | **본문에 `[사업기간]` 섹션이 명시적으로 들어옴** → 사업기간을 신청기간으로 오인 가능 |
| `youth-seoul-crawl.json` | 대부분 채워짐 (`사업신청기간` th 파싱) | 평문 (`사업개요:`, `지원대상:` …) | 라벨 일치 시 신뢰 가능 |
| `bokjiro-central-welfare.json` | **항상 null** (XML 필드 부재) | `[개요] [지원대상] [선정기준] [지원내용]` | 신청기간이 본문/첨부에만 있음. 첨부 보강이 결정적 |

## 2. 목표 & 비-목표

### 2.1 목표

- 본문 또는 첨부에 신청기간이 명시된 정책에서 누락률을 의미 있게 낮춘다.
- 잘못된 기간(사업기간 등)을 신청기간으로 채택하는 오류율을 낮춘다.
- LLM 호출은 **모호한 경우에만** 사용해 비용 폭증을 피한다.
- 추출 근거(출처/신뢰도/스니펫)를 운영 단계에서 확인할 수 있게 한다.

### 2.2 비-목표

- ingestion 외 경로(수동 입력, 관리자 편집)의 기간 결정 — 본 설계 범위 외
- 다회차 정책의 회차별 메타 모델링 — 별도 스펙으로 분리 (현재는 가장 임박한 1회차만 채택)
- 정책 도메인 모델의 신청기간 컬럼 구조 변경 — `applyStart`/`applyEnd` 그대로 사용
- 회귀 데이터셋 자동 평가 파이프라인 — 본 설계는 알고리즘에 집중, 평가는 후속 작업
- "기간 미확정" 정책의 UX 노출 (리스트 뱃지·상세 안내) — 별도 frontend 스펙

## 3. 설계 결정 요약 (사용자 합의)

| 영역 | 결정 |
|---|---|
| LLM 사용 강도 | **하이브리드** — 정규식·라벨 후보를 만들고, 모호할 때만 LLM disambiguation 호출 |
| n8n 값 신뢰 정책 | **교차 검증 후 신뢰도 높은 것 채택** — n8n 도 다른 소스와 동일하게 점수 비교 |
| 첨부 활용 시점 | **비동기 후처리 보강** — 등록 시점엔 본문만, 첨부 추출 완료 이벤트에서 재시도 |
| 라벨 정책 | **양성 + 명시적 네거티브 사전** — `[사업기간]` 등 네거티브 윈도우는 후보 수집 제외 + 본문 마스킹 |
| 정규식 범위 | **보수적 + 자연어는 LLM 위임** — 완전 범위, 연도 상속, 동월 단축형, 단일 마감일/시작일, 요일·시간 끼어듦까지 정규식 |
| LLM 호출 시나리오 | **두 경로** — (a) 후보 0개 → 본문 직접 추출, (b) 후보 ≥ 2 + 모호 → disambiguator |
| 보강 트리거 | **`PolicyAttachmentsExtracted` ApplicationEvent** — reindex 직후 발행 |
| 보강 대상 필터 | **`applyPeriodConfidence < 0.70` 또는 NULL** |
| 메타 저장 | **Policy 엔티티 컬럼 추가** (source / confidence / evidence) |
| 덮어쓰기 정책 | **새 confidence > 기존 + 0.05 마진** (요동 방지) |
| 운영 가시성 | **구조화 로그 + IngestionRunLog 메타 컬럼** — admin 대시보드는 Follow-up |

## 4. 섹션 A — 아키텍처: 후보 수집 + 점수화 + 선택

### 4.1 큰 그림

기존 `IngestionService.resolvePeriod()` 의 분기 로직을 **`PeriodResolver`** 도메인 서비스로 분리한다. 각 추출 경로는 **`PeriodCandidateSource`** 포트의 구현체로 나뉘고, 결과는 신뢰도 점수가 붙은 **`PeriodCandidate`** 값 객체로 통일된다.

```
IngestionService.receivePolicy()
    └─> PeriodResolver.resolve(ctx)
            ├─ 후보 수집 (모든 소스)
            │     ├─ N8nApplyFieldsSource    (command.applyStart / applyEnd)
            │     ├─ BodyLabeledRegexSource  (라벨 윈도우 안 + 네거티브 마스킹 후)
            │     ├─ BodyGenericRegexSource  (네거티브 윈도우 마스킹 후, 본문 전체)
            │     └─ AttachmentLabeledRegexSource (보강 흐름에서만 — 섹션 C)
            │
            ├─ 후보 0개 AND !CostGuard → LlmDirectExtractor (경로 ①, 섹션 B)
            │
            ├─ 동일 (start, end) 후보 병합
            │     confidence = max(group) + 0.05 × (group.size − 1)   (최대 +0.15)
            │
            ├─ 최고 점수 < 0.70 AND 후보 그룹 ≥ 2 AND !CostGuard.enabled()
            │     → LlmDisambiguator.choose(snippets, candidates)   (경로 ②)
            │
            └─ 최종 ResolvedPeriod { start, end, source, confidence, evidence }
                    confidence < 0.55 면 empty
```

### 4.2 도메인 모델

```java
package com.youthfit.ingestion.domain.model;

public record PeriodCandidate(
    LocalDate start,
    LocalDate end,
    PeriodSource source,        // N8N, BODY_LABELED, BODY_GENERIC, ATTACHMENT_LABELED, LLM_DIRECT, LLM_DISAMBIGUATED
    double confidence,          // 0.0 ~ 1.0
    String evidence             // 매치 스니펫 (최대 200자, Policy 컬럼 길이와 일치)
) {}

public enum PeriodSource {
    N8N,
    BODY_LABELED,
    BODY_GENERIC,
    ATTACHMENT_LABELED,
    LLM_DIRECT,
    LLM_DISAMBIGUATED
}

public record ResolvedPeriod(
    LocalDate start,
    LocalDate end,
    PeriodSource source,
    double confidence,
    String evidence
) {
    public static ResolvedPeriod empty() { /* ... */ }
    public boolean isEmpty() { return start == null && end == null; }
}
```

### 4.3 포트 인터페이스

```java
package com.youthfit.ingestion.application.port;

public interface PeriodCandidateSource {
    List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx);
}

public interface PeriodLlmDirectExtractor {
    Optional<PeriodCandidate> extract(String title, String body);
}

public interface PeriodLlmDisambiguator {
    Optional<PeriodCandidate> choose(String snippets, List<PeriodCandidate> candidates);
}

public record PeriodExtractionContext(
    String title,
    String body,
    LocalDate n8nApplyStart,
    LocalDate n8nApplyEnd,
    String externalId,
    List<String> attachmentTexts   // 등록 시점엔 null/empty, 보강 시점에 채워짐
) {
    public static PeriodExtractionContext forIngest(IngestPolicyCommand c) { ... }

    /**
     * 보강 시점 컨텍스트.
     * 기존 policy.applyStart/End 는 n8nApplyStart/End 슬롯에 매핑되어
     * N8nApplyFieldsSource 가 재평가 가능하게 한다.
     * (보강 흐름의 목적은 "더 좋은 결과 찾기" 이므로, 기존 값도 동일한 점수표에 따라
     * 재경쟁 — 다중 소스 보너스로 자연스럽게 합산됨)
     */
    public static PeriodExtractionContext forBackfill(Policy p, List<String> texts) { ... }
}
```

### 4.4 점수표

| 케이스 | confidence |
|---|---|
| N8N — 양쪽 모두 존재 | 0.60 |
| N8N — 한쪽만 존재 | 0.40 |
| BODY_LABELED 양성 + 완전 범위 (FULL_RANGE) | **0.85** |
| BODY_LABELED 양성 + 연도 상속 / 동월 단축형 | 0.80 |
| BODY_LABELED 양성 + 단일 마감일/시작일 | 0.65 |
| BODY_GENERIC 완전 범위 (네거티브 마스킹 후) | 0.45 |
| BODY_GENERIC 단일 마감일/시작일 | 0.35 |
| ATTACHMENT_LABELED 양성 + 완전 범위 | 0.75 |
| ATTACHMENT_LABELED 양성 + 단일 마감일/시작일 | 0.55 |
| LLM_DIRECT | 0.60 |
| LLM_DISAMBIGUATED | LLM 반환값 그대로 (0.0~1.0) |
| 다중 소스 일치 보너스 | +0.05 × (그룹 크기 − 1), 최대 +0.15 |
| 최종 < 0.55 → `ResolvedPeriod.empty()` | — |

### 4.5 선택 알고리즘

1. 모든 `PeriodCandidateSource` 에서 후보 리스트 수집
2. **후보가 0개** AND `!costGuard.enabled()` → `PeriodLlmDirectExtractor.extract()` 호출, 결과를 `LLM_DIRECT` 후보로 합류
3. `(start, end)` 동일 후보를 그룹화 → 보너스 가산
4. 그룹 중 최고 confidence 후보 1차 선택
5. **모호 분기**: 최고 confidence < 0.70 AND 후보 그룹 ≥ 2 AND `!costGuard.enabled()` → `PeriodLlmDisambiguator.choose()` 호출, 반환값으로 교체
6. 최종 confidence < **0.55** → `ResolvedPeriod.empty()`

### 4.6 왜 이 구조인가

- **단일 책임**: 각 소스가 자신의 패턴만 책임. 새 패턴 추가 시 새 구현체만 등록
- **비용 방어 + 정확도**: LLM 은 항상이 아니라 두 경로(0개, ≥2 모호)에서만 — CostGuard 환경에선 정규식·라벨만으로 동작
- **교차 검증**: 다중 소스가 같은 답을 내면 confidence 자동 상승 → n8n 값(0.60)이 라벨링된 본문(0.85)에 의해 자연스럽게 밀림
- **가시성**: 후보·점수·근거가 데이터로 남아 운영 단계 분석 가능

## 5. 섹션 B — 정규식 확장 & 라벨 윈도우 & LLM 프롬프트

### 5.1 라벨 사전

```java
public final class PeriodLabels {
    // 양성: 라벨 윈도우 → BodyLabeledRegexSource 후보 수집 대상
    public static final List<String> POSITIVE = List.of(
        "신청기간", "신청 기간", "접수기간", "접수 기간",
        "모집기간", "모집 기간", "공모기간", "공모 기간",
        "사업신청기간", "사업 신청 기간", "신청일정", "신청 일정"
    );
    // 네거티브: 매치되면 윈도우 추출 후 후보 수집에서 제외 + 본문 마스킹
    public static final List<String> NEGATIVE = List.of(
        "사업기간", "사업 기간", "운영기간", "운영 기간",
        "수행기간", "수행 기간", "교육기간", "교육 기간",
        "지원기간", "지원 기간"
    );
}
```

- "지원기간" 은 보수적으로 네거티브 (지원사업 기간 의미가 많음 — 누락 위험보다 오추출 위험이 큼)
- 라벨 매칭은 공백/콜론 무시 (`신청\s*기간\s*[:：]?`)

### 5.2 윈도우 추출 알고리즘

```
입력: body
출력: List<LabelWindow> { label, content, isNegative, startOffset, endOffset }

1. body 를 POSITIVE + NEGATIVE 패턴으로 단일 스캔 (위치 기준 병합)
2. 각 매치마다 윈도우 컨텐츠 추출:
   - 시작: 라벨 끝 + 후속 콜론/공백 스킵
   - 종료: min(
       다음 라벨 매치 시작 위치,
       이중 개행(\n\s*\n) 위치,
       시작 + 200자
     )
3. NEGATIVE 윈도우는 BodyLabeledRegexSource 후보에서 제외
4. NEGATIVE 윈도우의 [startOffset, endOffset] 구간을 본문에서 마스킹한 다음
   BodyGenericRegexSource 가 스캔 → 사업기간이 GENERIC 후보로 들어오는 것을 원천 차단
```

### 5.3 정규식 패턴 셋

라벨 윈도우 안 + 본문 마스킹 후 둘 다 적용.

```java
// 공통 토큰
SEP    = [.\-/]\s*|\s*년\s*|\s*월\s*
Y4     = (20\d{2})
M      = (\d{1,2})
D      = (\d{1,2})
TAIL   = (?:일\.?|\s*\([월화수목금토일]\)|\s*\d{1,2}:\d{2}|\s*오[전후])
ARROW  = \s*[~〜∼\-]\s*

// 1) 완전 범위 (기존)
FULL_RANGE = Y4 SEP M SEP D TAIL? ARROW Y4 SEP M SEP D TAIL?

// 2) 연도 상속: 2026.3.1 ~ 4.30
YEAR_INHERIT = Y4 SEP M SEP D TAIL? ARROW M SEP D TAIL?

// 3) 동월 단축형: 2026.3.1 ~ 31
SAME_MONTH = Y4 SEP M SEP D TAIL? ARROW D (?:일\.?)?

// 4) 단일 마감일: ~ 2026.6.30 까지 / 마감 / 이내
DEADLINE_ONLY = (?:[~〜∼\-]\s*|마감(?:일)?\s*[:：]?\s*)
                Y4 SEP M SEP D \s*(?:까지|마감|이내)?
                → start=null, end=parsed

// 5) 단일 시작일: 2026.6.1 부터
START_ONLY = Y4 SEP M SEP D \s*부터
             → start=parsed, end=null
```

요일/시간(TAIL) 토큰을 패턴 안에 흡수 → `2026.03.01(월) 09:00 ~ 04.30(금) 18:00` 같은 케이스도 매치.

자연어 패턴(매년 3월~4월, 한 달간, 공고일로부터 N일 이내 등)은 정규식 미지원 — LLM 경로에 위임.

### 5.4 LLM 사용 두 경로

| 경로 | 트리거 조건 | 입력 | 출력 | confidence |
|---|---|---|---|---|
| **① 직접 추출** | 후보 0개 AND `!CostGuard` | 본문 truncate (`maxBodyChars`) | `{applyStart, applyEnd}` | **0.60** (`LLM_DIRECT`) |
| **② Disambiguator** | 후보 ≥ 2 AND 최고점 < 0.70 AND `!CostGuard` | 후보별 evidence 윈도우 + 후보 JSON | `{chosenId, confidence, reasoning}` | LLM 반환값 (`LLM_DISAMBIGUATED`) |

- 후보 1개 + confidence ≥ 0.70 → LLM 미호출 (그대로 채택)
- 후보 1개 + confidence < 0.70 → LLM 미호출, empty 처리 (다음 단계는 첨부 보강에 위임)
- CostGuard 활성 환경 → 두 경로 모두 차단

기존 `OpenAiPolicyPeriodExtractor` 는 **경로 ① 의 구현체로 흡수** (인터페이스만 `PeriodLlmDirectExtractor` 로 변경, 프롬프트는 네거티브 라벨 명시 추가).

### 5.5 LLM 프롬프트

**경로 ① — 직접 추출**
```
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
```

**경로 ② — Disambiguator**
```
당신은 정책 본문에서 추출된 여러 신청기간 후보 중 정답을 선택하는 검수자입니다.
본문 스니펫과 후보 목록을 보고, 어느 후보가 진짜 신청기간인지 ID로 응답하세요.

반드시 아래 JSON 스키마로만 응답하세요.
{"chosenId": <정수|null>, "confidence": <0.0-1.0>, "reasoning": "<한 줄>"}

규칙:
- "사업기간", "운영기간", "수행기간"으로 보이는 후보는 무시합니다.
- 정답 후보가 없다면 chosenId = null.
- confidence 는 본문 근거가 명확할수록 높게.
- JSON 외의 텍스트를 출력하지 마세요.
```

### 5.6 본문 truncate / 토큰 절약

- **경로 ①**: 기존 `OpenAiPolicyPeriodProperties.maxBodyChars` 그대로
- **경로 ②**: 후보별 evidence 윈도우만 결합 (각 200자, 최대 5개 후보 → 약 1KB + 후보 JSON ~500B) → 입력 토큰 약 75% 절감 (기존 경로 ① 대비)

## 6. 섹션 C — 첨부 비동기 보강 & 운영 메타

### 6.1 첨부 완료 이벤트

기존 `AttachmentExtractionScheduler.runCycle()` 의 정책별 완료 체크 블록에 이벤트 발행 추가.

```java
// AttachmentExtractionScheduler.runCycle() 내부
for (Long policyId : reindexCandidates) {
    if (repository.isAllTerminalForPolicy(policyId)) {
        try {
            reindexService.reindex(policyId);
            eventPublisher.publishEvent(new PolicyAttachmentsExtracted(policyId));
        } catch (Exception e) { ... }
    }
}
```

- 이벤트: `com.youthfit.common.event.PolicyAttachmentsExtracted(Long policyId)` — 기존 `PolicyUpsertedEvent` 옆에 추가
- 발행 위치: reindex 성공 직후 (reindex 실패 시 미발행)

### 6.2 PeriodBackfillService

```java
package com.youthfit.ingestion.application.service;

@Service
@RequiredArgsConstructor
public class PeriodBackfillService {

    private static final double BACKFILL_THRESHOLD = 0.70;
    private static final double OVERWRITE_MARGIN = 0.05;

    private final PolicyRepository policyRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final PeriodResolver periodResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Async("periodBackfillExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onAttachmentsExtracted(PolicyAttachmentsExtracted event) {
        Policy policy = policyRepository.findById(event.policyId()).orElse(null);
        if (policy == null) return;

        // 1) 보강 대상 필터 — 저 신뢰도만
        Double currentConfidence = policy.getApplyPeriodConfidence();
        if (currentConfidence != null && currentConfidence >= BACKFILL_THRESHOLD) {
            return;
        }

        // 2) 첨부 텍스트 수집
        List<String> attachmentTexts = attachmentRepository
                .findExtractedTextsByPolicyId(event.policyId());

        // 3) 본문 + 첨부 통합 → PeriodResolver 재호출
        ResolvedPeriod newResult = periodResolver.resolve(
                PeriodExtractionContext.forBackfill(policy, attachmentTexts));

        // 4) 새 결과가 의미 있게 더 좋을 때만 업데이트
        if (shouldOverwrite(policy, newResult)) {
            policy.updateApplyPeriod(newResult);
            eventPublisher.publishEvent(new PolicyPeriodUpdated(policy.getId()));
        }
    }

    private boolean shouldOverwrite(Policy p, ResolvedPeriod r) {
        if (r.isEmpty()) return false;
        Double current = p.getApplyPeriodConfidence();
        return current == null || r.confidence() > current + OVERWRITE_MARGIN;
    }
}
```

- `@Async("periodBackfillExecutor")` — 다운로드 풀과 분리한 별도 executor
- `@TransactionalEventListener(AFTER_COMMIT)` — reindex 트랜잭션 커밋 이후 동작
- `BACKFILL_THRESHOLD = 0.70` 이상은 신뢰, 미만 또는 NULL 이면 재시도
- `OVERWRITE_MARGIN = 0.05` — 새 결과가 살짝만 더 높으면 흔들리지 않도록

### 6.3 AttachmentLabeledRegexSource

섹션 B 의 `BodyLabeledRegexSource` 와 같은 알고리즘을 첨부 텍스트에 적용.

```java
@Component
@RequiredArgsConstructor
public class AttachmentLabeledRegexSource implements PeriodCandidateSource {

    private final LabeledRegexExtractor labeledExtractor;  // 공용 추출기

    @Override
    public List<PeriodCandidate> findCandidates(PeriodExtractionContext ctx) {
        if (ctx.attachmentTexts() == null || ctx.attachmentTexts().isEmpty()) {
            return List.of();
        }
        return ctx.attachmentTexts().stream()
                .flatMap(text -> labeledExtractor
                        .candidates(text, PeriodSource.ATTACHMENT_LABELED)
                        .stream())
                .toList();
    }
}
```

- confidence 기본값은 본문보다 0.10 낮음 (본문이 1차 소스)
- 같은 (start, end) 가 여러 첨부에서 나오면 섹션 A 의 다중 소스 보너스가 자연스럽게 적용됨
- 등록 시점 흐름에선 `ctx.attachmentTexts()` 가 빈 리스트 → 자동으로 skip

### 6.4 Policy 엔티티 컬럼 추가

```java
// Policy.java
@Column(name = "apply_period_source", length = 32)
@Enumerated(EnumType.STRING)
private PeriodSource applyPeriodSource;        // nullable

@Column(name = "apply_period_confidence")
private Double applyPeriodConfidence;          // nullable, 0.0~1.0

@Column(name = "apply_period_evidence", length = 200)
private String applyPeriodEvidence;            // nullable, 운영용 디버깅 스니펫

public void updateApplyPeriod(ResolvedPeriod r) {
    this.applyStart = r.start();
    this.applyEnd = r.end();
    this.applyPeriodSource = r.source();
    this.applyPeriodConfidence = r.confidence();
    this.applyPeriodEvidence = r.evidence();
}
```

마이그레이션 (Flyway):
```sql
ALTER TABLE policy
    ADD COLUMN apply_period_source     VARCHAR(32),
    ADD COLUMN apply_period_confidence DOUBLE PRECISION,
    ADD COLUMN apply_period_evidence   VARCHAR(200);
```

- 전부 nullable, 기존 행은 NULL — 점진적으로 보강
- API 응답(`PolicyDetailResponse` 등)에는 노출하지 않음 (운영 전용)

### 6.5 운영 가시성

본 스펙 범위 내:

1. **구조화 로그** — `PeriodResolver.resolve()` 가 결정 시 한 줄 INFO 로그
   ```
   period-resolve policyId=12345 source=BODY_LABELED confidence=0.85
   start=2026-03-01 end=2026-04-30 evidence="신청기간: 2026.03.01~2026.04.30"
   ```

2. **`IngestionRunLog` 메타 컬럼** — `period_resolve_meta JSONB` 단일 컬럼
   - 스키마: `{ "sourceCounts": {"BODY_LABELED": 12, "N8N": 30, ...}, "avgConfidence": 0.78, "emptyCount": 3 }`
   - run 종료 시점에 해당 run 동안 처리된 정책의 `ResolvedPeriod` 들을 집계
   - JSONB 한 컬럼이면 PeriodSource enum 추가/변경 시 마이그레이션 불필요
   - 노출 UI 는 Follow-up

본 스펙 범위 외 (Follow-up):

- admin 대시보드 카드 "신청기간 추출 품질"
- 일괄 백필 배치 (NULL 인 정책 일괄 재계산)

### 6.6 비용 방어 / Async 분리

- `periodBackfillExecutor` 는 `attachmentDownloadExecutor` 와 별도 풀 — 다운로드/추출 큐 백프레셔와 격리
- CostGuard 활성 환경: backfill 안에서도 `PeriodResolver` 가 LLM 두 경로를 차단 (섹션 5.4) → 정규식·라벨 기반 보강만 수행
- 첨부 텍스트는 PostgreSQL `TEXT` 컬럼 (`PolicyAttachment.extractedText`) — 별도 다운로드 없이 DB 조회

### 6.7 덮어쓰기 정책

```
새 confidence > 기존 confidence + 0.05  →  덮어쓰기 + PolicyPeriodUpdated 이벤트
새 confidence ≤ 기존 confidence + 0.05  →  기존 유지 (요동 방지)
새 결과가 empty                       →  덮어쓰기 안 함 (보강은 보존적)
```

## 7. 마이그레이션 & 호환성

- 신규 컴포넌트만 추가, 기존 `PolicyPeriodExtractor` / `OpenAiPolicyPeriodExtractor` 는 새 소스/포트 구현체로 흡수 후 점진 제거
- `IngestionService.resolvePeriod()` 시그니처는 그대로, 내부에서 `PeriodResolver.resolve()` 호출로 교체
- 기존 등록된 정책의 `applyStart`/`applyEnd` 는 즉시 재계산하지 않음 — 첨부 추출 이벤트가 발생하면 자동 보강 (`apply_period_confidence` NULL 을 "재계산 허용" 으로 해석)
- 새 컬럼 3개 (`apply_period_source/confidence/evidence`) 모두 nullable — 기존 행은 NULL
- 일괄 백필 배치는 Follow-up — 실행 시간·비용 가시화 후 결정

## 8. 테스트 전략

### 8.1 단위 테스트

- 누락 패턴 표 (§1.3) #1–#6 각각에 대한 도메인 테스트 — `PeriodResolverTest`
- 점수 병합 / 모호 분기 / LLM 두 경로 호출 조건 시나리오 — `PeriodResolverTest`
- 각 `PeriodCandidateSource` 구현체별 단위 테스트
  - `BodyLabeledRegexSourceTest` — 양성/네거티브 라벨, 윈도우 자르기
  - `BodyGenericRegexSourceTest` — 네거티브 마스킹 후 스캔
  - `AttachmentLabeledRegexSourceTest` — 여러 첨부 텍스트 결합
  - `N8nApplyFieldsSourceTest` — 양쪽/한쪽
- `PeriodLlmDirectExtractor` / `PeriodLlmDisambiguator` 는 페이크/스텁으로 결정성 확보

### 8.2 통합 시나리오

- `IngestionServiceTest` — 정규식 성공/모호/0개/CostGuard 활성 시나리오
- `PeriodBackfillServiceTest` — 첨부 보강 트리거, 덮어쓰기 정책, 마진
- `AttachmentExtractionSchedulerTest` — `PolicyAttachmentsExtracted` 이벤트 발행 확인

### 8.3 회귀

- 기존 정상 추출 케이스(라벨 일치 + 완전 범위)가 confidence ≥ 0.85 로 동일 결과를 내는지 — `PolicyPeriodExtractorTest` 의 기존 케이스 마이그레이션

## 9. Follow-up (별도 스펙)

- 다회차 정책의 회차별 모델링 (현 스펙은 첫 회차만)
- 회귀 데이터셋 + 자동 평가 파이프라인 (정확/누락/오추출률 메트릭)
- admin 대시보드 "신청기간 추출 품질" 카드
- NULL 신뢰도 정책 일괄 백필 배치 (운영 배치)
- "기간 미확정" 정책의 UX 노출 (리스트 뱃지, 상세 안내) — frontend 스펙
- 외부 참고 URL 본문에서의 기간 추출 (가능성 검토)
