import { useState, useEffect, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, SlidersHorizontal, X, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/cn';
import PolicyCard from '@/components/policy/PolicyCard';
import LoginPromptModal from '@/components/auth/LoginPromptModal';
import { usePolicies } from '@/hooks/queries/usePolicies';
import { useMyBookmarkIds } from '@/hooks/queries/useMyBookmarkIds';
import { useAddBookmark, useRemoveBookmark } from '@/hooks/mutations/useToggleBookmark';
import { useAuthStore } from '@/stores/authStore';
import type {
  PolicyCategory,
  PolicyStatus,
} from '@/types/policy';
import {
  CATEGORY_LABELS,
  STATUS_LABELS,
  REGION_OPTIONS,
} from '@/types/policy';

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

const SORT_OPTIONS = [
  { value: 'createdAt:false', label: '최신순' },
  { value: 'applyEnd:true', label: '마감임박순' },
] as const;

const CATEGORY_ENTRIES = Object.entries(CATEGORY_LABELS) as [PolicyCategory, string][];
const STATUS_ENTRIES = Object.entries(STATUS_LABELS) as [PolicyStatus, string][];

/* ──────────────────────────────────────────────
   MobileFilterSheet
   ────────────────────────────────────────────── */

function MobileFilterSheet({
  isOpen,
  onClose,
  category,
  status,
  regionCode,
  onCategoryChange,
  onStatusChange,
  onRegionChange,
}: {
  isOpen: boolean;
  onClose: () => void;
  category: PolicyCategory | '';
  status: PolicyStatus | '';
  regionCode: string;
  onCategoryChange: (v: PolicyCategory | '') => void;
  onStatusChange: (v: PolicyStatus | '') => void;
  onRegionChange: (v: string) => void;
}) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="absolute bottom-0 left-0 right-0 rounded-t-2xl bg-white p-6 pb-8 shadow-xl">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">필터</h2>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full hover:bg-gray-100"
            aria-label="필터 닫기"
          >
            <X className="h-5 w-5 text-gray-500" />
          </button>
        </div>

        <fieldset>
          <legend className="mb-2 text-sm font-semibold text-gray-700">카테고리</legend>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_ENTRIES.map(([key, label]) => (
              <button
                key={key}
                onClick={() => onCategoryChange(category === key ? '' : key)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                  category === key
                    ? 'border-transparent bg-brand-100 text-indigo-600'
                    : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </fieldset>

        <fieldset className="mt-5">
          <legend className="mb-2 text-sm font-semibold text-gray-700">모집 상태</legend>
          <div className="flex flex-wrap gap-2">
            {STATUS_ENTRIES.map(([key, label]) => (
              <button
                key={key}
                onClick={() => onStatusChange(status === key ? '' : key)}
                className={cn(
                  'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
                  status === key
                    ? 'border-transparent bg-brand-100 text-indigo-600'
                    : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </fieldset>

        <fieldset className="mt-5">
          <legend className="mb-2 text-sm font-semibold text-gray-700">지역</legend>
          <select
            value={regionCode}
            onChange={(e) => onRegionChange(e.target.value)}
            className="w-full rounded-xl border border-neutral-200 bg-white px-4 py-3 text-sm text-gray-700 focus:border-brand-800 focus:outline-none focus:ring-1 focus:ring-brand-800"
            aria-label="지역 선택"
          >
            <option value="">전체 지역</option>
            {REGION_OPTIONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </fieldset>

        <button
          onClick={onClose}
          className="mt-6 w-full rounded-xl bg-brand-800 py-3 text-sm font-semibold text-white transition-colors hover:bg-brand-900"
        >
          필터 적용
        </button>
      </div>
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
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [inputValue, setInputValue] = useState(searchParams.get('keyword') ?? '');
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const { data: bookmarkIdPairs } = useMyBookmarkIds();
  const addBookmarkMutation = useAddBookmark();
  const removeBookmarkMutation = useRemoveBookmark();

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
  const status = (searchParams.get('status') ?? '') as PolicyStatus | '';
  const regionCode = searchParams.get('regionCode') ?? '';
  const sortParam = searchParams.get('sortBy') ?? 'createdAt';
  const ascendingParam = searchParams.get('ascending') ?? 'false';
  const page = Math.max(0, parseInt(searchParams.get('page') ?? '0', 10) || 0);

  // Fetch data via API
  const { data, isLoading, isError, refetch } = usePolicies({
    keyword: keyword || undefined,
    category,
    status,
    regionCode: regionCode || undefined,
    sortBy: sortParam,
    ascending: ascendingParam === 'true',
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

  // Active filter badges
  const activeFilters: { key: string; label: string }[] = [];
  if (category) activeFilters.push({ key: 'category', label: CATEGORY_LABELS[category] });
  if (status) activeFilters.push({ key: 'status', label: STATUS_LABELS[status] });
  if (regionCode) {
    const regionLabel = REGION_OPTIONS.find((r) => r.value === regionCode)?.label;
    if (regionLabel) activeFilters.push({ key: 'regionCode', label: regionLabel });
  }

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateParams({ keyword: inputValue, page: '' });
  };

  const sortValue = `${sortParam}:${ascendingParam}`;
  const hasActiveQuery = Boolean(keyword || category || status || regionCode);

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

      {/* ── Desktop Filters ── */}
      <div className="mb-4 hidden flex-wrap items-center gap-2 md:flex">
        {CATEGORY_ENTRIES.map(([key, label]) => (
          <button
            key={key}
            onClick={() => updateParams({ category: category === key ? '' : key, page: '' })}
            className={cn(
              'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
              category === key
                ? 'border-transparent bg-brand-100 text-indigo-600'
                : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
            )}
          >
            {label}
          </button>
        ))}

        <span className="mx-1 h-6 w-px bg-neutral-200" aria-hidden="true" />

        {STATUS_ENTRIES.map(([key, label]) => (
          <button
            key={key}
            onClick={() => updateParams({ status: status === key ? '' : key, page: '' })}
            className={cn(
              'rounded-full border px-4 py-2 text-sm font-semibold transition-colors',
              status === key
                ? 'border-transparent bg-brand-100 text-indigo-600'
                : 'border-neutral-200 bg-white text-neutral-700 hover:bg-gray-50',
            )}
          >
            {label}
          </button>
        ))}

        <span className="mx-1 h-6 w-px bg-neutral-200" aria-hidden="true" />

        <select
          value={regionCode}
          onChange={(e) => updateParams({ regionCode: e.target.value, page: '' })}
          className="rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold text-neutral-700 focus:border-brand-800 focus:outline-none focus:ring-1 focus:ring-brand-800"
          aria-label="지역 필터"
        >
          <option value="">전체 지역</option>
          {REGION_OPTIONS.map((r) => (
            <option key={r.value} value={r.value}>
              {r.label}
            </option>
          ))}
        </select>
      </div>

      {/* ── Mobile Filter Button ── */}
      <div className="mb-4 md:hidden">
        <button
          onClick={() => setFilterSheetOpen(true)}
          className="flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-4 py-2 text-sm font-semibold text-neutral-700 transition-colors hover:bg-gray-50"
          aria-label="필터 열기"
        >
          <SlidersHorizontal className="h-4 w-4" />
          필터
          {activeFilters.length > 0 && (
            <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-800 text-xs text-white">
              {activeFilters.length}
            </span>
          )}
        </button>
      </div>

      <MobileFilterSheet
        isOpen={filterSheetOpen}
        onClose={() => setFilterSheetOpen(false)}
        category={category}
        status={status}
        regionCode={regionCode}
        onCategoryChange={(v) => updateParams({ category: v, page: '' })}
        onStatusChange={(v) => updateParams({ status: v, page: '' })}
        onRegionChange={(v) => updateParams({ regionCode: v, page: '' })}
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

      {/* ── Result meta + sort ── */}
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
        <select
          value={sortValue}
          onChange={(e) => {
            const [sb, asc] = e.target.value.split(':');
            updateParams({ sortBy: sb, ascending: asc, page: '' });
          }}
          className="rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm text-gray-700 focus:border-brand-800 focus:outline-none focus:ring-1 focus:ring-brand-800"
          aria-label="정렬 기준"
        >
          {SORT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
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
