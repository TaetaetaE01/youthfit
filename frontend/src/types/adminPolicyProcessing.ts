/**
 * 어드민 정책 처리 현황 대시보드 타입 정의.
 *
 * 백엔드 응답 형태와 1:1 로 맞춘다.
 *
 * 백엔드 매핑:
 * - `stepStatuses` 는 백엔드 {@code Map<String, String>} (ProcessingStep#name() → ProcessingStatus#name()).
 *   일부 단계가 아직 실행되지 않을 수 있어 `Partial<Record<...>>` 로 정의한다.
 * - `StepDetail.step` / `status` 와 `AttachmentDetail.extractionStatus` 는 백엔드가 enum.name() 으로
 *   직렬화하지만 `string` 으로 받는다 (도메인 enum 누수 방지를 위한 의도된 평탄화).
 */

export type ProcessingStep = 'INGESTION' | 'ENRICHMENT' | 'GUIDE' | 'RULE' | 'RAG_INDEXING';

export type ProcessingStatus = 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'SKIPPED' | 'FAILED';

export type Completeness = 'COMPLETE' | 'PARTIAL' | 'INCOMPLETE';

export type Filter =
  | 'ALL'
  | 'INCOMPLETE'
  | 'PARTIAL'
  | 'RAG_FAILED'
  | 'ATTACHMENT_EMBEDDING_MISSING'
  | 'REFERENCE_FETCH_FAILED'
  | 'GUIDE_RULE_FAILED'
  | 'RECENT_24H';

export type Sort = 'UPDATED_DESC' | 'COMPLETENESS_ASC' | 'ID_ASC';

export interface AttachmentSummary {
  total: number;
  extracted: number;
  embedded: number;
}

export interface ReferenceSummary {
  total: number;
  succeeded: number;
}

export interface PolicyProcessingItem {
  policyId: number;
  title: string;
  region: string;
  completeness: Completeness;
  stepStatuses: Partial<Record<ProcessingStep, ProcessingStatus>>;
  attachments: AttachmentSummary;
  references: ReferenceSummary;
  updatedAt: string;
}

export interface PolicyProcessingList {
  totalCount: number;
  page: number;
  size: number;
  items: PolicyProcessingItem[];
}

export interface PolicyProcessingStats {
  totalCount: number;
  completeCount: number;
  partialCount: number;
  incompleteCount: number;
  recent24hCount: number;
}

export interface StepDetail {
  /** 백엔드 ProcessingStep#name() — 값 유효성은 ProcessingStep 으로 좁힐 수 있음 */
  step: string;
  /** 백엔드 ProcessingStatus#name() — 값 유효성은 ProcessingStatus 로 좁힐 수 있음 */
  status: string;
  durationMs: number | null;
  attempt: number;
  reason: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface AttachmentDetail {
  attachmentId: number;
  filename: string;
  /** 백엔드 AttachmentStatus#name() — 'PENDING' | 'DOWNLOADING' | 'DOWNLOADED' | 'EXTRACTING' | 'EXTRACTED' | 'FAILED' | 'SKIPPED' */
  extractionStatus: string;
  embedded: boolean;
}

export interface ReferenceDetail {
  url: string;
  status: string;
  chunkCount: number;
}

export interface PolicyProcessingDetail {
  policyId: number;
  title: string;
  completeness: Completeness;
  steps: StepDetail[];
  attachments: AttachmentDetail[];
  references: ReferenceDetail[];
}

export interface ReprocessResult {
  queued: boolean;
  stepIds: number[];
  message: string;
}

export interface PolicyProcessingListParams {
  q?: string;
  region?: string;
  filter?: Filter;
  sort?: Sort;
  page?: number;
  size?: number;
}
