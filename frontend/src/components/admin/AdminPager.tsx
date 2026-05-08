import { ChevronLeft, ChevronRight } from 'lucide-react';

type Props = {
  page: number;
  totalPages: number;
  totalElements?: number;
  onChange: (page: number) => void;
  itemLabel?: string;
};

export default function AdminPager({
  page,
  totalPages,
  totalElements,
  onChange,
  itemLabel = '건',
}: Props) {
  const safeTotalPages = Math.max(1, totalPages);
  const isFirst = page <= 0;
  const isLast = page + 1 >= safeTotalPages;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
      <span className="text-slate-500">
        {totalElements != null && (
          <>
            총{' '}
            <strong className="font-semibold tabular-nums text-slate-700">
              {totalElements.toLocaleString()}
            </strong>
            {itemLabel}
            <span className="px-2 text-slate-300">·</span>
          </>
        )}
        <span className="tabular-nums">
          {page + 1} / {safeTotalPages}
        </span>{' '}
        페이지
      </span>

      <div className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
        <button
          type="button"
          disabled={isFirst}
          onClick={() => onChange(page - 1)}
          className="inline-flex h-8 items-center gap-1 rounded-md px-2.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
          aria-label="이전 페이지"
        >
          <ChevronLeft className="h-3.5 w-3.5" aria-hidden />
          이전
        </button>
        <div className="h-4 w-px bg-slate-200" aria-hidden />
        <button
          type="button"
          disabled={isLast}
          onClick={() => onChange(page + 1)}
          className="inline-flex h-8 items-center gap-1 rounded-md px-2.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
          aria-label="다음 페이지"
        >
          다음
          <ChevronRight className="h-3.5 w-3.5" aria-hidden />
        </button>
      </div>
    </div>
  );
}
