import { useState } from 'react';
import { AlertCircle } from 'lucide-react';
import { LlmCostKpiSection } from '@/components/admin/llmCost/LlmCostKpiSection';
import { LlmCostLineChart } from '@/components/admin/llmCost/LlmCostLineChart';
import { LlmCostStackedBar } from '@/components/admin/llmCost/LlmCostStackedBar';
import { LlmCostModelTable } from '@/components/admin/llmCost/LlmCostModelTable';
import { LlmCostRangeToggle } from '@/components/admin/llmCost/LlmCostRangeToggle';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import { AdminCardShell } from '@/components/admin/AdminPlaceholders';
import { Skeleton } from '@/components/admin/AdminSkeleton';
import { useAdminLlmCost, type Range } from '@/hooks/useAdminLlmCost';

export default function AdminLlmCostPage() {
  const [range, setRange] = useState<Range>('7d');
  const { kpi, series, byModule, byModel, loading, error } = useAdminLlmCost(range);

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="LLM 비용 대시보드"
        description="모듈·모델별 호출 수와 비용을 일자 기준으로 집계합니다."
        actions={<LlmCostRangeToggle value={range} onChange={setRange} />}
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
          <LlmCostKpiSection kpi={kpi} />

          <AdminCardShell title="시간별 비용 추이" subtitle="기간 내 합계">
            <LlmCostLineChart points={series?.points ?? []} />
          </AdminCardShell>

          <AdminCardShell title="일자별 모듈 분포" subtitle="모듈별 누적 비용">
            <LlmCostStackedBar rows={byModule} />
          </AdminCardShell>

          <AdminCardShell title="모델별 합계" subtitle="호출 수 / 토큰 / 비용">
            <LlmCostModelTable rows={byModel} />
          </AdminCardShell>
        </>
      )}
    </div>
  );
}
