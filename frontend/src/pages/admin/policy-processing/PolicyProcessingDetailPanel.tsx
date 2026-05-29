import { useAdminPolicyProcessingDetail } from '@/hooks/queries/useAdminPolicyProcessingDetail';
import type {
  AttachmentDetail,
  ReferenceDetail,
  StepDetail,
} from '@/types/adminPolicyProcessing';

/**
 * 정책 처리 현황 펼침 영역 패널.
 *
 * `PolicyProcessingTable` 의 펼친 행 안에서 렌더링되며, 단일 정책의 처리 상세
 * (5단계 / 첨부 / 참고 사이트) 를 3개 표로 보여준다. 펼쳐질 때 자체적으로
 * `useAdminPolicyProcessingDetail` 로 상세 데이터를 가져온다.
 *
 * `onAction` 은 Phase 5 에서 mutation 훅으로 연결된다. Task 21 (현재) 에서는
 * 버튼이 핸들러가 있을 때만 호출하도록 모두 optional 로 둔다.
 */

interface Props {
  policyId: number;
  onAction?: {
    retryStep: (step: string) => void;
    reindexAttachment: (attachmentId: number) => void;
    reindexAllAttachments: () => void;
    reindexRag: () => void;
    reprocess: () => void;
  };
}

export function PolicyProcessingDetailPanel({ policyId, onAction }: Props) {
  const { data, isLoading } = useAdminPolicyProcessingDetail(policyId);

  if (isLoading) return <div className="text-xs text-neutral-500">로딩 중…</div>;
  if (!data) return null;

  return (
    <div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <StepDetailTable steps={data.steps} onRetry={onAction?.retryStep} />
        <AttachmentDetailTable
          attachments={data.attachments}
          onReindex={onAction?.reindexAttachment}
        />
        <ReferenceDetailTable references={data.references} />
      </div>
      <div className="mt-3 flex items-center gap-2">
        <span className="text-xs text-indigo-600">통합 액션</span>
        <button
          type="button"
          onClick={() => onAction?.reindexAllAttachments()}
          className="rounded-md border border-neutral-200 bg-white px-3 py-1 text-xs text-neutral-700 hover:bg-neutral-50"
        >
          첨부 임베딩 재인덱싱
        </button>
        <button
          type="button"
          onClick={() => onAction?.reindexRag()}
          className="rounded-md border border-neutral-200 bg-white px-3 py-1 text-xs text-neutral-700 hover:bg-neutral-50"
        >
          RAG 본문 재인덱싱
        </button>
        <button
          type="button"
          onClick={() => onAction?.reprocess()}
          className="rounded-md border border-neutral-200 bg-white px-3 py-1 text-xs text-neutral-700 hover:bg-neutral-50"
        >
          전체 재처리
        </button>
      </div>
    </div>
  );
}

function StepDetailTable({
  steps,
  onRetry,
}: {
  steps: StepDetail[];
  onRetry?: (step: string) => void;
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div className="bg-indigo-50 px-3 py-1.5 text-[10px] uppercase tracking-wider text-indigo-600">
        5단계 처리 이력
      </div>
      <table className="w-full text-[10px]">
        <thead className="bg-neutral-50">
          <tr className="text-indigo-600">
            <th className="p-1 text-left">단계</th>
            <th className="p-1">STATUS</th>
            <th className="p-1">소요</th>
            <th className="p-1">시도</th>
            <th className="p-1"></th>
          </tr>
        </thead>
        <tbody>
          {steps.map((s) => (
            <tr key={s.step} className="border-t border-neutral-200 text-neutral-700">
              <td className="p-1 text-neutral-900">{s.step}</td>
              <td className={`p-1 ${statusColor(s.status)}`}>{s.status}</td>
              <td className="p-1">
                {s.durationMs ? `${(s.durationMs / 1000).toFixed(1)}s` : '—'}
              </td>
              <td className="p-1">{s.attempt}</td>
              <td className="p-1">
                {s.step !== 'INGESTION' && (
                  <button
                    type="button"
                    onClick={() => onRetry?.(s.step)}
                    className="text-indigo-600 hover:text-green-600"
                    title="재실행"
                  >
                    ⟲
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AttachmentDetailTable({
  attachments,
  onReindex,
}: {
  attachments: AttachmentDetail[];
  onReindex?: (attachmentId: number) => void;
}) {
  if (attachments.length === 0) {
    return (
      <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
        <div className="bg-indigo-50 px-3 py-1.5 text-[10px] uppercase tracking-wider text-indigo-600">
          첨부파일
        </div>
        <div className="p-3 text-xs text-neutral-500">첨부 없음</div>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div className="bg-indigo-50 px-3 py-1.5 text-[10px] uppercase tracking-wider text-indigo-600">
        첨부파일 {attachments.length}건
      </div>
      <table className="w-full text-[10px]">
        <thead className="bg-neutral-50">
          <tr className="text-indigo-600">
            <th className="p-1 text-left">파일명</th>
            <th className="p-1">EXT</th>
            <th className="p-1">EMB</th>
            <th className="p-1"></th>
          </tr>
        </thead>
        <tbody>
          {attachments.map((a) => {
            const needsAction = a.extractionStatus === 'FAILED' || !a.embedded;
            return (
              <tr key={a.attachmentId} className="border-t border-neutral-200 text-neutral-700">
                <td
                  className="max-w-[12ch] truncate p-1 text-neutral-900"
                  title={a.filename}
                >
                  {a.filename}
                </td>
                <td
                  className={`p-1 ${
                    a.extractionStatus === 'EXTRACTED'
                      ? 'text-green-600'
                      : a.extractionStatus === 'FAILED'
                        ? 'text-red-600'
                        : 'text-neutral-500'
                  }`}
                >
                  {a.extractionStatus === 'EXTRACTED'
                    ? '✓'
                    : a.extractionStatus === 'FAILED'
                      ? 'FAIL'
                      : '—'}
                </td>
                <td
                  className={`p-1 ${a.embedded ? 'text-green-600' : 'text-amber-600'}`}
                >
                  {a.embedded ? '✓' : '누락'}
                </td>
                <td className="p-1">
                  {needsAction && (
                    <button
                      type="button"
                      onClick={() => onReindex?.(a.attachmentId)}
                      className="text-indigo-600"
                      title="재인덱싱"
                    >
                      ⟲
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ReferenceDetailTable({ references }: { references: ReferenceDetail[] }) {
  if (references.length === 0) {
    return (
      <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
        <div className="bg-indigo-50 px-3 py-1.5 text-[10px] uppercase tracking-wider text-indigo-600">
          참고 사이트
        </div>
        <div className="p-3 text-[10px] text-neutral-500">
          Phase D 완료 후 채워짐
        </div>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div className="bg-indigo-50 px-3 py-1.5 text-[10px] uppercase tracking-wider text-indigo-600">
        참고 사이트
      </div>
      <table className="w-full text-[10px]">
        <thead className="bg-neutral-50">
          <tr className="text-indigo-600">
            <th className="p-1 text-left">URL</th>
            <th className="p-1">STATUS</th>
            <th className="p-1">청크</th>
          </tr>
        </thead>
        <tbody>
          {references.map((r, i) => (
            <tr key={i} className="border-t border-neutral-200 text-neutral-700">
              <td className="max-w-[16ch] truncate p-1 text-neutral-900" title={r.url}>
                {r.url}
              </td>
              <td
                className={`p-1 ${r.status === 'SUCCESS' ? 'text-green-600' : 'text-amber-600'}`}
              >
                {r.status}
              </td>
              <td className="p-1">{r.chunkCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * 백엔드 ProcessingStatus#name() 문자열을 색상 클래스로 매핑.
 * SUCCESS/FAILED/SKIPPED/IN_PROGRESS 외 (PENDING 등) 는 neutral-500 으로 폴백.
 */
function statusColor(s: string): string {
  switch (s) {
    case 'SUCCESS':
      return 'text-green-600';
    case 'FAILED':
      return 'text-red-600';
    case 'SKIPPED':
      return 'text-neutral-500';
    case 'IN_PROGRESS':
      return 'text-indigo-600';
    default:
      return 'text-neutral-500';
  }
}
