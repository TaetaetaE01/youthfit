import { useQuery } from '@tanstack/react-query';
import { fetchPolicyDetail } from '@/apis/policy.api';

export function usePolicy(policyId: number) {
  return useQuery({
    queryKey: ['policy', policyId],
    queryFn: () => fetchPolicyDetail(policyId),
    enabled: policyId > 0,
  });
}
