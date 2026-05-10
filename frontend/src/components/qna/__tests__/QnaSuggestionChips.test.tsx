import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QnaSuggestionChips } from '../QnaSuggestionChips';

describe('QnaSuggestionChips', () => {
  it('questions prop 미지정 시 default 4개를 렌더한다', () => {
    render(<QnaSuggestionChips onPick={vi.fn()} />);

    expect(screen.getByText('신청 자격이 어떻게 되나요?')).toBeInTheDocument();
    expect(screen.getByText('어떤 서류가 필요한가요?')).toBeInTheDocument();
    expect(screen.getByText('신청은 언제까지인가요?')).toBeInTheDocument();
    expect(screen.getByText('지원 금액은 얼마인가요?')).toBeInTheDocument();
  });

  it('questions prop 지정 시 그것만 렌더한다', () => {
    render(
      <QnaSuggestionChips questions={['후속A', '후속B']} onPick={vi.fn()} />,
    );

    expect(screen.getByText('후속A')).toBeInTheDocument();
    expect(screen.getByText('후속B')).toBeInTheDocument();
    expect(screen.queryByText('신청 자격이 어떻게 되나요?')).not.toBeInTheDocument();
  });

  it('칩 클릭 시 onPick 이 해당 질문으로 호출된다', () => {
    const onPick = vi.fn();
    render(
      <QnaSuggestionChips questions={['후속A', '후속B']} onPick={onPick} />,
    );

    fireEvent.click(screen.getByText('후속B'));

    expect(onPick).toHaveBeenCalledWith('후속B');
  });

  it('questions 가 빈 배열이면 아무것도 렌더하지 않는다', () => {
    const { container } = render(
      <QnaSuggestionChips questions={[]} onPick={vi.fn()} />,
    );
    expect(container.querySelector('button')).toBeNull();
  });
});
