import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { QnaCacheResultBadge } from '../QnaCacheResultBadge';

describe('QnaCacheResultBadge', () => {
  it('HIT은 초록 뱃지', () => {
    const { container } = render(<QnaCacheResultBadge result="HIT" />);
    expect(screen.getByText('HIT')).toBeInTheDocument();
    expect(container.firstChild).toHaveClass(/emerald|green|success/);
  });
  it('BELOW_THRESHOLD는 주황 뱃지', () => {
    const { container } = render(<QnaCacheResultBadge result="BELOW_THRESHOLD" />);
    expect(screen.getByText('BELOW')).toBeInTheDocument();
    expect(container.firstChild).toHaveClass(/amber|orange|warning/);
  });
  it('MISS는 빨강 뱃지', () => {
    const { container } = render(<QnaCacheResultBadge result="MISS" />);
    expect(screen.getByText('MISS')).toBeInTheDocument();
    expect(container.firstChild).toHaveClass(/red|danger|error/);
  });
});
