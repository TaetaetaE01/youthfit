// frontend/src/apis/adminRag.api.ts
import { client } from './client';

export interface HybridOverride {
  hybridEnabled?: boolean | null;
  topNPerSearch?: number | null;
  rrfK?: number | null;
  trigramThreshold?: number | null;
  keywordBoostEnabled?: boolean | null;
  maxKeywords?: number | null;
}

export interface EffectiveConfig {
  hybridEnabled: boolean;
  topNPerSearch: number;
  rrfK: number;
  trigramThreshold: number;
  keywordBoostEnabled: boolean;
  maxKeywords: number;
}

export interface ChunkSummary {
  chunkId: number;
  chunkIndex: number;
  distance: number;
  preview: string;
}

export interface MergedChunk extends ChunkSummary {
  rrfScore: number;
  rank: number;
}

export interface PreviewSide {
  config: EffectiveConfig;
  vectorTopN: ChunkSummary[];
  trigramTopN: ChunkSummary[];
  merged: MergedChunk[];
  tookMs: number;
}

export interface RankChange {
  chunkId: number;
  baselineRank: number | null;
  candidateRank: number | null;
  delta: string; // "NEW" | "DROPPED" | "+N" | "-N" | "0"
}

export interface RagPreviewRequest {
  policyId: number;
  query: string;
  candidate: HybridOverride;
}

export interface RagPreviewResponse {
  policyId: number;
  query: string;
  extractedKeywords: string[];
  baseline: PreviewSide;
  candidate: PreviewSide;
  diff: { rankChanges: RankChange[] };
}

export async function ragPreview(req: RagPreviewRequest): Promise<RagPreviewResponse> {
  return client
    .post('api/v1/admin/rag/preview', { json: req })
    .json<RagPreviewResponse>();
}
