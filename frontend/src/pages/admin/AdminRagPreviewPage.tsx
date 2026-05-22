import { useState } from 'react';
import { useRagPreview } from '@/hooks/mutations/useRagPreview';
import type { HybridOverride, RankChange } from '@/types/ragPreview';
import { RagPreviewControls } from '@/components/admin/rag-preview/RagPreviewControls';
import { BaselineConfigPanel } from '@/components/admin/rag-preview/BaselineConfigPanel';
import { CandidateConfigForm } from '@/components/admin/rag-preview/CandidateConfigForm';
import { ResultTabs } from '@/components/admin/rag-preview/ResultTabs';

function deltaMap(changes: RankChange[]): Map<number, string> {
  const m = new Map<number, string>();
  for (const c of changes) m.set(c.chunkId, c.delta);
  return m;
}

export default function AdminRagPreviewPage() {
  const mutation = useRagPreview();
  const [candidate, setCandidate] = useState<HybridOverride>({});

  const onSubmit = (policyId: number, query: string) => {
    mutation.mutate({ policyId, query, candidate });
  };

  const data = mutation.data;
  const baselineConfig = data?.baseline.config;
  const deltas = data ? deltaMap(data.diff.rankChanges) : new Map<number, string>();

  return (
    <div className="hidden p-6 md:block">
      <h1 className="mb-4 text-xl font-semibold">RAG 검색 미리보기</h1>

      <RagPreviewControls onSubmit={onSubmit} isPending={mutation.isPending} />

      {mutation.isError && (
        <div className="mt-3 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {(mutation.error as Error).message}
        </div>
      )}

      {data && (
        <>
          <div className="mt-4 text-sm">
            <span className="text-neutral-500">추출 키워드: </span>
            {data.extractedKeywords.map((kw) => (
              <span key={kw} className="ml-1 rounded bg-neutral-100 px-1.5 py-0.5">{kw}</span>
            ))}
          </div>

          <div className="mt-4 grid grid-cols-2 gap-4">
            <section className="rounded border p-4">
              <h2 className="mb-2 font-medium">Baseline (yml) — {data.baseline.tookMs} ms</h2>
              <BaselineConfigPanel config={baselineConfig} />
              <div className="mt-4">
                <ResultTabs side={data.baseline} rankByChunkId={deltas} />
              </div>
            </section>

            <section className="rounded border p-4">
              <h2 className="mb-2 font-medium">Candidate — {data.candidate.tookMs} ms</h2>
              <CandidateConfigForm
                baseline={baselineConfig}
                onChange={setCandidate}
              />
              <div className="mt-4">
                <ResultTabs side={data.candidate} rankByChunkId={deltas} />
              </div>
            </section>
          </div>
        </>
      )}

      <div className="mt-8 text-xs text-neutral-400 md:hidden">
        데스크톱 화면에서 사용해주세요.
      </div>
    </div>
  );
}
