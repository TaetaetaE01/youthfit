import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ResultTabs } from '../ResultTabs';
import type { PreviewSide } from '@/types/ragPreview';

const side: PreviewSide = {
  config: {
    hybridEnabled: true, topNPerSearch: 20, rrfK: 60,
    trigramThreshold: 0.1, keywordBoostEnabled: true, maxKeywords: 5,
  },
  vectorTopN: [{ chunkId: 1, chunkIndex: 0, distance: 0.2, preview: 'v1' }],
  trigramTopN: [{ chunkId: 2, chunkIndex: 1, distance: 0.5, preview: 't1' }],
  merged: [{ chunkId: 1, chunkIndex: 0, distance: 0.2, rrfScore: 0.03, rank: 1, preview: 'm1' }],
  tookMs: 100,
};

describe('ResultTabs', () => {
  it('탭 전환', () => {
    render(<ResultTabs side={side} />);
    expect(screen.getByText(/m1/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('vector'));
    expect(screen.getByText(/v1/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('trigram'));
    expect(screen.getByText(/t1/)).toBeInTheDocument();
  });

  it('빈 결과 시 empty state', () => {
    const empty: PreviewSide = { ...side, merged: [], vectorTopN: [], trigramTopN: [] };
    render(<ResultTabs side={empty} />);
    expect(screen.getByText('결과 없음')).toBeInTheDocument();
  });

  it('hybrid 비활성 시 trigram 탭 안내', () => {
    const off: PreviewSide = { ...side, config: { ...side.config, hybridEnabled: false } };
    render(<ResultTabs side={off} />);
    fireEvent.click(screen.getByText('trigram'));
    expect(screen.getByText(/hybrid 비활성/)).toBeInTheDocument();
  });
});
