import { Link } from 'react-router-dom';
import { ChevronRight, RefreshCw } from 'lucide-react';
import type { EmailAttemptSummary } from '@/apis/admin.email.api';
import { useRedispatchEmail } from '@/hooks/mutations/useRedispatchEmail';

const STATUS_BADGE: Record<string, string> = {
  SENT: 'bg-indigo-50 text-indigo-700 ring-indigo-600/20',
  DELIVERED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  BOUNCED: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  COMPLAINED: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  FAILED: 'bg-rose-100 text-rose-800 ring-rose-700/30',
};

export function EmailAttemptTable({ rows }: { rows: EmailAttemptSummary[] }) {
  const redispatch = useRedispatchEmail();

  if (rows.length === 0) {
    return (
      <div className="grid place-items-center rounded-xl border border-dashed border-slate-200 bg-white px-6 py-10 text-center">
        <p className="text-sm font-medium text-slate-700">
          조건에 맞는 발송 이력이 없습니다.
        </p>
        <p className="mt-1 text-xs text-slate-500">
          기간이나 상태 필터를 조정해보세요.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200/80 bg-white shadow-card">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-semibold uppercase tracking-[0.08em] text-slate-500">
            <th className="px-4 py-2.5 text-left">시각</th>
            <th className="px-4 py-2.5 text-left">수신자</th>
            <th className="px-4 py-2.5 text-left">타입</th>
            <th className="px-4 py-2.5 text-left">상태</th>
            <th className="px-4 py-2.5 text-left">제목</th>
            <th className="px-4 py-2.5 text-right">액션</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((r) => (
            <tr key={r.id} className="transition-colors hover:bg-slate-50/60">
              <td className="px-4 py-3 font-mono text-xs tabular-nums text-slate-500">
                {r.sentAt.replace('T', ' ').slice(0, 16)}
              </td>
              <td className="px-4 py-3 text-slate-900">{r.recipient}</td>
              <td className="px-4 py-3 text-slate-600">{r.emailType}</td>
              <td className="px-4 py-3">
                <span
                  className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset ${STATUS_BADGE[r.status]}`}
                >
                  {r.status}
                </span>
              </td>
              <td className="max-w-md truncate px-4 py-3 text-slate-700">{r.subject}</td>
              <td className="px-4 py-3 text-right">
                <div className="inline-flex items-center gap-1">
                  {r.status === 'FAILED' && (
                    <button
                      onClick={() => redispatch.mutate(r.id)}
                      disabled={redispatch.isPending}
                      className="inline-flex items-center gap-1 rounded-md border border-amber-200 bg-amber-50 px-2 py-1 text-[11px] font-semibold text-amber-700 transition-colors hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-50"
                      title="재발송"
                    >
                      <RefreshCw
                        className={`h-3 w-3 ${redispatch.isPending ? 'animate-spin' : ''}`}
                        aria-hidden
                      />
                      재발송
                    </button>
                  )}
                  <Link
                    to={`/admin/email/${r.id}`}
                    className="inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-[11px] font-semibold text-brand-700 transition-colors hover:bg-brand-50"
                  >
                    상세
                    <ChevronRight className="h-3 w-3" aria-hidden />
                  </Link>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
