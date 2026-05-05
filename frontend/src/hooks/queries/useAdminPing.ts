import { useQuery } from '@tanstack/react-query';
import { pingAdmin } from '@/apis/admin.api';

export function useAdminPing() {
  return useQuery({
    queryKey: ['admin', 'ping'],
    queryFn: pingAdmin,
    staleTime: 0,
    retry: 0,
  });
}
