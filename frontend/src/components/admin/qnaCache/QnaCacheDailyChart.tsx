import { StackedBarChart } from '@/components/charts/StackedBarChart';
import type { QnaCacheDailyStat } from '@/apis/admin.qnaCache.api';

export function QnaCacheDailyChart({ stats }: { stats: QnaCacheDailyStat[] }) {
  const data = stats.map((s) => ({
    date: s.date.slice(5), // MM-DD
    HIT: s.hitCount,
    BELOW: s.belowThresholdCount,
    MISS: s.missCount,
  }));

  return (
    <div className="rounded-lg bg-white p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-slate-700">일자별 캐시 조회 결과</h3>
      <StackedBarChart
        data={data}
        xKey="date"
        series={[
          { key: 'HIT',   name: 'HIT',   color: '#10b981' },
          { key: 'BELOW', name: 'BELOW', color: '#f59e0b' },
          { key: 'MISS',  name: 'MISS',  color: '#ef4444' },
        ]}
      />
    </div>
  );
}
