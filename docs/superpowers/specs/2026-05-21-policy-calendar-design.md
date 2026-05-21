# 정책 달력 (Policy Calendar) — 설계 문서

- **작성일**: 2026-05-21
- **상태**: Draft (사용자 리뷰 대기)
- **참고 레퍼런스**: jasoseol.com/recruit (월 그리드 + 셀당 다중 항목), Google Calendar (기간 막대 스타일)

## 1. 목적

흩어진 정책 신청 기간을 *시간축 위에서* 한눈에 보여주는 새 진입 경로를 제공한다. 사용자는 "이번 달에 어떤 정책이 열려 있고, 어떤 정책이 곧 마감되는가" 를 그리드 한 장으로 파악할 수 있어야 한다.

현재 `/policies` 리스트는 *상태 필터* (모집중/예정/마감) 기준이라 "신청 기간이 언제부터 언제까지인가" 가 시각화되지 않는다. 달력은 이 정보 공백을 메우는 보완 뷰다.

## 2. 비-목표

- 정책 상세 신청 — 기존 `/policies/:id` 그대로 사용
- 사용자별 일정 추가/북마크 캘린더 연동 (.ics 등) — v0 제외
- 외부 캠페인 카운트다운 — v0 제외
- 관리자가 직접 일정 편집 — 데이터 소스는 ingestion 그대로

## 3. 진입점 & 라우팅

상단 네비 `Navbar.tsx` 의 `NAV_LINKS` 배열에 항목 추가.

```
정책 목록  |  정책 달력  |  적합도 판정  |  Q&A
            ↑ 신규
```

- 경로: `/policies/calendar`
- 라우팅은 `App.tsx` 의 `<AppLayout />` 자식 라우트로 추가 (Header/Footer 포함)
- URL 쿼리: `?month=YYYY-MM&regions=11000,28000&category=HOUSING`
  - `month` 누락 시 현재 월 (KST)
  - `regions` 는 행정코드 CSV, `PolicyListPage` 와 동일 포맷
  - `category` 는 단일 enum (또는 향후 CSV 확장 가능)
- 정책 목록 페이지에서 "달력으로 보기" 링크 클릭 시 동일 쿼리 그대로 전달

## 4. 표시 방식 결정

### 4.1 그리드 (데스크톱, `md:` 이상)

7×N 그리드. 각 정책은 `applyStart`→`applyEnd` 기간의 **막대(bar)** 로 표시.

```
┌─────────────────────────────────────────────────────────┐
│  Sun  Mon  Tue  Wed  Thu  Fri  Sat                     │
│  ──────────────────────────────────────                │
│   3    4    5    6    7    8    9                      │
│  ┃━━ 청년월세 한시 ━━━━━━━━━━━━━━━━━━━━━━━━━━┫           │
│       ┃━━━━ 도약계좌 청년 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│  ┃━━━━ 면접 정장 지원 ━━┫                                │
│                                                          │
│   10   11   12   13   14   15   16                      │
│  ━━━━━━━━━━ 도약계좌 청년 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│  ┃━━━━━━━━━━━━━ 주거자금 ━━━━━━┫                          │
│  +5개 더보기                                              │
└─────────────────────────────────────────────────────────┘
```

**막대 캡 모양**

| 좌측 / 우측 | 의미 |
|:---:|:---|
| `┃` / `┫` | 시작·마감 모두 이 주 |
| `┃` / 없음 | 이 주에 시작, 다음 주 이후로 이어짐 |
| 없음 / `┫` | 지난 주 이전에 시작, 이 주에 마감 |
| 없음 / 없음 | 이 주 전체를 가로지름 |

**막대 라벨**
- 시작 캡(`┃`) 이 있는 셀 또는 화면 좌측 첫 셀에만 한 번 노출 (ellipsis)
- 한 정책이 여러 셀에 걸쳐도 라벨은 한 번만 → 시각 노이즈 최소화

**색**
- 카테고리 기본 톤: HOUSING=blue, JOB=amber, EDUCATION=green, WELFARE=violet, OTHER=gray
- 마감까지 D-3 이내: 카테고리 색 위에 빨강 보더(`ring-2 ring-red-500`)
- 색맹 대응: 마감 임박은 라벨에 `D-N` 텍스트 동반

**셀당 표시 한도**
- 막대 행 최대 3개
- 초과 시 셀 하단에 `+N개 더보기` 칩 → 클릭 시 `CalendarDayPopover` 가 그 날 *겹치는 모든 정책* 을 리스트로 보여줌

### 4.2 아젠다 리스트 (모바일, `md:` 미만)

7칸 그리드는 320–420px 폭에서 라벨이 거의 안 보임. 모바일은 **날짜별로 그룹화된 세로 리스트** 로 전환.

```
2026년 3월 ▾

3/14 (수)
┃ 청년월세 한시 시작 (D-0)
┃ 면접 정장 지원 시작

3/15 (목)
┃ 도약계좌 청년 시작

3/16 (금)
┃ 면접 정장 지원 마감 ❗
```

- 각 행은 "시작" / "마감" / "진행 중" 이벤트로 분류
- 한 정책이 기간 중이면 시작일/마감일 두 번 표시 (중간일은 표시 안 함 — 노이즈)
- 빈 날짜는 생략 (그리드처럼 매일 한 행 차지하지 않음)
- 행 탭 시 `/policies/:id` 로 이동

### 4.3 상시 모집 섹션

`applyStart` 와 `applyEnd` 가 **둘 다 null** 인 정책은 그리드/아젠다에서 제외하고, 별도 섹션으로 그리드 바로 아래에 노출.

```
┌────────────────────────────────────────────────────────┐
│  상시 모집 정책 · 12건                  [모두 보기 →] │
│  [청년 자격증 응시료 지원]  [학자금 상담]              │
│  [구직 활동 지원]  [심리 상담 바우처]  [창업 멘토링]     │
└────────────────────────────────────────────────────────┘
```

- 최대 5건 칩으로 노출
- 더 많으면 `[모두 보기 →]` → 정책 목록 페이지 + 상시 필터 적용된 상태로 이동
- 지역/카테고리 필터는 그리드와 공유

## 5. 헤더 & 네비게이션

```
┌────────────────────────────────────────────────────┐
│  ←  2026년 3월  →          [오늘로]   [필터 ▾]    │
└────────────────────────────────────────────────────┘
```

- 좌/우 화살표: 월 단위 이동. 키보드 ←/→ 동일.
- "오늘로": 현재 월이면 disabled.
- "필터 ▾": `PolicyListPage` 의 필터 컴포넌트 재사용. 데스크톱은 popover, 모바일은 하단 sheet.

## 6. 필터 통합

`PolicyListPage` 와 동일한 시·도/시·군·구 드릴다운 + 카테고리 필터를 그대로 사용.

- 두 페이지가 동일한 URL 쿼리 키(`regions`, `category`) 를 공유 → 페이지 간 상태 보존
- 현재 상태: `RegionPicker` 는 이미 `components/policy/RegionPicker.tsx` 로 분리됨. 카테고리 칩과 필터 시트는 `PolicyListPage.tsx` 에 인라인.
- 이번 작업에서 카테고리 칩 + 필터 시트 컨테이너를 `components/policy/PolicyFilterBar.tsx` 로 추출 → `PolicyListPage` 와 `PolicyCalendarPage` 가 공유.
- 추출 범위는 *카테고리 칩 + 필터 시트의 카테고리/지역 통합 UI* 만. 키워드 검색, 상태 탭(모집중/예정/마감), 정렬은 리스트 페이지에 그대로 둠 (달력에는 해당 의미론이 없음).
- 필터 변경 시 `setSearchParams` 로 URL 갱신 → TanStack Query key 가 바뀌면서 자동 refetch

## 7. 백엔드 API

### 7.1 GET /api/v1/policies/calendar

**쿼리 파라미터**
| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | `LocalDate` (YYYY-MM-DD) | ✓ | 조회 시작일 (KST) |
| `to` | `LocalDate` (YYYY-MM-DD) | ✓ | 조회 종료일 (KST). `from <= to` |
| `regions` | CSV | | 행정코드 CSV. 기존 `/policies` 와 동일 포맷 |
| `category` | enum | | 단일 카테고리 |

**범위 제한**
- `from` 은 현재로부터 -24개월 ~ +24개월
- `to - from` 은 최대 92일 (3개월)
- 범위 초과 시 `400 Bad Request`

**쿼리 의미**
`applyStart <= to AND applyEnd >= from` 와 겹치는 정책. 단:
- `applyStart` 가 null 이면 "시작 시점 불명" 으로 처리 → `applyEnd >= from` 만 만족하면 포함
- `applyEnd` 가 null 이면 "마감 불명/장기" → `applyStart <= to` 만 만족하면 포함
- 둘 다 null 인 상시 정책은 **제외** (별도 엔드포인트)
- `applyStart > applyEnd` 인 데이터 오류는 응답에서 제외 + 서버 로그

**응답**
```json
{
  "items": [
    {
      "id": 1234,
      "title": "청년월세 한시 특별지원",
      "category": "HOUSING",
      "applyStart": "2026-03-14",
      "applyEnd": "2026-03-31",
      "regionLabel": "전국"
    }
  ]
}
```

**경량 DTO 인 이유**: 달력 셀에서 보여줄 정보만 — `description`, `referenceUrls`, `rawText` 등 무거운 필드는 제외하고 막대 클릭 시 기존 `GET /policies/:id` 로 상세 조회.

### 7.2 GET /api/v1/policies/calendar/always-open

**쿼리 파라미터**
| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `regions` | CSV | | 행정코드 CSV |
| `category` | enum | | 단일 카테고리 |
| `page` | int | | 0-base, 기본 0 |
| `size` | int | | 기본 20, 최대 50 |

**쿼리 의미**
`applyStart IS NULL AND applyEnd IS NULL` 인 정책만 반환.

**응답**: `Page<PolicyCalendarItem>` (Spring Page 표준)

### 7.3 백엔드 모듈 변경

```
backend/src/main/java/com/youthfit/policy/
├── presentation/
│   ├── controller/PolicyController.java
│   │   + @GetMapping("/calendar")
│   │   + @GetMapping("/calendar/always-open")
│   └── dto/response/PolicyCalendarResponse.java  (NEW — 경량 DTO)
├── application/service/
│   └── PolicyQueryService.java
│       + findByDateRange(from, to, regions, category)
│       + findAlwaysOpen(regions, category, pageable)
├── infrastructure/persistence/
│   └── PolicyJpaRepository.java
│       + overlap Specification (또는 @Query)
│       + alwaysOpen Specification
└── domain/                          (변경 없음)
```

Specification 합성으로 기존 `regions`, `category` 필터 재사용.

## 8. 프론트 모듈 변경

```
frontend/src/
├── pages/
│   └── PolicyCalendarPage.tsx           (NEW — 라우트, URL 상태 관리)
├── components/policy-calendar/
│   ├── CalendarHeader.tsx               (월 이동, 오늘로, 필터)
│   ├── CalendarMonthGrid.tsx            (데스크톱 7×N 그리드)
│   ├── CalendarAgenda.tsx               (모바일 아젠다 리스트)
│   ├── CalendarBar.tsx                  (한 막대 — 캡 모양/색/라벨)
│   ├── CalendarDayPopover.tsx           (셀 +N 클릭 시 그 날 정책 전체)
│   └── AlwaysOpenSection.tsx            (그리드 아래 상시 정책 칩)
├── components/policy/
│   └── PolicyFilterBar.tsx              (NEW — PolicyListPage 에서 추출, 두 페이지 공유)
├── apis/
│   └── policy.api.ts
│       + fetchCalendarPolicies(params)
│       + fetchAlwaysOpenPolicies(params)
├── hooks/queries/
│   ├── usePolicyCalendar.ts             (월 단위 query key)
│   └── useAlwaysOpenPolicies.ts
├── lib/
│   └── calendarLayout.ts                (NEW — layoutBars 순수 함수)
└── types/policy.ts
    + PolicyCalendarItem
```

**경계 원칙**
- `CalendarBar` 는 표현만. *어느 행에 들어갈지* 같은 레이아웃 계산은 `calendarLayout.ts` 에서 한 번에.
- 데스크톱/모바일은 동일 데이터 훅 (`usePolicyCalendar`) 을 쓰고 뷰만 갈라짐.
- `useMediaQuery('(min-width: 768px)')` 로 분기.

**막대 레이아웃 알고리즘** (`calendarLayout.ts`)

```typescript
type CalendarBarItem = {
  id: number;
  title: string;
  category: string;
  applyStart: LocalDate | null;
  applyEnd: LocalDate | null;
  regionLabel: string;
};

type BarSegment = {
  itemId: number;
  weekIndex: number;      // 0-base 주 인덱스
  startCol: number;       // 0-base 요일 (0=일)
  endCol: number;         // inclusive
  hasStartCap: boolean;   // 이 세그먼트가 정책의 실제 시작
  hasEndCap: boolean;     // 이 세그먼트가 정책의 실제 마감
  row: number;            // 이 주 안에서의 행 (0/1/2)
  isOverflow: boolean;    // 3행 초과로 잘림
};

function layoutBars(
  items: CalendarBarItem[],
  monthStart: LocalDate,
  monthEnd: LocalDate
): { segments: BarSegment[]; overflowByDay: Record<string, number> };
```

규칙:
1. 정렬: `applyStart` 오름차순, tie면 `applyEnd` 내림차순(긴 것 먼저)
2. 주 경계에서 막대 분할 (월 첫 주는 이전 달 일부 포함, 마지막 주는 다음 달 일부 포함)
3. 같은 행에 시간상 겹치지 않는 세그먼트만 배치
4. 한 셀당 최대 3행. 초과는 `overflowByDay` 카운트만 유지

## 9. 인터랙션

| 동작 | 결과 |
|---|---|
| 막대 호버 (데스크톱) | 툴팁: 제목 · 기간 · D-N |
| 막대 클릭 / 탭 | `/policies/:id` 이동 |
| 셀 `+N` 클릭 | `CalendarDayPopover` 열림 (그 날 정책 전체 리스트 + 상세 링크) |
| 월 화살표 / 키보드 ←→ | 월 이동 |
| 오늘로 | 현재 월로 점프 (현재 월이면 disabled) |
| 필터 변경 | URL 쿼리 갱신 + 자동 refetch |
| 빈 상태 | 필터 적용 중이면 `[필터 해제]`, 아니면 `[다른 달 보기]` |

## 10. 로딩 / 에러 / 빈 상태

- **로딩**: 셀 단위 skeleton (4주×7일), 상시 섹션 칩 4개 skeleton
- **에러**: 그리드 자리에 인라인 메시지 + `[재시도]`. 토스트는 안 씀.
- **빈 상태**:
  - 필터 적용 중: "조건에 맞는 정책이 없어요" + `[필터 해제]`
  - 필터 없음: "이 달에는 신청 기간이 걸친 정책이 없어요" + `[다른 달 보기]`

## 11. 접근성

- 모든 막대는 `<a>` 또는 `<button>` (색만 칠한 `div` 금지)
- 마감 임박은 색 + `D-N` 텍스트 동반 (색맹 대응)
- 셀 헤더 날짜는 `aria-label="2026년 3월 14일 목요일, 정책 5건"`
- Tab 순서: 헤더 컨트롤 → 막대들 (DOM 위→아래, 좌→우)
- 키보드 ←→ 로 월 이동, Esc 로 popover 닫기

## 12. 엣지케이스

| 상황 | 처리 |
|---|---|
| `applyStart` 만 있음 (`applyEnd=null`) | 시작 캡만, 우측은 화면 끝까지. 라벨에 "마감일 미정" |
| `applyEnd` 만 있음 (`applyStart=null`) | 우측 캡만, 좌측은 잘려 보임. 라벨에 "상시 모집·N월N일 마감" |
| `applyStart > applyEnd` (데이터 오류) | 백엔드 응답 제외 + 로그 |
| 1년 이상 장기 정책 | 그대로 그림. 화면 밖은 캡 없이 잘려 보임 |
| 같은 정책 id 중복 | 백엔드 dedupe |
| 범위 밖 월 이동 (±24개월 초과) | 백엔드 400, 프론트 인라인 에러 |
| 시간대 | 모두 `LocalDate`, KST 가정. UTC 변환 없음 |
| 비로그인 사용자 | 달력은 인증 불필요. 막대/상세도 공개. 북마크만 상세 페이지에서 로그인 처리 |
| 1일에 30+ 정책 시작 | 셀당 3행 + `+N개 더보기` → popover 에 전체 |

## 13. 테스트 전략

### 백엔드 (JUnit 5 + Spring Boot Test)

- `PolicyJpaRepository` overlap 쿼리 슬라이스 테스트 (`@DataJpaTest`):
  1. 완전 포함되는 정책
  2. 조회 범위 좌측에 걸친 정책
  3. 조회 범위 우측에 걸친 정책
  4. 조회 범위를 완전히 포함하는 정책
  5. 범위 밖 정책 (제외 확인)
  6. `applyStart=null` / `applyEnd=null` 한쪽 누락
- `PolicyController` MockMvc:
  - 200 + 필터 적용
  - 400 (`from > to`, 범위 초과, `to - from > 92일`)
- 상시 정책 엔드포인트: `applyStart IS NULL AND applyEnd IS NULL` 만 반환

### 프론트 (Vitest + Testing Library)

- `calendarLayout.layoutBars` 순수 함수 — 6 케이스:
  1. 한 주 안에서 완결되는 막대 1개
  2. 시간상 겹치는 두 막대 → 다른 행
  3. 안 겹치는 두 막대 → 같은 행 재사용
  4. 주 경계 분할 — 좌측 끝주 우측 캡 없음, 우측 끝주 좌측 캡 없음
  5. 셀당 3행 초과 → overflow 카운트
  6. `applyStart` / `applyEnd` 한쪽 null
- `CalendarMonthGrid` 통합: mock 데이터로 막대 3개 렌더, 빈 셀, `+N` 클릭 → popover
- `usePolicyCalendar`: month/필터 변경 시 query key invalidate
- 모바일 분기: `matchMedia` mock 으로 아젠다 뷰 렌더

### 수동 확인 (CLAUDE.md "UI 변경은 브라우저로 확인" 준수)

- 320 / 768 / 1280 폭에서 한 번씩
- 30+ 정책 mock 으로 overflow 확인
- 키보드 포커스 순서 확인

### 테스트 안 하는 것

- 막대 색 시각 회귀 (chromatic 없음 → 수동)
- 백엔드 ↔ 프론트 e2e (인프라 없음)

## 14. 구현 순서 제안

플랜 단계에서 자세히 분해하지만, 큰 단위 순서:

1. 백엔드 — Repository overlap 쿼리 + Specification + 테스트
2. 백엔드 — Controller / DTO / Service + MockMvc 테스트
3. 프론트 — `calendarLayout.ts` + 단위 테스트 (UI 없이 먼저)
4. 프론트 — API 클라이언트 + 쿼리 훅
5. 프론트 — `CalendarMonthGrid` + `CalendarBar` (데스크톱)
6. 프론트 — `CalendarAgenda` (모바일)
7. 프론트 — `CalendarDayPopover`, `AlwaysOpenSection`, `CalendarHeader`
8. 프론트 — Navbar 항목 추가, 라우트 등록
9. 통합 — 수동 검증, 반응형 확인, 빈 상태/에러 화면

## 15. 향후 확장 (v0 제외, 메모만)

- `.ics` 내보내기, Google Calendar 동기화
- 사용자 적합도와 결합한 "내 정책만" 필터
- 마감 알림 푸시 (이메일 알림과 합류)
- 주간 뷰, 일간 뷰
