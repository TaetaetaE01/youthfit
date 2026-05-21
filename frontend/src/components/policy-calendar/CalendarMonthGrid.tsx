import { useMemo, useState } from 'react';
import { cn } from '@/lib/cn';
import { layoutBars, type BarSegment } from '@/lib/calendarLayout';
import CalendarBar from './CalendarBar';
import CalendarDayPopover from './CalendarDayPopover';
import type { PolicyCalendarItem } from '@/types/policy';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

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

  return (
    <div className="w-full">
      <div className="grid grid-cols-7 border-b border-neutral-200 text-xs font-semibold text-neutral-600">
        {WEEKDAYS.map((d, i) => (
          <div
            key={d}
            className={cn(
              'py-2 text-center',
              i === 0 && 'text-red-500',
              i === 6 && 'text-blue-500',
            )}
          >
            {d}
          </div>
        ))}
      </div>

      <div className="flex flex-col">
        {weeks.map((week, wi) => {
          const segs = segmentsByWeek.get(wi) ?? [];

          return (
            <div key={wi} className="border-b border-neutral-100">
              <div className="grid grid-cols-7">
                {week.map((day) => {
                  const inMonth = Number(day.slice(5, 7)) === monthNum;
                  const isToday = day === today;
                  return (
                    <div
                      key={day}
                      className={cn(
                        'px-2 pt-1 text-xs',
                        inMonth ? 'text-neutral-800' : 'text-neutral-400',
                        isToday && 'font-bold text-brand-800',
                      )}
                    >
                      {Number(day.slice(8, 10))}
                    </div>
                  );
                })}
              </div>

              <div
                className="grid grid-cols-7 gap-y-0.5 px-1 pb-1"
                style={{ gridAutoRows: '20px' }}
              >
                {segs
                  .filter((s) => s.row < 3)
                  .map((seg) => {
                    const show = !labelShown.has(seg.itemId);
                    if (show) labelShown.add(seg.itemId);
                    return (
                      <div
                        key={`${seg.itemId}-${seg.weekIndex}`}
                        style={{ gridRow: seg.row + 1 }}
                      >
                        <CalendarBar
                          segment={seg}
                          showLabel={show && seg.hasStartCap}
                          daysUntilEnd={daysUntil(seg.applyEnd)}
                        />
                      </div>
                    );
                  })}
              </div>

              <div className="grid grid-cols-7 px-1 pb-1">
                {week.map((day) => {
                  const overflow = layout.overflowByDay[day] ?? 0;
                  if (overflow <= 0) return <div key={day} />;
                  return (
                    <button
                      key={day}
                      onClick={() => setPopoverDay(day)}
                      className="text-[10px] font-semibold text-neutral-500 hover:text-brand-800"
                    >
                      +{overflow}개 더보기
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

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
