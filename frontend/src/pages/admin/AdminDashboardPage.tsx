import { useAdminPing } from '@/hooks/queries/useAdminPing';

const CARDS = [
  { title: '이메일 발송', desc: '성공/실패/바운스 추적' },
  { title: 'Q&A 캐시 로그', desc: 'semantic-cache hit/miss' },
  { title: 'LLM 비용', desc: '토큰/비용 집계' },
  { title: 'Ingestion 헬스', desc: '수신/정규화 통계' },
];

export default function AdminDashboardPage() {
  const ping = useAdminPing();

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">관리자 대시보드</h1>
        <p className="mt-1 text-sm text-gray-500">운영 추적 영역. 항목별 상세는 후속 출시 예정.</p>
      </header>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {CARDS.map((c) => (
          <div key={c.title} className="rounded border bg-white p-4">
            <h2 className="text-base font-semibold">{c.title}</h2>
            <p className="mt-1 text-sm text-gray-500">{c.desc}</p>
            <p className="mt-3 text-xs text-gray-400">준비 중</p>
          </div>
        ))}
      </section>

      <footer className="rounded border bg-white p-3 text-xs text-gray-500">
        {ping.isLoading && '어드민 API 확인 중…'}
        {ping.isError && (
          <span className="text-error-600">어드민 API 연결 실패 — 권한 또는 서버 상태를 확인하세요.</span>
        )}
        {ping.data && <span>어드민 API: <strong>{ping.data.message}</strong> · {ping.data.serverTime}</span>}
      </footer>
    </div>
  );
}
