import { useState, useRef, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Heart,
  MapPin,
  Calendar,
  ChevronRight,
  CheckCircle,
  AlertCircle,
  XCircle,
  Send,
  Sparkles,
  Loader2,
} from 'lucide-react';
import { cn } from '@/lib/cn';
import { CategoryBadge, StatusBadge } from '@/components/policy/PolicyCard';
import { useAuthStore } from '@/stores/authStore';
import { usePolicy } from '@/hooks/queries/usePolicy';
import { useGuide } from '@/hooks/queries/useGuide';
import { useJudgeEligibility } from '@/hooks/mutations/useJudgeEligibility';
import { useAddBookmark, useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
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

function formatDateRange(start: string, end: string) {
  const fmt = (d: string) => d.slice(0, 10).replace(/-/g, '.');
  return `${fmt(start)} ~ ${fmt(end)}`;
}

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
        <StatusBadge status={policy.status} />
        <button
          onClick={onBookmarkToggle}
          className="ml-auto flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-gray-50"
          aria-label={isBookmarked ? '북마크 해제' : '북마크 추가'}
          aria-pressed={isBookmarked}
        >
          <Heart
            className={cn(
              'h-6 w-6 transition-colors',
              isBookmarked
                ? 'fill-error-500 text-error-500'
                : 'text-gray-300',
            )}
          />
        </button>
      </div>
      <h1 className="text-3xl font-bold text-neutral-900">{policy.title}</h1>
      <div className="mt-3 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
        <span className="flex items-center gap-1">
          <MapPin className="h-4 w-4" />
          {getRegionName(policy.regionCode)}
        </span>
        <span className="text-neutral-300">|</span>
        <span className="flex items-center gap-1">
          <Calendar className="h-4 w-4" />
          {formatDateRange(policy.applyStart, policy.applyEnd)}
        </span>
      </div>
    </header>
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
}: {
  isAuthenticated: boolean;
  eligibility: EligibilityResponse | null;
  loading: boolean;
  onCheck: () => void;
}) {
  return (
    <section className="rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-4 text-xl font-semibold text-neutral-900">
        내 적합도 확인
      </h2>

      {!isAuthenticated && (
        <div className="text-center">
          <p className="mb-4 text-sm text-neutral-500">
            로그인하면 적합도를 확인할 수 있어요
          </p>
          <Link
            to="/login"
            className="inline-flex h-11 w-full items-center justify-center rounded-xl bg-[#FEE500] text-sm font-semibold text-[#191919] transition-opacity hover:opacity-90"
          >
            카카오로 시작하기
          </Link>
        </div>
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
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Q&A Chat Section
// ---------------------------------------------------------------------------

function QnaChatSection({
  isAuthenticated,
  policyId,
}: {
  isAuthenticated: boolean;
  policyId: number;
}) {
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<QnaMessage[]>([]);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
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
          disabled={!isAuthenticated}
          placeholder={
            isAuthenticated
              ? '질문을 입력하세요...'
              : '로그인 후 질문할 수 있어요'
          }
          className={cn(
            'h-11 w-full rounded-xl bg-white/15 pl-4 pr-12 text-sm text-white placeholder-white/50 outline-none transition-colors focus:bg-white/25',
            !isAuthenticated && 'cursor-not-allowed opacity-60',
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

  // --- Data fetching ---
  const { data: policy, isLoading: policyLoading, isError: policyError } = usePolicy(policyId);
  const { data: guide, isLoading: guideLoading } = useGuide(policyId);

  // --- Bookmark state ---
  const [bookmarked, setBookmarked] = useState(false);
  const [bookmarkId, setBookmarkId] = useState<number | null>(null);
  const addBookmarkMutation = useAddBookmark();
  const removeBookmarkMutation = useRemoveBookmark();

  // --- Eligibility ---
  const [eligibility, setEligibility] = useState<EligibilityResponse | null>(null);
  const judgeMutation = useJudgeEligibility();

  const handleBookmarkToggle = () => {
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
          <section className="mb-8 rounded-2xl border border-neutral-200 bg-white p-6">
            <h2 className="mb-3 text-lg font-semibold text-neutral-900">정책 요약</h2>
            <p className="text-sm leading-relaxed text-neutral-700">{policy.summary}</p>
          </section>

          {/* Q&A */}
          <QnaChatSection
            isAuthenticated={isAuthenticated}
            policyId={policyId}
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
            />
          </div>
        </aside>

        {/* Eligibility Card (Mobile - inline) */}
        <div className="mt-8 lg:hidden">
          <EligibilityCard
            isAuthenticated={isAuthenticated}
            eligibility={eligibility}
            loading={judgeMutation.isPending}
            onCheck={handleEligibilityCheck}
          />
        </div>
      </div>

      {/* Spacer for mobile */}
      <div className="h-4 md:hidden" />
    </div>
  );
}
