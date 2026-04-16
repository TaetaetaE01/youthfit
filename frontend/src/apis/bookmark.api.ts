import api from './client';
import type { ApiResponse, BookmarkPage } from '@/types/policy';

export async function fetchBookmarks(page = 0, size = 20): Promise<BookmarkPage> {
  const res = await api
    .get('v1/bookmarks', { searchParams: { page: String(page), size: String(size) } })
    .json<ApiResponse<BookmarkPage>>();
  return res.data;
}

export async function addBookmark(policyId: number): Promise<{ bookmarkId: number; policyId: number }> {
  const res = await api
    .post('v1/bookmarks', { json: { policyId } })
    .json<ApiResponse<{ bookmarkId: number; policyId: number }>>();
  return res.data;
}

export async function removeBookmark(bookmarkId: number): Promise<void> {
  await api.delete(`v1/bookmarks/${bookmarkId}`);
}
