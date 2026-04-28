# 가이드 정확도 강화 — 소득 환산 / 그룹 분리 / 특징(highlights) 도입 — Design Spec

> **버전**: v0.1
> **작성일**: 2026-04-28
> **모듈**: `policy` (신규 reference 도메인), `guide` (출력 스키마·프롬프트·검증 강화), `PolicyDetailPage` (프론트)
> **선행 사이클**: `2026-04-28-attachment-extraction-pipeline-design.md` (첨부 임베딩) — 입력 활용 (변경 없음)
> **선행 사이클**: `2026-04-28-easy-policy-interpretation-design.md` (가이드 페어드 레이아웃) — 본 사이클이 출력 스키마 확장

---

## 1. 목표와 비목표

### 1.1 목표
- 소득 기준이 비율(%)로만 표기되어 사용자에게 와닿지 않던 풀이를 **실제 금액(2026년 기준 1·2인 가구 월 약 N만원)** 으로 환산해 병기한다.
- "차상위 초과 / 차상위 이하" 같이 **분류별로 기준이 다른 정책**에서, 한 group에 분류가 섞여 사용자가 자신의 케이스를 분간하기 어려운 문제를 해결한다.
- 사용자가 첨부 PDF를 직접 열지 않고도 **정책의 특징·차별점을 파악**할 수 있도록 `highlights`(이 정책의 특징) 출력 단위를 추가한다.
- 가이드 풀이의 정확도/구조 위반(그룹 분리·환산값·highlights 부족)을 **자동 검증 + 1회 LLM 재시도**로 강화한다.

### 1.2 비목표
- 첨부 추출 / 임베딩 파이프라인 자체 — 별도 spec (입력만 활용).
- 사용자별 가구원 수 맞춤 환산 — 정책 단위 가이드이므로 1·2인 기준 일반 표시.
- 항목 단위(불릿) 첨부 trace — `attachment_id` 출처 표시는 v0.x.
- 다국어 / 다른 통화 단위.
- 운영자 admin UI에서 reference 데이터 갱신 — yaml PR 방식 유지.
- 환산값 자동 갱신 봇 (보건복지부 발표 크롤링).

---

## 2. 배경

- 첨부 임베딩 파이프라인 도입(694d66a) 후에도 가이드 풀이 정확도가 기대만큼 오르지 않음.
- 정책 7번류처럼 **차상위 초과 / 차상위 이하**에 따라 자격·금액이 다른 정책이, 한 group의 items에 두 분류가 섞여 출력됨 → 사용자가 자기 분류를 골라낼 수 없음.
- 비율 표기("중위소득 60% 이하")만으로는 사용자가 본인 소득과 비교가 어려움. 일부 정책은 첨부 PDF에 환산 금액이 박혀 있음에도 가이드 풀이에는 누락.
- 현재 시스템 프롬프트(`OpenAiChatClient.SYSTEM_PROMPT`)는 환각 방지를 위해 **"환산 금액 임의 계산 금지"** 로 강하게 막혀 있어, LLM이 환산값을 절대 생성하지 못함. 첨부에 명시된 값마저 활용 못 하는 부작용.
- pitfalls(놓치기 쉬운 점)만으로는 "정책의 특징·차별점" 같은 긍정·중립 정보가 표현되지 않아, 사용자가 PDF를 보지 않고는 정책 정체를 충분히 파악하기 어려움.

---

## 3. 범위

### 포함
- `policy` 모듈에 `IncomeBracketReference` 도메인 + yaml 기반 reference 데이터 도입.
- `guide` 모듈:
  - `GuideContent`에 `highlights` 출력 단위 추가
  - 시스템 프롬프트 환산값 표기 규칙 추가, 그룹 분리 강제 강화, highlights/pitfalls 의미 분리 명확화, few-shot 예시 3개 추가
  - user message에 `[참고 - 환산표]` 컨텍스트 주입
  - `GuideValidator`에 그룹 분리·환산값 누락·highlights 부족·sourceField 유효성 검증 추가
  - 검증 1·2·3 위반 시 1회 LLM 재시도
  - `Guide.sourceHash` 입력에 `referenceData.version` + `prompt.version` 포함
- 프론트:
  - `PolicyHighlightsCard` 신규 컴포넌트
  - `SourceLinkedListCard` 공통 추출 (pitfalls·highlights 공유)
  - 두 카드 헤더 우측에 `📎 원본 첨부` 버튼 (단일=새 탭 / 다수=`AttachmentSection` 스크롤)
  - 가이드 응답 타입에 `highlights` 추가

### 제외 (§14 비범위 참고)

---

## 4. 아키텍처

### 4.1 모듈 배치

```
┌─────────────────────────────────────────────────────────────┐
│ policy 모듈                                                  │
│  - IncomeBracketReference (record, 신규 도메인)              │
│  - IncomeBracketReferenceLoader (port + yaml impl)          │
│  - resources/income-bracket/{year}.yaml                     │
└────┬────────────────────────────────────────────────────────┘
     ↓ (의존)
┌─────────────────────────────────────────────────────────────┐
│ guide 모듈                                                   │
│  - GuideContent: highlights 추가                             │
│  - GuideGenerationInput: referenceContext 추가               │
│  - GuideGenerationService: hash 입력 변경, 재시도 루프        │
│  - GuideValidator: 검증 4종 추가                              │
│  - OpenAiChatClient: 프롬프트·스키마·user message 변경        │
└─────────────────────────────────────────────────────────────┘
     ↓ (응답)
┌─────────────────────────────────────────────────────────────┐
│ frontend                                                     │
│  - PolicyHighlightsCard.tsx (신규)                           │
│  - SourceLinkedListCard.tsx (공통 추출)                       │
│  - PolicyDetailPage.tsx: 카드 1개 추가                        │
└─────────────────────────────────────────────────────────────┘
```

의존 방향: `guide` → `policy` (기존). frontend는 `guide` API 응답 사용.

### 4.2 손대지 않는 컴포넌트
- 첨부 추출 파이프라인 (`ingestion`) — 입력만 활용
- RAG 인덱싱·Q&A — 변경 없음
- 적합도 판정 — 변경 없음
- `Guide` 엔티티 컬럼 구조 — JSONB 자유 스키마라 컬럼 변경 없음

---

## 5. 도메인 모델

### 5.1 신규: `IncomeBracketReference` (policy 모듈)

```
policy/
├── domain/model/
│   └── IncomeBracketReference.java       (신규 record)
├── application/port/
│   └── IncomeBracketReferenceLoader.java (신규 port)
└── infrastructure/external/
    └── YamlIncomeBracketReferenceLoader.java (신규 impl)
```

**`IncomeBracketReference` (record)**:
```java
public record IncomeBracketReference(
    int year,
    Map<HouseholdSize, Map<Integer, Long>> medianIncome, // percent(int) -> KRW/month
    Map<HouseholdSize, Long> nearPoor                    // 차상위 (중위 50%)
) {
    public Optional<Long> findAmount(HouseholdSize size, int percent) {
        return Optional.ofNullable(medianIncome.get(size))
            .map(m -> m.get(percent));
    }
}

public enum HouseholdSize { ONE, TWO }
```

`Map<Integer, Long>` 채택 사유: 정책에서 등장하는 % 비율이 다양(10/20/30/40/47/50/60/70/80/90/100/120/130/140/150/170/180 등)해 enum보다 Map이 유연. 새 % 추가 시 yaml 행만 추가하면 자동 인식 — 코드 변경 불필요.

(가구원 수 3·4인은 본 사이클 비범위. yaml 행 추가만으로 확장 가능)

**`IncomeBracketReferenceLoader` (port)**:
```java
Optional<IncomeBracketReference> findByYear(int year);
IncomeBracketReference findLatest();
```

**구현**: `YamlIncomeBracketReferenceLoader` — `classpath:income-bracket/*.yaml` 전체 로드 (부팅 시 1회). `findByYear` 미스 시 로그 + `findLatest` 사용을 호출자가 결정.

### 5.2 yaml 형식

`backend/src/main/resources/income-bracket/2026.yaml`:

```yaml
year: 2026
version: 1                # sourceHash 입력에 사용. 갱신 시 증분
unit: KRW_MONTH
medianIncome:
  "1":                    # 1인 가구
    "10":  TBD            # implementation 단계에서 보건복지부 고시값으로 채움
    "20":  TBD
    "30":  TBD            # 생계급여 기준선 (참고)
    "40":  TBD            # 의료급여 기준선 (참고)
    "47":  TBD            # 주거급여 기준선 (참고)
    "50":  TBD            # 차상위 동등치
    "60":  TBD            # 청년 주거 정책 다수
    "70":  TBD
    "80":  TBD
    "90":  TBD
    "100": TBD
    "120": TBD
    "130": TBD
    "140": TBD
    "150": TBD
    "170": TBD
    "180": TBD            # 청년도약계좌 등
  "2":                    # 2인 가구 (결혼 / 동반)
    "10":  TBD
    "20":  TBD
    "30":  TBD
    "40":  TBD
    "47":  TBD
    "50":  TBD
    "60":  TBD
    "70":  TBD
    "80":  TBD
    "90":  TBD
    "100": TBD
    "120": TBD
    "130": TBD
    "140": TBD
    "150": TBD
    "170": TBD
    "180": TBD
nearPoor:                 # 차상위 (기준중위소득 50% 단일 기준)
  "1": TBD
  "2": TBD
```

**비율 셋 결정 기준**: 청년 정책 실무에서 자주 등장하는 % (10/20/30/40/47/50/60/70/80/90/100/120/130/140/150/170/180 — 총 17개) 를 1·2인 가구 모두 채워둔다.

**갱신 정책**:
- 운영 중 yaml에 없는 % 가 정책에 등장하면 그때 yaml에 행 1개만 추가 + `version` 증분 → PR 1건으로 갱신 (1~2분 작업).
- 매년 보건복지부 발표 갱신 시 새 yaml 파일(`2027.yaml`) 추가 + `version: 1`. 기존 연도 yaml은 보존 (과거 referenceYear 정책 안정 동작).

수치는 implementation 단계에서 2026년 보건복지부 고시값으로 채운다. spec 단계에서는 yaml 구조와 키 셋만 확정.

### 5.3 `GuideContent` 스키마 변경

**Before** (현재):
```json
{ "oneLineSummary": "...", "paired": {...}, "pitfalls": [...] }
```

**After**:
```json
{
  "oneLineSummary": "...",
  "highlights": [
    { "text": "월 최대 20만원 월세 지원", "sourceField": "SUPPORT_CONTENT" }
  ],
  "paired": { "target": {...}, "criteria": {...}, "content": {...} },
  "pitfalls": [
    { "text": "월세 60만원 초과 주택은 대상 제외", "sourceField": "SUPPORT_TARGET" }
  ]
}
```

- `highlights[].sourceField`: 기존 `pitfalls`와 동일 enum (`SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY`) 재사용. 새 enum 추가 안 함.
- `highlights` 길이: 3~6개 보장 (프롬프트 + validator 검증 3).
- `highlights.size() == 0`이면 카드 미렌더 (pitfalls와 동일 정책. 단 검증 3에서 0개는 재시도 트리거).

도메인 모델: `GuideContent` record에 `List<GuideHighlight> highlights` 필드 추가. `GuideHighlight` record는 `GuidePitfall`과 동일 형식이지만 의미 분리를 위해 별도 타입.

### 5.4 마이그레이션

- `Guide.content` JSONB 자유 스키마 — 컬럼 변경 없음.
- 기존 데이터는 `prompt.version` 변경(§7-3)으로 hash 불일치 → 다음 ingestion / 스케줄 사이클에서 자연 재생성.
- 별도 백필 스크립트 불필요.
- 비용 영향: 기존 정책 ~30개 × LLM 1~1.3회 호출 (재시도 평균 포함). 일회성, 운영 부하 미미.

---

## 6. 데이터 흐름

```
[1] GenerateGuideCommand 수신
    ↓
[2] GuideGenerationService
    ├─ Policy + PolicyDocument 청크 조회 (기존)
    ├─ IncomeBracketReferenceLoader.findByYear(policy.referenceYear)
    │    miss → findLatest() + 로그
    ├─ sourceHash 계산 (입력에 referenceData.version + prompt.version 추가)
    │    └─ 기존 Guide.sourceHash와 동일 → 스킵 (기존 동작)
    ├─ GuideGenerationInput.of(policy, chunks, referenceData) 생성
    ↓
[3] OpenAiChatClient.generateGuide(input)
    ├─ 1차 LLM 호출 (system + user message[원문 + 환산표])
    ↓
[4] GuideValidator.validate(input, content)
    ├─ 검증 1 (그룹 분리), 2 (환산값), 3 (highlights 부족) → 위반 시 retry 후보
    ├─ 검증 4 (sourceField 유효성) → 해당 항목 폐기
    └─ 검증 5·6 (숫자 토큰, 친근체) → 로그만
    ↓
[5] retry 후보가 있으면
    ├─ 2차 LLM 호출 (user message 끝에 [이전 응답 검증 실패] 피드백 추가)
    ├─ 다시 validate → retry 후보가 있어도 더 이상 재시도 안 함
    │    1차 응답 vs 2차 응답 중 위반 적은 쪽 저장 + 경고 로그
    ↓
[6] guideRepository.save(content, newHash)
```

### 6.1 컨텍스트 주입 — user message 추가 블록

```
[정책 메타]
title: ...
referenceYear: 2026

[원문]
[summary] ...
[supportTarget] ...
[chunk-0] ...

[참고 - 환산표 (2026년 기준)]
기준중위소득 (1인 가구):
  30%=70.6만 / 47%=110.6만 / 50%=117.6만 / 60%=141.1만 / 70%=164.7만 / 80%=188.2만 /
  100%=235.3만 / 120%=282.3만 / 130%=305.9만 / 150%=352.9만 / 180%=423.5만
기준중위소득 (2인 가구):
  30%=116.7만 / 47%=182.8만 / 50%=194.4만 / 60%=233.3만 / 70%=272.2만 / 80%=311.1만 /
  100%=388.9만 / 120%=466.7만 / 130%=505.6만 / 150%=583.3만 / 180%=700.0만
차상위계층 (기준중위소득 50%):
  1인=117.6만 / 2인=194.4만
주의: 위 값은 [원문 - 첨부]에 환산 금액이 명시되지 않은 경우에만 사용한다. 첨부에 명시된 값이 우선이다.
```

(예시 수치는 placeholder. implementation에서 yaml 실제값으로 채워짐. 환산표는 yaml에 들어있는 % 전부를 user message에 주입하나 spec에서는 가독성을 위해 일부만 표기)

### 6.2 referenceYear 미일치 처리

- `findByYear(policy.referenceYear)` miss 시 `findLatest()` 결과 사용 + `WARN` 로그.
- 풀이 텍스트에는 LLM이 환산표 헤더의 연도("2026년 기준")를 그대로 인용하도록 프롬프트 명시.

---

## 7. 프롬프트 / LLM 입력 변경

### 7.1 시스템 프롬프트 — 추가/변경 원칙

기존 `OpenAiChatClient.SYSTEM_PROMPT`에서 다음 매핑을 **제거**:
```
- "중위소득 60% 이하" → "전국 가구 소득을 일렬로 세웠을 때 중간에 위치한 가구 소득의 60% 이하 (정확한 금액은 매년 정부 발표 참조)"
```

대신 다음 원칙·매핑으로 교체/추가:

```
[변경] 원칙 7. 추정 금지
   - "~일 수 있다", "~로 추정" 등 가정 금지.
   - 입력 [원문] 또는 [참고 - 환산표]에 명시되지 않은 환경값을 임의로 만들지 않는다.

[추가] 원칙 9. 환산값 표기 규칙
   - "중위소득 N% 이하", "차상위계층" 같은 비율·분류 표기는 풀이에 환산 금액을 병기한다.
   - 우선순위:
     (a) [원문 - 첨부 청크]에 환산 금액이 명시되어 있으면 그 값을 그대로 인용. 가구원 수 범위가 명시되어 있으면 그 범위를 따른다.
     (b) (a)가 없으면 [참고 - 환산표]의 1·2인 가구 기준 금액을 사용한다.
   - 표기 형식: "중위소득 60% 이하 (2026년 기준 1인 가구 월 약 141만원, 2인 가구 월 약 233만원)"
   - 차상위계층은 별도 분류이므로 "차상위계층 이하 (2026년 기준 1인 가구 월 약 117만원 이하)"처럼 표기.
   - [참고 - 환산표]에도 없으면 비율만 표기 (만들어내지 않는다).
   - 같은 풀이 안에서 동일 비율이 반복 등장하면 환산값은 첫 등장에만 병기 (가독성).

[강화] 원칙 5. 독립성 유지(중복 분리)
   - 분류 키워드(차상위 초과/이하, 일반공급/특별공급, 미혼/기혼·맞벌이, 1인/다인 가구 등)가 다르면 절대 한 group에 섞지 않는다.
   - 같은 group 안에 서로 다른 분류 키워드가 등장하면 group 분할 실패로 간주.

[추가] 출력 단위 — highlights
   - 사용자가 PDF를 보지 않고도 정책의 핵심 특징을 파악할 수 있는 항목 3~6개.
   - 혜택의 강도, 차별점, 신청 시점/방법의 특이사항, 우대조건, 중복 수혜 가능 여부 등.
   - "조심·함정·예외·제외 조건"은 highlights가 아닌 pitfalls에 둔다.
   - 각 항목 sourceField 라벨 필수 (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY).

[명확화] pitfalls
   - 부정적·함정·예외·제외 조건만. 자격 미달 트리거, 중복 수혜 제한, 사후 의무, 신청기한 외.
   - 긍정·중립적 특징은 highlights로 보낸다.

[추가] 용어 매핑
   - "차상위계층" → "기초생활수급자보다는 소득이 조금 높지만 일반 가구보다는 낮은 계층 (정부가 정한 기준 이하)"
```

### 7.2 few-shot 예시 추가 (3개)

기존 [변환 예시 1·2·3]에 다음을 추가:

- **[변환 예시 4] 차상위 분류 분리** — 정책 7번류. selectionCriteria에 "차상위계층 이하: A / 차상위 초과: B" 같은 입력 → 두 group으로 분리. label = `"차상위계층 이하 (소득 기준)"` / `"차상위 초과 (소득 기준)"`.
- **[변환 예시 5] 환산값 PDF 우선** — 첨부 청크에 "2026년 1·2인 가구 월 138/230만원" 같은 표기가 있는 경우, 풀이는 그 값 그대로 인용. [참고 - 환산표]는 무시.
- **[변환 예시 6] highlights vs pitfalls 분리** — 같은 정책 입력에 대해 highlights 3개 (혜택/특이점/우대) + pitfalls 2개 (제외 조건/사후 의무) 작성. 어느 항목이 어디로 가야 하는지 명확히 보여줌.

### 7.3 user message 변경

`OpenAiChatClient.buildUserMessage(input)` 끝에 `[참고 - 환산표]` 블록을 추가. `GuideGenerationInput`에 `IncomeBracketReference referenceData` 필드 추가.

### 7.4 JSON 응답 스키마 변경

`buildResponseFormat()`의 `required`에 `highlights` 추가. 형식은 기존 `pitfallSchema`와 동일.

```json
{
  "required": ["oneLineSummary", "highlights", "target", "criteria", "content", "pitfalls"],
  "properties": {
    "oneLineSummary": { "type": "string" },
    "highlights": { "type": "array", "items": pitfallSchema },
    "target":   { "anyOf": [pairedSchema, null] },
    "criteria": { "anyOf": [pairedSchema, null] },
    "content":  { "anyOf": [pairedSchema, null] },
    "pitfalls": { "type": "array", "items": pitfallSchema }
  }
}
```

`pitfallSchema` (기존 그대로): `{ text: string, sourceField: enum }`.

---

## 8. 후처리 검증 / 재시도

### 8.1 검증 항목

| # | 검증 | 위반 조건 | 처리 |
|---|---|---|---|
| 1 | 그룹 분리 위반 | 한 `group.items` 안에 분류 키워드가 2종 이상 등장 | **재시도 트리거** |
| 2 | 환산값 누락 | `중위소득\s*\d+%` 또는 `차상위`가 등장한 group/항목 안에 `\d+\s*만원` 형태 환산값이 함께 없음 | **재시도 트리거** |
| 3 | highlights 부족 | `highlights.size() < 3` | **재시도 트리거** |
| 4 | sourceField 유효성 | `pitfalls/highlights[].sourceField`가 입력에서 비어있는 필드를 가리킴 | 해당 항목 폐기 |
| 5 | 숫자 토큰 누락 (기존) | 원문 토큰이 풀이에서 누락 | 로그만 |
| 6 | 친근체 (기존) | `~예요/~드려요/~해요` 등장 | 로그만 |

### 8.2 분류 키워드 화이트리스트

`GuideValidator` 상수:
```java
private static final List<String> CATEGORY_KEYWORDS = List.of(
    "차상위", "일반공급", "특별공급", "신혼부부",
    "생애최초", "맞벌이", "다자녀", "기혼", "미혼"
);
```
운영 데이터로 누락 키워드 발견 시 점진 보강. spec에 명시.

### 8.3 재시도 메커니즘

```
1차 LLM 호출 → 응답 A
GuideValidator.validate(input, A)
  ├─ 검증 1·2·3 위반 없음 → 검증 4(항목 폐기) 적용 후 저장 종료
  └─ 검증 1·2·3 위반 있음 → 재시도 진입

재시도 (최대 1회)
  user message 끝에 [이전 응답 검증 실패] 블록 추가:
    - "그룹 'X'에 '차상위 이하'와 '차상위 초과'가 섞여 있음. 분리할 것."
    - "'중위소득 60% 이하'에 환산값 누락. 환산표 참조해 병기할 것."
    - "highlights가 1개. 최소 3개 이상 작성할 것."
  2차 LLM 호출 → 응답 B
  GuideValidator.validate(input, B)
    ├─ 검증 1·2·3 위반 없음 → 검증 4 적용 후 B 저장
    └─ 검증 1·2·3 위반 있음 → 위반 수가 적은 쪽 저장 + WARN 로그 (운영 메트릭)
```

- 재시도는 **최대 1회**. 무한 루프 방지.
- 응답 비교는 검증 1·2·3 위반 항목 수의 합으로. 동일하면 1차 응답 우선 (재시도 비용 무의미해질 위험 줄임).

### 8.4 결정적 후처리는 도입하지 않음

대안으로 검증 후 텍스트 자동 수정(예: 정규식으로 환산값 자동 삽입)도 가능하나 도입하지 않음. 사유:
- LLM 자연어 출력에 정규식 짜깁기는 어색한 문장·중복 표기·맥락 손상 위험.
- 환산은 LLM에게 시키고 검증으로만 잡는 책임 분리.

### 8.5 메트릭 노출

- `guide_generation_retry_total` (counter)
- `guide_validation_violation{rule=1|2|3|4}` (counter, label)
- `guide_generation_final_violation` (counter — 재시도 후에도 위반인 경우)
- 재시도율(>5%) / 최종 위반율(>1%) 알람 → 프롬프트·few-shot 점검 신호.

---

## 9. UI 변경

### 9.1 `PolicyDetailPage` 좌측 메인 컬럼 순서

```
1. PolicyHeader (제목 + 북마크)               [기존]
2. ✨ 한 줄 요약 카드                          [기존]
3. 🌟 이 정책의 특징 (highlights) 카드        [NEW]
4. 정책 요약 (원문 policy.summary)             [기존]
5. PolicyMetaSummary                          [기존]
6. 페어 #1: 쉬운 지원대상 + 원문               [기존]
7. 페어 #2: 쉬운 선정기준 + 원문               [기존]
8. 페어 #3: 쉬운 지원내용 + 원문               [기존]
9. ⚠️ 놓치기 쉬운 점 (pitfalls) 카드           [기존]
10. ReferenceSiteSection                      [기존]
11. AttachmentSection                         [기존]
12. 공식 신청 채널                             [기존]
13. Q&A                                        [기존]
```

위치 사유: 한 줄 요약(정체) → 특징(차별점) → 페어드(자격·조건 정밀) → pitfalls(예외·함정) → 첨부.

### 9.2 `이 정책의 특징` 카드 시각 구성

```
┌─ 🌟 이 정책의 특징                  [📎 원본 첨부] ─┐
│                                                    │
│ • 월 최대 20만원 월세 지원 (12개월)                  │
│   └ [지원내용 ↗]                                    │
│                                                    │
│ • 다른 청년 주거 지원과 중복 수혜 가능                │
│   └ [지원내용 ↗]                                    │
│                                                    │
│ • 신청 후 평균 2주 내 지급 결정                      │
│   └ [지원내용 ↗]                                    │
└────────────────────────────────────────────────────┘
```

- **톤**: 브랜드 indigo (긍정·중립). pitfalls 카드는 amber 경고 톤 → 색으로 구분.
- **출처 라벨**: pitfalls와 동일 동작 — 클릭 시 페어드 섹션 원문 박스로 `scrollIntoView({behavior: 'smooth'})` + 1.5초 하이라이트.
- **빈 상태**: `highlights.length === 0`이면 카드 미렌더.

### 9.3 첨부 바로가기 버튼 (highlights·pitfalls 카드 공통)

두 카드 헤더 우측에 `📎 원본 첨부` 버튼.

| 첨부 수 | 동작 |
|---|---|
| 0 | 버튼 미렌더 |
| 1 | 새 탭으로 `attachment.url`(외부 원본 URL) 열기 |
| 2 이상 | `AttachmentSection`으로 `scrollIntoView({behavior:'smooth'})` + 1.5초 하이라이트 |

항목 단위(불릿) 첨부 trace는 본 사이클 비범위 (첨부 spec §14 유지).

### 9.4 컴포넌트 변경

- **신규**: `PolicyHighlightsCard.tsx`
- **공통 추출**: `SourceLinkedListCard.tsx` — pitfalls·highlights 두 카드가 공유 (헤더 텍스트/색/아이콘만 props)
- **변경**: `PolicyPitfallsCard.tsx` → `SourceLinkedListCard` props 호출로 재작성
- **변경**: `PolicyDetailPage.tsx` — 카드 1개 추가 + 가이드 응답 타입에 `highlights` 추가

### 9.5 환산값 표기 — UI 변경 없음

환산값은 LLM 출력 텍스트 안에 자연스럽게 들어감 (예: "중위소득 60% 이하 (2026년 1인 가구 월 약 141만원)"). 별도 컴포넌트·뱃지 없음.

---

## 10. API 변경

### 10.1 가이드 조회 응답 (`GET /api/v1/guides/{policyId}`)

기존 응답에 `highlights` 필드 추가:

```json
{
  "success": true,
  "data": {
    "policyId": 1,
    "oneLineSummary": "...",
    "highlights": [
      { "text": "월 최대 20만원 월세 지원", "sourceField": "SUPPORT_CONTENT" }
    ],
    "paired": { ... },
    "pitfalls": [ ... ],
    "updatedAt": "2026-04-28T03:21:00"
  }
}
```

- 응답 DTO `GuideResponse`에 `highlights` 필드 추가.
- 404 (가이드 미생성) 동작은 변경 없음 — 프론트는 카드 전부 숨김.

### 10.2 가이드 생성 트리거 (`POST /api/v1/guides/generate`)

시그니처 변경 없음. 내부적으로 `IncomeBracketReferenceLoader` 주입 + 재시도 로직 동작.

---

## 11. sourceHash / 재생성 정책

### 11.1 sourceHash 입력 변경

기존 (`GuideGenerationService.computeHash`):
```
title + summary + body + supportTarget + selectionCriteria + supportContent + referenceYear + chunks
```

추가:
```
+ referenceData.year + referenceData.version    (yaml 갱신 시 invalidate)
+ prompt.version                                 (시스템 프롬프트 / 스키마 변경 시 invalidate)
```

- `referenceData.version` 은 yaml 파일 안 `version` 필드 또는 파일 수정 시각 hash 중 하나로 결정 — implementation에서 **yaml 명시 필드** 채택 권장 (배포·테스트 일관성).
- `prompt.version` 은 코드 상수. 본 사이클 = `"v2"`. 향후 프롬프트 의미 변경 시 증분.

### 11.2 자동 재생성

- 본 사이클 배포 = `prompt.version` 변경 → 모든 정책 가이드 hash 불일치 → 다음 ingestion / 스케줄 사이클에서 자연 재생성.
- 첨부 임베딩 spec(`AttachmentReindexService`)도 동일 `GuideGenerationService.generateGuide` 호출 → 자동 적용.
- 별도 백필 스크립트 / 마이그레이션 SQL 불필요.

---

## 12. 환경 / 의존성

### 12.1 신규 의존성

`backend/build.gradle`:
- `org.yaml:snakeyaml` (Spring Boot starter에 포함되어 있을 가능성 높음 — implementation에서 확인 후 추가/생략)

### 12.2 환경변수

신규 환경변수 없음. yaml 파일은 classpath resource로만 관리.

### 12.3 prompt.version 상수 위치

`OpenAiChatProperties` 또는 `GuideGenerationService` 내 private static 상수. implementation에서 결정.

---

## 13. 테스트 전략

### 13.1 단위 테스트

- `IncomeBracketReference` / `IncomeBracketReferenceLoader`:
  - yaml 파싱 성공 / 누락 키 / 타입 오류
  - `findByYear` hit / miss
  - `findLatest` (다중 yaml 시 최신 연도 선택)
- `GuideValidator`:
  - 그룹 분리 위반 — 한 group items에 `차상위`+`일반공급` 섞임 → true
  - 환산값 누락 — `중위소득 60%` 단독 → true / `중위소득 60% (월 141만원)` → false
  - highlights 부족 — size<3 → true
  - sourceField 유효성 — 빈 `selectionCriteria` 가리키면 폐기
  - 숫자 토큰 누락 / 친근체 (기존 회귀)
- `OpenAiChatClient.parseResponse`:
  - highlights 정상 / null / 빈 배열
  - 응답에 `highlights` 누락 시 빈 배열로 fallback (스키마 strict 라 발생률 낮으나 방어)
- `GuideGenerationService.computeHash`:
  - referenceData version 변경 → 다른 hash
  - prompt.version 변경 → 다른 hash
  - 동일 입력 + 동일 version → 같은 hash

### 13.2 통합 테스트 (`@SpringBootTest`)

- `GuideGenerationService.generateGuide`:
  - LLM mock 1차 응답 위반 → 재시도 호출 발생 / 재시도 응답 통과 → 저장
  - LLM mock 2차도 위반 → 위반 수 적은 쪽 저장 + WARN 로그
  - reference yaml 변경 → 다음 호출에서 hash 불일치 → LLM 재호출
  - 동일 hash → LLM 호출 안 함 (기존 회귀)
- 정책 7번류 fixture (차상위 분류 입력) → 응답 group 라벨에 분류 명시 검증
- 환산값 fixture 두 시나리오:
  - 첨부 청크에 명시값 → 풀이에 그 값 인용 (reference 환산표는 컨텍스트로 들어가지만 무시되어야)
  - 첨부에 명시값 없음 → reference 환산표 1·2인 값 인용

### 13.3 프롬프트 회귀 테스트 (선택, implementation에서 결정)

- `src/test/resources/guide-prompt-fixtures/`에 입력 fixture + 기대 키 검증
- 자연어 매칭은 LLM 비결정성 고려해 안 함. JSON 스키마 + 분류 키워드 분리 + 환산값 등장 여부만 검증.

### 13.4 컨벤션

- `backend/CLAUDE.md`, `docs/CONVENTIONS.md`, `spring-test` 스킬 컨벤션 준수.
- DTO/Command/Result는 record. 도메인 모델은 의미 있는 도메인 메서드.
- 트랜잭션 경계는 application service.

---

## 14. 리스크 / 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| reference yaml 갱신 누락 (연도 바뀌었는데 직전 연도만 있음) | 환산값이 작년 기준 노출 | `findLatest()` fallback + 환산표 헤더에 연도 명시 + CI에서 "최신 연도가 N년 이전" 경고 |
| LLM이 reference 환산표를 인용했는데 첨부에 명시값 있음 (우선순위 위반) | 사용자 혼동 | 프롬프트 원칙 9 + few-shot 5번 강조. 운영 모니터링 |
| 재시도 1회로도 그룹 분리 못 풀리는 정책 (분류 5개 이상 복잡) | 가이드 품질 ↓ | 위반 수 적은 응답 저장 + 메트릭. 5% 초과 시 프롬프트·few-shot 보강 |
| 분류 키워드 화이트리스트 누락 → 검증 회피 | 그룹 섞여도 통과 | 운영 데이터 기반 점진 보강. spec에 명시 |
| 환산값 표기로 가이드 텍스트 길이 ↑ → 가독성 ↓ | 카드 길어짐 | 프롬프트 "동일 비율은 첫 등장에만 환산값" 규칙 + UI 카드 본문 max-height + ellipsis (UI 단계에서 평가) |
| highlights·pitfalls 경계 모호 → 항목 중복 | 정보 중복 | 프롬프트 명확화 + few-shot 6번. 동일 텍스트 중복 검증은 v0.x |
| LLM이 첨부 환산표를 잘못 추출 (예: 전년도 값 인용) | 부정확한 환산값 | 프롬프트에 "첨부 안의 환산표는 해당 정책의 referenceYear와 일치하는 값만 사용"  명시 |
| 비용 증가 (토큰 + 재시도) | LLM 비용 ↑ | reference 환산표 ~300토큰 / 재시도 발생률 메트릭 추적. 5% 초과 시 검토 |

---

## 15. 비범위

- **항목 단위(불릿) 첨부 trace** — `attachment_id`별 출처 표시는 v0.x.
- **다국어 / 다른 통화 단위**.
- **운영자 admin UI로 reference 데이터 갱신** — yaml PR 방식 유지 (MVP).
- **결정적 후처리(자동 텍스트 치환)** — §8.4 사유로 도입 안 함.
- **highlights·pitfalls 카드 접기/펼치기** — 3~6개 항목이라 불필요.
- **3·4인 가구 환산** — 청년 정책 타겟 외. yaml 행 추가만으로 확장 가능.
- **환산값 자동 갱신 봇 (보건복지부 발표 크롤링)** — spec 외.
- **사용자별 가구원 수 맞춤 환산** — 정책 단위 가이드 가정 유지.
- **highlights·pitfalls 항목 동일 텍스트 중복 검증** — v0.x.
- **사용자 피드백 메커니즘 ("이 풀이 틀렸어요" 신고)** — v1.

---

## 16. PR 전략

**단일 PR로 통합**한다. 사유:
- 백엔드 / 프론트가 한 사이클에서 e2e로 동작해야 가치 검증 가능 (가이드 출력 변경 → 사용자 화면 반영).
- 도메인 / 스키마 / 프롬프트 / 검증 / UI 변경이 의미상 한 묶음. 분할 시 중간 상태에서 LLM이 새 스키마로 응답하지 못하거나 (스키마만 먼저 머지) UI가 빈 카드를 렌더하거나 (UI만 먼저 머지) 하는 어색한 시점이 생김.
- 5개 분할로 가면 각 PR 의 독립 가치가 거의 없어 리뷰가 형식적이 되기 쉬움.

PR 안에서 commit 단위는 다음으로 나누어 리뷰 동선을 만든다:

1. `feat(policy): IncomeBracketReference 도메인 + yaml + loader`
2. `feat(guide): GuideContent.highlights 도메인 모델 + 응답 DTO`
3. `feat(guide): 시스템 프롬프트 / few-shot / user message + sourceHash 변경`
4. `feat(guide): GuideValidator 검증 4종 + 재시도 루프`
5. `feat(frontend): SourceLinkedListCard 공통 추출 + PolicyHighlightsCard + 첨부 바로가기`
6. `chore(guide): prompt.version 증분 + reference yaml 2026 데이터`

리뷰 중에 일부 commit 만 revert/수정 가능하도록 commit 분리는 유지.

---

## 17. 결정 로그 (이 사이클)

| # | 질문 | 선택 | 사유 |
|---|---|---|---|
| Q1 | 정확도 실패 모드 | A(그룹 분리) + B(환산값) + C(특징/주의 분리) 모두 | 사용자 응답 |
| Q2 | 환산값 데이터 출처 | (1) 첨부 PDF 우선 + reference fallback | 권위 소스 + 일관성 |
| Q3 | 가구원 수 처리 | PDF 명시 범위 그대로, fallback 1·2인 | 청년 정책 타겟 |
| Q4 | fallback 데이터 | (Y) 우리 reference yaml — 1·2인 중심 | 일관성 + 갱신 부담 적음 |
| Q5 | C축 표현 | (b) `highlights` 출력 단위 신규 추가 | "조심"과 "특징"의 의미 분리 |
| Q6 | 후처리 검증 강도 | 1회 LLM 재시도 + 로깅 (트리거 검증 1·2·3) | 비용·품질 균형 |
| Q7 | 첨부 바로가기 동작 | (가) 단일=새 탭 / 다수=`AttachmentSection` 스크롤 | UI 단순 |
| Q8 | reference 데이터 저장 | yaml seed (admin UI 없음) | MVP 범위, PR 기반 갱신 |
| Q9 | 결정적 후처리 도입 | 도입 안 함 | LLM 자연어에 정규식 짜깁기는 위험 |
| Q10 | 항목 단위 첨부 trace | 비범위 | 첨부 spec §14 유지 |
| Q11 | 3·4인 가구 환산 | 비범위 | 청년 정책 타겟 외 |
| Q12 | yaml 비율 셋 | 17개 (10/20/30/40/47/50/60/70/80/90/100/120/130/140/150/170/180) 처음부터 채움 + Map<Integer, Long> 채택 | 새 % 등장 시 yaml PR 1건으로 갱신 가능. 코드 변경 없이 yaml만으로 확장 |
| Q13 | PR 전략 | 단일 PR + 6 commit 분리 | e2e 가치 검증을 한 사이클에서. 분할 시 중간 상태 어색함 |

---

## 18. 후속 / 미결

- ~~yaml 파일 안 `version` 필드를 명시할지~~ → §5.2 에서 yaml 안 `version: 1` 명시 필드로 확정. 구조 변경 없을 시 정수 증분.
- `prompt.version` 상수 위치 — `OpenAiChatProperties` vs `GuideGenerationService` 내부 — implementation에서 결정.
- 2026년 보건복지부 고시 환산값 — implementation 첫 단계에서 공식 발표 자료 확인 후 17개 비율 × 2 가구원 수 + 차상위 2개 = 36개 값 yaml 채움.
- few-shot 예시 4번(차상위 분류 분리) 의 합성 입력 텍스트 — 정책 7번 또는 유사 정책의 실제 본문을 보고 implementation에서 정교화.
- 환산값 표기로 풀이 텍스트가 길어질 때 UI 카드 본문 max-height 정책 — implementation 검토.
- 동일 텍스트 중복 검증 (highlights·pitfalls 사이) — 운영 데이터 보고 v0.x 결정.
