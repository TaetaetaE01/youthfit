import { useState } from 'react';
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
      <div className="flex items-center gap-3 mb-2 text-sm">
        <label className="flex items-center gap-1">
          <input
            type="checkbox"
            checked={needsReview}
            onChange={(e) => {
              setNeedsReview(e.target.checked);
              setPage(0);
            }}
          />
          검토 필요만
        </label>
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          placeholder="제목/기관 검색"
          className="border px-2 py-1 flex-1"
        />
      </div>

      <table className="w-full text-sm">
        <thead className="bg-gray-50 text-left">
          <tr>
            <th className="p-2">제목</th>
            <th className="p-2">상태</th>
            <th className="p-2">신뢰도</th>
            <th className="p-2">레벨</th>
            <th className="p-2">최근 잡</th>
          </tr>
        </thead>
        <tbody>
          {isLoading && (
            <tr>
              <td colSpan={5} className="p-4">
                로딩...
              </td>
            </tr>
          )}
          {data?.content.map((c) => (
            <tr
              key={c.id}
              onClick={() => onSelect(c.id)}
              className="cursor-pointer hover:bg-gray-50"
            >
              <td className="p-2">{c.title}</td>
              <td className="p-2">{c.enrichmentStatus ?? '-'}</td>
              <td className="p-2">{c.confidence ?? '-'}</td>
              <td className="p-2">{c.detailLevel}</td>
              <td className="p-2">
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

      <div className="flex justify-between mt-2 text-xs">
        <span>전체 {data?.totalElements ?? 0}건</span>
        <div className="flex gap-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            이전
          </button>
          <span>{page + 1}</span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={(data?.content.length ?? 0) < 20}
          >
            다음
          </button>
        </div>
      </div>
    </div>
  );
}
