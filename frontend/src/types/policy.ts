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

export type EligibilityResult = 'LIKELY_ELIGIBLE' | 'UNCERTAIN' | 'LIKELY_INELIGIBLE';

/* ── Policy ── */

export interface Policy {
  id: number;
  title: string;
  summary: string;
  category: PolicyCategory;
  regionCode: string;
  applyStart: string | null;
  applyEnd: string | null;
  status: PolicyStatus;
  detailLevel: DetailLevel;
  organization: string | null;
}

export interface PolicyAttachment {
  name: string;
  url: string;
  mediaType: string | null;
}

export interface PolicyDetail extends Policy {
  body: string | null;
  supportTarget: string | null;
  selectionCriteria: string | null;
  supportContent: string | null;
  contact: string | null;
  lifeTags: string[];
  themeTags: string[];
  targetTags: string[];
  attachments: PolicyAttachment[];
  sourceUrl: string | null;
  createdAt: string;
  updatedAt: string;
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

export interface Guide {
  id: number;
  policyId: number;
  summaryHtml: string;
  createdAt: string;
  updatedAt: string;
}

/* ── Eligibility ── */

export interface CriterionItem {
  field: string;
  label: string;
  result: EligibilityResult;
  reason: string;
  sourceReference: string;
}

export interface EligibilityResponse {
  policyId: number;
  policyTitle: string;
  overallResult: EligibilityResult;
  criteria: CriterionItem[];
  missingFields: string[];
  disclaimer: string;
}

/* ── Q&A ── */

export interface QnaMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: string[];
  loading?: boolean;
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
  createdAt: string;
}

export interface UpdateProfileRequest {
  nickname?: string;
  email?: string | null;
}

export interface NotificationSettings {
  emailEnabled: boolean;
  daysBeforeDeadline: number;
  eligibilityRecommendationEnabled: boolean;
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

export function getRegionName(regionCode: string): string {
  if (regionCode === '전국') return '전국(중앙정부)';
  return REGION_OPTIONS.find((r) => r.value === regionCode)?.label ?? regionCode;
}
