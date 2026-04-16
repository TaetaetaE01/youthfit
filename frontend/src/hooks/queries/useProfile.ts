import { useQuery } from '@tanstack/react-query';
import { fetchProfile } from '@/apis/user.api';

export function useProfile() {
  return useQuery({
    queryKey: ['profile'],
    queryFn: fetchProfile,
  });
}
