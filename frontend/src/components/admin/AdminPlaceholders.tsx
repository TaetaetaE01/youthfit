import type { ReactNode } from 'react';
import { BarChart3, PieChart, Table2 } from 'lucide-react';
import { cn } from '@/lib/cn';

type CardShellProps = {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  headless?: boolean;
};

export function AdminCardShell({
  title,
  subtitle,
  action,
  children,
  className,
  bodyClassName,
  headless = false,
}: CardShellProps) {
  return (
    <section
      className={cn(
        'rounded-xl border border-slate-200/80 bg-white shadow-card transition-shadow hover:shadow-card-hover',
        className,
      )}
    >
      {!headless && (
        <header className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div className="min-w-0">
            <h2 className="text-[15px] font-semibold tracking-tight text-slate-900">
              {title}
            </h2>
            {subtitle && (
              <p className="mt-0.5 text-xs leading-relaxed text-slate-500">
                {subtitle}
              </p>
            )}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </header>
      )}
      <div className={cn('p-5', bodyClassName)}>{children}</div>
    </section>
  );
}

export function ChartPlaceholder({ height = 240, label }: { height?: number; label: string }) {
  const bars = [40, 65, 35, 80, 55, 90, 60];

  return (
    <div className="relative">
      <div
        className="flex items-end gap-3 rounded-lg bg-gradient-to-b from-slate-50/80 to-slate-100/50 p-4 ring-1 ring-inset ring-slate-100"
        style={{ height }}
        aria-label={label}
      >
        {bars.map((h, i) => (
          <div
            key={i}
            className="flex-1 rounded-t bg-gradient-to-t from-slate-200 to-slate-100"
            style={{ height: `${h}%` }}
          />
        ))}
      </div>
      <div className="pointer-events-none absolute inset-0 grid place-items-center">
        <div className="flex items-center gap-2 rounded-full border border-dashed border-slate-300 bg-white/95 px-3.5 py-1.5 text-xs text-slate-500 shadow-sm">
          <BarChart3 className="h-3.5 w-3.5" aria-hidden />
          데이터 연결 대기
        </div>
      </div>
    </div>
  );
}

export function DonutPlaceholder({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-4" aria-label={label}>
      <div className="relative grid h-36 w-36 place-items-center">
        <svg viewBox="0 0 36 36" className="h-full w-full -rotate-90">
          <circle
            cx="18"
            cy="18"
            r="15.9155"
            className="fill-none stroke-slate-100"
            strokeWidth="3.2"
          />
          <circle
            cx="18"
            cy="18"
            r="15.9155"
            className="fill-none stroke-slate-200"
            strokeWidth="3.2"
            strokeLinecap="round"
            strokeDasharray="0 100"
          />
        </svg>
        <div className="absolute text-center">
          <div className="text-2xl font-bold text-slate-300 tabular-nums">--%</div>
          <div className="text-[10px] uppercase tracking-wider text-slate-400">대기</div>
        </div>
      </div>
      <div className="flex items-center gap-2 rounded-full border border-dashed border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-500">
        <PieChart className="h-3.5 w-3.5" aria-hidden />
        {label}
      </div>
    </div>
  );
}

export function TablePlaceholder({ columns, label }: { columns: string[]; label: string }) {
  const rows = Array.from({ length: 4 });

  return (
    <div className="overflow-x-auto" aria-label={label}>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-100 text-left text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-400">
            {columns.map((c) => (
              <th key={c} className="px-3 py-2.5">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((_, i) => (
            <tr key={i} className="border-b border-slate-50 last:border-0">
              {columns.map((c) => (
                <td key={c} className="px-3 py-3">
                  <div className="h-2.5 rounded bg-gradient-to-r from-slate-100 via-slate-100/60 to-slate-100" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <div className="mt-3 flex items-center justify-center gap-2 text-xs text-slate-500">
        <Table2 className="h-3.5 w-3.5" aria-hidden />
        데이터 연결 대기
      </div>
    </div>
  );
}
