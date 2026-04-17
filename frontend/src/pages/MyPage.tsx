import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  User,
  Pencil,
  Bookmark as BookmarkIcon,
  Bell,
  Loader2,
  AlertCircle,
  Save,
  ClipboardCheck,
  Sparkles,
  Mail,
} from 'lucide-react';
import { cn } from '@/lib/cn';
import { useAuthStore } from '@/stores/authStore';
import { useProfile } from '@/hooks/queries/useProfile';
import { useBookmarks } from '@/hooks/queries/useBookmarks';
import { useNotificationSettings } from '@/hooks/queries/useNotificationSettings';
import { useUpdateProfile } from '@/hooks/mutations/useUpdateProfile';
import { useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
import { useUpdateNotificationSettings } from '@/hooks/mutations/useUpdateNotificationSettings';
import type { Bookmark, EmploymentStatus, UpdateProfileRequest } from '@/types/policy';
import { STATUS_LABELS, CATEGORY_LABELS, REGION_OPTIONS, EMPLOYMENT_STATUS_LABELS } from '@/types/policy';
import type { PolicyCategory, PolicyStatus } from '@/types/policy';

/* ─────────────────────────── Helpers ─────────────────────────── */

function getDDay(dateStr: string | null | undefined): number | null {
  if (!dateStr) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(dateStr);
  target.setHours(0, 0, 0, 0);
  return Math.ceil((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
}

/* ─────────────────────────── Sub-components ─────────────────────────── */

function Toast({
  message,
  onUndo,
  onClose,
}: {
  message: string;
  onUndo: () => void;
  onClose: () => void;
}) {
  useEffect(() => {
    const timer = setTimeout(onClose, 3000);
    return () => clearTimeout(timer);
  }, [onClose]);

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-6 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 rounded-xl bg-neutral-900 px-5 py-3 text-sm text-white shadow-lg"
    >
      <span>{message}</span>
      <button
        onClick={onUndo}
        className="font-semibold text-brand-300 underline underline-offset-2 hover:text-brand-200"
      >
        되돌리기
      </button>
    </div>
  );
}

function BookmarkCard({ bookmark, onRemove, fading }: { bookmark: Bookmark; onRemove: () => void; fading: boolean }) {
  const dDay = getDDay(bookmark.policy.applyEnd);
  const categoryLabel = CATEGORY_LABELS[bookmark.policy.category as PolicyCategory] ?? bookmark.policy.category;
  const statusLabel = STATUS_LABELS[bookmark.policy.status as PolicyStatus] ?? bookmark.policy.status;

  return (
    <div className={cn('transition-all duration-300', fading && 'scale-95 opacity-0')}>
      <article className="group relative rounded-2xl border border-gray-100 bg-white p-6 shadow-card transition-all duration-200 hover:-translate-y-0.5 hover:shadow-card-hover">
        <div className="mb-3 flex items-center gap-2">
          <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-indigo-600">
            {categoryLabel}
          </span>
          <span className={cn(
            'rounded-full px-2.5 py-0.5 text-xs font-semibold',
            bookmark.policy.status === 'OPEN' ? 'bg-success-100 text-success-500' :
            bookmark.policy.status === 'UPCOMING' ? 'bg-gray-100 text-gray-500' : 'bg-gray-100 text-gray-400',
          )}>
            {statusLabel}
          </span>
          {dDay !== null && dDay >= 0 && dDay <= 7 && (
            <span className="rounded-full bg-warning-500 px-2 py-0.5 text-xs font-bold text-white">
              D-{dDay}
            </span>
          )}
          <button
            onClick={(e) => { e.preventDefault(); e.stopPropagation(); onRemove(); }}
            className="ml-auto flex h-8 w-8 items-center justify-center rounded-full transition-colors hover:bg-gray-50"
            aria-label="북마크 해제"
          >
            <BookmarkIcon className="h-5 w-5 fill-brand-800 text-brand-800" />
          </button>
        </div>
        <Link to={`/policies/${bookmark.policy.id}`} className="block">
          <h3 className="text-lg font-semibold leading-snug text-gray-900 transition-colors group-hover:text-brand-800">
            {bookmark.policy.title}
          </h3>
        </Link>
        <div className="mt-3 text-xs text-gray-400">
          마감: {bookmark.policy.applyEnd
            ? bookmark.policy.applyEnd.slice(0, 10).replace(/-/g, '.')
            : '미정'}
        </div>
      </article>
    </div>
  );
}

/* ─────────────────────────── Main Component ─────────────────────────── */

type TabKey = 'bookmarks' | 'notifications';

export default function MyPage() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const logout = useAuthStore((s) => s.logout);

  /* ── Auth guard ── */
  useEffect(() => {
    if (!isAuthenticated) navigate('/login?redirect=/mypage', { replace: true });
  }, [isAuthenticated, navigate]);

  /* ── Data fetching ── */
  const { data: profile, isLoading: profileLoading } = useProfile();
  const { data: bookmarkPage, isLoading: bookmarksLoading, refetch: refetchBookmarks } = useBookmarks();
  const { data: notificationData } = useNotificationSettings();

  /* ── Mutations ── */
  const updateProfileMutation = useUpdateProfile();
  const removeBookmarkMutation = useRemoveBookmark();
  const updateNotificationMutation = useUpdateNotificationSettings();

  /* ── Profile edit state ── */
  const [isEditing, setIsEditing] = useState(false);
  const [editNickname, setEditNickname] = useState('');
  const nicknameInputRef = useRef<HTMLInputElement>(null);

  /* ── Email edit state ── */
  const [isEditingEmail, setIsEditingEmail] = useState(false);
  const [editEmail, setEditEmail] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const emailInputRef = useRef<HTMLInputElement>(null);

  /* ── Tab state ── */
  const [activeTab, setActiveTab] = useState<TabKey>('bookmarks');

  /* ── Bookmark removal state ── */
  const [fadingId, setFadingId] = useState<number | null>(null);
  const [toast, setToast] = useState<{
    message: string;
    bookmarkId: number;
  } | null>(null);

  /* ── Notification state (local optimistic) ── */
  const [emailEnabled, setEmailEnabled] = useState(true);
  const [daysBeforeDeadline, setDaysBeforeDeadline] = useState(7);
  const [eligibilityRecommendationEnabled, setEligibilityRecommendationEnabled] = useState(false);
  const [notificationToast, setNotificationToast] = useState<string | null>(null);

  /* ── Notification tab shared email editor ── */
  const [isEditingNotifEmail, setIsEditingNotifEmail] = useState(false);
  const [notifEmailDraft, setNotifEmailDraft] = useState('');
  const [notifEmailError, setNotifEmailError] = useState<string | null>(null);
  const [pendingEnable, setPendingEnable] = useState<'deadline' | 'recommendation' | null>(null);
  const notifEmailInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!notificationToast) return;
    const t = setTimeout(() => setNotificationToast(null), 3000);
    return () => clearTimeout(t);
  }, [notificationToast]);

  useEffect(() => {
    if (notificationData) {
      setEmailEnabled(notificationData.emailEnabled);
      setDaysBeforeDeadline(notificationData.daysBeforeDeadline);
      setEligibilityRecommendationEnabled(notificationData.eligibilityRecommendationEnabled);
    }
  }, [notificationData]);

  /* ── Profile extra info state ── */
  const [editingExtra, setEditingExtra] = useState(false);
  const [extraAge, setExtraAge] = useState<string>('');
  const [extraRegion, setExtraRegion] = useState<string>('');
  const [extraEmployment, setExtraEmployment] = useState<string>('');
  const [extraIncome, setExtraIncome] = useState<string>('');

  useEffect(() => {
    if (profile) {
      setExtraAge(profile.age != null ? String(profile.age) : '');
      setExtraRegion(profile.regionCode ?? '');
      setExtraEmployment(profile.employmentStatus ?? '');
      setExtraIncome(profile.incomeLevel != null ? String(profile.incomeLevel) : '');
    }
  }, [profile]);

  const handleSaveExtra = () => {
    const data: UpdateProfileRequest = {
      age: extraAge ? Number(extraAge) : null,
      regionCode: extraRegion || null,
      employmentStatus: (extraEmployment as EmploymentStatus) || null,
      incomeLevel: extraIncome ? Number(extraIncome) : null,
    };
    updateProfileMutation.mutate(data, {
      onSuccess: () => setEditingExtra(false),
    });
  };

  const extraFilled = profile?.age != null && profile?.regionCode && profile?.employmentStatus;

  /* ── Logout dialog ── */
  const [showLogoutDialog, setShowLogoutDialog] = useState(false);
  const logoutCancelRef = useRef<HTMLButtonElement>(null);

  /* ── Handlers ── */

  const handleStartEdit = () => {
    setEditNickname(profile?.nickname ?? '');
    setIsEditing(true);
    setTimeout(() => nicknameInputRef.current?.focus(), 0);
  };

  const handleSaveNickname = () => {
    const trimmed = editNickname.trim();
    if (trimmed && trimmed !== profile?.nickname) {
      updateProfileMutation.mutate({ nickname: trimmed });
    }
    setIsEditing(false);
  };

  const handleCancelEdit = () => setIsEditing(false);

  const handleStartEditEmail = () => {
    setEditEmail(profile?.email ?? '');
    setEmailError(null);
    setIsEditingEmail(true);
    setTimeout(() => emailInputRef.current?.focus(), 0);
  };

  const handleSaveEmail = () => {
    const trimmed = editEmail.trim();
    if (!trimmed) {
      setEmailError('이메일을 입력해주세요');
      return;
    }
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(trimmed)) {
      setEmailError('올바른 이메일 형식이 아니에요');
      return;
    }
    if (trimmed === profile?.email) {
      setIsEditingEmail(false);
      return;
    }
    updateProfileMutation.mutate(
      { nickname: profile?.nickname ?? '', email: trimmed },
      {
        onSuccess: () => {
          setIsEditingEmail(false);
          setEmailError(null);
        },
        onError: (err: unknown) => {
          const message = err instanceof Error ? err.message : '이메일 저장에 실패했어요';
          setEmailError(message.includes('409') ? '이미 사용 중인 이메일이에요' : '이메일 저장에 실패했어요');
        },
      },
    );
  };

  const handleCancelEditEmail = () => {
    setIsEditingEmail(false);
    setEmailError(null);
  };

  const handleBookmarkRemove = (bookmarkId: number) => {
    setFadingId(bookmarkId);
    setTimeout(() => {
      removeBookmarkMutation.mutate(bookmarkId, {
        onSuccess: () => {
          setFadingId(null);
          setToast({ message: '북마크를 해제했어요', bookmarkId });
          refetchBookmarks();
        },
      });
    }, 300);
  };

  const handleUndo = useCallback(() => {
    // Note: undo would require re-adding the bookmark; for now just dismiss
    setToast(null);
  }, []);

  const handleCloseToast = useCallback(() => setToast(null), []);

  const handleStartEditNotifEmail = () => {
    setNotifEmailDraft(profile?.email ?? '');
    setNotifEmailError(null);
    setIsEditingNotifEmail(true);
    setTimeout(() => notifEmailInputRef.current?.focus(), 0);
  };

  const handleCancelEditNotifEmail = () => {
    setIsEditingNotifEmail(false);
    setNotifEmailError(null);
    setPendingEnable(null);
  };

  const applyPendingEnable = () => {
    if (pendingEnable === 'deadline') {
      setEmailEnabled(true);
      updateNotificationMutation.mutate({
        emailEnabled: true,
        daysBeforeDeadline,
        eligibilityRecommendationEnabled,
      });
      setNotificationToast(`마감 ${daysBeforeDeadline}일 전 알려드릴게요`);
    } else if (pendingEnable === 'recommendation') {
      setEligibilityRecommendationEnabled(true);
      updateNotificationMutation.mutate({
        emailEnabled,
        daysBeforeDeadline,
        eligibilityRecommendationEnabled: true,
      });
      setNotificationToast('자격이 맞는 새 정책이 나오면 알려드릴게요');
    } else {
      setNotificationToast('이메일을 등록했어요');
    }
    setPendingEnable(null);
  };

  const handleSaveNotifEmail = () => {
    const trimmed = notifEmailDraft.trim();
    if (!trimmed) {
      setNotifEmailError('이메일을 입력해주세요');
      return;
    }
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(trimmed)) {
      setNotifEmailError('올바른 이메일 형식이 아니에요');
      return;
    }
    if (trimmed === profile?.email) {
      setIsEditingNotifEmail(false);
      setNotifEmailError(null);
      applyPendingEnable();
      return;
    }
    updateProfileMutation.mutate(
      { nickname: profile?.nickname ?? '', email: trimmed },
      {
        onSuccess: () => {
          setIsEditingNotifEmail(false);
          setNotifEmailError(null);
          applyPendingEnable();
        },
        onError: (err: unknown) => {
          const message = err instanceof Error ? err.message : '';
          setNotifEmailError(
            message.includes('409') ? '이미 사용 중인 이메일이에요' : '이메일 저장에 실패했어요',
          );
        },
      },
    );
  };

  const handleNotificationToggle = () => {
    if (!profile?.email) {
      setPendingEnable('deadline');
      handleStartEditNotifEmail();
      return;
    }
    const newEnabled = !emailEnabled;
    setEmailEnabled(newEnabled);
    updateNotificationMutation.mutate({
      emailEnabled: newEnabled,
      daysBeforeDeadline,
      eligibilityRecommendationEnabled,
    });
  };

  const handleDaysChange = (days: number) => {
    setDaysBeforeDeadline(days);
    updateNotificationMutation.mutate({
      emailEnabled,
      daysBeforeDeadline: days,
      eligibilityRecommendationEnabled,
    });
  };

  const handleRecommendationToggle = () => {
    if (!profile?.email) {
      setPendingEnable('recommendation');
      handleStartEditNotifEmail();
      return;
    }
    const newEnabled = !eligibilityRecommendationEnabled;
    setEligibilityRecommendationEnabled(newEnabled);
    updateNotificationMutation.mutate({
      emailEnabled,
      daysBeforeDeadline,
      eligibilityRecommendationEnabled: newEnabled,
    });
  };

  const handleLogout = () => {
    logout();
    navigate('/', { replace: true });
  };

  /* ── Focus trap for logout dialog ── */
  useEffect(() => {
    if (!showLogoutDialog) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setShowLogoutDialog(false); return; }
      if (e.key !== 'Tab') return;
      const container = document.querySelector('[role="alertdialog"]');
      if (!container) return;
      const focusable = container.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey) { if (document.activeElement === first) { e.preventDefault(); last.focus(); } }
      else { if (document.activeElement === last) { e.preventDefault(); first.focus(); } }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [showLogoutDialog]);

  useEffect(() => {
    if (showLogoutDialog) setTimeout(() => logoutCancelRef.current?.focus(), 0);
  }, [showLogoutDialog]);

  /* ── Loading state ── */
  if (profileLoading) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 lg:px-8">
        <div className="flex items-center justify-center py-20">
          <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
        </div>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 lg:px-8">
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <AlertCircle className="mb-4 h-12 w-12 text-error-500" />
          <h2 className="text-lg font-semibold text-gray-900">프로필을 불러오지 못했습니다</h2>
        </div>
      </div>
    );
  }

  const bookmarks = bookmarkPage?.content ?? [];

  /* ─────────────────────────── Render ─────────────────────────── */

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 lg:px-8">
      <div className="lg:grid lg:grid-cols-12 lg:gap-8">
        {/* ── Sidebar ── */}
        <aside className="space-y-6 lg:col-span-4">
          {/* Profile Header */}
          <div className="rounded-2xl bg-white p-6 shadow-card">
            <div className="flex items-center gap-4">
              <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full border-2 border-neutral-200 bg-brand-100">
                {profile.profileImageUrl ? (
                  <img
                    src={profile.profileImageUrl}
                    alt={`${profile.nickname} 프로필`}
                    width={64}
                    height={64}
                    className="h-16 w-16 rounded-full object-cover"
                  />
                ) : (
                  <User className="h-7 w-7 text-brand-800" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                {isEditing ? (
                  <div className="flex items-center gap-2">
                    <input
                      ref={nicknameInputRef}
                      type="text"
                      value={editNickname}
                      onChange={(e) => setEditNickname(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') handleSaveNickname();
                        if (e.key === 'Escape') handleCancelEdit();
                      }}
                      className="h-11 w-full rounded-xl border border-neutral-200 px-3 text-base font-bold text-neutral-900 outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                      aria-label="닉네임 수정"
                    />
                  </div>
                ) : (
                  <h2 className="truncate text-2xl font-bold text-neutral-900">{profile.nickname}</h2>
                )}
                {isEditingEmail ? (
                  <div className="mt-1">
                    <div className="flex items-center gap-2">
                      <input
                        ref={emailInputRef}
                        type="email"
                        value={editEmail}
                        onChange={(e) => setEditEmail(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') handleSaveEmail();
                          if (e.key === 'Escape') handleCancelEditEmail();
                        }}
                        placeholder="example@email.com"
                        className="h-9 w-full rounded-lg border border-neutral-200 px-2.5 text-sm text-neutral-900 outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                        aria-label="이메일 입력"
                        aria-invalid={emailError != null}
                        aria-describedby={emailError ? 'email-error' : undefined}
                      />
                    </div>
                    {emailError && (
                      <p id="email-error" className="mt-1 text-xs text-error-500">
                        {emailError}
                      </p>
                    )}
                    <div className="mt-2 flex gap-2">
                      <button
                        type="button"
                        onClick={handleSaveEmail}
                        disabled={updateProfileMutation.isPending}
                        className="rounded-md bg-brand-800 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-brand-900 disabled:opacity-50"
                      >
                        저장
                      </button>
                      <button
                        type="button"
                        onClick={handleCancelEditEmail}
                        className="rounded-md px-3 py-1.5 text-xs font-semibold text-neutral-700 transition-colors hover:bg-neutral-100"
                      >
                        취소
                      </button>
                    </div>
                  </div>
                ) : profile.email ? (
                  <div className="mt-0.5 flex items-center gap-1.5">
                    <p className="truncate text-sm text-neutral-500">{profile.email}</p>
                    <button
                      type="button"
                      onClick={handleStartEditEmail}
                      className="shrink-0 text-xs font-medium text-indigo-600 hover:underline"
                    >
                      변경
                    </button>
                  </div>
                ) : null}
              </div>
            </div>

            <div className="mt-4">
              {isEditing ? (
                <div className="flex gap-2">
                  <button
                    onClick={handleSaveNickname}
                    className="rounded-lg bg-brand-800 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
                  >
                    저장
                  </button>
                  <button
                    onClick={handleCancelEdit}
                    className="rounded-lg px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-neutral-100"
                  >
                    취소
                  </button>
                </div>
              ) : (
                <button
                  onClick={handleStartEdit}
                  className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-neutral-100"
                >
                  <Pencil className="h-4 w-4" />
                  프로필 수정
                </button>
              )}
            </div>
          </div>

          {/* Profile Extra Info */}
          <div className="rounded-2xl bg-white p-6 shadow-card">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardCheck className="h-5 w-5 text-brand-800" />
                <h3 className="text-base font-semibold text-neutral-900">적합도 판정 정보</h3>
              </div>
              {!editingExtra && (
                <button
                  onClick={() => setEditingExtra(true)}
                  className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-neutral-600 transition-colors hover:bg-neutral-100"
                >
                  <Pencil className="h-3.5 w-3.5" />
                  수정
                </button>
              )}
            </div>

            {!extraFilled && !editingExtra && (
              <div className="rounded-xl bg-brand-100/60 p-4 text-center">
                <p className="text-sm text-neutral-600">
                  정보를 입력하면 정책 적합도를 확인할 수 있어요
                </p>
                <button
                  onClick={() => setEditingExtra(true)}
                  className="mt-3 rounded-lg bg-brand-800 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
                >
                  정보 입력하기
                </button>
              </div>
            )}

            {extraFilled && !editingExtra && (
              <dl className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <dt className="text-neutral-500">나이</dt>
                  <dd className="font-medium text-neutral-900">만 {profile.age}세</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-neutral-500">거주 지역</dt>
                  <dd className="font-medium text-neutral-900">
                    {REGION_OPTIONS.find((r) => r.value === profile.regionCode)?.label ?? profile.regionCode}
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-neutral-500">고용 상태</dt>
                  <dd className="font-medium text-neutral-900">
                    {EMPLOYMENT_STATUS_LABELS[profile.employmentStatus as EmploymentStatus] ?? profile.employmentStatus}
                  </dd>
                </div>
                {profile.incomeLevel != null && (
                  <div className="flex justify-between">
                    <dt className="text-neutral-500">소득 수준</dt>
                    <dd className="font-medium text-neutral-900">중위소득 {profile.incomeLevel}%</dd>
                  </div>
                )}
              </dl>
            )}

            {editingExtra && (
              <div className="space-y-4">
                <div>
                  <label htmlFor="extra-age" className="mb-1.5 block text-sm font-medium text-neutral-700">나이</label>
                  <input
                    id="extra-age"
                    type="number"
                    min={15}
                    max={50}
                    value={extraAge}
                    onChange={(e) => setExtraAge(e.target.value)}
                    placeholder="만 나이"
                    className="h-11 w-full rounded-xl border border-neutral-200 px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label htmlFor="extra-region" className="mb-1.5 block text-sm font-medium text-neutral-700">거주 지역</label>
                  <select
                    id="extra-region"
                    value={extraRegion}
                    onChange={(e) => setExtraRegion(e.target.value)}
                    className="h-11 w-full rounded-xl border border-neutral-200 px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                  >
                    <option value="">선택해주세요</option>
                    {REGION_OPTIONS.map((r) => (
                      <option key={r.value} value={r.value}>{r.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="extra-employment" className="mb-1.5 block text-sm font-medium text-neutral-700">고용 상태</label>
                  <select
                    id="extra-employment"
                    value={extraEmployment}
                    onChange={(e) => setExtraEmployment(e.target.value)}
                    className="h-11 w-full rounded-xl border border-neutral-200 px-3 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                  >
                    <option value="">선택해주세요</option>
                    {(Object.entries(EMPLOYMENT_STATUS_LABELS) as [EmploymentStatus, string][]).map(([val, label]) => (
                      <option key={val} value={val}>{label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="extra-income" className="mb-1.5 block text-sm font-medium text-neutral-700">
                    소득 수준 <span className="text-neutral-400">(선택)</span>
                  </label>
                  <div className="relative">
                    <input
                      id="extra-income"
                      type="number"
                      min={0}
                      max={500}
                      value={extraIncome}
                      onChange={(e) => setExtraIncome(e.target.value)}
                      placeholder="중위소득 대비 %"
                      className="h-11 w-full rounded-xl border border-neutral-200 px-3 pr-10 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-neutral-400">%</span>
                  </div>
                </div>
                <div className="flex gap-2 pt-1">
                  <button
                    onClick={handleSaveExtra}
                    disabled={updateProfileMutation.isPending}
                    className="flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-brand-800 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-900 disabled:opacity-50"
                  >
                    {updateProfileMutation.isPending ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Save className="h-4 w-4" />
                    )}
                    저장
                  </button>
                  <button
                    onClick={() => {
                      setEditingExtra(false);
                      if (profile) {
                        setExtraAge(profile.age != null ? String(profile.age) : '');
                        setExtraRegion(profile.regionCode ?? '');
                        setExtraEmployment(profile.employmentStatus ?? '');
                        setExtraIncome(profile.incomeLevel != null ? String(profile.incomeLevel) : '');
                      }
                    }}
                    className="rounded-lg px-4 py-2.5 text-sm font-semibold text-neutral-700 transition-colors hover:bg-neutral-100"
                  >
                    취소
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Logout - desktop */}
          <div className="hidden lg:block">
            <button
              onClick={() => setShowLogoutDialog(true)}
              className="text-sm text-neutral-500 transition-colors hover:text-neutral-900"
            >
              로그아웃
            </button>
          </div>
        </aside>

        {/* ── Main Content ── */}
        <main className="mt-8 lg:col-span-8 lg:mt-0">
          {/* Tab Navigation */}
          <div role="tablist" aria-label="마이페이지 탭" className="flex border-b border-neutral-200">
            {([
              { key: 'bookmarks' as const, label: '북마크', icon: BookmarkIcon },
              { key: 'notifications' as const, label: '알림 설정', icon: Bell },
            ]).map((tab) => (
              <button
                key={tab.key}
                role="tab"
                id={`tab-${tab.key}`}
                aria-selected={activeTab === tab.key}
                aria-controls={`panel-${tab.key}`}
                onClick={() => setActiveTab(tab.key)}
                className={cn(
                  'flex h-12 items-center gap-2 border-b-2 px-4 text-sm font-semibold transition-colors',
                  activeTab === tab.key
                    ? 'border-brand-800 text-brand-800'
                    : 'border-transparent text-neutral-500 hover:text-neutral-700',
                )}
              >
                <tab.icon className="h-4 w-4" />
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab Panels */}
          <div className="mt-6">
            {/* Bookmarks Panel */}
            <div
              role="tabpanel"
              id="panel-bookmarks"
              aria-labelledby="tab-bookmarks"
              hidden={activeTab !== 'bookmarks'}
            >
              {bookmarksLoading ? (
                <div className="flex items-center justify-center py-16">
                  <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
                </div>
              ) : bookmarks.length === 0 ? (
                <div className="flex flex-col items-center py-16 text-center">
                  <BookmarkIcon className="h-12 w-12 text-neutral-300" />
                  <p className="mt-4 text-lg font-semibold text-neutral-700">
                    아직 북마크한 정책이 없어요
                  </p>
                  <p className="mt-1 text-sm text-neutral-500">
                    관심 있는 정책을 북마크해 보세요.
                  </p>
                  <Link
                    to="/policies"
                    className="mt-6 rounded-xl bg-brand-800 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
                  >
                    정책 둘러보기
                  </Link>
                </div>
              ) : (
                <div className="grid gap-4 md:grid-cols-2">
                  {bookmarks.map((bm) => (
                    <BookmarkCard
                      key={bm.bookmarkId}
                      bookmark={bm}
                      onRemove={() => handleBookmarkRemove(bm.bookmarkId)}
                      fading={fadingId === bm.bookmarkId}
                    />
                  ))}
                </div>
              )}
            </div>

            {/* Notifications Panel */}
            <div
              role="tabpanel"
              id="panel-notifications"
              aria-labelledby="tab-notifications"
              hidden={activeTab !== 'notifications'}
            >
              <div className="space-y-4">
                {/* 공통 이메일 카드 */}
                <div className="rounded-2xl bg-white p-6 shadow-card">
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
                      <Mail className="h-4 w-4 text-brand-800" />
                    </div>
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-neutral-900">알림 받을 이메일</h3>
                      <p className="mt-1 text-sm text-neutral-500">
                        마감 알림과 맞춤 추천을 이 이메일로 보내드려요.
                      </p>
                    </div>
                  </div>

                  {!profile?.email && !isEditingNotifEmail && (
                    <div className="mt-4 rounded-xl bg-brand-100/60 p-4 text-center">
                      <p className="text-sm text-neutral-600">
                        이메일을 등록하면 아래 알림을 받을 수 있어요.
                      </p>
                      <button
                        type="button"
                        onClick={handleStartEditNotifEmail}
                        className="mt-3 rounded-lg bg-brand-800 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
                      >
                        이메일 등록하기
                      </button>
                    </div>
                  )}

                  {profile?.email && !isEditingNotifEmail && (
                    <div className="mt-4 flex items-center gap-3 rounded-xl bg-neutral-50 px-4 py-3">
                      <Mail className="h-4 w-4 shrink-0 text-neutral-400" />
                      <span className="flex-1 truncate text-sm text-neutral-900">{profile.email}</span>
                      <button
                        type="button"
                        onClick={handleStartEditNotifEmail}
                        className="shrink-0 text-xs font-semibold text-indigo-600 hover:underline"
                      >
                        변경
                      </button>
                    </div>
                  )}

                  {isEditingNotifEmail && (
                    <div className="mt-4">
                      <label htmlFor="notif-email-input" className="mb-1.5 block text-sm font-medium text-neutral-700">
                        알림 받을 이메일
                      </label>
                      <input
                        ref={notifEmailInputRef}
                        id="notif-email-input"
                        type="email"
                        value={notifEmailDraft}
                        onChange={(e) => {
                          setNotifEmailDraft(e.target.value);
                          if (notifEmailError) setNotifEmailError(null);
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            handleSaveNotifEmail();
                          }
                          if (e.key === 'Escape') handleCancelEditNotifEmail();
                        }}
                        placeholder="example@email.com"
                        className="h-11 w-full rounded-xl border border-neutral-200 px-3 text-sm text-neutral-900 outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                        aria-invalid={notifEmailError != null}
                        aria-describedby={notifEmailError ? 'notif-email-error' : undefined}
                      />
                      {notifEmailError && (
                        <p id="notif-email-error" className="mt-2 text-xs text-error-500">
                          {notifEmailError}
                        </p>
                      )}
                      <div className="mt-3 flex gap-2">
                        <button
                          type="button"
                          onClick={handleSaveNotifEmail}
                          disabled={updateProfileMutation.isPending}
                          className="rounded-lg bg-brand-800 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-900 disabled:opacity-50"
                        >
                          {updateProfileMutation.isPending ? '저장 중…' : '저장'}
                        </button>
                        <button
                          type="button"
                          onClick={handleCancelEditNotifEmail}
                          className="rounded-lg px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-neutral-100"
                        >
                          취소
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                {/* 마감 임박 알림 */}
                <div
                  className={cn(
                    'rounded-2xl bg-white p-6 shadow-card transition-opacity',
                    !profile?.email && 'opacity-70',
                  )}
                >
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
                      <Bell className="h-4 w-4 text-brand-800" />
                    </div>
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-neutral-900">마감 임박 알림</h3>
                      <p className="mt-1 text-sm text-neutral-500">
                        북마크한 정책의 마감일 전에 이메일로 알려드려요.
                      </p>
                      {!profile?.email && (
                        <p className="mt-2 text-xs text-neutral-500">
                          활성화하려면 위에서 이메일을 먼저 등록해주세요.
                        </p>
                      )}
                    </div>
                    <button
                      role="switch"
                      aria-checked={emailEnabled}
                      aria-label="마감 알림 받기"
                      onClick={handleNotificationToggle}
                      className={cn(
                        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors duration-200',
                        emailEnabled && profile?.email ? 'bg-brand-800' : 'bg-neutral-300',
                      )}
                    >
                      <span
                        className={cn(
                          'pointer-events-none inline-block h-5 w-5 translate-y-0.5 rounded-full bg-white shadow-sm transition-transform duration-200',
                          emailEnabled && profile?.email ? 'translate-x-[22px]' : 'translate-x-0.5',
                        )}
                      />
                    </button>
                  </div>

                  <fieldset className="mt-6" disabled={!emailEnabled || !profile?.email}>
                    <legend className="text-sm font-semibold text-neutral-700">알림 시점</legend>
                    <div className="mt-3 flex flex-col gap-3">
                      {[3, 7, 14].map((days) => (
                        <label
                          key={days}
                          className={cn(
                            'flex cursor-pointer items-center gap-3 text-sm',
                            (!emailEnabled || !profile?.email) && 'cursor-not-allowed opacity-50',
                          )}
                        >
                          <input
                            type="radio"
                            name="daysBeforeDeadline"
                            value={days}
                            checked={daysBeforeDeadline === days}
                            onChange={() => handleDaysChange(days)}
                            disabled={!emailEnabled || !profile?.email}
                            className="h-4 w-4 border-neutral-300 text-brand-800 focus:ring-brand-800"
                          />
                          <span className="text-neutral-700">마감 {days}일 전</span>
                        </label>
                      ))}
                    </div>
                  </fieldset>
                </div>

                {/* 맞춤 정책 추천 */}
                <div
                  className={cn(
                    'rounded-2xl bg-white p-6 shadow-card transition-opacity',
                    !profile?.email && 'opacity-70',
                  )}
                >
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
                      <Sparkles className="h-4 w-4 text-brand-800" />
                    </div>
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-neutral-900">맞춤 정책 추천</h3>
                      <p className="mt-1 text-sm text-neutral-500">
                        적합도 판정 정보를 바탕으로 자격이 맞을 가능성이 높은 새 정책을 이메일로 추천해 드려요.
                      </p>
                      {!profile?.email && (
                        <p className="mt-2 text-xs text-neutral-500">
                          활성화하려면 위에서 이메일을 먼저 등록해주세요.
                        </p>
                      )}
                      {profile?.email && !extraFilled && (
                        <p className="mt-2 text-xs text-warning-500">
                          적합도 판정 정보를 먼저 입력하면 더 정확한 추천을 받을 수 있어요.
                        </p>
                      )}
                    </div>
                    <button
                      role="switch"
                      aria-checked={eligibilityRecommendationEnabled}
                      aria-label="맞춤 정책 추천 받기"
                      onClick={handleRecommendationToggle}
                      className={cn(
                        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors duration-200',
                        eligibilityRecommendationEnabled && profile?.email ? 'bg-brand-800' : 'bg-neutral-300',
                      )}
                    >
                      <span
                        className={cn(
                          'pointer-events-none inline-block h-5 w-5 translate-y-0.5 rounded-full bg-white shadow-sm transition-transform duration-200',
                          eligibilityRecommendationEnabled && profile?.email ? 'translate-x-[22px]' : 'translate-x-0.5',
                        )}
                      />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Logout - mobile */}
          <div className="mt-8 text-center lg:hidden">
            <button
              onClick={() => setShowLogoutDialog(true)}
              className="text-sm text-neutral-500 transition-colors hover:text-neutral-900"
            >
              로그아웃
            </button>
          </div>
        </main>
      </div>

      {/* ── Logout Dialog ── */}
      {showLogoutDialog && (
        <div
          className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowLogoutDialog(false);
          }}
        >
          <div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="logout-title"
            aria-describedby="logout-desc"
            className="w-full max-w-[360px] rounded-2xl bg-white p-6 shadow-xl"
          >
            <h3 id="logout-title" className="text-lg font-bold text-neutral-900">
              로그아웃할까요?
            </h3>
            <p id="logout-desc" className="mt-2 text-sm text-neutral-500">
              로그아웃하면 북마크와 알림 설정은 유지됩니다.
            </p>
            <div className="mt-6 flex gap-3">
              <button
                ref={logoutCancelRef}
                onClick={() => setShowLogoutDialog(false)}
                className="flex-1 rounded-xl px-4 py-2.5 text-sm font-semibold text-neutral-700 transition-colors hover:bg-neutral-100"
              >
                취소
              </button>
              <button
                onClick={handleLogout}
                className="flex-1 rounded-xl bg-error-500 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-red-600"
              >
                로그아웃
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Toast ── */}
      {toast && (
        <Toast message={toast.message} onUndo={handleUndo} onClose={handleCloseToast} />
      )}

      {/* ── Notification Toast ── */}
      {notificationToast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-xl bg-neutral-900 px-5 py-3 text-sm text-white shadow-lg"
        >
          {notificationToast}
        </div>
      )}
    </div>
  );
}
