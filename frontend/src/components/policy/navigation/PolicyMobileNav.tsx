import { useEffect, useRef } from 'react';
import { cn } from '@/lib/cn';
import { POLICY_GROUPS } from '@/components/policy/groups/policyGroups';

interface Props {
  activeId: string;
  visible: boolean;
}

export function PolicyMobileNav({ activeId, visible }: Props) {
  const activeChipRef = useRef<HTMLAnchorElement | null>(null);

  useEffect(() => {
    if (visible && activeChipRef.current) {
      activeChipRef.current.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
    }
  }, [activeId, visible]);

  return (
    <div
      className={cn(
        'sticky top-0 z-10 -mx-4 border-b border-neutral-200 bg-white/95 backdrop-blur lg:hidden',
        visible ? 'visible' : 'invisible',
      )}
    >
      <div className="flex gap-2 overflow-x-auto px-4 py-3 [&::-webkit-scrollbar]:hidden">
        {POLICY_GROUPS.map((g) => {
          const isActive = g.id === activeId;
          const { Icon } = g;
          return (
            <a
              key={g.id}
              ref={isActive ? activeChipRef : undefined}
              href={`#${g.id}`}
              aria-current={isActive ? 'location' : undefined}
              className={cn(
                'inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-3.5 py-2 text-sm transition-colors',
                isActive
                  ? 'border-brand-800 bg-brand-800 font-semibold text-white'
                  : 'border-neutral-200 bg-white font-medium text-neutral-600',
              )}
            >
              <Icon className="h-4 w-4" />
              <span>{g.label}</span>
            </a>
          );
        })}
      </div>
    </div>
  );
}
