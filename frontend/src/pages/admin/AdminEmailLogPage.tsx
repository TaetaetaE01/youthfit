import { useMemo, useState } from 'react';
import { EmailFilterBar, type EmailFilter } from '@/components/admin/email/EmailFilterBar';
import { EmailKpiSection } from '@/components/admin/email/EmailKpiSection';
import { EmailDailyChart } from '@/components/admin/email/EmailDailyChart';
import { EmailAttemptTable } from '@/components/admin/email/EmailAttemptTable';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminPager from '@/components/admin/AdminPager';
import { Skeleton } from '@/components/admin/AdminSkeleton';
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
      <AdminPageHeader
        title="이메일 발송 추적"
        description="발송 시도와 결과를 일자·상태·수신자 기준으로 추적합니다."
      />
      <EmailFilterBar value={filter} onChange={f => { setFilter(f); setPage(0); }} />
      <EmailKpiSection />
      <EmailDailyChart from={from} to={to} />
      {isLoading ? (
        <Skeleton className="h-32 w-full" rounded="lg" />
      ) : (
        <>
          <EmailAttemptTable rows={data?.content ?? []} />
          <AdminPager
            page={page}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </div>
  );
}
