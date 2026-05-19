import { Calendar, Repeat, Tag, Phone } from 'lucide-react';
import { AiSourceChip } from '@/components/policy/AiSourceChip';
import { pickWithFallback } from '@/lib/policyEnrichment';

interface Props {
  referenceYear: number | null;
  supportCycle: string | null;
  provideType: string | null;
  contact: string | null;
  contactFallback?: string | null;
  enrichmentSourceUrl?: string | null;
}

export function DecisionMetaGrid({
  referenceYear,
  supportCycle,
  provideType,
  contact,
  contactFallback,
  enrichmentSourceUrl,
}: Props) {
  const items: {
    Icon: typeof Calendar;
    label: string;
    value: string;
    fromAi?: boolean;
  }[] = [];
  if (referenceYear) items.push({ Icon: Calendar, label: '기준연도', value: `${referenceYear}년` });
  if (supportCycle) items.push({ Icon: Repeat, label: '지원주기', value: supportCycle });
  if (provideType) items.push({ Icon: Tag, label: '제공유형', value: provideType });

  const contactPick = pickWithFallback(contact, contactFallback);
  if (contactPick) {
    items.push({
      Icon: Phone,
      label: '문의처',
      value: contactPick.value,
      fromAi: contactPick.fromAi,
    });
  }

  if (items.length === 0) return null;

  return (
    <section className="mb-6 overflow-hidden rounded-2xl border border-neutral-200 bg-white">
      <div className="grid grid-cols-2 divide-x divide-y divide-neutral-200 sm:grid-cols-4 sm:divide-y-0">
        {items.map(({ Icon, label, value, fromAi }) => (
          <div key={label} className="flex flex-col items-center gap-1.5 px-4 py-5 text-center">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100">
              <Icon className="h-4 w-4 text-brand-800" />
            </div>
            <span className="flex items-center gap-1 text-xs text-neutral-500">
              {label}
              {fromAi && <AiSourceChip sourceUrl={enrichmentSourceUrl} />}
            </span>
            <span className="text-sm font-semibold text-neutral-900">{value}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
