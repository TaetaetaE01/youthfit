import { useQuery } from '@tanstack/react-query';
import { getEmailKpi } from '@/apis/admin.email.api';

export function useAdminEmailKpi() {
  return useQuery({
    queryKey: ['admin', 'email', 'stats', 'kpi'],
    queryFn: getEmailKpi,
    staleTime: 60_000,
  });
}
