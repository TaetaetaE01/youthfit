import type { AreaStatus } from '@/types/adminDashboard';

const CONFIG: Record<AreaStatus, { label: string; className: string; icon: string }> = {
  OK: { label: '정상', className: 'bg-success-50 text-success-700', icon: '✓' },
  WARN: { label: '주의', className: 'bg-amber-50 text-amber-700', icon: '⚠' },
  CRITICAL: { label: '확인 필요', className: 'bg-error-50 text-error-700', icon: '⚠' },
};

export default function StatusBadge({ status }: { status: AreaStatus }) {
  const c = CONFIG[status];
  return (
    <span
      data-status={status}
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${c.className}`}
    >
      <span aria-hidden>{c.icon}</span>
      {c.label}
    </span>
  );
}
