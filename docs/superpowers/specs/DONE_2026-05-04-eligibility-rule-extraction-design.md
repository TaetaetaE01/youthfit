# 적합도 룰 LLM 자동 추출 파이프라인 — 설계

- 작성일: 2026-05-04
- 범위: C (`EligibilityRule` 데이터를 LLM으로 자동 채우기)
- 의존: A (정책 상세 적합도 흐름 폴리싱), B (적합도 기반 추천 진입점) 의 선행 스펙
- v0 범위 외: 관리자 검수 페이지, 품질 메트릭 대시보드, 평가기 미지원 필드 확장, A·B 스펙

## 1. 배경

`eligibility` 모듈은 인프라가 모두 구현되어 있다.

- `POST /api/v1/eligibility/judge` 컨트롤러·서비스·평가기·`EligibilityRule` 도메인 모델 존재.
- 프론트도 정책 상세에서 "내 적합도 확인" 버튼·결과 카드까지 노출.

문제는 `EligibilityRule` 데이터가 비어 있다는 것이다. 룰이 비어 있으면 평가기가 모든 정책을 `LIKELY_ELIGIBLE`로 떨어뜨려 사용자에게 무가치한 결과를 보여준다. 정책 수가 늘면 손-시드는 비현실적이고, v0에 관리자 페이지가 없어 수기 입력 UI도 만들 수 없다.

해결: **정책 ingest 시점에 LLM이 정책 원문에서 룰을 자동으로 추출**한다. `guide` 모듈이 같은 패턴으로 운영 중이므로 그 패턴을 그대로 따른다.

## 2. 결정 사항 요약

| 결정 | 선택 | 근거 |
|---|---|---|
| 룰을 채우는 수단 | LLM 자동 추출 | v0에 관리자 페이지 없음, 정책 100+ 가정 |
| 트리거 시점 | `IngestionService` / `AttachmentReindexService` 안에서 정책 upsert / 첨부 재인덱싱 직후 | `guide`와 동일한 자리·패턴 |
| 신뢰도 처리 | 검증기 + `RuleConfidence` 메타 (HIGH/MEDIUM/LOW) | 적합도 도메인에 `UNCERTAIN` 등급이 이미 존재 → LLM 불완전성을 자연스럽게 흡수 |
| confidence 산정 | LLM-self-confidence (모델이 직접 라벨) | guide와 같은 추상화 수준, 결정적 후처리는 운영 데이터 보고 결정 |
| 갱신 전략 | delete-and-insert (정책 단위) | 룰 ID 외부 참조 없음, 부분 갱신 로직 복잡도 ↑ |
| 변경 감지 | 정책 단위 `sourceHash` (SHA-256, `PROMPT_VERSION` 포함) | guide 모듈과 동일 |
| 비용 방어 | 기존 `CostGuard` allowlist 재사용 | 별도 키 추가 안 함 |

## 3. 아키텍처

### 3.1 모듈 위치

모든 신규 코드는 기존 `eligibility` 모듈 안에서 끝난다 (모듈 경계 변경 없음).

```
[ingestion]  IngestionService.upsert(...)
                ↓ (한 줄 추가)
[eligibility] EligibilityRuleGenerationService.generateRules(policyId)
                ↓
              EligibilityRuleLlmProvider (port)
                ↓
              OpenAiEligibilityRuleClient (infra)
```

의존 방향
- `ingestion/application` → `eligibility/application` (`guide` 호출과 동일)
- `eligibility/application` → `eligibility/domain`, `policy/domain`, `rag/domain` (모두 도메인 포트)
- 도메인 레이어에 OpenAI/Spring 의존 없음

### 3.2 신규/변경 파일

| 레이어 | 파일 | 변경 |
|---|---|---|
| Domain | `eligibility/domain/model/RuleConfidence` | **신규** enum (HIGH/MEDIUM/LOW) |
| Domain | `eligibility/domain/model/EligibilityRule` | 컬럼 3개 추가 (`confidence`, `sourceHash`, `extractionVersion`) |
| Domain | `eligibility/domain/repository/EligibilityRuleRepository` | `deleteAllByPolicyId(Long)` 추가 |
| Domain | `eligibility/domain/service/EligibilityEvaluator` | LOW 룰 → 강제 `UNCERTAIN` 다운그레이드 |
| Application | `eligibility/application/service/EligibilityRuleGenerationService` | **신규** |
| Application | `eligibility/application/service/EligibilityRuleValidator` | **신규** |
| Application | `eligibility/application/port/EligibilityRuleLlmProvider` | **신규** 포트 |
| Application | `eligibility/application/dto/command/GenerateEligibilityRulesCommand` | **신규** |
| Application | `eligibility/application/dto/result/RuleGenerationResult` | **신규** |
| Application | `eligibility/application/dto/command/EligibilityRuleExtractionInput` | **신규** (LLM 입력) |
| Application | `eligibility/application/dto/result/RawExtractedRule` | **신규** (LLM 출력 DTO, 검증 전) |
| Application | `eligibility/application/dto/result/CriterionEvaluation` 또는 도메인 record | `confidenceNote` 필드 추가 |
| Presentation | `eligibility/presentation/dto/response/CriterionResponse` | `confidenceNote` 필드 추가 |
| Infrastructure | `eligibility/infrastructure/external/OpenAiEligibilityRuleClient` | **신규** (포트 구현) |
| Infrastructure | `eligibility/infrastructure/external/OpenAiEligibilityRuleProperties` | **신규** `@ConfigurationProperties` |
| Infrastructure | `eligibility/infrastructure/persistence/EligibilityRuleRepositoryImpl` | `deleteAllByPolicyId` 구현 |
| Infrastructure | `eligibility/infrastructure/persistence/EligibilityRuleJpaRepository` | `@Modifying` delete 쿼리 추가 |
| Application(외부) | `ingestion/application/service/IngestionService` | `ruleGenerationService.generateRules(...)` 한 줄 추가 |
| Application(외부) | `ingestion/application/service/AttachmentReindexService` | 동일하게 한 줄 추가 |
| 마이그레이션 | `db/migration/V{next}__add_eligibility_rule_extraction_meta.sql` | 컬럼 3개 ADD + 기본값 백필 (V 번호는 구현 시 가장 큰 기존 번호 + 1) |

## 4. 데이터 모델

### 4.1 `RuleConfidence` enum

```java
package com.youthfit.eligibility.domain.model;

public enum RuleConfidence {
    HIGH,    // 원문에 명시적 수치/단어 등장 (예: "만 19~34세")
    MEDIUM,  // 원문 단서로 합리적 추론 가능 (예: "청년" → age 19~34)
    LOW      // 모호하거나 추론 폭이 큼 (예: "근로 청년 우대" → employmentKind)
}
```

### 4.2 `EligibilityRule` 컬럼 추가

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| `confidence` | `VARCHAR(10)` enum | NOT NULL | `MEDIUM` | `RuleConfidence` |
| `source_hash` | `VARCHAR(64)` | NULL | NULL | 추출 당시 정책 sourceHash (정책 단위 변경 감지용; 같은 정책의 모든 룰이 같은 값) |
| `extraction_version` | `VARCHAR(10)` | NULL | NULL | 추출 시 사용된 `PROMPT_VERSION` |

- 기존 `field`, `operator`, `value`, `label`, `sourceReference` 그대로.
- 기존 데이터 백필: `confidence='MEDIUM'`, `source_hash=NULL`, `extraction_version=NULL`.
- NULL 허용은 시드된 손-입력 룰과의 호환을 위함.

### 4.3 도메인 메서드

- `EligibilityRule.builder()`에 `confidence`(필수), `sourceHash`(옵션), `extractionVersion`(옵션) 추가.
- setter 금지. 룰은 불변. 갱신은 정책 단위 delete-and-insert로 한다 (4.4).

### 4.4 갱신 전략 — delete-and-insert

정책 단위 `sourceHash`가 바뀌면 해당 정책의 기존 룰을 **전부 삭제하고 새로 insert**한다.

근거:
- 룰은 LLM이 묶음으로 만들고, 묶음 안에서 `field` 키도 바뀔 수 있음 (`incomeMin` ↔ `annualIncome`). 1:1 매칭 갱신은 어렵고 가치가 없다.
- 룰 ID에 외부 참조 없음 (다른 테이블에서 FK로 참조하지 않음).
- 한 트랜잭션에 묶으면 일관성 유지.

대안 (부분 갱신)을 채택하지 않은 이유: 룰 ID 안정성을 보장할 사유가 없고, 룰 비교 로직(field+operator+value 동등성)이 복잡도만 늘린다.

### 4.5 Repository 변경

`EligibilityRuleRepository` 인터페이스:

```java
List<EligibilityRule> findAllByPolicyId(Long policyId);   // 기존
void deleteAllByPolicyId(Long policyId);                   // 신규
```

JPA 구현은 `@Modifying` 쿼리로 1쿼리 = N rows delete.

## 5. 컴포넌트 / 호출 흐름

### 5.1 `EligibilityRuleGenerationService`

`@Transactional`로 추출→검증→저장을 한 트랜잭션에 묶음.

메서드 시그니처:
```java
RuleGenerationResult generateRules(GenerateEligibilityRulesCommand command);
```

흐름:
1. `costGuard.allows(policyId)` 통과 못 하면 skip + INFO 로그, `RuleGenerationResult(false, "cost-guard")` 반환.
2. `policyRepository.findById(policyId)` → 없으면 WARN 로그 + 실패 결과 반환.
3. `policyDocumentRepository.findByPolicyIdOrderByChunkIndex(policyId)`로 RAG 청크 조회.
4. 입력으로 `sourceHash` 계산 (SHA-256, `PROMPT_VERSION` 포함). guide와 같은 방식.
5. 기존 룰 1개 조회 → `source_hash`가 같으면 변경 없음 → `RuleGenerationResult(false, "변경 없음")`.
6. `EligibilityRuleLlmProvider.extractRules(input)` 1회 호출.
7. `EligibilityRuleValidator.validate(rawRules)`로 검증. 위반 트리거 있으면 `regenerateWithFeedback(input, feedback)` 1회 재시도. 위반 수가 줄지 않으면 1차 응답 사용.
8. 검증 통과 룰을 도메인 `EligibilityRule` 객체로 매핑 (`confidence`, `sourceHash`, `extractionVersion=PROMPT_VERSION` 채움).
9. `repository.deleteAllByPolicyId(policyId)` → 새 룰 묶음 일괄 insert.
10. INFO 로그 + 성공 결과 반환.

상수:
```java
static final String PROMPT_VERSION = "v1";  // 프롬프트/스키마 변경 시 증분
```

### 5.2 `EligibilityRuleValidator`

검증 항목:
- **필드 화이트리스트**: 평가기가 처리하는 10개 정규 필드명만 통과.
  `age`, `region`, `incomeMin`, `incomeMax`, `annualIncome`, `maritalStatus`, `employmentKind`, `education`, `majorField`, `specializationField`.
  평가기는 `region`/`legalDongCode`, `employmentKind`/`employmentStatus`, `education`/`educationLevel`을 모두 처리하지만 LLM 출력은 정규명 10개로만 받는다 (alias는 미허용 → 폐기). 프롬프트도 정규명 10개만 노출.
- **오퍼레이터 화이트리스트**: `RuleOperator` 6개 (EQ, NOT_EQ, GTE, LTE, IN, BETWEEN).
- **값 형식 검증**:
  - `age` + `BETWEEN` → `"19~34"` 형식 (양 끝 정수, min ≤ max).
  - `age` + `GTE`/`LTE` → 정수 1개.
  - `region` + `EQ` → 시도 코드 또는 법정동 코드.
  - `region` + `IN` → 콤마 분리 시도 코드.
  - `incomeMin`/`incomeMax`/`annualIncome` → 양의 정수.
  - `maritalStatus` + `EQ`/`NOT_EQ` → `MaritalStatus` enum 값.
  - `employmentKind`/`education`/`majorField`/`specializationField` → 해당 enum 값.
  - `IN` 오퍼레이터 → 콤마 분리, 모두 enum 값.
- **합리성 검증**: `age` 범위 0~100, 소득 컬럼 음수 거부.
- **confidence 보정**: `HIGH`/`MEDIUM`/`LOW` 외 값 → `MEDIUM` 기본값. 이건 검증 실패가 아닌 보정.

처리:
- 위반 항목은 폐기 (해당 룰만). 폐기 메시지를 `feedbackMessages`로 모아서 반환.
- **재시도 트리거 정책**: 살아남은 룰이 LLM 응답 룰의 50% 미만일 때만 재시도. 그 외엔 살아남은 룰만 사용.

### 5.3 포트 / 클라이언트

```java
// application/port
public interface EligibilityRuleLlmProvider {
    List<RawExtractedRule> extractRules(EligibilityRuleExtractionInput input);
    List<RawExtractedRule> regenerateWithFeedback(
            EligibilityRuleExtractionInput input,
            List<String> feedbackMessages);
}
```

`RawExtractedRule`은 `eligibility/application/dto/result/RawExtractedRule` (record)로 LLM 출력을 그대로 담는 검증 전 DTO. 필드: `field`, `operator`, `value`, `label`, `sourceReference`, `confidence` (모두 `String`). 검증·매핑을 거쳐 도메인 `EligibilityRule`로 변환된다.

`OpenAiEligibilityRuleClient` (infrastructure):
- OpenAI Chat API 호출. `response_format: json_object` 강제.
- `OpenAiEligibilityRuleProperties` (`@ConfigurationProperties("openai.eligibility-rule")`)로 모델/온도/timeouts 설정. 모델 기본 `gpt-4o-mini`, 온도 0.
- 실패는 `IllegalStateException`으로 application 레이어에 전달.

### 5.4 트리거 — `IngestionService` 한 줄 추가

`IngestionService`의 정책 upsert 정상 완료 + RAG 인덱싱 + guide 호출 직후:
```java
guideGenerationService.generateGuide(new GenerateGuideCommand(policyId, title, null));
ruleGenerationService.generateRules(new GenerateEligibilityRulesCommand(policyId));
```

`AttachmentReindexService`에도 동일하게 한 줄 추가 (첨부에 자격 정보가 있을 수 있어 첨부 재인덱싱 후에도 룰 재추출).

추출 실패는 ingest 실패로 전파하지 않음 — try-catch + 경고 로그. guide와 같은 정책.

### 5.5 사용자 판정 흐름의 변경 — `EligibilityEvaluator`

```
let raw = 기존 평가;
if (rule.confidence == LOW) return UNCERTAIN(rule, "근거가 모호함");
return raw;
```

- `CriterionEvaluation`에 `confidenceNote: String` 필드를 추가한다 (이번 스펙에 포함). LOW로 다운그레이드된 항목은 `"근거가 모호함"`, 그 외는 `null`. 이 값은 `CriterionResult` → `CriterionResponse`까지 그대로 흘려보낸다.
- `missingFields` 산정은 그대로 (UNCERTAIN으로 떨어진 항목 모두 포함).

## 6. LLM 입출력 스키마 / 프롬프트

### 6.1 입력

```java
record EligibilityRuleExtractionInput(
    Long policyId,
    String title,
    String summary,
    String supportTarget,
    String selectionCriteria,
    String supportContent,
    String body,
    List<ChunkInput> attachmentChunks   // source=ATTACHMENT 청크
) {
    String combinedSourceText() { /* [정책 메타]/[원문]/[원문 - 첨부 청크] 섹션 결합 */ }
}
```

### 6.2 출력 JSON 스키마

```json
{
  "rules": [
    {
      "field": "age",
      "operator": "BETWEEN",
      "value": "19~34",
      "label": "연령",
      "sourceReference": "자격 요건 > 연령: 만 19~34세",
      "confidence": "HIGH"
    }
  ]
}
```

`rules` 외 키 금지. 각 룰 객체의 필드도 위 7개로 고정.

### 6.3 시스템 프롬프트 (요약)

```
너는 한국 청년 정책의 자격 요건을 구조화된 JSON 룰로 추출하는 전문가다.

[작성 원칙]
1. 정보 통제: 입력된 원문에만 근거. 원문에 없는 조건/숫자/지역/고용/학력 추가 금지.
2. 출력 형식: { "rules": [...] } JSON 객체. rules 외 키 금지.
3. 필드 화이트리스트: age, region, incomeMin, incomeMax, annualIncome,
   maritalStatus, employmentKind, education, majorField, specializationField
4. 오퍼레이터 화이트리스트: EQ, NOT_EQ, GTE, LTE, IN, BETWEEN
5. 값 형식 규칙:
   - age + BETWEEN: "19~34" / age + GTE·LTE: 정수
   - region + EQ: 시도 코드 (SEOUL, BUSAN, ...) 또는 법정동 코드
   - incomeMin/Max/annualIncome: 연소득 원 단위 정수
   - maritalStatus + EQ/NOT_EQ: SINGLE | MARRIED
   - employmentKind: EMPLOYEE | SELF_EMPLOYED | UNEMPLOYED | FREELANCER
                    | DAILY_WORKER | ENTREPRENEUR | PART_TIME | FARMER | OTHER
   - education: UNDER_HIGH | HIGH_SCHOOL_IN | HIGH_SCHOOL_EXPECTED
                | HIGH_SCHOOL_GRAD | COLLEGE_IN | COLLEGE_EXPECTED
                | COLLEGE_GRAD | GRADUATE | OTHER
   - majorField/specializationField: 동일하게 enum 코드만 사용
6. 의미 매핑:
   - "청년" → age BETWEEN 19~34 (정책에 다른 연령 명시되면 그 값 우선)
   - "재직자/근로 청년" → employmentKind EMPLOYEE 또는 IN 으로 추론
7. confidence:
   - HIGH: 원문에 정확한 수치·단어 명시
   - MEDIUM: 합리적 추론
   - LOW: 모호하거나 추론 폭이 큼
8. 추출 안 함 (스킵):
   - 가구 형태 / 무주택 / 세대주 / 자가 보유 — 평가기 미지원
   - 부양가족 수 / 가구원 수 — 평가기 미지원
   - 신용등급 / 연체 이력 — 평가기 미지원
   화이트리스트 외 모든 자격 조건은 룰로 만들지 않는다.
9. label: 한국어 표시명 1~10자.
10. sourceReference: 원문에서 인용한 자격 조건 구절 1줄 (50자 내외).
11. 룰이 추출되지 않으면 빈 배열 반환. 빈 룰을 만들어내지 않는다.
12. 어조: 명사형/단정형. 친근체 금지.
```

### 6.4 사용자 프롬프트 템플릿

```
[정책 메타]
policyId: {id}
title: {title}
summary: {summary}

[원문 - 지원 대상]
{supportTarget}

[원문 - 선정 기준]
{selectionCriteria}

[원문 - 지원 내용]
{supportContent}

[원문 - 본문]
{body}

[원문 - 첨부 청크]
{chunks}
```

### 6.5 재시도 프롬프트

```
이전 응답에서 검증 위반:
- {feedbackMessages 줄단위}

위 위반을 모두 해결한 새 응답을 동일 JSON 스키마로 출력하라.
```

## 7. 에러 처리 정책

| 시나리오 | 처리 |
|---|---|
| `CostGuard.allows == false` | skip + INFO 로그. 정상 흐름. |
| `policy not found` | WARN 로그 + return early. |
| OpenAI 호출 타임아웃/오류 | `IllegalStateException` 잡기 → ERROR 로그. **기존 룰 유지** (delete-and-insert 안 함). 다음 ingest 때 재시도. |
| 응답 JSON 파싱 실패 | ERROR 로그 + return failure. 기존 룰 유지. |
| 살아남은 룰이 LLM 응답 룰의 50% 미만 | 1회 재시도. 2차도 동일하면 위반 수 감소 시 2차 사용, 아니면 1차 사용. |
| 1차/2차 모두 빈 배열 | 정상 간주. `deleteAllByPolicyId` + 빈 묶음 (DB 룰 0개). 평가 시 `LIKELY_ELIGIBLE` (룰 없음 = 자격 제한 없음으로 해석). |
| `IngestionService`에서 `generateRules` throw | try-catch 격리. ingest 트랜잭션은 성공 처리. |

## 8. 운영 / 관측

- **로그**: 주요 분기마다 INFO/WARN/ERROR 한 줄 (policyId, ruleCount, retried, violations).
- **CostGuard allowlist**: `application.yml`의 기존 `cost-guard.policy-ids` 그대로 재사용. 별도 키 추가 안 함.
- **수동 재추출**: v0에서는 별도 관리 API 없음. `IngestionController`의 기존 정책 재처리 트리거 또는 `AttachmentReindexService`로 자연 재호출. 필요해지면 다음 사이클에 추가.
- **메트릭**: 별도 Micrometer 카운터 없음. 로그로 충분.

## 9. 테스트 전략

### 9.1 단위 테스트

- `EligibilityRuleValidatorTest`
  - 필드 화이트리스트 통과/폐기
  - 오퍼레이터 화이트리스트 통과/폐기
  - 값 형식: age BETWEEN 형식, 음수 income 거부, enum 매칭, IN 콤마 분리
  - confidence 비정상 값 → MEDIUM 보정
  - 재시도 트리거: 50% 미만 잔존 케이스
- `EligibilityEvaluatorTest` (기존 + 신규)
  - LOW 룰 매칭/비매칭 모두 → UNCERTAIN
  - HIGH/MEDIUM 기존 동작 유지
- `EligibilityRuleGenerationServiceTest` (Mockito)
  - sourceHash 동일 → LLM 호출 안 함
  - LLM 정상 → delete-and-insert
  - LLM 응답 검증 위반 → regenerate 1회 호출
  - LLM 예외 → 기존 룰 유지, 실패 결과 반환
  - costGuard skip → LLM 호출 안 함

### 9.2 통합 테스트

`EligibilityControllerIntegrationTest` 신규 케이스:
- LOW 1개 + HIGH 1개 시드 → 최종 결과 UNCERTAIN
- 룰 0개 정책 → LIKELY_ELIGIBLE + criteria 빈 배열

### 9.3 LLM 라이브 테스트 제외

`OpenAiEligibilityRuleClient`는 외부 API 의존. guide 클라이언트와 같이 라이브 테스트 없음. 포트 모킹으로 응용 서비스만 검증.

### 9.4 테스트 데이터

- `EligibilityRule.builder()` 픽스처 헬퍼 1개 (`RuleConfidence` 기본 MEDIUM, override 옵션).
- `EligibilityProfile.empty(userId)` 기존 팩토리 활용.

## 10. 마이그레이션 / 배포

1. **DB 마이그레이션**: `eligibility_rule` 컬럼 3개 ADD (NOT NULL default `MEDIUM` 등), 기존 데이터 자동 백필.
2. **코드 배포**: 서비스/검증기/포트/클라이언트 + ingestion 호출 한 줄 추가.
3. **운영 확인**: allowlist 정책 1~2개로 ingest 수동 트리거 → DB의 `eligibility_rule` 조회 → 룰 품질 육안 검수.
4. **롤백**: ingestion의 `generateRules(...)` 한 줄 주석 처리 (DB 변경은 무해 — 추가 컬럼 모두 기본값).

## 11. Out-of-scope

- 관리자 룰 검수 페이지 — 별도 스펙. v0 범위 외.
- 룰 추출 결과 품질 메트릭 대시보드 — 운영 데이터 보고 결정.
- 평가기 미지원 필드(가구 형태/무주택 등) 추가 — 별도 스펙. 도메인 모델 확장 필요.
- 적합도 결과의 사용자 노출 UI 개선 — A 스펙 (별도).
- 적합도 기반 추천 진입점 — B 스펙 (별도).
