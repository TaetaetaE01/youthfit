import { CATEGORY_LABELS, CATEGORY_OPTIONS } from '@/lib/labels/category';
import type { PolicyCategory } from '@/types/policy';
import { cn } from '@/lib/cn';

interface Props {
  selected: PolicyCategory[];
  onChange: (next: PolicyCategory[]) => void;
}

export default function InterestCategoryChips({ selected, onChange }: Props) {
  const toggle = (category: PolicyCategory) => {
    if (selected.includes(category)) {
      onChange(selected.filter((c) => c !== category));
    } else {
      onChange([...selected, category]);
    }
  };

  return (
    <div className="flex flex-wrap gap-2">
      {CATEGORY_OPTIONS.map((category) => {
        const active = selected.includes(category);
        return (
          <button
            key={category}
            type="button"
            onClick={() => toggle(category)}
            aria-pressed={active}
            className={cn(
              'h-11 min-w-[44px] rounded-full border px-4 text-sm font-medium transition-colors',
              active
                ? 'border-brand-800 bg-brand-100 text-brand-900'
                : 'border-neutral-200 bg-white text-neutral-700 hover:border-neutral-300',
            )}
          >
            {CATEGORY_LABELS[category]}
          </button>
        );
      })}
    </div>
  );
}
