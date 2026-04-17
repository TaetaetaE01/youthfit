import api from './client';
import type { ApiResponse } from '@/types/policy';
import type { Region, RegionLevel } from '@/types/personalInfo';

export async function fetchRegions(level: RegionLevel, parentCode?: string): Promise<Region[]> {
  const searchParams: Record<string, string> = { level };
  if (parentCode) searchParams.parentCode = parentCode;
  const res = await api
    .get('v1/regions', { searchParams })
    .json<ApiResponse<Region[]>>();
  return res.data;
}
