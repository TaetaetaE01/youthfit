import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AdminDashboardPage from '../AdminDashboardPage';

vi.mock('@/apis/admin.api', () => ({
  pingAdmin: vi.fn(),
}));

import { pingAdmin } from '@/apis/admin.api';

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminDashboardPage />
    </QueryClientProvider>,
  );
}

describe('AdminDashboardPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('KPI 카드 4개를 노출한다', () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockResolvedValue({ message: 'pong', serverTime: '2026-05-05T10:00:00' });
    renderPage();
    expect(screen.getByText('이메일 발송')).toBeInTheDocument();
    expect(screen.getByText('Q&A 캐시 hit률')).toBeInTheDocument();
    expect(screen.getByText('LLM 비용 (이번주)')).toBeInTheDocument();
    expect(screen.getByText('Ingestion 신규')).toBeInTheDocument();
  });

  it('차트/테이블 placeholder 섹션을 노출한다', () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockResolvedValue({ message: 'pong', serverTime: '2026-05-05T10:00:00' });
    renderPage();
    expect(screen.getByText('발송 / 캐시 추이')).toBeInTheDocument();
    expect(screen.getByText('Q&A 캐시 적중률')).toBeInTheDocument();
    expect(screen.getByText('최근 발송 이메일')).toBeInTheDocument();
    expect(screen.getByText('LLM 비용 분포')).toBeInTheDocument();
  });

  it('ping 성공 시 pong을 표시한다', async () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockResolvedValue({ message: 'pong', serverTime: '2026-05-05T10:00:00' });
    renderPage();
    await waitFor(() => expect(screen.getByText(/pong/i)).toBeInTheDocument());
  });

  it('ping 실패 시 에러 메시지 표시', async () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getByText(/연결 실패/)).toBeInTheDocument());
  });
});
