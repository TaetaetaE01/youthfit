import { Calendar, Users, CalendarDays, Repeat } from 'lucide-react';

interface Props {
  applyEnd: string | null;
  supportScale: number | null;
  referenceYear: number | null;
  supportCycle: string | null;
}

function daysUntil(dateIso: string): number {
  const target = new Date(dateIso).getTime();
  const now = Date.now();
  return Math.ceil((target - now) / (24 * 60 * 60 * 1000));
}

function formatApplyEnd(dateIso: string): string {
  const d = new Date(dateIso);
  const dDay = daysUntil(dateIso);
  const md = `${d.getMonth() + 1}/${d.getDate()}`;
  return dDay >= 0 ? `${md} (D-${dDay})` : md;
}

export function DecisionMetaGrid({ applyEnd, supportScale, referenceYear, supportCycle }: Props) {
  const items: { Icon: typeof Calendar; label: string; value: string }[] = [];
  if (applyEnd) items.push({ Icon: Calendar, label: '마감일', value: formatApplyEnd(applyEnd) });
  if (supportScale != null) items.push({ Icon: Users, label: '지원규모', value: `${supportScale.toLocaleString()}명` });
  if (referenceYear) items.push({ Icon: CalendarDays, label: '기준연도', value: `'${String(referenceYear).slice(-2)}년` });
  if (supportCycle) items.push({ Icon: Repeat, label: '지원주기', value: supportCycle });

  if (items.length === 0) return null;

  return (
    <section className="mb-6 overflow-hidden rounded-2xl border border-neutral-200 bg-white">
      <div className="grid grid-cols-2 divide-x divide-y divide-neutral-200 sm:grid-cols-4 sm:divide-y-0">
        {items.map(({ Icon, label, value }) => (
          <div key={label} className="flex flex-col items-center gap-1.5 px-4 py-5 text-center">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100">
              <Icon className="h-4 w-4 text-brand-800" />
            </div>
            <span className="text-xs text-neutral-500">{label}</span>
            <span className="text-sm font-semibold text-neutral-900">{value}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
