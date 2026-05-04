import { cn } from '@/lib/cn';
import type { EligibilityResult, SummaryView } from '@/types/policy';
import { RESULT_CONFIG } from './eligibilityStyles';

interface Props {
  overallResult: EligibilityResult;
  summary: SummaryView;
}

export default function EligibilityHeader({ overallResult, summary }: Props) {
  const cfg = RESULT_CONFIG[overallResult];
  const Icon = cfg.icon;
  return (
    <div className={cn('mb-5 rounded-xl px-4 py-4 text-center', cfg.bg)}>
      <div className="mb-1 flex items-center justify-center gap-2">
        <Icon className={cn('h-5 w-5', cfg.color)} />
        <p className={cn('text-lg font-bold', cfg.color)}>{cfg.label}</p>
      </div>
      <p className="text-sm text-neutral-700">{summary.headline}</p>
    </div>
  );
}
