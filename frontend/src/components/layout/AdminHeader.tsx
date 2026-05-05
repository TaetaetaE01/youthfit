import { useNavigate } from 'react-router-dom';
import { Bell, BookOpen, LogOut, Search } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';

const BACKEND_ORIGIN =
  (import.meta.env.VITE_BACKEND_URL as string | undefined) ??
  (typeof window !== 'undefined'
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : 'http://localhost:8080');

const SWAGGER_URL = `${BACKEND_ORIGIN}/swagger-ui.html`;

export default function AdminHeader() {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.role);
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="flex h-16 items-center gap-4 border-b border-slate-200 bg-white px-6">
      <div className="text-sm font-medium text-slate-500">
        WELCOME!
      </div>

      <div className="ml-auto flex items-center gap-3">
        <div className="relative hidden md:block">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
          <input
            type="search"
            placeholder="검색..."
            className="h-9 w-64 rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm placeholder:text-slate-400 focus:border-brand-700 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-700/10"
            disabled
            aria-label="검색 (준비 중)"
          />
        </div>

        <a
          href={SWAGGER_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-xs font-medium text-slate-700 hover:border-brand-700 hover:bg-brand-50 hover:text-brand-700"
          title="Swagger UI 새 탭으로 열기"
        >
          <BookOpen className="h-4 w-4" aria-hidden />
          API 문서
        </a>

        <button
          type="button"
          className="relative grid h-9 w-9 place-items-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
          title="알림 (준비 중)"
          disabled
        >
          <Bell className="h-4 w-4" aria-hidden />
          <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-error-500" aria-hidden />
        </button>

        <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2 py-1.5">
          <div className="grid h-7 w-7 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-800">
            관
          </div>
          <div className="hidden text-xs leading-tight md:block">
            <div className="font-semibold text-slate-700">관리자</div>
            <div className="text-slate-400">{role ?? '—'}</div>
          </div>
        </div>

        <button
          type="button"
          onClick={handleLogout}
          className="grid h-9 w-9 place-items-center rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-error-500"
          title="로그아웃"
          aria-label="로그아웃"
        >
          <LogOut className="h-4 w-4" aria-hidden />
        </button>
      </div>
    </header>
  );
}
