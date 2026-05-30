import { ExternalLink } from 'lucide-react';

interface Props {
  applyUrl?: string | null;
  sourceUrl?: string | null;
}

/**
 * 상세페이지 '신청하기' 그룹의 공식 신청 페이지 CTA.
 * applyUrl(신청 사이트) 우선, 없으면 sourceUrl 로 폴백 — 모바일 하단바(PolicyMobileBottomBar)와 동일 동작.
 * 모바일은 하단바(lg:hidden)가 같은 링크를 제공하므로, 본문 CTA 는 데스크톱 전용(hidden lg:flex)으로 노출해
 * 모바일 중복을 피한다.
 */
export function PolicyApplyCta({ applyUrl, sourceUrl }: Props) {
  const targetUrl = applyUrl || sourceUrl || null;
  if (!targetUrl) return null;

  return (
    <a
      href={targetUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="mb-6 hidden items-center justify-center gap-2 rounded-xl bg-brand-800 px-5 py-4 text-base font-semibold text-white transition-colors hover:bg-brand-800/90 lg:flex"
    >
      공식 신청 페이지로 이동
      <ExternalLink className="h-4 w-4" />
    </a>
  );
}
