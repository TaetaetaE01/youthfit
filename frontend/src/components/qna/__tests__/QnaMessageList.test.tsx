import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeAll } from 'vitest';
import { QnaMessageList } from '../QnaMessageList';
import type { QnaMessage } from '@/types/qna';

beforeAll(() => {
  Element.prototype.scrollTo = vi.fn();
  Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
    configurable: true,
    get: function () {
      return Number((this as HTMLElement).getAttribute('data-scroll-height') ?? '500');
    },
  });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
    configurable: true,
    get: function () {
      return Number((this as HTMLElement).getAttribute('data-client-height') ?? '300');
    },
  });
});

describe('QnaMessageList', () => {
  const messages: QnaMessage[] = [
    { id: 'u1', role: 'user', content: 'q', status: 'done' },
    { id: 'a1', role: 'assistant', content: '답변', status: 'done', questionRef: 'u1' },
  ];

  it('메시지를 순서대로 렌더한다', () => {
    render(<QnaMessageList messages={messages} onCopy={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.getByText('q')).toBeInTheDocument();
    expect(screen.getByText('답변')).toBeInTheDocument();
  });

  it('streaming 이고 content 빈 마지막 assistant 자리에 typing indicator 표시', () => {
    const streaming: QnaMessage[] = [
      { id: 'u1', role: 'user', content: 'q', status: 'done' },
      { id: 'a1', role: 'assistant', content: '', status: 'streaming', questionRef: 'u1' },
    ];
    render(<QnaMessageList messages={streaming} onCopy={vi.fn()} onRetry={vi.fn()} />);
    expect(screen.getByRole('status', { name: '답변 준비 중' })).toBeInTheDocument();
  });

  it('스크롤이 위에 있고 메시지 push 되면 jump 버튼 노출', () => {
    const { container, rerender } = render(
      <QnaMessageList messages={messages} onCopy={vi.fn()} onRetry={vi.fn()} />,
    );
    const scroller = container.querySelector('[data-qna-scroller]') as HTMLElement;
    scroller.setAttribute('data-scroll-height', '1000');
    scroller.setAttribute('data-client-height', '300');
    Object.defineProperty(scroller, 'scrollTop', { configurable: true, value: 100 });
    fireEvent.scroll(scroller);

    rerender(
      <QnaMessageList
        messages={[...messages, { id: 'u2', role: 'user', content: 'q2', status: 'done' }]}
        onCopy={vi.fn()}
        onRetry={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: '가장 최근 메시지로 이동' })).toBeInTheDocument();
  });
});
