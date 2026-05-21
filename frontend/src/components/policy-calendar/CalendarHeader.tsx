import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/cn';

type Props = {
  month: string;
  todayMonth: string;
  onPrev: () => void;
  onNext: () => void;
  onToday: () => void;
};

export default function CalendarHeader({ month, todayMonth, onPrev, onNext, onToday }: Props) {
  const [y, m] = month.split('-').map(Number);
  const isToday = month === todayMonth;

  return (
    <header className="mb-4 flex items-center justify-between">
      <div className="flex items-center gap-2">
        <button
          onClick={onPrev}
          aria-label="이전 달"
          className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-neutral-100"
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
        <h1 className="text-lg font-bold text-neutral-900">
          {y}년 {m}월
        </h1>
        <button
          onClick={onNext}
          aria-label="다음 달"
          className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-neutral-100"
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>
      <button
        onClick={onToday}
        disabled={isToday}
        className={cn(
          'rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold',
          isToday ? 'cursor-not-allowed text-neutral-400' : 'text-neutral-700 hover:bg-neutral-50',
        )}
      >
        오늘로
      </button>
    </header>
  );
}
