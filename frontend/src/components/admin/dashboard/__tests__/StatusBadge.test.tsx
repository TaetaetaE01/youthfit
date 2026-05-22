import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from '../StatusBadge';

describe('StatusBadge', () => {
  it('renders OK in green', () => {
    const { container } = render(<StatusBadge status="OK" />);
    expect(screen.getByText(/정상/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass('bg-success-50');
  });

  it('renders WARN in amber', () => {
    const { container } = render(<StatusBadge status="WARN" />);
    expect(screen.getByText(/주의/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass('bg-amber-50');
  });

  it('renders CRITICAL in error', () => {
    const { container } = render(<StatusBadge status="CRITICAL" />);
    expect(screen.getByText(/확인 필요/)).toBeInTheDocument();
    expect(container.firstChild).toHaveClass('bg-error-50');
  });
});
