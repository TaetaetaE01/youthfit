import type { SourceTag } from '@/types/adminPolicyProcessing';
import type { SourceType } from '@/types/policy';
import { cn } from '@/lib/cn';

/**
 * 출처 code → 테두리/텍스트 색.
 * Record<SourceType, …> 로 선언해 SourceType 에 출처가 추가되면 누락 키가 컴파일 에러로 드러난다.
 */
const SOURCE_STYLE: Record<SourceType, string> = {
  YOUTH_SEOUL_CRAWL: 'border-blue-300 text-blue-700',
  BOKJIRO_CENTRAL: 'border-green-300 text-green-700',
  YOUTH_CENTER: 'border-purple-300 text-purple-700',
};

/** 타입상으론 도달 불가지만, 백엔드가 새 출처를 보내는 런타임 상황을 위한 안전망. */
const FALLBACK_STYLE = 'border-neutral-300 text-neutral-600';
const EMPTY_STYLE = 'border-neutral-200 text-neutral-400';

const BADGE_BASE =
  'inline-block rounded-full border bg-transparent px-2 py-0.5 text-[10px] font-medium leading-tight';

interface Props {
  sources: SourceTag[];
}

/**
 * 정책 출처 태그 뱃지 묶음.
 * 배경은 투명하고 테두리·텍스트 색으로 출처를 구분한다(요구사항: 테두리 색 구분).
 * 출처가 여러 개면 전부 나열하고, 없으면 "출처없음" 중립 뱃지를 보여준다.
 */
export function SourceBadges({ sources }: Props) {
  // 백엔드가 아직 sources 필드를 내려주지 않는 응답(구버전/부분 응답)에도 렌더가 깨지지 않도록 방어.
  const list = sources ?? [];
  if (list.length === 0) {
    return (
      <span className={cn(BADGE_BASE, EMPTY_STYLE)}>출처없음</span>
    );
  }
  return (
    <span className="flex flex-wrap gap-1">
      {list.map((s) => (
        <span
          key={s.code}
          className={cn(BADGE_BASE, SOURCE_STYLE[s.code] ?? FALLBACK_STYLE)}
          title={s.code}
        >
          {s.label}
        </span>
      ))}
    </span>
  );
}
