// frontend/src/hooks/mutations/useRagPreview.ts
import { useMutation } from '@tanstack/react-query';
import { ragPreview } from '@/apis/adminRag.api';
import type { RagPreviewRequest, RagPreviewResponse } from '@/types/ragPreview';

export function useRagPreview() {
  return useMutation<RagPreviewResponse, Error, RagPreviewRequest>({
    mutationFn: ragPreview,
  });
}
