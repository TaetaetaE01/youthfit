import type { LucideIcon } from 'lucide-react';
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
};

const TONE_STYLES: Record<NonNullable<Props['iconTone']>, string> = {
  brand: 'bg-brand-100 text-brand-700',
  success: 'bg-success-100 text-success-700',
  warning: 'bg-amber-100 text-amber-700',
  error: 'bg-rose-100 text-rose-700',
};

export default function AdminKpiCard({
  icon: Icon,
  iconTone = 'brand',
  title,
  value,
  trend,
  hint,
  ready = false,
}: Props) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card">
      <div className="flex items-start justify-between">
        <div className={`grid h-11 w-11 place-items-center rounded-lg ${TONE_STYLES[iconTone]}`}>
          <Icon className="h-5 w-5" aria-hidden />
        </div>
        {!ready && (
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-500">
            준비 중
          </span>
        )}
      </div>

      <div className="mt-4">
        <div className="text-sm text-slate-500">{title}</div>
        <div className="mt-1 text-2xl font-bold tabular-nums text-slate-900">
          {value}
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3 text-xs">
        {trend ? (
          <span
            className={`inline-flex items-center gap-1 font-semibold ${
              trend.direction === 'up' ? 'text-success-700' : 'text-error-500'
            }`}
          >
            {trend.direction === 'up' ? (
              <TrendingUp className="h-3.5 w-3.5" aria-hidden />
            ) : (
              <TrendingDown className="h-3.5 w-3.5" aria-hidden />
            )}
            {trend.value}
            {trend.label && <span className="ml-1 font-normal text-slate-400">{trend.label}</span>}
          </span>
        ) : (
          <span className="text-slate-400">{hint ?? '데이터 연결 대기'}</span>
        )}
        <button
          type="button"
          className="text-slate-400 hover:text-slate-600"
          disabled={!ready}
        >
          상세 →
        </button>
      </div>
    </div>
  );
}
