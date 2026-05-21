import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import type { PolicyCalendarItem } from '@/types/policy';

type Props = {
  day: string;
  items: PolicyCalendarItem[];
  onClose: () => void;
};

export default function CalendarDayPopover({ day, items, onClose }: Props) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
      role="dialog"
      aria-label={`${day} 정책 목록`}
    >
      <div
        className="w-full max-w-md max-h-[80vh] overflow-y-auto rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-3">
          <h2 className="text-sm font-bold text-neutral-900">
            {Number(day.slice(5, 7))}월 {Number(day.slice(8, 10))}일 정책 ({items.length}건)
          </h2>
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full hover:bg-neutral-100"
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <ul className="flex flex-col">
          {items.map((it) => (
            <li key={it.id} className="border-b border-neutral-100 last:border-b-0">
              <Link
                to={`/policies/${it.id}`}
                className="block px-5 py-3 text-sm hover:bg-neutral-50"
              >
                <div className="font-medium text-neutral-900">{it.title}</div>
                <div className="mt-0.5 text-xs text-neutral-500">
                  {it.applyStart ?? '?'} ~ {it.applyEnd ?? '?'} · {it.regionLabel}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
