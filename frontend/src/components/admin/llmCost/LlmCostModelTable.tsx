import type { LlmCostModelSummaryResponse } from '@/apis/admin.llmCost.api';

interface Props {
  rows: LlmCostModelSummaryResponse[];
}

export function LlmCostModelTable({ rows }: Props) {
  if (rows.length === 0) {
    return (
      <div className="grid place-items-center rounded-xl border border-dashed border-slate-200 bg-white px-6 py-8 text-center text-sm text-slate-500">
        호출 기록 없음
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white">
      <table className="min-w-full text-sm">
        <thead>
          <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-500">
            <th className="px-3 py-2.5 text-left">모델</th>
            <th className="px-3 py-2.5 text-right">호출 수</th>
            <th className="px-3 py-2.5 text-right">입력 토큰</th>
            <th className="px-3 py-2.5 text-right">출력 토큰</th>
            <th className="px-3 py-2.5 text-right">총 토큰</th>
            <th className="px-3 py-2.5 text-right">비용 (USD)</th>
            <th className="px-3 py-2.5 text-right">비중</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((r) => (
            <tr key={r.model} className="transition-colors hover:bg-slate-50/60">
              <td className="px-3 py-3 font-mono text-xs text-slate-700">{r.model}</td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-700">
                {r.callCount.toLocaleString()}
              </td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-600">
                {r.promptTokens.toLocaleString()}
              </td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-600">
                {r.completionTokens.toLocaleString()}
              </td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-700">
                {r.totalTokens.toLocaleString()}
              </td>
              <td className="px-3 py-3 text-right font-semibold tabular-nums text-slate-900">
                ${r.totalCostUsd.toFixed(4)}
              </td>
              <td className="px-3 py-3 text-right">
                <CostShareBar value={r.costShare} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CostShareBar({ value }: { value: number }) {
  return (
    <div className="inline-flex items-center gap-2">
      <span className="tabular-nums text-xs text-slate-500">{value.toFixed(2)}%</span>
      <span className="relative h-1.5 w-16 overflow-hidden rounded-full bg-slate-100">
        <span
          className="absolute inset-y-0 left-0 rounded-full bg-gradient-to-r from-indigo-400 to-brand-700"
          style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
        />
      </span>
    </div>
  );
}
