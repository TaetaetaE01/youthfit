import { useEffect, useState } from 'react';
import {
  AdminSearchInput,
  AdminSelect,
  SegmentedControl,
  ToggleChip,
} from '@/components/admin/AdminControls';
import type { EmailAttemptStatus, EmailAttemptType } from '@/apis/admin.email.api';

export type EmailFilter = {
  range: '7D' | '30D' | '90D';
  statuses: EmailAttemptStatus[];
  emailType?: EmailAttemptType;
  recipient: string;
};

const STATUS_LIST: readonly EmailAttemptStatus[] = [
  'SENT',
  'DELIVERED',
  'BOUNCED',
  'COMPLAINED',
  'FAILED',
];

const STATUS_TONE: Record<
  EmailAttemptStatus,
  'indigo' | 'emerald' | 'amber' | 'rose' | 'slate'
> = {
  SENT: 'indigo',
  DELIVERED: 'emerald',
  BOUNCED: 'amber',
  COMPLAINED: 'rose',
  FAILED: 'rose',
};

const RANGE_OPTIONS = [
  { value: '7D' as const, label: '7D' },
  { value: '30D' as const, label: '30D' },
  { value: '90D' as const, label: '90D' },
];

const TYPE_OPTIONS = [
  { value: 'DEADLINE' as const, label: '마감 알림 (DEADLINE)' },
  { value: 'RECOMMENDATION' as const, label: '추천 (RECOMMENDATION)' },
];

export function EmailFilterBar({
  value,
  onChange,
}: {
  value: EmailFilter;
  onChange: (next: EmailFilter) => void;
}) {
  const [recipient, setRecipient] = useState(value.recipient);

  useEffect(() => {
    setRecipient(value.recipient);
  }, [value.recipient]);

  const toggleStatus = (s: EmailAttemptStatus) => {
    const next = value.statuses.includes(s)
      ? value.statuses.filter((x) => x !== s)
      : [...value.statuses, s];
    onChange({ ...value, statuses: next });
  };

  const clearStatuses = () => onChange({ ...value, statuses: [] });

  const commitRecipient = () => {
    if (recipient !== value.recipient) {
      onChange({ ...value, recipient });
    }
  };

  return (
    <section
      aria-label="발송 추적 필터"
      className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-card"
    >
      <div className="flex flex-wrap items-center gap-3">
        <SegmentedControl
          ariaLabel="기간"
          value={value.range}
          onChange={(r) => onChange({ ...value, range: r })}
          options={RANGE_OPTIONS}
        />

        <AdminSelect
          ariaLabel="이메일 타입"
          value={value.emailType ?? ''}
          onChange={(v) =>
            onChange({ ...value, emailType: (v || undefined) as EmailAttemptType | undefined })
          }
          options={TYPE_OPTIONS}
          placeholder="모든 타입"
        />

        <AdminSearchInput
          aria-label="수신자 이메일"
          placeholder="수신자 이메일"
          value={recipient}
          onChange={(e) => setRecipient(e.target.value)}
          onBlur={commitRecipient}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commitRecipient();
          }}
          containerClassName="flex-1 min-w-[180px] max-w-xs"
          className="w-full"
        />

        <div className="ml-auto text-[11px] font-medium uppercase tracking-wider text-slate-400">
          상태 필터 {value.statuses.length > 0 && `(${value.statuses.length})`}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3">
        {STATUS_LIST.map((s) => {
          const active = value.statuses.includes(s);
          return (
            <ToggleChip
              key={s}
              active={active}
              tone={STATUS_TONE[s]}
              onToggle={() => toggleStatus(s)}
            >
              {s}
            </ToggleChip>
          );
        })}
        {value.statuses.length > 0 && (
          <button
            type="button"
            onClick={clearStatuses}
            className="ml-1 text-xs font-medium text-slate-500 underline-offset-2 hover:text-slate-700 hover:underline"
          >
            초기화
          </button>
        )}
      </div>
    </section>
  );
}
