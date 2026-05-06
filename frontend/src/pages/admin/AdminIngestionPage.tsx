import { useState } from 'react';
import { StaleSourceBanner } from '@/components/admin/ingestion/StaleSourceBanner';
import { IngestionKpiSection } from '@/components/admin/ingestion/IngestionKpiSection';
import { IngestionDailyChart } from '@/components/admin/ingestion/IngestionDailyChart';
import { SourceTable } from '@/components/admin/ingestion/SourceTable';
import { FailureTable } from '@/components/admin/ingestion/FailureTable';
import { RetryConfirmModal } from '@/components/admin/ingestion/RetryConfirmModal';
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

  return (
    <div className="space-y-6 p-6">
      <header>
        <h1 className="text-xl font-semibold">Ingestion 헬스</h1>
      </header>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          데이터를 불러오지 못했습니다: {error.message}
        </div>
      )}

      {loading && (
        <div className="space-y-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-24 animate-pulse rounded-lg bg-slate-100" />
          ))}
        </div>
      )}

      {!loading && (
        <>
          <StaleSourceBanner stale={stale} />
          <IngestionKpiSection kpi={kpi} />

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">일자별 신규/실패/중복 (14일)</h2>
            <IngestionDailyChart rows={daily} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">원천별 요약</h2>
            <SourceTable rows={sources} />
          </section>

          <section>
            <div className="mb-2 flex items-center gap-2">
              <h2 className="text-sm font-medium text-slate-700">실패 항목</h2>
              <input
                placeholder="source 필터"
                value={source}
                onChange={(e) => {
                  setSource(e.target.value);
                  setPage(0);
                }}
                className="rounded border border-slate-200 px-2 py-1 text-xs"
              />
              <select
                value={reason ?? ''}
                onChange={(e) => {
                  setReason(
                    e.target.value ? (e.target.value as FailureReason) : undefined,
                  );
                  setPage(0);
                }}
                className="rounded border border-slate-200 px-2 py-1 text-xs"
              >
                <option value="">사유 전체</option>
                <option value="VALIDATION">VALIDATION</option>
                <option value="PARSING">PARSING</option>
                <option value="MAPPING">MAPPING</option>
                <option value="DEDUPLICATION_CONFLICT">DEDUPLICATION_CONFLICT</option>
                <option value="OTHER">OTHER</option>
              </select>
              <span className="ml-auto text-xs text-slate-500">총 {totalFailures}건</span>
            </div>
            <FailureTable rows={failures} onRetry={(id) => setRetryTarget(id)} />
            <div className="mt-2 flex justify-end gap-2 text-xs">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                className="rounded border px-2 py-1"
              >
                ◀ 이전
              </button>
              <span>
                {page + 1} / {Math.max(1, Math.ceil(totalFailures / 20))}
              </span>
              <button
                onClick={() => setPage(page + 1)}
                className="rounded border px-2 py-1"
              >
                다음 ▶
              </button>
            </div>
          </section>
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
