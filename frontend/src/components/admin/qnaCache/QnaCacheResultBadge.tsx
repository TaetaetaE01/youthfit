import type { LookupResultType } from '@/apis/admin.qnaCache.api';

const STYLE: Record<LookupResultType, { label: string; cls: string }> = {
  HIT:             { label: 'HIT',   cls: 'bg-emerald-100 text-emerald-800' },
  BELOW_THRESHOLD: { label: 'BELOW', cls: 'bg-amber-100 text-amber-800' },
  MISS:            { label: 'MISS',  cls: 'bg-red-100 text-red-800' },
};

export function QnaCacheResultBadge({ result }: { result: LookupResultType }) {
  const s = STYLE[result];
  return (
    <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ${s.cls}`}>
      {s.label}
    </span>
  );
}
