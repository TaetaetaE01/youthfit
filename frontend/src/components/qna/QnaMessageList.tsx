import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { QnaMessageBubble } from './QnaMessageBubble';
import { QnaTypingIndicator } from './QnaTypingIndicator';
import type { QnaMessage } from '@/types/qna';

const NEAR_BOTTOM_PX = 80;

interface Props {
  messages: QnaMessage[];
  onCopy: (content: string) => Promise<void>;
  onRetry: (assistantMessageId: string) => void;
}

export function QnaMessageList({ messages, onCopy, onRetry }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [isNearBottom, setIsNearBottom] = useState(true);

  const isNearBottomNow = () => {
    const el = scrollRef.current;
    if (!el) return true;
    return el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
  };

  const scrollToBottom = (smooth = true) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
  };

  // chunk-by-chunk SSE 업데이트도 smooth follow 가 동작하도록
  // 마지막 메시지의 content 길이까지 의존성에 포함 (정적 변수로 추출)
  const lastContentLength = messages.at(-1)?.content.length ?? 0;

  useLayoutEffect(() => {
    if (isNearBottom) scrollToBottom(messages.length <= 2 ? false : true);
  }, [messages.length, lastContentLength, isNearBottom]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const onScroll = () => setIsNearBottom(isNearBottomNow());
    el.addEventListener('scroll', onScroll);
    return () => el.removeEventListener('scroll', onScroll);
  }, []);

  const last = messages.at(-1);
  const showTyping = last?.role === 'assistant' && last.status === 'streaming' && last.content === '';

  return (
    <div className="relative">
      <div
        ref={scrollRef}
        data-qna-scroller
        role="log"
        aria-live="polite"
        aria-atomic="false"
        className="min-h-[50vh] max-h-[70vh] md:max-h-[600px] space-y-3.5 overflow-y-auto pr-2 scrollbar-qna"
      >
        {messages.map((msg, idx) => {
          const isLast = idx === messages.length - 1;
          if (isLast && showTyping) {
            return <QnaTypingIndicator key={msg.id} />;
          }
          return (
            <QnaMessageBubble
              key={msg.id}
              message={msg}
              onCopy={onCopy}
              onRetry={onRetry}
            />
          );
        })}
      </div>
      {!isNearBottom && (
        <button
          type="button"
          aria-label="가장 최근 메시지로 이동"
          onClick={() => scrollToBottom(true)}
          className="absolute bottom-3 right-3 md:bottom-4 md:right-4 flex h-9 w-9 items-center justify-center rounded-full bg-white text-chat-surface shadow-lg shadow-chat-surface-deep/30 transition hover:bg-slate-50 animate-qna-jump-in"
        >
          <ChevronDown className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
