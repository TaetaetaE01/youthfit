import { useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { X } from 'lucide-react';

interface LoginPromptModalProps {
  open: boolean;
  onClose: () => void;
  message?: string;
}

export default function LoginPromptModal({
  open,
  onClose,
  message = '로그인하면 이 기능을 이용할 수 있어요',
}: LoginPromptModalProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    setTimeout(() => cancelRef.current?.focus(), 0);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const handleLogin = () => {
    onClose();
    navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="login-prompt-title"
        className="w-full max-w-[360px] rounded-2xl bg-white p-6 shadow-xl"
      >
        <div className="flex items-start justify-between">
          <h3 id="login-prompt-title" className="text-lg font-bold text-neutral-900">
            로그인이 필요해요
          </h3>
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-neutral-400 transition-colors hover:bg-neutral-100 hover:text-neutral-700"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <p className="mt-2 text-sm text-neutral-500">{message}</p>
        <div className="mt-6 flex flex-col gap-3">
          <button
            onClick={handleLogin}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#FEE500] text-sm font-semibold text-[#191919] transition-[filter] hover:brightness-95"
          >
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
              <path
                fillRule="evenodd"
                clipRule="evenodd"
                d="M9 0.5C4.029 0.5 0 3.588 0 7.415c0 2.482 1.644 4.665 4.123 5.897l-1.05 3.847c-.093.34.297.612.591.413L7.82 14.82c.388.034.782.053 1.18.053 4.971 0 9-3.088 9-6.915S13.971 0.5 9 0.5z"
                fill="#191919"
              />
            </svg>
            카카오로 시작하기
          </button>
          <button
            ref={cancelRef}
            onClick={onClose}
            className="h-11 rounded-xl text-sm font-semibold text-neutral-600 transition-colors hover:bg-neutral-100"
          >
            나중에 할게요
          </button>
        </div>
      </div>
    </div>
  );
}
