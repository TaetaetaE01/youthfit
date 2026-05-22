import { Link } from 'react-router-dom';
import type { DashboardAreaStatus } from '@/types/adminDashboard';
import StatusBadge from './StatusBadge';
import Sparkline from './Sparkline';

export default function AreaStatusCard({ area }: { area: DashboardAreaStatus }) {
  return (
    <Link
      to={area.deeplink}
      className="flex flex-col gap-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:shadow-md"
    >
      <div className="flex items-start justify-between">
        <h3 className="text-sm font-semibold text-slate-900">{area.label}</h3>
        <StatusBadge status={area.status} />
      </div>
      <div className="flex items-end justify-between">
        <p className="text-sm text-slate-600">{area.summary}</p>
        <Sparkline values={area.sparkline} />
      </div>
    </Link>
  );
}
