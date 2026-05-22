import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { DashboardOverview } from '@/types/adminDashboard';
import AdminDashboardPage from '../AdminDashboardPage';

vi.mock('@/apis/adminDashboard.api', () => ({
  getDashboardOverview: vi.fn(),
}));

import { getDashboardOverview } from '@/apis/adminDashboard.api';

const mockedGetOverview = getDashboardOverview as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const baseOverview: DashboardOverview = {
  generatedAt: new Date().toISOString(),
  actionItems: [],
  areas: [
    {
      key: 'ingestion',
      label: '수집',
      status: 'OK',
      summary: '최근 7일 정상 수신',
      sparkline: [1, 2, 3, 4, 5, 6, 7],
      deeplink: '/admin/ingestion',
    },
  ],
};

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    mockedGetOverview.mockReset();
  });

  it('초기 로딩 중에는 스켈레톤을 노출한다', async () => {
    mockedGetOverview.mockImplementation(
      () =>
        new Promise<DashboardOverview>((resolve) =>
          setTimeout(() => resolve(baseOverview), 50),
        ),
    );

    renderPage();

    expect(screen.getByTestId('admin-dashboard-skeleton')).toBeInTheDocument();
  });

  it('actionItems 가 비어 있으면 AllClearBanner 를 노출한다', async () => {
    mockedGetOverview.mockResolvedValue(baseOverview);

    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/현재 이상 없음/)).toBeInTheDocument(),
    );
  });

  it('HIGH actionItem 이 있으면 Action Required 헤더와 항목 제목을 노출한다', async () => {
    mockedGetOverview.mockResolvedValue({
      ...baseOverview,
      actionItems: [
        {
          code: 'INGESTION_FAILURE',
          severity: 'HIGH',
          title: '수집 파이프라인 실패',
          detail: '최근 1시간 내 실패율 30%',
          deeplink: '/admin/ingestion',
          detectedAt: new Date().toISOString(),
        },
      ],
    });

    renderPage();

    await waitFor(() =>
      expect(screen.getByText('수집 파이프라인 실패')).toBeInTheDocument(),
    );
    expect(screen.getByText(/Action Required \(1\)/)).toBeInTheDocument();
  });

  it('조회 실패 시 에러 메시지와 다시 시도 버튼을 노출한다', async () => {
    mockedGetOverview.mockRejectedValue(new Error('boom'));

    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/불러오지 못했습니다/)).toBeInTheDocument(),
    );

    const retryButton = screen.getByRole('button', { name: /다시 시도/ });
    expect(retryButton).toBeInTheDocument();

    mockedGetOverview.mockResolvedValueOnce(baseOverview);
    await userEvent.click(retryButton);

    await waitFor(() =>
      expect(screen.getByText(/현재 이상 없음/)).toBeInTheDocument(),
    );
  });
});
