import { useMutation } from '@tanstack/react-query';
import { judgeEligibility } from '@/apis/eligibility.api';

export function useJudgeEligibility() {
  return useMutation({
    mutationFn: (policyId: number) => judgeEligibility(policyId),
  });
}
