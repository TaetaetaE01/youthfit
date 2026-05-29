import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';

/**
 * 정책의 모든 첨부파일 RAG 일괄 재인덱싱.
 *
 * 성공 시 해당 정책의 상세와 전체 목록/통계를 invalidate 한다.
 */
export function useReindexAllAttachments(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminPolicyProcessingApi.reindexAllAttachments(policyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
