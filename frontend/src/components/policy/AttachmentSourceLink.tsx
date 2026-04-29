import type { AttachmentRef, PolicyAttachment } from '@/types/policy';

interface Props {
  attachmentRef: AttachmentRef;
  attachments: PolicyAttachment[];
  className?: string;
}

export function AttachmentSourceLink({ attachmentRef, attachments, className }: Props) {
  const target = attachments.find((a) => a.id === attachmentRef.attachmentId);
  if (!target) return null;

  const pageLabel =
    attachmentRef.pageStart === null
      ? ''
      : attachmentRef.pageStart === attachmentRef.pageEnd
        ? ` · ${attachmentRef.pageStart}페이지`
        : ` · ${attachmentRef.pageStart}-${attachmentRef.pageEnd}페이지`;

  const href =
    `/api/policies/attachments/${attachmentRef.attachmentId}/file` +
    (attachmentRef.pageStart !== null ? `#page=${attachmentRef.pageStart}` : '');

  return (
    <button
      type="button"
      onClick={() => window.open(href, '_blank', 'noopener,noreferrer')}
      className={
        className ??
        'inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ' +
          'border-indigo-300 text-indigo-800 hover:bg-indigo-100'
      }
    >
      📎 첨부: {target.name}
      {pageLabel} ↗
    </button>
  );
}
