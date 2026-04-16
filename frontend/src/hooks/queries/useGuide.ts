import { useQuery } from '@tanstack/react-query';
import { fetchGuide } from '@/apis/guide.api';

export function useGuide(policyId: number) {
  return useQuery({
    queryKey: ['guide', policyId],
    queryFn: () => fetchGuide(policyId),
    enabled: policyId > 0,
    retry: false,
  });
}
