import { StackedBarChart } from '@/components/charts/StackedBarChart';
import type { IngestionDailyStatsResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionDailyStatsResponse[];
}

export function IngestionDailyChart({ rows }: Props) {
  const map = new Map<string, { date: string; success: number; failure: number; duplicate: number }>();
  rows.forEach((r) => {
    const cur = map.get(r.date) ?? { date: r.date, success: 0, failure: 0, duplicate: 0 };
    cur.success += r.successCount;
    cur.failure += r.failureCount;
    cur.duplicate += r.duplicateCount;
    map.set(r.date, cur);
  });
  const data = Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));

  return (
    <StackedBarChart
      data={data}
      xKey="date"
      series={[
        { key: 'success', name: '성공', color: '#10b981' },
        { key: 'failure', name: '실패', color: '#ef4444' },
        { key: 'duplicate', name: '중복', color: '#94a3b8' },
      ]}
      emptyMessage="아직 수집 데이터가 없습니다"
    />
  );
}
