import AdminPageHeader from '@/components/admin/AdminPageHeader';
import { AdminCardSkeleton, AdminKpiSkeletonGrid } from '@/components/admin/AdminSkeleton';
import ActionQueueSection from '@/components/admin/dashboard/ActionQueueSection';
import AllClearBanner from '@/components/admin/dashboard/AllClearBanner';
import AreaStatusGrid from '@/components/admin/dashboard/AreaStatusGrid';
import { useAdminDashboardOverview } from '@/hooks/queries/useAdminDashboardOverview';

function formatRelative(iso: string): string {
  const diffSec = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (diffSec < 60) return `${diffSec}초 전`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}분 전`;
  return `${Math.floor(diffSec / 3600)}시간 전`;
}

function AdminDashboardSkeleton() {
  return (
    <div className="space-y-6" data-testid="admin-dashboard-skeleton" aria-busy="true">
      <AdminCardSkeleton rows={2} />
      <AdminKpiSkeletonGrid count={6} />
    </div>
  );
}

export default function AdminDashboardPage() {
  const overview = useAdminDashboardOverview();

  if (overview.isLoading) {
    return <AdminDashboardSkeleton />;
  }

  if (overview.isError || !overview.data) {
    return (
      <div className="space-y-6">
        <AdminPageHeader
          title="관리자 대시보드"
          description="운영 신호를 한 화면에서 추적합니다."
        />
        <section
          role="alert"
          className="flex flex-col items-start gap-3 rounded-xl border border-error-100 bg-error-50 px-4 py-4 text-sm text-error-700 sm:flex-row sm:items-center sm:justify-between"
        >
          <div>
            <p className="font-semibold">대시보드 데이터를 불러오지 못했습니다.</p>
            <p className="mt-0.5 text-xs text-error-600">
              네트워크 또는 서버 상태를 확인한 뒤 다시 시도해 주세요.
            </p>
          </div>
          <button
            type="button"
            onClick={() => overview.refetch()}
            className="shrink-0 rounded-md border border-error-200 bg-white px-3 py-1.5 text-xs font-semibold text-error-700 shadow-sm hover:bg-error-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error-500"
          >
            다시 시도
          </button>
        </section>
      </div>
    );
  }

  const { actionItems, areas, generatedAt } = overview.data;
  const isFetching = overview.isFetching;

  const status = (
    <div className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 shadow-sm md:inline-flex">
      <span
        className={[
          'h-1.5 w-1.5 rounded-full',
          isFetching
            ? 'bg-amber-500 animate-pulse'
            : 'bg-success-500 shadow-[0_0_8px_rgba(34,197,94,0.5)]',
        ].join(' ')}
        aria-hidden
      />
      <span>{isFetching ? '갱신 중…' : '실시간'}</span>
    </div>
  );

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="관리자 대시보드"
        description={`마지막 갱신: ${formatRelative(generatedAt)}`}
        status={status}
      />

      {actionItems.length > 0 ? (
        <ActionQueueSection items={actionItems} />
      ) : (
        <AllClearBanner />
      )}

      <AreaStatusGrid areas={areas} />
    </div>
  );
}
