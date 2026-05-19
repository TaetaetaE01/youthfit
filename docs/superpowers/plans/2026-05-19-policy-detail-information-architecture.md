# 정책 상세 페이지 정보 구조 리디자인 — 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `PolicyDetailPage.tsx`의 20개 평탄 섹션을 "결정 영역 + 4그룹" 구조로 재편하고, 데스크톱 우측 sticky TOC + 모바일 가로 스크롤 칩으로 위치 감각을 회복한다.

**Architecture:** 기존 컴포넌트는 최대한 유지하면서 신규 컨테이너(`decision/`, `navigation/`, `groups/`)로 감싸는 점진적 리팩토링. Phase 1(그룹 구조) → Phase 2(결정 영역) → Phase 3(네비) → Phase 4(모바일 bottom bar) 순서로 진행. 각 Phase 종료마다 페이지가 깨지지 않고 동작해야 함.

**Tech Stack:** React 19 + TypeScript 5 + Vite 6 + Tailwind CSS v4 (Pretendard) + Vitest + @testing-library/react + lucide-react + IntersectionObserver

**Reference spec:** `docs/superpowers/specs/2026-05-19-policy-detail-information-architecture-design.md`

---

## File Structure

신규 파일은 도메인별 하위 디렉토리로 그룹화. 각 컴포넌트는 단일 책임.

```
frontend/src/components/policy/
├── decision/                           # 결정 영역 (Phase 2)
│   ├── DecisionZone.tsx                # 헤더+요약+메타 컨테이너
│   ├── PolicyMetaLine.tsx              # 지역·기간·조직 인라인 라인
│   ├── SubRegionInline.tsx             # 5개 이하 시 메타 라인에 통합
│   ├── DecisionMetaGrid.tsx            # 마감일·지원규모·기준연도·지원주기 4칸
│   └── __tests__/
├── navigation/                          # 네비게이션 (Phase 3, 4)
│   ├── PolicyToc.tsx                   # 데스크톱 우측 sticky TOC
│   ├── PolicyMobileNav.tsx             # 모바일 상단 sticky 칩
│   ├── PolicyMobileBottomBar.tsx       # 모바일 fixed bottom bar
│   ├── usePolicyScrollSpy.ts           # IntersectionObserver 훅
│   └── __tests__/
└── groups/                             # 그룹 헤더 (Phase 1)
    ├── PolicyGroupHeader.tsx           # 아이콘+타이틀+안내문
    ├── PolicyGroupDivider.tsx          # "다음 · X" 인디케이터
    ├── policyGroups.ts                 # 그룹 정의 상수 (id, 라벨, 아이콘, 컬러)
    └── __tests__/
```

기존 수정 파일:
- `frontend/src/pages/PolicyDetailPage.tsx` — 그룹 구조 적용 + 단순화
- `frontend/src/components/policy/OneLineSummaryCard.tsx` — 스타일 강화 (Phase 2)
- `frontend/src/components/policy/SubRegionSection.tsx` — 6개 이상일 때만 렌더

---

# Phase 1 — 그룹 구조 적용

페이지가 가장 크게 변하는 단계. 이 Phase 만으로도 "정신없는 느낌" 의 상당 부분 해소.

## Task 1: 그룹 정의 상수 + 타입 (`policyGroups.ts`)

**Files:**
- Create: `frontend/src/components/policy/groups/policyGroups.ts`
- Test: `frontend/src/components/policy/groups/__tests__/policyGroups.test.ts`

- [ ] **Step 1: 실패 테스트 작성**

```ts
// policyGroups.test.ts
import { describe, it, expect } from 'vitest';
import { POLICY_GROUPS, type PolicyGroupId } from '../policyGroups';

describe('POLICY_GROUPS', () => {
  it('4개의 그룹을 정의한다', () => {
    expect(POLICY_GROUPS).toHaveLength(4);
  });

  it('각 그룹은 id, label, description, tone을 가진다', () => {
    POLICY_GROUPS.forEach((g) => {
      expect(g.id).toBeTruthy();
      expect(g.label).toBeTruthy();
      expect(g.description).toBeTruthy();
      expect(g.tone).toBeTruthy();
    });
  });

  it('id는 eligibility, benefits, apply, more 순서', () => {
    const ids: PolicyGroupId[] = POLICY_GROUPS.map((g) => g.id);
    expect(ids).toEqual(['eligibility', 'benefits', 'apply', 'more']);
  });
});
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/groups/__tests__/policyGroups.test.ts`
Expected: FAIL — "Cannot find module"

- [ ] **Step 3: 구현**

```ts
// policyGroups.ts
import type { LucideIcon } from 'lucide-react';
import { Users, Wallet, Pencil, Info } from 'lucide-react';

export type PolicyGroupId = 'eligibility' | 'benefits' | 'apply' | 'more';
export type PolicyGroupTone = 'brand' | 'amber' | 'success' | 'neutral';

export interface PolicyGroup {
  id: PolicyGroupId;
  label: string;
  description: string;
  Icon: LucideIcon;
  tone: PolicyGroupTone;
}

export const POLICY_GROUPS: PolicyGroup[] = [
  { id: 'eligibility', label: '받을 수 있는 사람', description: '이 정책을 받기 위한 조건을 알려드려요', Icon: Users, tone: 'brand' },
  { id: 'benefits', label: '받는 혜택', description: '어떤 지원을 받게 되는지 알려드려요', Icon: Wallet, tone: 'amber' },
  { id: 'apply', label: '신청하기', description: '어떻게 신청하는지 알려드려요', Icon: Pencil, tone: 'success' },
  { id: 'more', label: '더 알아보기', description: '문의·첨부·놓치기 쉬운 점·Q&A를 모았어요', Icon: Info, tone: 'neutral' },
];
```

- [ ] **Step 4: 테스트 성공 확인**

Run: `cd frontend && npx vitest run src/components/policy/groups/__tests__/policyGroups.test.ts`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/groups/policyGroups.ts frontend/src/components/policy/groups/__tests__/policyGroups.test.ts
git commit -m "feat(policy): 그룹 정의 상수 및 타입 추가"
```

---

## Task 2: PolicyGroupHeader 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/groups/PolicyGroupHeader.tsx`
- Test: `frontend/src/components/policy/groups/__tests__/PolicyGroupHeader.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
// PolicyGroupHeader.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyGroupHeader } from '../PolicyGroupHeader';
import { POLICY_GROUPS } from '../policyGroups';

describe('PolicyGroupHeader', () => {
  it('그룹 라벨과 설명을 렌더한다', () => {
    const eligibility = POLICY_GROUPS[0];
    render(<PolicyGroupHeader group={eligibility} />);
    expect(screen.getByRole('heading', { level: 2, name: eligibility.label })).toBeInTheDocument();
    expect(screen.getByText(eligibility.description)).toBeInTheDocument();
  });

  it('section에 그룹 id를 anchor로 부여한다', () => {
    const benefits = POLICY_GROUPS[1];
    const { container } = render(<PolicyGroupHeader group={benefits} />);
    expect(container.querySelector('section')?.id).toBe('benefits');
  });

  it('tone별 배경 클래스가 다르게 적용된다', () => {
    const { container, rerender } = render(<PolicyGroupHeader group={POLICY_GROUPS[0]} />);
    const brandIcon = container.querySelector('[data-icon-box]');
    expect(brandIcon?.className).toMatch(/bg-brand-100/);

    rerender(<PolicyGroupHeader group={POLICY_GROUPS[1]} />);
    const amberIcon = container.querySelector('[data-icon-box]');
    expect(amberIcon?.className).toMatch(/bg-amber-100/);
  });
});
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/groups/__tests__/PolicyGroupHeader.test.tsx`
Expected: FAIL — "Cannot find module"

- [ ] **Step 3: 구현**

```tsx
// PolicyGroupHeader.tsx
import { cn } from '@/lib/cn';
import type { PolicyGroup, PolicyGroupTone } from './policyGroups';

interface Props {
  group: PolicyGroup;
}

const TONE_STYLES: Record<PolicyGroupTone, string> = {
  brand: 'bg-brand-100 text-brand-800',
  amber: 'bg-amber-100 text-amber-700',
  success: 'bg-success-100 text-success-700',
  neutral: 'bg-neutral-100 text-neutral-600',
};

export function PolicyGroupHeader({ group }: Props) {
  const { id, label, description, Icon, tone } = group;
  return (
    <section id={id} className="mt-12 scroll-mt-24">
      <div className="mb-6 flex items-center gap-4 border-b-2 border-neutral-100 pb-5">
        <div
          data-icon-box
          className={cn(
            'flex h-13 w-13 shrink-0 items-center justify-center rounded-2xl',
            TONE_STYLES[tone],
          )}
        >
          <Icon className="h-7 w-7" strokeWidth={2} />
        </div>
        <div>
          <h2 className="text-xl font-semibold text-neutral-900">{label}</h2>
          <p className="mt-0.5 text-sm text-neutral-500">{description}</p>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: 테스트 실행 → 성공 확인**

Run: `cd frontend && npx vitest run src/components/policy/groups/__tests__/PolicyGroupHeader.test.tsx`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/groups/PolicyGroupHeader.tsx frontend/src/components/policy/groups/__tests__/PolicyGroupHeader.test.tsx
git commit -m "feat(policy): PolicyGroupHeader 컴포넌트 추가"
```

---

## Task 3: PolicyGroupDivider 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/groups/PolicyGroupDivider.tsx`
- Test: `frontend/src/components/policy/groups/__tests__/PolicyGroupDivider.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyGroupDivider } from '../PolicyGroupDivider';

describe('PolicyGroupDivider', () => {
  it('다음 그룹 라벨을 표시한다', () => {
    render(<PolicyGroupDivider nextLabel="받는 혜택" />);
    expect(screen.getByText(/다음 · 받는 혜택/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/groups/__tests__/PolicyGroupDivider.test.tsx`
Expected: FAIL

- [ ] **Step 3: 구현**

```tsx
// PolicyGroupDivider.tsx
interface Props {
  nextLabel: string;
}

export function PolicyGroupDivider({ nextLabel }: Props) {
  return (
    <div
      aria-hidden
      className="my-12 flex items-center gap-4 text-sm font-medium text-neutral-400"
    >
      <div className="h-px flex-1 bg-neutral-200" />
      <span>다음 · {nextLabel}</span>
      <div className="h-px flex-1 bg-neutral-200" />
    </div>
  );
}
```

- [ ] **Step 4: 성공 확인**

Run: `cd frontend && npx vitest run src/components/policy/groups/__tests__/PolicyGroupDivider.test.tsx`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/groups/PolicyGroupDivider.tsx frontend/src/components/policy/groups/__tests__/PolicyGroupDivider.test.tsx
git commit -m "feat(policy): PolicyGroupDivider 컴포넌트 추가"
```

---

## Task 4: PolicyDetailPage에 그룹 구조 적용

기존 본문 섹션 순서를 spec §3 IA 매핑대로 재배치하고 그룹 헤더/디바이더를 삽입한다.

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: 기존 페이지 구조 확인**

Read: `frontend/src/pages/PolicyDetailPage.tsx:531-870` (현재 본문 섹션 나열 부분)

- [ ] **Step 2: 그룹 매핑 적용**

`PolicyDetailPage.tsx`의 `<main>` 내부를 다음 구조로 재배치:

```tsx
import { PolicyGroupHeader } from '@/components/policy/groups/PolicyGroupHeader';
import { PolicyGroupDivider } from '@/components/policy/groups/PolicyGroupDivider';
import { POLICY_GROUPS } from '@/components/policy/groups/policyGroups';

// <main className="lg:col-span-8"> 내부

// === 결정 영역 (현재 헤더+요약+메타 그대로, Phase 2에서 추출 예정) ===
<PolicyHeader … />
{guide && <OneLineSummaryCard … />}
<section id="policy-summary-section" …>정책 요약 (원문)</section>
<PolicyMetaSummary … />
{policy.applyUrl && <공식 신청 페이지 CTA … />}
{(policy.businessPeriodStart …) && <사업기간/지원규모 chip … />}

// === 그룹 1: 받을 수 있는 사람 ===
<PolicyGroupHeader group={POLICY_GROUPS[0]} />
{/* 지원대상 PairedSection */}
{policy.additionalQualification && <추가 자격조건 …>}
{policy.participationRestriction && <참여 제한 대상 …>}
{/* 선정기준 PairedSection */}
{policy.screeningMethod && <심사방법 …>}
{policy.subRegions?.length >= 2 && <SubRegionSection subRegions={policy.subRegions} />}

<PolicyGroupDivider nextLabel="받는 혜택" />

// === 그룹 2: 받는 혜택 ===
<PolicyGroupHeader group={POLICY_GROUPS[1]} />
{guide && <HighlightsCard … />}
{/* 지원내용 PairedSection */}

<PolicyGroupDivider nextLabel="신청하기" />

// === 그룹 3: 신청하기 ===
<PolicyGroupHeader group={POLICY_GROUPS[2]} />
{guide?.applyMethod && <GuideListSectionCard title="신청방법" emoji="📝" … />}
{guide?.deadlineNote && <GuideListSectionCard title="신청기한" emoji="📅" … />}
{guide?.requiredDocuments && <GuideListSectionCard title="제출서류" emoji="📂" … />}
{/* fallback 섹션들 (제출서류, 신청방법, 마감안내) — 이 안에 유지 */}

<PolicyGroupDivider nextLabel="더 알아보기" />

// === 그룹 4: 더 알아보기 ===
<PolicyGroupHeader group={POLICY_GROUPS[3]} />
{guide?.contact && <GuideListSectionCard title="문의처" emoji="☎" … />}
<ReferenceSiteSection … />
<AttachmentSection … />
{policy.additionalNotes && <기타사항 …>}
{guide && <PitfallsCard … />}  {/* 그룹 4로 이동 */}
<QnaChatSection … />

// 공식 신청 채널 박스 (현재 :808-824) — 삭제
```

- [ ] **Step 3: 타입체크 + 빌드 확인**

```bash
cd frontend && npx tsc --noEmit
```
Expected: 에러 없음

```bash
cd frontend && npm run build
```
Expected: 성공

- [ ] **Step 4: 개발 서버에서 시각 확인**

```bash
cd frontend && npm run dev
```

브라우저에서 임의 정책 상세(`/policies/<id>`) 진입.
체크리스트:
- [ ] 4그룹 헤더가 모두 보인다
- [ ] 그룹 헤더 사이 디바이더가 "다음 · X" 형태로 표시된다
- [ ] PitfallsCard가 그룹 4 안에 있다
- [ ] 페이지 하단 "공식 신청 채널" 박스가 사라졌다

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "refactor(policy): 정책 상세 페이지에 4그룹 구조 적용

- 결정 영역 + 받을 수 있는 사람 + 받는 혜택 + 신청하기 + 더 알아보기
- PitfallsCard를 그룹 4로 이동
- 페이지 하단 공식 신청 채널 박스 제거 (Phase 4에서 sticky/bottom bar로 대체 예정)"
```

---

# Phase 2 — 결정 영역 정리

## Task 5: DecisionMetaGrid 컴포넌트 (PolicyMetaSummary 재편)

기존 4칸(기준연도·지원주기·제공유형·문의처) → 신규 4칸(마감일·지원규모·기준연도·지원주기). 문의처는 그룹 4의 `GuideListSectionCard` 가 이미 담당.

**Files:**
- Create: `frontend/src/components/policy/decision/DecisionMetaGrid.tsx`
- Test: `frontend/src/components/policy/decision/__tests__/DecisionMetaGrid.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DecisionMetaGrid } from '../DecisionMetaGrid';

describe('DecisionMetaGrid', () => {
  it('4개 메타가 모두 있을 때 렌더', () => {
    render(
      <DecisionMetaGrid
        applyEnd="2026-05-29"
        supportScale={60000}
        referenceYear={2026}
        supportCycle="1년 한도"
      />,
    );
    expect(screen.getByText('마감일')).toBeInTheDocument();
    expect(screen.getByText('지원규모')).toBeInTheDocument();
    expect(screen.getByText('기준연도')).toBeInTheDocument();
    expect(screen.getByText('지원주기')).toBeInTheDocument();
  });

  it('지원규모는 toLocaleString으로 표시', () => {
    render(
      <DecisionMetaGrid
        applyEnd={null}
        supportScale={60000}
        referenceYear={null}
        supportCycle={null}
      />,
    );
    expect(screen.getByText('60,000명')).toBeInTheDocument();
  });

  it('모두 null이면 컴포넌트는 null을 반환', () => {
    const { container } = render(
      <DecisionMetaGrid
        applyEnd={null}
        supportScale={null}
        referenceYear={null}
        supportCycle={null}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('마감일에는 D-day가 함께 표시된다', () => {
    const future = new Date(Date.now() + 10 * 24 * 60 * 60 * 1000)
      .toISOString().slice(0, 10);
    render(
      <DecisionMetaGrid
        applyEnd={future}
        supportScale={null}
        referenceYear={null}
        supportCycle={null}
      />,
    );
    expect(screen.getByText(/D-10|D-9|D-11/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/decision/__tests__/DecisionMetaGrid.test.tsx`

- [ ] **Step 3: 구현**

```tsx
// DecisionMetaGrid.tsx
import { Calendar, Users, CalendarDays, Repeat } from 'lucide-react';

interface Props {
  applyEnd: string | null;
  supportScale: number | null;
  referenceYear: number | null;
  supportCycle: string | null;
}

function daysUntil(dateIso: string): number {
  const target = new Date(dateIso).getTime();
  const now = Date.now();
  return Math.ceil((target - now) / (24 * 60 * 60 * 1000));
}

function formatApplyEnd(dateIso: string): string {
  const d = new Date(dateIso);
  const dDay = daysUntil(dateIso);
  const md = `${d.getMonth() + 1}/${d.getDate()}`;
  return dDay >= 0 ? `${md} (D-${dDay})` : md;
}

export function DecisionMetaGrid({ applyEnd, supportScale, referenceYear, supportCycle }: Props) {
  const items: { Icon: typeof Calendar; label: string; value: string }[] = [];
  if (applyEnd) items.push({ Icon: Calendar, label: '마감일', value: formatApplyEnd(applyEnd) });
  if (supportScale != null) items.push({ Icon: Users, label: '지원규모', value: `${supportScale.toLocaleString()}명` });
  if (referenceYear) items.push({ Icon: CalendarDays, label: '기준연도', value: `'${String(referenceYear).slice(-2)}년` });
  if (supportCycle) items.push({ Icon: Repeat, label: '지원주기', value: supportCycle });

  if (items.length === 0) return null;

  return (
    <section className="mb-6 overflow-hidden rounded-2xl border border-neutral-200 bg-white">
      <div className="grid grid-cols-2 divide-x divide-y divide-neutral-200 sm:grid-cols-4 sm:divide-y-0">
        {items.map(({ Icon, label, value }) => (
          <div key={label} className="flex flex-col items-center gap-1.5 px-4 py-5 text-center">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100">
              <Icon className="h-4 w-4 text-brand-800" />
            </div>
            <span className="text-xs text-neutral-500">{label}</span>
            <span className="text-sm font-semibold text-neutral-900">{value}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 4: 테스트 성공 확인**

Run: `cd frontend && npx vitest run src/components/policy/decision/__tests__/DecisionMetaGrid.test.tsx`
Expected: PASS (4 tests)

- [ ] **Step 5: PolicyDetailPage에서 PolicyMetaSummary → DecisionMetaGrid 교체**

`PolicyDetailPage.tsx` 의 `<PolicyMetaSummary>` 사용 부분을 다음으로 대체:

```tsx
<DecisionMetaGrid
  applyEnd={policy.applyEnd}
  supportScale={policy.supportScale ?? null}
  referenceYear={policy.referenceYear}
  supportCycle={policy.supportCycle}
/>
```

기존 `PolicyMetaSummary` 컴포넌트 정의(`PolicyDetailPage.tsx:200-257`)는 더 이상 사용되지 않으면 삭제.

- [ ] **Step 6: 빌드 + 커밋**

```bash
cd frontend && npx tsc --noEmit && npm run build
```

```bash
git add frontend/src/components/policy/decision/ frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(policy): DecisionMetaGrid 추가 — 마감일·지원규모·기준연도·지원주기 4칸"
```

---

## Task 6: SubRegionInline + SubRegionSection 분기

**Files:**
- Create: `frontend/src/components/policy/decision/SubRegionInline.tsx`
- Modify: `frontend/src/pages/PolicyDetailPage.tsx` (SubRegionSection 렌더 조건 변경)
- Test: `frontend/src/components/policy/decision/__tests__/SubRegionInline.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SubRegionInline } from '../SubRegionInline';
import type { PolicySubRegion } from '@/types/policy';

const sr = (name: string, sidoName = '서울'): PolicySubRegion => ({
  code: name, sidoCode: 'S', sidoName, name,
});

describe('SubRegionInline', () => {
  it('비어있으면 null', () => {
    const { container } = render(<SubRegionInline subRegions={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('5개 이하면 전체를 점으로 연결', () => {
    render(<SubRegionInline subRegions={[sr('관악구'), sr('동작구'), sr('강서구')]} />);
    expect(screen.getByText(/관악구·동작구·강서구/)).toBeInTheDocument();
  });

  it('6개 이상이면 첫 1개 + 외 N개', () => {
    const list = ['관악구', '동작구', '강서구', '강남구', '서초구', '송파구'].map((n) => sr(n));
    render(<SubRegionInline subRegions={list} />);
    expect(screen.getByText(/관악구 외 5개/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/decision/__tests__/SubRegionInline.test.tsx`

- [ ] **Step 3: 구현**

```tsx
// SubRegionInline.tsx
import type { PolicySubRegion } from '@/types/policy';

interface Props {
  subRegions: PolicySubRegion[] | null | undefined;
}

const INLINE_THRESHOLD = 5;

export function SubRegionInline({ subRegions }: Props) {
  if (!subRegions || subRegions.length === 0) return null;
  if (subRegions.length <= INLINE_THRESHOLD) {
    return <span>{subRegions.map((s) => s.name).join('·')}</span>;
  }
  const first = subRegions[0].name;
  const rest = subRegions.length - 1;
  return <span>{first} 외 {rest}개</span>;
}
```

- [ ] **Step 4: 테스트 성공**

Run: `cd frontend && npx vitest run src/components/policy/decision/__tests__/SubRegionInline.test.tsx`
Expected: PASS

- [ ] **Step 5: PolicyDetailPage에서 적용**

`PolicyHeader` 의 메타 라인에 `SubRegionInline` 을 지역 옆에 추가:

```tsx
<span className="flex items-center gap-1">
  <MapPin className="h-4 w-4" />
  {getRegionName(policy.regionCode, policy.sourceType)}
  {policy.subRegions && policy.subRegions.length > 0 && (
    <>
      <span className="mx-1 text-neutral-300">·</span>
      <SubRegionInline subRegions={policy.subRegions} />
    </>
  )}
</span>
```

그리고 그룹 1 안의 기존 `SubRegionSection` 렌더 조건을 6개 이상으로 변경:

```tsx
{policy.subRegions && policy.subRegions.length >= 6 && (
  <SubRegionSection subRegions={policy.subRegions} />
)}
```

- [ ] **Step 6: 빌드 + 커밋**

```bash
cd frontend && npx tsc --noEmit && npm run build
```

```bash
git add frontend/src/components/policy/decision/SubRegionInline.tsx frontend/src/components/policy/decision/__tests__/SubRegionInline.test.tsx frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(policy): 세부 지역을 결정 영역 메타 라인에 통합 (5개 이하 인라인)"
```

---

## Task 7: OneLineSummaryCard 스타일 강화

**Files:**
- Modify: `frontend/src/components/policy/OneLineSummaryCard.tsx`

- [ ] **Step 1: 코드 교체**

기존 파일 전체를 다음으로 교체:

```tsx
// OneLineSummaryCard.tsx
import { Sparkles } from 'lucide-react';

interface Props {
  oneLineSummary: string;
}

export function OneLineSummaryCard({ oneLineSummary }: Props) {
  return (
    <section className="mb-6 rounded-2xl border border-brand-100 bg-gradient-to-br from-brand-50 to-white p-6">
      <div className="flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-800 text-white">
          <Sparkles className="h-5 w-5" />
        </div>
        <div>
          <div className="mb-1 text-sm font-semibold text-brand-800">AI 한 줄 요약</div>
          <p className="text-[15px] leading-relaxed text-neutral-800">{oneLineSummary}</p>
          <p className="mt-2 text-xs text-neutral-500">
            AI가 정리한 해석이에요. 정확한 조건은 아래 원문과 공식 공고에서 확인해주세요.
          </p>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: 테스트가 있으면 통과 확인 / 없으면 시각 확인**

```bash
cd frontend && npx vitest run src/components/policy/__tests__/ 2>&1 | grep -i "oneline" || echo "no test"
```

기존 테스트가 있다면 PASS 여부 확인. 없으면 dev 서버에서 시각 확인.

- [ ] **Step 3: 빌드**

```bash
cd frontend && npx tsc --noEmit && npm run build
```

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/policy/OneLineSummaryCard.tsx
git commit -m "style(policy): OneLineSummaryCard에 Sparkles 아이콘 + brand 그라데이션 적용"
```

---

## Task 8: 결정 영역 정리 — 본문 중간 신청 CTA 박스 제거 준비

Phase 4 에서 sticky/bottom bar 로 대체되므로 본문 중간의 `policy.applyUrl` 버튼 (`PolicyDetailPage.tsx:580-592`) 은 Phase 4 작업까지는 그대로 둔다. 본 Task 에서는 결정 영역의 시각 흐름만 정리.

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: 결정 영역 섹션 순서 조정**

PolicyDetailPage 의 결정 영역(`<main>` 직하 첫 블록들)을 다음 순서로 재배치:

```
1. PolicyHeader (제목 + 배지 + 메타 + 태그)  — SubRegionInline 통합됨
2. OneLineSummaryCard  (가이드 있을 때만)
3. DecisionMetaGrid    (마감일/지원규모/기준연도/지원주기)
4. 사업기간/지원규모 chip  (기존 위치 유지, 결정 영역 끝)
5. policy.applyUrl 인라인 CTA  (Phase 4까지 임시 유지)
6. 정책 요약 (원문) — 그룹 1 직전이 아닌, "지원대상 PairedSection" 의 originalContent 로 흡수 (Task 8.2)
```

- [ ] **Step 2: "정책 요약(원문)" 섹션 제거 + 지원대상 PairedSection의 원문 측에 보강**

기존 `<section id="policy-summary-section">` 블록 삭제. `policy.summary` 는 PairedSection 의 원문 측에서 이미 다뤄지지 않으므로, 별도 카드는 유지하되 위치만 결정 영역에서 그룹 1 내부로 이동. 구체적 위치는 spec §3 그룹 1 매핑에 따라 "지원대상" PairedSection 바로 위에 두지 않고 **그대로 별도 카드로 그룹 1 시작에 둔다** (정보 손실 방지). 이 처리는 인터프리테이션 영역이므로 결정 보류 — 본 Task에서는 결정 영역에서만 빼내고 그룹 1 시작에 그대로 옮긴다.

이동 후:
```tsx
// 그룹 1 헤더 다음, 첫 카드로
<section
  id="policy-summary-section"
  className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6"
>
  <h3 className="mb-3 text-base font-semibold text-neutral-900">정책 요약</h3>
  <FormattedPolicyText text={policy.summary} />
</section>
```

- [ ] **Step 3: 빌드 + 시각 확인**

```bash
cd frontend && npx tsc --noEmit && npm run build && npm run dev
```

브라우저 체크리스트:
- [ ] 결정 영역에 5개 요소가 자연스럽게 흐른다 (헤더 → 요약 → 메타 → 사업기간chip → 신청CTA)
- [ ] "정책 요약(원문)" 이 그룹 1 시작 위치로 이동했다

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "refactor(policy): 결정 영역에서 정책 요약 카드를 그룹 1 시작으로 이동"
```

---

# Phase 3 — 네비게이션 (TOC + 모바일 칩)

## Task 9: usePolicyScrollSpy 훅

**Files:**
- Create: `frontend/src/components/policy/navigation/usePolicyScrollSpy.ts`
- Test: `frontend/src/components/policy/navigation/__tests__/usePolicyScrollSpy.test.ts`

- [ ] **Step 1: 실패 테스트 작성**

```ts
import { renderHook } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { usePolicyScrollSpy } from '../usePolicyScrollSpy';

class MockIntersectionObserver {
  static instances: MockIntersectionObserver[] = [];
  callback: IntersectionObserverCallback;
  observed: Element[] = [];
  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback;
    MockIntersectionObserver.instances.push(this);
  }
  observe(el: Element) { this.observed.push(el); }
  unobserve() {}
  disconnect() {}
  trigger(entries: Partial<IntersectionObserverEntry>[]) {
    this.callback(entries as IntersectionObserverEntry[], this as unknown as IntersectionObserver);
  }
}

describe('usePolicyScrollSpy', () => {
  beforeEach(() => {
    MockIntersectionObserver.instances = [];
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
    document.body.innerHTML = `
      <section id="eligibility"></section>
      <section id="benefits"></section>
      <section id="apply"></section>
      <section id="more"></section>
    `;
  });

  it('초기 active는 첫 그룹', () => {
    const { result } = renderHook(() => usePolicyScrollSpy(['eligibility', 'benefits', 'apply', 'more']));
    expect(result.current.activeId).toBe('eligibility');
  });

  it('intersection 변경 시 active 변경', () => {
    const { result } = renderHook(() => usePolicyScrollSpy(['eligibility', 'benefits', 'apply', 'more']));
    const observer = MockIntersectionObserver.instances[0];
    observer.trigger([
      { target: document.getElementById('benefits')!, isIntersecting: true, intersectionRatio: 0.5 },
    ]);
    expect(result.current.activeId).toBe('benefits');
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/usePolicyScrollSpy.test.ts`

- [ ] **Step 3: 구현**

```ts
// usePolicyScrollSpy.ts
import { useEffect, useState } from 'react';

export function usePolicyScrollSpy(ids: string[]) {
  const [activeId, setActiveId] = useState<string>(ids[0] ?? '');

  useEffect(() => {
    if (typeof window === 'undefined' || typeof IntersectionObserver === 'undefined') return;

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
        if (visible.length > 0) {
          setActiveId(visible[0].target.id);
        }
      },
      { rootMargin: '-96px 0px -50% 0px', threshold: [0, 0.3] },
    );

    const elements = ids
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null);

    elements.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [ids]);

  return { activeId };
}
```

- [ ] **Step 4: 테스트 성공 확인**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/usePolicyScrollSpy.test.ts`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/navigation/usePolicyScrollSpy.ts frontend/src/components/policy/navigation/__tests__/usePolicyScrollSpy.test.ts
git commit -m "feat(policy): usePolicyScrollSpy 훅 추가 — IntersectionObserver 기반 active 그룹 추적"
```

---

## Task 10: PolicyToc 컴포넌트 (데스크톱 우측 sticky)

**Files:**
- Create: `frontend/src/components/policy/navigation/PolicyToc.tsx`
- Test: `frontend/src/components/policy/navigation/__tests__/PolicyToc.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyToc } from '../PolicyToc';

describe('PolicyToc', () => {
  it('4개 그룹 라벨을 모두 렌더한다', () => {
    render(<PolicyToc activeId="eligibility" />);
    expect(screen.getByText('받을 수 있는 사람')).toBeInTheDocument();
    expect(screen.getByText('받는 혜택')).toBeInTheDocument();
    expect(screen.getByText('신청하기')).toBeInTheDocument();
    expect(screen.getByText('더 알아보기')).toBeInTheDocument();
  });

  it('active 그룹은 aria-current="location" 으로 표시', () => {
    render(<PolicyToc activeId="benefits" />);
    const items = screen.getAllByRole('link');
    const active = items.find((el) => el.getAttribute('aria-current') === 'location');
    expect(active?.textContent).toMatch(/받는 혜택/);
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/PolicyToc.test.tsx`

- [ ] **Step 3: 구현**

```tsx
// PolicyToc.tsx
import { List } from 'lucide-react';
import { cn } from '@/lib/cn';
import { POLICY_GROUPS } from '@/components/policy/groups/policyGroups';

interface Props {
  activeId: string;
}

export function PolicyToc({ activeId }: Props) {
  return (
    <nav className="rounded-2xl border border-neutral-200 bg-white p-5 shadow-card">
      <div className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-neutral-500">
        <List className="h-3.5 w-3.5" />
        <span>목차</span>
      </div>
      <ul>
        {POLICY_GROUPS.map((g) => {
          const isActive = g.id === activeId;
          const { Icon } = g;
          return (
            <li key={g.id}>
              <a
                href={`#${g.id}`}
                aria-current={isActive ? 'location' : undefined}
                className={cn(
                  'my-0.5 flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors',
                  'border-l-[3px] border-transparent',
                  isActive
                    ? 'border-brand-800 bg-brand-50 font-semibold text-brand-800'
                    : 'text-neutral-600 hover:bg-neutral-50 hover:text-neutral-900',
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span>{g.label}</span>
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
```

- [ ] **Step 4: 테스트 성공**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/PolicyToc.test.tsx`
Expected: PASS (2 tests)

- [ ] **Step 5: PolicyDetailPage 우측 sticky에 추가**

`PolicyDetailPage.tsx` 의 `<aside>` 안 `<div className="sticky top-24 space-y-6">` 의 최상단에 추가:

```tsx
import { usePolicyScrollSpy } from '@/components/policy/navigation/usePolicyScrollSpy';
import { PolicyToc } from '@/components/policy/navigation/PolicyToc';

// 컴포넌트 본문에서:
const { activeId } = usePolicyScrollSpy(['eligibility', 'benefits', 'apply', 'more']);

// <aside> 의 sticky 컨테이너:
<div className="sticky top-24 space-y-6">
  <PolicyToc activeId={activeId} />
  <EligibilityCard … />
  <NotificationCtaCard … />
</div>
```

- [ ] **Step 6: 시각 확인**

```bash
cd frontend && npm run dev
```

체크리스트:
- [ ] 정책 상세 페이지에 우측 sticky TOC 가 보인다
- [ ] 스크롤 시 active 그룹이 viewport 와 일치하게 바뀐다
- [ ] TOC 항목 클릭 시 해당 그룹으로 부드럽게 스크롤 (브라우저 기본 smooth)

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/components/policy/navigation/PolicyToc.tsx frontend/src/components/policy/navigation/__tests__/PolicyToc.test.tsx frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(policy): PolicyToc 데스크톱 우측 sticky 목차 추가"
```

---

## Task 11: PolicyMobileNav 컴포넌트 (모바일 상단 sticky 칩)

**Files:**
- Create: `frontend/src/components/policy/navigation/PolicyMobileNav.tsx`
- Test: `frontend/src/components/policy/navigation/__tests__/PolicyMobileNav.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyMobileNav } from '../PolicyMobileNav';

describe('PolicyMobileNav', () => {
  it('4개 칩을 렌더한다', () => {
    render(<PolicyMobileNav activeId="eligibility" visible />);
    expect(screen.getByText('받을 수 있는 사람')).toBeInTheDocument();
    expect(screen.getByText('받는 혜택')).toBeInTheDocument();
    expect(screen.getByText('신청하기')).toBeInTheDocument();
    expect(screen.getByText('더 알아보기')).toBeInTheDocument();
  });

  it('visible=false면 보이지 않는다', () => {
    const { container } = render(<PolicyMobileNav activeId="eligibility" visible={false} />);
    expect(container.firstChild).toHaveClass('invisible');
  });

  it('active 칩은 aria-current 가 부여된다', () => {
    render(<PolicyMobileNav activeId="benefits" visible />);
    const links = screen.getAllByRole('link');
    const active = links.find((el) => el.getAttribute('aria-current') === 'location');
    expect(active?.textContent).toMatch(/받는 혜택/);
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/PolicyMobileNav.test.tsx`

- [ ] **Step 3: 구현**

```tsx
// PolicyMobileNav.tsx
import { useEffect, useRef } from 'react';
import { cn } from '@/lib/cn';
import { POLICY_GROUPS } from '@/components/policy/groups/policyGroups';

interface Props {
  activeId: string;
  visible: boolean;
}

export function PolicyMobileNav({ activeId, visible }: Props) {
  const activeChipRef = useRef<HTMLAnchorElement | null>(null);

  useEffect(() => {
    if (visible && activeChipRef.current) {
      activeChipRef.current.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
    }
  }, [activeId, visible]);

  return (
    <div
      className={cn(
        'sticky top-0 z-10 -mx-4 border-b border-neutral-200 bg-white/95 backdrop-blur lg:hidden',
        visible ? 'visible' : 'invisible',
      )}
    >
      <div className="flex gap-2 overflow-x-auto px-4 py-3 [&::-webkit-scrollbar]:hidden">
        {POLICY_GROUPS.map((g) => {
          const isActive = g.id === activeId;
          const { Icon } = g;
          return (
            <a
              key={g.id}
              ref={isActive ? activeChipRef : undefined}
              href={`#${g.id}`}
              aria-current={isActive ? 'location' : undefined}
              className={cn(
                'inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-3.5 py-2 text-sm transition-colors',
                isActive
                  ? 'border-brand-800 bg-brand-800 font-semibold text-white'
                  : 'border-neutral-200 bg-white font-medium text-neutral-600',
              )}
            >
              <Icon className="h-4 w-4" />
              <span>{g.label}</span>
            </a>
          );
        })}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: 결정 영역 통과 감지 + PolicyDetailPage 통합**

`PolicyDetailPage.tsx` 에 결정 영역의 끝(또는 그룹 1 시작) 가시성을 추적:

```tsx
import { useRef, useState } from 'react';
import { PolicyMobileNav } from '@/components/policy/navigation/PolicyMobileNav';

// 컴포넌트 본문:
const decisionEndRef = useRef<HTMLDivElement | null>(null);
const [navVisible, setNavVisible] = useState(false);

useEffect(() => {
  const el = decisionEndRef.current;
  if (!el || typeof IntersectionObserver === 'undefined') return;
  const observer = new IntersectionObserver(
    ([entry]) => setNavVisible(!entry.isIntersecting && entry.boundingClientRect.top < 0),
    { threshold: 0 },
  );
  observer.observe(el);
  return () => observer.disconnect();
}, []);

// 그룹 1 헤더 직전에 sentinel
<div ref={decisionEndRef} />
<PolicyGroupHeader group={POLICY_GROUPS[0]} />

// 페이지 최상단(또는 main 시작 직전)에 모바일 네비:
<PolicyMobileNav activeId={activeId} visible={navVisible} />
```

- [ ] **Step 5: 테스트 성공 + 시각 확인**

```bash
cd frontend && npx vitest run src/components/policy/navigation/__tests__/PolicyMobileNav.test.tsx
```

dev 서버에서 모바일 viewport(375px)로 확인:
- [ ] 결정 영역 보일 때는 칩이 안 보인다
- [ ] 그룹 1 진입 후 sticky 칩이 상단에 나타난다
- [ ] 스크롤로 그룹 변경 시 active 칩이 자동으로 가시 영역에 들어온다

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/components/policy/navigation/PolicyMobileNav.tsx frontend/src/components/policy/navigation/__tests__/PolicyMobileNav.test.tsx frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(policy): PolicyMobileNav 모바일 상단 sticky 칩 네비 추가"
```

---

## Task 12: URL 해시 동기화 + 딥링킹

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: 초기 진입 시 해시로 스크롤**

`PolicyDetailPage.tsx` 의 첫 `useEffect` (현재 `window.scrollTo(...)`) 직후에 추가:

```tsx
useEffect(() => {
  const hash = window.location.hash.replace('#', '');
  if (!hash) return;
  const valid = ['eligibility', 'benefits', 'apply', 'more'];
  if (!valid.includes(hash)) return;
  // 데이터 로드 직후 한 차례 스크롤
  const timer = setTimeout(() => {
    const el = document.getElementById(hash);
    if (el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
  }, 100);
  return () => clearTimeout(timer);
}, [policyId, policy]);
```

- [ ] **Step 2: 그룹 헤더 anchor 에 scroll-margin-top 보장**

`PolicyGroupHeader.tsx` 의 section 에 이미 `scroll-mt-24` 가 있는지 확인. (Task 2에서 추가됨)

- [ ] **Step 3: TOC/모바일 칩 클릭 시 history.replaceState 로 URL 갱신**

`PolicyToc.tsx`, `PolicyMobileNav.tsx` 의 `<a href="#...">` 클릭 동작은 브라우저 기본 동작에 위임 (해시 갱신 자동). 별도 코드 불필요.

- [ ] **Step 4: 시각 확인**

```bash
cd frontend && npm run dev
```

- [ ] `/policies/1#apply` 로 직접 진입 시 자동으로 "신청하기" 그룹으로 스크롤
- [ ] TOC 클릭 시 URL 에 `#eligibility` 등이 반영
- [ ] 뒤로가기 시 이전 해시 위치로 복귀

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(policy): URL 해시 기반 그룹 딥링킹 — /policies/:id#apply 진입 시 자동 스크롤"
```

---

# Phase 4 — 모바일 fixed bottom bar

## Task 13: PolicyMobileBottomBar 컴포넌트

**Files:**
- Create: `frontend/src/components/policy/navigation/PolicyMobileBottomBar.tsx`
- Test: `frontend/src/components/policy/navigation/__tests__/PolicyMobileBottomBar.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PolicyMobileBottomBar } from '../PolicyMobileBottomBar';

describe('PolicyMobileBottomBar', () => {
  it('applyUrl이 있으면 신청 버튼이 렌더된다', () => {
    render(
      <PolicyMobileBottomBar
        applyUrl="https://example.com/apply"
        isSubscribed={false}
        visible
        onNotificationClick={() => {}}
      />,
    );
    expect(screen.getByRole('link', { name: /공식 신청 페이지/ })).toBeInTheDocument();
  });

  it('applyUrl 도 sourceUrl 도 없으면 신청 버튼 자체가 없다', () => {
    render(
      <PolicyMobileBottomBar
        applyUrl={null}
        sourceUrl={null}
        isSubscribed={false}
        visible
        onNotificationClick={() => {}}
      />,
    );
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('알림 토글 버튼을 누르면 콜백이 호출된다', () => {
    const onClick = vi.fn();
    render(
      <PolicyMobileBottomBar
        applyUrl="https://example.com"
        isSubscribed={false}
        visible
        onNotificationClick={onClick}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /알림/ }));
    expect(onClick).toHaveBeenCalled();
  });

  it('visible=false면 hidden 처리', () => {
    const { container } = render(
      <PolicyMobileBottomBar
        applyUrl="https://example.com"
        isSubscribed={false}
        visible={false}
        onNotificationClick={() => {}}
      />,
    );
    expect(container.firstChild).toHaveClass('translate-y-full');
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/PolicyMobileBottomBar.test.tsx`

- [ ] **Step 3: 구현**

```tsx
// PolicyMobileBottomBar.tsx
import { Bell, ExternalLink } from 'lucide-react';
import { cn } from '@/lib/cn';

interface Props {
  applyUrl?: string | null;
  sourceUrl?: string | null;
  isSubscribed: boolean;
  visible: boolean;
  onNotificationClick: () => void;
}

export function PolicyMobileBottomBar({
  applyUrl,
  sourceUrl,
  isSubscribed,
  visible,
  onNotificationClick,
}: Props) {
  const targetUrl = applyUrl ?? sourceUrl ?? null;

  return (
    <div
      className={cn(
        'fixed bottom-0 left-0 right-0 z-20 border-t border-neutral-200 bg-white/95 px-4 py-3 backdrop-blur lg:hidden',
        'transition-transform duration-200',
        visible ? 'translate-y-0' : 'translate-y-full',
      )}
    >
      <div className="mx-auto flex max-w-7xl items-center gap-2">
        <button
          type="button"
          onClick={onNotificationClick}
          aria-label={isSubscribed ? '알림 해제' : '알림 받기'}
          className={cn(
            'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border transition-colors',
            isSubscribed
              ? 'border-brand-800 bg-brand-100 text-brand-800'
              : 'border-neutral-200 bg-white text-neutral-600',
          )}
        >
          <Bell className={cn('h-5 w-5', isSubscribed && 'fill-brand-800')} />
        </button>
        {targetUrl && (
          <a
            href={targetUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex h-12 flex-1 items-center justify-center gap-1.5 rounded-xl bg-brand-800 text-sm font-semibold text-white"
          >
            공식 신청 페이지로 이동
            <ExternalLink className="h-4 w-4" />
          </a>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: 테스트 성공**

Run: `cd frontend && npx vitest run src/components/policy/navigation/__tests__/PolicyMobileBottomBar.test.tsx`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/policy/navigation/PolicyMobileBottomBar.tsx frontend/src/components/policy/navigation/__tests__/PolicyMobileBottomBar.test.tsx
git commit -m "feat(policy): PolicyMobileBottomBar 모바일 fixed bottom bar 컴포넌트 추가"
```

---

## Task 14: Q&A 진입 시 자동 숨김 + PolicyDetailPage 통합

**Files:**
- Modify: `frontend/src/pages/PolicyDetailPage.tsx`

- [ ] **Step 1: Q&A 가시성 추적 ref + 본문 중간 인라인 신청 CTA 제거**

`PolicyDetailPage.tsx` 에 추가:

```tsx
import { PolicyMobileBottomBar } from '@/components/policy/navigation/PolicyMobileBottomBar';

// 컴포넌트 본문:
const qnaRef = useRef<HTMLDivElement | null>(null);
const [bottomBarVisible, setBottomBarVisible] = useState(true);

useEffect(() => {
  const el = qnaRef.current;
  if (!el || typeof IntersectionObserver === 'undefined') return;
  const observer = new IntersectionObserver(
    ([entry]) => setBottomBarVisible(!entry.isIntersecting),
    { threshold: 0.15 },
  );
  observer.observe(el);
  return () => observer.disconnect();
}, []);

// QnaChatSection 위에 sentinel div:
<div ref={qnaRef}>
  <QnaChatSection … />
</div>

// 페이지 최하단 (return의 마지막 요소 직전):
<PolicyMobileBottomBar
  applyUrl={policy.applyUrl}
  sourceUrl={policy.sourceUrl}
  isSubscribed={isSubscribed}
  visible={bottomBarVisible}
  onNotificationClick={isSubscribed ? handleUnsubscribeClick : handleSubscribeClick}
/>
```

본문 중간의 인라인 신청 CTA 블록(`policy.applyUrl && <div className="mb-6"><a href={policy.applyUrl} … >공식 신청 페이지로 이동</a></div>`)은 삭제 — bottom bar 가 동일 역할.

- [ ] **Step 2: 모바일에서 적합도/알림 카드의 인라인 표시 제거**

기존 `PolicyDetailPage.tsx:854-870` 의 모바일 인라인 EligibilityCard + NotificationCtaCard 블록은 유지 (적합도는 결정 영역에서 봐야 함). bottom bar 는 알림과 신청만 담당하므로 충돌 없음.

- [ ] **Step 3: 빌드 + 시각 확인**

```bash
cd frontend && npx tsc --noEmit && npm run build && npm run dev
```

모바일 viewport(375px) 체크리스트:
- [ ] 페이지 진입 시 bottom bar 가 보인다
- [ ] 알림 토글 버튼이 동작한다 (구독 상태에 따라 색 변경)
- [ ] "공식 신청 페이지로 이동" 버튼이 새 탭에서 열린다
- [ ] Q&A 섹션이 화면 15% 이상 보이면 bottom bar 가 자연스럽게 아래로 사라진다
- [ ] Q&A 에서 나오면 다시 나타난다

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/pages/PolicyDetailPage.tsx
git commit -m "feat(policy): 모바일 fixed bottom bar 통합 + Q&A 진입 시 자동 숨김"
```

---

# 마무리

## Task 15: 통합 빌드 + 수동 회귀 테스트

**Files:** (검증만)

- [ ] **Step 1: 전체 테스트 통과 확인**

```bash
cd frontend && npm run test 2>&1 | tail -30
```
Expected: All tests pass

- [ ] **Step 2: 타입체크 + 프로덕션 빌드**

```bash
cd frontend && npx tsc --noEmit && npm run build
```
Expected: 에러 없이 빌드 성공

- [ ] **Step 3: 데스크톱 회귀 체크리스트**

dev 서버에서 정책 상세 페이지 (`/policies/<id>`) 진입.

- [ ] 결정 영역(헤더+요약+메타)만 보고 정책 적합 여부를 5초 이내 판단 가능
- [ ] 우측 sticky 영역: 목차 → 적합도 → 알림 → 공식 신청 순서로 표시
- [ ] 스크롤 시 우측 TOC active 가 viewport 와 일치
- [ ] TOC 클릭 시 해당 그룹으로 부드럽게 스크롤 (96px offset 보정)
- [ ] URL 해시(`#eligibility|benefits|apply|more`)로 직접 진입 시 해당 그룹으로 자동 스크롤
- [ ] 각 그룹 헤더가 시각적으로 구분되어 보임 (52px 컬러 아이콘 + 디바이더)
- [ ] PitfallsCard 가 그룹 4 안에 있음
- [ ] 페이지 하단 "공식 신청 채널" 박스가 없음 (sticky CTA 가 대체)

- [ ] **Step 4: 모바일 회귀 체크리스트**

DevTools 모바일 viewport (375 × 812) 로 진입.

- [ ] 결정 영역이 보일 때는 상단 칩 네비 숨김
- [ ] 그룹 1 진입 후 상단 sticky 칩 네비 등장
- [ ] 칩 탭 시 해당 그룹으로 스크롤 + active 칩 자동 가시화
- [ ] Bottom bar 가 페이지 진입 시 보임 (알림 + 신청)
- [ ] Q&A 섹션 진입 시 bottom bar 가 사라짐
- [ ] 결정 영역 메타 라인에 세부 지역이 5개 이하면 인라인, 6개 이상이면 "외 N개"
- [ ] 그룹 1 안에서 세부 지역 전체 카드는 6개 이상일 때만 표시

- [ ] **Step 5: 마이그레이션 종료 커밋 (변경 없음 — 신뢰 확인용)**

```bash
git status
```

추가 변경 없음을 확인. 만약 사소한 lint 누락이 있다면 한 번에 정리 후 커밋.

---

## 데이터 케이스별 시나리오 (수동 확인)

| 케이스 | 확인 사항 |
|---|---|
| 가이드(AI) 데이터 전체 있음 | 모든 PairedSection 의 쉬운설명이 표시, HighlightsCard·PitfallsCard 표시 |
| 가이드 없음 (레거시 정책) | 그룹 구조는 동일, PairedSection 은 원문 측만 표시, fallback 섹션이 그룹 3 안에서 동작 |
| 세부 지역 0개 | SubRegionInline 미렌더, 그룹 1 안 SubRegionSection 도 미렌더 |
| 세부 지역 1~5개 | 결정 영역 메타에 모두 인라인, 그룹 1 안 SubRegionSection 미렌더 |
| 세부 지역 6개 이상 | 결정 영역 메타에 "X 외 N개", 그룹 1 안에 SubRegionSection 전체 표시 |
| applyUrl 만 있음 | sticky 카드와 bottom bar 의 신청 CTA 가 applyUrl 으로 연결 |
| sourceUrl 만 있음 | sticky 카드와 bottom bar 의 신청 CTA 가 sourceUrl 으로 연결 |
| 둘 다 없음 | sticky 카드의 신청 CTA 카드 자체 숨김, bottom bar 도 알림 버튼만 |
| 마감일 없음 (상시 정책) | DecisionMetaGrid 의 마감일 칸 자체 미렌더 → 3칸 그리드 |

이상 케이스들이 깨지지 않음을 확인하면 본 플랜은 종료.

