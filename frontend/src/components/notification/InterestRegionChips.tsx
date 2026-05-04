import { REGION_LABELS, REGION_SIDO_OPTIONS, type RegionSidoCode } from '@/lib/labels/region';
import { cn } from '@/lib/cn';

interface Props {
  selected: RegionSidoCode[];
  onChange: (next: RegionSidoCode[]) => void;
}

export default function InterestRegionChips({ selected, onChange }: Props) {
  const toggle = (region: RegionSidoCode) => {
    if (selected.includes(region)) {
      onChange(selected.filter((r) => r !== region));
    } else {
      onChange([...selected, region]);
    }
  };

  return (
    <div className="flex flex-wrap gap-2">
      {REGION_SIDO_OPTIONS.map((region) => {
        const active = selected.includes(region);
        return (
          <button
            key={region}
            type="button"
            onClick={() => toggle(region)}
            aria-pressed={active}
            className={cn(
              'h-11 min-w-[44px] rounded-full border px-4 text-sm font-medium transition-colors',
              active
                ? 'border-brand-800 bg-brand-100 text-brand-900'
                : 'border-neutral-200 bg-white text-neutral-700 hover:border-neutral-300',
            )}
          >
            {REGION_LABELS[region]}
          </button>
        );
      })}
    </div>
  );
}
