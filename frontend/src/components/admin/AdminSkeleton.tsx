import { cn } from '@/lib/cn';

type SkeletonProps = {
  className?: string;
  rounded?: 'sm' | 'md' | 'lg' | 'full';
};

const ROUND: Record<NonNullable<SkeletonProps['rounded']>, string> = {
  sm: 'rounded',
  md: 'rounded-md',
  lg: 'rounded-lg',
  full: 'rounded-full',
};

export function Skeleton({ className, rounded = 'md' }: SkeletonProps) {
  return (
    <div
      className={cn(
        'animate-pulse bg-gradient-to-r from-slate-100 via-slate-200/70 to-slate-100',
        ROUND[rounded],
        className,
      )}
      aria-hidden
    />
  );
}

type CardSkeletonProps = {
  rows?: number;
  className?: string;
};

export function AdminCardSkeleton({ rows = 3, className }: CardSkeletonProps) {
  return (
    <div
      className={cn(
        'space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-card',
        className,
      )}
      aria-busy="true"
      aria-label="로딩 중"
    >
      <Skeleton className="h-3 w-24" />
      <Skeleton className="h-7 w-32" />
      <div className="space-y-2 pt-2">
        {Array.from({ length: rows }).map((_, i) => (
          <Skeleton key={i} className={cn('h-3', i === rows - 1 ? 'w-2/3' : 'w-full')} />
        ))}
      </div>
    </div>
  );
}

export function AdminKpiSkeletonGrid({ count = 4 }: { count?: number }) {
  return (
    <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <AdminCardSkeleton key={i} rows={2} />
      ))}
    </section>
  );
}
