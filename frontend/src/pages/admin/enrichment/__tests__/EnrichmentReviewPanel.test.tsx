import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EnrichmentReviewPanel } from '../EnrichmentReviewPanel';
import * as api from '@/apis/adminEnrichment';
import type { PolicyEnrichmentDetail, EnrichmentJobView, AttachmentView } from '@/types/adminEnrichment';

function renderWithClient(ui: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function baseDetail(overrides: Partial<PolicyEnrichmentDetail> = {}): PolicyEnrichmentDetail {
  return {
    policyId: 1,
    title: '테스트 정책',
    detailLevel: 'MEDIUM',
    enrichment: null,
    referenceSites: [],
    recentJobs: [],
    attachments: [],
    needsReview: true,
    ...overrides,
  };
}

describe('EnrichmentReviewPanel — 재크롤 버튼 비활성 조건', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  test('referenceSites 비어있으면 비활성', async () => {
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(baseDetail());
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).toBeDisabled();
  });

  test('PENDING 잡이 있으면 비활성', async () => {
    const pendingJob: EnrichmentJobView = {
      id: 1,
      status: 'PENDING',
      policyId: 1,
      attempt: 1,
      requestedBy: 'x',
      errorMessage: null,
      requestedAt: new Date().toISOString(),
      startedAt: null,
      finishedAt: null,
      requestedUrls: [],
    };
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(
      baseDetail({
        referenceSites: [{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }],
        recentJobs: [pendingJob],
      }),
    );
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).toBeDisabled();
  });

  test('referenceSites 있고 진행중 잡 없으면 활성', async () => {
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(
      baseDetail({
        referenceSites: [{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }],
        recentJobs: [],
      }),
    );
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).not.toBeDisabled();
  });

  test('첨부 다운로드 실패 사유를 표시한다', async () => {
    const failed: AttachmentView = {
      id: 10,
      name: '공고문.hwpx',
      mediaType: 'application/x-hwp',
      status: 'FAILED',
      skipReason: null,
      error: 'DownloadException <- InvalidMediaTypeException: does not contain /',
      retryCount: 3,
      hasText: false,
    };
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(baseDetail({ attachments: [failed] }));
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);

    expect(await screen.findByText(/공고문\.hwpx/)).toBeInTheDocument();
    expect(screen.getByText(/InvalidMediaTypeException/)).toBeInTheDocument();
  });

  test('첨부 건너뜀(SKIPPED) 사유를 표시한다', async () => {
    const skipped: AttachmentView = {
      id: 11,
      name: '스캔본.pdf',
      mediaType: 'application/pdf',
      status: 'SKIPPED',
      skipReason: 'SCANNED_PDF',
      error: null,
      retryCount: 0,
      hasText: false,
    };
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(baseDetail({ attachments: [skipped] }));
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);

    expect(await screen.findByText(/스캔본\.pdf/)).toBeInTheDocument();
    expect(screen.getByText(/SCANNED_PDF/)).toBeInTheDocument();
  });

  test('attachments 필드가 없는(구버전 백엔드) 응답에도 패널이 크래시하지 않는다', async () => {
    // 백/프 독립 배포 — 프런트가 먼저 배포되면 구버전 응답에 attachments 가 없다.
    const detail = baseDetail();
    delete (detail as Partial<PolicyEnrichmentDetail>).attachments;
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(detail as PolicyEnrichmentDetail);
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);

    expect(await screen.findByRole('button', { name: /재크롤/ })).toBeInTheDocument();
  });

  test('RUNNING 잡이 있으면 비활성', async () => {
    const runningJob: EnrichmentJobView = {
      id: 1,
      status: 'RUNNING',
      policyId: 1,
      attempt: 1,
      requestedBy: 'x',
      errorMessage: null,
      requestedAt: new Date().toISOString(),
      startedAt: new Date().toISOString(),
      finishedAt: null,
      requestedUrls: [],
    };
    vi.spyOn(api, 'fetchPolicyDetail').mockResolvedValue(
      baseDetail({
        referenceSites: [{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }],
        recentJobs: [runningJob],
      }),
    );
    renderWithClient(<EnrichmentReviewPanel policyId={1} />);
    const btn = await screen.findByRole('button', { name: /재크롤/ });
    expect(btn).toBeDisabled();
  });
});
