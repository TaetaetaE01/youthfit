import { Bell, ExternalLink } from 'lucide-react';
import { cn } from '@/lib/cn';

interface Props {
  applyUrl?: string | null;
  sourceUrl?: string | null;
  isSubscribed: boolean;
  visible: boolean;
  onNotificationClick: () => void;
}

export function PolicyMobileBottomBar({
  applyUrl,
  sourceUrl,
  isSubscribed,
  visible,
  onNotificationClick,
}: Props) {
  const targetUrl = applyUrl ?? sourceUrl ?? null;

  return (
    <div
      className={cn(
        'fixed bottom-0 left-0 right-0 z-20 border-t border-neutral-200 bg-white/95 px-4 py-3 backdrop-blur lg:hidden',
        'transition-transform duration-200',
        visible ? 'translate-y-0' : 'translate-y-full',
      )}
    >
      <div className="mx-auto flex max-w-7xl items-center gap-2">
        <button
          type="button"
          onClick={onNotificationClick}
          aria-label={isSubscribed ? '알림 해제' : '알림 받기'}
          className={cn(
            'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border transition-colors',
            isSubscribed
              ? 'border-brand-800 bg-brand-100 text-brand-800'
              : 'border-neutral-200 bg-white text-neutral-600',
          )}
        >
          <Bell className={cn('h-5 w-5', isSubscribed && 'fill-brand-800')} />
        </button>
        {targetUrl && (
          <a
            href={targetUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex h-12 flex-1 items-center justify-center gap-1.5 rounded-xl bg-brand-800 text-sm font-semibold text-white"
          >
            공식 신청 페이지로 이동
            <ExternalLink className="h-4 w-4" />
          </a>
        )}
      </div>
    </div>
  );
}
