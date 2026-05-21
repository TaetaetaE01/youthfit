import { useQuery } from '@tanstack/react-query';
import { fetchRegions, fetchRegionsByLevel } from '@/apis/region.api';
import type { Region } from '@/types/personalInfo';

export function useRegions() {
  return useQuery({
    queryKey: ['regions'],
    queryFn: fetchRegions,
    staleTime: Infinity,            // 24h 캐시 + 세션 중 재요청 안 함
    gcTime: 1000 * 60 * 60 * 24,    // 24h
  });
}

export function useSidoList() {
  return useQuery<Region[]>({
    queryKey: ['regions', 'sido'],
    queryFn: () => fetchRegionsByLevel('SIDO'),
    staleTime: 1000 * 60 * 60,
  });
}

export function useSigunguList(parentCode: string | null | undefined) {
  return useQuery<Region[]>({
    queryKey: ['regions', 'sigungu', parentCode],
    queryFn: () => fetchRegionsByLevel('SIGUNGU', parentCode!),
    enabled: !!parentCode,
    staleTime: 1000 * 60 * 60,
  });
}
