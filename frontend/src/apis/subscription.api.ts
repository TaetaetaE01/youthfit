import api from './client';
import type { ApiResponse } from '@/types/policy';

export interface PolicySubscription {
  subscriptionId: number | null;
  policyId: number;
  subscribed: boolean;
  subscribedAt: string | null;
}

export async function fetchPolicySubscription(policyId: number): Promise<PolicySubscription> {
  const res = await api
    .get(`v1/policies/${policyId}/subscription`)
    .json<ApiResponse<PolicySubscription>>();
  return res.data;
}

export async function subscribePolicy(policyId: number): Promise<PolicySubscription> {
  const res = await api
    .post(`v1/policies/${policyId}/subscription`)
    .json<ApiResponse<PolicySubscription>>();
  return res.data;
}

export async function unsubscribePolicy(policyId: number): Promise<void> {
  await api.delete(`v1/policies/${policyId}/subscription`);
}
