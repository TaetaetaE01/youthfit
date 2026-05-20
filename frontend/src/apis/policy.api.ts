import api from './client';
import type { PolicyPage, PolicyDetail, PolicyStatus } from '@/types/policy';

interface PolicyListParams {
  category?: string;
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
