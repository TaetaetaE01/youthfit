import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, X, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/cn';
import PolicyCard from '@/components/policy/PolicyCard';
import LoginPromptModal from '@/components/auth/LoginPromptModal';
import PolicyFilterBar from '@/components/policy/PolicyFilterBar';
import RegionPickerBanner from '@/components/policy/RegionPickerBanner';
import { usePolicies } from '@/hooks/queries/usePolicies';
import { useMyBookmarkIds } from '@/hooks/queries/useMyBookmarkIds';
import { useRegions } from '@/hooks/queries/useRegions';
import { useNotificationSettings } from '@/hooks/queries/useNotificationSettings';
import { useAddBookmark, useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
import { useAuthStore } from '@/stores/authStore';
import type {
  PolicyCategory,
  PolicyStatus,
  SourceType,
} from '@/types/policy';
import { CATEGORY_LABELS, SOURCE_LABELS, isSourceType } from '@/types/policy';
import { SIDO_CODE_BY_ENUM, type RegionSidoCode } from '@/lib/labels/region';
import { parseRegionsParam, toRegionsParam } from '@/lib/regionFilter';

/* ──────────────────────────────────────────────
   Sub-components
   ────────────────────────────────────────────── */

function SkeletonCard() {
  return (
    <div className="rounded-2xl bg-gray-100 p-6 animate-pulse" aria-hidden="true">
      <div className="mb-3 flex gap-2">
        <div className="h-5 w-12 rounded-full bg-gray-200" />
        <div className="h-5 w-10 rounded-full bg-gray-200" />
      </div>
      <div className="h-6 w-3/4 rounded bg-gray-200" />
      <div className="mt-2 h-4 w-full rounded bg-gray-200" />
      <div className="mt-1 h-4 w-2/3 rounded bg-gray-200" />
      <div className="mt-4 flex gap-3">
        <div className="h-4 w-16 rounded bg-gray-200" />
        <div className="h-4 w-28 rounded bg-gray-200" />
      </div>
    </div>
  );
}

const PAGE_SIZE = 6;

const STATUS_TABS: { value: PolicyStatus; label: string }[] = [
  { value: 'OPEN', label: '모집중' },
  { value: 'UPCOMING', label: '예정' },
  { value: 'CLOSED', label: '마감' },
];

const DEFAULT_STATUS: PolicyStatus = 'OPEN';

const STATUS_VALUES = STATUS_TABS.map((t) => t.value) as readonly PolicyStatus[];

function isPolicyStatus(value: string | null): value is PolicyStatus {
  return value !== null && (STATUS_VALUES as readonly string[]).includes(value);
}

/* ──────────────────────────────────────────────
   StatusTabBar
   ────────────────────────────────────────────── */

function StatusTabBar({
  status,
  onStatusChange,
}: {
  status: PolicyStatus;
  onStatusChange: (next: PolicyStatus) => void;
}) {
  return (
    <div
      role="tablist"
      aria-label="정책 상태 필터"
      className="mb-4 flex w-full gap-1 overflow-x-auto rounded-2xl bg-gray-100 p-1"
    >
      {STATUS_TABS.map((tab) => {
        const active = status === tab.value;
        return (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onStatusChange(tab.value)}
            className={cn(
              'min-h-11 flex-1 rounded-xl px-4 text-sm font-semibold transition-colors',
              active
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-900',
            )}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

/* ──────────────────────────────────────────────
   Pagination
   ────────────────────────────────────────────── */

function Pagination({
  currentPage,
  totalPages,
  onPageChange,
}: {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  const pages = useMemo(() => {
    const result: (number | 'ellipsis')[] = [];
    const maxVisible = 5;

    if (totalPages <= maxVisible) {
      for (let i = 0; i < totalPages; i++) result.push(i);
    } else {
      result.push(0);
      const start = Math.max(1, currentPage - 1);
      const end = Math.min(totalPages - 2, currentPage + 1);
      if (start > 1) result.push('ellipsis');
      for (let i = start; i <= end; i++) result.push(i);
      if (end < totalPages - 2) result.push('ellipsis');
      result.push(totalPages - 1);
    }

    return result;
  }, [currentPage, totalPages]);

  return (
    <nav aria-label="페이지네이션" className="mt-10 flex items-center justify-center gap-2">
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 0}
        className="flex h-11 w-11 items-center justify-center rounded-xl bg-gray-100 text-gray-700 transition-colors hover:bg-gray-200 disabled:cursor-not-allowed disabled:opacity-40"
        aria-label="이전 페이지"
      >
        <ChevronLeft className="h-5 w-5" />
      </button>

      {pages.map((p, i) =>
        p === 'ellipsis' ? (
          <span key={`ellipsis-${i}`} className="flex h-11 w-11 items-center justify-center text-gray-400">
            ...
          </span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            aria-current={p === currentPage ? 'page' : undefined}
            className={cn(
              'flex h-11 w-11 items-center justify-center rounded-xl text-sm font-semibold transition-colors',
              p === currentPage
                ? 'bg-brand-800 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
            )}
          >
            {p + 1}
          </button>
        ),
      )}

      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage >= totalPages - 1}
        className="flex h-11 w-11 items-center justify-center rounded-xl bg-gray-100 text-gray-700 transition-colors hover:bg-gray-200 disabled:cursor-not-allowed disabled:opacity-40"
        aria-label="다음 페이지"
      >
        <ChevronRight className="h-5 w-5" />
      </button>
    </nav>
  );
}

/* ──────────────────────────────────────────────
   PolicyListPage
   ────────────────────────────────────────────── */

export default function PolicyListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const [inputValue, setInputValue] = useState(searchParams.get('keyword') ?? '');
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const { data: bookmarkIdPairs } = useMyBookmarkIds();
  const { data: regionData } = useRegions();
  const { data: notificationSettings } = useNotificationSettings();
  const addBookmarkMutation = useAddBookmark();
  const removeBookmarkMutation = useRemoveBookmark();
  const autoAppliedRef = useRef(false);

  const bookmarkMap = useMemo(() => {
    const map = new Map<number, number>();
    for (const pair of bookmarkIdPairs ?? []) {
      map.set(pair.policyId, pair.bookmarkId);
    }
    return map;
  }, [bookmarkIdPairs]);

  const handleBookmarkToggle = useCallback(
    (policyId: number) => {
      if (!isAuthenticated) {
        setLoginModalOpen(true);
        return;
      }
      const bookmarkId = bookmarkMap.get(policyId);
      if (bookmarkId != null) {
        removeBookmarkMutation.mutate(bookmarkId);
      } else {
        addBookmarkMutation.mutate(policyId);
      }
    },
    [isAuthenticated, bookmarkMap, addBookmarkMutation, removeBookmarkMutation],
  );

  // Read URL params
  const keyword = searchParams.get('keyword') ?? '';
  const category = (searchParams.get('category') ?? '') as PolicyCategory | '';
  const rawStatus = searchParams.get('status');
  const status: PolicyStatus = isPolicyStatus(rawStatus) ? rawStatus : DEFAULT_STATUS;
  const rawSource = searchParams.get('source');
  const source: SourceType | '' = isSourceType(rawSource) ? rawSource : '';
  const regions = parseRegionsParam(searchParams.get('regions'));
  const page = Math.max(0, parseInt(searchParams.get('page') ?? '0', 10) || 0);

  // Legacy ?regionCode=SEOUL → ?regions=11 정규화 (1회)
  useEffect(() => {
    const legacy = searchParams.get('regionCode');
    if (!legacy) return;
    const mapped = SIDO_CODE_BY_ENUM[legacy as RegionSidoCode];
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete('regionCode');
        if (mapped) next.set('regions', mapped);
        return next;
      },
      { replace: true },
    );
  }, [searchParams, setSearchParams]);

  // 자동 적용 effect — 로그인 + interestRegions 존재 + URL에 regions 없음일 때 1회 적용
  useEffect(() => {
    if (autoAppliedRef.current) return;
    if (!isAuthenticated) return;
    if (searchParams.has('regions')) return;
    if (!notificationSettings) return;
    const interest = notificationSettings.interestRegions;
    if (!interest || interest.length === 0) return;

    const sidoCode = SIDO_CODE_BY_ENUM[interest[0]];
    if (!sidoCode) return;

    autoAppliedRef.current = true;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('regions', sidoCode);
        next.delete('page');
        return next;
      },
      { replace: true },
    );
  }, [isAuthenticated, notificationSettings, searchParams, setSearchParams]);

  // 자동 적용된 라벨 (배너용)
  const autoBannerLabel = useMemo(() => {
    if (!isAuthenticated || bannerDismissed) return null;
    if (!autoAppliedRef.current) return null;
    if (regions.length !== 1 || regions[0].length !== 2) return null;
    const sido = regionData?.sidos.find((s) => s.code === regions[0]);
    return sido ? sido.name : null;
  }, [isAuthenticated, bannerDismissed, regions, regionData]);

  // Fetch data via API
  const { data, isLoading, isError, refetch } = usePolicies({
    keyword: keyword || undefined,
    category,
    source,
    status,
    regions: regions.length > 0 ? regions : undefined,
    page,
    size: PAGE_SIZE,
  });

  // Helpers to update URL
  const updateParams = useCallback(
    (updates: Record<string, string>) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        for (const [k, v] of Object.entries(updates)) {
          if (v) {
            next.set(k, v);
          } else {
            next.delete(k);
          }
        }
        return next;
      });
    },
    [setSearchParams],
  );

  const handleStatusTabChange = useCallback(
    (next: PolicyStatus) => {
      updateParams({ status: next === DEFAULT_STATUS ? '' : next, page: '' });
    },
    [updateParams],
  );

  const handleRegionApply = useCallback(
    (codes: string[]) => {
      updateParams({ regions: codes.length > 0 ? toRegionsParam(codes) : '', page: '' });
    },
    [updateParams],
  );

  const handleBannerDismiss = useCallback(() => {
    setBannerDismissed(true);
    updateParams({ regions: '' });
  }, [updateParams]);

  const resetFilters = useCallback(() => {
    setSearchParams(new URLSearchParams());
    setInputValue('');
  }, [setSearchParams]);

  // Debounced keyword sync
  useEffect(() => {
    const timer = setTimeout(() => {
      if (inputValue !== keyword) {
        updateParams({ keyword: inputValue, page: '' });
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [inputValue, keyword, updateParams]);

  // Sync input when URL keyword changes externally
  useEffect(() => {
    setInputValue(searchParams.get('keyword') ?? '');
  }, [searchParams]);

  // Active filter badges (지역은 트리거 자체가 활성 칩 역할이므로 제외)
  const activeFilters: { key: string; label: string }[] = [];
  if (category) activeFilters.push({ key: 'category', label: CATEGORY_LABELS[category] });
  if (source) activeFilters.push({ key: 'source', label: SOURCE_LABELS[source] });

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateParams({ keyword: inputValue, page: '' });
  };

  const isSearchMode = Boolean(keyword);
  const hasActiveQuery = Boolean(
    keyword || category || source || regions.length > 0 || status !== DEFAULT_STATUS,
  );

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6 md:py-12">
      {/* ── Header ── */}
      <header className="mb-8 text-center">
        <h1 className="text-2xl font-bold text-gray-900 md:text-3xl">청년 정책 찾기</h1>
        <p className="mt-2 text-sm text-gray-500 md:text-base">
          나에게 맞는 청년 정책을 찾아보세요.
        </p>
      </header>

      {/* ── Search bar ── */}
      <form
        role="search"
        aria-label="정책 검색"
        onSubmit={handleSearchSubmit}
        className="mx-auto mb-6 flex max-w-[680px] items-center gap-2 rounded-[20px] border border-neutral-200 bg-white px-4 shadow-card transition-shadow focus-within:shadow-lg"
      >
        <Search className="h-5 w-5 shrink-0 text-neutral-500" aria-hidden="true" />
        <input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="키워드로 정책을 검색하세요"
          className="h-14 flex-1 bg-transparent text-sm text-gray-900 placeholder:text-neutral-400 focus:outline-none"
          aria-label="정책 검색어"
        />
        <button
          type="submit"
          className="shrink-0 rounded-full bg-brand-800 px-5 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
        >
          검색
        </button>
      </form>

      {/* 자동 적용 배너 */}
      {autoBannerLabel && (
        <RegionPickerBanner regionLabel={autoBannerLabel} onDismiss={handleBannerDismiss} />
      )}

      <StatusTabBar status={status} onStatusChange={handleStatusTabChange} />

      <PolicyFilterBar
        category={category}
        source={source}
        regions={regions}
        regionData={regionData}
        onCategoryChange={(v) => updateParams({ category: v, page: '' })}
        onSourceChange={(v) => updateParams({ source: v, page: '' })}
        onRegionsChange={handleRegionApply}
        disabled={isSearchMode}
        disabledHint="검색 결과에는 지역·출처 필터가 적용되지 않습니다"
      />

      {/* ── Active filter badges ── */}
      {activeFilters.length > 0 && (
        <div className="mb-4 flex flex-wrap gap-2">
          {activeFilters.map((f) => (
            <span
              key={f.key}
              className="inline-flex items-center gap-1 rounded-full bg-brand-100 px-3 py-1 text-sm font-semibold text-indigo-600"
            >
              {f.label}
              <button
                onClick={() => updateParams({ [f.key]: '', page: '' })}
                className="ml-0.5 flex h-4 w-4 items-center justify-center rounded-full hover:bg-brand-200"
                aria-label={`${f.label} 필터 제거`}
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      {/* ── Result meta ── */}
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-gray-500">
          {data ? (
            <>
              <span className="font-semibold text-gray-900">{data.totalCount ?? 0}개</span> 정책
            </>
          ) : (
            <span>&nbsp;</span>
          )}
        </p>
      </div>

      {/* ── Content area ── */}
      {isLoading && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6" aria-busy="true" aria-label="정책 로딩 중">
          {Array.from({ length: PAGE_SIZE }).map((_, i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      )}

      {isError && (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <AlertCircle className="mb-4 h-12 w-12 text-error-500" />
          <h2 className="text-lg font-semibold text-gray-900">정책을 불러오지 못했습니다</h2>
          <p className="mt-1 text-sm text-gray-500">잠시 후 다시 시도해주세요.</p>
          <button
            onClick={() => refetch()}
            className="mt-4 rounded-xl bg-brand-800 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
          >
            다시 시도
          </button>
        </div>
      )}

      {!isLoading && !isError && data && (data.content?.length ?? 0) === 0 && (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          {hasActiveQuery ? (
            <>
              <Search className="mb-4 h-12 w-12 text-gray-300" />
              <h2 className="text-lg font-semibold text-gray-900">검색 결과가 없습니다</h2>
              <p className="mt-1 text-sm text-gray-500">다른 키워드나 필터로 다시 시도해보세요.</p>
              <button
                onClick={resetFilters}
                className="mt-4 rounded-xl border border-neutral-200 bg-white px-6 py-3 text-sm font-semibold text-gray-700 transition-colors hover:bg-gray-50"
              >
                필터 초기화
              </button>
            </>
          ) : (
            <>
              <Search className="mb-4 h-12 w-12 text-gray-300" />
              <h2 className="text-lg font-semibold text-gray-900">아직 등록된 정책이 없어요</h2>
              <p className="mt-1 text-sm text-gray-500">
                정책 데이터를 수집 중이에요. 잠시 후 다시 확인해주세요.
              </p>
              <button
                onClick={() => refetch()}
                className="mt-4 rounded-xl bg-brand-800 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
              >
                새로고침
              </button>
            </>
          )}
        </div>
      )}

      {!isLoading && !isError && data && (data.content?.length ?? 0) > 0 && (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6">
            {(data.content ?? []).map((policy) => (
              <PolicyCard
                key={policy.id}
                policy={policy}
                isBookmarked={bookmarkMap.has(policy.id)}
                onBookmarkToggle={handleBookmarkToggle}
              />
            ))}
          </div>

          <Pagination
            currentPage={data.page ?? 0}
            totalPages={data.totalPages ?? 1}
            onPageChange={(p) => updateParams({ page: String(p) })}
          />
        </>
      )}

      <LoginPromptModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        message="로그인하면 정책을 북마크할 수 있어요"
      />
    </div>
  );
}
