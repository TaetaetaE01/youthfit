import { useState } from 'react';
import { QnaCacheKpiSection } from '@/components/admin/qnaCache/QnaCacheKpiSection';
import { QnaCacheDailyChart } from '@/components/admin/qnaCache/QnaCacheDailyChart';
import { QnaCacheFilterBar } from '@/components/admin/qnaCache/QnaCacheFilterBar';
import { QnaCacheLookupTable } from '@/components/admin/qnaCache/QnaCacheLookupTable';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminPager from '@/components/admin/AdminPager';
import { AdminCardShell } from '@/components/admin/AdminPlaceholders';
import { Skeleton } from '@/components/admin/AdminSkeleton';
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
      <AdminPageHeader
        title="Q&A 캐시 로그"
        description="semantic-cache 매칭 결과(HIT / BELOW / MISS)를 추적합니다."
      />

      <QnaCacheKpiSection kpi={kpi ?? null} loading={kpiLoading} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <QnaCacheDailyChart stats={stats} />
        </div>
        <AdminCardShell title="결과 비율" subtitle="최근 14일">
          {sum === 0 ? (
            <div className="grid h-32 place-items-center text-sm text-slate-400">
              데이터 없음
            </div>
          ) : (
            <ul className="space-y-2.5 text-sm">
              {[
                { key: 'HIT', label: 'HIT', color: 'bg-emerald-500', text: 'text-emerald-700' },
                { key: 'BELOW', label: 'BELOW', color: 'bg-amber-500', text: 'text-amber-700' },
                { key: 'MISS', label: 'MISS', color: 'bg-rose-500', text: 'text-rose-600' },
              ].map((row) => {
                const pct = (totals[row.key as keyof typeof totals] / sum) * 100;
                return (
                  <li key={row.key} className="space-y-1">
                    <div className="flex items-center justify-between">
                      <span className={`inline-flex items-center gap-1.5 font-semibold ${row.text}`}>
                        <span className={`h-2 w-2 rounded-full ${row.color}`} aria-hidden />
                        {row.label}
                      </span>
                      <span className="font-semibold tabular-nums text-slate-700">
                        {pct.toFixed(1)}%
                      </span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className={`h-full rounded-full ${row.color} transition-all`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </AdminCardShell>
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
          <Skeleton className="h-32 w-full" rounded="lg" />
        ) : (
          <>
            <QnaCacheLookupTable rows={page?.content ?? []} />
            <AdminPager
              page={filter.page}
              totalPages={page?.totalPages ?? 0}
              totalElements={page?.totalElements}
              onChange={(p) => setFilter((f) => ({ ...f, page: p }))}
            />
          </>
        )}
      </div>
    </div>
  );
}
