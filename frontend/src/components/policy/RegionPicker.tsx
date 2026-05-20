import { useEffect, useMemo, useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { RegionListResponse, RegionSigungu } from '@/types/region';
import { NATIONWIDE_TOKEN, classifyRegionCodes } from '@/lib/regionFilter';

interface RegionPickerProps {
  open: boolean;
  onClose: () => void;
  selectedCodes: string[];
  onApply: (codes: string[]) => void;
  regionData: RegionListResponse | undefined;
  mode: 'mobile-sheet' | 'desktop-popover';
}

const NATIONWIDE_SIDO = { code: NATIONWIDE_TOKEN, name: '전국' } as const;

export default function RegionPicker({
  open,
  onClose,
  selectedCodes,
  onApply,
  regionData,
  mode,
}: RegionPickerProps) {
  const [draft, setDraft] = useState<string[]>(selectedCodes);
  const [activeSido, setActiveSido] = useState<string>('11'); // 기본 서울

  // open 될 때 selectedCodes 로 초기화
  useEffect(() => {
    if (open) setDraft(selectedCodes);
  }, [open, selectedCodes]);

  // 모바일 시트일 때 body 스크롤 잠금
  useEffect(() => {
    if (mode !== 'mobile-sheet') return;
    if (open) document.body.style.overflow = 'hidden';
    else document.body.style.overflow = '';
    return () => { document.body.style.overflow = ''; };
  }, [open, mode]);

  // ESC 닫기
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const sidos = useMemo(
    () => regionData?.sidos ?? [],
    [regionData],
  );
  const sigunguBySido = useMemo(() => {
    const map = new Map<string, RegionSigungu[]>();
    (regionData?.sigungus ?? []).forEach((g) => {
      const arr = map.get(g.sidoCode) ?? [];
      arr.push(g);
      map.set(g.sidoCode, arr);
    });
    return map;
  }, [regionData]);

  const draftClassified = useMemo(() => classifyRegionCodes(draft), [draft]);

  const countForSido = (sidoCode: string): number => {
    return draft.filter((c) => c.length === 5 && c.startsWith(sidoCode)).length;
  };

  const toggleSigungu = (code: string) => {
    setDraft((prev) => prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]);
  };

  const toggleSidoFull = (sidoCode: string) => {
    // 시·도 전체 토글: 그 시·도 코드 자체를 draft 에 추가/제거.
    // 추가 시 해당 시·도 산하 시·군·구 코드는 정리 (중복 의미 회피)
    setDraft((prev) => {
      if (prev.includes(sidoCode)) {
        return prev.filter((c) => c !== sidoCode);
      }
      const withoutSidoChildren = prev.filter((c) => !(c.length === 5 && c.startsWith(sidoCode)));
      return [...withoutSidoChildren, sidoCode];
    });
  };

  const toggleNationwide = () => {
    setDraft((prev) => prev.includes(NATIONWIDE_TOKEN) ? [] : [NATIONWIDE_TOKEN]);
  };

  const applyAndClose = () => {
    onApply(draft);
    onClose();
  };

  const resetAll = () => setDraft([]);

  if (!open) return null;

  const appliedCount = draft.length;
  const isNationwide = draftClassified.isNationwideOnly;

  const body = (
    <div className={cn(
      'flex flex-col bg-white',
      mode === 'mobile-sheet'
        ? 'fixed inset-0 z-50 md:hidden'
        : 'absolute right-0 top-full z-40 mt-2 w-[460px] overflow-hidden rounded-2xl border border-gray-200 shadow-xl',
    )}>
      <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
        <h2 id="region-picker-title" className="text-base font-bold text-gray-900">지역 선택</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-gray-100"
        >
          <X className="h-5 w-5 text-gray-500" />
        </button>
      </div>

      <div className="border-b border-gray-100 bg-green-50 px-4 py-2 text-xs text-green-700">
        ✓ "전국" 정책은 어떤 지역을 골라도 항상 함께 보여요
      </div>

      <div className={cn('flex flex-1 min-h-0', mode === 'desktop-popover' && 'h-[360px]')}>
        {/* 좌측 시·도 리스트 */}
        <div
          role="listbox"
          aria-label="시·도 선택"
          className="w-[38%] overflow-y-auto border-r border-gray-100 bg-gray-50"
        >
          <button
            type="button"
            role="option"
            aria-selected={isNationwide}
            onClick={() => {
              toggleNationwide();
              setActiveSido(NATIONWIDE_TOKEN);
            }}
            className={cn(
              'flex w-full items-center justify-between px-3 py-3 text-left text-sm border-b border-gray-100',
              isNationwide ? 'bg-white font-semibold text-brand-800' : 'text-gray-700 hover:bg-white',
            )}
          >
            <span>{NATIONWIDE_SIDO.name}</span>
          </button>
          {sidos.map((sido) => {
            const active = activeSido === sido.code;
            const cnt = countForSido(sido.code);
            const sidoSelected = draft.includes(sido.code);
            return (
              <button
                key={sido.code}
                type="button"
                role="option"
                aria-selected={active}
                onClick={() => setActiveSido(sido.code)}
                className={cn(
                  'flex w-full items-center justify-between px-3 py-3 text-left text-sm border-b border-gray-100',
                  active ? 'bg-white font-semibold text-brand-800' : 'text-gray-700 hover:bg-white',
                )}
              >
                <span>{sido.name.replace('특별시', '').replace('광역시', '').replace('특별자치도', '').replace('특별자치시', '').replace('도', '도')}</span>
                {(cnt > 0 || sidoSelected) && (
                  <span className="text-xs text-brand-800">
                    {sidoSelected ? '전체' : `${cnt}`}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* 우측 시·군·구 */}
        <div className="flex-1 overflow-y-auto">
          {isNationwide ? (
            <div className="p-6 text-center text-sm text-gray-500">
              전국만 보기 모드입니다. 시·군·구를 추가하려면 좌측에서 다른 시·도를 선택하세요.
            </div>
          ) : (
            <>
              {/* 시·도 전체 토글 */}
              {activeSido !== NATIONWIDE_TOKEN && (
                <label className="flex cursor-pointer items-center justify-between border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-900 hover:bg-gray-50">
                  <span>
                    {sidos.find((s) => s.code === activeSido)?.name} 전체
                  </span>
                  <input
                    type="checkbox"
                    checked={draft.includes(activeSido)}
                    onChange={() => toggleSidoFull(activeSido)}
                    className="h-4 w-4 rounded border-gray-300 text-brand-800 focus:ring-brand-800"
                  />
                </label>
              )}
              {/* 시·군·구 리스트 */}
              {(sigunguBySido.get(activeSido) ?? []).map((g) => {
                const checked = draft.includes(g.code);
                return (
                  <label
                    key={g.code}
                    className="flex cursor-pointer items-center justify-between border-b border-gray-100 px-4 py-3 text-sm text-gray-700 hover:bg-gray-50"
                  >
                    <span>{g.name}</span>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleSigungu(g.code)}
                      className="h-4 w-4 rounded border-gray-300 text-brand-800 focus:ring-brand-800"
                    />
                  </label>
                );
              })}
            </>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="border-t border-gray-100 bg-gray-50 px-4 py-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={resetAll}
            className="rounded-md px-3 py-2 text-xs font-semibold text-gray-600 hover:bg-gray-100"
          >
            전체 해제
          </button>
          <button
            type="button"
            onClick={applyAndClose}
            className="flex-1 rounded-xl bg-brand-800 px-4 py-3 text-sm font-semibold text-white hover:bg-brand-900"
          >
            {appliedCount === 0 ? '전체 보기' : `${appliedCount}개 지역 적용`}
          </button>
        </div>
      </div>
    </div>
  );

  if (mode === 'mobile-sheet') {
    return (
      <div role="dialog" aria-modal="true" aria-labelledby="region-picker-title">
        <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={onClose} aria-hidden="true" />
        {body}
      </div>
    );
  }
  return (
    <div role="dialog" aria-modal="false" aria-labelledby="region-picker-title">
      {body}
    </div>
  );
}
