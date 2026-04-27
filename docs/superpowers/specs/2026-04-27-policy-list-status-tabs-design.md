# Policy List — Status Tabs Design

> **작성일**: 2026-04-27
> **관련 모듈**: `policy` (BE), `pages/PolicyListPage` (FE)
> **선행 PR**: #40 (목록 정렬을 상태 가중치 + 마감 임박순으로 개편)

---

## 1. 배경

현재 정책 목록은 `PolicySortType.DEADLINE`을 default 정렬로 사용하며, `OPEN → UPCOMING → CLOSED` 가중치 + `applyEnd asc(NULLS LAST)` + `createdAt desc` 순으로 한 페이지에 모든 status 정책을 섞어 노출한다(PR #40).

이 구조는 한 화면에서 정렬 우선순위를 명시적으로 표현하지만, 사용자 멘탈 모델에서는 다음과 같은 마찰이 있다.

- 진행중 정책을 보려는데 마감된 정책이 페이지 뒤편에 함께 섞여 페이지를 넘기게 됨.
- 마감 정책에서 마감 임박순(applyEnd asc) 정렬은 의미가 없다 — 이미 모두 지난 일이다.
- `PolicySortType.UPCOMING`(applyStart asc)은 사실상 "예정 정책 보기"와 의미가 중복된다.

## 2. 목표

- default 화면은 **진행중(OPEN)** 정책만 노출한다.
- 사용자는 **예정(UPCOMING) / 마감(CLOSED)** 탭을 명시적으로 클릭해 해당 상태의 정책만 본다.
- 각 탭은 그 탭에서 사용자가 가장 알고 싶을 시간 축 기준으로 정렬한다.
- 사용자에게 노출되던 sort dropdown을 제거하고, 탭 단위로 정렬을 고정해 단순화한다.

## 3. Non-Goals

- 다중 status 동시 선택 (예: `OPEN + UPCOMING` 합치기)
- 탭별 카운트 배지 (예: `진행중 (142)`)
- 사용자별 정렬 개인화(적합도 기반 랭킹 등)
- 키워드 검색에서의 별도 정렬 정책 — 탭 정렬을 그대로 따른다

## 4. 동작 명세

### 4.1 탭 구성

| 탭 라벨 | status | default 정렬 |
|---------|--------|-------------|
| 진행중 | `OPEN` | `applyEnd asc(NULLS LAST)`, `createdAt desc` |
| 예정 | `UPCOMING` | `applyStart asc(NULLS LAST)`, `createdAt desc` |
| 마감 | `CLOSED` | `applyEnd desc(NULLS LAST)`, `createdAt desc` |

탭 노출 순서: **진행중 → 예정 → 마감** (좌→우).

### 4.2 default 동작

- 페이지 진입 시 활성 탭은 `진행중`. URL `searchParams`의 `status` 값이 없으면 `OPEN`으로 간주한다.
- 탭 클릭 시 `setSearchParams({ status: <next>, page: '0' })` — 페이지 인덱스를 0으로 리셋한다.
- 카테고리·지역·검색어·페이지 등 다른 필터는 탭 전환과 독립적으로 유지된다.
- 키워드 검색 결과도 동일 탭 구조와 정렬을 따른다.

### 4.3 제거되는 동작

- `PolicySortType` enum (`DEADLINE / UPCOMING / LATEST`) 및 `sortType` 쿼리 파라미터.
- 사용자에게 노출되던 정렬 dropdown UI.
- `PolicySpecification.statusWeight()`의 가중치 정렬 (단일 status 적용으로 더 이상 불필요).

### 4.4 경계 처리

- 적용 기간(`applyStart`, `applyEnd`)이 NULL인 정책은 정렬상 항상 끝으로 보낸다(`NULLS LAST`).
- 검색·필터 결과 0건인 탭은 빈 상태 메시지를 노출한다 (예: "조건에 맞는 예정 정책이 없습니다").
- status가 명시적으로 지정되지 않은 호출(외부/admin/내부 경로) 응답은 `createdAt desc` 단일 정렬로 처리한다.
- 외부에서 `?sortType=...`이 포함된 URL이 들어오면 무시한다 — 별도 마이그레이션 코드는 두지 않는다.

## 5. 백엔드 변경

### 5.1 제거

- `policy/domain/model/PolicySortType.java` 파일 자체.
- `GET /api/v1/policies` / `GET /api/v1/policies/search`의 `sortType` 쿼리 파라미터 (`PolicyApi`, `PolicyController`, Swagger 문서 모두).
- `PolicySpecification.statusWeight()` 함수.

### 5.2 수정

- `PolicySpecification.buildOrders(...)`를 `PolicyStatus status` 인자 기반 분기로 교체:
  - `OPEN` → `applyEnd asc(NULLS LAST)`, `createdAt desc`
  - `UPCOMING` → `applyStart asc(NULLS LAST)`, `createdAt desc`
  - `CLOSED` → `applyEnd desc(NULLS LAST)`, `createdAt desc`
  - `null` → `createdAt desc` 단일 (admin/내부 호출 안전 default)
- `PolicyQueryService` 시그니처에서 `PolicySortType sortType` 파라미터 제거.
  - `findAllByFilters(...)` 및 `searchByKeyword(...)` 둘 다 동일하게 status 기반 정렬을 따른다.
- `PolicyController` / `PolicyApi`에서 `sortType` 파라미터 제거.
  - `status`의 백엔드 default는 그대로 `null` 유지 (필터 미지정 = 전체). default 활성 탭(`OPEN`) 적용 책임은 프론트엔드에 둔다.
- 키워드 검색 API(`searchPolicies`)도 `status`를 받아 동일한 status 분기 정렬을 적용한다. 필요 시 컨트롤러·서비스 시그니처를 보강하고, 프론트도 search 호출 시 `status`를 함께 전달한다.
- `@ApiResponses` Swagger 문서 갱신.

### 5.3 호출자 영향

- n8n / ingestion 파이프라인은 정책 **조회** API를 사용하지 않으므로 무영향.
- 외부 공개 API는 MVP 범위 외이므로 무영향.

### 5.4 인덱스

- 데이터 규모가 MVP 단계에서 작아 인덱스 추가는 보류한다.
- 정책 row 5,000+ 도달 시 `(status, apply_end)` / `(status, apply_start)` 부분 인덱스 도입 검토 — 후속 운영 작업으로 분리.

## 6. 프론트엔드 변경

### 6.1 컴포넌트 구조

- 검색바와 카테고리/지역 필터 사이에 **Tab Bar** 신설.
  - 라벨: `진행중 | 예정 | 마감` (단일 선택, 좌→우 노출).
  - shadcn/ui `Tabs` (또는 동일 segmented button 그룹)로 구현 — `role="tab"`, 키보드 좌우 이동 a11y 지원.
  - 모바일에서는 풀폭, 터치 타겟 최소 44px.
- 기존 status 칩 토글 로직(`PolicyListPage.tsx:143`, `:431`)은 Tab Bar로 **대체**.
- 정렬 dropdown UI(`PolicyListPage.tsx:521-524`) 일체 **제거**.

### 6.2 URL state

- `searchParams.get('status') ?? 'OPEN'`으로 default 적용.
- 탭 전환: `setSearchParams({ status: nextStatus, page: '0' })` — 페이지 0 리셋.
- `sortType` 쿼리 파라미터 코드 일체 제거 — `parseSortType`, `DEFAULT_SORT`, 관련 setSearchParams 호출 모두 삭제.
- 다른 필터(카테고리, 지역, 검색어)는 탭과 독립.

### 6.3 활성 필터 칩

- 현재 `status` 라벨이 활성 필터 칩으로도 표시된다(`PolicyListPage.tsx:362`).
- 탭이 항상 1개 status를 활성 상태로 고정하므로, **활성 필터 칩에서 `status`를 제외**한다 (중복 노출 방지).
- 카테고리·지역·키워드 칩은 그대로 유지.

### 6.4 영향 파일

| 파일 | 변경 |
|------|------|
| `frontend/src/pages/PolicyListPage.tsx` | 탭 도입, sortType 제거, default status=OPEN |
| `frontend/src/hooks/queries/usePolicies.ts` | `sortType` 인자 제거, queryKey 정리 |
| `frontend/src/apis/policy.api.ts` | `sortType` 쿼리 파라미터 제거, search API에 `status` 전달 추가 |
| `frontend/src/types/policy.ts` | `PolicySortType` 타입 export 제거 |

### 6.5 접근성

- 탭 컨테이너에 `aria-label="정책 상태 필터"` 부여.
- 활성 탭은 색상 + 하단 인디케이터 + `aria-selected="true"`.
- 빈 결과는 탭 아래 단일 일러스트·메시지로 노출.

## 7. 테스트

### 7.1 백엔드 (필수)

- `PolicySpecificationTest`: status별 정렬 케이스 3개(OPEN / UPCOMING / CLOSED) + `null` status 케이스 1개로 재작성.
- `PolicyQueryServiceTest`: `sortType` 인자 제거에 맞춰 시그니처 갱신, 기존 정렬 검증 케이스를 status별 정렬로 재작성.
- `PolicyControllerTest`: `sortType` 파라미터 제거된 요청·status 필터 시나리오 검증.
- 키워드 검색 API에 `status` 파라미터가 전달·반영되는지 검증 케이스 추가.

### 7.2 프론트엔드 (수동 검증 체크리스트)

- [ ] 페이지 진입 시 진행중 탭이 활성, 진행중 정책만 노출.
- [ ] 탭 클릭 시 URL `status` 갱신 + 페이지 0으로 리셋.
- [ ] 탭 + 카테고리 + 지역 + 검색어 조합 시 모두 정상 동작.
- [ ] 빈 탭(예: 예정 결과 0건) 시 빈 상태 메시지 노출.
- [ ] 브라우저 뒤로가기로 이전 탭 상태 복원.
- [ ] 활성 필터 칩에서 status가 제거되었는지 확인.
- [ ] 외부에서 `?sortType=...` 쿼리가 포함된 URL을 열어도 정상 동작 (무시).

## 8. 비기능

- PRD 응답 시간 목표 유지 — 목록 p95 < 300ms. 단일 status 필터 + 단일 정렬이라 현재 구현보다 약간 더 가벼우므로 회귀 위험은 낮다.
- 캐싱 정책 변경 없음 — TanStack Query queryKey에서 `sortType` 제거만 반영.

## 9. 작업 순서 가이드

1. 백엔드: `PolicySortType` 제거 + `PolicySpecification` status 분기 정렬 + Service/Controller/테스트 갱신.
2. 프론트엔드: 타입에서 `PolicySortType` 제거 + API/Hook에서 `sortType` 제거.
3. 프론트엔드: `PolicyListPage`에 Tab Bar 도입 + default status 적용 + sort dropdown 제거 + 활성 필터 칩에서 status 제외.
4. 프론트엔드: 키워드 검색 API에 status 전달 추가.
5. 수동 검증 체크리스트 통과 후 PR 생성.
