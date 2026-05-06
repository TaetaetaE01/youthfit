import { KpiCard } from '@/components/charts/KpiCard';
import type { LlmCostKpiResponse } from '@/apis/admin.llmCost.api';

interface Props {
  kpi: LlmCostKpiResponse | undefined;
}

export function LlmCostKpiSection({ kpi }: Props) {
  if (!kpi) return null;
  const krw = (usd: number) =>
    `≈ ₩${Math.round(usd * kpi.usdToKrwRate).toLocaleString()}`;
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
      <KpiCard
        label="오늘 비용"
        value={`$${kpi.todayCostUsd.toFixed(4)}`}
        hint={krw(kpi.todayCostUsd)}
      />
      <KpiCard
        label="이번주 비용"
        value={`$${kpi.thisWeekCostUsd.toFixed(4)}`}
        hint={krw(kpi.thisWeekCostUsd)}
      />
      <KpiCard
        label="이번달 비용"
        value={`$${kpi.thisMonthCostUsd.toFixed(4)}`}
        hint={krw(kpi.thisMonthCostUsd)}
      />
      <KpiCard
        label="이번달 호출 수"
        value={kpi.thisMonthCallCount.toLocaleString()}
        hint={kpi.lastMonthCostUsd > 0 ? `전월 $${kpi.lastMonthCostUsd.toFixed(2)}` : undefined}
      />
    </div>
  );
}
