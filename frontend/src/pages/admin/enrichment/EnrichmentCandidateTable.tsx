import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useCandidates } from '@/hooks/useAdminEnrichment';
import { EnrichmentJobBadge } from '@/components/admin/enrichment/EnrichmentJobBadge';

interface Props {
  onSelect: (policyId: number) => void;
}

export function EnrichmentCandidateTable({ onSelect }: Props) {
  const [needsReview, setNeedsReview] = useState(true);
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useCandidates({ needsReview, q, page, size: 20 });

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1.5">
          <input
            id="needs-review-only"
            type="checkbox"
            checked={needsReview}
            onChange={(e) => {
              setNeedsReview(e.target.checked);
              setPage(0);
            }}
            className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-2 focus:ring-brand-700/10"
          />
          <label
            htmlFor="needs-review-only"
            className="cursor-pointer select-none text-xs text-slate-700"
          >
            검토 필요만
          </label>
        </div>
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          placeholder="제목/기관 검색"
          className="h-8 flex-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs text-slate-700 placeholder:text-slate-400 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/10"
        />
      </div>

      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs text-slate-500">
          <tr>
            <th className="px-3 py-2 font-medium">제목</th>
            <th className="px-3 py-2 font-medium">상태</th>
            <th className="px-3 py-2 font-medium">신뢰도</th>
            <th className="px-3 py-2 font-medium">레벨</th>
            <th className="px-3 py-2 font-medium">최근 잡</th>
          </tr>
        </thead>
        <tbody>
          {isLoading && (
            <tr>
              <td colSpan={5} className="px-3 py-4 text-slate-400">
                로딩...
              </td>
            </tr>
          )}
          {data?.content.map((c) => (
            <tr
              key={c.id}
              onClick={() => onSelect(c.id)}
              className="cursor-pointer border-t border-slate-100 hover:bg-slate-50"
            >
              <td className="px-3 py-2">{c.title}</td>
              <td className="px-3 py-2">{c.enrichmentStatus ?? '-'}</td>
              <td className="px-3 py-2">{c.confidence ?? '-'}</td>
              <td className="px-3 py-2">{c.detailLevel}</td>
              <td className="px-3 py-2">
                {c.latestJob ? (
                  <EnrichmentJobBadge status={c.latestJob.status} />
                ) : (
                  '-'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-sm">
        <span className="text-xs text-slate-500">
          총{' '}
          <strong className="font-semibold tabular-nums text-slate-700">
            {(data?.totalElements ?? 0).toLocaleString()}
          </strong>
          건
        </span>
        <div className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="inline-flex h-8 items-center gap-1 rounded-md px-2.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
            aria-label="이전 페이지"
          >
            <ChevronLeft className="h-3.5 w-3.5" aria-hidden />
            이전
          </button>
          <div className="h-4 w-px bg-slate-200" aria-hidden />
          <span className="px-2 text-xs tabular-nums text-slate-500">{page + 1}</span>
          <div className="h-4 w-px bg-slate-200" aria-hidden />
          <button
            type="button"
            onClick={() => setPage((p) => p + 1)}
            disabled={(data?.content.length ?? 0) < 20}
            className="inline-flex h-8 items-center gap-1 rounded-md px-2.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
            aria-label="다음 페이지"
          >
            다음
            <ChevronRight className="h-3.5 w-3.5" aria-hidden />
          </button>
        </div>
      </div>
    </div>
  );
}
