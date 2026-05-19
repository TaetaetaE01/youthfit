# 정책 상세 페이지 정보 구조 리디자인 스펙

- **Date**: 2026-05-19
- **Status**: Draft (사용자 리뷰 대기)
- **Scope**: Frontend only — `frontend/src/pages/PolicyDetailPage.tsx` 및 `frontend/src/components/policy/` 하위
- **Out-of-scope**: 백엔드 API · 정책 데이터 모델 · 가이드(AI) 생성 로직 변경

## 1. 배경 및 목표

현재 `PolicyDetailPage.tsx`(약 900줄)는 약 20개의 정보 섹션을 단일 컬럼으로 순차 나열한다.

- 정보 위계가 평탄해 모든 섹션이 비슷한 비중으로 경쟁한다 → 사용자가 "정신없는 느낌"을 받고 우선순위 인지가 어렵다
- 스크롤 길이가 길어 빠른 판단·결정에 시간이 걸린다
- 어디까지 봤는지·다음에 무엇이 나오는지 알 수 없어 **위치/맥락 감각이 상실**된다
- 스크롤로 내리다 일부 섹션을 놓치기 쉽다

사용자 행동 가정: 세 가지 소비 패턴이 모두 섞여 들어온다 — ① 빠른 판단 → 상세 → 신청 ② 전체 훑기 ③ 특정 정보만 탐색. 따라서 단일 패턴(탭 전용 / 단일 스크롤 전용)으로는 모두를 만족시킬 수 없다.

**목표**: 단일 스크롤의 자유로움을 유지하면서, 정보 위계와 위치 감각을 회복한다. 세 사용자 유형이 모두 자기 페이스로 정보를 소비할 수 있게 한다.

## 2. 요약 (Before / After)

| 영역 | Before | After |
|---|---|---|
| 정보 구조 | 약 20개 섹션 평탄 나열 | 결정 영역 + 4그룹 (받을 수 있는 사람 · 받는 혜택 · 신청하기 · 더 알아보기) |
| 결정 영역 | 흩어진 헤더·요약·메타 | 한 화면 압축: 헤더 → 한 줄 요약 → 핵심 메타 4칸 |
| 핵심 메타 | 기준연도·지원주기·제공유형·문의처 | 마감일·지원규모·기준연도·지원주기 (결정에 직접 영향) |
| 위치 감각 | 없음 | 데스크톱 우측 sticky TOC + 모바일 상단 sticky 칩 |
| 그룹 구분 | h2 글씨 차이만 | 큰 아이콘 + 타이틀 + 안내문 + 디바이더 |
| 그룹 간 여백 | 일반 섹션과 동일 (24px) | 챕터 단절 (48px) + "다음 · X" 디바이더 |
| 신청 CTA 위치 | 본문 중간 + 페이지 하단 박스 (중복) | 데스크톱 우측 sticky + 모바일 fixed bottom bar |
| 세부 지역 | 별도 카드(`SubRegionSection`) | 결정 영역 메타 라인에 통합, 6개 초과 시 "외 N개 펼치기" |
| 정책 요약 원문 | 별도 카드 | "지원대상" PairedSection 의 원문 측에 흡수 |
| Pitfalls | 페이지 중간 | 그룹 4 마지막 |

## 3. 정보 구조 (IA) — 5영역 매핑

### 📌 결정 영역 (Decision Zone) — TOC 바깥, 항상 페이지 상단

빠른 판단형 사용자가 **여기까지만 보고 결정 가능**한 정보만.

- 제목 / 카테고리·상태·출처 배지 / 북마크
- 지역 메타 라인: `MapPin 서울 · 관악·동작·강서 | Calendar ~5/29 | Building2 국토교통부`
- 키워드 태그 (`lifeTags + targetTags + themeTags`)
- 한 줄 요약 (`OneLineSummaryCard` — Sparkles 아이콘 + brand-50 그라데이션)
- 핵심 메타 4칸 (`PolicyMetaSummary` 재편): 마감일·지원규모·기준연도·지원주기
  - 기존 "문의처"는 그룹 4로 이동
  - "사업기간"이 있으면 마감일 칸에 보조 표시

### 👥 그룹 1. 받을 수 있는 사람 (Eligibility)
1. 누가 받을 수 있나요 / 지원대상 (PairedSection)
2. 추가 자격조건 (`additionalQualification`, 해당사항 없음 제외)
3. ⚠ 참여 제한 대상 (`participationRestriction`)
4. 어떻게 뽑히나요 / 선정기준 (PairedSection)
5. 심사방법 (`screeningMethod`)
6. 세부 지역 (`SubRegionSection` — 5개 이하면 결정 영역 메타에서 끝, 6개 이상이면 여기에 전체 펼침)

### 💰 그룹 2. 받는 혜택 (Benefits)
1. 이 정책의 특징 (`HighlightsCard`) — 그룹 2의 헤드라인 역할
2. 무엇을 받나요 / 지원내용 (PairedSection)
3. 사업기간 · 지원규모 chip (현재 chip UI 유지)

### 📝 그룹 3. 신청하기 (Apply)
1. 신청방법 (`GuideListSectionCard` "신청방법" · 가이드 없을 때 enrichment fallback)
2. 신청기한 (`GuideListSectionCard` "신청기한" · enrichment fallback)
3. 제출서류 (`GuideListSectionCard` "제출서류" · `submissionDocuments` fallback)

### ℹ️ 그룹 4. 더 알아보기 (More)
1. 문의처 (`GuideListSectionCard` "문의처" · `contact` fallback)
2. 참고 사이트 (`ReferenceSiteSection`)
3. 첨부파일 (`AttachmentSection`)
4. 기타사항 (`additionalNotes`)
5. ⚠ 놓치기 쉬운 점 (`PitfallsCard`)
6. Q&A 챗봇 (`QnaChatSection`)
7. 공식 신청 채널 박스 — **삭제** (결정 영역 sticky/floating CTA로 대체)

### 빈 그룹 처리

- 가이드(AI) 데이터가 없거나 모든 항목이 비는 그룹: TOC에서 회색 비활성화, 그룹 헤더 자체는 렌더링 생략
- PairedSection 의 쉬운설명/원문 둘 중 한쪽만 있을 때: 단독 카드로 표시 (현재 `PairedSection.tsx` 동작 유지 가정)

## 4. 결정 영역 (Decision Zone)

### 데스크톱 (lg 이상, 8/4 그리드)

좌측 8: 제목 → 메타 → 태그 → 한 줄 요약 → 핵심 메타 그리드
우측 4 (sticky): 📑 목차 → 적합도 카드 → 알림 카드 → 공식 신청 CTA(다크 카드)

### 모바일 (md 미만)

- 동일 정보 단일 컬럼. 메타 그리드는 2×2.
- 적합도 카드는 결정 영역 끝에 인라인 (현재 동작 유지).
- **Fixed bottom bar** 추가: `[알림 토글] [공식 신청 페이지로 이동 →]` 2분할
- Q&A 챗봇 입력창과 충돌 방지: Q&A 섹션이 화면에 들어오면 IntersectionObserver 로 bottom bar 자동 숨김

### 신청 CTA URL 선택

기존 코드에는 `policy.applyUrl` (본문 중간) 과 `policy.sourceUrl` (페이지 하단 박스) 둘 다 있다. 신규 sticky / bottom bar 에서는:

- 1순위: `applyUrl` 이 있으면 사용
- 2순위: 없으면 `sourceUrl` fallback
- 둘 다 없으면 CTA 자체를 숨김 (sticky 카드 / bottom bar 모두)

### 세부 지역 메타 라인 처리

- `subRegions.length <= 5`: 메타에 전체 표시 (예: `서울 · 관악·동작·강서`)
- `subRegions.length > 5`: 첫 1개만 + "외 N개 ▾" 펼침 버튼 (인라인 popover)
- 그룹 1 마지막에 별도 `SubRegionSection` 카드는 6개 이상일 때만 렌더링

## 5. 그룹 헤더 디자인

```
┌─────────────────────────────────┐
│  ┌──┐                            │
│  │👥│  받을 수 있는 사람         │ ← text-xl font-semibold
│  └──┘  이 정책을 받기 위한 조건… │ ← text-sm text-neutral-500
│  ──────────────────────────      │ ← border-bottom-2 neutral-100
└─────────────────────────────────┘
```

**스타일 규칙**
- 아이콘 박스: 52px 정사각, `rounded-2xl`, 그룹별 컬러
  - eligibility: `bg-brand-100 text-brand-800` (Users 아이콘)
  - benefits: `bg-amber-100 text-amber-700` (Wallet 아이콘)
  - apply: `bg-success-100 text-success-700` (Pencil 아이콘)
  - more: `bg-neutral-100 text-neutral-600` (Info 아이콘)
- 타이틀: `text-xl font-semibold text-neutral-900`
- 안내문: `text-sm text-neutral-500`
- 헤더 아래 디바이더: `border-b-2 border-neutral-100 pb-5`
- 그룹 헤더는 sticky **아님** (모바일 칩 네비가 그 역할 전담)
- 그룹 사이 여백: `mt-12` (48px)
- 그룹 헤더 위 인디케이터: `── 다음 · 받을 수 있는 사람 ──` (`text-sm text-neutral-400`)
- 그룹 헤더에 anchor id: `eligibility | benefits | apply | more`

## 6. TOC (데스크톱 우측 sticky)

```
┌───────────────┐
│ 목차          │ ← text-sm font-semibold neutral-500
├───────────────┤
│ 📌 정책 개요   │ ← TOC item (group-level)
│ 👥 받을 수…   │ ← active 상태: brand-50 배경 + 좌측 3px brand-800 바
│   · 지원대상   │ ← TOC sub-item (현재 그룹 펼침)
│   · 참여제한   │
│   · 선정기준   │
│ 💰 받는 혜택   │
│ 📝 신청하기    │
│ ℹ️ 더 알아보기 │
└───────────────┘
```

**동작**
- 현재 viewport 에 가장 가까운 그룹을 active 처리, 자식 sub-item 펼침
- 다른 그룹은 헤더만 표시 (collapse)
- 클릭: `scrollIntoView({ behavior: 'smooth', block: 'start' })` + `scroll-margin-top: 96px`
- URL 해시 동기화: `/policies/123#apply` 로 진입하면 해당 그룹으로 자동 스크롤

**구현 메모**
- `IntersectionObserver` 로 각 `<section id="...">` 가시성 추적
- 옵션: `threshold: [0, 0.3]`, `rootMargin: '-96px 0px -50% 0px'`
- 빈 그룹은 `pointer-events-none opacity-40` 처리 (제거 X, 일관성)

**우측 영역 길이 처리**
- TOC + 적합도 + 알림 + 신청 = 4카드. 1280px 이상 viewport 에서는 자연 fit.
- 1280px 이하: sticky 컨테이너 내부에 `max-h-[calc(100vh-96px)] overflow-y-auto` 허용. 자체 스크롤 발생.

## 7. 모바일 sticky 네비 (가로 스크롤 칩)

```
(결정 영역 보이는 동안 — 숨김)

(결정 영역을 지난 순간부터 — sticky 활성)
┌─────────────────────────────────────┐ ← position: sticky top: 0
│ ◀ [받을 수 있는 사람] [받는 혜택]…▶│   z-index: 10
└─────────────────────────────────────┘   bg: white, border-b
```

- 활성 칩: `bg-brand-800 text-white font-semibold` 솔리드
- 비활성 칩: `bg-white border border-neutral-200 text-neutral-600`
- 가로 스크롤: `overflow-x-auto`, 스크롤바 숨김
- 활성 칩이 항상 가시 영역에 들어오도록: 활성 변경 시 `scrollIntoView({ inline: 'center' })`
- 칩 탭 → 해당 그룹 헤더로 smooth scroll (offset 96px 보정)

## 8. 디자인 토큰 사용

| 요소 | 토큰 |
|---|---|
| 카드 컨테이너 | `bg-white border border-neutral-200 rounded-2xl shadow-card` |
| 한 줄 요약 배경 | `linear-gradient(135deg, brand-50 0%, white 100%)` + `border-brand-100` |
| Easy 카드 | `bg-brand-50 border-brand-100` |
| 원문 카드 | `bg-white border-neutral-200` |
| 경고 카드 | `bg-amber-50 border-amber-200 text-amber-900` |
| Highlight | `linear-gradient(135deg, amber-100, amber-50)` |
| 신청 CTA | `linear-gradient(135deg, brand-800, brand-900)` text-white |
| 적합도/알림 CTA | `bg-white shadow-card` + `bg-brand-800 text-white` 버튼 |
| 그룹 아이콘 박스 | 52px `rounded-2xl`, 그룹별 컬러 |
| TOC active | `bg-brand-50 text-brand-800` + `border-l-[3px] border-brand-800` |

**폰트 (Pretendard)**
- h1 (제목): `text-3xl font-bold` (30px / 700)
- h2 (그룹 헤더): `text-xl font-semibold` (20px / 600)
- h2 (카드 헤더): `text-base font-semibold` (16px / 600)
- h4 (페어드·경고): `text-[15px] font-semibold` (15px / 600)
- 본문: `text-sm` (14px / 400, leading-7)
- 메타·서브: `text-sm text-neutral-500` 또는 `text-xs text-neutral-500`
- 메타값(숫자/날짜): `text-sm font-semibold`
- 태그·배지: `text-xs font-medium`
- TOC active: `text-sm font-semibold`
- **letter-spacing 트릭 사용 금지** — Pretendard 기본 자간 유지

## 9. 컴포넌트 변경 사항

### 9.1 신규 컴포넌트 (`frontend/src/components/policy/`)

```
policy/
├── decision/
│   ├── DecisionZone.tsx        # 결정 영역 컨테이너 (헤더+요약+메타)
│   ├── PolicyMetaLine.tsx      # 위치/기간/조직 인라인 메타
│   ├── SubRegionInline.tsx     # 메타에 통합되는 세부 지역 (5개 이하)
│   └── DecisionMetaGrid.tsx    # 마감일/지원규모/기준연도/지원주기 4칸
├── navigation/
│   ├── PolicyToc.tsx           # 데스크톱 우측 sticky TOC
│   ├── PolicyMobileNav.tsx     # 모바일 상단 sticky 칩
│   ├── PolicyMobileBottomBar.tsx # 모바일 fixed bottom bar (알림+신청)
│   └── usePolicyScrollSpy.ts   # IntersectionObserver 훅
├── groups/
│   ├── PolicyGroupHeader.tsx   # 아이콘+타이틀+안내문+디바이더
│   └── PolicyGroupDivider.tsx  # "다음 · X" 인디케이터
```

### 9.2 수정되는 기존 컴포넌트

- `PolicyDetailPage.tsx`: 단순화 — 데이터 fetch 와 그룹 컴포넌트 조립만. 약 900줄 → 약 250줄 목표
- `PolicyMetaSummary.tsx`: 4칸 콘텐츠 변경 (문의처 제거 → 마감일 추가). 그룹 4의 문의처 카드는 기존 `GuideListSectionCard "문의처"` 재사용
- `SubRegionSection.tsx`: 5개 이하 분기 추가 (메타에 흡수 → 컴포넌트 자체는 빈 렌더)
- `PolicyDetailPage` 의 "공식 신청 채널" 박스 (현 `:808-824`): 삭제

### 9.3 라우트/링크

- URL 해시 지원: `/policies/:id#eligibility|benefits|apply|more`
- 검색 결과나 알림 이메일에서 특정 그룹으로 딥링크 가능

## 10. 점진적 마이그레이션 단계

리스크를 줄이기 위해 4단계로 분리. 각 단계는 독립 PR 가능.

1. **Phase 1 — 그룹 구조 적용 (시각적 변화 최대)**
   - `PolicyGroupHeader`, `PolicyGroupDivider` 신규
   - 기존 섹션 순서 재배치 (4그룹으로)
   - 그룹 헤더 추가, 그룹 간 여백 확대
   - 공식 신청 채널 박스 제거
   - 이 시점에서 이미 "정신없는 느낌" 의 상당 부분 해소 예상

2. **Phase 2 — 결정 영역 정리**
   - `DecisionZone` 컴포넌트 추출
   - `PolicyMetaSummary` 의 4칸 콘텐츠 교체 (마감일·지원규모·기준연도·지원주기)
   - 세부 지역을 메타 라인에 통합 (5개 이하)
   - 한 줄 요약 카드 스타일 강화 (brand-50 그라데이션)

3. **Phase 3 — 네비게이션 (TOC + 모바일 칩)**
   - `PolicyToc` 데스크톱 우측 sticky
   - `PolicyMobileNav` 모바일 상단 칩
   - `usePolicyScrollSpy` 훅
   - URL 해시 동기화 + 딥링킹

4. **Phase 4 — 모바일 fixed bottom bar**
   - `PolicyMobileBottomBar`
   - Q&A 영역 진입 시 자동 숨김 (IntersectionObserver)
   - "공식 신청 페이지" 노출이 어디서 스크롤하든 가능

## 11. 미해결 결정 사항 (사용자 리뷰 대상)

다음 항목들은 합리적 기본값을 두었지만 사용자 확인을 받고 싶음.

1. **PairedSection 모바일에서의 처리**
   - 기본값: 좌우 → 위아래 자연 스택. 쉬운설명 먼저, 원문 그 아래
   - 대안 A: 토글 ("쉬운설명만" 기본, "원문 보기" 버튼)
   - 대안 B: 탭 (쉬운설명 / 원문 탭 전환)
   - 추천: 기본값 — 모바일에서도 둘 다 보이는 게 신뢰성에 유리

2. **PairedSection 데스크톱에서의 기본 표시**
   - 기본값: 둘 다 항상 표시 (현재 동작 유지)
   - 추천: 기본값 유지 — 사용자가 화면 폭이 좁다고 느낄 때만 모바일 처리로 보완

3. **Q&A 챗봇 위치**
   - 기본값: 그룹 4 마지막 (현재 위치 유지)
   - 대안: 별도 floating button 으로 어디서든 열기
   - 추천: 기본값 — Q&A 는 충분히 정보를 본 뒤 묻는 마지막 단계라는 컨텍스트가 자연스러움

4. **그룹 5번째 추가 가능성**
   - 4그룹 구조에 최적화되어 있음. 향후 "관련 정책 추천" 같은 5번째 그룹 추가 시 모바일 칩(가로 스크롤)은 자연 확장, 데스크톱 TOC 도 자연 확장 가능
   - 현재 결정에 영향 없음

## 12. 테스트 계획

### 12.1 단위 테스트

- `PolicyGroupHeader.test.tsx`: 그룹별 아이콘·컬러 매핑
- `DecisionMetaGrid.test.tsx`: 4칸 조건부 렌더링 (마감일 없을 때 등)
- `SubRegionInline.test.tsx`: 5개 이하 분기, 6개 이상 분기
- `usePolicyScrollSpy.test.ts`: IntersectionObserver mock 으로 active id 변경 확인
- `PolicyToc.test.tsx`: active 그룹 펼침, 빈 그룹 비활성화
- `PolicyMobileNav.test.tsx`: 활성 칩 자동 가시화 (scrollIntoView)
- `PolicyMobileBottomBar.test.tsx`: Q&A 가시 시 숨김

### 12.2 시나리오/스토리북

- 가이드 데이터 전체 있을 때
- 가이드 없을 때 (레거시 정책)
- 일부 그룹 비어있을 때 (예: 첨부 없음)
- 세부 지역 0/1/5/6/17개 케이스
- 모바일 / 데스크톱 viewport 각각

### 12.3 수동 확인 체크리스트

- 결정 영역만 보고 "이 정책이 나에게 맞는지" 5초 안에 판단 가능한가
- 스크롤 시 우측 TOC 의 active 그룹이 viewport 와 일치하는가
- 모바일에서 칩 탭 → 해당 그룹으로 정확히 스크롤되는가 (96px offset)
- Q&A 입력 시 fixed bottom bar 가 사라지는가
- URL 해시로 진입 시 해당 그룹으로 자동 스크롤되는가

## 13. 영향 받는 파일 (예상)

```
frontend/src/
├── pages/PolicyDetailPage.tsx                 # 대폭 단순화
├── components/policy/
│   ├── decision/ (신규)
│   ├── navigation/ (신규)
│   ├── groups/ (신규)
│   ├── PolicyMetaSummary.tsx                  # 4칸 콘텐츠 교체
│   ├── SubRegionSection.tsx                   # 5개 이하 분기 추가
│   ├── OneLineSummaryCard.tsx                 # 시각 스타일 강화 (선택)
│   └── HighlightsCard.tsx                     # 그룹 2 상단 배치 (위치만)
```

백엔드 변경: **없음**.

## 14. 비목표 (Out-of-scope)

- 정책 데이터 모델 변경
- 가이드(AI) 콘텐츠 생성 로직 변경
- 새 그룹 추가 또는 정책 데이터 필드 추가
- 검색·필터 UI 변경
- 적합도 카드 내부 UX 변경 (현 `EligibilityCard` 유지)
- 알림 구독 UX 변경 (현 `NotificationCtaCard`, `NotificationPromptSheet` 유지)
- 다크모드 (현재 미지원, 본 작업에서도 미지원)
