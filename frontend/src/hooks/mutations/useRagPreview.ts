// frontend/src/hooks/mutations/useRagPreview.ts
import { useMutation } from '@tanstack/react-query';
import { ragPreview, type RagPreviewRequest, type RagPreviewResponse } from '@/apis/adminRag.api';

export function useRagPreview() {
  return useMutation<RagPreviewResponse, Error, RagPreviewRequest>({
    mutationFn: ragPreview,
  });
}
