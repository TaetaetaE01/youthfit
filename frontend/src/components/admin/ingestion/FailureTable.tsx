import { Link } from 'react-router-dom';
import { FailureReasonBadge } from './FailureReasonBadge';
import type { IngestionFailureSummaryResponse } from '@/apis/admin.ingestion.api';

interface Props {
  rows: IngestionFailureSummaryResponse[];
  onRetry: (id: number) => void;
}

export function FailureTable({ rows, onRetry }: Props) {
  if (rows.length === 0) {
    return (
      <div className="rounded border border-slate-200 bg-slate-50 p-4 text-center text-sm text-slate-500">
        실패 항목 없음
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded border border-slate-200">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2 text-left">시각</th>
            <th className="px-3 py-2 text-left">source</th>
            <th className="px-3 py-2 text-left">사유</th>
            <th className="px-3 py-2 text-left">externalId</th>
            <th className="px-3 py-2 text-left">에러</th>
            <th className="px-3 py-2 text-right">재시도</th>
            <th className="px-3 py-2 text-center">액션</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-t border-slate-100">
              <td className="px-3 py-2 text-slate-600">
                {new Date(r.createdAt).toLocaleString('ko-KR')}
              </td>
              <td className="px-3 py-2 font-mono text-xs">{r.source}</td>
              <td className="px-3 py-2">
                <FailureReasonBadge reason={r.failureReason} />
              </td>
              <td className="px-3 py-2 text-xs text-slate-500">{r.sourceItemId ?? '-'}</td>
              <td className="max-w-md truncate px-3 py-2 text-xs text-slate-700">
                {r.errorMessageExcerpt}
              </td>
              <td className="px-3 py-2 text-right">{r.retryCount}</td>
              <td className="px-3 py-2 text-center">
                <button
                  onClick={() => onRetry(r.id)}
                  className="rounded bg-indigo-50 px-2 py-1 text-xs text-indigo-700 hover:bg-indigo-100"
                >
                  재처리
                </button>{' '}
                <Link
                  to={`/admin/ingestion/failures/${r.id}`}
                  className="text-xs text-slate-500 underline"
                >
                  상세
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
