import { useQuery } from '@tanstack/react-query';
import { fetchBookmarks } from '@/apis/bookmark.api';
import { useAuthStore } from '@/stores/authStore';

export function useBookmarks(page = 0, size = 20) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ['bookmarks', { page, size }],
    queryFn: () => fetchBookmarks(page, size),
    enabled: isAuthenticated,
  });
}
