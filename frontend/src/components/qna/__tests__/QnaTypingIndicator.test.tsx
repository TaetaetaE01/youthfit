import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { QnaTypingIndicator } from '../QnaTypingIndicator';

describe('QnaTypingIndicator', () => {
  it('3 dot 으로 status 라벨을 가진다', () => {
    const { container } = render(<QnaTypingIndicator />);
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', '답변 준비 중');
    expect(container.querySelectorAll('span[aria-hidden="true"]')).toHaveLength(3);
  });
});
