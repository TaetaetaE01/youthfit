import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Bookmark,
  Bell,
  MapPin,
  Calendar,
  ChevronRight,
  AlertCircle,
  ExternalLink,
  Building2,
  FileText,
  Paperclip,
  Globe,
} from 'lucide-react';
import { cn } from '@/lib/cn';
import { getEffectiveStatus, formatPolicyPeriod } from '@/lib/policyStatus';
import { CategoryBadge, StatusBadge } from '@/components/policy/PolicyCard';
import SourceBadge from '@/components/policy/SourceBadge';
import FormattedPolicyText from '@/components/policy/FormattedPolicyText';
import LoginPromptModal from '@/components/auth/LoginPromptModal';
import NotificationPromptSheet from '@/components/policy/NotificationPromptSheet';
import { OneLineSummaryCard } from '@/components/policy/OneLineSummaryCard';
import { PairedSection } from '@/components/policy/PairedSection';
import { PitfallsCard } from '@/components/policy/PitfallsCard';
import { HighlightsCard } from '@/components/policy/HighlightsCard';
import { GuideListSectionCard } from '@/components/policy/GuideListSectionCard';
import { useAuthStore } from '@/stores/authStore';
import { usePolicy } from '@/hooks/queries/usePolicy';
import { useGuide } from '@/hooks/queries/useGuide';
import { useMyBookmarkIds } from '@/hooks/queries/useMyBookmarkIds';
import { usePolicySubscription } from '@/hooks/queries/usePolicySubscription';
import { useJudgeEligibility } from '@/hooks/mutations/useJudgeEligibility';
import { useAddBookmark, useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
import { useUnsubscribePolicy } from '@/hooks/mutations/usePolicySubscription';
import { QnaChatSection } from '@/components/qna/QnaChatSection';
import { AiSourceChip } from '@/components/policy/AiSourceChip';
import { pickWithFallback, isMeaningful } from '@/lib/policyEnrichment';
import { getRegionName } from '@/types/policy';
import type { PolicyDetail, EligibilityResponse, PolicySubRegion } from '@/types/policy';
import EligibilityCard from '@/components/policy/eligibility/EligibilityCard';
import { PolicyGroupHeader } from '@/components/policy/groups/PolicyGroupHeader';
import { PolicyGroupDivider } from '@/components/policy/groups/PolicyGroupDivider';
import { POLICY_GROUPS } from '@/components/policy/groups/policyGroups';
import { SubRegionInline } from '@/components/policy/decision/SubRegionInline';
import { DecisionMetaGrid } from '@/components/policy/decision/DecisionMetaGrid';
import { PolicyToc } from '@/components/policy/navigation/PolicyToc';
import { PolicyMobileNav } from '@/components/policy/navigation/PolicyMobileNav';
import { PolicyMobileBottomBar } from '@/components/policy/navigation/PolicyMobileBottomBar';
import { PolicyApplyCta } from '@/components/policy/PolicyApplyCta';
import { usePolicyScrollSpy } from '@/components/policy/navigation/usePolicyScrollSpy';

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
        <SourceBadge sourceType={policy.sourceType} sourceLabel={policy.sourceLabel} size="md" />
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
          {getRegionName(policy.regionCode, policy.sourceType)}
          {policy.subRegions && policy.subRegions.length > 0 && (
            <>
              <span className="mx-1 text-neutral-300">·</span>
              <SubRegionInline subRegions={policy.subRegions} />
            </>
          )}
        </span>
        <span className="text-neutral-300">|</span>
        <span className="flex items-center gap-1">
          <Calendar className="h-4 w-4" />
          {formatPolicyPeriod(policy)}
        </span>
        {(() => {
          const org = pickWithFallback(
            policy.organization,
            policy.enrichment?.sections?.operatingOrganization,
          );
          if (!org) return null;
          return (
            <>
              <span className="text-neutral-300">|</span>
              <span className="flex items-center gap-1">
                <Building2 className="h-4 w-4" />
                {org.value}
                {org.fromAi && (
                  <AiSourceChip
                    sourceUrl={policy.enrichment?.sourceUrl ?? null}
                    className="ml-1"
                  />
                )}
              </span>
            </>
          );
        })()}
      </div>
    </header>
  );
}

function SubRegionSection({ subRegions }: { subRegions: PolicySubRegion[] }) {
  if (!subRegions || subRegions.length === 0) return null;

  const groups = subRegions.reduce<Record<string, PolicySubRegion[]>>((acc, sr) => {
    const key = sr.sidoName ?? '기타';
    (acc[key] ||= []).push(sr);
    return acc;
  }, {});

  return (
    <section className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6">
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <MapPin className="h-4 w-4 text-brand-800" />
        세부 지역
      </h2>
      <div className="space-y-3">
        {Object.entries(groups).map(([sidoName, items]) => (
          <div key={sidoName}>
            <div className="mb-1.5 text-xs font-medium text-neutral-500">{sidoName}</div>
            <div className="flex flex-wrap gap-1.5">
              {items.map((s) => (
                <span
                  key={s.code}
                  className="rounded-full border border-neutral-200 bg-white px-2.5 py-0.5 text-xs text-neutral-700"
                >
                  {s.name}
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>
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

function AttachmentSection({
  attachments,
  extraAttachments,
  enrichmentSourceUrl,
}: {
  attachments: PolicyDetail['attachments'];
  extraAttachments?: { name: string; url: string }[];
  enrichmentSourceUrl?: string | null;
}) {
  const base = attachments ?? [];
  const extras = (extraAttachments ?? []).filter(
    (e) => !base.some((b) => b.url === e.url),
  );
  if (base.length === 0 && extras.length === 0) return null;
  return (
    <section
      id="attachment-section"
      className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6"
    >
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-neutral-900">
        <Paperclip className="h-4 w-4 text-brand-800" />
        첨부파일
      </h2>
      <ul className="space-y-2">
        {base.map((att, i) => (
          <li key={`a-${i}`}>
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
        {extras.map((att, i) => (
          <li key={`e-${i}`}>
            <a
              href={att.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 rounded-lg border border-neutral-200 px-3 py-2.5 text-sm text-neutral-700 transition-colors hover:border-brand-800 hover:bg-brand-100/40"
            >
              <FileText className="h-4 w-4 shrink-0 text-neutral-500" />
              <span className="flex-1 truncate">{att.name}</span>
              <AiSourceChip sourceUrl={enrichmentSourceUrl} />
              <ExternalLink className="h-3.5 w-3.5 shrink-0 text-neutral-400" />
            </a>
          </li>
        ))}
      </ul>
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
  const { data: guide } = useGuide(policyId);

  // --- Scroll spy (TOC active group tracking) ---
  const { activeId } = usePolicyScrollSpy(['eligibility', 'benefits', 'apply', 'more']);

  // --- Decision zone sentinel (mobile nav visibility) ---
  const decisionEndRef = useRef<HTMLDivElement | null>(null);
  const [mobileNavVisible, setMobileNavVisible] = useState(false);

  useEffect(() => {
    const el = decisionEndRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      ([entry]) => setMobileNavVisible(!entry.isIntersecting && entry.boundingClientRect.top < 0),
      { threshold: 0 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [policy]);

  // --- QnA sentinel (mobile bottom bar auto-hide) ---
  const qnaRef = useRef<HTMLDivElement | null>(null);
  const [bottomBarVisible, setBottomBarVisible] = useState(true);

  useEffect(() => {
    const el = qnaRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      ([entry]) => setBottomBarVisible(!entry.isIntersecting),
      { threshold: 0.15 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [policy]);

  // --- URL hash deep linking (/policies/:id#eligibility|benefits|apply|more) ---
  useEffect(() => {
    if (!policy) return;
    const hash = window.location.hash.replace('#', '');
    const valid = ['eligibility', 'benefits', 'apply', 'more'];
    if (!hash || !valid.includes(hash)) return;
    const timer = setTimeout(() => {
      const el = document.getElementById(hash);
      if (el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
    }, 100);
    return () => clearTimeout(timer);
  }, [policy]);

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

      <PolicyMobileNav activeId={activeId} visible={mobileNavVisible} />

      <div className="lg:grid lg:grid-cols-12 lg:gap-8">
        {/* Left Column */}
        <main className="lg:col-span-8">
          <PolicyHeader
            policy={policy}
            isBookmarked={bookmarked}
            onBookmarkToggle={handleBookmarkToggle}
          />

          {policy.subRegions && policy.subRegions.length >= 6 && (
            <SubRegionSection subRegions={policy.subRegions} />
          )}

          {/* AI 한 줄 요약 — 가이드 있을 때만 */}
          {guide && <OneLineSummaryCard oneLineSummary={guide.oneLineSummary} />}

          {/* Decision Meta Grid (기준연도 / 지원주기 / 제공유형 / 문의처) */}
          <DecisionMetaGrid
            referenceYear={policy.referenceYear}
            supportCycle={policy.supportCycle}
            provideType={policy.provideType}
            contact={policy.contact}
            contactFallback={policy.enrichment?.sections?.contactPhone ?? null}
            enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
          />

          {/* 사업기간 / 지원규모 chip */}
          {(policy.businessPeriodStart || policy.businessPeriodNote || policy.supportScale != null) && (
            <div className="mb-6 flex flex-wrap gap-2 text-sm text-gray-700">
              {policy.businessPeriodStart && policy.businessPeriodEnd && (
                <span className="rounded-full bg-gray-100 px-3 py-1">
                  사업기간: {policy.businessPeriodStart} ~ {policy.businessPeriodEnd}
                  {policy.businessPeriodNote && ` (${policy.businessPeriodNote})`}
                </span>
              )}
              {policy.supportScale != null && (
                <span className="rounded-full bg-gray-100 px-3 py-1">
                  지원규모: {policy.supportScale.toLocaleString()}명
                  {policy.firstComeFirstServed && ' · 선착순'}
                </span>
              )}
            </div>
          )}

          {/* Group 1: 받을 수 있는 사람 */}
          <div ref={decisionEndRef} />
          <PolicyGroupHeader group={POLICY_GROUPS[0]} />

          {/* Policy Summary (원문) — 그룹 1 시작 */}
          <section
            id="policy-summary-section"
            className="mb-6 rounded-2xl border border-neutral-200 bg-white p-6"
          >
            <h3 className="mb-3 text-base font-semibold text-neutral-900">정책 요약</h3>
            <FormattedPolicyText text={policy.summary} />
          </section>

          {/* Paired: 지원대상 */}
          {(() => {
            const pick = pickWithFallback(
              policy.supportTarget,
              policy.enrichment?.sections?.supportTarget,
            );
            return (
              <PairedSection
                id="paired-supportTarget"
                easyTitle="누가 받을 수 있나요"
                easyData={guide?.target ?? null}
                originalTitle="지원대상"
                originalContent={pick?.value ?? null}
                originalRenderer={(c) => <FormattedPolicyText text={c} />}
                originalFromAi={pick?.fromAi ?? false}
                enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
              />
            );
          })()}

          {/* 추가 자격조건 — 지원대상 카드 다음 */}
          {policy.additionalQualification && policy.additionalQualification !== '해당사항 없음' && (
            <section className="mb-6 rounded-lg border border-gray-200 p-4">
              <h3 className="mb-2 font-semibold">추가 자격조건</h3>
              <FormattedPolicyText text={policy.additionalQualification} />
            </section>
          )}

          {/* 참여 제한 대상 — 경고 톤 */}
          {policy.participationRestriction && (
            <section className="mb-6 rounded-lg border border-amber-200 bg-amber-50 p-4">
              <h3 className="mb-2 font-semibold text-amber-900">⚠ 참여 제한 대상</h3>
              <FormattedPolicyText text={policy.participationRestriction} className="text-amber-900" />
            </section>
          )}

          {/* Paired: 선정기준 */}
          {(() => {
            const pick = pickWithFallback(
              policy.selectionCriteria,
              policy.enrichment?.sections?.eligibilityCriteria,
            );
            return (
              <PairedSection
                id="paired-selectionCriteria"
                easyTitle="어떻게 뽑히나요"
                easyData={guide?.criteria ?? null}
                originalTitle="선정기준"
                originalContent={pick?.value ?? null}
                originalRenderer={(c) => <FormattedPolicyText text={c} />}
                originalFromAi={pick?.fromAi ?? false}
                enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
              />
            );
          })()}

          {/* 심사방법 — 선정기준 다음 */}
          {policy.screeningMethod && (
            <section className="mb-6 rounded-lg border border-gray-200 p-4">
              <h3 className="mb-2 font-semibold">심사방법</h3>
              <FormattedPolicyText text={policy.screeningMethod} />
            </section>
          )}

          <PolicyGroupDivider nextLabel="받는 혜택" />

          {/* Group 2: 받는 혜택 */}
          <PolicyGroupHeader group={POLICY_GROUPS[1]} />

          {/* 이 정책의 특징 — 가이드 있고 highlights 있을 때만 */}
          {guide && (
            <HighlightsCard
              highlights={guide.highlights ?? []}
              attachments={policy.attachments ?? []}
              policyAttachments={policy.attachments ?? []}
            />
          )}

          {/* Paired: 지원내용 */}
          {(() => {
            const pick = pickWithFallback(
              policy.supportContent,
              policy.enrichment?.sections?.supportContent,
            );
            return (
              <PairedSection
                id="paired-supportContent"
                easyTitle="무엇을 받나요"
                easyData={guide?.content ?? null}
                originalTitle="지원내용"
                originalContent={pick?.value ?? null}
                originalRenderer={(c) => <FormattedPolicyText text={c} />}
                originalFromAi={pick?.fromAi ?? false}
                enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
              />
            );
          })()}

          <PolicyGroupDivider nextLabel="신청하기" />

          {/* Group 3: 신청하기 */}
          <PolicyGroupHeader group={POLICY_GROUPS[2]} />

          {/* 공식 신청 페이지 CTA — 데스크톱 전용 (모바일은 하단바 PolicyMobileBottomBar 가 동일 링크 제공) */}
          <PolicyApplyCta applyUrl={policy.applyUrl} sourceUrl={policy.sourceUrl} />

          {/* 신청방법 — 가이드 기반 bokjiro-style 카드 */}
          {guide?.applyMethod && (
            <GuideListSectionCard
              title="신청방법"
              emoji="📝"
              section={guide.applyMethod}
              enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
            />
          )}

          {/* 신청기한 — 가이드 기반 bokjiro-style 카드 */}
          {guide?.deadlineNote && (
            <GuideListSectionCard
              title="신청기한"
              emoji="📅"
              section={guide.deadlineNote}
              enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
            />
          )}

          {/* 제출서류 — 가이드 기반 bokjiro-style 카드 */}
          {guide?.requiredDocuments && (
            <GuideListSectionCard
              title="제출서류"
              emoji="📂"
              section={guide.requiredDocuments}
              enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
            />
          )}

          {/* 제출서류 — 지원내용 다음 (가이드 카드 없을 때만 fallback) */}
          {!guide?.requiredDocuments && (() => {
            const pick = pickWithFallback(
              policy.submissionDocuments,
              policy.enrichment?.sections?.requiredDocuments,
            );
            if (!pick) return null;
            return (
              <section className="mb-6 rounded-lg border border-gray-200 p-4">
                <h3 className="mb-2 flex items-center gap-2 font-semibold">
                  제출서류
                  {pick.fromAi && (
                    <AiSourceChip sourceUrl={policy.enrichment?.sourceUrl ?? null} />
                  )}
                </h3>
                <FormattedPolicyText text={pick.value} />
              </section>
            );
          })()}

          {/* 신청방법 — applyMethods 비어있고 enrichment에 있을 때 보조 표시 (가이드 카드 없을 때만) */}
          {!guide?.applyMethod &&
            (!policy.applyMethods || policy.applyMethods.length === 0) &&
            isMeaningful(policy.enrichment?.sections?.applyMethod) && (
              <section className="mb-6 rounded-lg border border-gray-200 p-4">
                <h3 className="mb-2 flex items-center gap-2 font-semibold">
                  신청방법
                  <AiSourceChip sourceUrl={policy.enrichment?.sourceUrl ?? null} />
                </h3>
                <FormattedPolicyText text={policy.enrichment!.sections!.applyMethod!} />
              </section>
            )}

          {/* 마감안내 — enrichment에만 존재 (가이드 카드 없을 때만) */}
          {!guide?.deadlineNote && isMeaningful(policy.enrichment?.sections?.deadlineNote) && (
            <section className="mb-6 rounded-lg border border-gray-200 p-4">
              <h3 className="mb-2 flex items-center gap-2 font-semibold">
                마감안내
                <AiSourceChip sourceUrl={policy.enrichment?.sourceUrl ?? null} />
              </h3>
              <FormattedPolicyText text={policy.enrichment!.sections!.deadlineNote!} />
            </section>
          )}

          <PolicyGroupDivider nextLabel="더 알아보기" />

          {/* Group 4: 더 알아보기 */}
          <PolicyGroupHeader group={POLICY_GROUPS[3]} />

          {/* 문의처 — 가이드 기반 bokjiro-style 카드 */}
          {guide?.contact && (
            <GuideListSectionCard
              title="문의처"
              emoji="☎"
              section={guide.contact}
              enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
            />
          )}

          {/* 기타사항 — 보조 톤 */}
          {policy.additionalNotes && (
            <section className="mb-6 rounded-lg border border-gray-100 bg-gray-50 p-4">
              <h3 className="mb-2 text-sm font-semibold text-gray-600">기타사항</h3>
              <FormattedPolicyText text={policy.additionalNotes} className="italic text-gray-600" />
            </section>
          )}

          {/* Reference Sites */}
          <ReferenceSiteSection referenceSites={policy.referenceSites} />

          {/* Attachments (원본 + enrichment extras 통합) */}
          <AttachmentSection
            attachments={policy.attachments}
            extraAttachments={policy.enrichment?.extraAttachments ?? []}
            enrichmentSourceUrl={policy.enrichment?.sourceUrl ?? null}
          />

          {/* 놓치기 쉬운 점 — 가이드 있고 함정 있을 때만 */}
          {guide && (
            <PitfallsCard
              pitfalls={guide.pitfalls}
              attachments={policy.attachments ?? []}
              policyAttachments={policy.attachments ?? []}
            />
          )}

          {/* Q&A */}
          <div ref={qnaRef}>
            <QnaChatSection
              isAuthenticated={isAuthenticated}
              policyId={policyId}
              onLoginPrompt={() => openLoginPrompt('로그인하면 정책에 대해 질문할 수 있어요')}
            />
          </div>
        </main>

        {/* Right Sidebar (Desktop) */}
        <aside className="hidden lg:col-span-4 lg:block">
          <div className="sticky top-24 space-y-6">
            <PolicyToc activeId={activeId} />
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

      {/* Mobile fixed bottom bar (알림 토글 + 공식 신청 페이지) */}
      <PolicyMobileBottomBar
        applyUrl={policy.applyUrl}
        sourceUrl={policy.sourceUrl}
        isSubscribed={isSubscribed}
        visible={bottomBarVisible}
        onNotificationClick={isSubscribed ? handleUnsubscribeClick : handleSubscribeClick}
      />
    </div>
  );
}
