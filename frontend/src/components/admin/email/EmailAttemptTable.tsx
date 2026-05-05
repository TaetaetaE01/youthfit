import { Link } from 'react-router-dom';
import type { EmailAttemptSummary } from '@/apis/admin.email.api';
import { useRedispatchEmail } from '@/hooks/mutations/useRedispatchEmail';

const STATUS_BADGE: Record<string, string> = {
  SENT: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-emerald-100 text-emerald-800',
  BOUNCED: 'bg-amber-100 text-amber-800',
  COMPLAINED: 'bg-red-100 text-red-800',
  FAILED: 'bg-red-200 text-red-900',
};

export function EmailAttemptTable({ rows }: { rows: EmailAttemptSummary[] }) {
  const redispatch = useRedispatchEmail();

  if (rows.length === 0) return (
    <div className="rounded-lg bg-white p-8 text-center text-sm text-slate-500 shadow-sm">
      조건에 맞는 발송 이력이 없습니다.
    </div>
  );

  return (
    <div className="overflow-x-auto rounded-lg bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-4 py-2 text-left">시각</th>
            <th className="px-4 py-2 text-left">수신자</th>
            <th className="px-4 py-2 text-left">타입</th>
            <th className="px-4 py-2 text-left">상태</th>
            <th className="px-4 py-2 text-left">제목</th>
            <th className="px-4 py-2 text-left">액션</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.id} className="border-b border-slate-100">
              <td className="px-4 py-2 text-slate-600">{r.sentAt.replace('T',' ').slice(0,16)}</td>
              <td className="px-4 py-2 text-slate-900">{r.recipient}</td>
              <td className="px-4 py-2 text-slate-600">{r.emailType}</td>
              <td className="px-4 py-2">
                <span className={`rounded-full px-2 py-0.5 text-xs ${STATUS_BADGE[r.status]}`}>
                  {r.status}
                </span>
              </td>
              <td className="px-4 py-2 max-w-md truncate text-slate-700">{r.subject}</td>
              <td className="px-4 py-2">
                <Link to={`/admin/email/${r.id}`} className="text-indigo-600 hover:underline">상세</Link>
                {r.status === 'FAILED' && (
                  <button className="ml-3 text-amber-700 hover:underline disabled:opacity-50"
                          onClick={() => redispatch.mutate(r.id)}
                          disabled={redispatch.isPending}>
                    재발송
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
