import { CheckCircle, AlertCircle, XCircle } from 'lucide-react';
import type { EligibilityResult } from '@/types/policy';

export type ResultVariant = EligibilityResult;

export const RESULT_CONFIG: Record<
  ResultVariant,
  {
    icon: typeof CheckCircle;
    color: string;
    bg: string;
    label: string;
    groupLabel: string;
  }
> = {
  LIKELY_ELIGIBLE: {
    icon: CheckCircle,
    color: 'text-success-500',
    bg: 'bg-success-50',
    label: '해당 가능성 높음',
    groupLabel: '충족한 조건',
  },
  UNCERTAIN: {
    icon: AlertCircle,
    color: 'text-warning-500',
    bg: 'bg-warning-50',
    label: '추가 확인 필요',
    groupLabel: '추가 확인이 필요한 조건',
  },
  LIKELY_INELIGIBLE: {
    icon: XCircle,
    color: 'text-error-500',
    bg: 'bg-error-50',
    label: '해당 가능성 낮음',
    groupLabel: '충족하지 못한 조건',
  },
};
