import api from './client';
import type { ApiResponse, EligibilityResponse } from '@/types/policy';

export async function judgeEligibility(policyId: number): Promise<EligibilityResponse> {
  const res = await api
    .post('v1/eligibility/judge', { json: { policyId } })
    .json<ApiResponse<EligibilityResponse>>();
  return res.data;
}
