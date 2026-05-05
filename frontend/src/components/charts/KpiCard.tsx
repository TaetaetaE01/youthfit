import type { ReactNode } from 'react';

export type KpiCardProps = {
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: 'default' | 'success' | 'warning' | 'danger';
};

const TONE: Record<NonNullable<KpiCardProps['tone']>, string> = {
  default: 'text-slate-900',
  success: 'text-emerald-600',
  warning: 'text-amber-600',
  danger: 'text-red-600',
};

export function KpiCard({ label, value, hint, tone = 'default' }: KpiCardProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-2 text-2xl font-semibold ${TONE[tone]}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-400">{hint}</div>}
    </div>
  );
}
