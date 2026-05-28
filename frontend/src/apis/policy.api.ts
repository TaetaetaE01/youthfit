import api from './client';
import type {
  PolicyPage,
  PolicyDetail,
  PolicyStatus,
  SourceType,
  PolicyCalendarResponse,
  PolicyCalendarPageResponse,
} from '@/types/policy';

interface PolicyListParams {
  category?: string;
  source?: SourceType;
  regions?: string[];         // 신규: 행정코드 배열 (서버에서 CSV 로 join)
  regionCode?: string;        // deprecated: regions 가 있으면 무시
  status?: PolicyStatus;
  page?: number;
  size?: number;
}

interface PolicySearchParams {
  status?: PolicyStatus;
  page?: number;
  size?: number;
}

export async function fetchPolicies(params: PolicyListParams): Promise<PolicyPage> {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set('category', params.category);
  if (params.source) searchParams.set('source', params.source);
  if (params.regions && params.regions.length > 0) {
    searchParams.set('regions', params.regions.join(','));
  } else if (params.regionCode) {
    searchParams.set('regionCode', params.regionCode);
  }
  if (params.status) searchParams.set('status', params.status);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies', { searchParams }).json<PolicyPage>();
}

export async function searchPolicies(
  keyword: string,
  params: PolicySearchParams = {},
): Promise<PolicyPage> {
  const searchParams = new URLSearchParams();
  searchParams.set('keyword', keyword);
  if (params.status) searchParams.set('status', params.status);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies/search', { searchParams }).json<PolicyPage>();
}

export async function fetchPolicyDetail(policyId: number): Promise<PolicyDetail> {
  return api.get(`v1/policies/${policyId}`).json<PolicyDetail>();
}

interface PolicyCalendarParams {
  from: string;          // YYYY-MM-DD
  to: string;            // YYYY-MM-DD
  regions?: string[];
  category?: string;
}

export async function fetchCalendarPolicies(
  params: PolicyCalendarParams,
): Promise<PolicyCalendarResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set('from', params.from);
  searchParams.set('to', params.to);
  if (params.regions && params.regions.length > 0) {
    searchParams.set('regions', params.regions.join(','));
  }
  if (params.category) searchParams.set('category', params.category);

  return api.get('v1/policies/calendar', { searchParams }).json<PolicyCalendarResponse>();
}

interface AlwaysOpenParams {
  regions?: string[];
  category?: string;
  page?: number;
  size?: number;
}

export async function fetchAlwaysOpenPolicies(
  params: AlwaysOpenParams = {},
): Promise<PolicyCalendarPageResponse> {
  const searchParams = new URLSearchParams();
  if (params.regions && params.regions.length > 0) {
    searchParams.set('regions', params.regions.join(','));
  }
  if (params.category) searchParams.set('category', params.category);
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies/calendar/always-open', { searchParams }).json<PolicyCalendarPageResponse>();
}
