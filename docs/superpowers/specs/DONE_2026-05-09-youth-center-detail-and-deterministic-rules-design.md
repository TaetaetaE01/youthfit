# 온통청년 정책 상세 보강 + Deterministic 자격 룰 추출 — 설계

## 1. 목표

온통청년(`getPlcy`) API 응답에 이미 포함된 풍부한 정보를 시스템이 흘려보내지 않도록 보강한다. 두 가지 트랙을 한 번에 처리한다.

- **트랙 B — 정책 상세 정보 보강**: 응답의 11개 텍스트/구조화 필드를 `policy` 컬럼으로 분리 저장하고, 정책 상세 페이지에 별도 카드 섹션으로 노출한다. `aplyUrlAddr`을 별도 CTA 버튼으로 분리한다.
- **트랙 A — Deterministic 자격 룰 추출**: 응답의 코드 필드(mrgSttsCd, jobCd, schoolCd, plcyMajorCd, sbizCd, earnCndSeCd, sprtTrgtMin/MaxAge 등)를 백엔드로 직접 전달해 LLM 없이 8개 카테고리 룰을 항상 생성한다. "제한없음"은 신규 `RuleOperator.ANY`로 매핑해 적합도 화면에서 통과(✅)로 표시한다.

## 2. 배경

### 현황 (PR #82 직후)

- 온통청년 워크플로우(`youth-center-seoul.json`)는 코드 기반 필드(mrgSttsCd, jobCd 등)와 텍스트 필드(srngMthdCn, sbmsnDcmntCn 등)를 모두 **본문 텍스트(body)에 합쳐 넣음**. 구조화된 출력 없음.
- `IngestPolicyRequest.RawData`도 body 외 추가 텍스트/코드 필드를 받지 않음.
- `EligibilityRuleGenerationService`는 모든 정책에 대해 비동기로 OpenAI gpt-4o-mini를 호출해 룰 추출. 코드가 깔끔하게 와있는 정책에도 LLM에 텍스트로 보내 추론하라고 시킴.
- "모두의 창업"처럼 모든 코드가 "제한없음"인 정책은 LLM이 0개 룰 반환 → 적합도 화면이 비어있음.

### "정보 부실"의 진짜 원인

조사 결과 (이 spec 작성 시 기준):
- **온통청년 자체 detail 페이지** (`youthcenter.go.kr/.../youngPlcyUnifDtl.do`): 외부 요청 시 무조건 302 → port 8080(차단) 리다이렉트. 헤드리스 브라우저 + 세션 쿠키 없이는 스크래핑 불가.
- **운영기관 사이트** (aplyUrlAddr/refUrlAddr): 10+ 도메인 분산(bokjiro, k-startup, kinfa, usc 등). 일괄 스크래핑 비현실적.
- **getPlcy 응답 자체**: `plcySprtCn`, `plcyAplyMthdCn`, `srngMthdCn`, `sbmsnDcmntCn`, `addAplyQlfcCndCn`, `etcMttrCn`, `ptcpPrpTrgtCn`, `bizPrdEtcCn` 등 풍부한 텍스트가 **이미 포함됨**. 우리가 활용 안 했을 뿐.

따라서 외부 스크래핑은 일체 시도하지 않고, getPlcy 응답 활용도를 최대화하는 방향으로 진행.

## 3. 스코프

### 포함

- `policy` 테이블에 11개 컬럼 추가
- 워크플로우 transform 노드 jsCode 변경 (신규 필드 + rawCodes 출력)
- `IngestPolicyRequest.RawData` 확장 + 신규 `RawCodes` 중첩 record
- `IngestPolicyCommand` / `RegisterPolicyCommand` / `Policy` 엔티티 / `PolicyIngestionService` / `IngestionService` 확장
- `RuleOperator.ANY` enum 추가
- `CodeBasedRuleExtractor` (도메인 서비스) + `CodeBasedRuleExtractionService` (애플리케이션 서비스) 신규
- `EligibilityEvaluator`에 ANY 케이스 추가
- `EligibilityRuleGenerationEventListener` 가드 추가 (code-v1 존재 시 LLM 스킵)
- 프론트 정책 상세에 신규 카드 섹션 6-7개 + 공식 신청 CTA 버튼
- 프론트 적합도 결과 화면에서 ANY 룰을 ✅로 표시
- `PolicyResponse` (백엔드/프론트) 신규 필드 직렬화

### 제외 (별도 후속 슬라이스)

- BOKJIRO 워크플로우/정책에 같은 컬럼 백필
- 어드민 화면에서 신규 컬럼/룰 노출
- 외부 사이트 스크래핑(어떤 도메인이든)
- region 룰 평가기의 legalDongCode ↔ 시도 enum 정합성 (현재 잠재 이슈 그대로 유지)
- 운영 모니터링 대시보드 갱신
- 기존 LLM 추출 프롬프트/검증기 변경

## 4. 전체 아키텍처

```
┌─────────────────┐         ┌───────────────────────────────────┐
│ getPlcy 응답    │──────►  │ n8n transform                     │
│ (이미 풍부함)   │         │  · 11개 텍스트 필드 분리 추출     │
│                 │         │  · rawCodes 객체 생성             │
│                 │         │  · body 합성은 그대로             │
└─────────────────┘         └───────────────────────────────────┘
                                          │
                                          ▼ POST /api/internal/ingestion/policies
                            ┌───────────────────────────────────┐
                            │ IngestPolicyRequest.RawData       │
                            │  · 신규 11개 필드                 │
                            │  · 신규 RawCodes (중첩)           │
                            └───────────────────────────────────┘
                                          │
                            ┌─────────────┼─────────────────────┐
                            ▼             ▼                     ▼
                     ┌─────────────┐  ┌─────────────────┐ ┌──────────────────────────┐
                     │ Policy      │  │ CodeBased       │ │ 기존 LLM 룰 추출 흐름   │
                     │ + 11 컬럼   │  │ RuleExtractor   │ │ (rawCodes 없거나         │
                     │             │  │ (동기 호출)     │ │  code-v1 없을 때만)      │
                     └─────────────┘  └─────────────────┘ └──────────────────────────┘
                                          │
                                          ▼
                                 EligibilityRule 8개
                                  + RuleOperator.ANY
                                  + extractionVersion="code-v1"
```

### 모듈별 변경 요약

| 모듈 | 변경 |
|---|---|
| `n8n/workflows/` | `youth-center-seoul.json` transform 노드 jsCode 갱신 |
| `ingestion` | DTO/Command 확장, `IngestionService.receivePolicy`에서 deterministic 추출 동기 호출 |
| `policy` | `Policy` 엔티티 + 11개 컬럼, `RegisterPolicyCommand` / `PolicyIngestionService` 매핑 |
| `eligibility` | `RuleOperator.ANY`, `CodeBasedRuleExtractor`, `CodeBasedRuleExtractionService`, `EligibilityEvaluator` ANY 분기, listener 가드 |
| `frontend` | 정책 상세 카드 섹션, ANY 룰 표시, 응답 타입 확장 |

### 사용자 가시 효과

- 온통청년 정책 상세 화면이 카드 섹션으로 분해되어 풍부해짐
- 적합도 화면에서 온통청년 정책은 항상 8개 카테고리 룰이 보임 ("연령 무관" 같은 통과 표시 포함)
- 기존 BOKJIRO 정책은 변화 없음

## 5. 스키마 변경

### 마이그레이션 SQL

`backend/src/main/resources/sql/2026-05-09-youth-center-detail-fields.sql`:

```sql
ALTER TABLE policy
  ADD COLUMN screening_method TEXT,
  ADD COLUMN submission_documents TEXT,
  ADD COLUMN additional_qualification TEXT,
  ADD COLUMN participation_restriction TEXT,
  ADD COLUMN additional_notes TEXT,
  ADD COLUMN business_period_start DATE,
  ADD COLUMN business_period_end DATE,
  ADD COLUMN business_period_note TEXT,
  ADD COLUMN support_scale INTEGER,
  ADD COLUMN first_come_first_served BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN apply_url VARCHAR(500);
```

비파괴적 — 모두 nullable 또는 default. 기존 row는 자동으로 `first_come_first_served=false`, 나머지 NULL.

### 컬럼 ↔ getPlcy 응답 필드 매핑

| 신규 컬럼 | 타입 | getPlcy 필드 | 의미 |
|---|---|---|---|
| `screening_method` | TEXT | `srngMthdCn` | 심사방법 (다단계 절차) |
| `submission_documents` | TEXT | `sbmsnDcmntCn` | 제출서류 |
| `additional_qualification` | TEXT | `addAplyQlfcCndCn` | 추가 자격조건 (가구소득/연령 상세) |
| `participation_restriction` | TEXT | `ptcpPrpTrgtCn` | 참여 제한 대상 |
| `additional_notes` | TEXT | `etcMttrCn` | 기타사항 |
| `business_period_start` | DATE | `bizPrdBgngYmd` (parsed) | 사업기간 시작 |
| `business_period_end` | DATE | `bizPrdEndYmd` (parsed) | 사업기간 종료 |
| `business_period_note` | TEXT | `bizPrdEtcCn` | "상시" 등 부가설명 |
| `support_scale` | INT | `sprtSclCnt` (parsed, 0 → NULL) | 지원규모(명) |
| `first_come_first_served` | BOOL | `sprtArvlSeqYn = 'Y'` | 선착순 여부 |
| `apply_url` | VARCHAR(500) | `aplyUrlAddr` | 공식 신청 페이지 URL |

### body 처리 — 변경 없음

기존 body 합성은 그대로 유지. 신규 컬럼은 추가 데이터일 뿐, body 텍스트로의 노출도 그대로 둠 (검색·LLM·RAG가 body 기반이라 호환성 유지).

### Hibernate ddl-auto 정책

- 운영: `validate` (스키마 일치 강제) → 마이그레이션 SQL 사전 적용 필수
- 로컬: `update` (자동 추가)
- 신규 컬럼 모두 단순 타입(TEXT, DATE, INTEGER, BOOLEAN, VARCHAR) — Hibernate가 올바르게 생성

## 6. 워크플로우 transform 변경

`n8n/workflows/youth-center-seoul.json`의 `정책 → IngestPolicyRequest 변환` 노드 jsCode를 갱신.

### 추가 출력 필드

기존 `result.rawData` 객체에 다음 필드 추가:

```js
{
  // ... (기존 필드 유지) ...

  // ── 신규 텍스트 필드 ──
  screeningMethod: clean(p.srngMthdCn) || null,
  submissionDocuments: clean(p.sbmsnDcmntCn) || null,
  additionalQualification: clean(p.addAplyQlfcCndCn) || null,
  participationRestriction: clean(p.ptcpPrpTrgtCn) || null,
  additionalNotes: clean(p.etcMttrCn) || null,

  // ── 신규 사업기간 ──
  businessPeriodStart: parseYmd(p.bizPrdBgngYmd),  // "20260101" → "2026-01-01"
  businessPeriodEnd: parseYmd(p.bizPrdEndYmd),
  businessPeriodNote: clean(p.bizPrdEtcCn) || null,

  // ── 신규 지원규모/선착순 ──
  supportScale: parseIntOrNull(p.sprtSclCnt),       // "0" → null
  firstComeFirstServed: p.sprtArvlSeqYn === 'Y',

  // ── 신규 신청 URL (CTA) ──
  applyUrl: clean(p.aplyUrlAddr) || null,

  // ── 신규 rawCodes (deterministic rule용) ──
  rawCodes: {
    ageMin: parseIntOrNull(p.sprtTrgtMinAge),
    ageMax: parseIntOrNull(p.sprtTrgtMaxAge),
    ageLimitYn: p.sprtTrgtAgeLmtYn || null,
    maritalStatusCd: p.mrgSttsCd || null,
    earnConditionCd: p.earnCndSeCd || null,
    earnMin: parseIntOrNull(p.earnMinAmt),
    earnMax: parseIntOrNull(p.earnMaxAmt),
    earnEtcCn: clean(p.earnEtcCn) || null,
    employmentKindCd: p.jobCd || null,
    educationCd: p.schoolCd || null,
    majorFieldCd: p.plcyMajorCd || null,
    specializationCd: p.sbizCd || null,
    zipCodes: splitTokens(p.zipCd, ',')
  }
}
```

### 헬퍼 함수 추가

```js
function parseIntOrNull(s) {
  if (s == null || String(s).trim() === '') return null;
  const n = parseInt(String(s).trim(), 10);
  if (isNaN(n) || n === 0) return null;
  return n;
}

function parseYmd(s) {
  if (!s) return null;
  const cleaned = String(s).trim();
  if (cleaned.length !== 8) return null;
  return `${cleaned.slice(0,4)}-${cleaned.slice(4,6)}-${cleaned.slice(6,8)}`;
}
```

기존 `clean`/`splitTokens` 헬퍼는 재사용.

### referenceSites 처리 변경

```js
// BEFORE
if (clean(p.aplyUrlAddr)) referenceSites.push({ name: '신청 페이지', url: clean(p.aplyUrlAddr) });
if (clean(p.refUrlAddr1)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr1) });
if (clean(p.refUrlAddr2)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr2) });

// AFTER
if (clean(p.refUrlAddr1)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr1) });
if (clean(p.refUrlAddr2)) referenceSites.push({ name: '참고 사이트', url: clean(p.refUrlAddr2) });
```

`aplyUrlAddr`은 `applyUrl` 컬럼으로 분리되므로 referenceSites에서 제외.

### body 합성 — 그대로

기존 `buildBody(p)` 함수는 변경하지 않음. 동일 정보가 두 군데(body 텍스트 + 컬럼)에 있어도 허용 — body는 fulltext용, 컬럼은 UI 카드용.

### 워크플로우 코드 사전 — 그대로

기존 transform의 `CODE` 객체는 body 합성에서만 쓰이고, rawCodes는 raw code 그대로 전송. 코드 사전의 단일 진실 공급원은 백엔드 `CodeBasedRuleExtractor`.

## 7. 백엔드 DTO/Command/Service 변경

### 7.1 `IngestPolicyRequest.RawData` 확장

`backend/src/main/java/com/youthfit/ingestion/presentation/dto/request/IngestPolicyRequest.java`:

```java
public record RawData(
    // ... (기존 필드) ...

    // 신규 11개 필드 (모두 optional):
    String screeningMethod,
    String submissionDocuments,
    String additionalQualification,
    String participationRestriction,
    String additionalNotes,
    LocalDate businessPeriodStart,
    LocalDate businessPeriodEnd,
    String businessPeriodNote,
    Integer supportScale,
    Boolean firstComeFirstServed,
    String applyUrl,
    @Valid RawCodes rawCodes
) {}

public record RawCodes(
    Integer ageMin,
    Integer ageMax,
    String ageLimitYn,           // "Y" or "N"
    String maritalStatusCd,      // 0055001..0055003
    String earnConditionCd,      // 0043001..0043003
    Integer earnMin,
    Integer earnMax,
    String earnEtcCn,
    String employmentKindCd,     // 0013001..0013010
    String educationCd,          // 0049001..0049010
    String majorFieldCd,         // 0011001..0011009
    String specializationCd,     // 0014001..0014010
    List<String> zipCodes
) {}
```

`toCommand()`도 확장.

### 7.2 `IngestPolicyCommand` 확장

`IngestPolicyRequest.RawData`와 동일 필드 (rawCodes 포함). 중첩 record `RawCodes` 한 군데에 정의 (`presentation` → `application`)이지만, 본 spec에서는 순환 의존 방지를 위해 `application/dto/command/IngestPolicyCommand.RawCodes`로 정의하고 `IngestPolicyRequest.RawCodes`는 같은 모양의 record를 별도 두고 `toCommand()`에서 변환.

### 7.3 `RegisterPolicyCommand` 확장 — rawCodes 제외

`rawCodes`는 ingestion-time 관심사이므로 policy 도메인까지 전달하지 않음. `RegisterPolicyCommand`에는 11개 텍스트/구조화 필드만 추가.

```java
public record RegisterPolicyCommand(
    // ... (기존 필드) ...

    String screeningMethod,
    String submissionDocuments,
    String additionalQualification,
    String participationRestriction,
    String additionalNotes,
    LocalDate businessPeriodStart,
    LocalDate businessPeriodEnd,
    String businessPeriodNote,
    Integer supportScale,
    boolean firstComeFirstServed,
    String applyUrl
) {}
```

### 7.4 `Policy` 엔티티 확장

`backend/src/main/java/com/youthfit/policy/domain/model/Policy.java`:

```java
@Column(columnDefinition = "TEXT")
private String screeningMethod;

@Column(columnDefinition = "TEXT")
private String submissionDocuments;

@Column(columnDefinition = "TEXT")
private String additionalQualification;

@Column(columnDefinition = "TEXT")
private String participationRestriction;

@Column(columnDefinition = "TEXT")
private String additionalNotes;

private LocalDate businessPeriodStart;
private LocalDate businessPeriodEnd;

@Column(columnDefinition = "TEXT")
private String businessPeriodNote;

private Integer supportScale;

@Column(nullable = false)
private boolean firstComeFirstServed;

@Column(length = 500)
private String applyUrl;
```

기존 `updateInfo(...)` 도메인 메서드 시그니처에 11개 파라미터 추가, 빌더에도 동일하게.

### 7.5 `PolicyIngestionService.registerPolicy` 확장

신규 정책 생성 시 빌더에 채우고, 기존 정책 업데이트 시 `policy.updateInfo(...)` 호출에 11개 인자 추가.

### 7.6 `IngestionService.receivePolicy` 확장 — deterministic 추출 트리거

```java
PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
duplicate = ingestionResult.outcome() != Outcome.REGISTERED;

if (ingestionResult.outcome() == Outcome.SKIPPED_DUPLICATE) {
    return new IngestPolicyResult(UUID.randomUUID(), "SKIPPED_DUPLICATE");
}

// ── 신규: rawCodes 있으면 deterministic 추출 동기 호출 ──
if (command.rawCodes() != null) {
    codeBasedRuleExtractionService.extractAndPersist(
        ingestionResult.policyId(), command.rawCodes());
}

eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
triggerAttachmentDownload(ingestionResult.policyId());
return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

### 7.7 `EligibilityRuleGenerationEventListener` 가드

```java
List<EligibilityRule> existing = ruleRepository.findAllByPolicyId(policyId);
if (existing.stream().anyMatch(r -> "code-v1".equals(r.getExtractionVersion()))) {
    log.info("deterministic 룰 존재, LLM 추출 스킵: policyId={}", policyId);
    return;
}
// ... 기존 로직
```

흐름 정리:
- YOUTH_CENTER 정책 → deterministic 룰 → LLM 스킵 ✅
- BOKJIRO 정책 → rawCodes null → deterministic 스킵 → LLM 동작 (기존 그대로) ✅
- 같은 정책 재ingest → deterministic 재추출(DELETE+INSERT) → LLM 스킵 ✅

## 8. CodeBasedRuleExtractor 설계 (트랙 A 핵심)

### 8.1 레이어 분리

```
eligibility/
├── domain/
│   ├── model/
│   │   └── RuleOperator.java                # +ANY 추가
│   └── service/
│       ├── EligibilityEvaluator.java        # +ANY 케이스
│       └── CodeBasedRuleExtractor.java      # 신규 (순수 매핑)
└── application/
    └── service/
        └── CodeBasedRuleExtractionService.java  # 신규 (DELETE+INSERT)
```

- **Domain service**: 순수 함수. 입력=rawCodes 값 객체, 출력=`List<EligibilityRule>`. 프레임워크/DB 없음.
- **Application service**: `@Transactional`로 `ruleRepository.deleteAllByPolicyId` + `saveAll`.

### 8.2 `RuleOperator.ANY` 추가

```java
public enum RuleOperator { EQ, GTE, LTE, IN, BETWEEN, NOT_EQ, ANY }
```

`EligibilityEvaluator.evaluateRule`에서 ANY는 null 체크 전에 처리:

```java
if (rule.getOperator() == RuleOperator.ANY) {
    return CriterionEvaluation.eligible(rule, userValue);  // userValue null 이어도 PASS
}
if (userValue == null) {
    return CriterionEvaluation.uncertain(rule);
}
// ... 기존 로직
```

### 8.3 8개 카테고리 매핑 규칙

각 정책마다 **반드시 8개 룰 생성** (제한없음·null도 ANY로). 모든 룰의 `confidence = HIGH`, `extractionVersion = "code-v1"`.

#### age (연령)
| 조건 | operator | value |
|---|---|---|
| `ageLimitYn = "N"` | ANY | "ALL" |
| ageMin>0 AND ageMax>0 | BETWEEN | `"19~34"` |
| ageMin>0 only | GTE | `"19"` |
| ageMax>0 only | LTE | `"34"` |
| 모두 null | ANY | "ALL" |

#### maritalStatus (결혼상태) — mrgSttsCd
| 코드 | operator | value |
|---|---|---|
| 0055001 | EQ | MARRIED |
| 0055002 | EQ | SINGLE |
| 0055003 / null | ANY | "ALL" |

#### annualIncome (연소득) — earnConditionCd + earnMin/Max
| 조건 | operator | value | sourceReference |
|---|---|---|---|
| 0043001(무관) / null | ANY | "ALL" | "getPlcy.earnCndSeCd: 0043001" |
| 0043002 + min>0 + max>0 | BETWEEN | `"0~32000000"` | "getPlcy.earnMaxAmt: 32000000" |
| 0043002 + max>0 only | LTE | `"32000000"` | 동일 |
| 0043003(기타) | ANY | "ALL" | `"getPlcy.earnEtcCn: ${earnEtcCn 첫 100자}"` |

#### employmentKind (취업상태) — jobCd
| 코드 | operator | value |
|---|---|---|
| 0013010 / 0013009 / null | ANY | "ALL" |
| 0013001..0013008 | EQ | EMPLOYEE / SELF_EMPLOYED / UNEMPLOYED / FREELANCER / DAILY_WORKER / ENTREPRENEUR / PART_TIME / FARMER |

#### education (학력) — schoolCd
| 코드 | operator | value |
|---|---|---|
| 0049010 / 0049009 / null | ANY | "ALL" |
| 0049001..0049008 | EQ | UNDER_HIGH / HIGH_SCHOOL_IN / HIGH_SCHOOL_EXPECTED / HIGH_SCHOOL_GRAD / COLLEGE_IN / COLLEGE_EXPECTED / COLLEGE_GRAD / GRADUATE |

#### majorField (전공) — plcyMajorCd
| 코드 | operator | value |
|---|---|---|
| 0011009 / 0011008 / null | ANY | "ALL" |
| 0011001..0011007 | EQ | HUMANITIES / SOCIAL / ECONOMICS / NATURAL / ENGINEERING / ARTS / AGRICULTURE |

#### specializationField (특화요건) — sbizCd
| 코드 | operator | value |
|---|---|---|
| 0014010 / 0014009 / null | ANY | "ALL" |
| 0014001..0014008 | EQ | SME / WOMAN / BASIC_LIVELIHOOD / SINGLE_PARENT / DISABLED / FARMER / MILITARY / LOCAL_TALENT |

#### region (지역) — zipCodes
zipCode 첫 2자리(시도 prefix) 수집 → 시도 enum 변환:
- 11→SEOUL, 26→BUSAN, 27→DAEGU, 28→INCHEON, 29→GWANGJU, 30→DAEJEON, 31→ULSAN, 36→SEJONG, 41→GYEONGGI, 51→GANGWON, 43→CHUNGBUK, 44→CHUNGNAM, 52→JEONBUK, 46→JEONNAM, 47→GYEONGBUK, 48→GYEONGNAM, 50→JEJU
- (구 코드 호환: 42→GANGWON, 45→JEONBUK)

| 조건 | operator | value |
|---|---|---|
| empty / null | ANY | "ALL" |
| 시도 1개 | EQ | "SEOUL" |
| 시도 2~16개 | IN | "SEOUL,BUSAN,..." |
| 시도 17개 모두 | ANY | "ALL" |

알 수 없는 prefix는 무시 (skipped). region 룰 평가기의 legalDongCode ↔ 시도 enum 매칭 정합성은 본 spec 범위 외 (현재 잠재 이슈 그대로).

### 8.4 출력 EligibilityRule 공통 필드

```java
EligibilityRule.builder()
    .policyId(policyId)
    .field(...)         // age, region, annualIncome, maritalStatus, employmentKind, education, majorField, specializationField
    .operator(...)
    .value(...)
    .label(...)         // 연령, 거주지, 연소득, 결혼상태, 취업상태, 학력, 전공, 특화요건
    .sourceReference("getPlcy.<field>: <code>")
    .confidence(RuleConfidence.HIGH)
    .sourceHash(<hash 동일 로직 재사용>)
    .extractionVersion("code-v1")
    .build();
```

### 8.5 코드 사전 단일 진실 공급원

코드 → enum 매핑은 `CodeBasedRuleExtractor` 내부 private static `Map<String, String>`으로 한 곳에만 둠. 워크플로우 transform의 `CODE` 객체는 body 텍스트 합성 전용.

## 9. 프론트엔드 정책 상세 변경

### 9.1 신규 카드 섹션

값이 null/blank/`-`/`해당사항 없음`이면 카드 자체 숨김.

```
┌─────────────────────────────────────────┐
│ 정책 제목 / 카테고리 뱃지               │
├─────────────────────────────────────────┤
│ 요약 (summary)                          │
├─────────────────────────────────────────┤
│ ▶ 공식 신청 페이지로 이동      ← 신규   │  applyUrl 있을 때만, primary CTA
├─────────────────────────────────────────┤
│ 사업기간: 2026-01-01 ~ 12-31           │  businessPeriodStart/End
│ 지원규모: 25명 · 선착순         ← 신규  │  supportScale + firstComeFirstServed
├─────────────────────────────────────────┤
│ [지원대상] (기존)                       │
│ [추가 자격조건]                  ← 신규 │  additionalQualification
│ [참여 제한 대상] ⚠              ← 신규  │  participationRestriction
├─────────────────────────────────────────┤
│ [선정기준] (기존)                       │
│ [심사방법] (단계별 번호)         ← 신규 │  screeningMethod
├─────────────────────────────────────────┤
│ [지원내용] (기존)                       │
│ [제출서류] (목록)                ← 신규 │  submissionDocuments
│ [기타사항] (이탤릭)              ← 신규 │  additionalNotes
├─────────────────────────────────────────┤
│ 신청방법 / 첨부파일 / 참고 사이트 (기존)│
└─────────────────────────────────────────┘
```

### 9.2 CTA 버튼 — `applyUrl`

- primary 톤 (브랜드 컬러), 화살표 아이콘
- "공식 신청 페이지로 이동 →" 라벨
- 새 탭으로 열기: `target="_blank" rel="noopener noreferrer"`
- applyUrl 없으면 버튼 숨김

### 9.3 적합도 결과 화면 — ANY 룰 표시

기존: ✅ / ❌ / ❓ 셋 중 하나
변경 후 (ANY 룰):

```
┌──────────────────┐
│ ✅ 연령 무관       │
│   조건 없음 (통과) │
└──────────────────┘
```

사용자가 프로필을 안 채워도 (예: 학력 미입력) ANY 룰은 항상 ✅로 보임. ❓("미확정") 카드 수가 줄어 결과가 깔끔해짐.

### 9.4 색·톤

| 카드 | 톤 |
|---|---|
| 사업기간 / 지원규모 | 정보 (회색·중립) |
| 추가 자격조건 | 기본 |
| 참여 제한 대상 | 경고 (옅은 노랑·아이콘 ⚠) |
| 심사방법 | 기본 (단계별 1, 2, 3 prefix) |
| 제출서류 | 기본 (개행 → 불릿) |
| 기타사항 | 보조 (이탤릭, 옅은 회색) |

### 9.5 변경 파일 (예상)

- `frontend/src/pages/policy/PolicyDetailPage.tsx` (또는 비슷한 위치) — 카드 섹션 추가
- `frontend/src/pages/eligibility/...` — ANY 룰 표시 분기
- 응답 타입 (`PolicyResponse`) — 11개 필드 추가
- 백엔드 `PolicyResponse` (DTO) — 동일 필드 추가

## 10. 마이그레이션 / 기존 데이터 처리

### 10.1 배포 순서 (운영)

1. SQL 마이그레이션 사전 적용 (psql) — `2026-05-09-youth-center-detail-fields.sql`
2. 백엔드 + 프론트 배포 (동시) — RuleOperator.ANY enum, CodeBasedRuleExtractor, listener 가드, 카드 섹션, CTA, ANY 표시
3. n8n 워크플로우 JSON 갱신 (test mode: lastPage=1) → 1페이지만 처리 → 백엔드 로그 확인
4. 검증 후 lastPage 풀 페이징 활성화 + cron 그대로 (매일 04:00)

### 10.2 기존 25개 YOUTH_CENTER 정책 백필

별도 부트스트랩 스크립트 불필요. 이유:
- 워크플로우 transform JS가 바뀌면 outbound payload 모양이 달라짐
- `IngestionService`는 payload 직렬화 → sha256으로 sourceHash 산출
- payload 모양 달라짐 → sourceHash 달라짐 → `policy_source.hasChanged=true` → `UPDATED` outcome
- `UPDATED`이면 `CodeBasedRuleExtractor` 동기 실행 → 신규 컬럼 채워짐 + 기존 LLM 룰(`v1`) 삭제 + code-v1 룰 저장
- listener는 code-v1 보고 LLM 스킵

**수동 워크플로우 재실행 1회로 모든 백필 완료.**

### 10.3 워크플로우 재실행 절차

```
1. n8n UI → "수동 실행 트리거" webhook 호출
   POST http://localhost:5678/webhook/youth-center-manual

2. 1페이지 (25건) 완료 대기 (~30초)

3. DB 검증:
   - SELECT COUNT(*) FROM policy WHERE first_come_first_served IS NOT NULL → ≥25
   - SELECT COUNT(*) FROM eligibility_rule WHERE extraction_version='code-v1' → ≥200 (25*8)
   - SELECT COUNT(*) FROM eligibility_rule
       WHERE policy_id IN (SELECT id FROM policy WHERE EXISTS
         (SELECT 1 FROM policy_source ps WHERE ps.policy_id=policy.id AND ps.source_type='YOUTH_CENTER'))
       AND extraction_version='v1' → 0

4. 정책 상세 페이지 한 건 열어 카드 섹션 노출 확인

5. 적합도 한 건 실행 → 8개 카테고리 모두 결과에 보이는지 확인
```

### 10.4 충돌 케이스

#### Bokjiro dedup으로 SKIPPED_DUPLICATE
YOUTH_CENTER ingest인데 같은 normalized_title의 BOKJIRO 정책이 이미 있어서 dedup된 케이스 (PR #82에서 도입된 동작). `outcome = SKIPPED_DUPLICATE` → deterministic 추출 안 함 → BOKJIRO 정책의 LLM 룰 유지. 의도된 동작.

#### 동일 정책 두 번 호출 (idempotency)
- 1차: REGISTERED → code-v1 룰 8개
- 2차: 같은 hash → SKIPPED_DUPLICATE → 추출 안 함
- 다른 hash → UPDATED → DELETE+INSERT (idempotent)

#### LLM 추출이 deterministic 보다 먼저 끝나는 race
불가능. deterministic은 `IngestionService` 안 동기 호출 → 트랜잭션 commit 후 listener async 발화 → 항상 deterministic 우선.

### 10.5 롤백 전략

```sql
-- 1) deterministic 룰 제거 (RuleOperator.ANY enum 미지원 코드로 롤백 시 평가기 폭발 방지)
DELETE FROM eligibility_rule WHERE extraction_version = 'code-v1';

-- 2) policy_source hash 무효화 → 다음 ingest 때 LLM 추출 재발화
UPDATE policy_source SET source_hash = source_hash || '_invalid'
  WHERE source_type = 'YOUTH_CENTER';

-- 3) 신규 컬럼은 nullable이므로 ALTER 굳이 ROLLBACK 안 해도 무해
```

코드만 revert해도 신규 컬럼은 무시됨 (Hibernate validate 모드에서 컬럼 추가는 호환).

## 11. 테스트 전략

### 11.1 단위 테스트 (Java/JUnit5)

#### `CodeBasedRuleExtractorTest` (도메인 서비스, 핵심)

가장 중요. 8개 카테고리 × 분기가 곧 테스트 케이스.

| 카테고리 | 핵심 케이스 |
|---|---|
| age | (1) ageLimitYn=N → ANY, (2) min/max 둘 다 → BETWEEN, (3) min만 → GTE, (4) max만 → LTE, (5) 모두 null → ANY |
| maritalStatus | (1) 0055001 → EQ MARRIED, (2) 0055002 → EQ SINGLE, (3) 0055003 → ANY, (4) null → ANY |
| annualIncome | (1) 0043001(무관) → ANY, (2) 0043002 + min/max → BETWEEN, (3) 0043002 + max만 → LTE, (4) 0043003(기타) → ANY (sourceReference에 earnEtcCn 보존), (5) null → ANY |
| employmentKind | (1) 0013010 → ANY, (2) 0013009(기타) → ANY, (3) 0013001 → EQ EMPLOYEE, (4) null → ANY |
| education | 동일 패턴 |
| majorField | 동일 패턴 |
| specializationField | 동일 패턴 |
| region | (1) empty → ANY, (2) 11xxx만 → EQ SEOUL, (3) 11xxx+26xxx → IN SEOUL,BUSAN, (4) 17개 시도 모두 → ANY, (5) prefix 매핑 (51→GANGWON) |

공통 검증:
- 모든 룰의 `field`/`label` 정확
- `confidence = HIGH`
- `extractionVersion = "code-v1"`
- `sourceReference = "getPlcy.<field>: <code>"` 패턴
- 결과 룰 개수 = 항상 8개

#### `EligibilityEvaluatorTest` (확장)
- `evaluateRule(ANY rule, userValue=null)` → eligible (uncertain 아님)
- `evaluateRule(ANY rule, userValue=any)` → eligible
- 기존 케이스 회귀 없음

#### `CodeBasedRuleExtractionServiceTest` (애플리케이션)
- mock repository로 `deleteAllByPolicyId` + `saveAll` 호출 확인
- `@Transactional` 마킹 확인

#### `IngestionServiceTest` (확장)
- (1) rawCodes != null + REGISTERED → extractor 호출
- (2) rawCodes != null + UPDATED → extractor 호출
- (3) rawCodes != null + SKIPPED_DUPLICATE → extractor 호출 안 함
- (4) rawCodes = null → extractor 호출 안 함 (BOKJIRO 시나리오)
- (5) PolicyUpsertedEvent는 REGISTERED/UPDATED 시 발행됨

#### `EligibilityRuleGenerationEventListenerTest` (확장)
- code-v1 룰 존재 → LLM 호출 안 함 (mock OpenAI 미호출 검증)
- code-v1 없음 → LLM 호출 (기존)

#### `PolicyIngestionServiceTest` (확장)
- 신규 11개 필드가 빌더/updateInfo로 정상 전파
- 신규 정책 등록 시 컬럼 채워짐
- 기존 정책 업데이트 시 컬럼 갱신

### 11.2 통합 스모크 (수동)

PR #82와 동일하게 `/tmp/yc-smoke/run.mjs` 패턴 재사용:

```
1. 워크플로우 transform 노드 jsCode 추출
2. /tmp/yc-smoke/gangnam25.json 25건에 대해 transform 실행
3. 출력 검증: 11개 신규 필드 + rawCodes 13개 코드 모두 존재
4. 백엔드 POST → DB 검증:
   - policy 테이블 신규 컬럼 NULL 아닌 row 수
   - eligibility_rule extraction_version='code-v1' row 수 = 25 * 8 = 200
   - 각 정책당 8개 카테고리 모두 존재
5. 적합도 평가 (mock 사용자 프로필 1개) → 8개 결과 표시
```

### 11.3 수동 UI 검증

빌드/타입체크 후 정책 상세 페이지 1건 열어:
- 신규 카드 6-7개 노출 (값 있을 때)
- 빈 값 카드 숨김
- "공식 신청 페이지" CTA 버튼 동작
- 적합도 결과에서 ANY 룰이 ✅ 통과로 표시

### 11.4 회귀 검증

- BOKJIRO 정책 1건 ingest → 신규 컬럼 NULL, LLM 룰 정상 추출 (기존과 동일)
- BOKJIRO 정책 적합도 → 기존과 동일 결과
- 프론트 BOKJIRO 정책 상세 → 신규 카드 모두 숨김, 기존과 동일 모양

### 11.5 비커버리지 (의도적 미테스트)

- 워크플로우 JS의 라인 단위 단위 테스트 (n8n 환경 모킹 비용↑, smoke로 대체)
- 운영 DB 마이그레이션 자동화 (수동 적용)
- E2E 자동화 (자원 vs 가치 ROI 낮음)

## 12. 위험 및 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 시도 enum 정합성 (legalDongCode vs SEOUL/BUSAN) | region 룰이 사용자에게 부정확 결과 | 본 spec 범위 외, 별도 슬라이스 |
| Hibernate가 신규 컬럼을 잘못된 타입으로 자동 생성 (PR #82 normalized_title 사례) | 운영 validate 실패 | 마이그레이션 SQL을 코드 배포 전 적용 + 단순 타입(TEXT/DATE/INTEGER/BOOLEAN/VARCHAR)만 사용 |
| 워크플로우 재실행이 cron 시각과 겹침 | 같은 정책에 대한 동시 ingest | dedup으로 idempotent. 그러나 PR-time 검증 시 cron 일시 중단 권장 |
| 기존 25개 YOUTH_CENTER 정책 백필 누락 | NULL 컬럼 + v1 LLM 룰 잔존 | 워크플로우 수동 재실행 1회로 해결. SQL로 검증 |
| code-v1 룰이 잘못 추출되어 평가기에서 폭발 | 적합도 화면 깨짐 | 단위 테스트 + 스모크에서 사전 차단. 롤백 SQL 준비 |

## 13. 별도 후속 슬라이스 (참고)

이번 spec은 다음을 의도적으로 제외하지만 후속으로 처리:

1. **BOKJIRO 워크플로우 백필** — 복지로 응답에도 일부 필드(tgtrDtlCn → additionalQualification 비슷, slctCritCn → screeningMethod 비슷)가 있으므로 transform에서 매핑.
2. **자주 등장하는 외부 도메인의 dedicated parser** — bokjiro/k-startup 등 일부 사이트만 선별적 정보 보완.
3. **어드민 화면에 신규 컬럼/룰 노출** — 검수·운영 가시성.
4. **region 평가기 정합성** — legalDongCode와 시도 enum 매칭 로직 정리.
5. **deterministic 룰 vs LLM 룰 병행 비교 스크립트** — 효과 측정.
