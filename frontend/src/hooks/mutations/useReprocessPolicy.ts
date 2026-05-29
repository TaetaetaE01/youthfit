import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from '@/hooks/queries/useAdminPolicyProcessingList';

/**
 * 정책 전체 파이프라인 재처리 (INGESTION 이후 모든 단계 재실행).
 *
 * 사유(reason) 는 감사 로그용으로 필수다.
 * 성공 시 해당 정책의 상세와 전체 목록/통계를 invalidate 한다.
 */
export function useReprocessPolicy(policyId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) => adminPolicyProcessingApi.reprocess(policyId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.detail(policyId) });
      queryClient.invalidateQueries({ queryKey: adminPolicyProcessingKeys.all });
    },
  });
}
