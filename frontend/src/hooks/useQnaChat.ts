import { useCallback, useRef, useState } from 'react';
import { fetchQnaAnswer, type QnaCallbacks } from '@/apis/qna.api';
import { useAuthStore } from '@/stores/authStore';
import type { QnaMessage } from '@/types/qna';

export interface UseQnaChat {
  messages: QnaMessage[];
  isStreaming: boolean;
  send: (question: string) => void;
  retry: (assistantMessageId: string) => void;
  copy: (content: string) => Promise<void>;
}

const ERROR_FALLBACK = '답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.';

export function useQnaChat(policyId: number): UseQnaChat {
  const [messages, setMessages] = useState<QnaMessage[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const accessTokenRef = useRef<string | null>(null);
  accessTokenRef.current = useAuthStore((s) => s.accessToken);

  const isStreaming = messages.some((m) => m.status === 'streaming');

  const streamInto = useCallback(
    (assistantId: string, question: string) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      const callbacks: QnaCallbacks = {
        onChunk: (chunk) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, content: m.content + chunk } : m,
            ),
          );
        },
        onSources: (sources) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantId ? { ...m, sources } : m)),
          );
        },
        onDone: () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, status: 'done' } : m,
            ),
          );
        },
        onError: () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId
                ? { ...m, status: 'error', content: ERROR_FALLBACK }
                : m,
            ),
          );
        },
      };

      void fetchQnaAnswer(
        policyId,
        question,
        callbacks,
        accessTokenRef.current,
        controller.signal,
      );
    },
    [policyId],
  );

  const send = useCallback(
    (question: string) => {
      const userId = `user-${Date.now()}`;
      const assistantId = `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

      setMessages((prev) => [
        ...prev,
        { id: userId, role: 'user', content: question, status: 'done' },
        {
          id: assistantId,
          role: 'assistant',
          content: '',
          status: 'streaming',
          questionRef: userId,
        },
      ]);

      streamInto(assistantId, question);
    },
    [streamInto],
  );

  const messagesRef = useRef<QnaMessage[]>(messages);
  messagesRef.current = messages;

  const retry = useCallback(
    (assistantMessageId: string) => {
      const target = messagesRef.current.find((m) => m.id === assistantMessageId);
      if (!target || !target.questionRef) return;
      const userMsg = messagesRef.current.find((m) => m.id === target.questionRef);
      if (!userMsg) return;
      const questionContent = userMsg.content;

      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMessageId
            ? { ...m, content: '', sources: undefined, status: 'streaming' }
            : m,
        ),
      );
      streamInto(assistantMessageId, questionContent);
    },
    [streamInto],
  );

  const copy = useCallback(async (content: string) => {
    await navigator.clipboard.writeText(content);
  }, []);

  return { messages, isStreaming, send, retry, copy };
}
