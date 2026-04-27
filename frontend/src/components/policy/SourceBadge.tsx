import bokjiroLogo from '@/assets/source-logos/bokjiro.svg';
import youthCenterLogo from '@/assets/source-logos/youth-center.svg';
import youthSeoulLogo from '@/assets/source-logos/youth-seoul.svg';
import { cn } from '@/lib/cn';
import type { SourceType } from '@/types/policy';

const LOGO_MAP: Record<SourceType, string> = {
  BOKJIRO_CENTRAL: bokjiroLogo,
  YOUTH_CENTER: youthCenterLogo,
  YOUTH_SEOUL_CRAWL: youthSeoulLogo,
};

interface Props {
  sourceType: SourceType | null;
  sourceLabel: string | null;
  size?: 'sm' | 'md';
}

export default function SourceBadge({ sourceType, sourceLabel, size = 'sm' }: Props) {
  if (!sourceType || !sourceLabel) return null;
  const heightCls = size === 'sm' ? 'h-5' : 'h-6';
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
