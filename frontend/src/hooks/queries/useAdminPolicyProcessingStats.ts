import { useQuery } from '@tanstack/react-query';
import { adminPolicyProcessingApi } from '@/apis/adminPolicyProcessing.api';
import { adminPolicyProcessingKeys } from './useAdminPolicyProcessingList';

/**
 * 정책 처리 현황 KPI 카드 (전체/완료/부분/미완료/24h 갱신) 조회.
 *
 * 목록 훅과 키 prefix 를 공유해 재실행 mutation 후 일괄 무효화한다.
 */
export function useAdminPolicyProcessingStats() {
  return useQuery({
    queryKey: adminPolicyProcessingKeys.stats(),
    queryFn: () => adminPolicyProcessingApi.stats(),
  });
}
