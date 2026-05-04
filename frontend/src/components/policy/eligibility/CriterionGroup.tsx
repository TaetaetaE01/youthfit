import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { CriterionItem, EligibilityResult } from '@/types/policy';
import { RESULT_CONFIG } from './eligibilityStyles';
import CriterionRow from './CriterionRow';

interface Props {
  variant: EligibilityResult;
  items: CriterionItem[];
  defaultCollapsed?: boolean;
}

export default function CriterionGroup({ variant, items, defaultCollapsed = false }: Props) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  if (items.length === 0) return null;

  const cfg = RESULT_CONFIG[variant];
  const Icon = cfg.icon;

  return (
    <div className="mb-5">
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="mb-2 flex w-full items-center justify-between text-left"
        aria-expanded={!collapsed}
      >
        <div className="flex items-center gap-2">
          <Icon className={cn('h-4 w-4', cfg.color)} />
          <span className="text-sm font-semibold text-neutral-800">
            {cfg.groupLabel} · {items.length}
          </span>
        </div>
        <ChevronDown
          className={cn(
            'h-4 w-4 text-neutral-400 transition-transform',
            collapsed ? '' : 'rotate-180',
          )}
        />
      </button>
      {!collapsed && (
        <ul className="space-y-2">
          {items.map((item, idx) => (
            <CriterionRow key={`${item.field}-${idx}`} item={item} />
          ))}
        </ul>
      )}
    </div>
  );
}
