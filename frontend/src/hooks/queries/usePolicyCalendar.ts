import { useQuery } from '@tanstack/react-query';
import { fetchCalendarPolicies } from '@/apis/policy.api';

interface Params {
  from: string;
  to: string;
  regions?: string[];
  category?: string;
}

export function usePolicyCalendar(params: Params) {
  return useQuery({
    queryKey: ['policy', 'calendar', params],
    queryFn: () => fetchCalendarPolicies(params),
  });
}
