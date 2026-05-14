import type { GuideListSection } from '@/types/policy';
import { AiSourceChip } from './AiSourceChip';

interface Props {
  title: string;
  emoji: string;
  section: GuideListSection | null;
}

export function GuideListSectionCard({ title, emoji, section }: Props) {
  if (!section) return null;

  const isEnrichment = section.sourceField === 'ENRICHMENT';
  const isSingle = section.items.length === 1;

  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-base font-semibold text-neutral-900">
          <span aria-hidden>{emoji}</span>
          {title}
        </h2>
        {isEnrichment && <AiSourceChip />}
      </div>
      {isSingle ? (
        <p className="text-sm text-neutral-800">{section.items[0]}</p>
      ) : (
        <ul className="space-y-2">
          {section.items.map((item, i) => (
            <li key={i} className="flex gap-2 text-sm text-neutral-800">
              <span aria-hidden>•</span>
              <span>{item}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
