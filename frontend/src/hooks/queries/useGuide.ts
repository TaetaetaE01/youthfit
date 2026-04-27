import { useQuery } from '@tanstack/react-query';
import { fetchGuide } from '@/apis/guide.api';
import type { Guide } from '@/types/policy';

export function useGuide(policyId: number) {
  return useQuery<Guide | null>({
    queryKey: ['guide', policyId],
    queryFn: () => fetchGuide(policyId),
    enabled: policyId > 0,
    retry: false,
  });
}
