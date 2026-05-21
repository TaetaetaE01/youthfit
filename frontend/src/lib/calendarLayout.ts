import type { PolicyCalendarItem } from '@/types/policy';

export type BarSegment = {
  itemId: number;
  title: string;
  category: string;
  weekIndex: number;
  startCol: number;     // 0=Sun ~ 6=Sat (inclusive)
  endCol: number;
  hasStartCap: boolean;
  hasEndCap: boolean;
  row: number;
  isOverflow: boolean;
  applyStart: string | null;
  applyEnd: string | null;
};

export type LayoutResult = {
  segments: BarSegment[];
  overflowByDay: Record<string, number>;
};

const MAX_ROWS_PER_CELL = 3;

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

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / 86400000);
}

function weekStart(d: Date): Date {
  const r = new Date(d);
  r.setUTCDate(r.getUTCDate() - r.getUTCDay());
  return r;
}

export function layoutBars(
  items: PolicyCalendarItem[],
  monthStartStr: string,
  monthEndStr: string,
): LayoutResult {
  const monthStart = parseDate(monthStartStr);
  const monthEnd = parseDate(monthEndStr);
  const gridStart = weekStart(monthStart);

  const sorted = [...items].sort((a, b) => {
    const aStart = a.applyStart ? parseDate(a.applyStart).getTime() : gridStart.getTime();
    const bStart = b.applyStart ? parseDate(b.applyStart).getTime() : gridStart.getTime();
    if (aStart !== bStart) return aStart - bStart;
    const aEnd = a.applyEnd ? parseDate(a.applyEnd).getTime() : Number.MAX_SAFE_INTEGER;
    const bEnd = b.applyEnd ? parseDate(b.applyEnd).getTime() : Number.MAX_SAFE_INTEGER;
    return bEnd - aEnd;
  });

  const lastWeekStart = weekStart(monthEnd);
  const gridEnd = addDays(lastWeekStart, 6);
  const totalWeeks = Math.round(daysBetween(gridStart, gridEnd) / 7) + 1;

  // rowOccupancy[weekIndex][row] = last occupied endCol in that row
  const rowOccupancy: number[][] = Array.from({ length: totalWeeks }, () => []);

  const segments: BarSegment[] = [];
  const overflowByDay: Record<string, number> = {};

  for (const item of sorted) {
    const effStart = item.applyStart ? parseDate(item.applyStart) : gridStart;
    const effEnd = item.applyEnd ? parseDate(item.applyEnd) : gridEnd;
    const visibleStart = effStart < gridStart ? gridStart : effStart;
    const visibleEnd = effEnd > gridEnd ? gridEnd : effEnd;
    if (visibleEnd < visibleStart) continue;

    const firstWeek = Math.floor(daysBetween(gridStart, weekStart(visibleStart)) / 7);
    const lastWeek = Math.floor(daysBetween(gridStart, weekStart(visibleEnd)) / 7);

    // Find lowest available row that fits across all weeks of this bar
    let chosenRow = -1;
    for (let row = 0; row < 20; row++) {
      let fits = true;
      for (let w = firstWeek; w <= lastWeek; w++) {
        const segStartCol = w === firstWeek ? visibleStart.getUTCDay() : 0;
        const occupied = rowOccupancy[w][row] ?? -1;
        // occupied holds the endCol of the last bar in this row/week.
        // A new bar can start at segStartCol only if occupied < segStartCol.
        if (occupied >= segStartCol) {
          fits = false;
          break;
        }
      }
      if (fits) {
        chosenRow = row;
        break;
      }
    }

    const isOverflow = chosenRow >= MAX_ROWS_PER_CELL;

    if (isOverflow) {
      let cursor = new Date(visibleStart);
      while (cursor <= visibleEnd) {
        const key = formatDate(cursor);
        overflowByDay[key] = (overflowByDay[key] ?? 0) + 1;
        cursor = addDays(cursor, 1);
      }
      continue;
    }

    // Emit one segment per week and update occupancy
    for (let w = firstWeek; w <= lastWeek; w++) {
      const wStartDay = addDays(gridStart, w * 7);
      const wEndDay = addDays(wStartDay, 6);
      const segStart = w === firstWeek ? visibleStart : wStartDay;
      const segEnd = w === lastWeek ? visibleEnd : wEndDay;

      const startCol = segStart.getUTCDay();
      const endCol = segEnd.getUTCDay();

      const hasStartCap =
        item.applyStart !== null &&
        formatDate(segStart) === item.applyStart;
      const hasEndCap =
        item.applyEnd !== null &&
        formatDate(segEnd) === item.applyEnd;

      segments.push({
        itemId: item.id,
        title: item.title,
        category: String(item.category),
        weekIndex: w,
        startCol,
        endCol,
        hasStartCap,
        hasEndCap,
        row: chosenRow,
        isOverflow: false,
        applyStart: item.applyStart,
        applyEnd: item.applyEnd,
      });

      rowOccupancy[w][chosenRow] = endCol;
    }
  }

  return { segments, overflowByDay };
}
