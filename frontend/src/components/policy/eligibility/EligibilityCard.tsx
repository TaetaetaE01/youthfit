import { Loader2 } from 'lucide-react';
import type { EligibilityResponse } from '@/types/policy';
import EligibilityHeader from './EligibilityHeader';
import CriterionGroup from './CriterionGroup';
import EligibilityFooter from './EligibilityFooter';

interface Props {
  isAuthenticated: boolean;
  eligibility: EligibilityResponse | null;
  loading: boolean;
  onCheck: () => void;
  onLoginPrompt: () => void;
  sourceUrl: string | null;
}

export default function EligibilityCard({
  isAuthenticated,
  eligibility,
  loading,
  onCheck,
  onLoginPrompt,
  sourceUrl,
}: Props) {
  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-4 text-xl font-semibold text-neutral-900">내 적합도 확인</h2>

      {!isAuthenticated && (
        <button
          onClick={onLoginPrompt}
          className="flex h-11 w-full items-center justify-center rounded-xl bg-brand-800 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          내 적합도 확인하기
        </button>
      )}

      {isAuthenticated && !eligibility && !loading && (
        <button
          onClick={onCheck}
          className="flex h-11 w-full items-center justify-center rounded-xl bg-brand-800 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          내 적합도 확인하기
        </button>
      )}

      {isAuthenticated && loading && (
        <div className="flex flex-col items-center gap-3 py-6">
          <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
          <p className="text-sm text-neutral-500">적합도를 분석하고 있어요...</p>
        </div>
      )}

      {isAuthenticated && eligibility && !loading && (
        <div>
          <EligibilityHeader
            overallResult={eligibility.overallResult}
            summary={eligibility.summary}
          />

          <CriterionGroup
            variant="LIKELY_INELIGIBLE"
            items={eligibility.criteria.ineligible}
          />
          <CriterionGroup
            variant="UNCERTAIN"
            items={eligibility.criteria.uncertain}
          />
          <CriterionGroup
            variant="LIKELY_ELIGIBLE"
            items={eligibility.criteria.eligible}
            defaultCollapsed={true}
          />

          <EligibilityFooter
            disclaimer={eligibility.disclaimer}
            sourceUrl={sourceUrl}
          />
        </div>
      )}
    </section>
  );
}
