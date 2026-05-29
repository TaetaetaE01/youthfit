import React from 'react';
import type {
  AttachmentSummary,
  PolicyProcessingItem,
  ProcessingStatus,
  ProcessingStep,
} from '@/types/adminPolicyProcessing';
import { cn } from '@/lib/cn';
import { CompletenessBadge } from './CompletenessBadge';

const STEP_ORDER: ProcessingStep[] = ['INGESTION', 'ENRICHMENT', 'GUIDE', 'RULE', 'RAG_INDEXING'];

interface Props {
  items: PolicyProcessingItem[];
  expandedIds: Set<number>;
  onToggle: (policyId: number) => void;
  renderDetail?: (policyId: number) => React.ReactNode;
}

/** 표 행 공통 셀 스타일. 모든 <td> 가 같은 vertical-align/height/font 를 가지게 한다. */
const TD_BASE = 'py-3 px-2 align-middle text-sm text-neutral-700';

/** 1차 표 헤더 셀 공통 스타일. 5단계 표 헤더와 시각 hierarchy 를 맞춘다. */
const TH_BASE =
  'py-2 px-2 text-left text-xs font-semibold uppercase tracking-wider text-indigo-600';

export function PolicyProcessingTable({ items, expandedIds, onToggle, renderDetail }: Props) {
  return (
    <div className="w-full max-w-full overflow-x-auto rounded-2xl border border-neutral-200 bg-white shadow-sm">
      <table className="w-full border-collapse text-sm">
        <thead className="bg-neutral-50">
          <tr className="border-b border-neutral-200">
            <th className={cn(TH_BASE, 'w-6')}></th>
            <th className={TH_BASE}>ID</th>
            <th className={TH_BASE}>제목</th>
            <th className={TH_BASE}>완성도</th>
            <th className={TH_BASE}>5단계</th>
            <th className={TH_BASE}>첨부 (임베딩/추출/총)</th>
            <th className={TH_BASE}>참조</th>
            <th className={TH_BASE}>업데이트</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const isExpanded = expandedIds.has(item.policyId);
            return (
              <React.Fragment key={item.policyId}>
                <tr
                  className={cn(
                    'border-b border-neutral-200 hover:bg-neutral-50 cursor-pointer',
                    isExpanded && 'bg-neutral-50',
                  )}
                  onClick={() => onToggle(item.policyId)}
                  style={{ minHeight: 44 }}
                >
                  <td className={cn(TD_BASE, 'text-neutral-500')}>
                    {isExpanded ? '▾' : '▸'}
                  </td>
                  <td className={cn(TD_BASE, 'tabular-nums text-neutral-900')}>
                    {item.policyId}
                  </td>
                  <td className={cn(TD_BASE, 'text-neutral-900')}>{item.title}</td>
                  <td className={TD_BASE}>
                    <CompletenessBadge value={item.completeness} />
                  </td>
                  <td className={TD_BASE}>
                    <StepDots statuses={item.stepStatuses} />
                  </td>
                  <td className={cn(TD_BASE, 'tabular-nums', attachmentTone(item.attachments))}>
                    {item.attachments.total > 0
                      ? `${item.attachments.embedded}/${item.attachments.extracted}/${item.attachments.total}`
                      : '—'}
                  </td>
                  <td className={cn(TD_BASE, 'tabular-nums')}>
                    {item.references.total > 0
                      ? `${item.references.succeeded}/${item.references.total}`
                      : '—'}
                  </td>
                  <td className={cn(TD_BASE, 'tabular-nums text-neutral-500')}>
                    {formatRelative(item.updatedAt)}
                  </td>
                </tr>
                {isExpanded && renderDetail && (
                  <tr className="bg-neutral-50">
                    <td colSpan={8} className="p-4">
                      {renderDetail(item.policyId)}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function StepDots({ statuses }: { statuses: Partial<Record<ProcessingStep, ProcessingStatus>> }) {
  return (
    <div className="flex items-center gap-0.5">
      {STEP_ORDER.map((step) => {
        const status = statuses[step];
        const tone =
          status === 'SUCCESS'
            ? 'bg-green-500'
            : status === 'FAILED'
              ? 'bg-red-500'
              : status === 'SKIPPED'
                ? 'bg-neutral-400'
                : status === 'IN_PROGRESS'
                  ? 'bg-indigo-500 animate-pulse'
                  : 'border border-neutral-300';
        const tooltip = status
          ? `${step}: ${status}`
          : `${step}: 미실행 (기록 없음)`;
        return (
          <span
            key={step}
            data-testid="step-dot"
            role="img"
            aria-label={tooltip}
            title={tooltip}
            className={cn('inline-block w-2 h-2 rounded-full align-middle', tone)}
          />
        );
      })}
    </div>
  );
}

function attachmentTone(a: AttachmentSummary): string {
  if (a.total === 0) return 'text-neutral-500';
  if (a.embedded === a.total) return 'text-green-600';
  if (a.embedded === 0) return 'text-red-600';
  return 'text-amber-600';
}

function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 60) return `${min}m`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}h`;
  return `${Math.floor(h / 24)}d`;
}
