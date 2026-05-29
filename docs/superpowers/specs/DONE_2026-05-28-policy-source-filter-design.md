# 정책 목록 출처 필터 (온통청년·복지로·청년서울)

> 작성: 2026-05-28
> 상태: spec

## 1. 배경

- 현재 정책 목록 화면(`/policies`)에는 **카테고리 / 상태(모집중·예정·마감) / 지역** 세 가지 필터가 있다. 출처(어떤 정부 시스템에서 가져온 정책인가)로는 거를 수 없다.
- DB·도메인에는 이미 `SourceType` enum 이 3개 값(`YOUTH_SEOUL_CRAWL` 청년서울, `BOKJIRO_CENTRAL` 복지로, `YOUTH_CENTER` 온통청년) 으로 정의되어 있고, n8n 파이프라인을 통해 세 출처 모두 데이터가 적재되고 있다.
- 정책 카드와 상세에는 이미 `SourceBadge` 가 3개 로고로 표시되고 있어서, 사용자는 출처 차이를 인지한다. 하지만 "복지로 정책만 보고 싶다" 같은 좁히기 동작은 지원되지 않는다.
- 사용자가 명시적으로 "온통청년·복지로 필터를 넣어달라" 라고 요청. 일관성과 데이터 노출 형평성을 위해 청년서울도 포함한다.

## 2. 목표 / 비목표

### 목표
- 정책 목록 화면에서 출처 단일 선택 필터를 추가한다.
- 백엔드 `GET /api/v1/policies` 가 `source` 쿼리 파라미터를 받아 `PolicySource.sourceType` 기준으로 정책을 필터링한다.
- 데스크톱은 카테고리 칩 옆 인라인, 모바일은 필터 시트의 "제공 출처" 섹션으로 노출한다.
- URL 에 필터 상태를 보존한다 (`?source=YOUTH_CENTER`).

### 비목표 (이번 spec 범위 밖)
- 다중 선택(여러 출처를 OR 로 묶기). 필요 시 후속 작업.
- 검색 모드(`/api/v1/policies/search`) 에서 출처 필터 적용. 현재 지역 필터와 동일하게 검색 시 disabled.
- 캘린더(`/policies/calendar`, `/calendar/always-open`) 에서 출처 필터. 현재 캘린더에는 카테고리·지역만 노출하므로 일관성 유지.
- `SourceType` enum 자체 변경 (값 추가/제거).
- `PolicySource` 스키마 / ingestion / n8n 워크플로우 / 마이그레이션.
- 다중 출처를 가진 정책의 dedup·머지 (이미 별도 영역).

## 3. 결정 사항 (브레인스토밍 결과)

| 항목 | 결정 | 근거 |
|---|---|---|
| 옵션 범위 | 3개 모두 (온통청년·복지로·청년서울) | n8n 에서 세 출처 모두 적재 중. `SourceBadge` 도 3개 로고 표시. 둘만 노출하면 "왜 청년서울 필터가 없지?" 라는 위화감 |
| 선택 동작 | 단일 선택 (카테고리 칩과 동일 패턴) | 기존 UI 일관성, 백엔드 단순(IN 절·다중 URL 직렬화 불필요), 사용자가 "그것 하나만" 보고 싶은 의도가 명확 |
| URL 파라미터 | `?source=BOKJIRO_CENTRAL` (enum 그대로) | 카테고리(`?category=JOBS`) 와 동일한 컨벤션. `source` 단수형으로 향후 다중 확장 시 `sources` 와 구분 가능 |
| UI 배치 (데스크톱) | 카테고리 칩 → 구분선 → 출처 칩 → 구분선 → 지역 picker | 한 줄에 자연스럽게 흐름. 기존 구분선(`mx-1 h-6 w-px bg-neutral-200`) 패턴 재사용 |
| UI 배치 (모바일) | 필터 시트에 "제공 출처" 섹션 추가 | 카테고리와 동일 위계. `activeFilterCount` 에도 포함 |
| 검색 모드 동작 | 출처 필터 disabled (지역 필터와 동일) | `/search` 엔드포인트가 source 파라미터를 받지 않음 + 일관성 |
| Specification 구현 | `EXISTS (SELECT 1 FROM policy_sources s WHERE s.policy_id = root.id AND s.source_type = :source)` | `Policy↔PolicySource` 가 1:N 분리 테이블. JPA `Join` 보다 EXISTS 가 결과 중복(distinct 필요) 위험 없고 페이징과 잘 맞음 |
| 라벨 출처 | 프론트 `SOURCE_LABELS` 상수 | 짧은 정적 매핑. 백엔드는 이미 `PolicySummaryResponse.sourceLabel` 을 내려주지만, 필터 칩 라벨은 미선택 정책 데이터와 무관하므로 프론트 상수가 더 단순 |

## 4. 아키텍처

### 4.1 손대는 모듈
- `policy` (BE)
- `frontend` (FE)

### 4.2 무변경
- `ingestion`, n8n 워크플로우
- `SourceType` enum 값
- `PolicySource` 엔티티, DB 스키마, 마이그레이션
- 정책 검색 `/search` 엔드포인트
- 캘린더 엔드포인트

### 4.3 데이터 흐름

```
사용자 클릭 (PolicyFilterBar 출처 칩)
  ↓
PolicyListPage.handleSourceChange → setSearchParams({ source: 'BOKJIRO_CENTRAL' })
  ↓
usePolicies({ source: 'BOKJIRO_CENTRAL', ...others })
  ↓ keyword 없을 때
fetchPolicies({ source: 'BOKJIRO_CENTRAL', ...others })
  ↓
GET /api/v1/policies?source=BOKJIRO_CENTRAL&category=...&regions=...
  ↓
PolicyController.findPolicies(@RequestParam SourceType source)
  ↓
PolicyQueryService.findPoliciesByFilters(regionFilter, category, status, source, page, size)
  ↓
PolicyRepository.findAllByFilters(regionFilter, category, status, source, pageable)
  ↓
PolicySpecification.withFilters(regionFilter, category, status, source)
    → EXISTS subquery on policy_sources
  ↓
Page<Policy> → PolicyPageResult → PolicyPageResponse
```

## 5. 백엔드 변경

### 5.1 `policy/presentation/controller/PolicyApi.java`
- `findPolicies` 시그니처에 `SourceType source` 파라미터 추가.
- `@Parameter(description = "출처 (YOUTH_SEOUL_CRAWL, BOKJIRO_CENTRAL, YOUTH_CENTER)")` 부착.
- Swagger 컨벤션에 따라 description 만 추가, ErrorCode 변경 없음 (400 외 동일).

### 5.2 `policy/presentation/controller/PolicyController.java`
```java
@GetMapping
@Override
public ResponseEntity<PolicyPageResponse> findPolicies(
        @RequestParam(required = false) String regions,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) Category category,
        @RequestParam(required = false) PolicyStatus status,
        @RequestParam(required = false) SourceType source,   // NEW
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) { ... }
```
- service 메서드로 `source` 전달.

### 5.3 `policy/application/service/PolicyQueryService.java`
```java
public PolicyPageResult findPoliciesByFilters(
        RegionFilter regionFilter,
        Category category,
        PolicyStatus status,
        SourceType source,            // NEW
        int page, int size) { ... }
```
- repository 로 그대로 전달. service 레벨 추가 검증 없음 (Spring 이 enum 바인딩 실패 시 400 자동).

### 5.4 `policy/domain/repository/PolicyRepository.java` + `infrastructure/persistence/PolicyRepositoryImpl.java`
- `findAllByFilters` 시그니처에 `SourceType source` 인자 추가.
- `PolicySpecification.withFilters(regionFilter, category, status, source)` 호출.

### 5.5 `policy/infrastructure/persistence/PolicySpecification.java`
`withFilters` 에 EXISTS predicate 추가. `PolicySource.policy` 가 `@ManyToOne Policy` 로 매핑되어 있으므로 `sourceRoot.get("policy").get("id")` 가 정확한 path:
```java
if (source != null) {
    Subquery<Long> sub = query.subquery(Long.class);
    Root<PolicySource> sourceRoot = sub.from(PolicySource.class);
    sub.select(cb.literal(1L))
       .where(
           cb.equal(sourceRoot.get("policy").get("id"), root.get("id")),
           cb.equal(sourceRoot.get("sourceType"), source)
       );
    predicates.add(cb.exists(sub));
}
```

## 6. 프론트엔드 변경

### 6.1 `src/types/policy.ts`
```typescript
export const SOURCE_LABELS: Record<SourceType, string> = {
  YOUTH_CENTER: '온통청년',
  BOKJIRO_CENTRAL: '복지로',
  YOUTH_SEOUL_CRAWL: '청년서울',
};
```

### 6.2 `src/apis/policy.api.ts`
```typescript
interface PolicyListParams {
  category?: string;
  source?: SourceType;     // NEW
  regions?: string[];
  // ...
}

// fetchPolicies 내부
if (params.source) searchParams.set('source', params.source);
```

### 6.3 `src/hooks/queries/usePolicies.ts`
- `UsePoliciesParams` 에 `source?: SourceType` 추가.
- `queryKey` 에 source 포함: `['policies', { keyword, category, source, status, regions, page, size }]`.
- `keyword` 가 있을 때는 `searchPolicies` 가 호출되므로 source 가 무시됨 (기존 지역 필터와 동일 동작).

### 6.4 `src/components/policy/PolicyFilterBar.tsx`
- props 에 `source: SourceType | ''`, `onSourceChange: (next: SourceType | '') => void` 추가.
- 데스크톱 라인 구성:
  ```
  [전체][카테고리들…] | [전체 출처][온통청년][복지로][청년서울] | [지역 picker]
  ```
- 모바일 시트에 카테고리 fieldset 아래 "제공 출처" fieldset 추가. 칩 패턴 동일.
- `activeFilterCount = (category ? 1 : 0) + (source ? 1 : 0)`.
- 출처 칩에도 `disabled` (검색 모드) 적용.

### 6.5 `src/pages/PolicyListPage.tsx`
- URL 파라미터 읽기:
  ```typescript
  const rawSource = searchParams.get('source');
  const source: SourceType | '' = isSourceType(rawSource) ? rawSource : '';
  ```
- `usePolicies` 호출에 `source: source || undefined` 추가.
- `PolicyFilterBar` 에 `source` + `onSourceChange={(v) => updateParams({ source: v, page: '' })}` 전달.
- `activeFilters` 배지에 source 도 푸시:
  ```typescript
  if (source) activeFilters.push({ key: 'source', label: SOURCE_LABELS[source] });
  ```
- `hasActiveQuery` 에 source 도 포함.

## 7. 테스트

### 7.1 백엔드
- `PolicyQueryServiceTest` (이미 존재 시 확장, 없으면 슬라이스 신설):
  - `findPoliciesByFilters` 가 `source = BOKJIRO_CENTRAL` 일 때 해당 출처 정책만 반환하는지.
  - `source = null` 일 때 기존 동작 유지 (회귀 방지).
- `PolicyControllerTest` (MockMvc):
  - `GET /api/v1/policies?source=YOUTH_CENTER` → 200, service 호출 파라미터 검증.
  - `GET /api/v1/policies?source=INVALID` → 400 (Spring enum 바인딩 실패).

### 7.2 프론트엔드
- 기존 `PolicyFilterBar` 에 unit 테스트가 없으므로 본 spec 에서도 컴포넌트 단위 테스트는 추가하지 않는다. 대신 다음을 수동 검증:
  - 출처 칩 클릭 → URL `?source=` 변경 → 목록 갱신
  - 칩 다시 클릭 → 해제
  - 검색 키워드 입력 → 출처 칩 disabled
  - 모바일 시트에서 카테고리·출처 동시 적용 → `activeFilterCount` 가 합산

## 8. 마이그레이션 / 호환성
- DB 스키마 변경 없음.
- API 호환: `source` 는 `required = false`. 기존 호출자(앱·외부) 영향 없음.
- 프론트 URL 호환: `source` 가 없는 기존 링크는 그대로 "전체 출처" 로 해석.

## 9. 위험 / 주의

| 항목 | 위험 | 대응 |
|---|---|---|
| 다중 출처 정책 | 한 정책이 2개 이상 출처로 ingest 된 경우 EXISTS 는 OR 매칭이라 중복 노출 없음 (Page 결과는 Policy 기준) | 별도 처리 불필요. spec 의 "단일 매칭이면 등장" 의도와 일치 |
| 빈 결과 UX | 출처 + 다른 필터 조합으로 결과 0건 시 빈 상태 노출 | 기존 빈 상태 컴포넌트가 잘 동작하므로 추가 작업 없음 |
| 청년서울 자체가 '서울' 한정 데이터 | 지역 + 출처 조합 시 "청년서울 + 부산" 같은 자연스럽지 못한 조합 | 결과 0건 빈 상태로 처리. 사전 차단 안 함 |
