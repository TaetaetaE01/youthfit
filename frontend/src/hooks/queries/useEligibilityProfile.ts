import { useQuery } from '@tanstack/react-query';
import { fetchEligibilityProfile } from '@/apis/eligibilityProfile.api';
import { useAuthStore } from '@/stores/authStore';
import type { EligibilityProfile } from '@/types/personalInfo';

export function useEligibilityProfile() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery<EligibilityProfile>({
    queryKey: ['eligibilityProfile'],
    queryFn: fetchEligibilityProfile,
    enabled: isAuthenticated,
  });
}
