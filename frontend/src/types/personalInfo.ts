import type { LucideIcon } from 'lucide-react';
import {
  Briefcase,
  Store,
  UserX,
  Laptop,
  Clock,
  Rocket,
  CalendarClock,
  Sprout,
  MoreHorizontal,
  BookOpen,
  Users,
  Calculator,
  FlaskConical,
  Cog,
  Palette,
  Wheat,
  Pencil,
  Building2,
  UserRound,
  PiggyBank,
  HeartHandshake,
  Accessibility,
  Tractor,
  Shield,
  MapPin,
  MessageCircle,
  Database,
  GraduationCap,
  ClipboardList,
  BadgeCheck,
  BookMarked,
} from 'lucide-react';

/* ─────────────────────────── Enums ─────────────────────────── */

export type MaritalStatus = 'MARRIED' | 'SINGLE';

export type Education =
  | 'UNDER_HIGH'
  | 'HIGH_SCHOOL_IN'
  | 'HIGH_SCHOOL_EXPECTED'
  | 'HIGH_SCHOOL_GRAD'
  | 'COLLEGE_IN'
  | 'COLLEGE_EXPECTED'
  | 'COLLEGE_GRAD'
  | 'GRADUATE'
  | 'OTHER';

export type EmploymentKind =
  | 'EMPLOYEE'
  | 'SELF_EMPLOYED'
  | 'UNEMPLOYED'
  | 'FREELANCER'
  | 'DAILY_WORKER'
  | 'ENTREPRENEUR'
  | 'PART_TIME'
  | 'FARMER'
  | 'OTHER';

export type MajorField =
  | 'HUMANITIES'
  | 'SOCIAL'
  | 'ECONOMICS'
  | 'NATURAL'
  | 'ENGINEERING'
  | 'ARTS'
  | 'AGRICULTURE'
  | 'OTHER';

export type SpecializationField =
  | 'SME'
  | 'WOMAN'
  | 'BASIC_LIVELIHOOD'
  | 'SINGLE_PARENT'
  | 'DISABLED'
  | 'FARMER'
  | 'MILITARY'
  | 'LOCAL_TALENT'
  | 'OTHER';


/* ─────────────────────────── Labels ─────────────────────────── */

export const MARITAL_STATUS_LABELS: Record<MaritalStatus, string> = {
  MARRIED: '기혼',
  SINGLE: '미혼',
};

export const EDUCATION_LABELS: Record<Education, string> = {
  UNDER_HIGH: '고졸 미만',
  HIGH_SCHOOL_IN: '고교 재학',
  HIGH_SCHOOL_EXPECTED: '고졸 예정',
  HIGH_SCHOOL_GRAD: '고교 졸업',
  COLLEGE_IN: '대학 재학',
  COLLEGE_EXPECTED: '대졸 예정',
  COLLEGE_GRAD: '대학 졸업',
  GRADUATE: '석·박사',
  OTHER: '기타',
};

export const EMPLOYMENT_KIND_LABELS: Record<EmploymentKind, string> = {
  EMPLOYEE: '재직자',
  SELF_EMPLOYED: '자영업자',
  UNEMPLOYED: '미취업자',
  FREELANCER: '프리랜서',
  DAILY_WORKER: '일용근로자',
  ENTREPRENEUR: '(예비)창업자',
  PART_TIME: '단기근로자',
  FARMER: '영농종사자',
  OTHER: '기타',
};

export const MAJOR_FIELD_LABELS: Record<MajorField, string> = {
  HUMANITIES: '인문계열',
  SOCIAL: '사회계열',
  ECONOMICS: '상경계열',
  NATURAL: '이학계열',
  ENGINEERING: '공학계열',
  ARTS: '예체능계열',
  AGRICULTURE: '농산업계열',
  OTHER: '기타',
};

export const SPECIALIZATION_LABELS: Record<SpecializationField, string> = {
  SME: '중소기업',
  WOMAN: '여성',
  BASIC_LIVELIHOOD: '기초생활수급자',
  SINGLE_PARENT: '한부모가정',
  DISABLED: '장애인',
  FARMER: '농업인',
  MILITARY: '군인',
  LOCAL_TALENT: '지역인재',
  OTHER: '기타',
};

/* ─────────────────────────── Icon option lists ─────────────────────────── */

export interface IconOption<T extends string> {
  value: T;
  label: string;
  icon: LucideIcon;
}

export const EMPLOYMENT_KIND_OPTIONS: IconOption<EmploymentKind>[] = [
  { value: 'EMPLOYEE', label: '재직자', icon: Briefcase },
  { value: 'SELF_EMPLOYED', label: '자영업자', icon: Store },
  { value: 'UNEMPLOYED', label: '미취업자', icon: UserX },
  { value: 'FREELANCER', label: '프리랜서', icon: Laptop },
  { value: 'DAILY_WORKER', label: '일용근로자', icon: Clock },
  { value: 'ENTREPRENEUR', label: '(예비)창업자', icon: Rocket },
  { value: 'PART_TIME', label: '단기근로자', icon: CalendarClock },
  { value: 'FARMER', label: '영농종사자', icon: Sprout },
  { value: 'OTHER', label: '기타', icon: MoreHorizontal },
];

export const MAJOR_FIELD_OPTIONS: IconOption<MajorField>[] = [
  { value: 'HUMANITIES', label: '인문계열', icon: BookOpen },
  { value: 'SOCIAL', label: '사회계열', icon: Users },
  { value: 'ECONOMICS', label: '상경계열', icon: Calculator },
  { value: 'NATURAL', label: '이학계열', icon: FlaskConical },
  { value: 'ENGINEERING', label: '공학계열', icon: Cog },
  { value: 'ARTS', label: '예체능계열', icon: Palette },
  { value: 'AGRICULTURE', label: '농산업계열', icon: Wheat },
  { value: 'OTHER', label: '기타', icon: Pencil },
];

export const SPECIALIZATION_OPTIONS: IconOption<SpecializationField>[] = [
  { value: 'SME', label: '중소기업', icon: Building2 },
  { value: 'WOMAN', label: '여성', icon: UserRound },
  { value: 'BASIC_LIVELIHOOD', label: '기초생활수급자', icon: PiggyBank },
  { value: 'SINGLE_PARENT', label: '한부모가정', icon: HeartHandshake },
  { value: 'DISABLED', label: '장애인', icon: Accessibility },
  { value: 'FARMER', label: '농업인', icon: Tractor },
  { value: 'MILITARY', label: '군인', icon: Shield },
  { value: 'LOCAL_TALENT', label: '지역인재', icon: MapPin },
  { value: 'OTHER', label: '기타', icon: Pencil },
];

/* ─────────────────────────── Summary card icons ─────────────────────────── */

export const SUMMARY_ICONS = {
  region: MapPin,
  age: MessageCircle,
  marital: Users,
  income: Database,
  education: GraduationCap,
  major: ClipboardList,
  employment: BadgeCheck,
  specialization: BookMarked,
} as const;

/* ─────────────────────────── Region ─────────────────────────── */

export type RegionLevel = 'SIDO' | 'SIGUNGU';

export interface Region {
  code: string;
  name: string;
  level: RegionLevel;
  parentCode: string | null;
}

/* ─────────────────────────── Eligibility Profile ─────────────────────────── */

export interface EligibilityProfile {
  id: number | null;
  userId: number;
  legalDongCode: string | null;
  sidoCode: string | null;
  sidoName: string | null;
  sigunguName: string | null;
  age: number | null;
  maritalStatus: MaritalStatus | null;
  incomeMin: number | null;
  incomeMax: number | null;
  education: Education | null;
  employmentKind: EmploymentKind | null;
  majorField: MajorField | null;
  specializationField: SpecializationField | null;
}

export interface UpdateEligibilityProfileRequest {
  legalDongCode?: string | null;
  age?: number | null;
  maritalStatus?: MaritalStatus | null;
  incomeMin?: number | null;
  incomeMax?: number | null;
  education?: Education | null;
  employmentKind?: EmploymentKind | null;
  majorField?: MajorField | null;
  specializationField?: SpecializationField | null;
}
