import { Link } from 'react-router-dom';
import { ChevronRight, RotateCcw } from 'lucide-react';
import { FailureReasonBadge } from './FailureReasonBadge';
import type { IngestionFailureSummaryResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionFailureSummaryResponse[];
  onRetry: (id: number) => void;
}

export function FailureTable({ rows, onRetry }: Props) {
  if (rows.length === 0) {
    return (
      <div className="grid place-items-center rounded-xl border border-dashed border-slate-200 bg-white px-6 py-8 text-center text-sm text-slate-500">
        실패 항목 없음
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white">
      <table className="min-w-full text-sm">
        <thead>
          <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-500">
            <th className="px-3 py-2.5 text-left">시각</th>
            <th className="px-3 py-2.5 text-left">source</th>
            <th className="px-3 py-2.5 text-left">사유</th>
            <th className="px-3 py-2.5 text-left">externalId</th>
            <th className="px-3 py-2.5 text-left">에러</th>
            <th className="px-3 py-2.5 text-right">재시도</th>
            <th className="px-3 py-2.5 text-right">액션</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((r) => (
            <tr key={r.id} className="transition-colors hover:bg-slate-50/60">
              <td className="px-3 py-3 font-mono text-xs tabular-nums text-slate-500">
                {new Date(r.createdAt).toLocaleString('ko-KR')}
              </td>
              <td className="px-3 py-3 font-mono text-xs text-slate-700">{r.source}</td>
              <td className="px-3 py-3">
                <FailureReasonBadge reason={r.failureReason} />
              </td>
              <td className="px-3 py-3 text-xs text-slate-500">{r.sourceItemId ?? '-'}</td>
              <td className="max-w-md truncate px-3 py-3 text-xs text-slate-700">
                {r.errorMessageExcerpt}
              </td>
              <td className="px-3 py-3 text-right tabular-nums text-slate-700">
                {r.retryCount}
              </td>
              <td className="px-3 py-3 text-right">
                <div className="inline-flex items-center gap-1">
                  <button
                    onClick={() => onRetry(r.id)}
                    className="inline-flex items-center gap-1 rounded-md border border-brand-200 bg-brand-50 px-2 py-1 text-[11px] font-semibold text-brand-700 transition-colors hover:bg-brand-100"
                    title="재처리"
                  >
                    <RotateCcw className="h-3 w-3" aria-hidden />
                    재처리
                  </button>
                  <Link
                    to={`/admin/ingestion/failures/${r.id}`}
                    className="inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-[11px] font-semibold text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
                  >
                    상세
                    <ChevronRight className="h-3 w-3" aria-hidden />
                  </Link>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
