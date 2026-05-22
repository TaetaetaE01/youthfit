import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useRagPreview } from '../useRagPreview';
import * as api from '@/apis/adminRag.api';

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client: qc }, children);
}

describe('useRagPreview', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('수동 호출 전엔 fetch 가 발생하지 않는다', () => {
    const spy = vi.spyOn(api, 'ragPreview').mockResolvedValue({} as any);
    renderHook(() => useRagPreview(), { wrapper });
    expect(spy).not.toHaveBeenCalled();
  });

  it('성공 시 데이터를 반환한다', async () => {
    const resp = {
      policyId: 1, query: 'q', extractedKeywords: [],
      baseline: {} as any, candidate: {} as any, diff: { rankChanges: [] },
    };
    vi.spyOn(api, 'ragPreview').mockResolvedValue(resp);

    const { result } = renderHook(() => useRagPreview(), { wrapper });
    result.current.mutate({ policyId: 1, query: 'q', candidate: {} });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(resp);
  });

  it('실패 시 에러를 노출한다', async () => {
    vi.spyOn(api, 'ragPreview').mockRejectedValue(new Error('boom'));

    const { result } = renderHook(() => useRagPreview(), { wrapper });
    result.current.mutate({ policyId: 1, query: 'q', candidate: {} });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('boom');
  });
});
