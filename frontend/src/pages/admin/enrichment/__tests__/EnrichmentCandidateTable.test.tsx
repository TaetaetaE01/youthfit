import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EnrichmentCandidateTable } from '../EnrichmentCandidateTable';
import * as api from '@/apis/adminEnrichment';

function renderWithClient(ui: React.ReactNode) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: {
            queries: { retry: false, refetchOnWindowFocus: false },
          },
        })
      }
    >
      {ui}
    </QueryClientProvider>,
  );
}

describe('EnrichmentCandidateTable', () => {
  beforeEach(() => {
    vi.spyOn(api, 'fetchCandidates').mockResolvedValue({
      content: [
        {
          id: 1,
          title: '청년주택',
          organization: '서울시',
          enrichmentStatus: 'TOO_SHORT',
          confidence: 0.42,
          detailLevel: 'MEDIUM',
          needsReview: true,
          latestJob: null,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0,
    } as never);
  });

  test('row 클릭 시 onSelect 호출', async () => {
    const onSelect = vi.fn();
    renderWithClient(<EnrichmentCandidateTable onSelect={onSelect} />);
    fireEvent.click(await screen.findByText('청년주택'));
    expect(onSelect).toHaveBeenCalledWith(1);
  });

  test('checkbox 토글 시 fetchCandidates 가 다시 호출된다', async () => {
    renderWithClient(<EnrichmentCandidateTable onSelect={() => {}} />);
    await screen.findByText('청년주택');
    const mocked = api.fetchCandidates as unknown as ReturnType<typeof vi.fn>;
    const initialCalls = mocked.mock.calls.length;
    fireEvent.click(screen.getByLabelText(/검토 필요만/));
    await waitFor(() =>
      expect(mocked.mock.calls.length).toBeGreaterThan(initialCalls),
    );
  });
});
