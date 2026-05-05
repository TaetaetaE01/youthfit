import { Link } from 'react-router-dom';
import { QnaCacheResultBadge } from './QnaCacheResultBadge';
import type { QnaCacheLookupSummary } from '@/apis/admin.qnaCache.api';

export function QnaCacheLookupTable({ rows }: { rows: QnaCacheLookupSummary[] }) {
  if (rows.length === 0) {
    return (
      <div className="rounded-lg bg-white p-8 text-center text-sm text-slate-500 shadow-sm">
        데이터 없음
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-4 py-2 text-left">시각</th>
            <th className="px-4 py-2 text-left">결과</th>
            <th className="px-4 py-2 text-left">정책 ID</th>
            <th className="px-4 py-2 text-left">질문</th>
            <th className="px-4 py-2 text-right">유사도</th>
            <th className="px-4 py-2 text-right">매칭 캐시</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-b border-slate-100">
              <td className="px-4 py-2 text-slate-600">
                <Link
                  to={`/admin/qna-cache/${r.id}`}
                  className="text-indigo-600 hover:underline"
                >
                  {new Date(r.lookedUpAt).toLocaleString('ko-KR', {
                    year: '2-digit',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </Link>
              </td>
              <td className="px-4 py-2">
                <QnaCacheResultBadge result={r.result} />
              </td>
              <td className="px-4 py-2 text-slate-700">{r.policyId}</td>
              <td className="max-w-xs truncate px-4 py-2 text-slate-700">
                {r.questionExcerpt}
              </td>
              <td className="px-4 py-2 text-right text-slate-700">
                {r.similarityScore != null ? r.similarityScore.toFixed(3) : '-'}
              </td>
              <td className="px-4 py-2 text-right text-slate-700">
                {r.matchedCachedId ?? '-'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
