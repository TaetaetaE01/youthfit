import { useState } from 'react';
import type { EmailAttemptStatus, EmailAttemptType } from '@/apis/admin.email.api';

export type EmailFilter = {
  range: '7D' | '30D' | '90D';
  statuses: EmailAttemptStatus[];
  emailType?: EmailAttemptType;
  recipient: string;
};

export function EmailFilterBar({
  value, onChange,
}: { value: EmailFilter; onChange: (next: EmailFilter) => void; }) {
  const [recipient, setRecipient] = useState(value.recipient);

  return (
    <div className="flex flex-wrap gap-3 rounded-lg bg-white p-4 shadow-sm">
      <div className="flex gap-1">
        {(['7D','30D','90D'] as const).map(r => (
          <button key={r}
            className={`rounded-md px-3 py-1.5 text-sm ${
              value.range === r ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`}
            onClick={() => onChange({ ...value, range: r })}>{r}</button>
        ))}
      </div>
      <select className="rounded-md border border-slate-300 px-2 py-1 text-sm"
        value={value.emailType ?? ''}
        onChange={e => onChange({ ...value, emailType: e.target.value as EmailAttemptType || undefined })}>
        <option value="">모든 타입</option>
        <option value="DEADLINE">DEADLINE</option>
        <option value="RECOMMENDATION">RECOMMENDATION</option>
      </select>
      <select multiple className="rounded-md border border-slate-300 px-2 py-1 text-sm min-w-[160px]"
        value={value.statuses}
        onChange={e => {
          const opts = Array.from(e.target.selectedOptions).map(o => o.value as EmailAttemptStatus);
          onChange({ ...value, statuses: opts });
        }}>
        {(['SENT','DELIVERED','BOUNCED','COMPLAINED','FAILED'] as const).map(s =>
          <option key={s} value={s}>{s}</option>)}
      </select>
      <input className="rounded-md border border-slate-300 px-3 py-1 text-sm"
        placeholder="수신자 이메일" value={recipient}
        onChange={e => setRecipient(e.target.value)}
        onBlur={() => onChange({ ...value, recipient })} />
    </div>
  );
}
