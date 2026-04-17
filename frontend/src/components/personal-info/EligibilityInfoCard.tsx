import { useMemo, useState, useEffect, type ReactNode } from 'react';
import { ChevronRight, ClipboardCheck, Check, Loader2 } from 'lucide-react';
import { cn } from '@/lib/cn';
import {
  MARITAL_STATUS_LABELS,
  EDUCATION_LABELS,
  EMPLOYMENT_KIND_LABELS,
  MAJOR_FIELD_LABELS,
  SPECIALIZATION_LABELS,
  type MaritalStatus,
  type Education,
  type EmploymentKind,
  type MajorField,
  type SpecializationField,
  type EligibilityProfile,
  type UpdateEligibilityProfileRequest,
} from '@/types/personalInfo';
import { useSidoList, useSigunguList } from '@/hooks/queries/useRegions';

type RowKey =
  | 'region'
  | 'age'
  | 'marital'
  | 'education'
  | 'income'
  | 'employment'
  | 'major'
  | 'specialization';

interface EligibilityInfoCardProps {
  profile: EligibilityProfile;
  onUpdate: (data: UpdateEligibilityProfileRequest) => void;
  isUpdating?: boolean;
}

const MARITAL_LIST: MaritalStatus[] = ['MARRIED', 'SINGLE'];
const EDUCATION_LIST: Education[] = [
  'UNDER_HIGH', 'HIGH_SCHOOL_IN', 'HIGH_SCHOOL_EXPECTED', 'HIGH_SCHOOL_GRAD',
  'COLLEGE_IN', 'COLLEGE_EXPECTED', 'COLLEGE_GRAD', 'GRADUATE', 'OTHER',
];
const EMPLOYMENT_LIST: EmploymentKind[] = [
  'EMPLOYEE', 'SELF_EMPLOYED', 'UNEMPLOYED', 'FREELANCER',
  'DAILY_WORKER', 'ENTREPRENEUR', 'PART_TIME', 'FARMER', 'OTHER',
];
const MAJOR_LIST: MajorField[] = [
  'HUMANITIES', 'SOCIAL', 'ECONOMICS', 'NATURAL',
  'ENGINEERING', 'ARTS', 'AGRICULTURE', 'OTHER',
];
const SPEC_LIST: SpecializationField[] = [
  'SME', 'WOMAN', 'BASIC_LIVELIHOOD', 'SINGLE_PARENT',
  'DISABLED', 'FARMER', 'MILITARY', 'LOCAL_TALENT', 'OTHER',
];

const onlyDigits = (v: string) => v.replace(/[^0-9]/g, '');
const formatWithCommas = (v: string) => {
  const digits = onlyDigits(v);
  return digits === '' ? '' : Number(digits).toLocaleString();
};
const parseNullableNumber = (v: string): number | null => {
  const digits = onlyDigits(v);
  return digits === '' ? null : Number(digits);
};

function ProgressRing({ value }: { value: number }) {
  const r = 26;
  const c = 2 * Math.PI * r;
  const clamped = Math.max(0, Math.min(100, value));
  const offset = c * (1 - clamped / 100);
  return (
    <div className="relative h-16 w-16 shrink-0">
      <svg viewBox="0 0 64 64" className="h-full w-full -rotate-90" aria-hidden="true">
        <defs>
          <linearGradient id="ring-grad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#6366F1" />
            <stop offset="100%" stopColor="#8B5CF6" />
          </linearGradient>
        </defs>
        <circle cx="32" cy="32" r={r} fill="none" stroke="#EEF0F7" strokeWidth="6" />
        <circle
          cx="32"
          cy="32"
          r={r}
          fill="none"
          stroke="url(#ring-grad)"
          strokeWidth="6"
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 400ms ease' }}
        />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-indigo-600">
        {clamped}%
      </span>
    </div>
  );
}

function Chip({
  label,
  selected,
  onClick,
}: {
  label: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={cn(
        'inline-flex h-9 items-center justify-center gap-1 rounded-lg border px-3 text-xs font-semibold transition-all',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1',
        selected
          ? 'border-indigo-500 bg-indigo-50 text-indigo-600'
          : 'border-neutral-200 bg-white text-neutral-600 hover:border-neutral-300 hover:text-neutral-900',
      )}
    >
      {selected && <Check className="h-3 w-3" strokeWidth={3} />}
      {label}
    </button>
  );
}

function Row({
  label,
  value,
  filled,
  open,
  onToggle,
  children,
}: {
  label: string;
  value: string;
  filled: boolean;
  open: boolean;
  onToggle: () => void;
  children?: ReactNode;
}) {
  return (
    <div className={cn('rounded-xl', open && 'bg-neutral-50 ring-1 ring-inset ring-neutral-100')}>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex min-h-11 w-full items-center justify-between gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-neutral-50"
      >
        <span className="text-sm text-neutral-600">{label}</span>
        <span className="flex items-center gap-1.5">
          <span
            className={cn(
              'truncate text-sm font-medium',
              filled ? 'text-neutral-900' : 'text-indigo-600',
            )}
          >
            {filled ? value : '+ 추가'}
          </span>
          <ChevronRight
            className={cn(
              'h-4 w-4 shrink-0 text-neutral-400 transition-transform duration-200',
              open && 'rotate-90',
            )}
          />
        </span>
      </button>
      {open && (
        <div className="border-t border-neutral-100 px-3 py-3">
          {children}
        </div>
      )}
    </div>
  );
}

function GroupHeading({ children }: { children: ReactNode }) {
  return (
    <h4 className="mb-1 mt-5 px-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-neutral-400 first:mt-0">
      {children}
    </h4>
  );
}

export default function EligibilityInfoCard({ profile, onUpdate, isUpdating }: EligibilityInfoCardProps) {
  const [open, setOpen] = useState<RowKey | null>(null);

  const [ageDraft, setAgeDraft] = useState('');
  const [incomeMinDraft, setIncomeMinDraft] = useState('');
  const [incomeMaxDraft, setIncomeMaxDraft] = useState('');
  const [sidoDraft, setSidoDraft] = useState<string | null>(null);
  const [sigunguDraft, setSigunguDraft] = useState<string | null>(null);

  const { data: sidoList = [], isLoading: sidoLoading } = useSidoList();
  const { data: sigunguList = [], isLoading: sigunguLoading } = useSigunguList(sidoDraft);

  useEffect(() => {
    setSidoDraft(profile.sidoCode);
    setSigunguDraft(profile.legalDongCode);
  }, [profile.sidoCode, profile.legalDongCode]);

  const handleToggle = (k: RowKey) => {
    if (open === k) {
      setOpen(null);
      return;
    }
    if (k === 'age') setAgeDraft(profile.age != null ? String(profile.age) : '');
    if (k === 'region') {
      setSidoDraft(profile.sidoCode);
      setSigunguDraft(profile.legalDongCode);
    }
    if (k === 'income') {
      setIncomeMinDraft(profile.incomeMin != null ? profile.incomeMin.toLocaleString() : '');
      setIncomeMaxDraft(profile.incomeMax != null ? profile.incomeMax.toLocaleString() : '');
    }
    setOpen(k);
  };

  const regionLabel = useMemo(() => {
    if (!profile.sidoName) return '';
    return profile.sigunguName ? `${profile.sidoName} · ${profile.sigunguName}` : profile.sidoName;
  }, [profile.sidoName, profile.sigunguName]);

  const ageLabel = profile.age != null ? `만 ${profile.age}세` : '';
  const maritalLabel = profile.maritalStatus ? MARITAL_STATUS_LABELS[profile.maritalStatus] : '';
  const educationLabel = profile.education ? EDUCATION_LABELS[profile.education] : '';
  const incomeLabel = useMemo(() => {
    const { incomeMin: mn, incomeMax: mx } = profile;
    if (mn == null && mx == null) return '';
    if (mn != null && mx != null) return `연 ${mn.toLocaleString()}~${mx.toLocaleString()}만원`;
    if (mn != null) return `연 ${mn.toLocaleString()}만원 이상`;
    return `연 ${mx!.toLocaleString()}만원 이하`;
  }, [profile]);
  const employmentLabel = profile.employmentKind ? EMPLOYMENT_KIND_LABELS[profile.employmentKind] : '';
  const majorLabel = profile.majorField ? MAJOR_FIELD_LABELS[profile.majorField] : '';
  const specLabel = profile.specializationField ? SPECIALIZATION_LABELS[profile.specializationField] : '';

  const filledCount = [
    profile.age != null,
    !!profile.legalDongCode,
    profile.maritalStatus != null,
    profile.education != null,
    profile.incomeMin != null || profile.incomeMax != null,
    profile.employmentKind != null,
    profile.majorField != null,
    profile.specializationField != null,
  ].filter(Boolean).length;
  const pct = Math.round((filledCount / 8) * 100);

  const saveAge = () => {
    if (ageDraft === '') {
      onUpdate({ age: null });
      setOpen(null);
      return;
    }
    const n = Number(ageDraft);
    if (Number.isNaN(n) || n < 0 || n > 99) return;
    onUpdate({ age: n });
    setOpen(null);
  };
  const saveRegion = () => {
    const code = sigunguDraft ?? sidoDraft ?? null;
    onUpdate({ legalDongCode: code });
    setOpen(null);
  };
  const clearRegion = () => {
    setSidoDraft(null);
    setSigunguDraft(null);
    onUpdate({ legalDongCode: null });
    setOpen(null);
  };
  const saveIncome = () => {
    onUpdate({
      incomeMin: parseNullableNumber(incomeMinDraft),
      incomeMax: parseNullableNumber(incomeMaxDraft),
    });
    setOpen(null);
  };

  return (
    <section
      aria-label="적합도 판정 정보"
      className="rounded-2xl bg-white p-6 shadow-card"
    >
      <div className="flex items-center gap-4">
        <ProgressRing value={pct} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <ClipboardCheck className="h-4 w-4 text-brand-800" />
            <h3 className="text-base font-semibold text-neutral-900">적합도 판정 정보</h3>
            {isUpdating && <Loader2 className="h-3.5 w-3.5 animate-spin text-neutral-400" />}
          </div>
          <p className="mt-1 text-xs leading-relaxed text-neutral-500">
            {pct === 100
              ? '모든 항목을 채우셨어요. 맞춤 추천이 더 정확해졌어요.'
              : '완성할수록 맞춤 추천이 정확해져요.'}
          </p>
        </div>
      </div>

      <div className="mt-5 space-y-1">
        <GroupHeading>기본</GroupHeading>

        <Row
          label="거주 지역"
          value={regionLabel}
          filled={!!profile.legalDongCode}
          open={open === 'region'}
          onToggle={() => handleToggle('region')}
        >
          <div className="space-y-3">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-neutral-600">시/도</label>
              <select
                value={sidoDraft ?? ''}
                onChange={(e) => {
                  const v = e.target.value || null;
                  setSidoDraft(v);
                  setSigunguDraft(null);
                }}
                disabled={sidoLoading}
                className="h-10 w-full rounded-lg border border-neutral-200 bg-white px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
              >
                <option value="">선택</option>
                {sidoList.map((r) => (
                  <option key={r.code} value={r.code}>{r.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-neutral-600">
                시/군/구 <span className="text-neutral-400">(선택)</span>
              </label>
              <select
                value={sigunguDraft ?? ''}
                onChange={(e) => setSigunguDraft(e.target.value || null)}
                disabled={!sidoDraft || sigunguLoading}
                className="h-10 w-full rounded-lg border border-neutral-200 bg-white px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
              >
                <option value="">전체</option>
                {sigunguList.map((r) => (
                  <option key={r.code} value={r.code}>{r.name}</option>
                ))}
              </select>
            </div>
            <div className="flex justify-between">
              <button
                type="button"
                onClick={clearRegion}
                className="rounded-lg px-3 py-1.5 text-xs font-semibold text-neutral-500 transition-colors hover:bg-neutral-100"
              >
                지우기
              </button>
              <button
                type="button"
                onClick={saveRegion}
                disabled={!sidoDraft}
                className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-indigo-700 disabled:opacity-50"
              >
                완료
              </button>
            </div>
          </div>
        </Row>

        <Row
          label="나이"
          value={ageLabel}
          filled={profile.age != null}
          open={open === 'age'}
          onToggle={() => handleToggle('age')}
        >
          <div className="flex items-center gap-2">
            <div className="flex h-10 w-32 items-center gap-1 rounded-lg border border-neutral-200 px-3 focus-within:border-indigo-500 focus-within:ring-1 focus-within:ring-indigo-500">
              <span className="shrink-0 text-sm text-neutral-400">만</span>
              <input
                type="text"
                inputMode="numeric"
                maxLength={2}
                value={ageDraft}
                onChange={(e) => setAgeDraft(onlyDigits(e.target.value))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') saveAge();
                }}
                placeholder="나이"
                className="w-full border-0 bg-transparent text-sm outline-none"
              />
              <span className="shrink-0 text-sm text-neutral-500">세</span>
            </div>
            <button
              type="button"
              onClick={saveAge}
              className="ml-auto rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-indigo-700"
            >
              완료
            </button>
          </div>
        </Row>

        <Row
          label="혼인 여부"
          value={maritalLabel}
          filled={profile.maritalStatus != null}
          open={open === 'marital'}
          onToggle={() => handleToggle('marital')}
        >
          <div className="flex flex-wrap gap-2">
            {MARITAL_LIST.map((v) => (
              <Chip
                key={v}
                label={MARITAL_STATUS_LABELS[v]}
                selected={profile.maritalStatus === v}
                onClick={() => {
                  onUpdate({ maritalStatus: v });
                  setOpen(null);
                }}
              />
            ))}
          </div>
        </Row>

        <GroupHeading>교육·소득</GroupHeading>

        <Row
          label="학력"
          value={educationLabel}
          filled={profile.education != null}
          open={open === 'education'}
          onToggle={() => handleToggle('education')}
        >
          <div className="flex flex-wrap gap-2">
            {EDUCATION_LIST.map((v) => (
              <Chip
                key={v}
                label={EDUCATION_LABELS[v]}
                selected={profile.education === v}
                onClick={() => {
                  onUpdate({ education: v });
                  setOpen(null);
                }}
              />
            ))}
          </div>
        </Row>

        <Row
          label="연소득"
          value={incomeLabel}
          filled={profile.incomeMin != null || profile.incomeMax != null}
          open={open === 'income'}
          onToggle={() => handleToggle('income')}
        >
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <input
                type="text"
                inputMode="numeric"
                value={incomeMinDraft}
                onChange={(e) => setIncomeMinDraft(formatWithCommas(e.target.value))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') saveIncome();
                }}
                placeholder="최소"
                className="h-10 w-full rounded-lg border border-neutral-200 px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
              />
              <span className="shrink-0 text-sm text-neutral-400">~</span>
              <input
                type="text"
                inputMode="numeric"
                value={incomeMaxDraft}
                onChange={(e) => setIncomeMaxDraft(formatWithCommas(e.target.value))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') saveIncome();
                }}
                placeholder="최대"
                className="h-10 w-full rounded-lg border border-neutral-200 px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
              />
              <span className="shrink-0 text-sm text-neutral-500">만원</span>
            </div>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={saveIncome}
                className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-indigo-700"
              >
                완료
              </button>
            </div>
          </div>
        </Row>

        <GroupHeading>활동</GroupHeading>

        <Row
          label="취업 상태"
          value={employmentLabel}
          filled={profile.employmentKind != null}
          open={open === 'employment'}
          onToggle={() => handleToggle('employment')}
        >
          <div className="flex flex-wrap gap-2">
            {EMPLOYMENT_LIST.map((v) => (
              <Chip
                key={v}
                label={EMPLOYMENT_KIND_LABELS[v]}
                selected={profile.employmentKind === v}
                onClick={() => {
                  onUpdate({ employmentKind: v });
                  setOpen(null);
                }}
              />
            ))}
          </div>
        </Row>

        <Row
          label="전공 분야"
          value={majorLabel}
          filled={profile.majorField != null}
          open={open === 'major'}
          onToggle={() => handleToggle('major')}
        >
          <div className="flex flex-wrap gap-2">
            {MAJOR_LIST.map((v) => (
              <Chip
                key={v}
                label={MAJOR_FIELD_LABELS[v]}
                selected={profile.majorField === v}
                onClick={() => {
                  onUpdate({ majorField: v });
                  setOpen(null);
                }}
              />
            ))}
          </div>
        </Row>

        <Row
          label="특화 분야"
          value={specLabel}
          filled={profile.specializationField != null}
          open={open === 'specialization'}
          onToggle={() => handleToggle('specialization')}
        >
          <div className="flex flex-wrap gap-2">
            {SPEC_LIST.map((v) => (
              <Chip
                key={v}
                label={SPECIALIZATION_LABELS[v]}
                selected={profile.specializationField === v}
                onClick={() => {
                  onUpdate({ specializationField: v });
                  setOpen(null);
                }}
              />
            ))}
          </div>
        </Row>
      </div>
    </section>
  );
}
