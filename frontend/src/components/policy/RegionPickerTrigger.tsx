import { MapPin, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { RegionListResponse } from '@/types/region';
import { classifyRegionCodes } from '@/lib/regionFilter';

interface RegionPickerTriggerProps {
  selectedCodes: string[];
  regionData: RegionListResponse | undefined;
  onOpen: () => void;
  disabled?: boolean;
}

function buildLabel(selectedCodes: string[], data: RegionListResponse | undefined): string {
  if (selectedCodes.length === 0) return '전체';
  const { isNationwideOnly, sidoCodes, sigunguCodes } = classifyRegionCodes(selectedCodes);
  if (isNationwideOnly) return '전국만';
  if (!data) return `${selectedCodes.length}개 선택`;

  const sidoNames = new Map(data.sidos.map((s) => [s.code, s.name.replace('특별시', '').replace('광역시', '').replace('특별자치도', '').replace('특별자치시', '').replace('도', '')]));
  const sigunguMap = new Map(data.sigungus.map((g) => [g.code, g]));

  // 시·군·구만 있는 경우: 첫 항목을 풀네임으로 + 나머지 개수
  if (sidoCodes.length === 0 && sigunguCodes.length > 0) {
    const first = sigunguMap.get(sigunguCodes[0]);
    if (!first) return `${sigunguCodes.length}개 선택`;
    const firstSido = sidoNames.get(first.sidoCode) ?? first.sidoName;
    const rest = sigunguCodes.length - 1;
    return rest > 0 ? `${firstSido} ${first.name} +${rest}` : `${firstSido} ${first.name}`;
  }
  // 시·도만 있는 경우
  if (sigunguCodes.length === 0 && sidoCodes.length > 0) {
    const firstSido = sidoNames.get(sidoCodes[0]) ?? sidoCodes[0];
    const rest = sidoCodes.length - 1;
    return rest > 0 ? `${firstSido} +${rest}` : firstSido;
  }
  // 혼합
  const total = sidoCodes.length + sigunguCodes.length;
  return `${total}개 선택`;
}

export default function RegionPickerTrigger({
  selectedCodes,
  regionData,
  onOpen,
  disabled = false,
}: RegionPickerTriggerProps) {
  const label = buildLabel(selectedCodes, regionData);
  const active = selectedCodes.length > 0;
  const count = selectedCodes.length;

  return (
    <button
      type="button"
      onClick={onOpen}
      disabled={disabled}
      aria-label={count > 0 ? `지역 선택: ${label}` : '지역 선택'}
      aria-haspopup="dialog"
      className={cn(
        'inline-flex min-h-11 items-center gap-1 rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
        active
          ? 'border-transparent bg-brand-100 text-indigo-600'
          : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
        disabled && 'cursor-not-allowed opacity-50',
      )}
    >
      <MapPin className="h-4 w-4" aria-hidden="true" />
      <span className="max-w-[140px] truncate">{label}</span>
      <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
    </button>
  );
}
