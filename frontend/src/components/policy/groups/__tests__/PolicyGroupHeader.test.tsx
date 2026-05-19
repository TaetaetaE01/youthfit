import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyGroupHeader } from '../PolicyGroupHeader';
import { POLICY_GROUPS } from '../policyGroups';

describe('PolicyGroupHeader', () => {
  it('그룹 라벨과 설명을 렌더한다', () => {
    const eligibility = POLICY_GROUPS[0];
    render(<PolicyGroupHeader group={eligibility} />);
    expect(screen.getByRole('heading', { level: 2, name: eligibility.label })).toBeInTheDocument();
    expect(screen.getByText(eligibility.description)).toBeInTheDocument();
  });

  it('section에 그룹 id를 anchor로 부여한다', () => {
    const benefits = POLICY_GROUPS[1];
    const { container } = render(<PolicyGroupHeader group={benefits} />);
    expect(container.querySelector('section')?.id).toBe('benefits');
  });

  it('tone별 배경 클래스가 다르게 적용된다', () => {
    const { container, rerender } = render(<PolicyGroupHeader group={POLICY_GROUPS[0]} />);
    const brandIcon = container.querySelector('[data-icon-box]');
    expect(brandIcon?.className).toMatch(/bg-brand-100/);

    rerender(<PolicyGroupHeader group={POLICY_GROUPS[1]} />);
    const amberIcon = container.querySelector('[data-icon-box]');
    expect(amberIcon?.className).toMatch(/bg-amber-100/);
  });
});
