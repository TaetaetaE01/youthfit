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

  return (
    <StackedBarChart
      data={data}
      xKey="date"
      series={MODULES.map((m) => ({ key: m, name: m, color: COLORS[m] }))}
      valueFormatter={(v) =>
        typeof v === 'number' ? `$${v.toFixed(4)}` : String(v ?? '-')
      }
      emptyMessage="아직 모듈별 비용 데이터가 없습니다"
    />
  );
}
