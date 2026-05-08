import { useEffect, useState } from 'react';
import { Calendar, Download } from 'lucide-react';
import {
  AdminSearchInput,
  AdminSelect,
} from '@/components/admin/AdminControls';
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

const RESULT_OPTIONS = [
  { value: 'HIT' as const, label: 'HIT' },
  { value: 'BELOW_THRESHOLD' as const, label: 'BELOW' },
  { value: 'MISS' as const, label: 'MISS' },
];

export function QnaCacheFilterBar(p: Props) {
  const [policyIdInput, setPolicyIdInput] = useState(
    p.policyId != null ? String(p.policyId) : '',
  );

  useEffect(() => {
    setPolicyIdInput(p.policyId != null ? String(p.policyId) : '');
  }, [p.policyId]);

  const commitPolicyId = () => {
    const next = policyIdInput ? Number(policyIdInput) : undefined;
    if (next !== p.policyId) {
      p.onChange({ policyId: next });
    }
  };

  return (
    <section
      aria-label="Q&A 캐시 필터"
      className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-card"
    >
      <div className="flex flex-wrap items-center gap-3">
        <AdminSelect
          ariaLabel="결과"
          value={p.result ?? ''}
          onChange={(v) =>
            p.onChange({ result: (v || undefined) as LookupResultType | undefined })
          }
          options={RESULT_OPTIONS}
          placeholder="전체 결과"
        />

        <AdminSearchInput
          type="number"
          aria-label="정책 ID"
          placeholder="정책 ID"
          value={policyIdInput}
          onChange={(e) => setPolicyIdInput(e.target.value)}
          onBlur={commitPolicyId}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commitPolicyId();
          }}
          containerClassName="w-32"
          className="w-full"
        />

        <DateRangeInput
          label="시작"
          value={p.from ?? ''}
          onChange={(v) => p.onChange({ from: v || undefined })}
        />
        <DateRangeInput
          label="종료"
          value={p.to ?? ''}
          onChange={(v) => p.onChange({ to: v || undefined })}
        />

        <button
          type="button"
          onClick={p.onExportCsv}
          className="ml-auto inline-flex h-9 items-center gap-1.5 rounded-lg bg-slate-900 px-3.5 text-xs font-semibold text-white transition-colors hover:bg-slate-800"
        >
          <Download className="h-3.5 w-3.5" aria-hidden />
          미스 CSV export
        </button>
      </div>
    </section>
  );
}

function DateRangeInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <label className="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 text-xs text-slate-600 transition-colors hover:border-slate-300 focus-within:border-brand-700 focus-within:ring-2 focus-within:ring-brand-700/10">
      <Calendar className="h-3.5 w-3.5 text-slate-400" aria-hidden />
      <span className="font-medium text-slate-500">{label}</span>
      <input
        type="datetime-local"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="bg-transparent text-xs text-slate-700 focus:outline-none"
      />
    </label>
  );
}
