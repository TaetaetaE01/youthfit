import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import type { PolicyProcessingStats } from '@/types/adminPolicyProcessing';
import { PolicyProcessingKpiCards } from '../PolicyProcessingKpiCards';

describe('PolicyProcessingKpiCards', () => {
  const stats: PolicyProcessingStats = {
    totalCount: 247,
    completeCount: 182,
    partialCount: 51,
    incompleteCount: 14,
    recent24hCount: 8,
  };

  it('renders all four KPIs with values', () => {
    render(<PolicyProcessingKpiCards stats={stats} />);
    expect(screen.getByText('182')).toBeInTheDocument();
    expect(screen.getByText('51')).toBeInTheDocument();
    expect(screen.getByText('14')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
  });

  it('shows percentage for complete count', () => {
    render(<PolicyProcessingKpiCards stats={stats} />);
    expect(screen.getByText(/74%/)).toBeInTheDocument();
  });
});
