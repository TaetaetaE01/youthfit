import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchCandidates,
  fetchSummary,
  fetchPolicyDetail,
  updateReferenceSites,
  createEnrichmentJob,
  fetchJob,
  type CandidateQuery,
  type SpringPage,
} from '@/apis/adminEnrichment';
import type {
  CandidateView,
  PolicyEnrichmentDetail,
  ReferenceSite,
} from '@/types/adminEnrichment';

/**
 * 관리자 enrichment 검토 콘솔용 react-query 훅.
 *
 * 폴링 전략:
 *  - 활성 작업 (PENDING | RUNNING) 이 있으면 3 초마다 refetch
 *  - 작업이 60 초를 넘기면 5 초 간격으로 backoff
 *  - 활성 작업이 없으면 폴링 중단
 */

const POLL_FAST_MS = 3000;
const POLL_SLOW_MS = 5000;
const BACKOFF_AFTER_MS = 60_000;

function isJobActive(status: string | undefined): boolean {
  return status === 'PENDING' || status === 'RUNNING';
}

/**
 * 가장 오래된 활성 작업의 requestedAt 으로부터 BACKOFF_AFTER_MS 이상 경과했는지에 따라
 * 빠른/느린 폴링 간격을 결정한다.
 *
 * @param requestedAtList 활성 작업들의 requestedAt (ISO 문자열) 배열
 * @returns 폴링 간격 (ms) 또는 false (폴링 중단)
 */
function decidePollInterval(requestedAtList: string[]): number | false {
  if (requestedAtList.length === 0) {
    return false;
  }
  const parsed = requestedAtList.map((s) => Date.parse(s)).filter((n) => !Number.isNaN(n));
  if (parsed.length === 0) {
    return POLL_FAST_MS;
  }
  const oldest = Math.min(...parsed);
  return Date.now() - oldest > BACKOFF_AFTER_MS ? POLL_SLOW_MS : POLL_FAST_MS;
}

export function useCandidates(q: CandidateQuery) {
  return useQuery<SpringPage<CandidateView>>({
    queryKey: ['adminEnrichment', 'candidates', q],
    queryFn: () => fetchCandidates(q),
    refetchInterval: (query) => {
      const data = query.state.data;
      const items = data?.content ?? [];
      const activeRequestedAts = items
        .filter((c) => c.latestJob !== null && isJobActive(c.latestJob.status))
        .map((c) => c.latestJob!.requestedAt);
      return decidePollInterval(activeRequestedAts);
    },
  });
}

export function useSummary() {
  return useQuery({
    queryKey: ['adminEnrichment', 'summary'],
    queryFn: fetchSummary,
  });
}

export function usePolicyDetail(policyId: number | null) {
  return useQuery<PolicyEnrichmentDetail>({
    queryKey: ['adminEnrichment', 'policy', policyId],
    queryFn: () => fetchPolicyDetail(policyId as number),
    enabled: policyId !== null,
    refetchInterval: (query) => {
      const data = query.state.data;
      const recent = data?.recentJobs?.[0];
      if (!recent || !isJobActive(recent.status)) {
        return false;
      }
      return decidePollInterval([recent.requestedAt]);
    },
  });
}

export function useJob(jobId: number | null) {
  return useQuery({
    queryKey: ['adminEnrichment', 'job', jobId],
    queryFn: () => fetchJob(jobId as number),
    enabled: jobId !== null,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data || !isJobActive(data.status)) {
        return false;
      }
      return decidePollInterval([data.requestedAt]);
    },
  });
}

export function useUpdateReferenceSites(policyId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ReferenceSite[]) => updateReferenceSites(policyId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['adminEnrichment', 'policy', policyId] });
    },
  });
}

export function useCreateJob(policyId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (urls?: ReferenceSite[]) => createEnrichmentJob(policyId, urls),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['adminEnrichment', 'policy', policyId] });
      qc.invalidateQueries({ queryKey: ['adminEnrichment', 'candidates'] });
      qc.invalidateQueries({ queryKey: ['adminEnrichment', 'summary'] });
    },
  });
}
