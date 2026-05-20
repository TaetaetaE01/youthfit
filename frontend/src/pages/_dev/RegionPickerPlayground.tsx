import { useState } from 'react';
import RegionPicker from '@/components/policy/RegionPicker';
import RegionPickerTrigger from '@/components/policy/RegionPickerTrigger';
import RegionPickerBanner from '@/components/policy/RegionPickerBanner';
import { useRegions } from '@/hooks/queries/useRegions';

export default function RegionPickerPlayground() {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string[]>(['11680', '11440']);
  const [bannerOn, setBannerOn] = useState(true);
  const [mode, setMode] = useState<'mobile-sheet' | 'desktop-popover'>('mobile-sheet');
  const { data, isLoading } = useRegions();

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="mb-6 text-2xl font-bold">RegionPicker Playground</h1>

      <div className="mb-4 flex gap-2 text-sm">
        <button
          className={mode === 'mobile-sheet' ? 'rounded bg-brand-800 px-3 py-1.5 text-white' : 'rounded border px-3 py-1.5'}
          onClick={() => setMode('mobile-sheet')}
        >mobile-sheet</button>
        <button
          className={mode === 'desktop-popover' ? 'rounded bg-brand-800 px-3 py-1.5 text-white' : 'rounded border px-3 py-1.5'}
          onClick={() => setMode('desktop-popover')}
        >desktop-popover</button>
      </div>

      {bannerOn && (
        <RegionPickerBanner regionLabel="서울 강남구" onDismiss={() => setBannerOn(false)} />
      )}

      <div className="relative inline-block">
        <RegionPickerTrigger
          selectedCodes={selected}
          regionData={data}
          onOpen={() => setOpen(true)}
        />
        <RegionPicker
          open={open}
          onClose={() => setOpen(false)}
          selectedCodes={selected}
          onApply={setSelected}
          regionData={data}
          mode={mode}
        />
      </div>

      <div className="mt-8 rounded-lg bg-gray-100 p-4">
        <p className="text-xs text-gray-500 mb-1">current selectedCodes</p>
        <code className="text-sm">{JSON.stringify(selected)}</code>
      </div>

      {isLoading && <p className="mt-4 text-sm text-gray-500">로딩 중…</p>}
    </div>
  );
}
