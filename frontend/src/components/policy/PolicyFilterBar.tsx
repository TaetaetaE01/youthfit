import { useState, useEffect } from 'react';
import { SlidersHorizontal, X } from 'lucide-react';
import { cn } from '@/lib/cn';
import RegionPicker from '@/components/policy/RegionPicker';
import RegionPickerTrigger from '@/components/policy/RegionPickerTrigger';
import type { PolicyCategory } from '@/types/policy';
import { CATEGORY_LABELS } from '@/types/policy';
import type { RegionListResponse } from '@/types/region';

const CATEGORY_ENTRIES = Object.entries(CATEGORY_LABELS) as [PolicyCategory, string][];

type Props = {
  category: PolicyCategory | '';
  regions: string[];
  regionData: RegionListResponse | undefined;
  onCategoryChange: (next: PolicyCategory | '') => void;
  onRegionsChange: (codes: string[]) => void;
  disabled?: boolean;
  disabledHint?: string;
};

/* ──────────────────────────────────────────────
   MobileFilterSheet (내부 전용)
   ────────────────────────────────────────────── */

function MobileFilterSheet({
  isOpen,
  onClose,
  category,
  onCategoryChange,
}: {
  isOpen: boolean;
  onClose: () => void;
  category: PolicyCategory | '';
  onCategoryChange: (v: PolicyCategory | '') => void;
}) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="absolute bottom-0 left-0 right-0 rounded-t-2xl bg-white p-6 pb-8 shadow-xl">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">필터</h2>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-gray-100"
            aria-label="필터 닫기"
          >
            <X className="h-5 w-5 text-gray-500" />
          </button>
        </div>

        <fieldset>
          <legend className="mb-2 text-sm font-semibold text-gray-700">카테고리</legend>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => onCategoryChange('')}
              className={cn(
                'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                category === ''
                  ? 'border-transparent bg-brand-100 text-indigo-600'
                  : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
              )}
            >
              전체
            </button>
            {CATEGORY_ENTRIES.map(([key, label]) => (
              <button
                key={key}
                onClick={() => onCategoryChange(category === key ? '' : key)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                  category === key
                    ? 'border-transparent bg-brand-100 text-indigo-600'
                    : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </fieldset>

        <button
          onClick={onClose}
          className="mt-6 w-full rounded-xl bg-brand-800 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
        >
          필터 적용
        </button>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────
   PolicyFilterBar
   ────────────────────────────────────────────── */

export default function PolicyFilterBar({
  category,
  regions,
  regionData,
  onCategoryChange,
  onRegionsChange,
  disabled = false,
  disabledHint,
}: Props) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const [regionPickerOpen, setRegionPickerOpen] = useState(false);

  // Active filter count for mobile badge (region trigger handles its own active state)
  const activeFilterCount = category ? 1 : 0;

  return (
    <>
      {/* ── Desktop Filters ── */}
      <div className="mb-4 hidden flex-wrap items-center gap-2 md:flex">
        <button
          onClick={() => onCategoryChange('')}
          className={cn(
            'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
            category === ''
              ? 'border-transparent bg-brand-100 text-indigo-600'
              : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
          )}
        >
          전체
        </button>
        {CATEGORY_ENTRIES.map(([key, label]) => (
          <button
            key={key}
            onClick={() => onCategoryChange(category === key ? '' : key)}
            className={cn(
              'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
              category === key
                ? 'border-transparent bg-brand-100 text-indigo-600'
                : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
            )}
          >
            {label}
          </button>
        ))}

        <span className="mx-1 h-6 w-px bg-neutral-200" aria-hidden="true" />

        <div className="relative">
          <RegionPickerTrigger
            selectedCodes={regions}
            regionData={regionData}
            onOpen={() => setRegionPickerOpen(true)}
            disabled={disabled}
          />
          {/* 데스크톱 팝오버 모드 */}
          <div className="hidden md:block">
            <RegionPicker
              open={regionPickerOpen}
              onClose={() => setRegionPickerOpen(false)}
              selectedCodes={regions}
              onApply={onRegionsChange}
              regionData={regionData}
              mode="desktop-popover"
            />
          </div>
        </div>

        {disabled && disabledHint && (
          <span className="ml-2 text-xs text-gray-500">{disabledHint}</span>
        )}
      </div>

      {/* ── Mobile Filter Bar ── */}
      <div className="mb-4 flex flex-wrap items-center gap-2 md:hidden">
        <button
          onClick={() => setSheetOpen(true)}
          className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-gray-50"
          aria-label="필터 열기"
        >
          <SlidersHorizontal className="h-4 w-4" />
          필터
          {activeFilterCount > 0 && (
            <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-800 text-xs text-white">
              {activeFilterCount}
            </span>
          )}
        </button>
        <RegionPickerTrigger
          selectedCodes={regions}
          regionData={regionData}
          onOpen={() => setRegionPickerOpen(true)}
          disabled={disabled}
        />
        {disabled && disabledHint && (
          <span className="text-[10px] text-gray-500">검색 결과 미적용</span>
        )}
      </div>

      {/* Mobile category sheet */}
      <MobileFilterSheet
        isOpen={sheetOpen}
        onClose={() => setSheetOpen(false)}
        category={category}
        onCategoryChange={onCategoryChange}
      />

      {/* 모바일 RegionPicker (sheet) */}
      <div className="md:hidden">
        <RegionPicker
          open={regionPickerOpen}
          onClose={() => setRegionPickerOpen(false)}
          selectedCodes={regions}
          onApply={onRegionsChange}
          regionData={regionData}
          mode="mobile-sheet"
        />
      </div>
    </>
  );
}
