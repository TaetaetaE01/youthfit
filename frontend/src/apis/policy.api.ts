import api from './client';
import type { PolicyPage, PolicyDetail } from '@/types/policy';

interface PolicyListParams {
  category?: string;
  regionCode?: string;
  status?: string;
  sortBy?: string;
  ascending?: boolean;
  page?: number;
  size?: number;
}

export async function fetchPolicies(params: PolicyListParams): Promise<PolicyPage> {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set('category', params.category);
  if (params.regionCode) searchParams.set('regionCode', params.regionCode);
  if (params.status) searchParams.set('status', params.status);
  if (params.sortBy) searchParams.set('sortBy', params.sortBy);
  if (params.ascending !== undefined) searchParams.set('ascending', String(params.ascending));
  searchParams.set('page', String(params.page ?? 0));
  searchParams.set('size', String(params.size ?? 20));

  return api.get('v1/policies', { searchParams }).json<PolicyPage>();
}

export async function searchPolicies(keyword: string, page = 0, size = 20): Promise<PolicyPage> {
  return api
    .get('v1/policies/search', { searchParams: { keyword, page: String(page), size: String(size) } })
    .json<PolicyPage>();
}

export async function fetchPolicyDetail(policyId: number): Promise<PolicyDetail> {
  return api.get(`v1/policies/${policyId}`).json<PolicyDetail>();
}
