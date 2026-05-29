import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PolicyProcessingFilters } from '../PolicyProcessingFilters';
import type { PolicyProcessingListParams } from '@/types/adminPolicyProcessing';

describe('PolicyProcessingFilters', () => {
  const baseParams: PolicyProcessingListParams = {
    filter: 'ALL',
    sort: 'UPDATED_DESC',
    page: 0,
    size: 50,
  };

  it('calls onChange when chip clicked', () => {
    const onChange = vi.fn();
    render(
      <PolicyProcessingFilters
        params={baseParams}
        onChange={onChange}
        chipCounts={{ ALL: 247, INCOMPLETE: 14 }}
      />,
    );
    fireEvent.click(screen.getByText('미흡만 14'));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ filter: 'INCOMPLETE' }),
    );
  });

  it('calls onChange when search input changes', () => {
    const onChange = vi.fn();
    render(
      <PolicyProcessingFilters
        params={baseParams}
        onChange={onChange}
        chipCounts={{}}
      />,
    );
    fireEvent.change(screen.getByPlaceholderText(/검색/), {
      target: { value: '월세' },
    });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ q: '월세' }),
    );
  });
});
