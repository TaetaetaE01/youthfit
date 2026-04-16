import { Link } from 'react-router-dom';
import { Heart, MapPin, Calendar } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { Policy, PolicyCategory, PolicyStatus } from '@/types/policy';
import { CATEGORY_LABELS, STATUS_LABELS, getRegionName } from '@/types/policy';

interface PolicyCardProps {
  policy: Policy;
  isBookmarked?: boolean;
  onBookmarkToggle?: (policyId: number) => void;
  dDay?: number | null;
}

function CategoryBadge({ category }: { category: PolicyCategory }) {
  return (
    <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-indigo-600">
      {CATEGORY_LABELS[category]}
    </span>
  );
}

function StatusBadge({ status }: { status: PolicyStatus }) {
  const styles: Record<PolicyStatus, string> = {
    OPEN: 'bg-success-100 text-success-500',
    UPCOMING: 'bg-gray-100 text-gray-500',
    CLOSED: 'bg-gray-100 text-gray-400',
  };
  return (
    <span className={cn('rounded-full px-2.5 py-0.5 text-xs font-semibold', styles[status])}>
      {STATUS_LABELS[status]}
    </span>
  );
}

function formatDateRange(start: string | null, end: string | null) {
  if (!start && !end) return '상시';
  const fmt = (d: string) => d.slice(0, 10).replace(/-/g, '.');
  if (!start) return `~${fmt(end!)}`;
  if (!end) return `${fmt(start)}~`;
  return `${fmt(start)}~${fmt(end)}`;
}

export { CategoryBadge, StatusBadge };

export default function PolicyCard({ policy, isBookmarked = false, onBookmarkToggle, dDay }: PolicyCardProps) {
  return (
    <article className="group relative rounded-2xl border border-gray-100 bg-white p-6 shadow-card transition-all duration-200 hover:-translate-y-0.5 hover:shadow-card-hover md:p-6">
      {/* 상단: 배지 + 북마크 */}
      <div className="mb-3 flex items-center gap-2">
        <CategoryBadge category={policy.category} />
        <StatusBadge status={policy.status} />
        {dDay != null && dDay <= 7 && dDay >= 0 && (
          <span className="rounded-full bg-warning-500 px-2 py-0.5 text-xs font-bold text-white">
            D-{dDay}
          </span>
        )}
        {onBookmarkToggle && (
          <button
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onBookmarkToggle(policy.id);
            }}
            className="ml-auto flex h-8 w-8 items-center justify-center rounded-full transition-colors hover:bg-gray-50"
            aria-label={isBookmarked ? '북마크 해제' : '북마크 추가'}
            aria-pressed={isBookmarked}
          >
            <Heart
              className={cn(
                'h-5 w-5 transition-colors',
                isBookmarked ? 'fill-error-500 text-error-500' : 'text-gray-300',
              )}
            />
          </button>
        )}
      </div>

      {/* 제목 */}
      <Link to={`/policies/${policy.id}`} className="block">
        <h3 className="text-lg font-semibold leading-snug text-gray-900 transition-colors group-hover:text-brand-800 md:text-xl">
          {policy.title}
        </h3>
      </Link>

      {/* 요약 */}
      <p className="mt-2 line-clamp-2 text-sm leading-relaxed text-gray-500">
        {policy.summary}
      </p>

      {/* 메타 */}
      <div className="mt-4 flex items-center gap-3 text-xs text-gray-400">
        <span className="flex items-center gap-1">
          <MapPin className="h-3.5 w-3.5" />
          {getRegionName(policy.regionCode)}
        </span>
        <span className="text-gray-200">|</span>
        <span className="flex items-center gap-1">
          <Calendar className="h-3.5 w-3.5" />
          {formatDateRange(policy.applyStart, policy.applyEnd)}
        </span>
      </div>
    </article>
  );
}
