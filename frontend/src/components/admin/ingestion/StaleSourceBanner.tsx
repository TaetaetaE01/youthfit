import { AlertTriangle } from 'lucide-react';
import type { IngestionStaleSourceResponse } from '@/apis/admin.ingestion.api';

interface Props {
  stale: IngestionStaleSourceResponse[];
}

export function StaleSourceBanner({ stale }: Props) {
  if (stale.length === 0) return null;
  return (
    <div
      role="alert"
      className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50/70 px-4 py-3 text-sm text-amber-900"
    >
      <div className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-amber-100 text-amber-700">
        <AlertTriangle className="h-4 w-4" aria-hidden />
      </div>
      <div className="min-w-0 flex-1">
        <div className="font-semibold text-amber-900">
          마지막 24시간 동안 수신 없는 source ({stale.length})
        </div>
        <ul className="mt-1.5 space-y-0.5 text-xs text-amber-800/90">
          {stale.map((s) => (
            <li key={s.source} className="truncate">
              <span className="font-mono">{s.source}</span>
              <span className="px-1.5 text-amber-600/60">·</span>
              {s.hoursSinceLastReceived}시간 전
              <span className="px-1.5 text-amber-600/60">·</span>
              {new Date(s.lastReceivedAt).toLocaleString('ko-KR')}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
