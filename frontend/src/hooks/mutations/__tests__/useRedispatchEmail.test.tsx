import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi } from 'vitest';
import { useRedispatchEmail } from '../useRedispatchEmail';
import * as api from '@/apis/admin.email.api';
import type { ReactNode } from 'react';

describe('useRedispatchEmail', () => {
  it('성공 시 list/stats 캐시 invalidate', async () => {
    vi.spyOn(api, 'redispatchEmail').mockResolvedValue({ newAttemptId: 99 });
    const qc = new QueryClient();
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    const wrapper = ({ children }: { children: ReactNode }) =>
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>;

    const { result } = renderHook(() => useRedispatchEmail(), { wrapper });
    result.current.mutate(50);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['admin', 'email', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['admin', 'email', 'stats'] });
  });
});
