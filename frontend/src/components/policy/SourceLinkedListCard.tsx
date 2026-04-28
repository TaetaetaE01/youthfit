import { scrollAndHighlight } from '@/lib/scrollHighlight';
import type { GuideSourceField } from '@/types/policy';

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

const TONE_CLASSES = {
  indigo: {
    container: 'border-indigo-200 bg-indigo-50/50',
    title: 'text-indigo-900',
    button: 'border-indigo-300 text-indigo-800 hover:bg-indigo-100',
  },
  amber: {
    container: 'border-amber-200 bg-amber-50/50',
    title: 'text-amber-900',
    button: 'border-amber-300 text-amber-800 hover:bg-amber-100',
  },
} as const;

interface Item {
  text: string;
  sourceField: GuideSourceField;
}

export interface AttachmentRef {
  id?: number;
  name: string;
  url: string;
}

interface Props {
  title: string;
  emoji: string;
  tone: keyof typeof TONE_CLASSES;
  items: Item[];
  attachments: AttachmentRef[];
}

export function SourceLinkedListCard({ title, emoji, tone, items, attachments }: Props) {
  if (!items.length) return null;
  const t = TONE_CLASSES[tone];

  const renderAttachmentTrigger = () => {
    if (attachments.length === 0) return null;
    if (attachments.length === 1) {
      return (
        <a
          href={attachments[0].url}
          target="_blank"
          rel="noopener noreferrer"
          className={`inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
        >
          📎 원본 첨부
        </a>
      );
    }
    return (
      <button
        type="button"
        onClick={() => scrollAndHighlight('attachment-section')}
        className={`inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
      >
        📎 원본 첨부
      </button>
    );
  };

  return (
    <section className={`mb-6 rounded-2xl border p-6 ${t.container}`}>
      <div className="mb-4 flex items-center justify-between">
        <h2 className={`flex items-center gap-2 text-base font-semibold ${t.title}`}>
          <span aria-hidden>{emoji}</span>
          {title}
        </h2>
        {renderAttachmentTrigger()}
      </div>
      <ul className="space-y-3">
        {items.map((it, i) => (
          <li key={i} className="text-sm text-neutral-800">
            <p className="mb-1">• {it.text}</p>
            <button
              type="button"
              onClick={() => scrollAndHighlight(SCROLL_TARGETS[it.sourceField])}
              className={`ml-3 inline-flex items-center gap-1 rounded-md border bg-white px-2 py-0.5 text-xs ${t.button}`}
            >
              {SOURCE_LABELS[it.sourceField]} ↗
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
