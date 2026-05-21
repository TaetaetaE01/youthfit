import { useMemo, useState } from 'react';
import { cn } from '@/lib/cn';
import { layoutBars, type BarSegment } from '@/lib/calendarLayout';
import { isKoreanHoliday } from '@/lib/holidays';
import CalendarBar from './CalendarBar';
import CalendarDayPopover from './CalendarDayPopover';
import type { PolicyCalendarItem } from '@/types/policy';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const ROW_DATE_HEIGHT = 28;
const ROW_BAR_HEIGHT = 22;
const ROW_BAR_COUNT = 3;
const ROW_OVERFLOW_HEIGHT = 20;

type Props = {
  items: PolicyCalendarItem[];
  monthStart: string;
  monthEnd: string;
  today: string;
};

function parseDate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}
function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() + n);
  return r;
}
function weekStart(d: Date): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() - r.getUTCDay());
  return r;
}

export default function CalendarMonthGrid({ items, monthStart, monthEnd, today }: Props) {
  const [popoverDay, setPopoverDay] = useState<string | null>(null);

  const { layout, weeks } = useMemo(() => {
    const layout = layoutBars(items, monthStart, monthEnd);

    const start = weekStart(parseDate(monthStart));
    const end = parseDate(monthEnd);
    const lastWeek = weekStart(end);
    const lastDay = addDays(lastWeek, 6);
    const totalDays = Math.round((lastDay.getTime() - start.getTime()) / 86400000) + 1;
    const totalWeeks = totalDays / 7;

    const weeks: Array<Array<string>> = [];
    for (let w = 0; w < totalWeeks; w++) {
      const week: string[] = [];
      for (let d = 0; d < 7; d++) {
        week.push(formatDate(addDays(start, w * 7 + d)));
      }
      weeks.push(week);
    }
    return { layout, weeks };
  }, [items, monthStart, monthEnd]);

  const segmentsByWeek = useMemo(() => {
    const m = new Map<number, BarSegment[]>();
    for (const seg of layout.segments) {
      const arr = m.get(seg.weekIndex) ?? [];
      arr.push(seg);
      m.set(seg.weekIndex, arr);
    }
    return m;
  }, [layout.segments]);

  const monthNum = Number(monthStart.slice(5, 7));

  const todayDate = parseDate(today);
  const daysUntil = (end: string | null) =>
    end ? Math.round((parseDate(end).getTime() - todayDate.getTime()) / 86400000) : null;

  const labelShown = new Set<number>();

  const gridTemplateColumns = 'repeat(7, minmax(0, 1fr))';
  const gridTemplateRows = `${ROW_DATE_HEIGHT}px repeat(${ROW_BAR_COUNT}, ${ROW_BAR_HEIGHT}px) ${ROW_OVERFLOW_HEIGHT}px`;

  return (
    <div className="w-full overflow-hidden rounded-xl border border-neutral-200 bg-white">
      {/* 요일 헤더 */}
      <div
        className="grid border-b border-neutral-200 bg-neutral-50 text-xs font-semibold text-neutral-600"
        style={{ gridTemplateColumns }}
      >
        {WEEKDAYS.map((d, i) => (
          <div
            key={d}
            className={cn(
              'py-2 text-center',
              i !== 6 && 'border-r border-neutral-200',
              i === 0 && 'text-red-500',
              i === 6 && 'text-blue-500',
            )}
          >
            {d}
          </div>
        ))}
      </div>

      {/* 주별 행 */}
      {weeks.map((week, wi) => {
        const segs = segmentsByWeek.get(wi) ?? [];
        return (
          <div
            key={wi}
            className={cn(
              'grid',
              wi !== weeks.length - 1 && 'border-b border-neutral-100',
            )}
            style={{ gridTemplateColumns, gridTemplateRows }}
          >
            {/* 셀 배경 — 컬럼 구분선 + 오늘 강조용. 각 셀이 자기 컬럼·전체 행을 차지. */}
            {week.map((day, di) => {
              const inMonth = Number(day.slice(5, 7)) === monthNum;
              const isToday = day === today;
              return (
                <div
                  key={`bg-${day}`}
                  className={cn(
                    'min-w-0',
                    di !== 6 && 'border-r border-neutral-100',
                    !inMonth && 'bg-neutral-50/50',
                    isToday && 'bg-brand-100/30',
                  )}
                  style={{
                    gridColumn: di + 1,
                    gridRow: `1 / span ${ROW_BAR_COUNT + 2}`,
                  }}
                  aria-hidden="true"
                />
              );
            })}

            {/* 날짜 번호 */}
            {week.map((day, di) => {
              const inMonth = Number(day.slice(5, 7)) === monthNum;
              const isToday = day === today;
              const isRed = di === 0 || isKoreanHoliday(day);
              return (
                <div
                  key={`d-${day}`}
                  className={cn(
                    'pointer-events-none px-2 pt-1 text-xs',
                    inMonth
                      ? isRed
                        ? 'text-red-500'
                        : 'text-neutral-700'
                      : isRed
                        ? 'text-red-300'
                        : 'text-neutral-400',
                    isToday && 'font-bold text-brand-800',
                  )}
                  style={{ gridColumn: di + 1, gridRow: 1 }}
                >
                  {Number(day.slice(8, 10))}
                </div>
              );
            })}

            {/* 막대 */}
            {segs
              .filter((s) => s.row < ROW_BAR_COUNT)
              .map((seg) => {
                // 첫 등장 세그먼트에만 라벨을 노출. hasStartCap=false 인 세그먼트(이전 달에서
                // 이어온 막대)는 라벨을 그리지 않는 동시에 labelShown 갱신도 보류 —
                // 그래야 같은 itemId 의 다음 캡 있는 세그먼트가 라벨을 받을 수 있다.
                const showLabel = !labelShown.has(seg.itemId) && seg.hasStartCap;
                if (showLabel) labelShown.add(seg.itemId);
                return (
                  <CalendarBar
                    key={`${seg.itemId}-${seg.weekIndex}`}
                    segment={seg}
                    showLabel={showLabel}
                    daysUntilEnd={daysUntil(seg.applyEnd)}
                    gridRow={seg.row + 2}
                  />
                );
              })}

            {/* +N 더보기 */}
            {week.map((day, di) => {
              const overflow = layout.overflowByDay[day] ?? 0;
              if (overflow <= 0) return null;
              return (
                <button
                  key={`o-${day}`}
                  onClick={() => setPopoverDay(day)}
                  className="self-center px-2 text-left text-[11px] font-semibold text-neutral-500 hover:text-brand-800"
                  style={{ gridColumn: di + 1, gridRow: ROW_BAR_COUNT + 2 }}
                >
                  +{overflow}개 더보기
                </button>
              );
            })}
          </div>
        );
      })}

      {popoverDay && (
        <CalendarDayPopover
          day={popoverDay}
          items={items.filter((it) => {
            const start = it.applyStart ? parseDate(it.applyStart) : null;
            const end = it.applyEnd ? parseDate(it.applyEnd) : null;
            const target = parseDate(popoverDay);
            const afterStart = !start || target >= start;
            const beforeEnd = !end || target <= end;
            return afterStart && beforeEnd;
          })}
          onClose={() => setPopoverDay(null)}
        />
      )}
    </div>
  );
}
