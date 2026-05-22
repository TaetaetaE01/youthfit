import { Link, NavLink } from 'react-router-dom';
import {
  BookOpen,
  ChevronRight,
  DollarSign,
  Download,
  ExternalLink,
  LayoutDashboard,
  Mail,
  MessageSquareText,
  SearchCode,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

type InternalMenuItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
  soon?: boolean;
};

type ExternalMenuItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  external: true;
};

type MenuItem = InternalMenuItem | ExternalMenuItem;

type MenuGroup = {
  title: string;
  items: MenuItem[];
};

const BACKEND_ORIGIN =
  (import.meta.env.VITE_BACKEND_URL as string | undefined) ??
  (typeof window !== 'undefined'
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : 'http://localhost:8080');

const SWAGGER_URL = `${BACKEND_ORIGIN}/swagger-ui.html`;

const GROUPS: MenuGroup[] = [
  {
    title: '일반',
    items: [
      { to: '/admin', label: '대시보드', icon: LayoutDashboard, end: true },
    ],
  },
  {
    title: '추적',
    items: [
      { to: '/admin/email', label: '이메일 발송', icon: Mail },
      { to: '/admin/qna-cache', label: 'Q&A 캐시 로그', icon: MessageSquareText },
      { to: '/admin/llm-cost', label: 'LLM 비용', icon: DollarSign },
      { to: '/admin/ingestion', label: 'Ingestion 헬스', icon: Download },
      { to: '/admin/rag-preview', label: 'RAG 미리보기', icon: SearchCode },
    ],
  },
  {
    title: '외부 도구',
    items: [
      { href: SWAGGER_URL, label: 'API 문서 (Swagger)', icon: BookOpen, external: true },
    ],
  },
];

function isExternal(item: MenuItem): item is ExternalMenuItem {
  return 'external' in item && item.external === true;
}

export default function AdminSidebar() {
  return (
    <aside className="relative flex w-64 shrink-0 flex-col bg-gradient-to-b from-brand-900 via-brand-900 to-[#161E45] text-slate-200">
      {/* subtle radial glow */}
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-48 bg-[radial-gradient(circle_at_50%_-20%,rgba(99,102,241,0.18),transparent_60%)]"
        aria-hidden
      />

      <Link
        to="/admin"
        className="relative flex items-center gap-2.5 px-6 py-5 text-base font-bold text-white"
      >
        <span className="grid h-9 w-9 place-items-center rounded-lg bg-gradient-to-br from-indigo-500 to-brand-700 text-[11px] font-extrabold tracking-tight text-white shadow-[0_4px_12px_rgba(99,102,241,0.35)] ring-1 ring-white/10">
          YF
        </span>
        <span className="leading-tight">
          <span className="block text-[15px] font-bold">YouthFit</span>
          <span className="block text-[10px] font-medium uppercase tracking-[0.18em] text-indigo-300/80">
            Admin Console
          </span>
        </span>
      </Link>

      <nav className="relative flex-1 space-y-5 px-3 py-2">
        {GROUPS.map((group) => (
          <div key={group.title}>
            <div className="px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400/70">
              {group.title}
            </div>
            <ul className="space-y-0.5">
              {group.items.map((item) => (
                <li key={isExternal(item) ? item.href : item.to}>
                  {isExternal(item) ? (
                    <a
                      href={item.href}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-white/5 hover:text-white"
                    >
                      <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                      <span className="flex-1">{item.label}</span>
                      <ExternalLink
                        className="h-3.5 w-3.5 opacity-50 transition-opacity group-hover:opacity-80"
                        aria-hidden
                      />
                    </a>
                  ) : (
                    <NavLink
                      to={item.to}
                      end={item.end}
                      onClick={(e) => {
                        if (item.soon) {
                          e.preventDefault();
                          alert(`${item.label} — 준비 중입니다`);
                        }
                      }}
                      className={({ isActive }) =>
                        [
                          'group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-all',
                          isActive
                            ? 'bg-white/[0.08] font-semibold text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.06)]'
                            : 'text-slate-300/90 hover:bg-white/5 hover:text-white',
                        ].join(' ')
                      }
                    >
                      {({ isActive }) => (
                        <>
                          <span
                            className={[
                              'pointer-events-none absolute left-0 top-1/2 h-5 -translate-y-1/2 rounded-r-full bg-gradient-to-b from-indigo-400 to-indigo-500 transition-all',
                              isActive ? 'w-1 opacity-100' : 'w-0 opacity-0',
                            ].join(' ')}
                            aria-hidden
                          />
                          <item.icon
                            className={[
                              'h-4 w-4 shrink-0 transition-colors',
                              isActive ? 'text-indigo-300' : 'text-slate-400 group-hover:text-slate-200',
                            ].join(' ')}
                            aria-hidden
                          />
                          <span className="flex-1">{item.label}</span>
                          {item.soon && (
                            <span className="rounded bg-white/10 px-1.5 py-0.5 text-[10px] font-medium text-slate-300">
                              준비중
                            </span>
                          )}
                          {!item.soon && !isActive && (
                            <ChevronRight
                              className="h-3.5 w-3.5 opacity-0 transition-opacity group-hover:opacity-50"
                              aria-hidden
                            />
                          )}
                        </>
                      )}
                    </NavLink>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="relative border-t border-white/5 px-6 py-4">
        <div className="flex items-center gap-2 text-[11px] text-slate-400">
          <span className="h-1.5 w-1.5 rounded-full bg-success-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]" aria-hidden />
          <span>v0.1 · admin foundation</span>
        </div>
      </div>
    </aside>
  );
}
