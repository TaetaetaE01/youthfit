import type { LookupResultType } from '@/apis/admin.qnaCache.api';

const STYLE: Record<LookupResultType, { label: string; cls: string }> = {
  HIT: { label: 'HIT', cls: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  BELOW_THRESHOLD: { label: 'BELOW', cls: 'bg-amber-50 text-amber-700 ring-amber-600/20' },
  MISS: { label: 'MISS', cls: 'bg-rose-50 text-rose-700 ring-rose-600/20' },
};

export function QnaCacheResultBadge({ result }: { result: LookupResultType }) {
  const s = STYLE[result];
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${s.cls}`}
    >
      {s.label}
    </span>
  );
}
