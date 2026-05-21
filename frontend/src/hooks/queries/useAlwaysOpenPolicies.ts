import { useQuery } from '@tanstack/react-query';
import { fetchAlwaysOpenPolicies } from '@/apis/policy.api';

interface Params {
  regions?: string[];
  category?: string;
  page?: number;
  size?: number;
}

export function useAlwaysOpenPolicies(params: Params = {}) {
  return useQuery({
    queryKey: ['policy', 'calendar', 'always-open', params],
    queryFn: () => fetchAlwaysOpenPolicies(params),
  });
}
