import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { TrendingDown, TrendingUp } from 'lucide-react';

type Trend = {
  direction: 'up' | 'down';
  value: string;
  label?: string;
};

type Props = {
  icon: LucideIcon;
  iconTone?: 'brand' | 'success' | 'warning' | 'error';
  title: string;
  value: string;
  trend?: Trend;
  hint?: string;
  ready?: boolean;
  sparkline?: ReactNode;
};

const TONE_STYLES: Record<NonNullable<Props['iconTone']>, string> = {
  brand: 'bg-brand-100 text-brand-700 ring-brand-700/10',
  success: 'bg-success-100 text-success-700 ring-success-700/10',
  warning: 'bg-amber-100 text-amber-700 ring-amber-700/10',
  error: 'bg-rose-100 text-rose-700 ring-rose-700/10',
};

const TONE_GLOW: Record<NonNullable<Props['iconTone']>, string> = {
  brand: 'before:bg-brand-700/[0.04]',
  success: 'before:bg-success-500/[0.04]',
  warning: 'before:bg-amber-500/[0.04]',
  error: 'before:bg-rose-500/[0.04]',
};

export default function AdminKpiCard({
  icon: Icon,
  iconTone = 'brand',
  title,
  value,
  trend,
  hint,
  ready = false,
  sparkline,
}: Props) {
  return (
    <div
      className={[
        'group relative overflow-hidden rounded-xl border border-slate-200/80 bg-white p-5 shadow-card transition-all duration-200',
        'hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-card-hover',
        'before:pointer-events-none before:absolute before:inset-0 before:opacity-0 before:transition-opacity before:duration-300 group-hover:before:opacity-100',
        TONE_GLOW[iconTone],
      ].join(' ')}
    >
      <div className="relative flex items-start justify-between">
        <div
          className={[
            'grid h-11 w-11 place-items-center rounded-lg ring-1 transition-transform duration-200 group-hover:scale-105',
            TONE_STYLES[iconTone],
          ].join(' ')}
        >
          <Icon className="h-5 w-5" aria-hidden />
        </div>
        {!ready && (
          <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[10px] font-medium text-slate-500">
            <span className="h-1 w-1 rounded-full bg-slate-400" aria-hidden />
            준비 중
          </span>
        )}
      </div>

      <div className="relative mt-4">
        <div className="text-xs font-medium text-slate-500">{title}</div>
        <div className="mt-1.5 text-[26px] font-bold leading-none tabular-nums tracking-tight text-slate-900">
          {value}
        </div>
      </div>

      {sparkline && <div className="relative mt-3 h-10">{sparkline}</div>}

      <div className="relative mt-4 flex items-center justify-between border-t border-slate-100 pt-3 text-xs">
        {trend ? (
          <span
            className={[
              'inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 font-semibold',
              trend.direction === 'up'
                ? 'bg-success-100/60 text-success-700'
                : 'bg-rose-100/60 text-rose-600',
            ].join(' ')}
          >
            {trend.direction === 'up' ? (
              <TrendingUp className="h-3 w-3" aria-hidden />
            ) : (
              <TrendingDown className="h-3 w-3" aria-hidden />
            )}
            {trend.value}
          </span>
        ) : (
          <span className="text-slate-400">{hint ?? '데이터 연결 대기'}</span>
        )}
        {trend?.label && (
          <span className="text-slate-400">{trend.label}</span>
        )}
      </div>
    </div>
  );
}
