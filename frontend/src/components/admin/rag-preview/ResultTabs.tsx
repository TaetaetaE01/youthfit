import { useState } from 'react';
import type { ChunkSummary, MergedChunk, PreviewSide } from '@/types/ragPreview';
import { ChunkRow } from './ChunkRow';

type Tab = 'merged' | 'vector' | 'trigram';

interface Props {
  side?: PreviewSide;
  rankByChunkId?: Map<number, string>;   // merged 탭에서 사용할 delta
}

export function ResultTabs({ side, rankByChunkId }: Props) {
  const [tab, setTab] = useState<Tab>('merged');

  if (!side) return <div className="text-sm text-neutral-400">결과 없음</div>;

  const items =
    tab === 'merged' ? side.merged
    : tab === 'vector' ? side.vectorTopN
    : side.trigramTopN;

  return (
    <div>
      <div className="mb-2 flex gap-1 border-b">
        {(['merged', 'vector', 'trigram'] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`px-3 py-1 text-sm ${tab === t ? 'border-b-2 border-brand-800 font-medium' : 'text-neutral-500'}`}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 'trigram' && !side.config.hybridEnabled && (
        <div className="text-sm text-neutral-400">hybrid 비활성 — trigram 검색이 실행되지 않음</div>
      )}
      {items.length === 0 ? (
        <div className="py-4 text-sm text-neutral-400">결과 없음</div>
      ) : (
        items.map((c: ChunkSummary | MergedChunk, idx: number) => {
          const isMerged = 'rank' in c;
          return (
            <ChunkRow
              key={c.chunkId}
              rank={isMerged ? (c as MergedChunk).rank : idx + 1}
              chunkId={c.chunkId}
              distance={c.distance}
              rrfScore={isMerged ? (c as MergedChunk).rrfScore : undefined}
              preview={c.preview}
              delta={tab === 'merged' ? rankByChunkId?.get(c.chunkId) : undefined}
            />
          );
        })
      )}
    </div>
  );
}
