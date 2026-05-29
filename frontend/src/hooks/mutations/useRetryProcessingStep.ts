import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';
import type { ProcessingStep } from '@/types/adminPolicyProcessing';

/**
 * 특정 처리 단계(INGESTION/ENRICHMENT/GUIDE/RULE/RAG) 재실행.
 *
 * 성공 시 해당 정책의 상세와 전체 목록/통계를 invalidate 한다.
 */
export function useRetryProcessingStep(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (step: ProcessingStep) =>
      adminPolicyProcessingApi.retryStep(policyId, step),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
