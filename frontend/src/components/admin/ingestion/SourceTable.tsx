import type { IngestionSourceSummaryResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionSourceSummaryResponse[];
}

export function SourceTable({ rows }: Props) {
  if (rows.length === 0) {
    return (
      <div className="rounded border border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
        수신 기록 없음
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2 text-left">source</th>
            <th className="px-3 py-2 text-left">마지막 수신</th>
            <th className="px-3 py-2 text-right">7일 신규</th>
            <th className="px-3 py-2 text-right">실패율</th>
            <th className="px-3 py-2 text-center">상태</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.source} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.source}</td>
              <td className="px-3 py-2 text-slate-600">
                {new Date(r.lastReceivedAt).toLocaleString('ko-KR')}
              </td>
              <td className="px-3 py-2 text-right">{r.sevenDayReceived.toLocaleString()}</td>
              <td className="px-3 py-2 text-right">{(r.sevenDayFailureRate * 100).toFixed(1)}%</td>
              <td className="px-3 py-2 text-center">
                {r.stale ? (
                  <span className="inline-block rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700">
                    24h 미수신
                  </span>
                ) : (
                  <span className="inline-block rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700">
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
