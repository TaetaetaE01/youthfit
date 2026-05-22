import { useState } from 'react';
import { cn } from '@/lib/cn';
import { RankDeltaBadge } from './RankDeltaBadge';

interface Props {
  rank: number;
  chunkId: number;
  distance?: number;
  rrfScore?: number;
  preview: string;
  delta?: string;
}

export function ChunkRow({ rank, chunkId, distance, rrfScore, preview, delta }: Props) {
  const [expanded, setExpanded] = useState(false);
  const truncated = preview.length > 120 ? preview.slice(0, 120) + '…' : preview;
  return (
    <div className="border-b border-neutral-100 py-2 text-sm">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-medium text-neutral-700">{rank}.</span>
        <span className="text-neutral-500">chunk#{chunkId}</span>
        {distance !== undefined && (
          <span className="text-xs text-neutral-400">d={distance.toFixed(3)}</span>
        )}
        {rrfScore !== undefined && rrfScore > 0 && (
          <span className="text-xs text-neutral-400">rrf={rrfScore.toFixed(4)}</span>
        )}
        {delta && <RankDeltaBadge delta={delta} />}
      </div>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className={cn('text-left text-neutral-700', expanded ? '' : 'line-clamp-3')}
      >
        {expanded ? preview : truncated}
      </button>
    </div>
  );
}
