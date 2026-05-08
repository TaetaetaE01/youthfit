import { CheckCircle2, AlertTriangle } from 'lucide-react';
import type { IngestionSourceSummaryResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionSourceSummaryResponse[];
}

export function SourceTable({ rows }: Props) {
  if (rows.length === 0) {
    return (
      <div className="grid place-items-center rounded-xl border border-dashed border-slate-200 bg-white px-6 py-8 text-center text-sm text-slate-500">
        수신 기록 없음
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white">
      <table className="min-w-full text-sm">
        <thead>
          <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-500">
            <th className="px-3 py-2.5 text-left">source</th>
            <th className="px-3 py-2.5 text-left">마지막 수신</th>
            <th className="px-3 py-2.5 text-right">7일 신규</th>
            <th className="px-3 py-2.5 text-right">실패율</th>
            <th className="px-3 py-2.5 text-center">상태</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((r) => (
            <tr key={r.source} className="transition-colors hover:bg-slate-50/60">
              <td className="px-3 py-3 font-mono text-xs text-slate-700">{r.source}</td>
              <td className="px-3 py-3 text-slate-600">
                {new Date(r.lastReceivedAt).toLocaleString('ko-KR')}
              </td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-700">
                {r.sevenDayReceived.toLocaleString()}
              </td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-700">
                {(r.sevenDayFailureRate * 100).toFixed(1)}%
              </td>
              <td className="px-3 py-3 text-center">
                {r.stale ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-[11px] font-semibold text-rose-700 ring-1 ring-inset ring-rose-600/20">
                    <AlertTriangle className="h-3 w-3" aria-hidden />
                    24h 미수신
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                    <CheckCircle2 className="h-3 w-3" aria-hidden />
                    정상
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
