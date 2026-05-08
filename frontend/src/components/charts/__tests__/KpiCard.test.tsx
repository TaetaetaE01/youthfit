import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { KpiCard } from '../KpiCard';

describe('KpiCard', () => {
  it('label, value, hint 렌더', () => {
    render(<KpiCard label="오늘 발송" value={125} hint="어제 대비 +5" />);
    expect(screen.getByText('오늘 발송')).toBeInTheDocument();
    expect(screen.getByText('125')).toBeInTheDocument();
    expect(screen.getByText('어제 대비 +5')).toBeInTheDocument();
  });

  it('tone=danger 시 red/rose 색상 클래스 적용', () => {
    const { container } = render(<KpiCard label="실패" value={3} tone="danger" />);
    expect(
      container.querySelector('.text-rose-600, .text-red-600'),
    ).not.toBeNull();
  });
});
