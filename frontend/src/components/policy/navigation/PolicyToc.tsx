import { List } from 'lucide-react';
import { cn } from '@/lib/cn';
import { POLICY_GROUPS } from '@/components/policy/groups/policyGroups';

interface Props {
  activeId: string;
}

export function PolicyToc({ activeId }: Props) {
  return (
    <nav className="rounded-2xl border border-neutral-200 bg-white p-5 shadow-card">
      <div className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-neutral-500">
        <List className="h-3.5 w-3.5" />
        <span>목차</span>
      </div>
      <ul>
        {POLICY_GROUPS.map((g) => {
          const isActive = g.id === activeId;
          const { Icon } = g;
          return (
            <li key={g.id}>
              <a
                href={`#${g.id}`}
                aria-current={isActive ? 'location' : undefined}
                className={cn(
                  'my-0.5 flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors',
                  'border-l-[3px] border-transparent',
                  isActive
                    ? 'border-brand-800 bg-brand-50 font-semibold text-brand-800'
                    : 'text-neutral-600 hover:bg-neutral-50 hover:text-neutral-900',
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span>{g.label}</span>
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
