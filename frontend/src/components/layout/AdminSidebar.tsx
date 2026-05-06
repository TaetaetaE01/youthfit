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
      { to: '/admin/ingestion', label: 'Ingestion 헬스', icon: Download, soon: true },
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
    <aside className="flex w-64 shrink-0 flex-col bg-brand-900 text-slate-200">
      <Link
        to="/admin"
        className="flex items-center gap-2 px-6 py-5 text-lg font-bold text-white"
      >
        <span className="grid h-8 w-8 place-items-center rounded-lg bg-white/10">
          YF
        </span>
        YouthFit Admin
      </Link>

      <nav className="flex-1 space-y-6 px-3 py-2">
        {GROUPS.map((group) => (
          <div key={group.title}>
            <div className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
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
                      className="group flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-white/5 hover:text-white"
                    >
                      <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                      <span className="flex-1">{item.label}</span>
                      <ExternalLink className="h-3.5 w-3.5 opacity-60" aria-hidden />
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
                          'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
                          isActive
                            ? 'bg-white/10 font-semibold text-white'
                            : 'text-slate-300 hover:bg-white/5 hover:text-white',
                        ].join(' ')
                      }
                    >
                      <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                      <span className="flex-1">{item.label}</span>
                      {item.soon && (
                        <span className="rounded bg-white/10 px-1.5 py-0.5 text-[10px] font-medium text-slate-300">
                          준비중
                        </span>
                      )}
                      {!item.soon && (
                        <ChevronRight
                          className="h-3.5 w-3.5 opacity-0 transition-opacity group-hover:opacity-60"
                          aria-hidden
                        />
                      )}
                    </NavLink>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-white/5 px-6 py-4 text-[11px] text-slate-400">
        v0.1 · admin foundation
      </div>
    </aside>
  );
}
