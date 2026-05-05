import { useState } from 'react';
import type { LookupResultType } from '@/apis/admin.qnaCache.api';

type Props = {
  result?: LookupResultType;
  policyId?: number;
  from?: string;
  to?: string;
  onChange: (patch: {
    result?: LookupResultType;
    policyId?: number;
    from?: string;
    to?: string;
  }) => void;
  onExportCsv: () => void;
};

export function QnaCacheFilterBar(p: Props) {
  const [policyIdInput, setPolicyIdInput] = useState(
    p.policyId != null ? String(p.policyId) : '',
  );

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg bg-white p-4 shadow-sm">
      <select
        className="rounded-md border border-slate-300 px-2 py-1 text-sm"
        value={p.result ?? ''}
        onChange={(e) =>
          p.onChange({ result: (e.target.value || undefined) as LookupResultType | undefined })
        }
      >
        <option value="">전체 결과</option>
        <option value="HIT">HIT</option>
        <option value="BELOW_THRESHOLD">BELOW</option>
        <option value="MISS">MISS</option>
      </select>

      <input
        className="w-28 rounded-md border border-slate-300 px-2 py-1 text-sm"
        placeholder="정책 ID"
        type="number"
        value={policyIdInput}
        onChange={(e) => setPolicyIdInput(e.target.value)}
        onBlur={() =>
          p.onChange({
            policyId: policyIdInput ? Number(policyIdInput) : undefined,
          })
        }
      />

      <div className="flex items-center gap-1 text-sm text-slate-600">
        <span>from</span>
        <input
          className="rounded-md border border-slate-300 px-2 py-1 text-sm"
          type="datetime-local"
          value={p.from ?? ''}
          onChange={(e) => p.onChange({ from: e.target.value || undefined })}
        />
      </div>

      <div className="flex items-center gap-1 text-sm text-slate-600">
        <span>to</span>
        <input
          className="rounded-md border border-slate-300 px-2 py-1 text-sm"
          type="datetime-local"
          value={p.to ?? ''}
          onChange={(e) => p.onChange({ to: e.target.value || undefined })}
        />
      </div>

      <button
        onClick={p.onExportCsv}
        className="ml-auto rounded-md border border-slate-300 bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-700"
      >
        미스 CSV export
      </button>
    </div>
  );
}
