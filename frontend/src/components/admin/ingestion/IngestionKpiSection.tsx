import { KpiCard } from '@/components/charts/KpiCard';
import type { IngestionKpiResponse } from '@/apis/admin.ingestion.api';

interface Props {
  kpi: IngestionKpiResponse | undefined;
}

export function IngestionKpiSection({ kpi }: Props) {
  if (!kpi) return null;
  const failurePct = (kpi.sevenDayFailureRate * 100).toFixed(2);
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
      <KpiCard label="어제 신규 수신" value={kpi.yesterdayReceived.toLocaleString()} />
      <KpiCard label="어제 실패" value={kpi.yesterdayFailure.toLocaleString()} />
      <KpiCard label="7일 평균 신규/일" value={Number(kpi.sevenDayAvgReceivedPerDay).toFixed(1)} />
      <KpiCard label="7일 실패율" value={`${failurePct}%`} />
    </div>
  );
}
