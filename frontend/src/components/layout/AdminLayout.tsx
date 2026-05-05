import { Link, NavLink, Outlet } from 'react-router-dom';

const MENU = [
  { to: '/admin', label: '대시보드', end: true },
  { to: '/admin/email', label: '이메일 발송', soon: true },
  { to: '/admin/qna-cache', label: 'Q&A 캐시 로그', soon: true },
  { to: '/admin/llm-cost', label: 'LLM 비용', soon: true },
  { to: '/admin/ingestion', label: 'Ingestion 헬스', soon: true },
];

export default function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="w-60 shrink-0 border-r bg-white p-4">
        <Link to="/admin" className="mb-6 block text-lg font-bold">
          YouthFit Admin
        </Link>
        <nav className="flex flex-col gap-1">
          {MENU.map((m) => (
            <NavLink
              key={m.to}
              to={m.to}
              end={m.end}
              className={({ isActive }) =>
                `rounded px-3 py-2 text-sm ${isActive ? 'bg-brand-50 font-semibold text-brand-700' : 'text-gray-700 hover:bg-gray-100'}`
              }
              onClick={(e) => {
                if (m.soon) {
                  e.preventDefault();
                  alert(`${m.label} — 준비 중입니다`);
                }
              }}
            >
              {m.label}
              {m.soon && <span className="ml-2 text-xs text-gray-400">(준비 중)</span>}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
