import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { PolicyMobileNav } from '../PolicyMobileNav';

beforeEach(() => {
  Element.prototype.scrollIntoView = () => {};
});

describe('PolicyMobileNav', () => {
  it('4개 칩을 렌더한다', () => {
    render(<PolicyMobileNav activeId="eligibility" visible />);
    expect(screen.getByText('받을 수 있는 사람')).toBeInTheDocument();
    expect(screen.getByText('받는 혜택')).toBeInTheDocument();
    expect(screen.getByText('신청하기')).toBeInTheDocument();
    expect(screen.getByText('더 알아보기')).toBeInTheDocument();
  });

  it('visible=false면 invisible 클래스가 적용된다', () => {
    const { container } = render(<PolicyMobileNav activeId="eligibility" visible={false} />);
    expect(container.firstChild).toHaveClass('invisible');
  });

  it('active 칩은 aria-current 가 부여된다', () => {
    render(<PolicyMobileNav activeId="benefits" visible />);
    const links = screen.getAllByRole('link');
    const active = links.find((el) => el.getAttribute('aria-current') === 'location');
    expect(active?.textContent).toMatch(/받는 혜택/);
  });
});
