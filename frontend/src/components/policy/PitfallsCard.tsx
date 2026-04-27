import type { GuidePitfall, GuideSourceField } from '@/types/policy';
import { scrollAndHighlight } from '@/lib/scrollHighlight';

const SOURCE_LABELS: Record<GuideSourceField, string> = {
  SUPPORT_TARGET: '지원대상',
  SELECTION_CRITERIA: '선정기준',
  SUPPORT_CONTENT: '지원내용',
  BODY: '정책 본문',
};

const SCROLL_TARGETS: Record<GuideSourceField, string> = {
  SUPPORT_TARGET: 'paired-supportTarget',
  SELECTION_CRITERIA: 'paired-selectionCriteria',
  SUPPORT_CONTENT: 'paired-supportContent',
  BODY: 'policy-summary-section',
};

interface Props {
  pitfalls: GuidePitfall[];
}

export function PitfallsCard({ pitfalls }: Props) {
  if (!pitfalls.length) return null;

  return (
    <section className="mb-6 rounded-2xl border border-amber-200 bg-amber-50/50 p-6">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-amber-900">
        <span aria-hidden>⚠️</span>
        놓치기 쉬운 점
      </h2>
      <ul className="space-y-3">
        {pitfalls.map((p, i) => (
          <li key={i} className="text-sm text-neutral-800">
            <p className="mb-1">• {p.text}</p>
            <button
              type="button"
              onClick={() => scrollAndHighlight(SCROLL_TARGETS[p.sourceField])}
              className="ml-3 inline-flex items-center gap-1 rounded-md border border-amber-300 bg-white px-2 py-0.5 text-xs text-amber-800 hover:bg-amber-100"
            >
              {SOURCE_LABELS[p.sourceField]} ↗
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
