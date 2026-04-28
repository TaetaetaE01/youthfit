# 가이드 환산값 결정적 후처리 (IncomeBracketAnnotator) 설계

> 후속: `docs/superpowers/plans/DONE_2026-04-28-05-income-bracket-postprocess.md` (예정)
> 사이클 번호: `05` (2026-04-28 다섯 번째 사이클 — 네이밍 규칙은 `docs/superpowers/README.md` 참조)

## 1. 목표

PR #48 (`88a65c0`) 에서 도입한 가이드 정확도 강화 작업의 후속이다. LLM(gpt-4o-mini)이 system prompt §9 환산값 표기 규칙을 retry 후에도 안정적으로 따르지 못하는 문제를 **결정적 후처리**로 해결한다.

`중위소득 N%` / `차상위(계층)?` 패턴이 등장하는 가이드 텍스트에 `IncomeBracketReference` (yaml) 의 환산값을 자동 삽입하여, LLM 누락 여부와 무관하게 사용자에게 항상 환산 금액을 제공한다.

## 2. 배경

### 2.1 현재 문제

PR #48 시점 동작:
1. LLM 호출 → `GuideContent` 응답
2. `GuideValidator.checkMissingAmount` 가 paired bullet 안의 `중위소득 N%` / `차상위` 표기에 `\d+\s*만원` 누락 여부 검사
3. 누락 시 1회 `regenerateWithFeedback` 으로 retry
4. retry 후에도 누락 → 1차 응답 그대로 저장

운영 관찰 (backlog `next/2026-04-28-attachment-source-trace-backlog.md` §환산값 섹션):
- `중위소득 50% 이하` 같은 표기는 들어가지만 환산값(예: `약 117만원`)이 LLM 출력에 자주 누락
- retry 트리거되지만 gpt-4o-mini 가 retry 후에도 같은 bullet 안에 환산값을 안 붙임
- 시스템 프롬프트가 길어질수록 instruction following 약화 (v2 환산값 ✓ / PDF 디테일 약함 ↔ v3 PDF 디테일 ✓ / 환산값 약함)

### 2.2 해결 후보 비교 (backlog 4가지)

| 옵션 | 비용 | 안정성 | 결정 |
|---|---|---|---|
| A. 모델 업그레이드 (gpt-4o-mini → gpt-4o) | ~10× | ↑↑ | reject (운영 비용 부담) |
| **B. 결정적 후처리** | 0 | ↑↑↑ | **채택** |
| C. 프롬프트 분해 (1차 + 2차 호출) | 1.x× | ↑ | reject (호출 2회 + 결과 합치기 복잡도) |
| D. 환산값 표기 전용 LLM 호출 | 1.x× | ↑ | reject (C와 유사한 부담) |

### 2.3 B 채택 근거

- 비용 0, 지연 0, 100% 결정적
- `IncomeBracketReference` 가 yaml 결정적 데이터라 환각 위험 0
- LLM 누락이 빈번 / 환각이 드묾 → 검증 후 후처리만으로 충분
- system prompt §9 의 PDF 인용값 우선 원칙은 "이미 만원 표기 있으면 skip" 으로 보존

## 3. 비범위 (의도적 제외)

- **모델 업그레이드 (gpt-4o-mini → gpt-4o)**: 본 사이클이 환산값 문제를 결정적으로 해결하므로 불필요. 다른 정확도 이슈 발생 시 재검토.
- **정책 본문에서 가구원수 명시 추출 → 표기 압축 (1인만 vs 1·2인 모두)**: PR #48이 1·2인 모두 표기로 굳힘. 변경하려면 별도 사이클 (brainstorming Q1 의 C 옵션 재검토).
- **3인 이상 가구 환산**: yaml 미보유. 청년 정책의 1·2인 가정과 일치. 추가는 yaml 만 확장하면 됨.
- **LLM 환산값 정확도 메트릭**: WARN 로그로 부분 visibility 만 확보. 정식 메트릭은 v0.x.
- **첨부 PDF 인용값 vs yaml 값 차이 검출**: system prompt §9(a) "PDF 우선" 원칙 그대로 보존 (skip). 차이 검출은 `attachment-source-trace-backlog.md` 의 후속.
- **PROMPT_VERSION 증분**: 시스템 프롬프트 자체는 변경 없음 (LLM 응답이 어쩌다 잘 나온 케이스를 후처리가 보존하기 위해 §9 규칙은 유지). `ANNOTATOR_VERSION` 으로 sourceHash 무효화.

## 4. 도메인 / 컴포넌트 변경

### 4.1 신규 — `IncomeBracketAnnotator`

```java
// guide/application/service/IncomeBracketAnnotator.java
@Component
@RequiredArgsConstructor
public class IncomeBracketAnnotator {
    /**
     * 가이드 콘텐츠 내 모든 텍스트 필드에서 "중위소득 N%" / "차상위" 패턴을
     * 찾아 환산값을 결정적으로 병기. 이미 같은 텍스트 단위에 만원 표기가 있으면 skip.
     * policyId 는 로깅용 (yaml 미등록 비율 / 빈 reference 케이스 식별).
     */
    public GuideContent annotate(GuideContent content, IncomeBracketReference reference, Long policyId);
}
```

배치 위치: `com.youthfit.guide.application.service`. application service 로 분류 (두 도메인 — guide, policy — 데이터 결합 책임).

순수 함수: 외부 의존 0, I/O 0, 예외 발생 가능성 0. `try/catch` 불필요.

### 4.2 변경 — `GuideGenerationService`

생성자에 `IncomeBracketAnnotator` 주입. `generateGuide()` 흐름 변경:

```
reference = referenceLoader.resolveReference(year)               [기존]
content   = llmProvider.generateGuide(input)                      [기존]
content   = incomeBracketAnnotator.annotate(content, reference)   ← 신규
report    = validator.validate(content, originalText)             [기존, 단 hasMissingAmount 제거됨]
if report.hasRetryTrigger():
    content = llmProvider.regenerateWithFeedback(input, feedback)
    content = incomeBracketAnnotator.annotate(content, reference) ← 신규 (retry 결과에도)
guideRepository.save(...)
```

`computeHash` 입력에 `annotator:v1` 추가:
```java
private static final String ANNOTATOR_VERSION = "v1";
// computeHash 내부: sb.append("annotator:").append(ANNOTATOR_VERSION);
```

### 4.3 변경 — `GuideValidator`

`checkMissingAmount` 와 관련 필드/메시지 제거:
- 삭제: `PERCENT_PATTERN`, `AMOUNT_PATTERN`, `checkMissingAmount()`, `ValidationReport.hasMissingAmount`, "환산 금액 병기" feedback 메시지
- 유지: `checkGroupMix`, `insufficientHighlights`, friendly tone 체크, `findMissingNumericTokens`

`ValidationReport`:
```java
public record ValidationReport(
        boolean hasGroupMixViolation,
        boolean hasInsufficientHighlights,
        List<String> feedbackMessages
) { /* hasMissingAmount 제거 */ }
```

### 4.4 변경 없음

- `IncomeBracketReference` (record): 변경 없음. `findAmount` / `nearPoor` / `formatManwon` 그대로.
- `OpenAiChatClient`: system prompt 변경 없음. PR #48 v3 그대로.
- `IncomeBracketReferenceLoader` / `YamlIncomeBracketReferenceLoader`: 변경 없음.
- yaml 데이터: 변경 없음 (`2025.yaml`, `2026.yaml`).

## 5. 패턴 / 표기 규칙

### 5.1 정규식

```java
// 중위소득 패턴 — 다양한 표현 흡수 + capture group 으로 % 추출
MEDIAN_INCOME_PATTERN = Pattern.compile(
    "(?:기준\\s*)?중위소득(?:의)?\\s*(\\d+)\\s*%(?:\\s*이내|\\s*이하|\\s*까지)?"
);

// 차상위 패턴 — "계층" 유무 모두 허용
NEAR_POOR_PATTERN = Pattern.compile(
    "차상위(?:계층)?(?:\\s*이하|\\s*이내)?"
);

// skip 판정용
EXISTING_AMOUNT_PATTERN = Pattern.compile("\\d+\\s*만원");
```

### 5.2 표기 형식

system prompt §9 표기 예시와 일치시킨다 (사용자 노출 일관성).

| 패턴 | 출력 |
|---|---|
| `중위소득 N% (이하)?` (yaml 등록) | `중위소득 N% 이하 (YYYY년 기준 1인 가구 월 약 X만원, 2인 가구 월 약 Y만원)` |
| `차상위(계층)? (이하)?` | `차상위계층 이하 (YYYY년 기준 1인 가구 월 약 Z만원 이하)` |

- **중위소득**: 1·2인 모두 병기
- **차상위**: 1인만 병기 (system prompt §9 표기 예시 일관)
- **금액 형식**: 만원 단위 반올림 정수 + `만원` (예: `1538543원` → `약 154만원`)
- **삽입 위치**: 매칭된 패턴 **직후**에 괄호 추가
- **연도**: `reference.year()` (정책 referenceYear 매칭 우선, 못 찾으면 PR #48 의 `findLatest` fallback)

### 5.3 Skip 조건

각 텍스트 단위(bullet, oneLineSummary, highlight.text, pitfall.text) 안에:
```
EXISTING_AMOUNT_PATTERN ("\\d+\\s*만원") 이 1개 이상 존재
→ 그 텍스트의 모든 패턴 skip
```

판정 단위는 **bullet/카드 텍스트 단위**. group 단위 X. 같은 group 내 다른 bullet 에 만원 표기가 있어도 현재 bullet 자체에 없으면 후처리 적용.

### 5.4 yaml 미등록 비율

```
"중위소득 75%" 등장 → reference.findAmount(ONE, 75) → Optional.empty()
→ 환산값 미삽입, 텍스트 그대로 보존
→ log.warn("unmapped median income percent: percent={}, year={}, policyId={}, snippet={}")
```

system prompt §9 끝줄 "참고표에 없으면 만들어내지 마라" 와 일관.

### 5.5 같은 텍스트 안 동일 비율 반복

```
"중위소득 60% 이하인 자로서 ... 중위소득 60% 이하 가구"
→ 첫 등장에만 환산값 삽입 (system prompt §9 동일)
→ 두 번째 등장은 패턴 매칭은 되지만 환산값 미삽입
```

구현: **각 텍스트 단위(bullet / oneLineSummary / highlight.text / pitfall.text)** 마다 환산 처리한 비율 `Set<Integer>` 를 새로 만들어 추적. 한 텍스트 단위 내부에서 두 번째 매칭 시 set 확인 후 skip. 다른 텍스트 단위로 넘어가면 set 리셋. (`차상위` 는 `MEDIAN_INCOME_PATTERN` 과 별개 set 으로 추적 — 한 텍스트에 둘 다 등장하면 모두 한 번씩 환산값 삽입).

### 5.6 적용 대상 텍스트 (Q3 B)

`GuideContent` 의 모든 문자열 필드:
- `oneLineSummary`
- `highlights[].text`
- `target / criteria / content . groups[].items[]`
- `pitfalls[].text`

## 6. 데이터 흐름

```
[GenerateGuideCommand]
       │
       ▼
GuideGenerationService.generateGuide()
       │
       ├─ resolveReference(year)
       ├─ computeHash(policy, chunks, ref)  ← annotator:v1 포함 → 캐시 무효
       ├─ if !changed → return "변경 없음"
       │
       ├─ llmProvider.generateGuide(input)  ─→  [GuideContent]
       │
       ├─ incomeBracketAnnotator.annotate(content, ref)  ─→  [GuideContent']  ← 신규
       │       │
       │       ├─ for each text field:
       │       │   if EXISTING_AMOUNT_PATTERN.find() → skip
       │       │   else for each pattern match:
       │       │       insert formatted reference value
       │       └─ return new GuideContent (immutable)
       │
       ├─ validator.validate(content, originalText)  ← checkMissingAmount 제거
       │
       ├─ if report.hasRetryTrigger():
       │     llmProvider.regenerateWithFeedback(input, feedback)
       │     incomeBracketAnnotator.annotate(...)  ← 신규 (retry 결과에도)
       │
       └─ guideRepository.save(...)
```

## 7. 캐시 무효화

`computeHash` 입력 변경 (`annotator:v1` 추가) → 모든 기존 가이드의 `sourceHash` 변경 → 다음 generateGuide 호출 시 `Guide.hasChanged()` true → 자동 재생성. **수동 backfill 불필요.**

## 8. 에러 처리 / 로깅

| 상황 | 레벨 | 메시지 형식 |
|---|---|---|
| 환산값 N개 삽입 완료 | DEBUG | `annotated income brackets: policyId={}, inserted={}` |
| yaml 미등록 비율 발견 | WARN | `unmapped median income percent: percent={}, year={}, policyId={}, snippet={}` |
| reference 가 빈 객체 (loader fallback 도 실패) | INFO 1회 | `empty income bracket reference, skipping annotation: policyId={}, year={}` |

reference 빈 객체 케이스: `IncomeBracketReferenceLoader.resolveReference` 가 PR #48 에서 이미 처리하지만 만약을 대비한 방어층. Annotator 가 빈 reference 받으면 모든 패턴 skip.

`policyId` 는 Annotator 시그니처 명시 인자 (§4.1 참조). 호출 측 `GuideGenerationService.generateGuide()` 가 `command.policyId()` 를 그대로 전달. MDC 미사용 — 시그니처가 명시적이 더 단순하고 테스트 친화적.

## 9. 테스트 전략 (TDD)

### 9.1 `IncomeBracketAnnotatorTest` (신규, 단위)

| 테스트 케이스 | 검증 |
|---|---|
| `중위소득_60퍼_패턴이_텍스트에_있으면_1인2인_환산값_삽입` | 단일 bullet, yaml 등록 비율 |
| `차상위_패턴이_텍스트에_있으면_1인_환산값만_삽입` | nearPoor 50%, 1인만 |
| `이미_만원_표기가_있으면_같은_bullet은_전체_skip` | LLM 값 보존 (PDF 인용값 우선) |
| `yaml에_없는_비율은_텍스트_보존_및_WARN_로그` | LogCaptor 또는 LoggingEvent 검증 |
| `같은_텍스트_안_동일_비율_반복은_첫_등장에만_삽입` | 두 번째 매칭 skip |
| `oneLineSummary_highlights_pitfalls에도_적용` | 5개 텍스트 필드 모두 |
| `target_criteria_content_groups_items에_적용` | paired 3개 |
| `빈_reference면_모든_패턴_skip_및_INFO_로그_1회` | reference fallback 실패 케이스 |
| `중위소득_표현_변형_매칭` | "기준중위소득 60%", "중위소득의 60%", "중위소득 60% 이내", "중위소득 60% 까지", "중위소득 60%" (이하/이내/까지 없음) |

### 9.2 `GuideGenerationServiceTest` 갱신

- Annotator mock 주입, 1차 호출에서 `verify(annotator, times(1)).annotate(...)` 검증
- retry 시나리오: `verify(annotator, times(2)).annotate(...)` 2회 호출 검증
- `computeHashForTest` 가 annotator 버전 변경 시 다른 hash 반환 (캐시 무효화 검증)

### 9.3 `GuideValidatorTest` 정리

- `checkMissingAmount` / `hasMissingAmount` 관련 테스트 **삭제**
- `ValidationReport` 시그니처 변경 반영
- group mix / insufficient highlights 테스트는 그대로 유지

### 9.4 통합 테스트는 추가 안 함

Annotator 가 순수 함수 + 단위 테스트로 충분히 cover. PR #48 의 `GuideGenerationServiceRetryTest` 가 retry 흐름 자체는 이미 cover.

## 10. PR 분할 제안

**단일 PR 권장**. 변경 범위:
- 신규 1 클래스 + 단위 테스트
- 기존 2 클래스 (Service, Validator) 변경
- 변경량 적고 (~400 라인 추정), 커밋 단위로 분할 시 caching/test fixture 가 어색해짐

대신 커밋은 분리:
1. `feat(guide): IncomeBracketAnnotator 도메인 + 단위 테스트` (TDD)
2. `feat(guide): GuideValidator 환산값 검증 제거`
3. `feat(guide): GuideGenerationService Annotator 통합 + sourceHash invalidate`

## 11. 결정 로그

| # | 결정 | 선택 | 사유 |
|---|---|---|---|
| Q1 | 표기 가구원수 정책 | C(본문 추출) → 사용자 결정. 단 PR #48이 1·2인 모두 굳힘 | 사이클 범위 외, 별도 사이클 |
| Q2 | 데이터 소스 | yaml (PR #48 이미 적용) | 환경별 차이 없음, 연 1회 갱신 |
| 새Q1 | 환산값 안정화 방향 | B 결정적 후처리 | 비용 0, 100% 결정적 |
| Q3 | 후처리 적용 범위 | B 모든 텍스트 필드 | 일관성, 단일 함수 호출 |
| Q4 | 이미 환산값 있는 경우 | A skip | system prompt §9(a) PDF 우선 보존 |
| Q5 | yaml 미등록 비율 | D skip + WARN | system prompt §9 끝줄 일관, 운영 visibility |
| Q6 | 컴포넌트 위치 | B 별도 application service | SRP, 단위 테스트 용이, 모듈 경계 명확 |
| Q7 | validator 검증 2 | B 제거 | 후처리가 책임 인수, 무의미한 retry 비용 제거 |

## 12. 위험 / 완화

| 위험 | 영향 | 완화 |
|---|---|---|
| 후처리 정규식 누락 (특수 표현 미흡수) | 환산값 미삽입 | 단위 테스트에서 변형 표현 9종 cover, 운영 발견 시 정규식 보강 |
| LLM 환산값이 yaml 과 다른데 skip | 부정확값 노출 | system prompt §9 가 LLM 에게 만들지 말라 명시 + PDF 인용값은 유효 우선. WARN 메트릭은 v0.x |
| referenceYear 가 yaml 미등록 | 환산값 전체 누락 | PR #48 `resolveReference` findLatest fallback. 빈 reference 면 INFO 로그 + 모든 패턴 skip |
| 같은 텍스트 안 환산값 자체 재인식 | 중복 삽입 | EXISTING_AMOUNT_PATTERN 으로 사전 검사 (skip 조건) |
| Annotator 버그로 환산값 누락 | retry 안전망 없음 (Q7 B) | 단위 테스트 cover. WARN 로그로 운영 detection. 발견 시 패치 PR |

## 13. 후속 / 미결

- 정책 본문 가구원수 추출 → 표기 압축 (Q1 C 재검토): PR #48의 1·2인 모두 표기를 가구원수 명시 정책에 맞게 좁힐지 별도 사이클
- LLM 환산값 정확도 메트릭 (Q4 C): WARN 로그 → 정식 메트릭 승격은 v0.x
- 첨부 PDF 인용값과 yaml 값 차이 검출: `attachment-source-trace-backlog.md` 의 후속
- `formatManwon` 통일: 현재 `IncomeBracketReference.formatManwon` 은 `%.1f만` 형식 (toContextText 용), Annotator 는 정수만원 형식. 추후 두 형식 통일 검토
