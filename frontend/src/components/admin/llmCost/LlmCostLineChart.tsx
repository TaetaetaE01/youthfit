import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
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
    return (
      <div className="flex h-64 items-center justify-center rounded border border-slate-200 bg-slate-50 text-sm text-slate-500">
        데이터 없음
      </div>
    );
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

  return (
    <ResponsiveContainer width="100%" height={320}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="at" tick={{ fontSize: 11 }} />
        <YAxis tickFormatter={(v: number) => `$${v.toFixed(3)}`} tick={{ fontSize: 11 }} />
        <Tooltip formatter={(v) => typeof v === 'number' ? `$${v.toFixed(4)}` : String(v)} />
        <Legend />
        {MODULES.map((m) => (
          <Line
            key={m}
            type="monotone"
            dataKey={m}
            stroke={COLORS[m]}
            dot={false}
            strokeWidth={2}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
