import { StackedBarChart } from '@/components/charts/StackedBarChart';
import type { IngestionDailyStatsResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionDailyStatsResponse[];
}

export function IngestionDailyChart({ rows }: Props) {
  // group by date — aggregate across sources
  const map = new Map<string, { date: string; success: number; failure: number; duplicate: number }>();
  rows.forEach((r) => {
    const cur = map.get(r.date) ?? { date: r.date, success: 0, failure: 0, duplicate: 0 };
    cur.success += r.successCount;
    cur.failure += r.failureCount;
    cur.duplicate += r.duplicateCount;
    map.set(r.date, cur);
  });
  const data = Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));

  if (data.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center rounded border border-slate-200 bg-slate-50 text-sm text-slate-500">
        데이터 없음
      </div>
    );
  }

  return (
    <StackedBarChart
      data={data}
      xKey="date"
      series={[
        { key: 'success', name: '성공', color: '#10b981' },
        { key: 'failure', name: '실패', color: '#ef4444' },
        { key: 'duplicate', name: '중복', color: '#94a3b8' },
      ]}
    />
  );
}
