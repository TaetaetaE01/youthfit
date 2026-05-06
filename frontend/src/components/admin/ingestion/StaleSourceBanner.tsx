import type { IngestionStaleSourceResponse } from '@/apis/admin.ingestion.api';

interface Props {
  stale: IngestionStaleSourceResponse[];
}

export function StaleSourceBanner({ stale }: Props) {
  if (stale.length === 0) return null;
  return (
    <div className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
      <strong>마지막 24시간 동안 수신 없는 source:</strong>
      <ul className="mt-1 list-inside list-disc">
        {stale.map((s) => (
          <li key={s.source}>
            <span className="font-mono">{s.source}</span>
            {' — '}
            {s.hoursSinceLastReceived}시간 전 ({new Date(s.lastReceivedAt).toLocaleString('ko-KR')})
          </li>
        ))}
      </ul>
    </div>
  );
}
