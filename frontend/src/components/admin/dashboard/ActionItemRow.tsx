import { Link } from 'react-router-dom';
import type { DashboardActionItem } from '@/types/adminDashboard';

export default function ActionItemRow({ item }: { item: DashboardActionItem }) {
  const dotClass = item.severity === 'HIGH' ? 'bg-error-500' : 'bg-amber-500';
  return (
    <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 py-3 last:border-b-0">
      <div className="flex items-start gap-3">
        <span
          data-severity={item.severity}
          className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${dotClass}`}
          aria-hidden
        />
        <div>
          <p className="text-sm font-semibold text-slate-900">{item.title}</p>
          {item.detail && <p className="mt-0.5 text-xs text-slate-500">{item.detail}</p>}
        </div>
      </div>
      <Link
        to={item.deeplink}
        className="shrink-0 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
      >
        확인
      </Link>
    </div>
  );
}
