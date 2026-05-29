import type { Completeness } from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';

const STYLE: Record<Completeness, string> = {
  COMPLETE: 'bg-green-900/30 text-green-400',
  PARTIAL: 'bg-amber-900/30 text-amber-400',
  INCOMPLETE: 'bg-red-900/30 text-red-400',
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
    <span className={cn('inline-block px-2 py-0.5 rounded text-[10px] font-semibold', STYLE[value])}>
      {LABEL[value]}
    </span>
  );
}
