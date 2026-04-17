import { useQuery } from '@tanstack/react-query';
import { fetchProfile } from '@/apis/user.api';
import { useAuthStore } from '@/stores/authStore';

export function useProfile() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ['profile'],
    queryFn: fetchProfile,
    enabled: isAuthenticated,
  });
}
