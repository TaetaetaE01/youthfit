import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { PolicyProcessingDetailPanel } from '../PolicyProcessingDetailPanel';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import type { PolicyProcessingDetail } from '@/types/adminPolicyProcessing';

function renderWithClient(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function baseDetail(overrides: Partial<PolicyProcessingDetail> = {}): PolicyProcessingDetail {
  return {
    policyId: 86,
    title: '테스트 정책',
    completeness: 'PARTIAL',
    steps: [
      {
        step: 'INGESTION',
        status: 'SUCCESS',
        durationMs: 1500,
        attempt: 1,
        reason: null,
        startedAt: null,
        finishedAt: null,
      },
      {
        step: 'RAG_INDEXING',
        status: 'FAILED',
        durationMs: 800,
        attempt: 2,
        reason: 'embedding error',
        startedAt: null,
        finishedAt: null,
      },
    ],
    attachments: [],
    references: [],
    ...overrides,
  };
}

describe('PolicyProcessingDetailPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  test('데이터 로드 후 5단계 + 통합 액션 버튼 3개를 렌더한다', async () => {
    vi.spyOn(adminPolicyProcessingApi, 'detail').mockResolvedValue(baseDetail());

    renderWithClient(<PolicyProcessingDetailPanel policyId={86} />);

    // 5단계 표 — step name 노출
    expect(await screen.findByText('INGESTION')).toBeInTheDocument();
    expect(screen.getByText('RAG_INDEXING')).toBeInTheDocument();

    // 첨부/참조 placeholder
    expect(screen.getByText('첨부 없음')).toBeInTheDocument();
    expect(screen.getByText('Phase D 완료 후 채워짐')).toBeInTheDocument();

    // 통합 액션 3종
    expect(
      screen.getByRole('button', { name: '첨부 임베딩 재인덱싱' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'RAG 본문 재인덱싱' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '전체 재처리' })).toBeInTheDocument();
  });

  test('INGESTION 행에는 재실행(⟲) 버튼이 없고 다른 단계는 있다 — 클릭 시 onRetry 호출', async () => {
    vi.spyOn(adminPolicyProcessingApi, 'detail').mockResolvedValue(baseDetail());
    const onAction = {
      retryStep: vi.fn(),
      reindexAttachment: vi.fn(),
      reindexAllAttachments: vi.fn(),
      reindexRag: vi.fn(),
      reprocess: vi.fn(),
    };

    renderWithClient(
      <PolicyProcessingDetailPanel policyId={86} onAction={onAction} />,
    );

    // RAG_INDEXING 행이 렌더될 때까지 대기
    await screen.findByText('RAG_INDEXING');

    // INGESTION 행 (header 제외 첫 데이터 row) 에는 재실행 버튼이 없어야 함
    const retryButtons = screen.getAllByTitle('재실행');
    expect(retryButtons).toHaveLength(1); // RAG_INDEXING 하나만

    fireEvent.click(retryButtons[0]);
    expect(onAction.retryStep).toHaveBeenCalledWith('RAG_INDEXING');
  });
});
