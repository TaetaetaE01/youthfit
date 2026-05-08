import type { ReactNode } from 'react';
import { TrendingDown, TrendingUp } from 'lucide-react';

export type KpiCardProps = {
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: 'default' | 'success' | 'warning' | 'danger';
  delta?: { direction: 'up' | 'down'; label: string };
  sparkline?: ReactNode;
};

const VALUE_TONE: Record<NonNullable<KpiCardProps['tone']>, string> = {
  default: 'text-slate-900',
  success: 'text-emerald-600',
  warning: 'text-amber-600',
  danger: 'text-rose-600',
};

const DELTA_TONE: Record<'up' | 'down', string> = {
  up: 'bg-emerald-50 text-emerald-700 ring-emerald-600/10',
  down: 'bg-rose-50 text-rose-600 ring-rose-600/10',
};

export function KpiCard({
  label,
  value,
  hint,
  tone = 'default',
  delta,
  sparkline,
}: KpiCardProps) {
  return (
    <div className="group relative overflow-hidden rounded-xl border border-slate-200/80 bg-white p-5 shadow-card transition-all duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-card-hover">
      <div className="flex items-start justify-between gap-2">
        <div className="text-xs font-medium text-slate-500">{label}</div>
        {delta && (
          <span
            className={[
              'inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[10px] font-semibold ring-1 ring-inset',
              DELTA_TONE[delta.direction],
            ].join(' ')}
          >
            {delta.direction === 'up' ? (
              <TrendingUp className="h-2.5 w-2.5" aria-hidden />
            ) : (
              <TrendingDown className="h-2.5 w-2.5" aria-hidden />
            )}
            {delta.label}
          </span>
        )}
      </div>

      <div
        className={[
          'mt-2.5 text-[26px] font-bold leading-none tabular-nums tracking-tight',
          VALUE_TONE[tone],
        ].join(' ')}
      >
        {value}
      </div>

      <div className="mt-3 flex items-end justify-between gap-2">
        {hint ? (
          <div className="text-xs text-slate-400">{hint}</div>
        ) : (
          <span aria-hidden />
        )}
        {sparkline && <div className="ml-auto h-9 w-24 shrink-0">{sparkline}</div>}
      </div>
    </div>
  );
}
