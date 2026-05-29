import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';

/**
 * 정책 본문 기반 RAG 청크 재인덱싱.
 *
 * 성공 시 해당 정책의 상세와 전체 목록/통계를 invalidate 한다.
 */
export function useReindexRag(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminPolicyProcessingApi.reindexRag(policyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
