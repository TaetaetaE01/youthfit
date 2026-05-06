import type { LlmCostModelSummaryResponse } from '@/apis/admin.llmCost.api';

interface Props {
  rows: LlmCostModelSummaryResponse[];
}

export function LlmCostModelTable({ rows }: Props) {
  if (rows.length === 0) {
    return (
      <div className="rounded border border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
        호출 기록 없음
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2 text-left">모델</th>
            <th className="px-3 py-2 text-right">호출 수</th>
            <th className="px-3 py-2 text-right">입력 토큰</th>
            <th className="px-3 py-2 text-right">출력 토큰</th>
            <th className="px-3 py-2 text-right">총 토큰</th>
            <th className="px-3 py-2 text-right">비용 (USD)</th>
            <th className="px-3 py-2 text-right">비중</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.model} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.model}</td>
              <td className="px-3 py-2 text-right">{r.callCount.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{r.promptTokens.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{r.completionTokens.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{r.totalTokens.toLocaleString()}</td>
              <td className="px-3 py-2 text-right font-semibold">${r.totalCostUsd.toFixed(4)}</td>
              <td className="px-3 py-2 text-right text-slate-500">{r.costShare.toFixed(2)}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
