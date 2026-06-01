import type { AttachmentStatus } from '@/types/adminEnrichment';

const STYLES: Record<AttachmentStatus, { cls: string; label: string }> = {
  PENDING: { cls: 'bg-gray-50 text-gray-700 ring-gray-600/20', label: '대기' },
  DOWNLOADING: { cls: 'bg-blue-50 text-blue-700 ring-blue-600/20', label: '다운로드중' },
  DOWNLOADED: { cls: 'bg-blue-50 text-blue-700 ring-blue-600/20', label: '다운로드됨' },
  EXTRACTING: { cls: 'bg-blue-50 text-blue-700 ring-blue-600/20', label: '추출중' },
  EXTRACTED: { cls: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: '추출완료' },
  FAILED: { cls: 'bg-rose-50 text-rose-700 ring-rose-600/20', label: '실패' },
  SKIPPED: { cls: 'bg-amber-50 text-amber-700 ring-amber-600/20', label: '건너뜀' },
};

interface Props {
  status: AttachmentStatus;
}

export function AttachmentStatusBadge({ status }: Props) {
  const s = STYLES[status];
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${s.cls}`}
    >
      {s.label}
    </span>
  );
}
