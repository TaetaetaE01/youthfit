import { ExternalLink } from 'lucide-react';

interface Props {
  applyUrl?: string | null;
  sourceUrl?: string | null;
}

/**
 * 상세페이지 '신청하기' 그룹의 공식 신청 페이지 CTA.
 * applyUrl(신청 사이트) 우선, 없으면 sourceUrl 로 폴백 — 모바일 하단바(PolicyMobileBottomBar)와 동일 동작.
 * 하단바는 lg:hidden 이라 데스크톱에선 안 보이므로, 본문 CTA 로 데스크톱에서도 노출한다.
 */
export function PolicyApplyCta({ applyUrl, sourceUrl }: Props) {
  const targetUrl = applyUrl ?? sourceUrl ?? null;
  if (!targetUrl) return null;

  return (
    <a
      href={targetUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="mb-6 flex items-center justify-center gap-2 rounded-xl bg-brand-800 px-5 py-4 text-base font-semibold text-white transition-colors hover:bg-brand-800/90"
    >
      공식 신청 페이지로 이동
      <ExternalLink className="h-4 w-4" />
    </a>
  );
}
