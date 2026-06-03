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

export interface SourceTag {
  /** 백엔드 SourceType#name() — 'YOUTH_SEOUL_CRAWL' | 'BOKJIRO_CENTRAL' | 'YOUTH_CENTER' */
  code: string;
  /** 한글 표시명 */
  label: string;
}

export interface PolicyProcessingItem {
  policyId: number;
  title: string;
  region: string;
  completeness: Completeness;
  stepStatuses: Partial<Record<ProcessingStep, ProcessingStatus>>;
  attachments: AttachmentSummary;
  references: ReferenceSummary;
  sources: SourceTag[];
  updatedAt: string;
}

export interface PolicyProcessingList {
  /**
   * 검색·지역 SQL 필터까지 적용된 모집단 크기.
   * computed 빠른필터(RAG_FAILED 등)는 페이지 후 in-memory 처리되므로 totalCount 에 반영되지 않는다.
   */
  totalCount: number;
  page: number;
  size: number;
  /**
   * in-memory 빠른필터까지 적용한 페이지 결과 수 (= items.length).
   *
   * 백엔드는 페이지를 SQL 로 잘라온 뒤 메모리에서 한 번 더 필터링하므로 같은 totalCount 라도
   * 페이지마다 0 ~ size 사이로 변동될 수 있다. 빈 페이지가 나와도 다른 페이지에는 결과가 있을 수 있다.
   */
  filteredItemCount: number;
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
  sourceType?: string;
  filter?: Filter;
  sort?: Sort;
  page?: number;
  size?: number;
}
