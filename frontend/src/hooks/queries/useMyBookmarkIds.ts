import { useQuery } from '@tanstack/react-query';
import { fetchMyBookmarkIds } from '@/apis/bookmark.api';
import { useAuthStore } from '@/stores/authStore';

export function useMyBookmarkIds() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ['bookmarkIds'],
    queryFn: fetchMyBookmarkIds,
    enabled: isAuthenticated,
    staleTime: 60_000,
  });
}
