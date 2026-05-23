import api from './client';
import type { RagPreviewRequest, RagPreviewResponse } from '@/types/ragPreview';

export type { RagPreviewRequest, RagPreviewResponse } from '@/types/ragPreview';

interface ApiEnvelope<T> { data: T }

export async function ragPreview(req: RagPreviewRequest): Promise<RagPreviewResponse> {
  const res = await api.post('v1/admin/rag/preview', { json: req }).json<ApiEnvelope<RagPreviewResponse>>();
  return res.data;
}
