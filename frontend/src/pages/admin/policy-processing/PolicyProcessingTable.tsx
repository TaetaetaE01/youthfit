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

export function PolicyProcessingTable({ items, expandedIds, onToggle, renderDetail }: Props) {
  return (
    <table className="w-full text-xs border-collapse">
      <thead>
        <tr className="border-b border-slate-700 text-blue-300">
          <th className="w-6 p-2 text-left"></th>
          <th className="p-2 text-left">ID</th>
          <th className="p-2 text-left">제목</th>
          <th className="p-2 text-left">완성도</th>
          <th className="p-2 text-left">5단계</th>
          <th className="p-2 text-left">첨부 (임베딩/추출/총)</th>
          <th className="p-2 text-left">참조</th>
          <th className="p-2 text-left">업데이트</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => {
          const isExpanded = expandedIds.has(item.policyId);
          return (
            <React.Fragment key={item.policyId}>
              <tr
                className={cn(
                  'border-b border-slate-800 hover:bg-slate-900 cursor-pointer',
                  isExpanded && 'bg-slate-900',
                )}
                onClick={() => onToggle(item.policyId)}
              >
                <td className="p-2">{isExpanded ? '▾' : '▸'}</td>
                <td className="p-2">{item.policyId}</td>
                <td className="p-2">{item.title}</td>
                <td className="p-2">
                  <CompletenessBadge value={item.completeness} />
                </td>
                <td className="p-2">
                  <StepDots statuses={item.stepStatuses} />
                </td>
                <td className={cn('p-2', attachmentTone(item.attachments))}>
                  {item.attachments.total > 0
                    ? `${item.attachments.embedded}/${item.attachments.extracted}/${item.attachments.total}`
                    : '—'}
                </td>
                <td className="p-2">
                  {item.references.total > 0
                    ? `${item.references.succeeded}/${item.references.total}`
                    : '—'}
                </td>
                <td className="p-2 text-slate-500">{formatRelative(item.updatedAt)}</td>
              </tr>
              {isExpanded && renderDetail && (
                <tr className="bg-slate-950">
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
  );
}

function StepDots({ statuses }: { statuses: Partial<Record<ProcessingStep, ProcessingStatus>> }) {
  return (
    <div className="flex gap-0.5">
      {STEP_ORDER.map((step) => {
        const status = statuses[step];
        const tone =
          status === 'SUCCESS'
            ? 'bg-green-500'
            : status === 'FAILED'
              ? 'bg-red-500'
              : status === 'SKIPPED'
                ? 'bg-slate-500'
                : status === 'IN_PROGRESS'
                  ? 'bg-blue-500 animate-pulse'
                  : 'border border-slate-600';
        return (
          <span
            key={step}
            data-testid="step-dot"
            title={`${step}: ${status ?? '미실행'}`}
            className={cn('inline-block w-2 h-2 rounded-full', tone)}
          />
        );
      })}
    </div>
  );
}

function attachmentTone(a: AttachmentSummary): string {
  if (a.total === 0) return 'text-slate-500';
  if (a.embedded === a.total) return 'text-green-500';
  if (a.embedded === 0) return 'text-red-500';
  return 'text-amber-500';
}

function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 60) return `${min}m`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}h`;
  return `${Math.floor(h / 24)}d`;
}
