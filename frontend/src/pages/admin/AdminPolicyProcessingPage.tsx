import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAdminPolicyProcessingList } from '@/hooks/queries/useAdminPolicyProcessingList';
import { useAdminPolicyProcessingStats } from '@/hooks/queries/useAdminPolicyProcessingStats';
import { useRetryProcessingStep } from '@/hooks/mutations/useRetryProcessingStep';
import { useReindexAttachment } from '@/hooks/mutations/useReindexAttachment';
import { useReindexAllAttachments } from '@/hooks/mutations/useReindexAllAttachments';
import { useReindexRag } from '@/hooks/mutations/useReindexRag';
import { useReprocessPolicy } from '@/hooks/mutations/useReprocessPolicy';
import { PolicyProcessingKpiCards } from './policy-processing/PolicyProcessingKpiCards';
import { PolicyProcessingFilters } from './policy-processing/PolicyProcessingFilters';
import { PolicyProcessingTable } from './policy-processing/PolicyProcessingTable';
import { PolicyProcessingDetailPanel } from './policy-processing/PolicyProcessingDetailPanel';
import { ReprocessConfirmDialog } from './policy-processing/ReprocessConfirmDialog';
import type {
  Filter,
  PolicyProcessingListParams,
  ProcessingStep,
  Sort,
} from '@/types/adminPolicyProcessing';

/**
 * 어드민 정책 처리 현황 대시보드 페이지.
 *
 * KPI 카드, 필터, 표(펼침 포함), 재처리 확인 다이얼로그를 한 화면에서 통합한다.
 * URL 쿼리(`q`, `region`, `filter`, `sort`, `page`, `size`) 로 필터·페이지를 보존해
 * 새로고침/공유 시 같은 화면을 재현한다.
 *
 * 재처리 다이얼로그는 별도 컨테이너 컴포넌트로 분리해 `useReprocessPolicy(policyId)`
 * 가 안정된 policyId 와 함께 마운트되도록 한다 (mutation 훅이 0/null 로 한 번이라도
 * 마운트되는 것을 피함).
 */
export default function AdminPolicyProcessingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const params = useMemo<PolicyProcessingListParams>(
    () => ({
      q: searchParams.get('q') ?? undefined,
      region: searchParams.get('region') ?? undefined,
      filter: (searchParams.get('filter') as Filter | null) ?? 'ALL',
      sort: (searchParams.get('sort') as Sort | null) ?? 'UPDATED_DESC',
      page: Number(searchParams.get('page') ?? 0),
      size: Number(searchParams.get('size') ?? 50),
    }),
    [searchParams],
  );

  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [reprocessFor, setReprocessFor] = useState<{ id: number; title: string } | null>(null);

  const { data: list, isLoading, error } = useAdminPolicyProcessingList(params);
  const { data: stats } = useAdminPolicyProcessingStats();

  const onParamsChange = (next: PolicyProcessingListParams) => {
    const sp = new URLSearchParams();
    if (next.q) sp.set('q', next.q);
    if (next.region) sp.set('region', next.region);
    if (next.filter && next.filter !== 'ALL') sp.set('filter', next.filter);
    if (next.sort && next.sort !== 'UPDATED_DESC') sp.set('sort', next.sort);
    if (next.page && next.page > 0) sp.set('page', String(next.page));
    if (next.size && next.size !== 50) sp.set('size', String(next.size));
    setSearchParams(sp);
  };

  const toggleExpand = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  return (
    <div className="p-4 md:p-6">
      <h1 className="mb-1 text-lg font-semibold">정책 처리 현황</h1>
      <p className="mb-4 text-xs text-slate-500">
        5단계 적재 + 첨부 임베딩 + 참고 사이트 fetch — 전체 {stats?.totalCount ?? '…'}건
      </p>

      {stats && <PolicyProcessingKpiCards stats={stats} />}

      <PolicyProcessingFilters
        params={params}
        onChange={onParamsChange}
        chipCounts={
          stats
            ? {
                ALL: stats.totalCount,
                INCOMPLETE: stats.incompleteCount,
                PARTIAL: stats.partialCount,
                RECENT_24H: stats.recent24hCount,
              }
            : {}
        }
      />

      {isLoading && <div className="text-xs text-slate-500">로딩 중…</div>}
      {error && (
        <div className="rounded border border-red-700 bg-red-950/50 p-3 text-xs text-red-300">
          목록 조회 실패: {error instanceof Error ? error.message : String(error)}
          <br />
          <span className="text-[10px] text-red-400">
            브라우저 DevTools(F12) → Network 탭에서 <code>/api/v1/admin/policies/processing</code> 응답 상태/본문을 확인해 주세요.
          </span>
        </div>
      )}
      {list && list.items.length === 0 && !isLoading && (
        <div className="text-xs text-slate-500">조건에 맞는 정책이 없습니다.</div>
      )}
      {list && list.items.length > 0 && (
        <PolicyProcessingTable
          items={list.items}
          expandedIds={expandedIds}
          onToggle={toggleExpand}
          renderDetail={(policyId) => {
            const matchedItem = list.items.find((i) => i.policyId === policyId);
            return (
              <DetailWithActions
                policyId={policyId}
                onOpenReprocess={() =>
                  setReprocessFor({
                    id: policyId,
                    title: matchedItem?.title ?? `정책 ${policyId}`,
                  })
                }
              />
            );
          }}
        />
      )}

      {reprocessFor && (
        <ReprocessDialogContainer
          policyId={reprocessFor.id}
          policyTitle={reprocessFor.title}
          onClose={() => setReprocessFor(null)}
        />
      )}
    </div>
  );
}

/**
 * 펼침 영역에서 사용할 mutation 훅 묶음.
 *
 * 각 mutation 훅은 안정된 policyId 와 함께 마운트되므로 invalidation 키도
 * 정확히 해당 정책 상세를 가리킨다.
 */
function DetailWithActions({
  policyId,
  onOpenReprocess,
}: {
  policyId: number;
  onOpenReprocess: () => void;
}) {
  const retryStep = useRetryProcessingStep(policyId);
  const reindexAttachment = useReindexAttachment(policyId);
  const reindexAllAttachments = useReindexAllAttachments(policyId);
  const reindexRag = useReindexRag(policyId);

  return (
    <PolicyProcessingDetailPanel
      policyId={policyId}
      onAction={{
        retryStep: (step) => retryStep.mutate(step as ProcessingStep),
        reindexAttachment: (id) => reindexAttachment.mutate(id),
        reindexAllAttachments: () => reindexAllAttachments.mutate(),
        reindexRag: () => reindexRag.mutate(),
        reprocess: onOpenReprocess,
      }}
    />
  );
}

/**
 * 재처리 다이얼로그 컨테이너.
 *
 * `useReprocessPolicy(policyId)` 가 0/null 로 마운트되는 것을 피하려고
 * `reprocessFor !== null` 일 때만 렌더링한다. policyId 는 props 로 들어와 stable 하다.
 */
function ReprocessDialogContainer({
  policyId,
  policyTitle,
  onClose,
}: {
  policyId: number;
  policyTitle: string;
  onClose: () => void;
}) {
  const reprocess = useReprocessPolicy(policyId);
  return (
    <ReprocessConfirmDialog
      open
      policyTitle={policyTitle}
      onClose={onClose}
      isSubmitting={reprocess.isPending}
      onConfirm={(reason) => {
        reprocess.mutate(reason, {
          onSuccess: () => onClose(),
        });
      }}
    />
  );
}
