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
  const isLong = preview.length > 120;
  const truncated = isLong ? preview.slice(0, 120) + '…' : preview;

  return (
    <div
      onClick={() => setExpanded((v) => !v)}
      className={cn(
        'rounded-md border border-neutral-200 bg-white px-3 py-2 mb-2 text-sm',
        'cursor-pointer hover:bg-neutral-50 transition-colors',
      )}
    >
      <div className="flex items-center gap-2">
        <span className="bg-neutral-100 px-1.5 py-0.5 rounded text-xs font-semibold">
          {rank}
        </span>
        <span className="text-neutral-700">chunk#{chunkId}</span>
        {distance !== undefined && (
          <span className="text-xs text-neutral-500 font-mono">d={distance.toFixed(3)}</span>
        )}
        {rrfScore !== undefined && rrfScore > 0 && (
          <span className="text-xs text-neutral-500 font-mono">rrf={rrfScore.toFixed(4)}</span>
        )}
        {delta && (
          <span className="ml-auto">
            <RankDeltaBadge delta={delta} />
          </span>
        )}
      </div>
      <div
        className={cn(
          'mt-1.5 text-sm text-neutral-700 whitespace-pre-wrap leading-relaxed',
          expanded ? '' : 'line-clamp-3',
        )}
      >
        {expanded ? preview : truncated}
      </div>
      {isLong && (
        <div className="mt-1 text-xs text-neutral-400">
          {expanded ? '< 접기' : '> 더 보기'}
        </div>
      )}
    </div>
  );
}
