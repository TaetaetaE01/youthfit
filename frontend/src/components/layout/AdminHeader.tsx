import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useMemo } from 'react';
import { Bell, BookOpen, ChevronRight, LogOut, Search } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';

const BACKEND_ORIGIN =
  (import.meta.env.VITE_BACKEND_URL as string | undefined) ??
  (typeof window !== 'undefined'
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : 'http://localhost:8080');

const SWAGGER_URL = `${BACKEND_ORIGIN}/swagger-ui.html`;

const SEGMENT_LABEL: Record<string, string> = {
  admin: '관리자',
  email: '이메일 발송',
  'qna-cache': 'Q&A 캐시',
  'llm-cost': 'LLM 비용',
  ingestion: 'Ingestion',
  failures: '실패',
};

function getGreeting(now = new Date()) {
  const h = now.getHours();
  if (h < 5) return '늦은 시간이에요';
  if (h < 12) return '좋은 아침입니다';
  if (h < 18) return '좋은 오후입니다';
  return '좋은 저녁입니다';
}

function buildCrumbs(pathname: string) {
  const segs = pathname.split('/').filter(Boolean);
  const crumbs: { label: string; to: string }[] = [];
  let acc = '';
  segs.forEach((seg) => {
    acc += `/${seg}`;
    const label =
      SEGMENT_LABEL[seg] ?? (Number.isFinite(Number(seg)) ? `#${seg}` : '상세');
    crumbs.push({ label, to: acc });
  });
  return crumbs;
}

export default function AdminHeader() {
  const navigate = useNavigate();
  const location = useLocation();
  const role = useAuthStore((s) => s.role);
  const logout = useAuthStore((s) => s.logout);

  const greeting = useMemo(() => getGreeting(), []);
  const crumbs = useMemo(() => buildCrumbs(location.pathname), [location.pathname]);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-4 border-b border-slate-200/80 bg-white/85 px-6 backdrop-blur-md">
      <div className="min-w-0">
        <div className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
          {greeting}, 관리자님
        </div>
        <nav
          aria-label="breadcrumb"
          className="mt-0.5 flex items-center gap-1 text-xs text-slate-500"
        >
          {crumbs.length === 0 ? (
            <span>관리자</span>
          ) : (
            crumbs.map((c, i) => {
              const isLast = i === crumbs.length - 1;
              return (
                <span key={c.to} className="inline-flex items-center gap-1">
                  {isLast ? (
                    <span className="font-semibold text-slate-700">{c.label}</span>
                  ) : (
                    <Link
                      to={c.to}
                      className="rounded-sm transition-colors hover:text-brand-700"
                    >
                      {c.label}
                    </Link>
                  )}
                  {!isLast && (
                    <ChevronRight className="h-3 w-3 text-slate-300" aria-hidden />
                  )}
                </span>
              );
            })
          )}
        </nav>
      </div>

      <div className="ml-auto flex items-center gap-2">
        <div className="relative hidden md:block">
          <Search
            className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
            aria-hidden
          />
          <input
            type="search"
            placeholder="검색 (준비 중)"
            className="h-9 w-56 rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm text-slate-700 placeholder:text-slate-400 focus:border-brand-700 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-700/10 disabled:cursor-not-allowed disabled:opacity-60"
            disabled
            aria-label="검색 (준비 중)"
          />
        </div>

        <a
          href={SWAGGER_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-xs font-medium text-slate-700 transition-colors hover:border-brand-700/40 hover:bg-brand-50 hover:text-brand-700"
          title="Swagger UI 새 탭으로 열기"
        >
          <BookOpen className="h-3.5 w-3.5" aria-hidden />
          API 문서
        </a>

        <button
          type="button"
          className="relative grid h-9 w-9 place-items-center rounded-lg border border-slate-200 bg-white text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
          title="알림 (준비 중)"
          aria-label="알림 (준비 중)"
          disabled
        >
          <Bell className="h-4 w-4" aria-hidden />
          <span
            className="absolute right-2 top-2 h-1.5 w-1.5 rounded-full bg-error-500 ring-2 ring-white"
            aria-hidden
          />
        </button>

        <div className="hidden h-9 w-px bg-slate-200 md:block" aria-hidden />

        <div className="flex items-center gap-2 rounded-lg px-1 py-0.5">
          <div className="grid h-8 w-8 place-items-center rounded-full bg-gradient-to-br from-brand-700 to-brand-900 text-xs font-bold text-white shadow-sm ring-2 ring-white">
            관
          </div>
          <div className="hidden text-xs leading-tight md:block">
            <div className="font-semibold text-slate-700">관리자</div>
            <div className="text-[10px] uppercase tracking-wide text-slate-400">
              {role ?? '—'}
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={handleLogout}
          className="grid h-9 w-9 place-items-center rounded-lg border border-slate-200 bg-white text-slate-500 transition-colors hover:border-error-500/30 hover:bg-error-500/5 hover:text-error-500"
          title="로그아웃"
          aria-label="로그아웃"
        >
          <LogOut className="h-4 w-4" aria-hidden />
        </button>
      </div>
    </header>
  );
}
