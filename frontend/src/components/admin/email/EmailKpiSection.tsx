import { KpiCard } from '@/components/charts/KpiCard';
import { useAdminEmailKpi } from '@/hooks/queries/useAdminEmailKpi';

export function EmailKpiSection() {
  const { data, isLoading } = useAdminEmailKpi();
  if (isLoading || !data) {
    return <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
      {[0,1,2,3].map(i => <div key={i} className="h-24 animate-pulse rounded-lg bg-slate-100" />)}
    </div>;
  }
  const todaySum = data.today.sent + data.today.delivered + data.today.bounced + data.today.failed;
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
      <KpiCard label="오늘 발송" value={todaySum} />
      <KpiCard label="이번주 성공률" value={`${(data.successRate * 100).toFixed(1)}%`}
               tone={data.successRate > 0.95 ? 'success' : 'warning'} />
      <KpiCard label="오늘 바운스" value={data.today.bounced}
               tone={data.today.bounced > 0 ? 'warning' : 'default'} />
      <KpiCard label="오늘 실패" value={data.today.failed}
               tone={data.today.failed > 0 ? 'danger' : 'default'} />
    </div>
  );
}
