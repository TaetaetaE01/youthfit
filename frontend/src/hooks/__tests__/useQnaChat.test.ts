import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

vi.mock('@/apis/qna.api');
vi.mock('@/stores/authStore', () => {
  const state = { accessToken: 'test-token' as string | null, isAuthenticated: true };
  const useAuthStore = <T,>(selector?: (s: typeof state) => T) =>
    selector ? selector(state) : state;
  useAuthStore.setState = (partial: Partial<typeof state>) => {
    Object.assign(state, partial);
  };
  useAuthStore.getState = () => state;
  return { useAuthStore };
});

import { useQnaChat } from '../useQnaChat';
import * as qnaApi from '@/apis/qna.api';
import { useAuthStore } from '@/stores/authStore';

beforeEach(() => {
  vi.clearAllMocks();
  (useAuthStore as unknown as { setState: (s: Record<string, unknown>) => void }).setState({
    accessToken: 'test-token',
    isAuthenticated: true,
  });
});

describe('useQnaChat', () => {
  it('send 시 user/assistant 메시지가 push 된다', () => {
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async () => {});
    const { result } = renderHook(() => useQnaChat(1));

    act(() => {
      result.current.send('신청 자격은?');
    });

    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0]).toMatchObject({
      role: 'user',
      content: '신청 자격은?',
      status: 'done',
    });
    expect(result.current.messages[1]).toMatchObject({
      role: 'assistant',
      content: '',
      status: 'streaming',
    });
    expect(result.current.messages[1].questionRef).toBe(result.current.messages[0].id);
  });

  it('onChunk 콜백으로 assistant content 가 누적된다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));

    await waitFor(() => expect(callbacks).not.toBeNull());

    act(() => callbacks!.onChunk('안녕'));
    act(() => callbacks!.onChunk('하세요'));

    expect(result.current.messages[1].content).toBe('안녕하세요');
  });

  it('onError 시 status 가 error 로 바뀐다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(callbacks).not.toBeNull());

    act(() => callbacks!.onError(new Error('boom')));

    expect(result.current.messages[1].status).toBe('error');
    expect(result.current.messages[1].content).toContain('답변을 생성하지 못했습니다');
  });

  it('onDone 시 isStreaming 이 false 가 된다', async () => {
    let callbacks: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      callbacks = cb;
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(callbacks).not.toBeNull());

    expect(result.current.isStreaming).toBe(true);
    act(() => callbacks!.onDone());
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.messages[1].status).toBe('done');
  });

  it('retry 는 같은 assistant 메시지를 제자리에서 reset 한다', async () => {
    const calls: qnaApi.QnaCallbacks[] = [];
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, cb) => {
      calls.push(cb);
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q'));
    await waitFor(() => expect(calls).toHaveLength(1));
    act(() => calls[0].onError(new Error('boom')));
    expect(result.current.messages[1].status).toBe('error');

    const assistantId = result.current.messages[1].id;
    act(() => result.current.retry(assistantId));

    expect(result.current.messages).toHaveLength(2); // 새 메시지 추가 안 됨
    expect(result.current.messages[1].id).toBe(assistantId);
    expect(result.current.messages[1].status).toBe('streaming');
    expect(result.current.messages[1].content).toBe('');
    await waitFor(() => expect(calls).toHaveLength(2));
  });

  it('연속 send 시 직전 요청을 abort 한다', async () => {
    const aborts: AbortSignal[] = [];
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, _cb, _t, signal) => {
      if (signal) aborts.push(signal);
    });

    const { result } = renderHook(() => useQnaChat(1));
    act(() => result.current.send('q1'));
    expect(aborts[0].aborted).toBe(false);
    act(() => result.current.send('q2'));
    expect(aborts[0].aborted).toBe(true);
  });
});
