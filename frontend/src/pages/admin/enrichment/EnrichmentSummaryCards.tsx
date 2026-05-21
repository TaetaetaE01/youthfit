import { useSummary } from '@/hooks/useAdminEnrichment';
import type { EnrichmentStatus } from '@/types/adminEnrichment';

const FAILED_STATUSES: EnrichmentStatus[] = [
  'FETCH_FAILED',
  'LLM_FAILED',
  'PARSE_FAILED',
  'LOW_CONFIDENCE',
  'NO_LINK',
  'TOO_SHORT',
];

export function EnrichmentSummaryCards() {
  const { data } = useSummary();
  if (!data) return null;
  const failed = FAILED_STATUSES.reduce(
    (sum, k) => sum + (data.byStatus[k] ?? 0),
    0,
  );
  return (
    <div className="grid grid-cols-4 gap-3 mb-4">
      <Card label="전체" value={data.total} />
      <Card label="검토 필요" value={data.needsReview} />
      <Card label="실패" value={failed} />
      <Card label="LITE" value={data.byDetailLevel.LITE ?? 0} />
    </div>
  );
}

function Card({ label, value }: { label: string; value: number }) {
  return (
    <div className="p-3 border rounded">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-2xl font-semibold">{value}</p>
    </div>
  );
}
