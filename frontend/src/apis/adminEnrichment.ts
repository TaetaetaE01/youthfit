import api from './client';
import type {
  CandidateView,
  CandidateSummary,
  PolicyEnrichmentDetail,
  EnrichmentJobView,
  ReferenceSite,
  JobAccepted,
} from '@/types/adminEnrichment';

/**
 * 관리자 enrichment 검토 콘솔 API 클라이언트.
 *
 * - 기존 admin.*.api.ts 패턴과 동일하게 `ky` 기반 `api` 인스턴스 (Authorization 자동 주입) 를 사용한다.
 * - 베이스 prefix `/api` 는 `client.ts` 에서 처리되므로 여기서는 `v1/...` 부터 작성한다.
 */

/** Spring Data Page<T> 응답 형태 */
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

export interface CandidateQuery {
  needsReview?: boolean;
  statuses?: string[];
  detailLevels?: string[];
  q?: string;
  page?: number;
  size?: number;
}

function buildCandidateParams(q: CandidateQuery): URLSearchParams {
  const params = new URLSearchParams();
  if (q.needsReview !== undefined) {
    params.set('needsReview', String(q.needsReview));
  }
  if (q.q) {
    params.set('q', q.q);
  }
  q.statuses?.forEach((s) => params.append('statuses', s));
  q.detailLevels?.forEach((d) => params.append('detailLevels', d));
  params.set('page', String(q.page ?? 0));
  params.set('size', String(q.size ?? 20));
  return params;
}

export const fetchCandidates = (q: CandidateQuery): Promise<SpringPage<CandidateView>> => {
  const params = buildCandidateParams(q);
  return api.get(`v1/admin/enrichment/candidates?${params.toString()}`).json<SpringPage<CandidateView>>();
};

export const fetchSummary = (): Promise<CandidateSummary> =>
  api.get('v1/admin/enrichment/candidates/summary').json<CandidateSummary>();

export const fetchPolicyDetail = (policyId: number): Promise<PolicyEnrichmentDetail> =>
  api.get(`v1/admin/enrichment/policies/${policyId}`).json<PolicyEnrichmentDetail>();

export const updateReferenceSites = async (
  policyId: number,
  body: ReferenceSite[],
): Promise<void> => {
  await api.put(`v1/admin/enrichment/policies/${policyId}/reference-sites`, {
    json: body,
  });
};

export const createEnrichmentJob = (
  policyId: number,
  urls?: ReferenceSite[],
): Promise<JobAccepted> =>
  api
    .post(`v1/admin/enrichment/policies/${policyId}/jobs`, {
      json: urls && urls.length > 0 ? { urls } : {},
    })
    .json<JobAccepted>();

export const fetchJob = (jobId: number): Promise<EnrichmentJobView> =>
  api.get(`v1/admin/enrichment/jobs/${jobId}`).json<EnrichmentJobView>();
