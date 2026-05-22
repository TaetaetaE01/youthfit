import { useQuery } from '@tanstack/react-query';
import { getDashboardOverview } from '@/apis/adminDashboard.api';

export function useAdminDashboardOverview() {
  return useQuery({
    queryKey: ['admin', 'dashboard', 'overview'],
    queryFn: getDashboardOverview,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    staleTime: 25_000,
  });
}
