import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from '../StatusBadge';

describe('StatusBadge', () => {
  it('renders OK with data-status="OK"', () => {
    const { container } = render(<StatusBadge status="OK" />);
    expect(screen.getByText(/정상/)).toBeInTheDocument();
    expect(container.firstChild).toHaveAttribute('data-status', 'OK');
  });

  it('renders WARN with data-status="WARN"', () => {
    const { container } = render(<StatusBadge status="WARN" />);
    expect(screen.getByText(/주의/)).toBeInTheDocument();
    expect(container.firstChild).toHaveAttribute('data-status', 'WARN');
  });

  it('renders CRITICAL with data-status="CRITICAL"', () => {
    const { container } = render(<StatusBadge status="CRITICAL" />);
    expect(screen.getByText(/확인 필요/)).toBeInTheDocument();
    expect(container.firstChild).toHaveAttribute('data-status', 'CRITICAL');
  });
});
