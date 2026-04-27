import { HTTPError } from 'ky';
import api from './client';
import type { ApiResponse, Guide } from '@/types/policy';

export async function fetchGuide(policyId: number): Promise<Guide | null> {
  try {
    const res = await api.get(`v1/guides/${policyId}`).json<ApiResponse<Guide>>();
    return res.data;
  } catch (err) {
    if (err instanceof HTTPError && err.response.status === 404) {
      return null;
    }
    throw err;
  }
}
