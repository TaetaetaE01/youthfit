import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import AdminRagPreviewPage from '../AdminRagPreviewPage';
import * as api from '@/apis/adminRag.api';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{ui}</QueryClientProvider>;
}

const mockResp: api.RagPreviewResponse = {
  policyId: 1, query: '주거',
  extractedKeywords: ['주거'],
  baseline: {
    config: { hybridEnabled: true, topNPerSearch: 20, rrfK: 60, trigramThreshold: 0.1,
              keywordBoostEnabled: true, maxKeywords: 5 },
    vectorTopN: [], trigramTopN: [],
    merged: [{ chunkId: 1, chunkIndex: 0, distance: 0.2, rrfScore: 0.03, rank: 1, preview: 'b1' }],
    tookMs: 142,
  },
  candidate: {
    config: { hybridEnabled: true, topNPerSearch: 30, rrfK: 30, trigramThreshold: 0.15,
              keywordBoostEnabled: true, maxKeywords: 7 },
    vectorTopN: [], trigramTopN: [],
    merged: [{ chunkId: 2, chunkIndex: 1, distance: 0.18, rrfScore: 0.04, rank: 1, preview: 'c1' }],
    tookMs: 167,
  },
  diff: { rankChanges: [{ chunkId: 2, baselineRank: null, candidateRank: 1, delta: 'NEW' }] },
};

describe('AdminRagPreviewPage', () => {
  it('실행 → 양쪽 패널 렌더 + diff 배지', async () => {
    vi.spyOn(api, 'ragPreview').mockResolvedValue(mockResp);

    render(wrap(<AdminRagPreviewPage />));
    fireEvent.change(screen.getByLabelText('정책 ID'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('쿼리'), { target: { value: '주거' } });
    fireEvent.click(screen.getByText(/비교 실행/));

    await waitFor(() => expect(screen.getByText('b1')).toBeInTheDocument());
    expect(screen.getByText('c1')).toBeInTheDocument();
    expect(screen.getByText('NEW')).toBeInTheDocument();
  });

  it('500 응답 시 에러 노출', async () => {
    vi.spyOn(api, 'ragPreview').mockRejectedValue(new Error('서버 오류'));

    render(wrap(<AdminRagPreviewPage />));
    fireEvent.change(screen.getByLabelText('정책 ID'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('쿼리'), { target: { value: '주거' } });
    fireEvent.click(screen.getByText(/비교 실행/));

    await waitFor(() => expect(screen.getByText('서버 오류')).toBeInTheDocument());
  });
});
