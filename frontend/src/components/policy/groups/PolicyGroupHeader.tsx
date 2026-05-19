import { cn } from '@/lib/cn';
import type { PolicyGroup, PolicyGroupTone } from './policyGroups';

interface Props {
  group: PolicyGroup;
}

const TONE_STYLES: Record<PolicyGroupTone, string> = {
  brand: 'bg-brand-100 text-brand-800',
  amber: 'bg-amber-100 text-amber-700',
  success: 'bg-success-100 text-success-700',
  neutral: 'bg-neutral-100 text-neutral-600',
};

export function PolicyGroupHeader({ group }: Props) {
  const { id, label, description, Icon, tone } = group;
  return (
    <section id={id} className="mt-12 scroll-mt-24">
      <div className="mb-6 flex items-center gap-4 border-b-2 border-neutral-100 pb-5">
        <div
          data-icon-box
          className={cn(
            'flex h-13 w-13 shrink-0 items-center justify-center rounded-2xl',
            TONE_STYLES[tone],
          )}
        >
          <Icon className="h-7 w-7" strokeWidth={2} />
        </div>
        <div>
          <h2 className="text-xl font-semibold text-neutral-900">{label}</h2>
          <p className="mt-0.5 text-sm text-neutral-500">{description}</p>
        </div>
      </div>
    </section>
  );
}
