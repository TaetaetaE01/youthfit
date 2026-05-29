import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { PolicyProcessingItem } from '@/types/adminPolicyProcessing';
import { PolicyProcessingTable } from '../PolicyProcessingTable';

function mockItem(policyId: number, overrides: Partial<PolicyProcessingItem> = {}): PolicyProcessingItem {
  return {
    policyId,
    title: '월세 지원',
    region: '서울',
    completeness: 'COMPLETE',
    stepStatuses: {
      INGESTION: 'SUCCESS',
      ENRICHMENT: 'SUCCESS',
      GUIDE: 'SUCCESS',
      RULE: 'SUCCESS',
      RAG_INDEXING: 'SUCCESS',
    },
    attachments: { total: 3, extracted: 3, embedded: 3 },
    references: { total: 0, succeeded: 0 },
    updatedAt: '2026-05-29T03:00:00Z',
    ...overrides,
  };
}

describe('PolicyProcessingTable', () => {
  it('renders 5 step dots per row', () => {
    render(
      <PolicyProcessingTable
        items={[mockItem(100)]}
        expandedIds={new Set()}
        onToggle={() => {}}
      />,
    );
    expect(screen.getAllByTestId('step-dot')).toHaveLength(5);
    expect(screen.getByText('월세 지원')).toBeInTheDocument();
  });

  it('toggles expansion on row click', () => {
    const onToggle = vi.fn();
    render(
      <PolicyProcessingTable
        items={[mockItem(100)]}
        expandedIds={new Set()}
        onToggle={onToggle}
      />,
    );
    fireEvent.click(screen.getByText('월세 지원'));
    expect(onToggle).toHaveBeenCalledWith(100);
  });
});
