import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyToc } from '../PolicyToc';

describe('PolicyToc', () => {
  it('4개 그룹 라벨을 모두 렌더한다', () => {
    render(<PolicyToc activeId="eligibility" />);
    expect(screen.getByText('받을 수 있는 사람')).toBeInTheDocument();
    expect(screen.getByText('받는 혜택')).toBeInTheDocument();
    expect(screen.getByText('신청하기')).toBeInTheDocument();
    expect(screen.getByText('더 알아보기')).toBeInTheDocument();
  });

  it('active 그룹은 aria-current="location" 으로 표시', () => {
    render(<PolicyToc activeId="benefits" />);
    const items = screen.getAllByRole('link');
    const active = items.find((el) => el.getAttribute('aria-current') === 'location');
    expect(active?.textContent).toMatch(/받는 혜택/);
  });
});
