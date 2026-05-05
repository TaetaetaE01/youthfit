import api from './client';
import type { Page } from './admin.email.api';

interface ApiEnvelope<T> { data: T }

export type LookupResultType = 'HIT' | 'BELOW_THRESHOLD' | 'MISS';

export interface QnaCacheKpi {
  todayHitRate: number;
  yesterdayHitRate: number;
  sevenDaysAvgSimilarity: number;
  sevenDaysEstimatedSavingsUsd: number;
  sevenDaysHitCount: number;
  sevenDaysTotalCount: number;
}

export interface QnaCacheDailyStat {
  date: string;
  hitCount: number;
  belowThresholdCount: number;
  missCount: number;
  avgSimilarity: number;
}

export interface QnaCacheLookupSummary {
  id: number;
  lookedUpAt: string;
  result: LookupResultType;
  policyId: number;
  questionExcerpt: string;
  similarityScore: number | null;
  matchedCachedId: number | null;
}

export interface QnaCacheLookupDetail {
  id: number;
  lookedUpAt: string;
  result: LookupResultType;
  policyId: number;
  questionText: string;
  normalizedText: string;
  matchedCachedId: number | null;
  matchedCachedQuestion: string | null;
  matchedCachedAnswerExcerpt: string | null;
  similarityScore: number | null;
  thresholdApplied: number;
  llmCallMade: boolean;
}

export interface ListFilter {
  result?: LookupResultType;
  policyId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export async function getQnaCacheKpi(): Promise<QnaCacheKpi> {
  const res = await api.get('v1/admin/qna-cache/stats/kpi').json<ApiEnvelope<QnaCacheKpi>>();
  return res.data;
}

export async function getQnaCacheDailyStats(days = 14): Promise<QnaCacheDailyStat[]> {
  const res = await api
    .get(`v1/admin/qna-cache/stats/daily?days=${days}`)
    .json<ApiEnvelope<QnaCacheDailyStat[]>>();
  return res.data;
}

export async function listQnaCacheLookups(filter: ListFilter): Promise<Page<QnaCacheLookupSummary>> {
  const params = new URLSearchParams();
  if (filter.result) params.set('result', filter.result);
  if (filter.policyId != null) params.set('policyId', String(filter.policyId));
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  params.set('page', String(filter.page ?? 0));
  params.set('size', String(filter.size ?? 20));
  const res = await api
    .get(`v1/admin/qna-cache?${params}`)
    .json<ApiEnvelope<Page<QnaCacheLookupSummary>>>();
  return res.data;
}

export async function getQnaCacheLookup(id: number): Promise<QnaCacheLookupDetail> {
  const res = await api
    .get(`v1/admin/qna-cache/${id}`)
    .json<ApiEnvelope<QnaCacheLookupDetail>>();
  return res.data;
}

export async function exportQnaCacheCsv(
  filter: Omit<ListFilter, 'page' | 'size'>,
): Promise<void> {
  const params = new URLSearchParams();
  if (filter.result) params.set('result', filter.result);
  if (filter.policyId != null) params.set('policyId', String(filter.policyId));
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  const blob = await api.get(`v1/admin/qna-cache/export?${params}`).blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'qna-cache-lookup.csv';
  link.click();
  URL.revokeObjectURL(url);
}
