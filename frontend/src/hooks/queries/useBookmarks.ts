import { useQuery } from '@tanstack/react-query';
import { fetchBookmarks } from '@/apis/bookmark.api';

export function useBookmarks(page = 0, size = 20) {
  return useQuery({
    queryKey: ['bookmarks', { page, size }],
    queryFn: () => fetchBookmarks(page, size),
  });
}
