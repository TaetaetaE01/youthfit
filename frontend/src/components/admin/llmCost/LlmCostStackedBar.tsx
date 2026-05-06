import type { LlmCostModuleDailyResponse, LlmModule } from '@/apis/admin.llmCost.api';
import { StackedBarChart } from '@/components/charts/StackedBarChart';

const MODULES: LlmModule[] = ['QNA', 'GUIDE', 'EMBEDDING', 'INGESTION', 'ELIGIBILITY'];
const COLORS: Record<LlmModule, string> = {
  QNA: '#6366f1',
  GUIDE: '#10b981',
  EMBEDDING: '#f59e0b',
  INGESTION: '#ec4899',
  ELIGIBILITY: '#8b5cf6',
};

interface Props {
  rows: LlmCostModuleDailyResponse[];
}

export function LlmCostStackedBar({ rows }: Props) {
  const map = new Map<string, Record<LlmModule, number>>();
  rows.forEach((r) => {
    if (!map.has(r.date)) {
      map.set(
        r.date,
        MODULES.reduce((a, m) => ({ ...a, [m]: 0 }), {} as Record<LlmModule, number>),
      );
    }
    map.get(r.date)![r.module] = r.totalCostUsd;
  });
  const data = Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, costs]) => ({ date, ...costs }));

  if (data.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center rounded border border-slate-200 bg-slate-50 text-sm text-slate-500">
        데이터 없음
      </div>
    );
  }

  return (
    <StackedBarChart
      data={data}
      xKey="date"
      series={MODULES.map((m) => ({ key: m, name: m, color: COLORS[m] }))}
    />
  );
}
