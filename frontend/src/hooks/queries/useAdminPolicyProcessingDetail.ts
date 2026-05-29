import { useQuery } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from './useAdminPolicyProcessingList';

/**
 * 정책 처리 현황 펼침 영역 상세 조회.
 *
 * `policyId` 가 null 인 동안에는 쿼리가 비활성화된다 (행을 펼치기 전 상태).
 */
export function useAdminPolicyProcessingDetail(policyId: number | null) {
  return useQuery({
    queryKey: adminPolicyProcessingKeys.detail(policyId ?? 0),
    queryFn: () => adminPolicyProcessingApi.detail(policyId!),
    enabled: policyId !== null,
  });
}
