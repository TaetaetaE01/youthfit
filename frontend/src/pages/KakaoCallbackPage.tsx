import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { loginWithKakao } from '@/apis/auth.api';
import { useAuthStore } from '@/stores/authStore';

export default function KakaoCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [error, setError] = useState(false);

  useEffect(() => {
    const code = searchParams.get('code');
    const redirect = searchParams.get('state') || '/policies';

    if (!code) {
      setError(true);
      return;
    }

    loginWithKakao(code)
      .then((tokens) => {
        login(tokens.accessToken);
        navigate(redirect, { replace: true });
      })
      .catch(() => {
        setError(true);
        setTimeout(() => navigate('/login', { replace: true }), 2000);
      });
  }, []);

  if (error) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3">
        <p className="text-sm text-error-500">로그인에 실패했어요. 다시 시도해주세요.</p>
        <p className="text-xs text-gray-400">잠시 후 로그인 페이지로 이동합니다...</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
      <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
      <p className="text-sm font-medium text-gray-600">로그인 중이에요...</p>
    </div>
  );
}
