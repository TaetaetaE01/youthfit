import { Link } from 'react-router-dom';
import { useAlwaysOpenPolicies } from '@/hooks/queries/useAlwaysOpenPolicies';

type Props = {
  regions: string[];
  category: string;
};

export default function AlwaysOpenSection({ regions, category }: Props) {
  const { data, isLoading } = useAlwaysOpenPolicies({
    regions,
    category: category || undefined,
    page: 0,
    size: 5,
  });

  if (isLoading) {
    return (
      <section className="mt-6">
        <div className="h-20 animate-pulse rounded-xl bg-neutral-100" />
      </section>
    );
  }

  if (!data || data.totalCount === 0) return null;

  const params = new URLSearchParams();
  if (regions.length > 0) params.set('regions', regions.join(','));
  if (category) params.set('category', category);
  params.set('alwaysOpen', '1');
  const moreHref = `/policies?${params.toString()}`;

  return (
    <section className="mt-6 rounded-2xl border border-neutral-200 bg-white p-4">
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-bold text-neutral-900">
          상시 모집 정책 · {data.totalCount}건
        </h2>
        <Link to={moreHref} className="text-xs font-semibold text-brand-800 hover:underline">
          모두 보기 →
        </Link>
      </header>
      <div className="flex flex-wrap gap-2">
        {data.content.map((it) => {
          const periodLabel = it.deadlineYear
            ? `${it.deadlineYear % 100}년 상시`
            : '상시';
          return (
            <Link
              key={it.id}
              to={`/policies/${it.id}`}
              className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1.5 text-xs font-medium text-neutral-700 hover:bg-neutral-100"
            >
              <span className="rounded-full bg-brand-100 px-1.5 py-0.5 text-[10px] font-semibold text-brand-800">
                {periodLabel}
              </span>
              <span>{it.title}</span>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
