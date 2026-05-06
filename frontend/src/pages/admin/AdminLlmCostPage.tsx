import { useState } from 'react';
import { LlmCostKpiSection } from '@/components/admin/llmCost/LlmCostKpiSection';
import { LlmCostLineChart } from '@/components/admin/llmCost/LlmCostLineChart';
import { LlmCostStackedBar } from '@/components/admin/llmCost/LlmCostStackedBar';
import { LlmCostModelTable } from '@/components/admin/llmCost/LlmCostModelTable';
import { LlmCostRangeToggle } from '@/components/admin/llmCost/LlmCostRangeToggle';
import { useAdminLlmCost, type Range } from '@/hooks/useAdminLlmCost';

export default function AdminLlmCostPage() {
  const [range, setRange] = useState<Range>('7d');
  const { kpi, series, byModule, byModel, loading, error } = useAdminLlmCost(range);

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">LLM 비용 대시보드</h1>
        <LlmCostRangeToggle value={range} onChange={setRange} />
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
          <LlmCostKpiSection kpi={kpi} />

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">시간별 비용 추이</h2>
            <LlmCostLineChart points={series?.points ?? []} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">일자별 모듈 분포</h2>
            <LlmCostStackedBar rows={byModule} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium text-slate-700">모델별 합계</h2>
            <LlmCostModelTable rows={byModel} />
          </section>
        </>
      )}
    </div>
  );
}
