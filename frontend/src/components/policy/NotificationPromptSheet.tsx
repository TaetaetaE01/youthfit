import { useEffect, useRef, useState } from 'react';
import { Bell, X, Loader2, Mail } from 'lucide-react';
import { cn } from '@/lib/cn';
import { useProfile } from '@/hooks/queries/useProfile';
import { useNotificationSettings } from '@/hooks/queries/useNotificationSettings';
import { useUpdateProfile } from '@/hooks/mutations/useUpdateProfile';
import { useUpdateNotificationSettings } from '@/hooks/mutations/useUpdateNotificationSettings';

interface NotificationPromptSheetProps {
  open: boolean;
  onClose: () => void;
  onSubscribed: (email: string) => void;
}

const DEFAULT_DAYS_BEFORE = 7;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function NotificationPromptSheet({
  open,
  onClose,
  onSubscribed,
}: NotificationPromptSheetProps) {
  const { data: profile } = useProfile();
  const { data: existingSettings } = useNotificationSettings();
  const updateProfile = useUpdateProfile();
  const updateNotification = useUpdateNotificationSettings();

  const existingEmail = profile?.email ?? '';
  const [isEditing, setIsEditing] = useState(!existingEmail);
  const [email, setEmail] = useState(existingEmail);
  const [error, setError] = useState<string | null>(null);
  const emailInputRef = useRef<HTMLInputElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    setEmail(existingEmail);
    setIsEditing(!existingEmail);
    setError(null);
  }, [open, existingEmail]);

  useEffect(() => {
    if (!open) return;
    if (isEditing) {
      setTimeout(() => emailInputRef.current?.focus(), 50);
    } else {
      setTimeout(() => closeButtonRef.current?.focus(), 50);
    }
  }, [open, isEditing]);

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const isPending = updateProfile.isPending || updateNotification.isPending;

  const finalizeSubscription = (targetEmail: string) => {
    updateNotification.mutate(
      {
        emailEnabled: true,
        daysBeforeDeadline: existingSettings?.daysBeforeDeadline ?? DEFAULT_DAYS_BEFORE,
        eligibilityRecommendationEnabled: existingSettings?.eligibilityRecommendationEnabled ?? false,
      },
      {
        onSuccess: () => {
          onSubscribed(targetEmail);
          onClose();
        },
        onError: () => setError('알림 신청에 실패했어요. 잠시 후 다시 시도해주세요.'),
      },
    );
  };

  const handleSubmit = () => {
    const trimmed = email.trim();
    if (!trimmed) {
      setError('이메일을 입력해주세요');
      return;
    }
    if (!EMAIL_PATTERN.test(trimmed)) {
      setError('올바른 이메일 형식이 아니에요');
      return;
    }

    if (trimmed === existingEmail) {
      finalizeSubscription(trimmed);
      return;
    }

    updateProfile.mutate(
      { nickname: profile?.nickname ?? '', email: trimmed },
      {
        onSuccess: () => finalizeSubscription(trimmed),
        onError: (err: unknown) => {
          const message = err instanceof Error ? err.message : '';
          setError(message.includes('409') ? '이미 사용 중인 이메일이에요' : '이메일 저장에 실패했어요');
        },
      },
    );
  };

  const handleConfirmExisting = () => {
    if (!existingEmail) return;
    finalizeSubscription(existingEmail);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="notification-prompt-title"
        className="w-full max-w-[420px] rounded-t-3xl bg-white p-6 shadow-xl sm:rounded-3xl"
      >
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-100">
              <Bell className="h-5 w-5 text-brand-800" />
            </div>
            <div>
              <h3 id="notification-prompt-title" className="text-lg font-bold text-neutral-900">
                마감일, 놓치지 않게 알려드릴게요
              </h3>
            </div>
          </div>
          <button
            ref={closeButtonRef}
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-neutral-400 transition-colors hover:bg-neutral-100 hover:text-neutral-700"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <p className="mt-3 text-sm text-neutral-500">
          신청 마감 {DEFAULT_DAYS_BEFORE}일 전, 이메일로 딱 한 번만 보내요. 마케팅 메일 없음.
        </p>

        {!isEditing && existingEmail && (
          <div className="mt-5">
            <div className="flex items-center gap-2 rounded-xl bg-neutral-50 px-4 py-3">
              <Mail className="h-4 w-4 shrink-0 text-neutral-400" />
              <span className="truncate text-sm text-neutral-700">{existingEmail}</span>
            </div>
            <button
              type="button"
              onClick={() => {
                setIsEditing(true);
                setError(null);
              }}
              className="mt-2 text-xs font-medium text-indigo-600 hover:underline"
            >
              다른 이메일 쓰기
            </button>
          </div>
        )}

        {isEditing && (
          <div className="mt-5">
            <label htmlFor="notification-email" className="mb-1.5 block text-sm font-medium text-neutral-700">
              알림 받을 이메일
            </label>
            <input
              ref={emailInputRef}
              id="notification-email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (error) setError(null);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleSubmit();
                }
              }}
              placeholder="example@email.com"
              className="h-11 w-full rounded-xl border border-neutral-200 px-3 text-sm text-neutral-900 outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
              aria-invalid={error != null}
              aria-describedby={error ? 'notification-email-error' : undefined}
            />
            {existingEmail && (
              <button
                type="button"
                onClick={() => {
                  setIsEditing(false);
                  setEmail(existingEmail);
                  setError(null);
                }}
                className="mt-2 text-xs font-medium text-neutral-500 hover:underline"
              >
                기존 이메일 사용
              </button>
            )}
          </div>
        )}

        {error && (
          <p id="notification-email-error" className="mt-3 text-xs text-error-500">
            {error}
          </p>
        )}

        <div className="mt-6 flex flex-col gap-2">
          <button
            type="button"
            onClick={isEditing ? handleSubmit : handleConfirmExisting}
            disabled={isPending}
            className={cn(
              'flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-brand-800 text-sm font-semibold text-white transition-colors hover:bg-brand-900',
              isPending && 'opacity-70',
            )}
          >
            {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            알림 받기
          </button>
          <button
            type="button"
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
