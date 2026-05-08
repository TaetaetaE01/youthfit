import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  ChartEmptyState,
  ChartLegend,
  ChartTooltip,
} from '@/components/charts/ChartShared';
import type { LlmCostSeriesPoint, LlmModule } from '@/apis/admin.llmCost.api';

const MODULES: LlmModule[] = ['QNA', 'GUIDE', 'EMBEDDING', 'INGESTION', 'ELIGIBILITY'];
const COLORS: Record<LlmModule, string> = {
  QNA: '#6366f1',
  GUIDE: '#10b981',
  EMBEDDING: '#f59e0b',
  INGESTION: '#ec4899',
  ELIGIBILITY: '#8b5cf6',
};

interface Props {
  points: LlmCostSeriesPoint[];
}

export function LlmCostLineChart({ points }: Props) {
  if (points.length === 0) {
    return <ChartEmptyState height={320} message="아직 비용 데이터가 없습니다" />;
  }

  const data = points.map((p) => ({
    at: new Date(p.at).toLocaleString('ko-KR', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
    }),
    ...MODULES.reduce(
      (acc, m) => ({ ...acc, [m]: p.costByModule[m] ?? 0 }),
      {} as Record<LlmModule, number>,
    ),
  }));

  const series = MODULES.map((m) => ({ key: m, name: m, color: COLORS[m] }));
  const formatUsd = (v: number | string | undefined) =>
    typeof v === 'number' ? `$${v.toFixed(4)}` : String(v ?? '-');

  return (
    <div className="space-y-3">
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: -8 }}>
          <CartesianGrid strokeDasharray="2 4" stroke="#e2e8f0" vertical={false} />
          <XAxis
            dataKey="at"
            tick={{ fontSize: 11, fill: '#64748b' }}
            tickLine={false}
            axisLine={{ stroke: '#e2e8f0' }}
            tickMargin={8}
          />
          <YAxis
            tickFormatter={(v: number) => `$${v.toFixed(3)}`}
            tick={{ fontSize: 11, fill: '#94a3b8' }}
            tickLine={false}
            axisLine={false}
            width={56}
          />
          <Tooltip
            content={<ChartTooltip formatter={formatUsd} />}
            cursor={{ stroke: '#cbd5e1', strokeDasharray: '3 3' }}
          />
          {MODULES.map((m) => (
            <Line
              key={m}
              type="monotone"
              dataKey={m}
              stroke={COLORS[m]}
              dot={false}
              activeDot={{ r: 4, strokeWidth: 2, stroke: '#fff' }}
              strokeWidth={2}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
      <ChartLegend series={series} className="px-1" />
    </div>
  );
}
