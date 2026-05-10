import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QnaMessageBubble } from '../QnaMessageBubble';
import type { QnaMessage } from '@/types/qna';

describe('QnaMessageBubble', () => {
  const baseAssistant: QnaMessage = {
    id: 'a1',
    role: 'assistant',
    content: '**만 19세** 이상 청년이면 가능합니다.',
    status: 'done',
    questionRef: 'u1',
  };
  const baseUser: QnaMessage = {
    id: 'u1',
    role: 'user',
    content: '신청 자격은?',
    status: 'done',
  };

  it('user 메시지는 본문을 평문으로 렌더한다', () => {
    render(
      <QnaMessageBubble
        message={baseUser}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    expect(screen.getByText('신청 자격은?')).toBeInTheDocument();
  });

  it('assistant 메시지는 마크다운 강조를 렌더한다', () => {
    render(
      <QnaMessageBubble
        message={baseAssistant}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    const strong = screen.getByText('만 19세');
    expect(strong.tagName).toBe('STRONG');
  });

  it('출처가 있으면 출처 박스 렌더', () => {
    render(
      <QnaMessageBubble
        message={{
          ...baseAssistant,
          sources: [
            {
              policyId: 1,
              attachmentLabel: '청년정책 시행계획',
              pageStart: 12,
              pageEnd: 13,
              excerpt: null,
            },
          ],
        }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    expect(screen.getByText('출처')).toBeInTheDocument();
    expect(screen.getByText(/청년정책 시행계획/)).toBeInTheDocument();
    expect(screen.getByText(/p\.12-13/)).toBeInTheDocument();
  });

  it('복사 버튼 클릭 시 onCopy 호출', () => {
    const onCopy = vi.fn().mockResolvedValue(undefined);
    render(
      <QnaMessageBubble
        message={baseAssistant}
        onCopy={onCopy}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '답변 복사' }));
    expect(onCopy).toHaveBeenCalledWith(baseAssistant.content);
  });

  it('error status 면 재시도 버튼 노출', () => {
    render(
      <QnaMessageBubble
        message={{ ...baseAssistant, status: 'error', content: '오류' }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: '답변 재생성' })).toBeInTheDocument();
  });

  it('재시도 버튼 클릭 시 onRetry 호출', () => {
    const onRetry = vi.fn();
    render(
      <QnaMessageBubble
        message={{ ...baseAssistant, status: 'error' }}
        onCopy={vi.fn()}
        onRetry={onRetry}
        onFollowUpPick={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '답변 재생성' }));
    expect(onRetry).toHaveBeenCalledWith('a1');
  });

  it('streaming 상태이면 깜빡이는 커서를 표시한다', () => {
    const { container } = render(
      <QnaMessageBubble
        message={{ ...baseAssistant, status: 'streaming', content: '안녕' }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    expect(container.querySelector('[data-qna-cursor]')).not.toBeNull();
  });

  it('user 메시지에는 복사/재시도 버튼이 없다', () => {
    render(
      <QnaMessageBubble
        message={baseUser}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    expect(screen.queryByRole('button', { name: '답변 복사' })).toBeNull();
    expect(screen.queryByRole('button', { name: '답변 재생성' })).toBeNull();
  });

  it('error status 에서는 sources 가 있어도 출처 박스를 렌더하지 않는다', () => {
    render(
      <QnaMessageBubble
        message={{
          ...baseAssistant,
          status: 'error',
          content: '오류',
          sources: [
            {
              policyId: 1,
              attachmentLabel: '청년정책 시행계획',
              pageStart: 12,
              pageEnd: 13,
              excerpt: null,
            },
          ],
        }}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
        onFollowUpPick={vi.fn()}
      />,
    );
    expect(screen.queryByText('출처')).toBeNull();
    expect(screen.queryByText(/청년정책 시행계획/)).toBeNull();
  });

  describe('follow-up 칩', () => {
    it('followUpQuestions 가 있고 status=done 이면 칩과 헤더가 렌더된다', () => {
      const onFollowUpPick = vi.fn();
      render(
        <QnaMessageBubble
          message={{
            ...baseAssistant,
            followUpQuestions: ['후속A', '후속B'],
          }}
          onCopy={vi.fn()}
          onRetry={vi.fn()}
          onFollowUpPick={onFollowUpPick}
        />,
      );
      expect(screen.getByText('이어서 물어볼 만한 질문')).toBeInTheDocument();
      expect(screen.getByText('후속A')).toBeInTheDocument();
      expect(screen.getByText('후속B')).toBeInTheDocument();
    });

    it('칩 클릭 시 onFollowUpPick 이 호출된다', () => {
      const onFollowUpPick = vi.fn();
      render(
        <QnaMessageBubble
          message={{
            ...baseAssistant,
            followUpQuestions: ['후속A'],
          }}
          onCopy={vi.fn()}
          onRetry={vi.fn()}
          onFollowUpPick={onFollowUpPick}
        />,
      );
      fireEvent.click(screen.getByText('후속A'));
      expect(onFollowUpPick).toHaveBeenCalledWith('후속A');
    });

    it('followUpQuestions 가 없으면 칩 영역 자체가 렌더되지 않는다', () => {
      render(
        <QnaMessageBubble
          message={baseAssistant}
          onCopy={vi.fn()}
          onRetry={vi.fn()}
          onFollowUpPick={vi.fn()}
        />,
      );
      expect(screen.queryByText('이어서 물어볼 만한 질문')).not.toBeInTheDocument();
    });

    it('streaming 상태에서는 칩이 렌더되지 않는다 (status=done 만)', () => {
      render(
        <QnaMessageBubble
          message={{
            ...baseAssistant,
            status: 'streaming',
            content: '답변 일부',
            followUpQuestions: ['후속A'],
          }}
          onCopy={vi.fn()}
          onRetry={vi.fn()}
          onFollowUpPick={vi.fn()}
        />,
      );
      expect(screen.queryByText('후속A')).not.toBeInTheDocument();
    });
  });
});
