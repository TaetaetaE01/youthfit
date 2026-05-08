# 온통청년 청년정책 통합검색 API 수집 설계

> **작성일**: 2026-05-08
> **모듈**: `ingestion` + `policy` + `n8n`
> **상태**: 설계 완료 / 사용자 검토 대기
> **연관 PRD**: `docs/prd/08-ingestion.md` (정정 대상)

---

## 1. 목적

청년정책 데이터 소스를 복지로 중앙부처(`BOKJIRO_CENTRAL`) 한 곳에서 **온통청년(`YOUTH_CENTER`) 청년정책 통합검색 API**까지 확장한다. 서울 단위 정책 커버리지를 보강하면서 복지로와의 동일 정책 중복은 차단한다.

- 외부 API: data.go.kr 서비스 ID 15128179, `https://www.youthcenter.go.kr/go/ythip/getPlcy`
- 응답 포맷: JSON
- 일일 호출량: 외부 API 약 26회 / 백엔드 intake 약 450~500회 (서울 필터 통과분)
- 스코프(v0): 서울특별시 + 25개 자치구

## 2. 배경 — 응답 검증 결과

PRD `08-ingestion.md`에 적힌 가정 중 일부는 실제 응답과 달랐다. 키 발급 후 직접 호출 검증으로 확인된 사실:

1. **`zipCd`는 콤마 구분 다중값**. 전국 정책은 행정코드 255개 전부, 서울 전용은 25개 자치구 코드 전부 들어간다.
2. **자치구별 호출 = 비효율**. 강남구(11680) 442건과 서울특별시(11000) 454건 응답 대부분이 동일 — 전국 정책이 모든 자치구 응답에 중복 노출. 26회 호출 시 같은 정책이 26번 백엔드로 보내진다.
3. **`lclsfNm`은 `･`(U+FF65) 구분 다중값**으로 등장 (`"금융･복지･문화"`, `"교육･직업훈련"`).
4. **신청기간은 `aplyYmd` 단일 문자열** (예: `"20260301 ~ 20260331"`). PRD가 가정한 `aplyYmdBgn/End` 별도 필드는 존재하지 않음. 게다가 절반 이상 빈 값(수시 정책).
5. **첨부파일 필드 없음**. `aplyUrlAddr`(신청 URL), `refUrlAddr1/2`(참고)만 있음. 복지로의 `basfrmList`(PDF/HWP)에 대응되는 필드 없음 → 첨부 다운로드 파이프 적용 불가.
6. **인코딩 이상 글자** (`᭼` U+1B3C — 원본은 `·`)가 일부 정책 본문에 존재.
7. **`코드정보` 사전 별도 제공** — `mrgSttsCd`, `earnCndSeCd`, `jobCd`, `schoolCd`, `plcyMajorCd`, `sbizCd`, `plcyPvsnMthdCd`, `aplyPrdSeCd`, `bizPrdSeCd` 등의 코드값에 대한 한글 풀이가 공식 엑셀로 제공됨 → 본문 풍부화에 활용 가능.

## 3. 핵심 결정사항

| 결정 | 선택 | 사유 |
|---|---|---|
| 호출 전략 | **전체 페이징 + 응답측 서울 필터** | 26회 외부 호출, 중복 없음, 누락 없음. 자치구별 26회 호출 대비 백엔드 부담 1/26 |
| `region_code` 표현 | **한글 라벨 정규화 (기존 VARCHAR(20) 유지)** | 복지로 패턴(`"전국"`)과 일관. 가장 긴 라벨 `"서울특별시 영등포구"`도 10자라 기존 컬럼 길이로 충분 |
| 동일 정책 중복 | **복지로 우선, YOUTH_CENTER 정규화 제목 일치 시 스킵** | PRD 명시, BOKJIRO 03:00 → YOUTH_CENTER 04:00 순서로 자연스러운 우선권 부여 |
| 동시성 안전망 | **`(source_type, external_id)` UNIQUE 제약** | DB 레벨 무결성 보장 |
| 정규화 제목 비교 | **Postgres GENERATED 컬럼 + 인덱스** | 애플리케이션-DB 일관성 자동 보장, O(log n) 조회 |
| 코드 사전 보관 | **워크플로우에 인라인 JS + docs에 출처 사본** | 워크플로우 self-contained, 추적 가능 |
| 양방향 중복 머지 | **이번 슬라이스 비범위** | YAGNI, 발생 빈도 매우 낮음, v1 또는 어드민 수동 머지 |

## 4. 응답 → DB 매핑

### 4.1 직접 매핑

| 응답 필드 | DB 컬럼 | 메모 |
|---|---|---|
| `plcyNo` | `policy_source.external_id` | UNIQUE 키 |
| `plcyNm` | `policy.title` | NotBlank |
| `plcyExplnCn` | `policy.summary` | 빈 값이면 `plcyNm`로 폴백 |
| `sprvsnInstCdNm` + `operInstCdNm` | `policy.organization` | `"{주관} / {운영}"`, 200자 컷 |
| `sprvsnInstPicNm` | `policy.contact` | `"담당: {이름}"` (전화번호 필드 없음) |
| `aplyUrlAddr` | `policy.reference_sites` | `name="신청 페이지"` |
| `refUrlAddr1`, `refUrlAddr2` | `policy.reference_sites` | `name="참고 사이트"` |
| `plcyAplyMthdCn` | `policy.apply_methods` | 단일 entry, `stageName="신청 절차"` |
| `frstRegDt` 연도 | `policy.reference_year` | `"2026-05-04 14:15:22"` → 2026 |

### 4.2 변환 매핑

| 응답 | DB | 규칙 |
|---|---|---|
| `lclsfNm` | `policy.category` | `･`(U+FF65)·`·`·`,`로 split → 첫 매칭 토큰 → 없으면 `WELFARE` |
| `mclsfNm` + `plcyKywdNm` | `policy.theme_tags` | 둘 다 split해서 합집합 |
| `aplyYmd` | `policy.apply_start` / `apply_end` | `"YYYYMMDD ~ YYYYMMDD"` regex split → LocalDate. 빈 값/"수시"/"상시"/형식 불일치 → 둘 다 null |
| `zipCd` | `policy.region_code` | 4.4 규칙 |
| 모든 텍스트 | — | `᭼`(U+1B3C) → `·`, ` ` → 공백, 연속 공백 정리 |

### 4.3 `lclsfNm` → Category 매핑

매칭 우선순위:

| 토큰 | Category |
|---|---|
| `일자리` | `JOBS` |
| `주거` | `HOUSING` |
| `교육`, `직업훈련` | `EDUCATION` |
| `금융` | `FINANCE` |
| `문화`, `여가` | `CULTURE` |
| `복지`, `복지문화` | `WELFARE` |
| `참여`, `권리`, `참여권리` | `PARTICIPATION` |
| 그 외 | `WELFARE` (기본값) |

다중 토큰일 때 표 등장 순서대로 첫 매치 우선.

### 4.4 `zipCd` → `region_code` 한글 라벨

서울 행정코드 셋 `S`:
```
{11000, 11110, 11140, 11170, 11200, 11215, 11230, 11260, 11290, 11305,
 11320, 11350, 11380, 11410, 11440, 11470, 11500, 11530, 11545, 11560,
 11590, 11620, 11650, 11680, 11710, 11740}
```

분기 (`Z` = 응답 `zipCd` 토큰 셋):

1. `S \ {11000} ⊆ Z` (자치구 25개 모두 포함) → `"서울특별시"`
2. `|Z ∩ (S \ {11000})| == 1` (자치구 정확히 1개) → `"서울특별시 {자치구명}"` (예: `"서울특별시 강남구"`)
3. `Z ∩ S` 의 자치구 수가 2~24개 → `"서울특별시"` (단순화, 권역 분류 없음)
4. `Z ∩ S` 비어있음 → 백엔드 미전송 (서울 외 정책)

도착 시점 `region_code` ∈ `{"서울특별시", "서울특별시 종로구", ..., "서울특별시 강동구"}` 26가지.

### 4.5 `body` 본문 섹션 결합

기존 `IngestionService.parseSections` 정규식 (`[개요|지원대상|선정기준|지원내용]` 4섹션)이 자동으로 `support_target/selection_criteria/support_content` 컬럼에 분리해 넣음. 4섹션 헤더는 그대로 사용.

```
[개요]
{plcyExplnCn}

[지원대상]
- 연령: {sprtTrgtMinAge}~{sprtTrgtMaxAge}세 (sprtTrgtAgeLmtYn=N이면 "제한없음")
- 결혼상태: {mrgSttsCd 풀이}
- 소득조건: {earnCndSeCd 풀이}{earnMin~Max있으면 부가}{earnEtcCn있으면 부가}
- 취업상태: {jobCd 풀이}
- 학력: {schoolCd 풀이}
- 전공: {plcyMajorCd 풀이}
- 특화요건: {sbizCd 풀이}
{ptcpPrpTrgtCn 있으면 "- 참여 제한 대상: ..."}

[선정기준]
{srngMthdCn 또는 "별도 문의"}
{addAplyQlfcCndCn 있으면 "추가 자격: ..."}

[지원내용]
{plcySprtCn}
{plcyPvsnMthdCd 풀이로 "제공방식: ..."}
{sprtSclCnt + sprtArvlSeqYn으로 "지원규모: N명{선착순여부}"}

[제출서류]
{sbmsnDcmntCn}

[사업기간]
{bizPrdBgngYmd YYYY-MM-DD} ~ {bizPrdEndYmd YYYY-MM-DD} ({bizPrdSeCd 풀이})
{bizPrdEtcCn 있으면 부가}

[기타]
{etcMttrCn}
```

빈 항목/섹션은 conditional 출력 (헤더도 생략).

### 4.6 태그

| DB | 값 |
|---|---|
| `life_tags` | `["청년"]` 고정 (이 API는 모든 정책이 청년 대상) |
| `theme_tags` | `mclsfNm` split + `plcyKywdNm` split + `lclsfNm` 토큰 합집합 |
| `target_tags` | `sbizCd` 풀이가 `"제한없음"`/`"기타"` 외이면 추가 (예: `["여성"]`, `["장애인"]`) |

### 4.7 첨부 / source.url

- `attachments: []` 항상 빈 배열
- `source.url` 패턴: `https://www.youthcenter.go.kr/youngPlcyUnif/youngPlcyUnifDtl.do?plcyNo={plcyNo}` (워크플로우 작성 시점에 1건 직접 검증)

## 5. n8n 워크플로우

### 5.1 파일

`n8n/workflows/youth-center-seoul.json` (신규, 복지로 워크플로우 구조 모방)

### 5.2 노드 그래프

```
[매일 새벽 4시 실행]              cron: 0 4 * * *
[수동 실행 트리거]                webhook: youth-center-manual
       ↓ (둘 다 같은 분기)
[페이지 초기화]                   pageNum=1
       ↓
[getPlcy 호출]                    GET /go/ythip/getPlcy
                                  ?apiKeyNm={ENV} &rtnType=json &pageNum={n} &pageSize=100
                                  (zipCd 필터 없음 — 전체 페이징)
       ↓
[JSON 파싱 + 서울 필터]            Code 노드:
                                  - resultCode != 200 → throw
                                  - 첫 페이지에서 totCount/lastPage 계산
                                  - youthPolicyList[] 순회
                                  - zipCd에 서울 26개 코드 중 하나라도 포함된 정책만 통과
                                  - 통과 정책 각각을 item으로 emit
       ↓
[정책별 순차 처리]                 SplitInBatches batchSize=1
       ↓ ("처리 분기")
[1초 대기]                        Wait 1s (백엔드 보호)
       ↓
[정책 → IngestPolicyRequest 변환]  Code 노드:
                                  - 인코딩 정제 (᭼→·, NBSP→공백)
                                  - lclsfNm → category 매핑
                                  - mclsfNm + plcyKywdNm → themeTags
                                  - sbizCd 풀이 → targetTags
                                  - aplyYmd → applyStart/End 파싱
                                  - zipCd → region_code 한글 라벨
                                  - 코드 사전(인라인 JS) 풀이 → body 섹션 결합
                                  - sourceUrl 생성, fetchedAt ISO
                                  - referenceSites: aplyUrlAddr + refUrlAddr1/2
                                  - applyMethods: plcyAplyMthdCn 단일 entry
                                  - attachments: []
       ↓
[백엔드 API 전송]                  POST {{BACKEND_URL}}/api/internal/ingestion/policies
                                  헤더: X-Internal-Api-Key
                                  바디: 위 변환 결과
                                  - 응답 status=RECEIVED 또는 SKIPPED_DUPLICATE
                                  - "Continue On Fail" 켬 (한 정책 실패가 전체 막지 않도록)
       ↓ (정책별 순차 처리 루프 복귀)

       ↓ ("done 분기" — SplitInBatches 완료)
[다음 페이지 확인]                 pageNum < lastPage → hasNext=true
       ↓
[다음 페이지 존재?]                IF
   ↓ true                          ↓ false
[다음 페이지 이동]                 [수집 완료]
   pageNum += 1                   message, totalPolicies 로깅
   ↓
[getPlcy 호출] 로 복귀
```

### 5.3 환경변수

| 변수 | 출처 | 용도 |
|---|---|---|
| `YOUTH_CENTER_API_KEY` | `.env` | 외부 API 인증 (data.go.kr 디코딩 키) |
| `BACKEND_URL` | n8n 환경 (기존) | 내부 intake URL |
| `INTERNAL_API_KEY` | n8n 환경 (기존) | 내부 API 인증 |

### 5.4 에러 처리

| 케이스 | 처리 |
|---|---|
| 외부 API `resultCode != 200` | Code 노드에서 throw → 워크플로우 실패 → n8n executions에 기록 |
| 첫 페이지 totCount=0 | "수집 완료"로 정상 종료 (서비스 점검 등 일시적 상황 가정) |
| 단일 정책 변환 중 예외 | "Continue On Fail"로 다음 정책 진행 |
| 백엔드 4xx/5xx | "Continue On Fail" — 백엔드는 자체 `IngestionItemFailure` 기록 |
| 같은 정책이 여러 페이지 등장 | 백엔드 `(source_type, external_id)` UNIQUE로 차단 |

### 5.5 테스트 모드

복지로 워크플로우와 동일하게, 첫 배포 시 코드 노드 내 `lastPage = 1`로 강제하여 1페이지만 처리. 스키마 검증 후 풀 페이징 활성화.

## 6. 백엔드 변경

### 6.1 마이그레이션 — `backend/src/main/resources/sql/2026-05-08-youth-center-prep.sql`

```sql
-- 사전 점검: (source_type, external_id) 중복이 없어야 ② ALTER 성공
-- SELECT source_type, external_id, COUNT(*) FROM policy_source
--  GROUP BY source_type, external_id HAVING COUNT(*) > 1;
-- 결과 1건이라도 나오면 가장 최신 row만 남기고 정리한 뒤 진행.

-- 1) (source_type, external_id) UNIQUE
ALTER TABLE policy_source
  ADD CONSTRAINT uq_policy_source_type_external_id
  UNIQUE (source_type, external_id);

-- 2) BOKJIRO 우선 dedup용 정규화 제목 GENERATED 컬럼 + 인덱스
ALTER TABLE policy
  ADD COLUMN normalized_title TEXT
  GENERATED ALWAYS AS (
    lower(regexp_replace(title, '[^[:alnum:]가-힣]', '', 'g'))
  ) STORED;
CREATE INDEX idx_policy_normalized_title ON policy (normalized_title);
```

> Postgres 12+ `GENERATED ALWAYS AS ... STORED` 사용. title 변경 시 자동 재계산. 인덱스로 O(log n) 조회. `region_code` 길이는 기존 VARCHAR(20)으로 유지 (한글 라벨 최대 10자).

### 6.2 엔티티 변경

`Policy.java`:
```java
// regionCode는 기존 length=20 그대로 유지 — 변경 없음

@Column(name = "normalized_title", insertable = false, updatable = false)
private String normalizedTitle;   // GENERATED 컬럼 → 읽기 전용 (Builder/updateInfo 시그니처 영향 없음)
```

`PolicySource.java`:
```java
@Table(name = "policy_source", uniqueConstraints = {
    @UniqueConstraint(name = "uq_policy_source_type_external_id",
                      columnNames = {"source_type", "external_id"})
})
```

### 6.3 신규 유틸 — `policy/domain/service/TitleNormalizer.java`

```java
public final class TitleNormalizer {
    private static final Pattern STRIP = Pattern.compile("[^\\p{Alnum}가-힣]");
    private TitleNormalizer() {}
    public static String normalize(String title) {
        if (title == null) return "";
        return STRIP.matcher(title.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
```

DB의 `regexp_replace(lower(title), '[^[:alnum:]가-힣]', '', 'g')` 와 동일 결과.

### 6.4 Repository 추가

`PolicyRepository`:
```java
@Query("""
    SELECT p FROM Policy p
    WHERE p.normalizedTitle = :normalizedTitle
      AND EXISTS (
        SELECT 1 FROM PolicySource s
        WHERE s.policy = p AND s.sourceType = com.youthfit.policy.domain.model.SourceType.BOKJIRO_CENTRAL
      )
""")
Optional<Policy> findByNormalizedTitleWithBokjiroSource(@Param("normalizedTitle") String normalizedTitle);
```

### 6.5 `PolicyIngestionResult` 확장

```java
public record PolicyIngestionResult(Long policyId, Outcome outcome) {
    public enum Outcome { REGISTERED, UPDATED, SKIPPED_DUPLICATE }
    public boolean isNew() { return outcome == Outcome.REGISTERED; }
    public static PolicyIngestionResult registered(Long id) { return new PolicyIngestionResult(id, Outcome.REGISTERED); }
    public static PolicyIngestionResult updated(Long id) { return new PolicyIngestionResult(id, Outcome.UPDATED); }
    public static PolicyIngestionResult skippedDuplicate(Long id) { return new PolicyIngestionResult(id, Outcome.SKIPPED_DUPLICATE); }
}
```

기존 호출부는 `isNew()`만 쓰므로 호환 유지.

### 6.6 `PolicyIngestionService.registerPolicy` 분기 추가

기존 흐름 앞에 dedup 분기:

```java
if (command.sourceType() == SourceType.YOUTH_CENTER) {
    String normalized = TitleNormalizer.normalize(command.title());
    Optional<Policy> bokjiro = policyRepository.findByNormalizedTitleWithBokjiroSource(normalized);
    if (bokjiro.isPresent()) {
        return PolicyIngestionResult.skippedDuplicate(bokjiro.get().getId());
    }
}
// (이하 기존 로직 — sourceType+externalId dedup, upsert)
```

기존 `existingSource` 분기는 `Outcome.UPDATED` 반환, 신규 등록은 `Outcome.REGISTERED` 반환으로 변경.

### 6.7 `IngestionService.receivePolicy` 분기

스킵 시 attachment download / event publish 건너뜀:

```java
PolicyIngestionResult ingestionResult = policyIngestionService.registerPolicy(registerCommand);
duplicate = ingestionResult.outcome() != Outcome.REGISTERED;

if (ingestionResult.outcome() == Outcome.SKIPPED_DUPLICATE) {
    return new IngestPolicyResult(UUID.randomUUID(), "SKIPPED_DUPLICATE");
}

eventPublisher.publishEvent(new PolicyUpsertedEvent(ingestionResult.policyId(), command.title()));
triggerAttachmentDownload(ingestionResult.policyId());
return new IngestPolicyResult(UUID.randomUUID(), "RECEIVED");
```

## 7. 변경 파일 목록

| 파일 | 변경 |
|---|---|
| `backend/src/main/resources/sql/2026-05-08-youth-center-prep.sql` | **신규** — 마이그레이션 SQL |
| `backend/.../policy/domain/model/Policy.java` | `normalizedTitle` 읽기 전용 컬럼 추가 (regionCode 길이는 그대로) |
| `backend/.../policy/domain/model/PolicySource.java` | `@UniqueConstraint` 추가 |
| `backend/.../policy/domain/service/TitleNormalizer.java` | **신규** |
| `backend/.../policy/domain/repository/PolicyRepository.java` | `findByNormalizedTitleWithBokjiroSource` 추가 |
| `backend/.../policy/application/dto/result/PolicyIngestionResult.java` | `Outcome` enum + 팩토리 메서드 |
| `backend/.../policy/application/service/PolicyIngestionService.java` | YOUTH_CENTER dedup 분기 추가 |
| `backend/.../ingestion/application/service/IngestionService.java` | `SKIPPED_DUPLICATE` 응답 분기 |
| `n8n/workflows/youth-center-seoul.json` | **신규** |
| `docs/prd/08-ingestion.md` | 응답 매핑/엔드포인트/카테고리 매핑 정정, "자연 스킵" 문구 수정 |
| `docs/prd/reference/youth-center-codes.xlsx` | **신규** — 코드 사전 출처 사본 |
| `.env.example` | `YOUTH_CENTER_API_KEY` 추가 (이미 적용됨) |

## 8. 테스트

### 8.1 단위 테스트
- `TitleNormalizerTest`: 한글, 영숫자, 특수문자, NBSP, 공백, null/empty
- `PolicyIngestionServiceTest`:
  - YOUTH_CENTER + 동일 정규화 제목 BOKJIRO 존재 → `SKIPPED_DUPLICATE` (DB 미저장)
  - YOUTH_CENTER + BOKJIRO 없음 → 정상 등록
  - YOUTH_CENTER + 동일 제목이지만 BOKJIRO 외 sourceType만 → 정상 등록
  - 기존 BOKJIRO 흐름 회귀 확인
- `PolicyIngestionResultTest`: Outcome 팩토리 + isNew() 호환

### 8.2 슬라이스 테스트
> 프로젝트의 기존 테스트 인프라가 H2면 GENERATED 컬럼 미지원으로 슬라이스 테스트 불가 — 통합 테스트로 대체. Testcontainers Postgres가 있으면 슬라이스 테스트도 가능.

- `normalized_title` GENERATED 컬럼 자동 채워짐 (insert/update)
- `findByNormalizedTitleWithBokjiroSource` 정확성 (BOKJIRO 외 sourceType 제외)
- `(source_type, external_id)` UNIQUE 위반 시 `DataIntegrityViolationException`

### 8.3 통합 테스트
- `POST /api/internal/ingestion/policies` (sourceType=YOUTH_CENTER):
  - BOKJIRO 사전 시드 → `status=SKIPPED_DUPLICATE`, 정책 수 변화 없음
  - BOKJIRO 없음 → `status=RECEIVED`, `region_code` 한글 라벨 검증
- 인코딩 정제: `᭼` 포함 title → 정제된 값 저장 + `raw_json`엔 원본 보존

### 8.4 n8n 워크플로우 수동 검증
- 1페이지 테스트 모드 배포 → webhook 수동 트리거
- 응답 5건 샘플 → 백엔드 DB 직접 조회로 매핑 정확성 확인 (`body` 섹션 결합, `region_code`, `category`)
- 정상 확인 후 풀 페이징 + cron 활성화

## 9. 롤아웃

### 9.1 사전 회귀 점검 (구현 시작 전 필수)

| 점검 | 명령 | 통과 조건 |
|---|---|---|
| ① `(source_type, external_id)` 중복 row 없음 | `SELECT source_type, external_id, COUNT(*) FROM policy_source GROUP BY source_type, external_id HAVING COUNT(*) > 1;` (운영 DB) | 0건 |
| ② `PolicyIngestionResult` 직접 생성자 호출부 전수 확인 | `grep -rn "new PolicyIngestionResult" backend/src` | `PolicyIngestionService` 외 호출자 없음 (있으면 팩토리로 일괄 변경) |

①에서 중복 발견 시 가장 최신 row만 남기고 사전 정리 후 진행. ②에서 추가 호출자 발견 시 plan에 일괄 변경 작업 추가.

### 9.2 롤아웃 순서

| 순서 | 작업 | PR | 비고 |
|---|---|---|---|
| 1 | SQL 마이그레이션 운영 DB 적용 | — | 다운타임 없음 (ALTER ADD COLUMN GENERATED + ADD CONSTRAINT). 사전 점검 9.1-① 통과 후 실행 |
| 2 | 백엔드 코드 + 단위/통합 테스트 | PR #1 | 로컬 `ddl-auto: update` 자동 ALTER, prod는 1번 SQL로 처리 |
| 3 | n8n 워크플로우 import (1페이지 테스트 모드) | PR #2 | 환경변수 `YOUTH_CENTER_API_KEY` 운영 등록 |
| 4 | 1회 수동 트리거 → DB 매핑 검증 | — | 응답 5건 샘플 시각 확인 |
| 5 | 풀 페이징 활성화 + cron(0 4 * * *) on | PR #2 후속 | |

## 10. 운영 모니터링

- `ingestion_run_log`에 `source_label='YOUTH_CENTER'` 행 일일 1건 (성공/실패/중복 카운트)
- 어드민 대시보드 source 필터에 "온통청년" 자동 노출 (`SourceType.YOUTH_CENTER` enum label 등록 완료, UI 확인만)
- 첫 1주: `SKIPPED_DUPLICATE` 비율 로깅 → 비정상적으로 높으면 정규화 규칙 재검토

## 11. 명시적 비범위 (이번 슬라이스 미포함)

- 양방향 중복 머지 — BOKJIRO가 YOUTH_CENTER보다 늦게 들어오는 케이스에서의 자동 머지
- `sprtTrgtMinAge/MaxAge`, `earnMin/MaxAmt`, `jobCd`, `schoolCd`, `mrgSttsCd` 등을 적합도(eligibility) 모듈에 직접 활용 (현재는 본문 텍스트로만)
- 첨부파일 다운로드 — 온통청년 응답에 첨부 필드 없음
- 전국 확대 — 서울 외 지역
- 실패한 ingestion item을 워크플로우 측 백오프/재시도

→ v1 또는 별도 슬라이스로 미룸.

## 12. 위험과 대응

| 위험 | 대응 |
|---|---|
| 운영 DB에 `(source_type, external_id)` 중복 row가 이미 존재 | §9.1-① 사전 점검으로 차단. 발견 시 최신 row만 남기고 정리 후 ALTER |
| `PolicyIngestionResult` 직접 생성자 호출자가 다른 곳에 있어 컴파일 에러 | §9.1-② 사전 grep으로 차단. 발견 시 팩토리 호출로 일괄 변경 |
| `source.url` 패턴이 부정확 | 워크플로우 작성 시 1건 직접 브라우저 검증. 안 되면 검색 페이지 + plcyNo 폴백 |
| 정규화 제목 충돌 (다른 정책이 우연히 같은 정규화 제목) | 첫 1주 `SKIPPED_DUPLICATE` 카운트 모니터링. 비정상이면 정규화 규칙 보강 (예: 기관명 포함) |
| H2 기반 테스트 인프라에서 GENERATED 컬럼 미지원 | 통합 테스트로 대체. Testcontainers Postgres 도입은 별도 작업 |
| 외부 API 응답 스키마 변경 | 첫 페이지 응답 검증 시 필드 누락 throw. 매일 04:05쯤 n8n executions 점검 |
| 일일 호출 한도 초과 | 외부 API 26회/일로 한도(1만/일) 대비 0.26% 사용 — 무시 가능 |
