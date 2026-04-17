import { useQuery } from '@tanstack/react-query';
import { fetchRegions } from '@/apis/region.api';
import type { Region } from '@/types/personalInfo';

export function useSidoList() {
  return useQuery<Region[]>({
    queryKey: ['regions', 'sido'],
    queryFn: () => fetchRegions('SIDO'),
    staleTime: 1000 * 60 * 60,
  });
}

export function useSigunguList(parentCode: string | null | undefined) {
  return useQuery<Region[]>({
    queryKey: ['regions', 'sigungu', parentCode],
    queryFn: () => fetchRegions('SIGUNGU', parentCode!),
    enabled: !!parentCode,
    staleTime: 1000 * 60 * 60,
  });
}
