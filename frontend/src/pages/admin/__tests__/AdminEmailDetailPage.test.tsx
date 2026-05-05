import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import AdminEmailDetailPage from '../AdminEmailDetailPage';
import * as api from '@/apis/admin.email.api';

vi.mock('@/apis/admin.email.api');

const baseDetail = {
  id: 42, notificationHistoryId: 100, recipient: 'u@ex.com', recipientUserId: 7,
  emailType: 'DEADLINE' as const, subject: 'S', inputPayloadJson: '{"policyId":1}',
  sesMessageId: 'msg', status: 'FAILED' as const,
  errorCode: 'X', errorMessage: 'boom', bounceType: null,
  sentAt: '2026-05-05T10:00:00', updatedAt: '2026-05-05T10:00:00',
};

const renderPage = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/admin/email/42']}>
        <Routes><Route path="/admin/email/:attemptId" element={<AdminEmailDetailPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>);
};

describe('AdminEmailDetailPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('FAILED 상태에서 재발송 버튼 활성', async () => {
    vi.mocked(api.getEmailAttempt).mockResolvedValue(baseDetail);
    renderPage();
    expect(await screen.findByText(/^재발송$/)).toBeEnabled();
  });

  it('SENT 상태에서는 재발송 버튼 안 보임', async () => {
    vi.mocked(api.getEmailAttempt).mockResolvedValue({ ...baseDetail, status: 'SENT' });
    renderPage();
    await screen.findByText('S');
    expect(screen.queryByText(/^재발송$/)).toBeNull();
  });

  it('미리보기는 클릭 전엔 호출 안 함, 클릭 후 호출', async () => {
    vi.mocked(api.getEmailAttempt).mockResolvedValue(baseDetail);
    vi.mocked(api.getEmailPreview).mockResolvedValue({ subject: 'S', htmlBody: '<p>x</p>', textBody: 'x' });
    renderPage();
    await screen.findByText('본문 미리보기 불러오기');
    expect(api.getEmailPreview).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText('본문 미리보기 불러오기'));
    await waitFor(() => expect(api.getEmailPreview).toHaveBeenCalledWith(42));
  });
});
