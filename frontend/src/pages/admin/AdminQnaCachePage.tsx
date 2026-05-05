import { useState } from 'react';
import { QnaCacheKpiSection } from '@/components/admin/qnaCache/QnaCacheKpiSection';
import { QnaCacheDailyChart } from '@/components/admin/qnaCache/QnaCacheDailyChart';
import { QnaCacheFilterBar } from '@/components/admin/qnaCache/QnaCacheFilterBar';
import { QnaCacheLookupTable } from '@/components/admin/qnaCache/QnaCacheLookupTable';
import {
  useAdminQnaCacheKpi,
  useAdminQnaCacheDailyStats,
  useAdminQnaCacheLookups,
} from '@/hooks/queries/useAdminQnaCache';
import { exportQnaCacheCsv, type LookupResultType } from '@/apis/admin.qnaCache.api';

type FilterState = {
  result: LookupResultType | undefined;
  policyId: number | undefined;
  from: string | undefined;
  to: string | undefined;
  page: number;
  size: number;
};

export default function AdminQnaCachePage() {
  const { data: kpi, isLoading: kpiLoading } = useAdminQnaCacheKpi();
  const { data: stats = [] } = useAdminQnaCacheDailyStats(14);

  const [filter, setFilter] = useState<FilterState>({
    result: undefined,
    policyId: undefined,
    from: undefined,
    to: undefined,
    page: 0,
    size: 20,
  });

  const { data: page, isLoading } = useAdminQnaCacheLookups(filter);

  const onChange = (patch: Partial<Omit<FilterState, 'page' | 'size'>>) =>
    setFilter((f) => ({ ...f, ...patch, page: 0 }));

  const totals = stats.reduce(
    (a, s) => ({
      HIT: a.HIT + s.hitCount,
      BELOW: a.BELOW + s.belowThresholdCount,
      MISS: a.MISS + s.missCount,
    }),
    { HIT: 0, BELOW: 0, MISS: 0 },
  );
  const sum = totals.HIT + totals.BELOW + totals.MISS;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-slate-900">Q&amp;A 캐시 로그</h1>

      <QnaCacheKpiSection kpi={kpi ?? null} loading={kpiLoading} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <QnaCacheDailyChart stats={stats} />
        </div>
        <div className="rounded-lg bg-white p-4 shadow-sm">
          <h2 className="mb-3 text-sm font-medium text-slate-700">결과 비율 (14일)</h2>
          {sum === 0 ? (
            <div className="text-sm text-slate-500">데이터 없음</div>
          ) : (
            <div className="space-y-1 text-sm">
              <div className="flex justify-between">
                <span className="text-emerald-600 font-medium">HIT</span>
                <span>{((totals.HIT / sum) * 100).toFixed(1)}%</span>
              </div>
              <div className="flex justify-between">
                <span className="text-amber-500 font-medium">BELOW</span>
                <span>{((totals.BELOW / sum) * 100).toFixed(1)}%</span>
              </div>
              <div className="flex justify-between">
                <span className="text-red-500 font-medium">MISS</span>
                <span>{((totals.MISS / sum) * 100).toFixed(1)}%</span>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="space-y-3">
        <QnaCacheFilterBar
          result={filter.result}
          policyId={filter.policyId}
          from={filter.from}
          to={filter.to}
          onChange={onChange}
          onExportCsv={() =>
            exportQnaCacheCsv({
              result: filter.result,
              policyId: filter.policyId,
              from: filter.from,
              to: filter.to,
            })
          }
        />
        {isLoading ? (
          <div className="h-32 animate-pulse rounded-lg bg-slate-100" />
        ) : (
          <>
            <QnaCacheLookupTable rows={page?.content ?? []} />
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-500">
                총 {page?.totalElements ?? 0}건 / {page?.totalPages ?? 0}페이지
              </span>
              <div className="space-x-2">
                <button
                  disabled={filter.page === 0}
                  onClick={() => setFilter((f) => ({ ...f, page: f.page - 1 }))}
                  className="rounded border px-3 py-1 disabled:opacity-50"
                >
                  이전
                </button>
                <button
                  disabled={filter.page + 1 >= (page?.totalPages ?? 1)}
                  onClick={() => setFilter((f) => ({ ...f, page: f.page + 1 }))}
                  className="rounded border px-3 py-1 disabled:opacity-50"
                >
                  다음
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
