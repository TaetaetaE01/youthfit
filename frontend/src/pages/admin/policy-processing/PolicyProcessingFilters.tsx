import type {
  Filter,
  PolicyProcessingListParams,
  Sort,
} from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';

interface Props {
  params: PolicyProcessingListParams;
  onChange: (next: PolicyProcessingListParams) => void;
  chipCounts: Partial<Record<Filter, number>>;
}

const CHIPS: Array<{ key: Filter; label: string; tone: 'default' | 'fail' | 'warn' }> = [
  { key: 'ALL', label: '전체', tone: 'default' },
  { key: 'INCOMPLETE', label: '미흡만', tone: 'fail' },
  { key: 'PARTIAL', label: '부분만', tone: 'warn' },
  { key: 'RAG_FAILED', label: 'RAG 본문 FAILED', tone: 'default' },
  { key: 'ATTACHMENT_EMBEDDING_MISSING', label: '첨부 임베딩 누락', tone: 'default' },
  { key: 'REFERENCE_FETCH_FAILED', label: '참조 fetch 실패', tone: 'default' },
  { key: 'GUIDE_RULE_FAILED', label: 'GUIDE/RULE 실패', tone: 'default' },
  { key: 'RECENT_24H', label: '최근 24h', tone: 'default' },
];

export function PolicyProcessingFilters({ params, onChange, chipCounts }: Props) {
  return (
    <div className="space-y-3 mb-4">
      <div className="flex items-center gap-2">
        <input
          type="text"
          className="rounded border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm flex-1 max-w-xs"
          placeholder="정책 ID, 제목 검색…"
          defaultValue={params.q ?? ''}
          onChange={(e) =>
            onChange({ ...params, q: e.target.value || undefined, page: 0 })
          }
        />
        <select
          className="rounded border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm"
          value={params.sort ?? 'UPDATED_DESC'}
          onChange={(e) =>
            onChange({ ...params, sort: e.target.value as Sort })
          }
        >
          <option value="UPDATED_DESC">업데이트 최신순</option>
          <option value="COMPLETENESS_ASC">완성도 미흡순</option>
          <option value="ID_ASC">ID 순</option>
        </select>
      </div>
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-xs text-slate-500">빠른 필터</span>
        {CHIPS.map((chip) => {
          const active = (params.filter ?? 'ALL') === chip.key;
          const count = chipCounts[chip.key];
          const disabled = chip.key === 'REFERENCE_FETCH_FAILED';
          return (
            <button
              key={chip.key}
              type="button"
              disabled={disabled}
              onClick={() =>
                onChange({ ...params, filter: chip.key, page: 0 })
              }
              className={cn(
                'rounded-full border px-3 py-1 text-xs',
                active && chip.tone === 'fail' && 'border-red-500 bg-red-900/30 text-red-400',
                active && chip.tone === 'warn' && 'border-amber-500 bg-amber-900/30 text-amber-400',
                active && chip.tone === 'default' && 'border-green-500 bg-green-900/30 text-green-400',
                !active && 'border-slate-700 bg-slate-900 text-slate-400',
                disabled && 'opacity-50 cursor-not-allowed',
              )}
            >
              {chip.label}
              {count !== undefined && ` ${count}`}
            </button>
          );
        })}
      </div>
    </div>
  );
}
