import { Sparkles, MessageSquare } from 'lucide-react';
import { useQnaChat } from '@/hooks/useQnaChat';
import { QnaMessageList } from './QnaMessageList';
import { QnaSuggestionChips } from './QnaSuggestionChips';
import { QnaComposer } from './QnaComposer';

interface Props {
  isAuthenticated: boolean;
  policyId: number;
  onLoginPrompt: () => void;
}

export function QnaChatSection({ isAuthenticated, policyId, onLoginPrompt }: Props) {
  const { messages, isStreaming, send, retry, copy } = useQnaChat(policyId);

  const handlePick = (question: string) => {
    if (!isAuthenticated) {
      onLoginPrompt();
      return;
    }
    send(question);
  };

  const isEmpty = messages.length === 0;

  return (
    <section className="overflow-hidden rounded-2xl bg-gradient-to-br from-chat-surface-deep to-chat-surface p-4 md:p-6 shadow-xl shadow-chat-surface-deep/20 ring-1 ring-chat-surface/30">
      <header className="mb-4 flex items-center gap-2">
        <span className="inline-flex items-center gap-1 rounded-full bg-chat-accent/18 px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-chat-accent">
          <Sparkles className="h-3.5 w-3.5" />
          Smart Q&amp;A
        </span>
      </header>

      <div className="mb-4">
        {isEmpty ? (
          <div className="flex min-h-[50vh] flex-col items-center justify-center px-4 text-center">
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-chat-accent/15">
              <MessageSquare className="h-6 w-6 text-chat-accent" />
            </div>
            <p className="mb-1 text-[15px] font-medium text-chat-accent">
              이 정책에 대해 궁금한 점을 질문해보세요
            </p>
            <p className="mb-4 text-[13px] text-chat-accent/55">
              아래 추천 질문으로 빠르게 시작할 수 있어요
            </p>
            <QnaSuggestionChips onPick={handlePick} />
          </div>
        ) : (
          <QnaMessageList messages={messages} onCopy={copy} onRetry={retry} />
        )}
      </div>

      <QnaComposer
        disabled={!isAuthenticated || isStreaming}
        readOnly={!isAuthenticated}
        onFocus={!isAuthenticated ? onLoginPrompt : undefined}
        placeholder={
          isAuthenticated
            ? isStreaming
              ? '답변을 받는 중입니다...'
              : '질문을 입력하세요...'
            : '로그인 후 질문할 수 있어요'
        }
        onSubmit={send}
      />
    </section>
  );
}
