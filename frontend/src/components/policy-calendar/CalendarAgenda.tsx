import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';
import { isKoreanHoliday } from '@/lib/holidays';
import type { PolicyCalendarItem } from '@/types/policy';

type Event = {
  day: string;
  weekday: string;
  kind: 'start' | 'end';
  item: PolicyCalendarItem;
  daysUntil: number;
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function parseDate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

export default function CalendarAgenda({
  items,
  monthStart,
  monthEnd,
  today,
}: {
  items: PolicyCalendarItem[];
  monthStart: string;
  monthEnd: string;
  today: string;
}) {
  const events = useMemo<Event[]>(() => {
    const start = parseDate(monthStart);
    const end = parseDate(monthEnd);
    const todayD = parseDate(today);
    const result: Event[] = [];

    for (const item of items) {
      if (item.applyStart) {
        const d = parseDate(item.applyStart);
        if (d >= start && d <= end) {
          result.push({
            day: item.applyStart,
            weekday: WEEKDAYS[d.getUTCDay()],
            kind: 'start',
            item,
            daysUntil: Math.round((d.getTime() - todayD.getTime()) / 86400000),
          });
        }
      }
      if (item.applyEnd) {
        const d = parseDate(item.applyEnd);
        if (d >= start && d <= end) {
          result.push({
            day: item.applyEnd,
            weekday: WEEKDAYS[d.getUTCDay()],
            kind: 'end',
            item,
            daysUntil: Math.round((d.getTime() - todayD.getTime()) / 86400000),
          });
        }
      }
    }

    return result.sort((a, b) => (a.day < b.day ? -1 : a.day > b.day ? 1 : 0));
  }, [items, monthStart, monthEnd, today]);

  const groups = useMemo(() => {
    const map = new Map<string, Event[]>();
    for (const e of events) {
      const arr = map.get(e.day) ?? [];
      arr.push(e);
      map.set(e.day, arr);
    }
    return Array.from(map.entries());
  }, [events]);

  if (groups.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-neutral-500">
        이 달에는 신청 기간이 걸친 정책이 없어요.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-4">
      {groups.map(([day, evs]) => {
        const isRed = evs[0].weekday === '일' || isKoreanHoliday(day);
        return (
        <li key={day}>
          <h3 className={cn('mb-1 text-sm font-semibold', isRed ? 'text-red-500' : 'text-neutral-700')}>
            {Number(day.slice(5, 7))}/{Number(day.slice(8, 10))} ({evs[0].weekday})
          </h3>
          <ul className="flex flex-col gap-1.5">
            {evs.map((e) => {
              const urgent = e.kind === 'end' && e.daysUntil >= 0 && e.daysUntil <= 3;
              return (
                <li key={`${e.item.id}-${e.kind}`}>
                  <Link
                    to={`/policies/${e.item.id}`}
                    className="flex items-center gap-2 rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm hover:bg-neutral-50"
                  >
                    <span
                      className={
                        e.kind === 'end' ? 'text-red-500' : 'text-brand-800'
                      }
                    >
                      ┃
                    </span>
                    <span className="flex-1 font-medium text-neutral-900 truncate">
                      {e.item.title}
                    </span>
                    <span className="text-xs text-neutral-500">
                      {e.kind === 'start' ? '시작' : '마감'}
                      {urgent && ` · D-${e.daysUntil}❗`}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </li>
        );
      })}
    </ul>
  );
}
