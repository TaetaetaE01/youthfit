import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import InterestCategoryChips from './InterestCategoryChips';
import InterestRegionChips from './InterestRegionChips';
import type { PolicyCategory } from '@/types/policy';
import type { RegionSidoCode } from '@/lib/labels/region';
import { cn } from '@/lib/cn';

interface Props {
  enabled: boolean;
  categories: PolicyCategory[];
  regions: RegionSidoCode[];
  hasEligibilityProfile: boolean;
  hasEmail: boolean;
  onToggle: (next: boolean) => void;
  onSave: (categories: PolicyCategory[], regions: RegionSidoCode[]) => void;
  saving?: boolean;
}

export default function RecommendationSection({
  enabled,
  categories,
  regions,
  hasEligibilityProfile,
  hasEmail,
  onToggle,
  onSave,
  saving,
}: Props) {
  const [draftCategories, setDraftCategories] = useState<PolicyCategory[]>(categories);
  const [draftRegions, setDraftRegions] = useState<RegionSidoCode[]>(regions);

  useEffect(() => {
    setDraftCategories(categories);
  }, [categories]);

  useEffect(() => {
    setDraftRegions(regions);
  }, [regions]);

  const dirty =
    JSON.stringify([...draftCategories].sort()) !== JSON.stringify([...categories].sort()) ||
    JSON.stringify([...draftRegions].sort()) !== JSON.stringify([...regions].sort());
  const totalSelected = draftCategories.length + draftRegions.length;
  const canSave = enabled && dirty && totalSelected > 0;
  const showInterestArea = enabled && hasEmail;

  return (
    <section
      className={cn(
        'rounded-2xl bg-white p-6 shadow-card transition-opacity',
        !hasEmail && 'opacity-70',
      )}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
          <Sparkles className="h-4 w-4 text-brand-800" />
        </div>
        <div className="flex-1">
          <h3 className="text-lg font-semibold text-neutral-900">맞춤 정책 추천</h3>
          <p className="mt-1 text-sm text-neutral-500">
            적합도 판정 정보와 관심 분야를 바탕으로 자격이 맞을 가능성이 높은 새 정책을 이메일로
            추천해 드려요.
          </p>
          {!hasEmail && (
            <p className="mt-2 text-xs text-neutral-500">
              활성화하려면 위에서 이메일을 먼저 등록해주세요.
            </p>
          )}
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          aria-label="맞춤 정책 추천 받기"
          onClick={() => onToggle(!enabled)}
          className={cn(
            'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors duration-200',
            enabled && hasEmail ? 'bg-brand-800' : 'bg-neutral-300',
          )}
        >
          <span
            className={cn(
              'pointer-events-none inline-block h-5 w-5 translate-y-0.5 rounded-full bg-white shadow-sm transition-transform duration-200',
              enabled && hasEmail ? 'translate-x-[22px]' : 'translate-x-0.5',
            )}
          />
        </button>
      </div>

      {showInterestArea && (
        <div className="mt-5 space-y-5">
          {!hasEligibilityProfile && (
            <div className="rounded-xl border border-warning-500/30 bg-warning-500/10 px-4 py-3 text-xs text-warning-500">
              적합도 정보를 먼저 입력하면 더 정확한 추천을 받을 수 있어요.
            </div>
          )}

          <div>
            <p className="mb-2 text-sm font-semibold text-neutral-700">관심 카테고리</p>
            <InterestCategoryChips selected={draftCategories} onChange={setDraftCategories} />
          </div>

          <div>
            <p className="mb-2 text-sm font-semibold text-neutral-700">관심 지역 (시/도)</p>
            <InterestRegionChips selected={draftRegions} onChange={setDraftRegions} />
          </div>

          {totalSelected === 0 && (
            <p className="text-xs text-error-500">관심 분야를 1개 이상 선택해주세요</p>
          )}

          <button
            type="button"
            disabled={!canSave || saving}
            onClick={() => onSave(draftCategories, draftRegions)}
            className={cn(
              'h-11 w-full rounded-xl text-sm font-semibold transition-colors',
              canSave && !saving
                ? 'bg-brand-800 text-white hover:bg-brand-900'
                : 'cursor-not-allowed bg-neutral-100 text-neutral-400',
            )}
          >
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      )}
    </section>
  );
}
