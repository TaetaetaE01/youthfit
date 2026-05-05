import { StackedBarChart } from '@/components/charts/StackedBarChart';
import { useAdminEmailDailyStats } from '@/hooks/queries/useAdminEmailDailyStats';

export function EmailDailyChart({ from, to }: { from: string; to: string }) {
  const { data = [], isLoading } = useAdminEmailDailyStats(from, to);
  if (isLoading) return <div className="h-72 animate-pulse rounded-lg bg-slate-100" />;
  return (
    <div className="rounded-lg bg-white p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-slate-700">일자별 발송 결과</h3>
      <StackedBarChart
        data={data} xKey="date"
        series={[
          { key: 'delivered', name: 'Delivered', color: '#10b981' },
          { key: 'sent',      name: 'Sent',      color: '#6366f1' },
          { key: 'bounced',   name: 'Bounced',   color: '#f59e0b' },
          { key: 'complained',name: 'Complained',color: '#ef4444' },
          { key: 'failed',    name: 'Failed',    color: '#b91c1c' },
        ]}
      />
    </div>
  );
}
