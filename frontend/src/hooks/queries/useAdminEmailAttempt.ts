import { useQuery } from '@tanstack/react-query';
import { getEmailAttempt } from '@/apis/admin.email.api';

export function useAdminEmailAttempt(id: number) {
  return useQuery({
    queryKey: ['admin', 'email', 'detail', id],
    queryFn: () => getEmailAttempt(id),
    enabled: id > 0,
  });
}
