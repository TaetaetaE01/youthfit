import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import AdminEmailLogPage from '../AdminEmailLogPage';

vi.mock('@/apis/admin.email.api', () => ({
  listEmailAttempts: vi.fn().mockResolvedValue({
    content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
  }),
  getEmailKpi: vi.fn().mockResolvedValue({
    today: { sent: 0, delivered: 0, bounced: 0, failed: 0 },
    thisWeek: { sent: 0, delivered: 0, bounced: 0, failed: 0 },
    successRate: 0,
  }),
  getEmailDailyStats: vi.fn().mockResolvedValue([]),
}));

describe('AdminEmailLogPage', () => {
  it('빈 상태 메시지 노출', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <AdminEmailLogPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() =>
      expect(screen.getByText(/조건에 맞는 발송 이력이 없습니다/)).toBeInTheDocument(),
    );
  });
});
