import api from './client';

export type FailureReason = 'VALIDATION' | 'PARSING' | 'MAPPING' | 'DEDUPLICATION_CONFLICT' | 'OTHER';
export type RetryStatus = 'SUCCESS' | 'FAILURE' | 'NOT_FOUND' | 'PAYLOAD_EXPIRED';

export interface IngestionKpiResponse {
  yesterdayReceived: number;
  yesterdayFailure: number;
  sevenDayAvgReceivedPerDay: number;
  sevenDayFailureRate: number;
}

export interface IngestionDailyStatsResponse {
  date: string;
  source: string;
  successCount: number;
  failureCount: number;
  duplicateCount: number;
}

export interface IngestionSourceSummaryResponse {
  source: string;
  lastReceivedAt: string;
  sevenDayReceived: number;
  sevenDayFailureRate: number;
  stale: boolean;
}

export interface IngestionStaleSourceResponse {
  source: string;
  lastReceivedAt: string;
  hoursSinceLastReceived: number;
}

export interface IngestionFailureSummaryResponse {
  id: number;
  source: string;
  failureReason: FailureReason;
  sourceItemId: string | null;
  errorMessageExcerpt: string;
  retryCount: number;
  createdAt: string;
}

export interface IngestionFailureDetailResponse {
  id: number;
  source: string;
  sourceItemId: string | null;
  failureReason: FailureReason;
  errorMessage: string;
  errorStack: string | null;
  rawPayload: string | null;
  rawPayloadHash: string | null;
  payloadAvailable: boolean;
  retryCount: number;
  lastRetriedAt: string | null;
  createdAt: string;
  n8nWorkflowName: string | null;
  n8nExecutionId: string | null;
  n8nNodeName: string | null;
}

export interface IngestionRetryResponse {
  status: RetryStatus;
  message: string;
  newFailureId: number | null;
}

export interface FailureSearchParams {
  source?: string;
  reason?: FailureReason;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** Spring Page<T> envelope */
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export const fetchIngestionKpi = (): Promise<IngestionKpiResponse> =>
  api.get('v1/admin/ingestion/kpi').json<IngestionKpiResponse>();

export const fetchIngestionDailyStats = (days = 14): Promise<IngestionDailyStatsResponse[]> =>
  api.get(`v1/admin/ingestion/daily-stats?days=${days}`).json<IngestionDailyStatsResponse[]>();

export const fetchIngestionSources = (): Promise<IngestionSourceSummaryResponse[]> =>
  api.get('v1/admin/ingestion/sources').json<IngestionSourceSummaryResponse[]>();

export const fetchIngestionStaleSources = (): Promise<IngestionStaleSourceResponse[]> =>
  api.get('v1/admin/ingestion/stale-sources').json<IngestionStaleSourceResponse[]>();

export const searchIngestionFailures = (
  params: FailureSearchParams,
): Promise<SpringPage<IngestionFailureSummaryResponse>> => {
  const query = new URLSearchParams();
  if (params.source) query.set('source', params.source);
  if (params.reason) query.set('reason', params.reason);
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 20));
  return api
    .get(`v1/admin/ingestion/failures?${query.toString()}`)
    .json<SpringPage<IngestionFailureSummaryResponse>>();
};

export const fetchIngestionFailureDetail = (id: number): Promise<IngestionFailureDetailResponse> =>
  api.get(`v1/admin/ingestion/failures/${id}`).json<IngestionFailureDetailResponse>();

export const retryIngestionFailure = (id: number): Promise<IngestionRetryResponse> =>
  api.post(`v1/admin/ingestion/failures/${id}/retry`).json<IngestionRetryResponse>();
