import { useState } from 'react';

interface Props {
  open: boolean;
  policyTitle: string;
  onClose: () => void;
  onConfirm: (reason: string) => void;
  isSubmitting?: boolean;
}

export function ReprocessConfirmDialog({ open, policyTitle, onClose, onConfirm, isSubmitting }: Props) {
  const [reason, setReason] = useState('');

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className="bg-white border border-neutral-200 rounded-2xl shadow-xl p-6 w-96"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-base font-semibold mb-2 text-neutral-900">전체 재처리</h3>
        <p className="text-xs text-neutral-500 mb-4">
          "{policyTitle}" 의 ENRICHMENT/GUIDE/RULE/RAG 단계가 모두 다시 큐잉됩니다. 사유를 입력해 주세요.
        </p>
        <textarea
          className="w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm text-neutral-900 placeholder:text-neutral-500 mb-4 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15"
          rows={3}
          placeholder="예: LLM 모델 업데이트"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <div className="flex gap-2 justify-end">
          <button
            onClick={onClose}
            className="text-xs rounded-md border border-neutral-200 bg-white px-3 py-1.5 text-neutral-700 hover:bg-neutral-50"
          >
            취소
          </button>
          <button
            disabled={!reason.trim() || isSubmitting}
            onClick={() => onConfirm(reason)}
            className="text-xs rounded-md bg-[#1E2A78] hover:bg-[#1B2559] text-white px-3 py-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? '처리 중…' : '재처리 실행'}
          </button>
        </div>
      </div>
    </div>
  );
}
