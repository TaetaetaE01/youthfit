# 정책 신청 기간 추출 정확도 개선 — 설계 문서

- **작성일**: 2026-05-21
- **상태**: Draft — 섹션 A 확정 / 섹션 B·C 브레인스토밍 진행 중
- **대상 모듈**: `backend/src/main/java/com/youthfit/ingestion`
- **관련 코드**:
  - `IngestionService.resolvePeriod()` — 현재 추출 진입점
  - `PolicyPeriodExtractor` — 정규식 기반 본문 추출기
  - `OpenAiPolicyPeriodExtractor` — LLM 폴백 (CostGuard로 차단 가능)
  - `AttachmentExtractionScheduler` / `TikaAttachmentExtractor` / `HwpAttachmentExtractor` — 첨부 추출 파이프라인 (현재 신청기간 추출과 미연동)

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

## 3. 설계 결정 사항 (사용자 합의 요약)

브레인스토밍 단계에서 다음 결정이 합의되었다.

| 결정 항목 | 선택 | 이유 |
|---|---|---|
| LLM 사용 강도 | **하이브리드** | 정규식 + 라벨 컨텍스트로 후보를 만들고, 모호할 때만 LLM disambiguation 호출 |
| n8n 값 신뢰 정책 | **교차 검증 후 신뢰도 높은 것 채택** | n8n 한 쌍의 값도 본문/첨부 추출 결과와 점수 비교, 조용한 채널이 이기도록 설계 |
| 첨부 활용 시점 | **비동기 후처리 보강** | 정책 등록 시점엔 본문만, 첨부 추출 완료 이벤트에서 재시도 (등록 응답 지연 회피) |

## 4. 섹션 A — 아키텍처: 후보 수집 + 점수화 + 선택 ✅ 확정

### 4.1 큰 그림

기존 `IngestionService.resolvePeriod()` 의 분기 로직을 **`PeriodResolver`** 도메인 서비스로 분리한다. 각 추출 경로는 **`PeriodCandidateSource`** 포트의 구현체로 나뉘고, 결과는 신뢰도 점수가 붙은 **`PeriodCandidate`** 값 객체로 통일된다.

```
IngestionService.receivePolicy()
    └─> PeriodResolver.resolve(ctx)
            ├─ 후보 수집 (모든 소스)
            │     ├─ N8nApplyFieldsSource    (command.applyStart / applyEnd)
            │     ├─ BodyLabeledRegexSource  ("신청기간" 등 라벨 근접 윈도우)
            │     ├─ BodyGenericRegexSource  (라벨 무관, 본문 전체)
            │     └─ (첨부는 등록 시점엔 없음 → 섹션 C 비동기 보강)
            │
            ├─ 동일 (start, end) 후보 병합
            │     confidence = max(group) + 0.05 × (group.size − 1)   (최대 +0.15)
            │
            ├─ 최고 점수 < 0.70 AND 후보 그룹 ≥ 2 AND !CostGuard.enabled()
            │     → LlmDisambiguator.choose(body, candidates)
            │
            └─ 최종 ResolvedPeriod { start, end, source, confidence, evidence }
```

### 4.2 도메인 모델

```java
package com.youthfit.ingestion.domain.model;

public record PeriodCandidate(
    LocalDate start,
    LocalDate end,
    PeriodSource source,        // N8N, BODY_LABELED, BODY_GENERIC, ATTACHMENT_LABELED, LLM_DISAMBIGUATED
    double confidence,          // 0.0 ~ 1.0
    String evidence             // 매치 스니펫 (최대 80자, 운영 가시성용)
) {}

public enum PeriodSource {
    N8N,
    BODY_LABELED,
    BODY_GENERIC,
    ATTACHMENT_LABELED,
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

public interface PeriodLlmDisambiguator {
    Optional<PeriodCandidate> choose(String body, List<PeriodCandidate> candidates);
}

public record PeriodExtractionContext(
    String title,
    String body,
    LocalDate n8nApplyStart,
    LocalDate n8nApplyEnd,
    String externalId
) {}
```

### 4.4 점수 산정 정책

| 소스 / 케이스 | 기본 confidence | 비고 |
|---|---|---|
| `N8N` — 양쪽 모두 존재 | **0.60** | 단일 소스 신뢰도. 라벨 단서가 없어 검증 가능성이 낮음 |
| `N8N` — 한쪽만 존재 | **0.40** | 부분 정보 |
| `BODY_LABELED` — "신청\|접수\|모집기간" 라벨 + 완전 범위 | **0.85** | 가장 강한 단서 |
| `BODY_LABELED` — 같은 라벨 + 단일 마감일 | **0.65** | start = null, end만 |
| `BODY_GENERIC` — 라벨 없는 완전 범위 | **0.45** | 라벨 부재 페널티 |
| `BODY_GENERIC` — 라벨 없는 단일 마감일 | **0.35** | 매우 모호 |
| `ATTACHMENT_LABELED` — 첨부에서 라벨 + 완전 범위 | **0.75** | 본문보다 약간 낮게 (본문이 1차 소스) |
| **다중 소스 병합 보너스** | `+0.05 × (group.size − 1)`, **최대 +0.15** | 같은 (start, end) 가 N개 소스에서 나오면 보너스 |
| **LLM disambiguation 결과** | LLM이 반환한 confidence 그대로 (0.0~1.0), `source = LLM_DISAMBIGUATED` | 폴백 분기에서만 |

**선택 알고리즘**

1. 모든 `PeriodCandidateSource` 에서 후보 리스트를 수집 (각 소스는 0~N개 반환)
2. `(start, end)` 가 동일한 후보들을 그룹화 → `confidence = max(group.confidence) + bonus`
3. 그룹 중 최고 confidence 후보를 1차 선택
4. **모호 분기**: 최고 confidence < 0.70 AND 후보 그룹 ≥ 2 AND `!costGuard.enabled()` → `PeriodLlmDisambiguator.choose()` 호출, 반환값으로 교체
5. 최종 confidence < **0.55** 면 `ResolvedPeriod.empty()` 반환 — "확인 필요" 상태 (정책의 `applyStart`/`applyEnd` 는 null로 둠)

### 4.5 왜 이 구조인가

- **단일 책임**: 각 소스가 자신의 패턴만 책임. 새 패턴(예: HTML `<dl>` 표) 추가 시 새 구현체만 등록
- **비용 방어 + 정확도**: LLM은 항상 호출이 아니라 모호 분기에서만 — CostGuard 활성 환경에서도 정규식·라벨 기반 다단 후보로 누락이 줄어든다
- **교차 검증**: 다중 소스가 같은 답을 내면 confidence 가 자연스럽게 올라감 → n8n 값이 본문 라벨링 추출과 다르면, 라벨링된 쪽(0.85)이 n8n(0.60)을 이긴다
- **가시성**: 후보·점수·근거를 자료로 남기면 (섹션 C) 운영 단계에서 추출 실패를 분석할 수 있다

## 5. 섹션 B — 정규식 확장 & 라벨 윈도우 & LLM 프롬프트 ⏳ 진행 예정

> 브레인스토밍 단계 다음 차례. 다음 사항을 결정한다.
>
> - 라벨 윈도우 크기 및 라벨 사전 (`신청기간|접수기간|모집기간|공모기간|지원기간` 등)
> - 패턴 1~5 (시작 연도 생략, 동월 단축형, 단일 마감일, 요일/시간 끼어듦, 자연어) 처리 전략
> - 사업기간 vs 신청기간 라벨 가중치 (네거티브 라벨)
> - LLM disambiguator 프롬프트 — 후보 목록을 ID로 주고 ID만 받기 (JSON 스키마 단순화)
> - 본문 truncate 정책 — 라벨 위치 기준 스니펫 추출로 LLM 입력 크기 절감

## 6. 섹션 C — 첨부 비동기 보강 & 운영 메타 ⏳ 진행 예정

> 다음 사항을 결정한다.
>
> - 첨부 추출 완료 이벤트(`AttachmentExtracted` 등) 발행 위치 — `AttachmentExtractionScheduler` / `AttachmentReindexService`
> - `PeriodBackfillService` — 기존 정책의 `applyPeriodConfidence` 가 임계값 미만일 때만 재계산
> - 재계산 후 confidence 가 더 높을 때만 정책 업데이트 (덮어쓰기 정책)
> - 메타 저장 위치 — `Policy` 엔티티 컬럼 추가 vs 별도 `PolicyPeriodAudit` 테이블
> - 운영 가시성 — `IngestionRunLog` 에 추출 메타 통계 추가, 또는 admin 대시보드 카드
> - "기간 미확정" 정책의 UX 노출 — 리스트 뱃지, 상세 페이지 안내 문구

## 7. 마이그레이션 & 호환성 (잠정)

- 신규 컴포넌트만 추가, 기존 `PolicyPeriodExtractor` / `OpenAiPolicyPeriodExtractor` 는 새 소스 구현체로 흡수 후 점진 제거
- `IngestionService.resolvePeriod()` 시그니처는 그대로 유지, 내부에서 `PeriodResolver.resolve()` 호출로 교체
- 기존 등록된 정책의 `applyStart`/`applyEnd` 는 즉시 재계산하지 않음 — 섹션 C 의 보강 흐름이 점진적으로 보정
- `applyPeriodSource`/`applyPeriodConfidence` 컬럼은 nullable 로 추가 — 기존 행에는 NULL, 신규 등록부터 채움

## 8. 테스트 전략 (잠정)

- 누락 패턴 표 (§1.3) 1~6 각각에 대한 도메인 단위 테스트 — `PeriodResolverTest`
- 점수 병합 / 모호 분기 / LLM 폴백 호출 조건 시나리오 — `PeriodResolverTest`
- 각 `PeriodCandidateSource` 구현체별 단위 테스트
- `LlmDisambiguator` 는 페이크/스텁으로 결정성 확보 (실 호출은 통합 테스트에서만 옵트인)
- 회귀: 기존 정상 추출 케이스가 confidence 0.70+ 로 동일한 결과를 내는지 확인 (BODY_LABELED 라벨 일치 + 완전 범위)

## 9. 향후 작업 (Follow-up)

- 다회차 정책의 회차별 모델링 (별도 스펙)
- 회귀 데이터셋 + 자동 평가 파이프라인 (정확/누락/오추출률 메트릭)
- 첨부 외 외부 참고 URL 본문에서의 기간 추출 (가능성 검토)
