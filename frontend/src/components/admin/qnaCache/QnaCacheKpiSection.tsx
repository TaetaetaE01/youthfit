import { KpiCard } from '@/components/charts/KpiCard';
import type { QnaCacheKpi } from '@/apis/admin.qnaCache.api';

function trendHint(today: number, yesterday: number): string {
  if (yesterday === 0) return '';
  const diff = (today - yesterday) * 100;
  return diff >= 0 ? `↑ ${diff.toFixed(1)}%p` : `↓ ${Math.abs(diff).toFixed(1)}%p`;
}

export function QnaCacheKpiSection({
  kpi,
  loading,
}: {
  kpi: QnaCacheKpi | null;
  loading: boolean;
}) {
  if (loading || !kpi) {
    return (
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-24 animate-pulse rounded-lg bg-slate-100" />
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
      <KpiCard
        label="오늘 hit률"
        value={`${(kpi.todayHitRate * 100).toFixed(1)}%`}
        tone={kpi.todayHitRate >= 0.5 ? 'success' : 'warning'}
      />
      <KpiCard
        label="어제 hit률"
        value={`${(kpi.yesterdayHitRate * 100).toFixed(1)}%`}
        hint={trendHint(kpi.todayHitRate, kpi.yesterdayHitRate)}
      />
      <KpiCard
        label="7일 평균 유사도"
        value={kpi.sevenDaysAvgSimilarity.toFixed(3)}
      />
      <KpiCard
        label="7일 비용 절감"
        value={`$${kpi.sevenDaysEstimatedSavingsUsd.toFixed(4)}`}
        hint={`hit ${kpi.sevenDaysHitCount}건`}
        tone="success"
      />
    </div>
  );
}
