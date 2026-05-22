import { useState } from 'react';

interface Props {
  onSubmit: (policyId: number, query: string) => void;
  isPending?: boolean;
}

export function RagPreviewControls({ onSubmit, isPending }: Props) {
  const [policyId, setPolicyId] = useState('');
  const [query, setQuery] = useState('');
  const canSubmit = policyId && query.trim() && !isPending;
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) onSubmit(Number(policyId), query.trim());
      }}
      className="flex flex-wrap items-end gap-3"
    >
      <div className="flex flex-col text-sm">
        <label htmlFor="rag-policy-id" className="text-neutral-500">정책 ID</label>
        <input
          id="rag-policy-id"
          type="number"
          aria-label="정책 ID"
          value={policyId}
          onChange={(e) => setPolicyId(e.target.value)}
          className="w-32 rounded border px-2 py-1"
        />
      </div>
      <div className="flex flex-1 flex-col text-sm">
        <label htmlFor="rag-query" className="text-neutral-500">쿼리</label>
        <input
          id="rag-query"
          type="text"
          aria-label="쿼리"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          maxLength={500}
          className="rounded border px-2 py-1"
        />
      </div>
      <button
        type="submit"
        disabled={!canSubmit}
        className="rounded bg-brand-800 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {isPending ? '실행 중…' : '▶ 비교 실행'}
      </button>
    </form>
  );
}
