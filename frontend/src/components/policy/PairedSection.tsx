import type { ReactNode } from 'react';
import type { GuidePairedSection } from '@/types/policy';
import { EasySectionBox } from './EasySectionBox';

interface Props {
  id: string;
  easyTitle: string;
  easyData: GuidePairedSection | null;
  originalTitle: string;
  originalContent: string | null;
  originalRenderer: (content: string) => ReactNode;
}

export function PairedSection({
  id,
  easyTitle,
  easyData,
  originalTitle,
  originalContent,
  originalRenderer,
}: Props) {
  if (!originalContent) return null;

  return (
    <section id={id} className="mb-6">
      {easyData && <EasySectionBox title={easyTitle} items={easyData.items} />}
      <div
        className={
          easyData
            ? 'rounded-b-2xl border border-neutral-200 bg-white p-5'
            : 'rounded-2xl border border-neutral-200 bg-white p-5'
        }
      >
        <h3 className="mb-3 text-base font-semibold text-neutral-700">{originalTitle}</h3>
        <div className="text-sm text-neutral-600">{originalRenderer(originalContent)}</div>
      </div>
    </section>
  );
}
