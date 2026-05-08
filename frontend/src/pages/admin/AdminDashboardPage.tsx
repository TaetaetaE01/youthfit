import { Mail, MessageSquareText, Zap, Download } from 'lucide-react';
import AdminKpiCard from '@/components/admin/AdminKpiCard';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import {
  AdminCardShell,
  ChartPlaceholder,
  DonutPlaceholder,
  TablePlaceholder,
} from '@/components/admin/AdminPlaceholders';
import { useAdminPing } from '@/hooks/queries/useAdminPing';

export default function AdminDashboardPage() {
  const ping = useAdminPing();

  const status = (
    <div className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 shadow-sm md:inline-flex">
      <span
        className={[
          'h-1.5 w-1.5 rounded-full',
          ping.isError
            ? 'bg-error-500'
            : ping.isLoading
              ? 'bg-amber-500 animate-pulse'
              : 'bg-success-500 shadow-[0_0_8px_rgba(34,197,94,0.5)]',
        ].join(' ')}
        aria-hidden
      />
      {ping.isLoading && <span>API 확인 중…</span>}
      {ping.isError && <span className="text-error-500">API 연결 실패</span>}
      {ping.data && (
        <span>
          API <strong className="text-slate-900">{ping.data.message}</strong>
        </span>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="관리자 대시보드"
        description="운영 추적 영역. 항목별 상세는 후속 출시 예정입니다."
        status={status}
      />

      {/* KPI 4개 — 후속 spec과 1:1 매칭 */}
      <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <AdminKpiCard
          icon={Mail}
          iconTone="brand"
          title="이메일 발송"
          value="--"
          hint="성공/실패/바운스 추적"
        />
        <AdminKpiCard
          icon={MessageSquareText}
          iconTone="success"
          title="Q&A 캐시 hit률"
          value="--%"
          hint="semantic-cache 매칭"
        />
        <AdminKpiCard
          icon={Zap}
          iconTone="warning"
          title="LLM 비용 (이번주)"
          value="--"
          hint="Q&A·가이드·임베딩"
        />
        <AdminKpiCard
          icon={Download}
          iconTone="error"
          title="Ingestion 신규"
          value="--"
          hint="수신 / 정규화 실패"
        />
      </section>

      {/* 메인 차트 + 도넛 */}
      <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <AdminCardShell
          className="xl:col-span-2"
          title="발송 / 캐시 추이"
          subtitle="일자별 합계 — 데이터 연결 후 표시"
          action={
            <div className="flex gap-0.5 rounded-lg border border-slate-200 bg-slate-50 p-0.5 text-xs">
              <span className="rounded-md bg-white px-2.5 py-1 font-semibold text-slate-700 shadow-sm">
                7D
              </span>
              <span className="rounded-md px-2.5 py-1 text-slate-500 hover:text-slate-700">
                30D
              </span>
              <span className="rounded-md px-2.5 py-1 text-slate-500 hover:text-slate-700">
                90D
              </span>
            </div>
          }
        >
          <ChartPlaceholder label="시간별 추이 차트 placeholder" />
        </AdminCardShell>

        <AdminCardShell title="Q&A 캐시 적중률" subtitle="hit / total">
          <DonutPlaceholder label="Q&A 캐시 적중률 도넛 placeholder" />
        </AdminCardShell>
      </section>

      {/* 하단 테이블 2개 */}
      <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <AdminCardShell title="최근 발송 이메일" subtitle="최신 5건">
          <TablePlaceholder
            columns={['수신자', '제목', '상태', '시각']}
            label="최근 발송 이메일 테이블 placeholder"
          />
        </AdminCardShell>

        <AdminCardShell title="LLM 비용 분포" subtitle="모듈별">
          <TablePlaceholder
            columns={['모듈', '호출 수', '토큰', '비용']}
            label="LLM 비용 테이블 placeholder"
          />
        </AdminCardShell>
      </section>
    </div>
  );
}
