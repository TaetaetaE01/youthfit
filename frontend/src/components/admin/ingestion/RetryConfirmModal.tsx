interface Props {
  open: boolean;
  failureId: number | null;
  onClose: () => void;
  onConfirm: () => void;
  loading?: boolean;
}

export function RetryConfirmModal({ open, failureId, onClose, onConfirm, loading }: Props) {
  if (!open || failureId == null) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-base font-semibold">실패 항목 재처리</h3>
        <p className="mt-2 text-sm text-slate-600">
          실패 항목 #{failureId} 를 다시 처리합니다. raw_payload 가 살아있어야 가능하며, 결과는 새 RunLog 로 적재됩니다.
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="rounded border border-slate-200 px-3 py-1.5 text-sm"
          >
            취소
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-60"
          >
            {loading ? '처리 중…' : '재처리'}
          </button>
        </div>
      </div>
    </div>
  );
}
