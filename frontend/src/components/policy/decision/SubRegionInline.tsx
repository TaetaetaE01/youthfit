import type { PolicySubRegion } from '@/types/policy';

interface Props {
  subRegions: PolicySubRegion[] | null | undefined;
}

const INLINE_THRESHOLD = 5;

export function SubRegionInline({ subRegions }: Props) {
  if (!subRegions || subRegions.length === 0) return null;
  if (subRegions.length <= INLINE_THRESHOLD) {
    return <span>{subRegions.map((s) => s.name).join('·')}</span>;
  }
  const first = subRegions[0].name;
  const rest = subRegions.length - 1;
  return <span>{first} 외 {rest}개</span>;
}
