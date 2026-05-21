import type { JobStatus } from '@/types/adminEnrichment';

const STYLES: Record<JobStatus, { cls: string; label: string }> = {
  PENDING: { cls: 'bg-gray-50 text-gray-700 ring-gray-600/20', label: '대기' },
  RUNNING: { cls: 'bg-blue-50 text-blue-700 ring-blue-600/20', label: '진행' },
  SUCCESS: { cls: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: '성공' },
  FAILED: { cls: 'bg-rose-50 text-rose-700 ring-rose-600/20', label: '실패' },
};

interface Props {
  status: JobStatus;
}

export function EnrichmentJobBadge({ status }: Props) {
  const s = STYLES[status];
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${s.cls}`}
    >
      {s.label}
    </span>
  );
}
