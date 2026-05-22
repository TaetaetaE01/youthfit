import type { DashboardActionItem } from '@/types/adminDashboard';
import ActionItemRow from './ActionItemRow';

export default function ActionQueueSection({ items }: { items: DashboardActionItem[] }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
      <header className="border-b border-slate-100 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-900">
          <span className="text-error-500" aria-hidden>⚠</span> Action Required ({items.length})
        </h2>
      </header>
      <div>{items.map((i) => <ActionItemRow key={i.code} item={i} />)}</div>
    </section>
  );
}
