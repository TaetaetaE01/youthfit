import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router';
import { AlertCircle } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';

/* ─────────────────────── Kakao Logo SVG ─────────────────────── */

function KakaoLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M9 0.5C4.029 0.5 0 3.588 0 7.415c0 2.482 1.644 4.665 4.123 5.897l-1.05 3.847c-.093.34.297.612.591.413L7.82 14.82c.388.034.782.053 1.18.053 4.971 0 9-3.088 9-6.915S13.971 0.5 9 0.5z"
        fill="#191919"
      />
    </svg>
  );
}

/* ─────────────────────── LoginPage ─────────────────────── */

export default function LoginPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const errorParam = searchParams.get('error');
  const redirectTo = searchParams.get('redirect') ?? '/policies';

  const [showError, setShowError] = useState(errorParam === 'auth_failed');

  // Already authenticated -> redirect
  useEffect(() => {
    if (isAuthenticated) {
      navigate(redirectTo, { replace: true });
    }
  }, [isAuthenticated, navigate, redirectTo]);

  // Fade-out error after 3 seconds
  useEffect(() => {
    if (!showError) return;
    const timer = setTimeout(() => {
      setShowError(false);
      // Clean up the error param from URL
      searchParams.delete('error');
      setSearchParams(searchParams, { replace: true });
    }, 3000);
    return () => clearTimeout(timer);
  }, [showError, searchParams, setSearchParams]);

  const handleKakaoLogin = () => {
    // TODO: Replace with actual Kakao OAuth URL when Client ID is available
    // const kakaoAuthUrl = `https://kauth.kakao.com/oauth/authorize?client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&response_type=code&state=${encodeURIComponent(redirectTo)}`;
    // window.location.href = kakaoAuthUrl;
    alert('카카오 로그인은 준비 중입니다.');
  };

  return (
    <div className="min-h-[calc(100vh-theme(spacing.16))] flex items-center justify-center px-4">
      {/* Card */}
      <div className="w-full max-w-[400px] bg-white md:rounded-2xl md:shadow-card md:p-8 p-6 pt-16 md:pt-8">
        {/* Logo */}
        <div className="flex flex-col items-center gap-2 mb-6">
          <div className="flex items-center justify-center w-10 h-10 rounded-full bg-brand-100">
            <div className="w-5 h-5 rounded-full bg-brand-800" />
          </div>
          <span className="text-base font-bold text-brand-800">YouthFit</span>
        </div>

        {/* Title & Description */}
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-neutral-900 mb-2">로그인</h1>
          <p className="text-sm text-neutral-500">
            카카오 계정으로 간편하게 시작하세요
          </p>
        </div>

        {/* Error Message */}
        {showError && (
          <div
            role="alert"
            className="flex items-center gap-2.5 rounded-lg bg-error-500/10 text-error-500 px-4 py-3 mb-4 text-sm animate-[fadeOut_0.3s_ease-in_2.7s_forwards]"
          >
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>로그인에 실패했습니다. 다시 시도해 주세요.</span>
          </div>
        )}

        {/* Kakao Login Button */}
        <button
          type="button"
          onClick={handleKakaoLogin}
          aria-label="카카오 계정으로 로그인"
          className="flex items-center justify-center gap-2 w-full h-12 rounded-xl bg-[#FEE500] text-[#191919] text-sm font-semibold cursor-pointer transition-[filter] hover:brightness-95 active:brightness-90"
        >
          <KakaoLogo />
          카카오로 시작하기
        </button>

        {/* Guest browsing */}
        <div className="text-center mt-6">
          <p className="text-sm text-neutral-500 mb-1">
            회원가입 없이도 정책을 둘러볼 수 있어요.
          </p>
          <Link
            to="/policies"
            className="text-sm text-indigo-600 hover:underline font-medium"
          >
            정책 둘러보기
          </Link>
        </div>

        {/* Privacy consent */}
        <p className="text-center text-xs text-neutral-500 mt-8">
          로그인 시{' '}
          <a href="/terms" className="text-indigo-600 underline">
            이용약관
          </a>{' '}
          및{' '}
          <a href="/privacy" className="text-indigo-600 underline">
            개인정보처리방침
          </a>
          에 동의합니다.
        </p>
      </div>
    </div>
  );
}
