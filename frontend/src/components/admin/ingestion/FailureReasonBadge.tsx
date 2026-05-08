import type { FailureReason } from '@/apis/admin.ingestion.api';

const REASON_STYLES: Record<FailureReason, { color: string; label: string }> = {
  VALIDATION: { color: 'bg-amber-50 text-amber-700 ring-amber-600/20', label: '검증 실패' },
  PARSING: { color: 'bg-orange-50 text-orange-700 ring-orange-600/20', label: '파싱 실패' },
  MAPPING: { color: 'bg-rose-50 text-rose-700 ring-rose-600/20', label: '매핑 실패' },
  DEDUPLICATION_CONFLICT: {
    color: 'bg-purple-50 text-purple-700 ring-purple-600/20',
    label: '중복 충돌',
  },
  OTHER: { color: 'bg-slate-100 text-slate-600 ring-slate-500/20', label: '기타' },
};

interface Props {
  reason: FailureReason;
}

export function FailureReasonBadge({ reason }: Props) {
  const s = REASON_STYLES[reason];
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${s.color}`}
    >
      {s.label}
    </span>
  );
}
