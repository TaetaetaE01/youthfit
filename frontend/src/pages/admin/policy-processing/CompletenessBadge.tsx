import type { Completeness } from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';

const STYLE: Record<Completeness, string> = {
  COMPLETE: 'bg-green-50 text-green-700',
  PARTIAL: 'bg-amber-50 text-amber-700',
  INCOMPLETE: 'bg-red-50 text-red-700',
};

const LABEL: Record<Completeness, string> = {
  COMPLETE: '완전',
  PARTIAL: '부분',
  INCOMPLETE: '미흡',
};

interface Props {
  value: Completeness;
}

export function CompletenessBadge({ value }: Props) {
  return (
    <span className={cn('inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold', STYLE[value])}>
      {LABEL[value]}
    </span>
  );
}
