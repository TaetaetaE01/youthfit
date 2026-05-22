import { Link } from 'react-router-dom';
import type { DashboardAreaStatus } from '@/types/adminDashboard';
import StatusBadge from './StatusBadge';
import Sparkline from './Sparkline';

export default function AreaStatusCard({ area }: { area: DashboardAreaStatus }) {
  return (
    <Link
      to={area.deeplink}
      aria-label={`${area.label} — ${area.summary}`}
      className="flex flex-col gap-2 rounded-xl border border-slate-200/80 bg-white p-4 shadow-card transition hover:shadow-card-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2"
    >
      <div className="flex items-start justify-between">
        <h3 className="text-sm font-semibold text-slate-900">{area.label}</h3>
        <StatusBadge status={area.status} />
      </div>
      <div className="flex items-end justify-between">
        <p className="text-sm text-slate-600">{area.summary}</p>
        <Sparkline values={area.sparkline} label="최근 7일 추세" />
      </div>
    </Link>
  );
}
