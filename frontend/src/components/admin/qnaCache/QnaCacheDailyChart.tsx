import { StackedBarChart } from '@/components/charts/StackedBarChart';
import { AdminCardShell } from '@/components/admin/AdminPlaceholders';
import type { QnaCacheDailyStat } from '@/apis/admin.qnaCache.api';

export function QnaCacheDailyChart({ stats }: { stats: QnaCacheDailyStat[] }) {
  const data = stats.map((s) => ({
    date: s.date.slice(5), // MM-DD
    HIT: s.hitCount,
    BELOW: s.belowThresholdCount,
    MISS: s.missCount,
  }));

  return (
    <AdminCardShell title="일자별 캐시 조회 결과" subtitle="HIT / BELOW / MISS 누적">
      <StackedBarChart
        data={data}
        xKey="date"
        series={[
          { key: 'HIT', name: 'HIT', color: '#10b981' },
          { key: 'BELOW', name: 'BELOW', color: '#f59e0b' },
          { key: 'MISS', name: 'MISS', color: '#ef4444' },
        ]}
        emptyMessage="아직 캐시 조회 데이터가 없습니다"
      />
    </AdminCardShell>
  );
}
