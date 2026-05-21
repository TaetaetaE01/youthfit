import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';
import type { BarSegment } from '@/lib/calendarLayout';

const CATEGORY_TONE: Record<string, string> = {
  HOUSING: 'bg-blue-100 text-blue-800 border-blue-300',
  JOB: 'bg-amber-100 text-amber-800 border-amber-300',
  EDUCATION: 'bg-green-100 text-green-800 border-green-300',
  WELFARE: 'bg-violet-100 text-violet-800 border-violet-300',
  OTHER: 'bg-gray-100 text-gray-700 border-gray-300',
};

type Props = {
  segment: BarSegment;
  showLabel: boolean;
  daysUntilEnd: number | null;
};

export default function CalendarBar({ segment, showLabel, daysUntilEnd }: Props) {
  const tone = CATEGORY_TONE[segment.category] ?? CATEGORY_TONE.OTHER;
  const urgent = daysUntilEnd !== null && daysUntilEnd <= 3 && daysUntilEnd >= 0;
  const widthCols = segment.endCol - segment.startCol + 1;

  return (
    <Link
      to={`/policies/${segment.itemId}`}
      className={cn(
        'flex items-center gap-1 border px-2 py-0.5 text-xs font-semibold truncate',
        tone,
        segment.hasStartCap ? 'rounded-l-md' : 'rounded-l-none border-l-0',
        segment.hasEndCap ? 'rounded-r-md' : 'rounded-r-none border-r-0',
        urgent && 'ring-2 ring-red-500',
      )}
      style={{
        gridColumn: `${segment.startCol + 1} / span ${widthCols}`,
      }}
      title={`${segment.title} · ${segment.applyStart ?? '?'}~${segment.applyEnd ?? '?'}${
        daysUntilEnd !== null ? ` · 마감 D-${daysUntilEnd}` : ''
      }`}
    >
      {showLabel && (
        <>
          <span className="truncate">{segment.title}</span>
          {urgent && <span className="ml-auto shrink-0">D-{daysUntilEnd}</span>}
        </>
      )}
    </Link>
  );
}
