import { useQuery } from '@tanstack/react-query';
import { fetchPolicySubscription } from '@/apis/subscription.api';
import { useAuthStore } from '@/stores/authStore';

export function usePolicySubscription(policyId: number) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ['policySubscription', policyId],
    queryFn: () => fetchPolicySubscription(policyId),
    enabled: isAuthenticated && policyId > 0,
    staleTime: 60_000,
  });
}
