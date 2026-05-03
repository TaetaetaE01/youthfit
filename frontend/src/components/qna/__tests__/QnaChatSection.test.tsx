import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';

beforeAll(() => {
  Element.prototype.scrollTo = vi.fn();
});

vi.mock('@/apis/qna.api');
vi.mock('@/stores/authStore', () => {
  const state = { accessToken: 'token' as string | null, isAuthenticated: true };
  const useAuthStore = <T,>(selector?: (s: typeof state) => T) =>
    selector ? selector(state) : state;
  useAuthStore.setState = (partial: Partial<typeof state>) => {
    Object.assign(state, partial);
  };
  useAuthStore.getState = () => state;
  return { useAuthStore };
});

import { QnaChatSection } from '../QnaChatSection';
import * as qnaApi from '@/apis/qna.api';
import { useAuthStore } from '@/stores/authStore';

beforeEach(() => {
  vi.clearAllMocks();
  (useAuthStore as unknown as { setState: (s: Record<string, unknown>) => void }).setState({
    accessToken: 'token',
    isAuthenticated: true,
  });
});

describe('QnaChatSection (통합)', () => {
  it('빈 상태에서 추천 칩이 노출된다', () => {
    render(<QnaChatSection isAuthenticated={true} policyId={1} onLoginPrompt={vi.fn()} />);
    expect(screen.getByText('신청 자격이 어떻게 되나요?')).toBeInTheDocument();
  });

  it('칩 클릭 → user 메시지 push → assistant streaming 인디케이터', async () => {
    let cb: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, c) => {
      cb = c;
    });

    render(<QnaChatSection isAuthenticated={true} policyId={1} onLoginPrompt={vi.fn()} />);
    fireEvent.click(screen.getByText('신청 자격이 어떻게 되나요?'));

    expect(screen.getByText('신청 자격이 어떻게 되나요?')).toBeInTheDocument();
    await waitFor(() => expect(cb).not.toBeNull());
    expect(screen.getByRole('status', { name: '답변 준비 중' })).toBeInTheDocument();
  });

  it('미인증 사용자가 칩 클릭 → onLoginPrompt 호출, 메시지 push 안 됨', () => {
    const onLoginPrompt = vi.fn();
    render(<QnaChatSection isAuthenticated={false} policyId={1} onLoginPrompt={onLoginPrompt} />);
    fireEvent.click(screen.getByText('신청 자격이 어떻게 되나요?'));
    expect(onLoginPrompt).toHaveBeenCalledTimes(1);
    expect(qnaApi.fetchQnaAnswer).not.toHaveBeenCalled();
  });

  it('전체 플로우: chunks → done → 답변 + 출처 표시', async () => {
    let cb: qnaApi.QnaCallbacks | null = null;
    vi.mocked(qnaApi.fetchQnaAnswer).mockImplementation(async (_id, _q, c) => {
      cb = c;
    });

    render(<QnaChatSection isAuthenticated={true} policyId={1} onLoginPrompt={vi.fn()} />);
    fireEvent.click(screen.getByText('어떤 서류가 필요한가요?'));
    await waitFor(() => expect(cb).not.toBeNull());

    fireEvent.click(document.body); // no-op
    cb!.onChunk('주민등록등본');
    cb!.onChunk('이 필요합니다.');
    cb!.onSources(['청년정책 시행계획 p.20']);
    cb!.onDone();

    await waitFor(() => {
      expect(screen.getByText(/주민등록등본이 필요합니다\./)).toBeInTheDocument();
      expect(screen.getByText('청년정책 시행계획 p.20')).toBeInTheDocument();
    });
  });

  it('미인증 사용자가 composer 포커스 → onLoginPrompt 호출', () => {
    const onLoginPrompt = vi.fn();
    render(<QnaChatSection isAuthenticated={false} policyId={1} onLoginPrompt={onLoginPrompt} />);
    fireEvent.focus(screen.getByPlaceholderText('로그인 후 질문할 수 있어요'));
    expect(onLoginPrompt).toHaveBeenCalledTimes(1);
  });
});
