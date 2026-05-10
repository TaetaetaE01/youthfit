import bokjiroLogo from '@/assets/source-logos/bokjiro.gif';
import youthCenterLogo from '@/assets/source-logos/youth-center.png';
import youthSeoulLogo from '@/assets/source-logos/youth-seoul.svg';
import { cn } from '@/lib/cn';
import type { SourceType } from '@/types/policy';

const LOGO_MAP: Record<SourceType, string> = {
  BOKJIRO_CENTRAL: bokjiroLogo,
  YOUTH_CENTER: youthCenterLogo,
  YOUTH_SEOUL_CRAWL: youthSeoulLogo,
};

// 출처별 종횡비가 달라 같은 높이를 적용하면 시각적 균형이 맞지 않는다.
// 복지로(3:2)·온통청년(약 1.7:1)은 로고형이라 약간 크게, 청년 서울(4:1)은
// 텍스트 배지형이라 작게 둔다.
const HEIGHT_MAP: Record<SourceType, { sm: string; md: string }> = {
  BOKJIRO_CENTRAL: { sm: 'h-7', md: 'h-9' },
  YOUTH_CENTER: { sm: 'h-7', md: 'h-9' },
  YOUTH_SEOUL_CRAWL: { sm: 'h-5', md: 'h-6' },
};

interface Props {
  sourceType: SourceType | null;
  sourceLabel: string | null;
  size?: 'sm' | 'md';
}

export default function SourceBadge({ sourceType, sourceLabel, size = 'sm' }: Props) {
  if (!sourceType || !sourceLabel) return null;
  const heightCls = HEIGHT_MAP[sourceType][size];
  return (
    <span
      className="inline-flex items-center"
      title={`출처: ${sourceLabel}`}
    >
      <img
        src={LOGO_MAP[sourceType]}
        alt={sourceLabel}
        className={cn(heightCls, 'w-auto')}
      />
    </span>
  );
}
