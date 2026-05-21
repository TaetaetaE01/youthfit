import type { JobStatus } from '@/types/adminEnrichment';

const STYLES: Record<JobStatus, { bg: string; label: string }> = {
  PENDING: { bg: 'bg-gray-200 text-gray-700', label: '대기' },
  RUNNING: { bg: 'bg-blue-200 text-blue-800', label: '진행' },
  SUCCESS: { bg: 'bg-green-200 text-green-800', label: '성공' },
  FAILED: { bg: 'bg-red-200 text-red-800', label: '실패' },
};

interface Props {
  status: JobStatus;
}

export function EnrichmentJobBadge({ status }: Props) {
  const s = STYLES[status];
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${s.bg}`}>
      {s.label}
    </span>
  );
}
