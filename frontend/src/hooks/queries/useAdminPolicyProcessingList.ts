import { useQuery } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import type { PolicyProcessingListParams } from '@/types/adminPolicyProcessing';

/**
 * 어드민 정책 처리 현황 대시보드 Query Key Factory.
 *
 * stats / detail 훅도 동일한 prefix 를 공유해야 일괄 invalidation (재실행 mutation 후) 이 가능하다.
 */
export const adminPolicyProcessingKeys = {
  all: ['admin-policy-processing'] as const,
  list: (params: PolicyProcessingListParams) =>
    [...adminPolicyProcessingKeys.all, 'list', params] as const,
  stats: () => [...adminPolicyProcessingKeys.all, 'stats'] as const,
  detail: (policyId: number) =>
    [...adminPolicyProcessingKeys.all, 'detail', policyId] as const,
};

/**
 * 정책 처리 현황 목록 조회.
 *
 * 필터/정렬/페이지 변경 시 깜빡임을 피하기 위해 이전 응답을 placeholderData 로 유지한다.
 */
export function useAdminPolicyProcessingList(params: PolicyProcessingListParams) {
  return useQuery({
    queryKey: adminPolicyProcessingKeys.list(params),
    queryFn: () => adminPolicyProcessingApi.list(params),
    placeholderData: (prev) => prev,
  });
}
