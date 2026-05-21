import { useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { usePolicyCalendar } from '@/hooks/queries/usePolicyCalendar';
import { useRegions } from '@/hooks/queries/useRegions';
import PolicyFilterBar from '@/components/policy/PolicyFilterBar';
import CalendarHeader from '@/components/policy-calendar/CalendarHeader';
import CalendarMonthGrid from '@/components/policy-calendar/CalendarMonthGrid';
import CalendarAgenda from '@/components/policy-calendar/CalendarAgenda';
import AlwaysOpenSection from '@/components/policy-calendar/AlwaysOpenSection';
import type { PolicyCategory } from '@/types/policy';

function todayKST(): string {
  // toISOString 은 UTC 기준이라 KST 0~9시 사이에 어제 날짜를 돌려준다.
  // 'en-CA' 로케일이 YYYY-MM-DD 포맷, timeZone: 'Asia/Seoul' 로 KST 자정 경계 보장.
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
}

function currentMonth(): string {
  return todayKST().slice(0, 7);
}

function monthBoundaries(month: string): { start: string; end: string } {
  const [y, m] = month.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return {
    start: `${month}-01`,
    end: `${month}-${String(last).padStart(2, '0')}`,
  };
}

function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1 + delta, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

export default function PolicyCalendarPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const month = searchParams.get('month') ?? currentMonth();
  const regions = (searchParams.get('regions') ?? '').split(',').filter(Boolean);
  const category = (searchParams.get('category') ?? '') as PolicyCategory | '';

  const isDesktop = useMediaQuery('(min-width: 768px)');
  const { start: monthStart, end: monthEnd } = useMemo(() => monthBoundaries(month), [month]);
  const today = useMemo(() => todayKST(), []);
  const todayMonth = today.slice(0, 7);

  const { data: regionData } = useRegions();
  const { data, isLoading, error } = usePolicyCalendar({
    from: monthStart,
    to: monthEnd,
    regions: regions.length > 0 ? regions : undefined,
    category: category || undefined,
  });

  const updateParams = (patch: Record<string, string>) => {
    const next = new URLSearchParams(searchParams);
    for (const [k, v] of Object.entries(patch)) {
      if (v === '') next.delete(k);
      else next.set(k, v);
    }
    setSearchParams(next);
  };

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key === 'ArrowLeft') updateParams({ month: shiftMonth(month, -1) });
      else if (e.key === 'ArrowRight') updateParams({ month: shiftMonth(month, +1) });
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [month]);

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 md:px-6 md:py-8">
      <CalendarHeader
        month={month}
        todayMonth={todayMonth}
        onPrev={() => updateParams({ month: shiftMonth(month, -1) })}
        onNext={() => updateParams({ month: shiftMonth(month, +1) })}
        onToday={() => updateParams({ month: todayMonth })}
      />

      {regionData && (
        <PolicyFilterBar
          category={category}
          regions={regions}
          regionData={regionData}
          onCategoryChange={(v) => updateParams({ category: v })}
          onRegionsChange={(codes) =>
            updateParams({ regions: codes.length > 0 ? codes.join(',') : '' })
          }
        />
      )}

      {error && (
        <div className="my-8 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          정책을 불러오지 못했습니다. 다시 시도해 주세요.
        </div>
      )}

      {isLoading ? (
        <div className="h-96 animate-pulse rounded-xl bg-neutral-100" />
      ) : isDesktop ? (
        <CalendarMonthGrid
          items={data?.items ?? []}
          monthStart={monthStart}
          monthEnd={monthEnd}
          today={today}
        />
      ) : (
        <CalendarAgenda
          items={data?.items ?? []}
          monthStart={monthStart}
          monthEnd={monthEnd}
          today={today}
        />
      )}

      <AlwaysOpenSection regions={regions} category={category} />
    </div>
  );
}
