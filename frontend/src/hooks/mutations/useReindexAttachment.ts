import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';

/**
 * 정책 첨부파일 단건 RAG 재인덱싱.
 *
 * 성공 시 해당 정책의 상세와 전체 목록/통계를 invalidate 한다.
 */
export function useReindexAttachment(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attachmentId: number) =>
      adminPolicyProcessingApi.reindexAttachment(policyId, attachmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
