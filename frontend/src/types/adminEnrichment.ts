/**
 * 관리자 enrichment 검토 콘솔용 타입 정의.
 *
 * 백엔드 응답 (presentation/dto/response) 과 1:1 매칭되며, 신규 필드를 추가할 때는
 * 백엔드 record 의 필드명을 그대로 따라간다.
 */

export type EnrichmentStatus =
  | 'OK'
  | 'NO_LINK'
  | 'FETCH_FAILED'
  | 'TOO_SHORT'
  | 'LLM_FAILED'
  | 'PARSE_FAILED'
  | 'LOW_CONFIDENCE';

export type DetailLevel = 'LITE' | 'MEDIUM' | 'FULL';

export type ReferenceSiteSource = 'AUTO' | 'ADMIN';

export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export type AttachmentStatus =
  | 'PENDING'
  | 'DOWNLOADING'
  | 'DOWNLOADED'
  | 'EXTRACTING'
  | 'EXTRACTED'
  | 'FAILED'
  | 'SKIPPED';

export type AttachmentSkipReason = 'SCANNED_PDF' | 'UNSUPPORTED_MIME' | 'OVERSIZED';

/** UrlResponse */
export interface ReferenceSite {
  name: string;
  url: string;
  source: ReferenceSiteSource;
}

/** EnrichmentJobResponse */
export interface EnrichmentJobView {
  id: number;
  policyId: number;
  status: JobStatus;
  attempt: number;
  requestedBy: string;
  errorMessage: string | null;
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  requestedUrls: ReferenceSite[];
}

/** EnrichmentCandidateResponse */
export interface CandidateView {
  id: number;
  title: string;
  organization: string;
  enrichmentStatus: EnrichmentStatus | null;
  confidence: number | null;
  detailLevel: DetailLevel;
  needsReview: boolean;
  latestJob: EnrichmentJobView | null;
}

/** EnrichmentCandidateSummaryResponse */
export interface CandidateSummary {
  total: number;
  needsReview: number;
  byStatus: Partial<Record<EnrichmentStatus, number>>;
  byDetailLevel: Partial<Record<DetailLevel, number>>;
}

/**
 * PolicyEnrichmentDetailResponse.EnrichmentResponse.SectionsResponse
 *
 * 백엔드 Sections 의 모든 필드를 포함한다. 모든 항목은 nullable.
 * 추가 키 접근을 허용하기 위해 인덱스 시그니처를 둔다.
 */
export interface EnrichmentSections {
  supportTarget: string | null;
  supportContent: string | null;
  eligibilityCriteria: string | null;
  applyMethod: string | null;
  requiredDocuments: string | null;
  deadlineNote: string | null;
  policyOverview: string | null;
  operatingOrganization: string | null;
  contactPhone: string | null;
  [key: string]: string | null;
}

/** PolicyEnrichmentDetailResponse.EnrichmentResponse */
export interface EnrichmentDetail {
  status: EnrichmentStatus | null;
  confidence: number | null;
  fetchedAt: string | null;
  extractor: string | null;
  sections: EnrichmentSections | null;
}

/** PolicyEnrichmentDetailResponse.AttachmentResponse */
export interface AttachmentView {
  id: number;
  name: string;
  mediaType: string | null;
  status: AttachmentStatus;
  skipReason: AttachmentSkipReason | null;
  error: string | null;
  retryCount: number;
  hasText: boolean;
}

/** PolicyEnrichmentDetailResponse */
export interface PolicyEnrichmentDetail {
  policyId: number;
  title: string;
  enrichment: EnrichmentDetail | null;
  detailLevel: DetailLevel;
  referenceSites: ReferenceSite[];
  recentJobs: EnrichmentJobView[];
  attachments: AttachmentView[];
  needsReview: boolean;
}

/** JobAcceptedResponse */
export interface JobAccepted {
  jobId: number;
  status: JobStatus;
}
