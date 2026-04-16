import api from './client';
import type { ApiResponse, Guide } from '@/types/policy';

export async function fetchGuide(policyId: number): Promise<Guide> {
  const res = await api.get(`v1/guides/${policyId}`).json<ApiResponse<Guide>>();
  return res.data;
}
