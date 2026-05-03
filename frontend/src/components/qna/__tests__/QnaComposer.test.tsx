import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QnaComposer } from '../QnaComposer';

describe('QnaComposer', () => {
  it('빈 입력은 전송 버튼이 disabled', () => {
    render(<QnaComposer disabled={false} placeholder="질문" onSubmit={vi.fn()} />);
    expect(screen.getByRole('button', { name: '질문 전송' })).toBeDisabled();
  });

  it('submit 시 trim 된 텍스트로 onSubmit 호출 + 입력값 비움', () => {
    const onSubmit = vi.fn();
    render(<QnaComposer disabled={false} placeholder="질문" onSubmit={onSubmit} />);
    const input = screen.getByPlaceholderText('질문') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '  신청 자격은?  ' } });
    fireEvent.submit(input.closest('form')!);
    expect(onSubmit).toHaveBeenCalledWith('신청 자격은?');
    expect(input.value).toBe('');
  });

  it('IME composition 중 Enter 는 무시한다', () => {
    const onSubmit = vi.fn();
    render(<QnaComposer disabled={false} placeholder="질문" onSubmit={onSubmit} />);
    const input = screen.getByPlaceholderText('질문');
    fireEvent.change(input, { target: { value: '안녕' } });
    fireEvent.keyDown(input, { key: 'Enter', nativeEvent: { isComposing: true } });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('readOnly + onFocus 호출 (disabled 모드)', () => {
    const onFocus = vi.fn();
    render(
      <QnaComposer
        disabled={true}
        placeholder="로그인 후"
        onSubmit={vi.fn()}
        readOnly
        onFocus={onFocus}
      />,
    );
    const input = screen.getByPlaceholderText('로그인 후');
    fireEvent.focus(input);
    expect(onFocus).toHaveBeenCalled();
    expect(input).toHaveAttribute('readonly');
  });
});
