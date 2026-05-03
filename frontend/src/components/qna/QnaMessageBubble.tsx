import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Copy, Check, RotateCcw } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { QnaMessage } from '@/types/qna';

interface Props {
  message: QnaMessage;
  onCopy: (content: string) => Promise<void>;
  onRetry: (assistantMessageId: string) => void;
}

export function QnaMessageBubble({ message, onCopy, onRetry }: Props) {
  const isUser = message.role === 'user';
  const isError = message.status === 'error';
  const isStreaming = message.status === 'streaming';
  const [copied, setCopied] = useState(false);
  const [announceCopy, setAnnounceCopy] = useState(false);

  const timersRef = useRef<number[]>([]);
  useEffect(
    () => () => {
      timersRef.current.forEach((id) => window.clearTimeout(id));
      timersRef.current = [];
    },
    [],
  );

  const handleCopy = async () => {
    try {
      await onCopy(message.content);
      timersRef.current.forEach((id) => window.clearTimeout(id));
      timersRef.current = [];
      setCopied(true);
      setAnnounceCopy(true);
      timersRef.current.push(
        window.setTimeout(() => setCopied(false), 1500),
        window.setTimeout(() => setAnnounceCopy(false), 1500),
      );
    } catch {
      // 클립보드 실패 시 silent (드물게 권한 거부)
    }
  };

  if (isUser) {
    return (
      <div className="flex justify-end animate-qna-msg-in">
        <div className="max-w-[88%] md:max-w-[80%] rounded-2xl rounded-br-md bg-chat-surface px-4 py-[11px] text-[15px] leading-6 text-white shadow-sm">
          <p className="whitespace-pre-wrap">{message.content}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="group flex justify-start animate-qna-msg-in">
      <div
        className={cn(
          'max-w-[88%] md:max-w-[80%] rounded-2xl rounded-bl-md px-[18px] py-[14px] text-[15px] leading-7 shadow-sm',
          isError
            ? 'border-l-[3px] border-error-500 bg-red-50 text-red-900'
            : 'bg-chat-bubble text-chat-bubble-text',
        )}
      >
        {isError ? (
          <p className="m-0">⚠️ {message.content}</p>
        ) : (
          <div className="qna-md">
            <ReactMarkdown
              components={{
                p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                strong: ({ children }) => (
                  <strong className="font-bold text-chat-surface">{children}</strong>
                ),
                em: ({ children }) => (
                  <em className="italic text-chat-surface">{children}</em>
                ),
                ul: ({ children }) => (
                  <ul className="my-1.5 list-disc pl-[1.4em] marker:text-chat-soft">
                    {children}
                  </ul>
                ),
                ol: ({ children }) => (
                  <ol className="my-1.5 list-decimal pl-[1.4em] marker:text-chat-soft">
                    {children}
                  </ol>
                ),
                li: ({ children }) => <li className="my-0.5">{children}</li>,
                a: ({ href, children }) => (
                  <a
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-medium text-chat-surface underline underline-offset-2 hover:text-chat-surface-deep"
                  >
                    {children}
                  </a>
                ),
                code: ({ children }) => (
                  <code className="rounded bg-chat-source-bg px-[7px] py-[2px] font-mono text-[0.88em] text-chat-surface">
                    {children}
                  </code>
                ),
                blockquote: ({ children }) => (
                  <blockquote className="my-2 border-l-[3px] border-chat-soft pl-3 italic text-slate-600">
                    {children}
                  </blockquote>
                ),
                hr: () => <hr className="my-3 border-t border-slate-200" />,
                table: () => null,
                pre: ({ children }) => <>{children}</>,
                img: () => null,
              }}
            >
              {message.content}
            </ReactMarkdown>
            {isStreaming && message.content !== '' && (
              <span
                data-qna-cursor
                aria-hidden="true"
                className="ml-0.5 inline-block h-[1em] w-[2px] -mb-0.5 bg-chat-surface animate-qna-cursor-blink"
              />
            )}
          </div>
        )}

        {!isError && message.sources && message.sources.length > 0 && (
          <div className="mt-3 rounded-[10px] bg-chat-source-bg px-[14px] py-3 text-[13px] text-chat-bubble-text">
            <p className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-chat-surface">
              출처
            </p>
            <ul className="m-0 list-disc pl-[1.2em] marker:text-chat-soft">
              {message.sources.map((src, i) => (
                <li key={i} className="my-0.5">
                  {src}
                </li>
              ))}
            </ul>
          </div>
        )}

        {!isError && message.status === 'done' && (
          <div className="mt-2 flex justify-end gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
            <button
              type="button"
              aria-label="답변 복사"
              onClick={handleCopy}
              className={cn(
                'flex h-9 w-9 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition hover:bg-slate-100 hover:text-chat-bubble-text',
                copied && 'bg-chat-source-bg text-chat-surface',
              )}
            >
              {copied ? (
                <Check className="h-4 w-4 animate-qna-check-pop" />
              ) : (
                <Copy className="h-4 w-4" />
              )}
            </button>
          </div>
        )}

        {isError && (
          <div className="mt-2">
            <button
              type="button"
              aria-label="답변 재생성"
              onClick={() => onRetry(message.id)}
              className="inline-flex items-center gap-1.5 rounded-md bg-chat-surface px-3 py-1.5 text-[13px] font-medium text-white transition hover:bg-chat-surface-deep"
            >
              <RotateCcw className="h-3.5 w-3.5" /> 재시도
            </button>
          </div>
        )}

        {announceCopy && (
          <span className="sr-only" role="status" aria-live="polite">
            복사되었습니다
          </span>
        )}
      </div>
    </div>
  );
}
