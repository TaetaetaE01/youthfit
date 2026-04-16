import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Menu, X, User } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';

const NAV_LINKS = [
  { to: '/policies', label: '정책 목록' },
  { to: '/policies#eligibility', label: '적합도 판정' },
  { to: '/policies#qna', label: 'Q&A' },
];

export default function Navbar() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();

  return (
    <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-6">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100">
            <div className="h-3 w-3 rounded-full bg-brand-800" />
          </div>
          <span className="text-[17px] font-bold tracking-tight text-gray-900">YouthFit</span>
        </Link>

        {/* Nav links (desktop) */}
        <nav className="hidden items-center gap-8 md:flex">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`text-sm font-semibold transition-colors hover:text-brand-800 ${
                location.pathname === link.to ? 'text-brand-800' : 'text-gray-600'
              }`}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        {/* CTA + Mobile toggle */}
        <div className="flex items-center gap-3">
          {isAuthenticated ? (
            <Link
              to="/mypage"
              className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-100 text-brand-800 transition-colors hover:bg-brand-700 hover:text-white"
              aria-label="마이페이지"
            >
              <User className="h-5 w-5" />
            </Link>
          ) : (
            <Link
              to="/login"
              className="rounded-xl bg-brand-800 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
            >
              시작하기
            </Link>
          )}
          <button
            className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-600 md:hidden"
            onClick={() => setMobileOpen(!mobileOpen)}
            aria-label={mobileOpen ? '메뉴 닫기' : '메뉴 열기'}
          >
            {mobileOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
        </div>
      </div>

      {/* Mobile nav */}
      {mobileOpen && (
        <nav className="border-t border-gray-100 bg-white px-6 py-4 md:hidden">
          <div className="flex flex-col gap-3">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className="text-sm font-semibold text-gray-600 transition-colors hover:text-brand-800"
                onClick={() => setMobileOpen(false)}
              >
                {link.label}
              </Link>
            ))}
          </div>
        </nav>
      )}
    </header>
  );
}
