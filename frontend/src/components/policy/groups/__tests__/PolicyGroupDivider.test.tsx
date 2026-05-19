import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyGroupDivider } from '../PolicyGroupDivider';

describe('PolicyGroupDivider', () => {
  it('다음 그룹 라벨을 표시한다', () => {
    render(<PolicyGroupDivider nextLabel="받는 혜택" />);
    expect(screen.getByText(/다음 · 받는 혜택/)).toBeInTheDocument();
  });
});
