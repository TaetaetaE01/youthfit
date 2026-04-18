import { useState, useRef, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Bookmark,
  Bell,
  MapPin,
  Calendar,
  ChevronRight,
  CheckCircle,
  AlertCircle,
  XCircle,
  Send,
  Sparkles,
  Loader2,
  ExternalLink,
  Building2,
  Phone,
  FileText,
  Users,
  ClipboardCheck,
  Gift,
  Paperclip,
  Repeat,
  Tag,
  ListOrdered,
  Globe,
} from 'lucide-react';
import { cn } from '@/lib/cn';
import { getEffectiveStatus, formatPolicyPeriod } from '@/lib/policyStatus';
import { CategoryBadge, StatusBadge } from '@/components/policy/PolicyCard';
import FormattedPolicyText from '@/components/policy/FormattedPolicyText';
import LoginPromptModal from '@/components/auth/LoginPromptModal';
import NotificationPromptSheet from '@/components/policy/NotificationPromptSheet';
import { useAuthStore } from '@/stores/authStore';
import { usePolicy } from '@/hooks/queries/usePolicy';
import { useGuide } from '@/hooks/queries/useGuide';
import { useMyBookmarkIds } from '@/hooks/queries/useMyBookmarkIds';
import { usePolicySubscription } from '@/hooks/queries/usePolicySubscription';
import { useJudgeEligibility } from '@/hooks/mutations/useJudgeEligibility';
import { useAddBookmark, useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
import { useUnsubscribePolicy } from '@/hooks/mutations/usePolicySubscription';
import { fetchQnaAnswer } from '@/apis/qna.api';
import { getRegionName } from '@/types/policy';
import type {
  PolicyDetail,
  EligibilityResponse,
  EligibilityResult,
  CriterionItem,
  QnaMessage,
} from '@/types/policy';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const RESULT_CONFIG: Record<
  EligibilityResult,
  { icon: typeof CheckCircle; color: string; label: string }
> = {
  LIKELY_ELIGIBLE: {
    icon: CheckCircle,
    color: 'text-success-500',
    label: '해당 가능성 높음',
  },
  UNCERTAIN: {
    icon: AlertCircle,
    color: 'text-warning-500',
    label: '추가 확인 필요',
  },
  LIKELY_INELIGIBLE: {
    icon: XCircle,
    color: 'text-error-500',
    label: '해당 가능성 낮음',
  },
};

const OVERALL_COLOR: Record<EligibilityResult, string> = {
  LIKELY_ELIGIBLE: 'text-success-500',
  UNCERTAIN: 'text-warning-500',
  LIKELY_INELIGIBLE: 'text-error-500',
};

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function Breadcrumb({ title }: { title: string }) {
  return (
    <nav aria-label="breadcrumb" className="mb-6 text-sm text-neutral-500">
      <ol className="flex items-center gap-1">
        <li>
          <Link
            to="/policies"
            className="transition-colors hover:text-brand-800"
          >
            정책 목록
          </Link>
        </li>
        <li>
          <ChevronRight className="h-3.5 w-3.5" />
        </li>
        <li className="truncate font-medium text-neutral-900">{title}</li>
      </ol>
    </nav>
  );
}

function PolicyHeader({
  policy,
  isBookmarked,
  onBookmarkToggle,
}: {
  policy: PolicyDetail;
  isBookmarked: boolean;
  onBookmarkToggle: () => void;
}) {
  return (
    <header className="relative mb-8">
      <div className="mb-3 flex items-center gap-2">
        <CategoryBadge category={policy.category} />
        <StatusBadge status={getEffectiveStatus(policy)} />
        <button
          onClick={onBookmarkToggle}
          className="ml-auto flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-gray-50"
          aria-label={isBookmarked ? '북마크 해제' : '북마크 추가'}
          aria-pressed={isBookmarked}
        >
          <Bookmark
            className={cn(
              'h-6 w-6 transition-colors',
              isBookmarked
                ? 'fill-brand-800 text-brand-800'
                : 'text-gray-300',
            )}
          />
        </button>
      </div>
      <h1 className="text-3xl font-bold text-neutral-900">{policy.title}</h1>
      <PolicyTagList policy={policy} />
      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-sm text-neutral-500">
        <span className="flex items-center gap-1">
          <MapPin className="h-4 w-4" />
          {getRegionName(policy.regionCode)}
        </span>
        <span className="text-neutral-300">|</span>
        <span className="flex items-center gap-1">
          <Calendar className="h-4 w-4" />
          {formatPolicyPeriod(policy)}
        </span>
        {policy.organization && (
          <>
            <span className="text-neutral-300">|</span>
            <span className="flex items-center gap-1">
              <Building2 className="h-4 w-4" />
              {policy.organization}
            </span>
          </>
        )}
      </div>
    </header>
  );
}

function DetailSection({
  icon: Icon,
  title,
  content,
}: {
  icon: typeof FileText;
  title: string;
  content: string;
}) {
  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <Icon className="h-4 w-4 text-brand-800" />
        {title}
      </h2>
      <FormattedPolicyText text={content} />
    </section>
  );
}

function PolicyTagList({ policy }: { policy: PolicyDetail }) {
  const tags = Array.from(
    new Set([
      ...(policy.lifeTags ?? []),
      ...(policy.targetTags ?? []),
      ...(policy.themeTags ?? []),
    ]),
  );
  if (tags.length === 0) return null;
  return (
    <div className="mt-3 flex flex-wrap gap-1.5">
      {tags.map((tag) => (
        <span
          key={tag}
          className="rounded-full border border-neutral-300 bg-white px-3 py-1 text-xs font-medium text-neutral-800"
        >
          #{tag}
        </span>
      ))}
    </div>
  );
}

function SupportOverviewSection({
  supportCycle,
  provideType,
}: {
  supportCycle: string | null;
  provideType: string | null;
}) {
  if (!supportCycle && !provideType) return null;
  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-4 text-base font-semibold text-neutral-900">지원 개요</h2>
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {supportCycle && (
          <div className="flex items-start gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
              <Repeat className="h-4 w-4 text-brand-800" />
            </div>
            <div>
              <dt className="text-xs text-neutral-500">지원주기</dt>
              <dd className="text-sm font-medium text-neutral-900">{supportCycle}</dd>
            </div>
          </div>
        )}
        {provideType && (
          <div className="flex items-start gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
              <Tag className="h-4 w-4 text-brand-800" />
            </div>
            <div>
              <dt className="text-xs text-neutral-500">제공유형</dt>
              <dd className="text-sm font-medium text-neutral-900">{provideType}</dd>
            </div>
          </div>
        )}
      </dl>
    </section>
  );
}

function ApplyMethodSection({ applyMethods }: { applyMethods: PolicyDetail['applyMethods'] }) {
  if (!applyMethods || applyMethods.length === 0) return null;
  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <ListOrdered className="h-4 w-4 text-brand-800" />
        신청방법
      </h2>
      <ol className="space-y-3">
        {applyMethods.map((step, i) => (
          <li key={i} className="flex items-start gap-3">
            <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-800 text-xs font-bold text-white">
              {i + 1}
            </span>
            <div className="flex-1">
              <p className="text-sm font-semibold text-neutral-900">{step.stageName}</p>
              {step.description && (
                <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-neutral-600">
                  {step.description}
                </p>
              )}
            </div>
          </li>
        ))}
      </ol>
      <p className="mt-4 text-xs text-neutral-500">
        자세한 절차는 공식 신청 채널에서 확인해주세요.
      </p>
    </section>
  );
}

function ReferenceSiteSection({
  referenceSites,
}: {
  referenceSites: PolicyDetail['referenceSites'];
}) {
  if (!referenceSites || referenceSites.length === 0) return null;
  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <Globe className="h-4 w-4 text-brand-800" />
        관련 사이트
      </h2>
      <ul className="space-y-2">
        {referenceSites.map((site, i) => (
          <li key={i}>
            <a
              href={site.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 rounded-lg border border-neutral-200 px-3 py-2.5 text-sm text-neutral-700 transition-colors hover:border-brand-800 hover:bg-brand-100/40"
            >
              <Globe className="h-4 w-4 shrink-0 text-neutral-500" />
              <span className="flex-1 truncate">{site.name}</span>
              <ExternalLink className="h-3.5 w-3.5 shrink-0 text-neutral-400" />
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

function AttachmentSection({ attachments }: { attachments: PolicyDetail['attachments'] }) {
  if (!attachments || attachments.length === 0) return null;
  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <Paperclip className="h-4 w-4 text-brand-800" />
        첨부파일
      </h2>
      <ul className="space-y-2">
        {attachments.map((att, i) => (
          <li key={i}>
            <a
              href={att.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 rounded-lg border border-neutral-200 px-3 py-2.5 text-sm text-neutral-700 transition-colors hover:border-brand-800 hover:bg-brand-100/40"
            >
              <FileText className="h-4 w-4 shrink-0 text-neutral-500" />
              <span className="flex-1 truncate">{att.name}</span>
              <ExternalLink className="h-3.5 w-3.5 shrink-0 text-neutral-400" />
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ContactSection({ contact }: { contact: string | null }) {
  if (!contact) return null;
  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-2 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <Phone className="h-4 w-4 text-brand-800" />
        문의처
      </h2>
      <FormattedPolicyText text={contact} />
    </section>
  );
}

function GuideSummaryCard({ html, isLoading }: { html: string | null; isLoading: boolean }) {
  if (isLoading) {
    return (
      <section className="mb-8 rounded-2xl border border-neutral-200 bg-white p-6">
        <div className="animate-pulse">
          <div className="mb-4 h-5 w-24 rounded-full bg-gray-200" />
          <div className="h-4 w-full rounded bg-gray-200" />
          <div className="mt-2 h-4 w-3/4 rounded bg-gray-200" />
          <div className="mt-2 h-4 w-1/2 rounded bg-gray-200" />
        </div>
      </section>
    );
  }

  if (!html) return null;

  return (
    <section className="mb-8 rounded-2xl border border-neutral-200 bg-white p-6">
      <span className="mb-4 inline-block rounded-full bg-brand-100 px-3 py-1 text-xs font-bold uppercase tracking-wide text-indigo-600">
        AI Summary
      </span>
      <div
        className="prose prose-sm max-w-none text-neutral-700"
        dangerouslySetInnerHTML={{ __html: html }}
      />
      <p className="mt-4 text-xs text-neutral-500">
        AI가 정리한 요약이에요. 정확한 내용은 공식 공고에서 확인해주세요.
      </p>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Eligibility Card
// ---------------------------------------------------------------------------

function EligibilityCard({
  isAuthenticated,
  eligibility,
  loading,
  onCheck,
  onLoginPrompt,
  sourceUrl,
}: {
  isAuthenticated: boolean;
  eligibility: EligibilityResponse | null;
  loading: boolean;
  onCheck: () => void;
  onLoginPrompt: () => void;
  sourceUrl: string | null;
}) {
  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-4 text-xl font-semibold text-neutral-900">
        내 적합도 확인
      </h2>

      {!isAuthenticated && (
        <button
          onClick={onLoginPrompt}
          className="flex h-11 w-full items-center justify-center rounded-xl bg-brand-800 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          내 적합도 확인하기
        </button>
      )}

      {isAuthenticated && !eligibility && !loading && (
        <button
          onClick={onCheck}
          className="flex h-11 w-full items-center justify-center rounded-xl bg-brand-800 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          내 적합도 확인하기
        </button>
      )}

      {isAuthenticated && loading && (
        <div className="flex flex-col items-center gap-3 py-6">
          <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
          <p className="text-sm text-neutral-500">적합도를 분석하고 있어요...</p>
        </div>
      )}

      {isAuthenticated && eligibility && !loading && (
        <div>
          {/* Overall result */}
          <div className="mb-4 text-center">
            <p className={cn('text-lg font-bold', OVERALL_COLOR[eligibility.overallResult])}>
              {RESULT_CONFIG[eligibility.overallResult].label}
            </p>
          </div>

          <ul className="space-y-3">
            {eligibility.criteria.map((item: CriterionItem, idx: number) => {
              const cfg = RESULT_CONFIG[item.result];
              const Icon = cfg.icon;
              return (
                <li key={idx} className="flex items-start gap-3">
                  <Icon className={cn('mt-0.5 h-5 w-5 shrink-0', cfg.color)} />
                  <div>
                    <p className="text-sm font-medium text-neutral-900">
                      {item.label}
                    </p>
                    <p className="text-xs text-neutral-500">{cfg.label}</p>
                    <p className="mt-0.5 text-xs text-neutral-400">
                      {item.reason}
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
          {eligibility.missingFields.length > 0 && (
            <div className="mt-4 rounded-lg bg-gray-50 p-3">
              <p className="text-xs font-medium text-neutral-600">
                추가 정보가 필요해요
              </p>
              <p className="mt-1 text-xs text-neutral-500">
                {eligibility.missingFields.join(', ')}
              </p>
            </div>
          )}
          <p className="mt-4 text-xs text-neutral-500">
            {eligibility.disclaimer}
          </p>
          {sourceUrl && (
            <a
              href={sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-4 flex items-center justify-center gap-1.5 rounded-xl border border-brand-800 px-4 py-2.5 text-sm font-semibold text-brand-800 transition-colors hover:bg-brand-100"
            >
              공식 신청 채널에서 확인
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
          )}
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Notification CTA Card
// ---------------------------------------------------------------------------

function NotificationCtaCard({
  onSubscribe,
  onUnsubscribe,
  isSubscribed,
  isPending,
}: {
  onSubscribe: () => void;
  onUnsubscribe: () => void;
  isSubscribed: boolean;
  isPending: boolean;
}) {
  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-100">
          <Bell className={cn('h-5 w-5', isSubscribed ? 'fill-brand-800 text-brand-800' : 'text-brand-800')} />
        </div>
        <div className="flex-1">
          <h2 className="text-base font-semibold text-neutral-900">마감일 알림 받기</h2>
          <p className="mt-1 text-xs text-neutral-500">
            마감 7일 전 이메일로 한 번만 알려드려요.
          </p>
        </div>
      </div>
      <button
        onClick={isSubscribed ? onUnsubscribe : onSubscribe}
        disabled={isPending}
        className={cn(
          'mt-4 flex h-11 w-full items-center justify-center rounded-xl text-sm font-semibold transition-colors',
          isSubscribed
            ? 'border border-brand-800 bg-brand-100 text-brand-800 hover:bg-brand-100/70'
            : 'bg-brand-800 text-white hover:bg-brand-900',
          isPending && 'opacity-60',
        )}
      >
        {isSubscribed ? '알림 해제' : '알림 받기'}
      </button>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Q&A Chat Section
// ---------------------------------------------------------------------------

function QnaChatSection({
  isAuthenticated,
  policyId,
  onLoginPrompt,
}: {
  isAuthenticated: boolean;
  policyId: number;
  onLoginPrompt: () => void;
}) {
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<QnaMessage[]>([]);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (messages.length === 0) return;
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }, [messages]);

  const handleSend = useCallback(
    (text: string) => {
      const userMsg: QnaMessage = {
        id: `user-${Date.now()}`,
        role: 'user',
        content: text,
      };

      const assistantId = `assistant-${Date.now()}`;
      const assistantMsg: QnaMessage = {
        id: assistantId,
        role: 'assistant',
        content: '',
        loading: true,
      };

      setMessages((prev) => [...prev, userMsg, assistantMsg]);

      fetchQnaAnswer(
        policyId,
        text,
        (chunk) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId
                ? { ...m, content: m.content + chunk }
                : m,
            ),
          );
        },
        (sources) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, sources } : m,
            ),
          );
        },
        () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId ? { ...m, loading: false } : m,
            ),
          );
        },
        () => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId
                ? { ...m, loading: false, content: '답변을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.' }
                : m,
            ),
          );
        },
        accessToken,
      );
    },
    [policyId, accessToken],
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = input.trim();
    if (!trimmed) return;
    handleSend(trimmed);
    setInput('');
  };

  return (
    <section className="overflow-hidden rounded-2xl bg-brand-800/80 p-6">
      <div className="mb-4 flex items-center gap-2">
        <span className="inline-flex items-center gap-1 rounded-full bg-white/15 px-3 py-1 text-xs font-bold uppercase tracking-wide text-white">
          <Sparkles className="h-3.5 w-3.5" />
          Smart Q&A
        </span>
      </div>

      <div
        role="log"
        aria-live="polite"
        className="mb-4 max-h-80 space-y-3 overflow-y-auto"
      >
        {messages.length === 0 && (
          <p className="py-8 text-center text-sm text-white/60">
            이 정책에 대해 궁금한 점을 질문해보세요.
          </p>
        )}
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={cn(
              'flex',
              msg.role === 'user' ? 'justify-end' : 'justify-start',
            )}
          >
            <div
              className={cn(
                'max-w-[80%] px-4 py-3 text-sm text-white',
                msg.role === 'user'
                  ? 'rounded-xl rounded-br-sm bg-indigo-500/40'
                  : 'rounded-xl rounded-bl-sm bg-white/20',
              )}
            >
              <p className="whitespace-pre-wrap">{msg.content}</p>
              {msg.sources && msg.sources.length > 0 && (
                <div className="mt-2 rounded-lg bg-white/10 p-3 text-xs text-white/85">
                  <p className="mb-1 font-semibold">출처</p>
                  <ul className="list-inside list-disc space-y-0.5">
                    {msg.sources.map((src, i) => (
                      <li key={i}>{src}</li>
                    ))}
                  </ul>
                </div>
              )}
              {msg.loading && (
                <span className="mt-1 inline-block h-4 w-1 animate-pulse bg-white/60" />
              )}
            </div>
          </div>
        ))}
        <div ref={chatEndRef} />
      </div>

      <form onSubmit={handleSubmit} className="relative">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onFocus={() => { if (!isAuthenticated) onLoginPrompt(); }}
          readOnly={!isAuthenticated}
          placeholder={
            isAuthenticated
              ? '질문을 입력하세요...'
              : '로그인 후 질문할 수 있어요'
          }
          className={cn(
            'h-11 w-full rounded-xl bg-white/15 pl-4 pr-12 text-sm text-white placeholder-white/50 outline-none transition-colors focus:bg-white/25',
            !isAuthenticated && 'cursor-pointer opacity-60',
          )}
        />
        <button
          type="submit"
          disabled={!isAuthenticated || !input.trim()}
          className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-lg text-white/70 transition-colors hover:text-white disabled:opacity-40"
          aria-label="질문 전송"
        >
          <Send className="h-4 w-4" />
        </button>
      </form>
    </section>
  );
}

// ---------------------------------------------------------------------------
// PolicyDetailPage (Main)
// ---------------------------------------------------------------------------

export default function PolicyDetailPage() {
  const { policyId: policyIdParam } = useParams<{ policyId: string }>();
  const policyId = Number(policyIdParam) || 0;
  const { isAuthenticated } = useAuthStore();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [policyId]);

  // --- Data fetching ---
  const { data: policy, isLoading: policyLoading, isError: policyError } = usePolicy(policyId);
  const { data: guide, isLoading: guideLoading } = useGuide(policyId);

  // --- Bookmark state ---
  const [bookmarked, setBookmarked] = useState(false);
  const [bookmarkId, setBookmarkId] = useState<number | null>(null);
  const { data: bookmarkIdPairs } = useMyBookmarkIds();
  const addBookmarkMutation = useAddBookmark();
  const removeBookmarkMutation = useRemoveBookmark();

  useEffect(() => {
    if (!bookmarkIdPairs) return;
    const found = bookmarkIdPairs.find((p) => p.policyId === policyId);
    if (found) {
      setBookmarked(true);
      setBookmarkId(found.bookmarkId);
    } else {
      setBookmarked(false);
      setBookmarkId(null);
    }
  }, [bookmarkIdPairs, policyId]);

  // --- Eligibility ---
  const [eligibility, setEligibility] = useState<EligibilityResponse | null>(null);
  const judgeMutation = useJudgeEligibility();

  // --- Login prompt modal ---
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [loginModalMessage, setLoginModalMessage] = useState('');

  const openLoginPrompt = useCallback((message?: string) => {
    setLoginModalMessage(message ?? '로그인하면 이 기능을 이용할 수 있어요');
    setLoginModalOpen(true);
  }, []);

  // --- Notification prompt sheet ---
  const [notificationSheetOpen, setNotificationSheetOpen] = useState(false);
  const [notificationToast, setNotificationToast] = useState<string | null>(null);
  const { data: subscription } = usePolicySubscription(policyId);
  const unsubscribeMutation = useUnsubscribePolicy();
  const isSubscribed = !!subscription?.subscribed;

  const handleSubscribeClick = () => {
    if (!isAuthenticated) {
      openLoginPrompt('로그인하면 마감일 알림을 받을 수 있어요');
      return;
    }
    setNotificationSheetOpen(true);
  };

  const handleUnsubscribeClick = () => {
    if (!isAuthenticated) return;
    unsubscribeMutation.mutate(policyId, {
      onSuccess: () => setNotificationToast('알림을 해제했어요'),
    });
  };

  useEffect(() => {
    if (!notificationToast) return;
    const t = setTimeout(() => setNotificationToast(null), 3000);
    return () => clearTimeout(t);
  }, [notificationToast]);

  const handleBookmarkToggle = () => {
    if (!isAuthenticated) {
      openLoginPrompt('로그인하면 정책을 북마크할 수 있어요');
      return;
    }
    if (bookmarked && bookmarkId) {
      removeBookmarkMutation.mutate(bookmarkId, {
        onSuccess: () => {
          setBookmarked(false);
          setBookmarkId(null);
        },
      });
    } else {
      addBookmarkMutation.mutate(policyId, {
        onSuccess: (data) => {
          setBookmarked(true);
          setBookmarkId(data.bookmarkId);
        },
      });
    }
  };

  const handleEligibilityCheck = () => {
    judgeMutation.mutate(policyId, {
      onSuccess: (data) => setEligibility(data),
    });
  };

  // --- Loading / Error ---
  if (policyLoading) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-6 lg:px-8">
        <div className="animate-pulse">
          <div className="mb-6 h-4 w-24 rounded bg-gray-200" />
          <div className="h-8 w-2/3 rounded bg-gray-200" />
          <div className="mt-4 h-4 w-1/3 rounded bg-gray-200" />
          <div className="mt-8 h-48 rounded-2xl bg-gray-100" />
        </div>
      </div>
    );
  }

  if (policyError || !policy) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-6 lg:px-8">
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <AlertCircle className="mb-4 h-12 w-12 text-error-500" />
          <h2 className="text-lg font-semibold text-gray-900">정책을 불러오지 못했습니다</h2>
          <p className="mt-1 text-sm text-gray-500">잠시 후 다시 시도해주세요.</p>
          <Link
            to="/policies"
            className="mt-4 rounded-xl bg-brand-800 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
          >
            정책 목록으로
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 lg:px-8">
      <Breadcrumb title={policy.title} />

      <div className="lg:grid lg:grid-cols-12 lg:gap-8">
        {/* Left Column */}
        <main className="lg:col-span-8">
          <PolicyHeader
            policy={policy}
            isBookmarked={bookmarked}
            onBookmarkToggle={handleBookmarkToggle}
          />

          {/* AI Guide Summary */}
          <GuideSummaryCard html={guide?.summaryHtml ?? null} isLoading={guideLoading} />

          {/* Policy Summary */}
          <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
            <h2 className="mb-3 text-lg font-semibold text-neutral-900">정책 요약</h2>
            <FormattedPolicyText text={policy.summary} />
          </section>

          {/* Support Overview (cycle / provide type) */}
          <SupportOverviewSection
            supportCycle={policy.supportCycle}
            provideType={policy.provideType}
          />

          {/* Structured Detail Sections */}
          {policy.supportTarget && (
            <DetailSection icon={Users} title="지원대상" content={policy.supportTarget} />
          )}
          {policy.selectionCriteria && (
            <DetailSection
              icon={ClipboardCheck}
              title="선정기준"
              content={policy.selectionCriteria}
            />
          )}
          {policy.supportContent && (
            <DetailSection icon={Gift} title="지원내용" content={policy.supportContent} />
          )}

          {/* Apply Methods */}
          <ApplyMethodSection applyMethods={policy.applyMethods} />

          {/* Reference Sites */}
          <ReferenceSiteSection referenceSites={policy.referenceSites} />

          {/* Attachments */}
          <AttachmentSection attachments={policy.attachments} />

          {/* Contact */}
          <ContactSection contact={policy.contact} />

          {/* Official Application Link */}
          {policy.sourceUrl && (
            <section className="mb-8 rounded-2xl border border-indigo-100 bg-indigo-50/50 p-6">
              <h2 className="mb-2 text-lg font-semibold text-neutral-900">공식 신청 채널</h2>
              <p className="mb-4 text-sm text-neutral-600">
                정책의 정확한 내용과 신청은 공식 채널에서 확인해주세요.
              </p>
              <a
                href={policy.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 rounded-xl bg-brand-800 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
              >
                공식 신청 페이지로 이동
                <ExternalLink className="h-4 w-4" />
              </a>
            </section>
          )}

          {/* Q&A */}
          <QnaChatSection
            isAuthenticated={isAuthenticated}
            policyId={policyId}
            onLoginPrompt={() => openLoginPrompt('로그인하면 정책에 대해 질문할 수 있어요')}
          />
        </main>

        {/* Right Sidebar (Desktop) */}
        <aside className="hidden lg:col-span-4 lg:block">
          <div className="sticky top-24 space-y-6">
            <EligibilityCard
              isAuthenticated={isAuthenticated}
              eligibility={eligibility}
              loading={judgeMutation.isPending}
              onCheck={handleEligibilityCheck}
              onLoginPrompt={() => openLoginPrompt('로그인하면 적합도를 확인할 수 있어요')}
              sourceUrl={policy.sourceUrl}
            />
            <NotificationCtaCard
              onSubscribe={handleSubscribeClick}
              onUnsubscribe={handleUnsubscribeClick}
              isSubscribed={isSubscribed}
              isPending={unsubscribeMutation.isPending}
            />
          </div>
        </aside>

        {/* Eligibility + Notification (Mobile - inline) */}
        <div className="mt-8 space-y-6 lg:hidden">
          <EligibilityCard
            isAuthenticated={isAuthenticated}
            eligibility={eligibility}
            loading={judgeMutation.isPending}
            onCheck={handleEligibilityCheck}
            onLoginPrompt={() => openLoginPrompt('로그인하면 적합도를 확인할 수 있어요')}
            sourceUrl={policy.sourceUrl}
          />
          <NotificationCtaCard
            onSubscribe={handleSubscribeClick}
            onUnsubscribe={handleUnsubscribeClick}
            isSubscribed={isSubscribed}
            isPending={unsubscribeMutation.isPending}
          />
        </div>
      </div>

      {/* Spacer for mobile */}
      <div className="h-4 md:hidden" />

      {/* Login Prompt Modal */}
      <LoginPromptModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        message={loginModalMessage}
      />

      {/* Notification Prompt Sheet */}
      <NotificationPromptSheet
        open={notificationSheetOpen}
        policyId={policyId}
        onClose={() => setNotificationSheetOpen(false)}
        onSubscribed={() => setNotificationToast('마감 7일 전 알려드릴게요')}
      />

      {/* Notification Toast */}
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
