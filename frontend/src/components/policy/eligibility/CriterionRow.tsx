import { Link } from 'react-router-dom';
import { Quote } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { CriterionItem } from '@/types/policy';
import { fieldToRowKey } from './fieldToRowKey';

interface Props {
  item: CriterionItem;
}

export default function CriterionRow({ item }: Props) {
  const isMissing = item.uncertainReason === 'MISSING_FIELD';
  const isAmbiguous = item.uncertainReason === 'AMBIGUOUS_SOURCE';
  const rowKey = fieldToRowKey(item.field);

  return (
    <li className="rounded-xl border border-neutral-100 bg-white p-4">
      {/* Row header */}
      <div className="mb-3 flex items-center gap-2">
        <p className="text-sm font-semibold text-neutral-900">{item.label}</p>
        {isAmbiguous && (
          <span className="inline-flex items-center rounded bg-warning-50 px-1.5 py-0.5 text-[10px] font-medium text-warning-700">
            근거 모호
          </span>
        )}
      </div>

      {/* Requirement vs userValue grid */}
      <dl className="mb-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
        <dt className="text-neutral-500">정책 요구</dt>
        <dd className="text-neutral-900">{item.requirement.displayText}</dd>
        <dt className="text-neutral-500">내 정보</dt>
        <dd
          className={cn(
            'break-words',
            item.userValue ? 'text-neutral-900' : 'italic text-neutral-400',
          )}
        >
          {item.userValue ? item.userValue.displayText : '미입력'}
        </dd>
      </dl>

      {/* Verdict text */}
      <p className="mb-3 text-sm leading-relaxed text-neutral-700">{item.verdictText}</p>

      {/* MISSING_FIELD CTA */}
      {isMissing && rowKey && (
        <Link
          to={`/mypage?focus=${encodeURIComponent(rowKey)}`}
          className="mb-3 inline-flex items-center gap-1 text-xs font-medium text-brand-800 hover:underline"
        >
          👉 정보 입력하면 더 정확해져요
        </Link>
      )}

      {/* Source snippet */}
      {item.source?.snippet && (
        <div className="flex items-start gap-1.5 rounded bg-neutral-50 p-2 text-xs italic text-neutral-600">
          <Quote className="mt-0.5 h-3 w-3 shrink-0 text-neutral-400" />
          <span>{item.source.snippet}</span>
        </div>
      )}
    </li>
  );
}
