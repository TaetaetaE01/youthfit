import api from './client';
import type { ApiResponse } from '@/types/policy';
import type { EligibilityProfile, UpdateEligibilityProfileRequest } from '@/types/personalInfo';

export async function fetchEligibilityProfile(): Promise<EligibilityProfile> {
  const res = await api
    .get('v1/users/me/eligibility-profile')
    .json<ApiResponse<EligibilityProfile>>();
  return res.data;
}

export async function updateEligibilityProfile(
  data: UpdateEligibilityProfileRequest,
): Promise<EligibilityProfile> {
  const res = await api
    .patch('v1/users/me/eligibility-profile', { json: data })
    .json<ApiResponse<EligibilityProfile>>();
  return res.data;
}
