import api from './client';
import type { RagPreviewRequest, RagPreviewResponse } from '@/types/ragPreview';

export type { RagPreviewRequest, RagPreviewResponse } from '@/types/ragPreview';

export async function ragPreview(req: RagPreviewRequest): Promise<RagPreviewResponse> {
  return api.post('v1/admin/rag/preview', { json: req }).json<RagPreviewResponse>();
}
