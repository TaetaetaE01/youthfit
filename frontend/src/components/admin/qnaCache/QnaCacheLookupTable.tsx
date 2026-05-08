import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { QnaCacheResultBadge } from './QnaCacheResultBadge';
import type { QnaCacheLookupSummary } from '@/apis/admin.qnaCache.api';

export function QnaCacheLookupTable({ rows }: { rows: QnaCacheLookupSummary[] }) {
  if (rows.length === 0) {
    return (
      <div className="grid place-items-center rounded-xl border border-dashed border-slate-200 bg-white px-6 py-10 text-center">
        <p className="text-sm font-medium text-slate-700">조건에 맞는 캐시 조회가 없습니다.</p>
        <p className="mt-1 text-xs text-slate-500">필터를 넓혀보세요.</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white shadow-card">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-500">
            <th className="px-4 py-2.5 text-left">시각</th>
            <th className="px-4 py-2.5 text-left">결과</th>
            <th className="px-4 py-2.5 text-left">정책 ID</th>
            <th className="px-4 py-2.5 text-left">질문</th>
            <th className="px-4 py-2.5 text-right">유사도</th>
            <th className="px-4 py-2.5 text-right">매칭 캐시</th>
            <th className="px-4 py-2.5 text-right">액션</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((r) => (
            <tr key={r.id} className="transition-colors hover:bg-slate-50/60">
              <td className="px-4 py-3 font-mono text-xs tabular-nums text-slate-500">
                {new Date(r.lookedUpAt).toLocaleString('ko-KR', {
                  year: '2-digit',
                  month: '2-digit',
                  day: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </td>
              <td className="px-4 py-3">
                <QnaCacheResultBadge result={r.result} />
              </td>
              <td className="px-4 py-3 text-slate-700 tabular-nums">{r.policyId}</td>
              <td className="max-w-xs truncate px-4 py-3 text-slate-700">
                {r.questionExcerpt}
              </td>
              <td className="px-4 py-3 text-right tabular-nums text-slate-700">
                {r.similarityScore != null ? r.similarityScore.toFixed(3) : '-'}
              </td>
              <td className="px-4 py-3 text-right tabular-nums text-slate-500">
                {r.matchedCachedId ?? '-'}
              </td>
              <td className="px-4 py-3 text-right">
                <Link
                  to={`/admin/qna-cache/${r.id}`}
                  className="inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-[11px] font-semibold text-brand-700 transition-colors hover:bg-brand-50"
                >
                  상세
                  <ChevronRight className="h-3 w-3" aria-hidden />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
