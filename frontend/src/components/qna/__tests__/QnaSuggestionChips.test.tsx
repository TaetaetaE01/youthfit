import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QnaSuggestionChips } from '../QnaSuggestionChips';

describe('QnaSuggestionChips', () => {
  it('4 개 칩을 button 으로 렌더한다', () => {
    render(<QnaSuggestionChips onPick={vi.fn()} />);
    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(4);
  });

  it('칩 클릭 시 onPick 에 텍스트가 전달된다', () => {
    const onPick = vi.fn();
    render(<QnaSuggestionChips onPick={onPick} />);
    fireEvent.click(screen.getByText('신청 자격이 어떻게 되나요?'));
    expect(onPick).toHaveBeenCalledWith('신청 자격이 어떻게 되나요?');
  });
});
