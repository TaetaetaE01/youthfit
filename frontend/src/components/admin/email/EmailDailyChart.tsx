import { StackedBarChart } from '@/components/charts/StackedBarChart';
import { AdminCardShell } from '@/components/admin/AdminPlaceholders';
import { Skeleton } from '@/components/admin/AdminSkeleton';
import { useAdminEmailDailyStats } from '@/hooks/queries/useAdminEmailDailyStats';

export function EmailDailyChart({ from, to }: { from: string; to: string }) {
  const { data = [], isLoading } = useAdminEmailDailyStats(from, to);

  return (
    <AdminCardShell title="일자별 발송 결과" subtitle="상태별 누적">
      {isLoading ? (
        <Skeleton className="h-72 w-full" rounded="lg" />
      ) : (
        <StackedBarChart
          data={data}
          xKey="date"
          series={[
            { key: 'delivered', name: 'Delivered', color: '#10b981' },
            { key: 'sent', name: 'Sent', color: '#6366f1' },
            { key: 'bounced', name: 'Bounced', color: '#f59e0b' },
            { key: 'complained', name: 'Complained', color: '#ef4444' },
            { key: 'failed', name: 'Failed', color: '#b91c1c' },
          ]}
          emptyMessage="해당 기간의 발송 기록이 없습니다"
        />
      )}
    </AdminCardShell>
  );
}
