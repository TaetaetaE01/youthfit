# Policy List — Region Filter Redesign (시·도 / 시·군·구 드릴다운)

> **작성일**: 2026-05-21
> **관련 모듈**: `policy` (BE), `pages/PolicyListPage`, `lib/labels/region` (FE)
> **선행**: PR #40 (목록 정렬), PR #110 (정책 상세 IA 리디자인)

---

## 1. 배경

현재 정책 목록의 지역 필터는 시·도(광역) 단위 **단일 선택**만 지원한다.

- 프론트 `REGION_OPTIONS` 는 17개 광역시도 enum 코드(`SEOUL`, `BUSAN`, …, `JEJU`)를 단일 `<select>` 로 노출 (`PolicyListPage.tsx:131-146, 450-462`).
- 백엔드 `PolicySpecification.withFilters` 는 `cb.equal(root.get("regionCode"), regionCode)` 로 **단일 값 정확 일치**만 수행 (`PolicySpecification.java:31-33`).
- 그러나 정책 데이터 자체는 시·군·구 행정코드 단위로 들어온다.
  - `Policy.regionCode`: 단일 대표 코드 (예: `"11680"` 강남구, `"전국"`).
  - `Policy.regionCodes`: 행정코드 CSV (예: `"11110,11140,11170"`) — `getRegionCodeList()` 로 파싱.
  - `region-codes.json` 약 226개 행정 단위 마스터 (`backend/src/main/resources/region-codes.json`).
  - n8n workflow `youth-center-seoul.json` 의 `regionLabel(zipCd)` 함수가 시·도 / 시·도 + 시·군·구 / `"전국"` 을 분기 생성.

여기서 비롯되는 마찰:

- 사용자가 "서울"을 골라도 강남구 전용 정책과 마포구 전용 정책이 같은 리스트에 섞이며, 본인 거주지로 좁힐 방법이 없다.
- 프론트가 보내는 enum 코드(`SEOUL`)는 백엔드 `Policy.regionCode` (행정코드 또는 한글 라벨 `"서울"`, `"서울특별시"` 등) 와 **표기가 일치하지 않을 가능성**이 높다. 현재 매칭이 실제로 의도대로 동작하는지 자체가 의심스럽다 — 행정코드 기반으로 통일하는 김에 함께 정리한다.
- 시·군·구 단위로 정책이 들어오지만 사용자에게 그 입도(granularity)가 노출되지 않으므로, 데이터 가치를 잃고 있다.

## 2. 목표

- 사용자가 **시·도 → 시·군·구** 2단계로 지역을 선택할 수 있다.
- 같은 시·도 안 / 시·도를 넘나드는 **다중 선택**을 허용한다.
- 사용자가 어떤 지역을 골라도 **"전국" 적용 정책은 항상 함께 노출**된다.
- 로그인 사용자의 `NotificationSetting.interestRegions` 가 1개 이상 있으면 첫 진입 시 **자동으로 기본 필터**로 적용하고, 사용자가 명시적으로 해제할 수 있다.
- URL 은 행정코드 CSV (`?regions=11680,11440`) 로 표준화한다. 광역시도만 적용된 케이스는 시·도 코드 (예: `?regions=11`) 로 표현 가능하다.
- 결과 카드에 지역 출처 뱃지(`서울 강남구` / `전국`) 를 표시해, 같은 리스트 안 정책의 적용 범위를 한눈에 구분한다.

## 3. Non-Goals

- 지도 기반 선택 UI (행정구역 지도 클릭).
- 사용자 위치(GPS) 기반 자동 감지.
- 검색·랭킹 알고리즘에 "내 지역" 가중치 반영.
- `interestRegions` 외 별도 "거주지" 필드를 사용자 프로필에 신설 — MVP 에서는 `interestRegions[0]` 을 거주지 대용으로 사용한다. 향후 별도 필드가 생기면 대체.
- 시·군·구 보다 더 작은 단위(읍·면·동) 필터.
- 키워드 검색 API(`/policies/search`) 에 지역 필터 추가 — 후속 작업으로 분리(아래 §10 참조).
- 행정코드 마스터 데이터 사용자 편집 UI.

## 4. 동작 명세

### 4.1 지역 모델

행정코드 기반으로 다음 두 단계만 다룬다.

| 단계 | 예시 | 데이터 출처 |
|------|------|-------------|
| 시·도 | `11` 서울, `26` 부산, `41` 경기 | `region-codes.json` 의 `sidoCode`/`sidoName` distinct |
| 시·군·구 | `11680` 강남구, `11440` 마포구, `26260` 동구 | `region-codes.json` 의 각 row |

`"전국"` 은 데이터 상 별도 토큰 (`Policy.regionCode == "전국"`) 이며, 필터의 단계와는 독립적으로 다룬다.

### 4.2 사용자가 보는 흐름 (모바일)

1. **진입** — 목록 상단 필터 칩 영역에 `📍 전체 ▾` 또는 `📍 서울 강남구 ▾` 가 보인다. 로그인 사용자이고 `interestRegions[0]` 이 있으며 URL 에 `regions` 가 명시되지 않은 경우, 페이지 진입 시 자동 적용 + "📍 내 지역(서울 강남구)으로 보고 있어요 [해제]" 배너 노출.
2. **선택** — 칩 탭 시 풀스크린 `RegionPicker` 가 위로 슬라이드. 좌측 시·도 리스트, 우측 시·군·구 체크박스 리스트. 좌측 각 시·도 옆에 `3/25` 식 진행 카운트.
3. **확정** — 하단 "N개 지역 적용" CTA 탭 시 URL `regions` 갱신 + 페이지 0 리셋. 다시 목록으로 돌아옴.
4. **표시** — 결과 카드 좌상단에 뱃지 노출: 노란(`region-local`) = 내가 선택한 지역, 회색(`region-nat`) = "전국".

### 4.3 데스크톱

같은 `RegionPicker` 컴포넌트를 **팝오버**(절대 위치 카드, 460px 폭) 로 노출한다. 트리거는 데스크톱 필터 줄(`PolicyListPage.tsx:432-463`) 의 지역 select 를 대체한 `RegionTriggerButton`.

### 4.4 "전국" 정책 OR 항상 포함

사용자가 어떤 시·도/시·군·구를 골라도, 응답에는 `Policy.regionCode == "전국"` 인 정책을 OR 로 항상 포함한다 — 별도 토글 UI 없이 picker 상단에 안내 텍스트로만 알린다. 사용자가 picker 좌측 리스트에서 "전국" 을 명시적으로 클릭하면 **"전국만 보기"** 모드가 되어 시·군·구 정책은 제외하고 전국 정책만 보여준다.

| 사용자 선택 | 응답에 포함되는 정책 |
|-------------|---------------------|
| (없음) — 전체 | 모든 정책 (전국 + 모든 지역) |
| 전국만 | `regionCode == "전국"` 만 |
| 시·도 1개 (예: 서울 `11`) | 서울 어딘가에 매칭되는 정책 + 전국 |
| 시·군·구 N개 (예: 강남·마포) | 강남 OR 마포에 매칭 + 전국 |
| 시·도 + 시·군·구 혼합 | 각 OR 합집합 + 전국 |

### 4.5 매칭 규칙 — 백엔드

`?regions=<codes>` 가 비어 있지 않은 경우, 다음 조건의 OR 합집합으로 매칭한다.

- 각 시·군·구 코드 `Cxxxxx` 에 대해:
  - `Policy.regionCode == Cxxxxx` 또는
  - `Policy.regionCodes` CSV 안에 `Cxxxxx` 가 포함.
- 각 시·도 코드 `Sxx` (2자리) 에 대해:
  - `Policy.regionCode == Sxx` 또는
  - `Policy.regionCode` 가 `Sxx` 로 시작하는 5자리 행정코드 또는
  - `Policy.regionCodes` 안에 `Sxx` 또는 `Sxx*` (prefix) 코드가 하나라도 포함.
- 그리고 `Policy.regionCode == "전국"` 인 정책은 **요청에 "전국" 토큰이 있는지 여부와 무관하게 항상 OR**.
  - 단, 사용자가 picker 좌측에서 명시적으로 "전국만" 모드를 선택한 경우 (요청 파라미터 `?regions=NATIONWIDE` — 프론트는 항상 이 canonical 토큰을 사용. 백엔드는 호환을 위해 한글 별칭 `?regions=전국` 도 동일하게 인식) — 이때만 "전국" 정책만 반환하고 다른 지역 매칭은 제외.

CSV `regionCodes` 의 멤버십 검사는 PostgreSQL 함수 `string_to_array(region_codes, ',')` + `?| ARRAY[...]` 또는 단순 `LIKE '%,Cxxxxx,%'` 패턴 중 후자를 채택한다 (정렬·인덱스에 영향 적음, 데이터 규모도 MVP 단계라 안전).

### 4.6 사용자 프로필 자동 적용

- 로그인 사용자만 적용.
- 적용 조건: URL `searchParams.get('regions')` 가 `null` (= 명시되지 않음) AND `notification-setting` 로드 완료 AND `interestRegions.length >= 1`.
- 적용 방식: `setSearchParams({ regions: <매핑된 시·도 행정코드 CSV>, page: '' }, { replace: true })`.
  - 예: `interestRegions == ['SEOUL']` → `?regions=11`.
- 배너 노출: 자동 적용된 직후에만 1회. "해제" 클릭 시 `setSearchParams({ regions: '' })` 로 명시적 비움 (다시 자동 적용 안 함 — `regions=` 키가 명시되었으므로 4.6 의 적용 조건을 통과하지 못함).
- 비로그인 사용자는 자동 적용 안 함. 자동 적용 여부는 인증 상태와 무관하게 URL 명시 우선.

### 4.7 경계 처리

- **검색 키워드 + 지역 동시 적용**: MVP 에서는 검색 API 가 지역을 받지 않으므로(§10), 검색 모드 진입 시 picker 칩을 disabled 처리하고 "검색 결과에는 지역 필터가 적용되지 않습니다" 헬퍼 텍스트를 보여준다.
- **잘못된 코드**: URL `regions` CSV 안에서 알 수 없는 코드(예: `?regions=99999,11680`) 가 섞여 있으면, **알 수 없는 코드만 silently 제거하고 유효한 코드는 그대로 매칭에 사용**. 모든 코드가 알 수 없는 경우(`?regions=99999`) 만 결과적으로 빈 지역 필터(= 전체) 로 fallback. 백엔드 400 응답 대신 무음 fallback 이 핫패스에 안전하다.
- **"전국" 정책의 카드 뱃지**: `regionCode == "전국"` 이고 source 가 BOKJIRO 인 경우 `전국(중앙정부)`, YOUTH_CENTER 인 경우 `전국(여러 지자체)` (기존 `getRegionName` 헬퍼 재사용).
- **count 라벨**: 좌측 시·도 리스트의 `3/25` 표시는 현재 picker 안에서 선택 중인 개수만 의미한다 (정책 실제 분포가 아님 — 비용·복잡도 대비 이득 낮음).

## 5. 백엔드 변경

### 5.1 신규 — Region 마스터 API

행정코드 마스터를 프론트에 노출한다. 도메인 소유 원칙대로 backend 가 단일 출처.

- 신규 컨트롤러 `RegionController` (`policy.presentation.controller`):
  - `GET /api/v1/regions` → `{ sidos: [{ code, name }], sigungus: [{ code, sidoCode, sidoName, name }] }`.
  - 캐시: 응답에 `Cache-Control: public, max-age=86400` 헤더 부여. 데이터 변경 빈도가 매우 낮음.
- 신규 Service `RegionQueryService` (`policy.application.service`): `RegionCodeRegistry.findAll(null)` 로 전체 조회 후 result DTO 생성.
- 신규 Result DTO: `RegionListResult` (`record`), `RegionListResult.Sido` / `RegionListResult.Sigungu`.
- 신규 Response DTO: `RegionListResponse` (presentation).
- 신규 Api 인터페이스 `RegionApi` 에 Swagger 어노테이션 부여. Controller 가 implements.

### 5.2 수정 — Policy 검색 시그니처

`/api/v1/policies` 엔드포인트에 다중 지역 코드 받기.

- `PolicyController.findPolicies`:
  - 파라미터 `String regionCode` (deprecated, 호환성 유지) → `String regions` (CSV) 추가.
  - 둘 다 `required=false`. 우선순위: `regions` 가 있으면 `regionCode` 무시.
  - 응답은 그대로 `PolicyPageResponse`.
- `PolicyQueryService.findPoliciesByFilters`:
  - 시그니처 변경: `(String regionCode, Category category, PolicyStatus status, int page, int size)` → `(List<String> regionCodes, Category category, PolicyStatus status, int page, int size)`.
  - 기존 호출자 1곳 (Controller) — 컨트롤러에서 CSV → `List<String>` 파싱하여 전달. 잘못된 코드는 무시.
- `PolicyRepository.findAllByFilters`:
  - 시그니처 동일하게 `List<String> regionCodes` 로.
- `PolicySpecification.withFilters`:
  - 시그니처 변경 + 매칭 분기 로직 §4.5 구현.
  - 핵심 의사코드:
    ```java
    if (regionCodes != null && !regionCodes.isEmpty()) {
      boolean onlyNationwide =
          regionCodes.size() == 1 && (regionCodes.get(0).equals("NATIONWIDE")
                                       || regionCodes.get(0).equals("전국"));
      if (onlyNationwide) {
        predicates.add(cb.equal(root.get("regionCode"), "전국"));
      } else {
        List<Predicate> ors = new ArrayList<>();
        ors.add(cb.equal(root.get("regionCode"), "전국")); // 전국 항상 포함
        for (String code : regionCodes) {
          if (code.length() == 2) {
            // 시·도: regionCode 정확 일치 OR LIKE 'code%' (5자리 행정코드) OR regionCodes CSV LIKE '%,code...'
            ors.add(cb.equal(root.get("regionCode"), code));
            ors.add(cb.like(root.get("regionCode"), code + "%"));
            ors.add(cb.like(root.get("regionCodes"), "%" + code));
            ors.add(cb.like(root.get("regionCodes"), "%" + code + ",%"));
          } else {
            // 시·군·구: 정확 일치 OR CSV 멤버십
            ors.add(cb.equal(root.get("regionCode"), code));
            ors.add(cb.like(root.get("regionCodes"), "%" + code + "%"));
          }
        }
        predicates.add(cb.or(ors.toArray(new Predicate[0])));
      }
    }
    ```
  - 위 의사코드의 `LIKE` 패턴은 false-positive 가능성이 있으므로 (예: `LIKE '%41%'` 가 `4111` 도 매칭) 실제 구현은 다음 둘 중 하나로 정확하게 분기한다:
    1. 콤마 패딩 — `regionCodes` 를 DB 에 저장할 때 양 끝에 콤마 부여하지 않으므로, 쿼리에서 `',' || region_codes || ','` 형태로 패딩 후 `LIKE '%,Cxxxxx,%'` 매칭. PostgreSQL `concat()` 사용.
    2. `string_to_array` + `= ANY` — Postgres 한정 native query 로 분리. 가독성 우수하지만 Specification 추상화를 깬다.
  - **선택**: 1번 (콤마 패딩) — 추상화 유지 + 비용 적음. SpecificationUtil 보조 메서드로 추출.

### 5.3 수정 — `findPolicyById` 의 `summarizeSubRegions`

기존 `PolicyQueryService.summarizeSubRegions` 는 그대로 (시·군·구 라벨 List 반환). 결과 카드 뱃지에서 이 값을 함께 사용해 "서울 강남구" 같은 표시를 만들도록 한다. 추가 변경 불필요.

### 5.4 삭제

없음. `regionCode` 단일 파라미터는 호환성 유지로 deprecated 처리만.

### 5.5 인덱스

- 데이터 규모가 MVP 단계에서 작아 인덱스 추가는 보류.
- 정책 row 5,000+ 도달 시:
  - `policy.region_code` 컬럼에 b-tree 인덱스 (정확 일치 가속).
  - `policy.region_codes` CSV 검색은 GIN 인덱스로 전환 (`USING gin (string_to_array(region_codes, ','))`) 검토.

## 6. 프론트엔드 변경

### 6.1 신규 컴포넌트

```
src/components/policy/
├── RegionPicker.tsx              # 본체 (모바일 풀스크린 / 데스크톱 팝오버)
├── RegionPickerTrigger.tsx       # 칩 형태 트리거 ("📍 서울 +2 ▾")
└── RegionPickerBanner.tsx        # "📍 내 지역으로 보고 있어요 [해제]"
```

`RegionPicker` props (요지):

```ts
interface RegionPickerProps {
  open: boolean;
  onClose: () => void;
  selectedCodes: string[];               // 현재 선택된 행정코드 (URL 기반)
  onApply: (codes: string[]) => void;    // 적용 버튼 — CSV 로 URL 갱신 위임
  mode: 'mobile-sheet' | 'desktop-popover';
}
```

### 6.2 신규 데이터 훅

```
src/apis/region.api.ts            # fetchRegions(): GET /api/v1/regions
src/hooks/queries/useRegions.ts   # useQuery 래퍼, staleTime: Infinity (24h)
src/lib/labels/region.ts          # RegionSidoCode → 행정코드 매핑 추가
```

`RegionSidoCode` enum 과 시·도 행정코드의 매핑 테이블을 `region.ts` 에 추가한다:

```ts
export const SIDO_CODE_BY_ENUM: Record<RegionSidoCode, string> = {
  SEOUL: '11', BUSAN: '26', DAEGU: '27', INCHEON: '28',
  GWANGJU: '29', DAEJEON: '30', ULSAN: '31', SEJONG: '36',
  GYEONGGI: '41', GANGWON: '42', /* ... */
};
```

`NotificationSetting.interestRegions` 가 enum 코드를 들고 있으므로, 자동 적용 시 이 테이블로 행정코드 CSV 로 변환한다.

### 6.3 PolicyListPage 변경

- `MobileFilterSheet` 의 지역 `<select>` (line 131-146) **제거**. 카테고리 토글만 남기고, 지역은 시트 바깥 칩으로 노출.
- 데스크톱 필터 줄의 지역 `<select>` (line 450-462) 제거 — 같은 자리에 `RegionPickerTrigger` 배치.
- 상단 status tab bar 아래에 다음 영역 신설:
  - 좌측: `RegionPickerTrigger` 칩 (모바일·데스크톱 공통).
  - 우측 (데스크톱): 기존 카테고리 칩.
- URL 파싱:
  - `searchParams.get('regions')` 으로 CSV 읽기 → `string[]`.
  - 기존 `regionCode` 가 명시된 경우(레거시 URL) 한 번 변환해서 `regions` 로 정규화 후 replace (1회).
- `usePolicies` 훅에 `regions: string[]` 파라미터 추가, queryKey 에 반영.
- 자동 적용 effect:
  - `useEffect` 안에서 (`isAuthenticated && interestRegions[0] && !searchParams.has('regions')`) 조건에서 1회 `setSearchParams({ regions: <매핑>, page: '' }, { replace: true })` + 배너 표시 플래그 상태 설정.
- `activeFilters` 배지 처리: 지역은 picker 내부 표현에 위임. 칩 영역에서 따로 표시하지 않는다 (트리거 자체가 지역 칩 역할).

### 6.4 PolicyCard 변경

`src/components/policy/PolicyCard.tsx`:

- 지역 뱃지 추가. 기존 카테고리 뱃지와 같은 줄에 prepend.
- 표시 규칙:
  - `policy.regionCode == "전국"` → 회색 뱃지 `"전국"` (또는 `getRegionName` 결과).
  - 그 외 → `policy.subRegions` (백엔드 `summarizeSubRegions` 결과 활용) 첫 번째 항목을 노란 뱃지로 표시. 다중이면 "서울 강남구 +2" 형태.

### 6.5 API 클라이언트 변경

`src/apis/policy.api.ts`:

```ts
interface PolicyListParams {
  category?: string;
  regions?: string[];           // 신규
  regionCode?: string;          // deprecated (호환)
  status?: PolicyStatus;
  page?: number;
  size?: number;
}

// fetchPolicies 내부
if (params.regions && params.regions.length > 0) {
  searchParams.set('regions', params.regions.join(','));
}
```

### 6.6 영향 파일 요약

| 파일 | 변경 종류 |
|------|-----------|
| `frontend/src/pages/PolicyListPage.tsx` | 지역 select 제거, picker 트리거 배치, 자동 적용 effect |
| `frontend/src/components/policy/PolicyCard.tsx` | 지역 뱃지 추가 |
| `frontend/src/components/policy/RegionPicker.tsx` | 신규 |
| `frontend/src/components/policy/RegionPickerTrigger.tsx` | 신규 |
| `frontend/src/components/policy/RegionPickerBanner.tsx` | 신규 |
| `frontend/src/apis/policy.api.ts` | `regions` 파라미터 추가 |
| `frontend/src/apis/region.api.ts` | 신규 |
| `frontend/src/hooks/queries/usePolicies.ts` | `regions` 인자 + queryKey |
| `frontend/src/hooks/queries/useRegions.ts` | 신규 |
| `frontend/src/lib/labels/region.ts` | `SIDO_CODE_BY_ENUM` 추가 |
| `frontend/src/types/policy.ts` | (그대로, 새 타입 없음) |

| 파일 (백엔드) | 변경 종류 |
|---------------|-----------|
| `backend/.../policy/presentation/controller/PolicyController.java` | `regions` 파라미터 추가 (기존 `regionCode` 호환 유지) |
| `backend/.../policy/presentation/controller/PolicyApi.java` | Swagger 어노테이션 갱신 |
| `backend/.../policy/presentation/controller/RegionController.java` | 신규 |
| `backend/.../policy/presentation/controller/RegionApi.java` | 신규 |
| `backend/.../policy/application/service/PolicyQueryService.java` | 시그니처 변경 |
| `backend/.../policy/application/service/RegionQueryService.java` | 신규 |
| `backend/.../policy/application/dto/result/RegionListResult.java` | 신규 |
| `backend/.../policy/presentation/dto/response/RegionListResponse.java` | 신규 |
| `backend/.../policy/domain/repository/PolicyRepository.java` | 시그니처 변경 |
| `backend/.../policy/infrastructure/persistence/PolicyRepositoryImpl.java` | 시그니처 변경 |
| `backend/.../policy/infrastructure/persistence/PolicySpecification.java` | 매칭 로직 §4.5 구현 |

## 7. 접근성

- `RegionPicker` 본체는 `role="dialog"`, `aria-labelledby="region-picker-title"`, `aria-modal="true"`.
- 좌측 시·도 리스트는 `role="listbox"`, 항목은 `role="option"`, 현재 활성 시·도에 `aria-selected="true"`.
- 우측 시·군·구는 체크박스(`<input type="checkbox">`) + label 패턴 — 키보드 Tab 이동 + Space 토글.
- 트리거 칩은 `<button>` 이며 적용 개수를 `aria-label` 에 풀어쓴다 (예: `aria-label="지역 선택: 서울 강남구 외 2개"`).
- 자동 적용 배너에 `role="status"` (assertive 아님) — 스크린리더가 진입 시 1회 안내.
- 모바일 풀스크린 시트는 ESC 또는 swipe-down 으로 닫힘, `body` 스크롤 잠금 (기존 `MobileFilterSheet` 의 `body.style.overflow` 패턴 재사용).

## 8. 테스트

### 8.1 백엔드 (필수)

- `PolicySpecificationTest`: §4.5 의 7개 케이스 모두 검증.
  - 빈 `regionCodes` (전체).
  - `["NATIONWIDE"]` 만 (전국만).
  - 시·도 1개 (예: `["11"]`).
  - 시·군·구 1개 (`["11680"]`).
  - 시·군·구 다중 (`["11680","11440"]`).
  - 시·도 + 시·군·구 혼합 (`["11","26260"]`).
  - 알 수 없는 코드 1개 + 유효 1개 — 알 수 없는 건 무시되고 유효한 매칭만.
- `PolicyQueryServiceTest`: CSV 파싱이 잘못된 입력 (`",,"`, `null`) 에서도 안전한지 검증.
- `RegionControllerTest`: 응답 구조와 캐시 헤더 검증.
- `PolicyControllerTest`: legacy `?regionCode=` 가 정규화되어 동일 결과를 내는지 검증.

### 8.2 프론트엔드 (수동 검증 체크리스트)

- [ ] 비로그인 첫 진입: 지역 칩 `📍 전체 ▾`, 자동 적용 배너 없음.
- [ ] 로그인 + `interestRegions=['SEOUL']` 첫 진입: URL 이 `?regions=11` 로 replace, 배너 1회 노출, 목록은 서울 + 전국 정책만.
- [ ] 배너 "해제" 클릭: URL 의 `regions` 가 비고, 모든 정책 노출, 새로고침해도 자동 적용 안 됨.
- [ ] picker 모바일 시트: 좌측 서울 탭 후 우측에서 강남구·마포구 체크 → "2개 지역 적용" → URL `?regions=11680,11440`.
- [ ] picker 데스크톱 팝오버: 트리거 클릭 시 460px 폭 카드, 외부 클릭으로 닫힘.
- [ ] "전국만 보기": 좌측 "전국" 클릭 후 적용 → URL `?regions=NATIONWIDE`, 결과에 시·군·구 정책 안 보임.
- [ ] 카드 뱃지: 전국 정책 → 회색 `"전국"`, 강남구 정책 → 노란 `"서울 강남구"`.
- [ ] 검색 모드: 검색바에 키워드 입력 → 지역 트리거 disabled, 헬퍼 텍스트 노출.
- [ ] 잘못된 URL (`?regions=99999`): 빈 필터로 fallback, 전체 정책 노출.
- [ ] 활성 status tab (모집중) + 지역(서울 강남) + 카테고리(금융) 동시 적용 후 페이지네이션 정상.
- [ ] 브라우저 뒤로가기로 직전 지역 선택 복원.

### 8.3 접근성 체크

- [ ] 키보드만으로 picker 열기 → 시·도 선택 → 시·군·구 체크 → 적용 가능.
- [ ] 스크린리더 (VoiceOver) 로 트리거 칩 라벨이 선택 개수까지 안내됨.
- [ ] 자동 적용 배너가 `role="status"` 로 한 번 안내됨 (반복 안 됨).

## 9. 비기능 / 성능

- **응답 시간**: 4.5 의 매칭 로직은 OR 의 분기가 늘어나지만, MVP 정책 row 수가 작아 영향 미미. p95 < 300ms 유지.
- **캐시**:
  - 프론트 `useRegions` queryKey 의 `staleTime: Infinity` (24h gcTime), 사용자 세션 동안 1회 조회.
  - 백엔드 응답 `Cache-Control: public, max-age=86400`.
- **번들 크기**: `RegionPicker` + region.api 추가로 약 3-4KB gzip 예상. 정책 페이지 진입 시점에만 로드 (lazy import 검토 — 첫 진입 핫패스 영향 측정 후 결정).
- **사용자 부담**: 자동 적용 배너는 1회 노출 후 닫히므로 반복 마찰 없음. 사용자가 의도적으로 전체로 보고 싶을 경우 명시 해제 후 URL 공유 가능.

## 10. 작업 순서 가이드

1. **백엔드 — Region API 신설**: `RegionController`/`Api`/`Service`/`Result` + 단위 테스트.
2. **백엔드 — Specification 매칭 로직**: `PolicySpecification.withFilters` 시그니처 변경 + 콤마 패딩 매칭 + 테스트.
3. **백엔드 — Service/Repository/Controller 시그니처 전파** + legacy `regionCode` 호환 + Swagger 갱신.
4. **프론트 — region.api.ts + useRegions + SIDO_CODE_BY_ENUM 매핑**.
5. **프론트 — RegionPicker / Trigger / Banner 컴포넌트** (먼저 페이지 통합 없이 임시 라우트 `/_dev/region-picker` 에 단독으로 띄워 디자인 검증 가능. 통합 후 임시 라우트 삭제).
6. **프론트 — PolicyListPage 통합** (지역 select 제거, picker 배치, URL `regions` 처리, 자동 적용 effect).
7. **프론트 — PolicyCard 지역 뱃지** 추가.
8. **수동 검증** (§8.2 체크리스트 통과) → PR 생성.
9. (후속) 키워드 검색 API 에도 지역 필터 적용 — 별도 스펙으로 분리.

## 11. 열린 질문 / 후속

- **n8n 수집 데이터 정규화**: `Policy.regionCode` 가 워크플로마다 한글 라벨 / 행정코드 / `"전국"` 으로 혼재. 새 매칭 로직은 양쪽 모두 어느 정도 흡수하지만, 점진적으로 행정코드로 통일하는 마이그레이션이 필요. 별도 ingestion 스펙으로 분리.
- **NotificationSetting.interestRegions 시·군·구 확장**: 현재 시·도 enum 만 저장. 동일한 picker 를 알림 설정 페이지에서도 재사용하려면 시·군·구 코드까지 저장 가능하게 모델 확장 필요. 후속.
- **검색 API 의 지역 필터**: §3 Non-Goals 로 분리. 후속 스펙에서 다룰 때 본 스펙의 §4.5 매칭 로직을 그대로 차용한다.
- **시·군·구 뱃지의 시·도 prefix 생략 여부**: "서울 강남구" 가 좋은지 "강남구" 만으로 충분한지 — 데이터 분포가 풍부해진 후 사용자 피드백으로 결정.
