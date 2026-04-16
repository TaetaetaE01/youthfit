import { useMutation, useQueryClient } from '@tanstack/react-query';
import { addBookmark, removeBookmark } from '@/apis/bookmark.api';

export function useAddBookmark() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (policyId: number) => addBookmark(policyId),
    onSuccess: (_data, policyId) => {
      queryClient.invalidateQueries({ queryKey: ['bookmarks'] });
      queryClient.invalidateQueries({ queryKey: ['policy', policyId] });
    },
  });
}

export function useRemoveBookmark() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (bookmarkId: number) => removeBookmark(bookmarkId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bookmarks'] });
      queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });
}
