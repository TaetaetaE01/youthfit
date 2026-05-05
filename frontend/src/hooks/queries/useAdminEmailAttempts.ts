import { useQuery } from '@tanstack/react-query';
import { listEmailAttempts, type ListFilter } from '@/apis/admin.email.api';

export function useAdminEmailAttempts(filter: ListFilter) {
  return useQuery({
    queryKey: ['admin', 'email', 'list', filter],
    queryFn: () => listEmailAttempts(filter),
    staleTime: 10_000,
  });
}
