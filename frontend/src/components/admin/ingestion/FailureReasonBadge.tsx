import type { FailureReason } from '@/apis/admin.ingestion.api';

const REASON_STYLES: Record<FailureReason, { color: string; label: string }> = {
  VALIDATION: { color: 'bg-amber-100 text-amber-800', label: '검증 실패' },
  PARSING: { color: 'bg-orange-100 text-orange-800', label: '파싱 실패' },
  MAPPING: { color: 'bg-rose-100 text-rose-800', label: '매핑 실패' },
  DEDUPLICATION_CONFLICT: { color: 'bg-purple-100 text-purple-800', label: '중복 충돌' },
  OTHER: { color: 'bg-slate-100 text-slate-700', label: '기타' },
};

interface Props {
  reason: FailureReason;
}

export function FailureReasonBadge({ reason }: Props) {
  const s = REASON_STYLES[reason];
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${s.color}`}>
      {s.label}
    </span>
  );
}
