import type { ReactNode } from 'react';
import { BarChart3, PieChart, Table2 } from 'lucide-react';

type CardShellProps = {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function AdminCardShell({ title, subtitle, action, children, className }: CardShellProps) {
  return (
    <section
      className={`rounded-xl border border-slate-200 bg-white shadow-card ${className ?? ''}`}
    >
      <header className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="text-base font-semibold text-slate-900">{title}</h2>
          {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
        </div>
        {action}
      </header>
      <div className="p-5">{children}</div>
    </section>
  );
}

export function ChartPlaceholder({ height = 240, label }: { height?: number; label: string }) {
  // 막대 7개 — 간단한 시각적 자리잡기. 실제 데이터는 후속 spec.
  const bars = [40, 65, 35, 80, 55, 90, 60];

  return (
    <div className="relative">
      <div
        className="flex items-end gap-3 rounded-lg bg-slate-50/60 p-4"
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
        <div className="flex items-center gap-2 rounded-lg border border-dashed border-slate-300 bg-white/90 px-4 py-2 text-xs text-slate-500">
          <BarChart3 className="h-4 w-4" aria-hidden />
          데이터 연결 대기 — 후속 spec
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
            strokeWidth="3"
          />
          <circle
            cx="18"
            cy="18"
            r="15.9155"
            className="fill-none stroke-slate-300"
            strokeWidth="3"
            strokeDasharray="0 100"
          />
        </svg>
        <div className="absolute text-center">
          <div className="text-xl font-bold text-slate-300 tabular-nums">--%</div>
          <div className="text-[10px] text-slate-400">대기</div>
        </div>
      </div>
      <div className="flex items-center gap-2 text-xs text-slate-500">
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
          <tr className="border-b border-slate-100 text-left text-xs text-slate-400">
            {columns.map((c) => (
              <th key={c} className="px-3 py-2 font-medium uppercase tracking-wide">
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
                  <div className="h-2.5 rounded bg-slate-100" />
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
