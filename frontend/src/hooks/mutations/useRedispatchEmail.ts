import { useMutation, useQueryClient } from '@tanstack/react-query';
import { redispatchEmail } from '@/apis/admin.email.api';

export function useRedispatchEmail() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => redispatchEmail(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'email', 'list'] });
      qc.invalidateQueries({ queryKey: ['admin', 'email', 'stats'] });
    },
  });
}
