import type { RegionSidoCode } from '@/lib/labels/region';

export type PolicyCategory =
  | 'JOBS'
  | 'HOUSING'
  | 'EDUCATION'
  | 'WELFARE'
  | 'FINANCE'
  | 'CULTURE'
  | 'PARTICIPATION';

export type PolicyStatus = 'UPCOMING' | 'OPEN' | 'CLOSED';

export type DetailLevel = 'BASIC' | 'FULL';

export type SourceType = 'YOUTH_SEOUL_CRAWL' | 'BOKJIRO_CENTRAL' | 'YOUTH_CENTER';

export type EligibilityResult = 'LIKELY_ELIGIBLE' | 'UNCERTAIN' | 'LIKELY_INELIGIBLE';

/* ── Policy ── */

export interface PolicySubRegion {
  code: string;
  sidoCode: string;
  sidoName: string;
  name: string;
}

export interface Policy {
  id: number;
  title: string;
  summary: string;
  category: PolicyCategory;
  regionCode: string;
  subRegions: string[];
  applyStart: string | null;
  applyEnd: string | null;
  referenceYear: number | null;
  status: PolicyStatus;
  detailLevel: DetailLevel;
  organization: string | null;
  sourceType: SourceType | null;
  sourceLabel: string | null;
}

export interface PolicyAttachment {
  id: number;
  name: string;
  url: string;
  mediaType: string | null;
}

export interface PolicyReferenceSite {
  name: string;
  url: string;
}

export interface PolicyApplyMethod {
  stageName: string;
  description: string | null;
}

export interface PolicyEnrichmentSections {
  supportTarget: string | null;
  supportContent: string | null;
  applyMethod: string | null;
  requiredDocuments: string | null;
  deadlineNote: string | null;
  // NEW
  policyOverview: string | null;
  eligibilityCriteria: string | null;
  operatingOrganization: string | null;
  contactPhone: string | null;
}

export interface PolicyEnrichmentAttachment {
  name: string;
  url: string;
}

export interface PolicyEnrichment {
  sourceUrl: string;
  fetchedAt: string;
  sections: PolicyEnrichmentSections | null;
  extraAttachments: PolicyEnrichmentAttachment[];
}

export interface PolicyDetail extends Omit<Policy, 'subRegions'> {
  subRegions: PolicySubRegion[];
  body: string | null;
  supportTarget: string | null;
  selectionCriteria: string | null;
  supportContent: string | null;
  contact: string | null;
  referenceYear: number | null;
  supportCycle: string | null;
  provideType: string | null;
  // ── 신규 정책 상세 11개 ──
  screeningMethod: string | null;
  submissionDocuments: string | null;
  additionalQualification: string | null;
  participationRestriction: string | null;
  additionalNotes: string | null;
  businessPeriodStart: string | null;
  businessPeriodEnd: string | null;
  businessPeriodNote: string | null;
  supportScale: number | null;
  firstComeFirstServed: boolean;
  applyUrl: string | null;
  lifeTags: string[];
  themeTags: string[];
  targetTags: string[];
  attachments: PolicyAttachment[];
  referenceSites: PolicyReferenceSite[];
  applyMethods: PolicyApplyMethod[];
  sourceUrl: string | null;
  createdAt: string;
  updatedAt: string;
  enrichment: PolicyEnrichment | null;
}

export interface PolicyPage {
  content: Policy[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
  hasNext: boolean;
}

/* ── Guide ── */

export type GuideSourceField =
  | 'SUPPORT_TARGET'
  | 'SELECTION_CRITERIA'
  | 'SUPPORT_CONTENT'
  | 'BODY'
  | 'ATTACHMENT'
  | 'ENRICHMENT';

export interface AttachmentRef {
  attachmentId: number;
  pageStart: number | null;
  pageEnd: number | null;
}

export interface GuideGroup {
  label: string | null;
  items: string[];
}

export interface GuidePairedSection {
  groups: GuideGroup[];
}

export interface GuideListSection {
  items: string[];
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}

export interface GuidePitfall {
  text: string;
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}

export interface GuideHighlight {
  text: string;
  sourceField: GuideSourceField;
  attachmentRef: AttachmentRef | null;
}

export interface Guide {
  policyId: number;
  oneLineSummary: string;
  highlights: GuideHighlight[];
  target: GuidePairedSection | null;
  criteria: GuidePairedSection | null;
  content: GuidePairedSection | null;
  applyMethod: GuideListSection | null;
  deadlineNote: GuideListSection | null;
  requiredDocuments: GuideListSection | null;
  contact: GuideListSection | null;
  pitfalls: GuidePitfall[];
  updatedAt: string;
}

/* ── Eligibility ── */

export type UncertainReason = 'MISSING_FIELD' | 'AMBIGUOUS_SOURCE' | null;

export interface RequirementView {
  operator: string;
  displayText: string;
}

export interface UserValueView {
  raw: string;
  displayText: string;
}

export interface SourceView {
  snippet: string | null;
}

export interface CriterionItem {
  field: string;
  label: string;
  result: EligibilityResult;
  uncertainReason: UncertainReason;
  requirement: RequirementView;
  userValue: UserValueView | null;
  verdictText: string;
  source: SourceView;
}

export interface GroupedCriteria {
  ineligible: CriterionItem[];
  uncertain: CriterionItem[];
  eligible: CriterionItem[];
}

export interface SummaryView {
  headline: string;
  eligibleCount: number;
  uncertainCount: number;
  ineligibleCount: number;
}

export interface EligibilityResponse {
  policyId: number;
  policyTitle: string;
  overallResult: EligibilityResult;
  summary: SummaryView;
  criteria: GroupedCriteria;
  disclaimer: string;
}

/* ── Bookmark ── */

export interface BookmarkPolicy {
  id: number;
  title: string;
  category: string;
  status: string;
  applyEnd: string | null;
}

export interface Bookmark {
  bookmarkId: number;
  policy: BookmarkPolicy;
  createdAt: string;
}

export interface BookmarkPage {
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
  content: Bookmark[];
}

export interface BookmarkIdPair {
  bookmarkId: number;
  policyId: number;
}

/* ── User ── */

export interface UserProfile {
  id: number;
  email: string | null;
  nickname: string;
  profileImageUrl: string | null;
  role: 'USER' | 'ADMIN';
  createdAt: string;
}

export interface UpdateProfileRequest {
  nickname?: string;
  email?: string | null;
}

export interface NotificationSettings {
  emailEnabled: boolean;
  daysBeforeDeadline: number;
  recommendationEnabled: boolean;
  interestCategories: PolicyCategory[];
  interestRegions: RegionSidoCode[];
}

/* ── Common ── */

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string };
}

/* ── Labels & Options ── */

export const CATEGORY_LABELS: Record<PolicyCategory, string> = {
  JOBS: '취업',
  HOUSING: '주거',
  EDUCATION: '교육',
  WELFARE: '복지',
  FINANCE: '금융',
  CULTURE: '문화',
  PARTICIPATION: '참여',
};

export const STATUS_LABELS: Record<PolicyStatus, string> = {
  UPCOMING: '예정',
  OPEN: '모집중',
  CLOSED: '마감',
};

export const REGION_OPTIONS = [
  { value: 'SEOUL', label: '서울' },
  { value: 'BUSAN', label: '부산' },
  { value: 'DAEGU', label: '대구' },
  { value: 'INCHEON', label: '인천' },
  { value: 'GWANGJU', label: '광주' },
  { value: 'DAEJEON', label: '대전' },
  { value: 'ULSAN', label: '울산' },
  { value: 'SEJONG', label: '세종' },
  { value: 'GYEONGGI', label: '경기' },
  { value: 'GANGWON', label: '강원' },
  { value: 'CHUNGBUK', label: '충북' },
  { value: 'CHUNGNAM', label: '충남' },
  { value: 'JEONBUK', label: '전북' },
  { value: 'JEONNAM', label: '전남' },
  { value: 'GYEONGBUK', label: '경북' },
  { value: 'GYEONGNAM', label: '경남' },
  { value: 'JEJU', label: '제주' },
] as const;

export function getRegionName(
  regionCode: string,
  sourceType?: SourceType | null,
): string {
  if (regionCode === '전국') {
    if (sourceType === 'BOKJIRO_CENTRAL') return '전국(중앙정부)';
    if (sourceType === 'YOUTH_CENTER') return '전국(여러 지자체)';
    return '전국';
  }
  return REGION_OPTIONS.find((r) => r.value === regionCode)?.label ?? regionCode;
}

/* ── Policy Calendar ── */

export type PolicyCalendarItem = {
  id: number;
  title: string;
  category: PolicyCategory;
  applyStart: string | null;   // YYYY-MM-DD
  applyEnd: string | null;     // YYYY-MM-DD
  regionLabel: string;
};

export type PolicyCalendarResponse = {
  items: PolicyCalendarItem[];
};

export type PolicyCalendarPageResponse = {
  content: PolicyCalendarItem[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
  hasNext: boolean;
};
