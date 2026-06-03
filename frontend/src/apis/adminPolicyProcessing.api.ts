import api from './client';
import type {
  PolicyProcessingDetail,
  PolicyProcessingList,
  PolicyProcessingListParams,
  PolicyProcessingStats,
  ProcessingStep,
  ReprocessResult,
} from '@/types/adminPolicyProcessing';

/**
 * 어드민 정책 처리 현황 대시보드 API 클라이언트.
 *
 * - `client.ts` 의 ky 인스턴스 (`prefix: '/api'`, Authorization 자동 주입) 를 재사용한다.
 * - 다른 admin API (`adminDashboard`, `admin.ingestion`, `adminEnrichment`) 와 동일한 `v1/admin/...` 패턴.
 * - 백엔드는 `/api/v1/admin/policies/processing` 에서 서비스되므로 BASE 는 `v1/admin/policies/processing`.
 */
const BASE = 'v1/admin/policies/processing';

export const adminPolicyProcessingApi = {
  list: (params: PolicyProcessingListParams) =>
    api.get(BASE, { searchParams: cleanParams(params) }).json<PolicyProcessingList>(),

  stats: () => api.get(`${BASE}/stats`).json<PolicyProcessingStats>(),

  detail: (policyId: number) =>
    api.get(`${BASE}/${policyId}`).json<PolicyProcessingDetail>(),

  retryStep: (policyId: number, step: ProcessingStep) =>
    api.post(`${BASE}/${policyId}/steps/${step}/retry`).json<ReprocessResult>(),

  reindexAttachment: (policyId: number, attachmentId: number) =>
    api.post(`${BASE}/${policyId}/attachments/${attachmentId}/reindex`).json<ReprocessResult>(),

  reindexAllAttachments: (policyId: number) =>
    api.post(`${BASE}/${policyId}/attachments/reindex`).json<ReprocessResult>(),

  reindexRag: (policyId: number) =>
    api.post(`${BASE}/${policyId}/rag/reindex`).json<ReprocessResult>(),

  reprocess: (policyId: number, reason: string) =>
    api.post(`${BASE}/${policyId}/reprocess`, { json: { reason } }).json<ReprocessResult>(),
};

/**
 * undefined 키를 떨궈 ky 의 `searchParams` 에 안전하게 넘긴다.
 * 숫자는 String 으로 변환한다 (ky 는 string 또는 number 만 허용).
 */
function cleanParams(params: PolicyProcessingListParams): Record<string, string> {
  const out: Record<string, string> = {};
  if (params.q) out.q = params.q;
  if (params.region) out.region = params.region;
  if (params.sourceType) out.sourceType = params.sourceType;
  if (params.filter) out.filter = params.filter;
  if (params.sort) out.sort = params.sort;
  if (params.page !== undefined) out.page = String(params.page);
  if (params.size !== undefined) out.size = String(params.size);
  return out;
}
