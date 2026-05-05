import { useMemo, useState } from 'react';
import { EmailFilterBar, type EmailFilter } from '@/components/admin/email/EmailFilterBar';
import { EmailKpiSection } from '@/components/admin/email/EmailKpiSection';
import { EmailDailyChart } from '@/components/admin/email/EmailDailyChart';
import { EmailAttemptTable } from '@/components/admin/email/EmailAttemptTable';
import { useAdminEmailAttempts } from '@/hooks/queries/useAdminEmailAttempts';

const RANGE_DAYS: Record<EmailFilter['range'], number> = { '7D': 7, '30D': 30, '90D': 90 };

export default function AdminEmailLogPage() {
  const [filter, setFilter] = useState<EmailFilter>({
    range: '7D', statuses: [], recipient: '',
  });
  const [page, setPage] = useState(0);

  const { from, to } = useMemo(() => {
    const today = new Date();
    const fromDate = new Date(today); fromDate.setDate(today.getDate() - RANGE_DAYS[filter.range]);
    return { from: fromDate.toISOString().slice(0,10), to: today.toISOString().slice(0,10) };
  }, [filter.range]);

  const { data, isLoading } = useAdminEmailAttempts({
    from, to, statuses: filter.statuses, emailType: filter.emailType,
    recipient: filter.recipient || undefined, page, size: 20,
  });

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-slate-900">이메일 발송 추적</h1>
      <EmailFilterBar value={filter} onChange={f => { setFilter(f); setPage(0); }} />
      <EmailKpiSection />
      <EmailDailyChart from={from} to={to} />
      {isLoading ? (
        <div className="h-32 animate-pulse rounded-lg bg-slate-100" />
      ) : (
        <>
          <EmailAttemptTable rows={data?.content ?? []} />
          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-500">
              총 {data?.totalElements ?? 0}건 / {data?.totalPages ?? 0}페이지
            </span>
            <div className="space-x-2">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                className="rounded border px-3 py-1 disabled:opacity-50">이전</button>
              <button disabled={page + 1 >= (data?.totalPages ?? 1)}
                onClick={() => setPage(p => p + 1)}
                className="rounded border px-3 py-1 disabled:opacity-50">다음</button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
