import { useState } from 'react';
import { AlertCircle } from 'lucide-react';
import { StaleSourceBanner } from '@/components/admin/ingestion/StaleSourceBanner';
import { IngestionKpiSection } from '@/components/admin/ingestion/IngestionKpiSection';
import { IngestionDailyChart } from '@/components/admin/ingestion/IngestionDailyChart';
import { SourceTable } from '@/components/admin/ingestion/SourceTable';
import { FailureTable } from '@/components/admin/ingestion/FailureTable';
import { RetryConfirmModal } from '@/components/admin/ingestion/RetryConfirmModal';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminPager from '@/components/admin/AdminPager';
import { AdminCardShell } from '@/components/admin/AdminPlaceholders';
import { Skeleton } from '@/components/admin/AdminSkeleton';
import { useAdminIngestion } from '@/hooks/useAdminIngestion';
import type { FailureReason } from '@/apis/admin.ingestion.api';

export default function AdminIngestionPage() {
  const [page, setPage] = useState(0);
  const [reason, setReason] = useState<FailureReason | undefined>();
  const [source, setSource] = useState<string>('');
  const [retryTarget, setRetryTarget] = useState<number | null>(null);
  const [retryLoading, setRetryLoading] = useState(false);

  const { kpi, daily, sources, stale, failures, totalFailures, loading, error, retry } =
    useAdminIngestion({ page, size: 20, reason, source: source || undefined });

  const handleConfirmRetry = async () => {
    if (retryTarget == null) return;
    setRetryLoading(true);
    try {
      const result = await retry(retryTarget);
      alert(`재처리 결과: ${result.status} — ${result.message}`);
    } finally {
      setRetryLoading(false);
      setRetryTarget(null);
    }
  };

  const filterControls = (
    <div className="flex flex-wrap items-center gap-2">
      <input
        placeholder="source 필터"
        value={source}
        onChange={(e) => {
          setSource(e.target.value);
          setPage(0);
        }}
        className="h-8 rounded-md border border-slate-200 bg-white px-2.5 text-xs text-slate-700 placeholder:text-slate-400 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/10"
      />
      <select
        value={reason ?? ''}
        onChange={(e) => {
          setReason(
            e.target.value ? (e.target.value as FailureReason) : undefined,
          );
          setPage(0);
        }}
        className="h-8 rounded-md border border-slate-200 bg-white px-2 text-xs text-slate-700 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/10"
      >
        <option value="">사유 전체</option>
        <option value="VALIDATION">VALIDATION</option>
        <option value="PARSING">PARSING</option>
        <option value="MAPPING">MAPPING</option>
        <option value="DEDUPLICATION_CONFLICT">DEDUPLICATION_CONFLICT</option>
        <option value="OTHER">OTHER</option>
      </select>
    </div>
  );

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="Ingestion 헬스"
        description="원천별 수집·정규화 결과와 실패 항목을 추적하고 재처리합니다."
      />

      {error && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <div>
            <div className="font-semibold">데이터를 불러오지 못했습니다</div>
            <div className="mt-0.5 text-xs text-rose-600/80">{error.message}</div>
          </div>
        </div>
      )}

      {loading && (
        <div className="space-y-4">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-24 w-full" rounded="lg" />
          ))}
        </div>
      )}

      {!loading && (
        <>
          <StaleSourceBanner stale={stale} />
          <IngestionKpiSection kpi={kpi} />

          <AdminCardShell
            title="일자별 신규/실패/중복"
            subtitle="최근 14일"
          >
            <IngestionDailyChart rows={daily} />
          </AdminCardShell>

          <AdminCardShell title="원천별 요약" subtitle="source 단위 집계">
            <SourceTable rows={sources} />
          </AdminCardShell>

          <AdminCardShell
            title="실패 항목"
            subtitle={`총 ${totalFailures.toLocaleString()}건`}
            action={filterControls}
          >
            <FailureTable rows={failures} onRetry={(id) => setRetryTarget(id)} />
            <div className="mt-3">
              <AdminPager
                page={page}
                totalPages={Math.max(1, Math.ceil(totalFailures / 20))}
                totalElements={totalFailures}
                onChange={(p) => setPage(Math.max(0, p))}
              />
            </div>
          </AdminCardShell>
        </>
      )}

      <RetryConfirmModal
        open={retryTarget != null}
        failureId={retryTarget}
        onClose={() => setRetryTarget(null)}
        onConfirm={handleConfirmRetry}
        loading={retryLoading}
      />
    </div>
  );
}
