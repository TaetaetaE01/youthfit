import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateEligibilityProfile } from '@/apis/eligibilityProfile.api';
import type {
  EligibilityProfile,
  UpdateEligibilityProfileRequest,
} from '@/types/personalInfo';

export function useUpdateEligibilityProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateEligibilityProfileRequest) => updateEligibilityProfile(data),
    onSuccess: (updated: EligibilityProfile) => {
      queryClient.setQueryData(['eligibilityProfile'], updated);
    },
  });
}
