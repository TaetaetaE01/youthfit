import type { DashboardAreaStatus } from '@/types/adminDashboard';
import AreaStatusCard from './AreaStatusCard';

export default function AreaStatusGrid({ areas }: { areas: DashboardAreaStatus[] }) {
  return (
    <section>
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">영역별 상태 ({areas.length})</h2>
        <span className="text-xs text-slate-500">최근 7일</span>
      </header>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {areas.map((a) => (
          <AreaStatusCard key={a.key} area={a} />
        ))}
      </div>
    </section>
  );
}
