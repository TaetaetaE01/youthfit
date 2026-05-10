import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { QnaSource } from '@/types/qna';

interface Props {
  source: QnaSource;
}

function formatPage(start: number | null, end: number | null): string {
  if (start == null || end == null) return '';
  if (start === end) return ` · p.${start}`;
  return ` · p.${start}-${end}`;
}

export function QnaSourceItem({ source }: Props) {
  const [expanded, setExpanded] = useState(false);
  const label = source.attachmentLabel ?? `정책 #${source.policyId}`;
  const page = formatPage(source.pageStart, source.pageEnd);
  const hasExcerpt = source.excerpt != null && source.excerpt.length > 0;

  return (
    <li className="my-1">
      <div className="flex items-center gap-1">
        <span className="text-[13px] text-chat-bubble-text">
          📄 {label}
          {page}
        </span>
        {hasExcerpt && (
          <button
            type="button"
            aria-label="발췌 펼치기"
            aria-expanded={expanded}
            onClick={() => setExpanded((v) => !v)}
            className="ml-1 inline-flex h-6 w-6 items-center justify-center rounded text-chat-soft hover:bg-chat-source-bg"
          >
            <ChevronDown
              className={cn('h-3.5 w-3.5 transition-transform', expanded && 'rotate-180')}
            />
          </button>
        )}
      </div>
      {hasExcerpt && expanded && (
        <p className="mt-1 rounded bg-chat-source-bg/60 px-2 py-1.5 text-[12px] italic text-slate-600">
          {source.excerpt}
        </p>
      )}
    </li>
  );
}
