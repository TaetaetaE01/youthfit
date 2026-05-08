import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

export type AdminCrumb = {
  label: string;
  to?: string;
};

type Props = {
  title: string;
  description?: string;
  breadcrumb?: AdminCrumb[];
  status?: ReactNode;
  actions?: ReactNode;
};

export default function AdminPageHeader({
  title,
  description,
  breadcrumb,
  status,
  actions,
}: Props) {
  return (
    <header className="space-y-2 pb-1">
      {breadcrumb && breadcrumb.length > 0 && (
        <nav
          aria-label="breadcrumb"
          className="flex items-center gap-1 text-xs text-slate-400"
        >
          {breadcrumb.map((c, i) => {
            const isLast = i === breadcrumb.length - 1;
            return (
              <span key={`${c.label}-${i}`} className="inline-flex items-center gap-1">
                {c.to && !isLast ? (
                  <Link
                    to={c.to}
                    className="rounded-sm transition-colors hover:text-brand-700 focus-visible:text-brand-700 focus-visible:outline-none"
                  >
                    {c.label}
                  </Link>
                ) : (
                  <span className={isLast ? 'font-medium text-slate-600' : ''}>
                    {c.label}
                  </span>
                )}
                {!isLast && (
                  <ChevronRight className="h-3 w-3 text-slate-300" aria-hidden />
                )}
              </span>
            );
          })}
        </nav>
      )}

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="truncate text-[22px] font-bold tracking-tight text-slate-900 sm:text-2xl">
            {title}
          </h1>
          {description && (
            <p className="mt-1 text-sm leading-relaxed text-slate-500">
              {description}
            </p>
          )}
        </div>

        {(status || actions) && (
          <div className="flex shrink-0 items-center gap-2">
            {status}
            {actions}
          </div>
        )}
      </div>
    </header>
  );
}
