import { useQuery } from '@tanstack/react-query';
import { getEmailDailyStats } from '@/apis/admin.email.api';

export function useAdminEmailDailyStats(from: string, to: string) {
  return useQuery({
    queryKey: ['admin', 'email', 'stats', 'daily', from, to],
    queryFn: () => getEmailDailyStats(from, to),
    staleTime: 60_000,
  });
}
