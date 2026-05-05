import { useQuery } from '@tanstack/react-query';
import { getEmailPreview } from '@/apis/admin.email.api';

export function useAdminEmailPreview(id: number, enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'email', 'preview', id],
    queryFn: () => getEmailPreview(id),
    enabled,
    staleTime: Infinity,
  });
}
