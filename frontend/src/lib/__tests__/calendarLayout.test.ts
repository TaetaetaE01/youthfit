import { describe, it, expect } from 'vitest';
import { layoutBars } from '../calendarLayout';
import type { PolicyCalendarItem } from '@/types/policy';

const mk = (
  id: number,
  applyStart: string | null,
  applyEnd: string | null,
  title = `policy-${id}`,
): PolicyCalendarItem => ({
  id,
  title,
  category: 'HOUSING' as any,
  applyStart,
  applyEnd,
  regionLabel: '전국',
});

describe('layoutBars', () => {
  // 2026-03: 일=3/1, 첫 주 = 3/1(일)~3/7(토), 둘째 = 3/8~3/14, ...
  const monthStart = '2026-03-01';
  const monthEnd = '2026-03-31';

  it('한 주 안에서 완결되는 막대 1개 — 정확히 1 세그먼트', () => {
    const items = [mk(1, '2026-03-10', '2026-03-12')];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments).toHaveLength(1);
    expect(segments[0]).toMatchObject({
      itemId: 1,
      weekIndex: 1,
      startCol: 2,  // 화요일 (일=0)
      endCol: 4,    // 목요일
      hasStartCap: true,
      hasEndCap: true,
      row: 0,
    });
  });

  it('시간상 겹치는 두 막대 — 다른 행에 배치', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-14'),
      mk(2, '2026-03-12', '2026-03-16'),
    ];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    const rows = segments.map((s) => s.row);
    expect(new Set(rows).size).toBeGreaterThan(1);
  });

  it('안 겹치는 두 막대 — 같은 행 재사용', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-11'),
      mk(2, '2026-03-13', '2026-03-14'),
    ];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments[0].row).toBe(0);
    expect(segments[1].row).toBe(0);
  });

  it('주 경계 분할 — 좌측 끝주 우측 캡 없음, 우측 끝주 좌측 캡 없음', () => {
    const items = [mk(1, '2026-03-05', '2026-03-12')];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments).toHaveLength(2);

    const first = segments.find((s) => s.weekIndex === 0)!;
    const second = segments.find((s) => s.weekIndex === 1)!;

    expect(first.hasStartCap).toBe(true);
    expect(first.hasEndCap).toBe(false);
    expect(second.hasStartCap).toBe(false);
    expect(second.hasEndCap).toBe(true);
  });

  it('셀당 3행 초과 시 overflowByDay 카운트 반환', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-12'),
      mk(2, '2026-03-10', '2026-03-12'),
      mk(3, '2026-03-10', '2026-03-12'),
      mk(4, '2026-03-10', '2026-03-12'),
      mk(5, '2026-03-10', '2026-03-12'),
    ];
    const { segments, overflowByDay } = layoutBars(items, monthStart, monthEnd);

    expect(segments.filter((s) => !s.isOverflow)).toHaveLength(3);
    expect(overflowByDay['2026-03-10']).toBe(2);
    expect(overflowByDay['2026-03-11']).toBe(2);
    expect(overflowByDay['2026-03-12']).toBe(2);
  });

  it('applyStart=null 케이스 — 좌측 캡 없는 막대로 처리, 첫 주부터 시작', () => {
    const items = [mk(1, null, '2026-03-10')];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments.length).toBeGreaterThan(0);
    const first = segments[0];
    expect(first.hasStartCap).toBe(false);
    expect(first.weekIndex).toBe(0);
  });

  it('applyEnd=null 케이스 — 우측 캡 없는 막대로 처리, 마지막 주까지', () => {
    const items = [mk(1, '2026-03-25', null)];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    expect(segments.length).toBeGreaterThan(0);
    const last = segments[segments.length - 1];
    expect(last.hasEndCap).toBe(false);
  });

  it('정렬 — applyStart 오름차순, tie 면 더 긴 정책 먼저', () => {
    const items = [
      mk(1, '2026-03-10', '2026-03-12', 'short'),
      mk(2, '2026-03-10', '2026-03-20', 'long'),
    ];
    const { segments } = layoutBars(items, monthStart, monthEnd);
    const s1 = segments.find((s) => s.itemId === 1)!;
    const s2 = segments.find((s) => s.itemId === 2)!;
    expect(s2.row).toBeLessThanOrEqual(s1.row);
  });
});
