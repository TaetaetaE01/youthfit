import type { ReactNode } from 'react';
import { BarChart3 } from 'lucide-react';

export type ChartSeriesMeta = {
  key: string;
  name: string;
  color: string;
};

type LegendProps = {
  series: ChartSeriesMeta[];
  className?: string;
};

export function ChartLegend({ series, className }: LegendProps) {
  return (
    <ul
      className={[
        'flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-slate-600',
        className ?? '',
      ].join(' ')}
    >
      {series.map((s) => (
        <li key={s.key} className="inline-flex items-center gap-1.5">
          <span
            className="h-2.5 w-2.5 rounded-full"
            style={{ backgroundColor: s.color }}
            aria-hidden
          />
          {s.name}
        </li>
      ))}
    </ul>
  );
}

type TooltipPayload = {
  dataKey?: string | number;
  name?: string;
  value?: number | string;
  color?: string;
};

type TooltipProps = {
  active?: boolean;
  payload?: TooltipPayload[];
  label?: string | number;
  formatter?: (v: number | string | undefined) => string;
};

export function ChartTooltip({
  active,
  payload,
  label,
  formatter,
}: TooltipProps) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-slate-200 bg-white/95 px-3 py-2 text-xs shadow-lg backdrop-blur-sm">
      {label != null && (
        <div className="mb-1.5 text-[10px] font-medium uppercase tracking-wider text-slate-400">
          {label}
        </div>
      )}
      <ul className="space-y-1">
        {payload.map((p, i) => (
          <li
            key={`${p.dataKey ?? p.name ?? i}`}
            className="flex items-center gap-2"
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ backgroundColor: p.color }}
              aria-hidden
            />
            <span className="text-slate-600">{p.name}</span>
            <span className="ml-3 font-semibold tabular-nums text-slate-900">
              {formatter
                ? formatter(p.value)
                : typeof p.value === 'number'
                  ? p.value.toLocaleString()
                  : (p.value ?? '-')}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

type EmptyProps = {
  height?: number;
  message?: string;
  icon?: ReactNode;
};

export function ChartEmptyState({
  height = 280,
  message = '데이터가 없습니다',
  icon,
}: EmptyProps) {
  return (
    <div
      className="grid place-items-center rounded-lg border border-dashed border-slate-200 bg-slate-50/40 text-sm text-slate-400"
      style={{ height }}
    >
      <div className="flex flex-col items-center gap-1.5">
        {icon ?? <BarChart3 className="h-5 w-5 text-slate-300" aria-hidden />}
        <span>{message}</span>
      </div>
    </div>
  );
}
