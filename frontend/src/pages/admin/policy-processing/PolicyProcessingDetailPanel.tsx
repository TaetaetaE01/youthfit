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

/**
 * 5단계 표는 항상 모든 단계를 보여준다 — 백엔드 응답에 누락된 단계는
 * "미실행" 으로 placeholder 처리해 사용자가 어떤 단계가 안 돌았는지 즉시 인지하게 한다.
 */
const STEP_ORDER: Array<'INGESTION' | 'ENRICHMENT' | 'GUIDE' | 'RULE' | 'RAG_INDEXING'> = [
  'INGESTION',
  'ENRICHMENT',
  'GUIDE',
  'RULE',
  'RAG_INDEXING',
];

/** 표 헤더 공통 스타일 — 5단계 / 첨부 / 참조 3표가 같은 시각 hierarchy 를 유지한다. */
const PANEL_HEADER_CLASS =
  'bg-indigo-50 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-indigo-600';

/** 표 헤더 셀 공통 스타일. */
const TH_BASE = 'p-2 text-xs font-semibold uppercase tracking-wider text-indigo-600';

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
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <span className="mr-2 text-xs font-semibold uppercase tracking-wider text-indigo-600">
          통합 액션
        </span>
        <button
          type="button"
          onClick={() => onAction?.reindexAllAttachments()}
          className="rounded-md border border-neutral-300 bg-white px-3 py-1.5 text-sm text-neutral-700 hover:bg-neutral-50"
        >
          첨부 임베딩 재인덱싱
        </button>
        <button
          type="button"
          onClick={() => onAction?.reindexRag()}
          className="rounded-md border border-neutral-300 bg-white px-3 py-1.5 text-sm text-neutral-700 hover:bg-neutral-50"
        >
          RAG 본문 재인덱싱
        </button>
        <button
          type="button"
          onClick={() => onAction?.reprocess()}
          className="rounded-md border border-neutral-300 bg-white px-3 py-1.5 text-sm text-neutral-700 hover:bg-neutral-50"
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
  const byStep = new Map(steps.map((s) => [s.step, s]));
  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div className={PANEL_HEADER_CLASS}>5단계 처리 이력</div>
      <table className="w-full text-xs tabular-nums">
        <thead className="bg-neutral-50">
          <tr>
            <th className={`${TH_BASE} w-32 text-left`}>단계</th>
            <th className={`${TH_BASE} w-28 text-left`}>STATUS</th>
            <th className={`${TH_BASE} w-16 text-right`}>소요</th>
            <th className={`${TH_BASE} w-12 text-right`}>시도</th>
            <th className={`${TH_BASE} w-10 text-center`}></th>
          </tr>
        </thead>
        <tbody>
          {STEP_ORDER.map((step) => {
            const s = byStep.get(step);
            if (!s) {
              return (
                <tr key={step} className="border-t border-neutral-200">
                  <td className="p-2 align-middle text-left text-neutral-700">{step}</td>
                  <td className="p-2 align-middle text-left text-neutral-400">미실행</td>
                  <td className="p-2 align-middle text-right text-neutral-300">—</td>
                  <td className="p-2 align-middle text-right text-neutral-300">—</td>
                  <td className="p-2 align-middle text-center"></td>
                </tr>
              );
            }
            return (
              <tr key={step} className="border-t border-neutral-200">
                <td className="p-2 align-middle text-left text-neutral-700">{s.step}</td>
                <td
                  className={`p-2 align-middle text-left ${statusColor(s.status)}`}
                >
                  {s.status}
                </td>
                <td className="p-2 align-middle text-right text-neutral-700">
                  {s.durationMs ? `${(s.durationMs / 1000).toFixed(1)}s` : '—'}
                </td>
                <td className="p-2 align-middle text-right text-neutral-700">{s.attempt}</td>
                <td className="p-2 align-middle text-center">
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
            );
          })}
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
        <div className={PANEL_HEADER_CLASS}>첨부파일</div>
        <div className="flex min-h-[140px] items-center justify-center text-sm text-neutral-400">
          첨부 없음
        </div>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div className={PANEL_HEADER_CLASS}>첨부파일 {attachments.length}건</div>
      <table className="w-full table-fixed text-xs tabular-nums">
        <thead className="bg-neutral-50">
          <tr>
            <th className={`${TH_BASE} text-left`}>파일명</th>
            <th className={`${TH_BASE} w-14 text-center`}>DL</th>
            <th className={`${TH_BASE} w-14 text-center`}>EXT</th>
            <th className={`${TH_BASE} w-14 text-center`}>EMB</th>
            <th className={`${TH_BASE} w-10 text-center`}></th>
          </tr>
        </thead>
        <tbody>
          {attachments.map((a) => {
            const needsAction = a.extractionStatus === 'FAILED' || !a.embedded;
            const dlTone = downloadTone(a.extractionStatus);
            return (
              <tr key={a.attachmentId} className="border-t border-neutral-200">
                <td
                  className="min-w-0 truncate p-2 align-middle text-left text-neutral-700"
                  title={a.filename}
                >
                  {a.filename}
                </td>
                <td className={`p-2 align-middle text-center ${dlTone.className}`}>
                  {dlTone.label}
                </td>
                <td
                  className={`p-2 align-middle text-center ${
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
                  className={`p-2 align-middle text-center ${a.embedded ? 'text-green-600' : 'text-amber-600'}`}
                >
                  {a.embedded ? '✓' : '누락'}
                </td>
                <td className="p-2 align-middle text-center">
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
        <div className={PANEL_HEADER_CLASS}>참고 사이트</div>
        <div className="flex min-h-[140px] flex-col items-center justify-center gap-1 text-center text-sm text-neutral-400">
          <span>참조 사이트 데이터 없음</span>
          <span className="text-xs text-neutral-400">Phase D 완료 후 채워짐</span>
        </div>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div className={PANEL_HEADER_CLASS}>참고 사이트 {references.length}건</div>
      <table className="w-full table-fixed text-xs tabular-nums">
        <thead className="bg-neutral-50">
          <tr>
            <th className={`${TH_BASE} text-left`}>URL</th>
            <th className={`${TH_BASE} w-32 text-left`}>STATUS</th>
            <th className={`${TH_BASE} w-12 text-right`}>청크</th>
          </tr>
        </thead>
        <tbody>
          {references.map((r, i) => (
            <tr key={i} className="border-t border-neutral-200">
              <td
                className="min-w-0 truncate p-2 align-middle text-left text-neutral-700"
                title={r.url}
              >
                {r.url}
              </td>
              <td
                className={`p-2 align-middle text-left ${r.status === 'SUCCESS' ? 'text-green-600' : 'text-amber-600'}`}
              >
                {r.status}
              </td>
              <td className="p-2 align-middle text-right text-neutral-700">{r.chunkCount}</td>
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

/**
 * 첨부파일 다운로드 상태(DL 컬럼) 를 extractionStatus 로부터 파생.
 *
 * 백엔드 AttachmentStatus: PENDING | DOWNLOADING | DOWNLOADED | EXTRACTING | EXTRACTED | FAILED | SKIPPED
 * - DOWNLOADED 이후 단계는 다운로드 성공으로 본다.
 * - DOWNLOADING 은 진행 중.
 * - FAILED 는 실패(추출 실패와 구분되지 않지만 동일하게 표시).
 */
function downloadTone(status: string): { label: string; className: string } {
  switch (status) {
    case 'DOWNLOADED':
    case 'EXTRACTING':
    case 'EXTRACTED':
      return { label: '✓', className: 'text-green-600' };
    case 'DOWNLOADING':
      return { label: '…', className: 'text-indigo-600' };
    case 'FAILED':
      return { label: 'FAIL', className: 'text-red-600' };
    case 'SKIPPED':
      return { label: '—', className: 'text-neutral-400' };
    default:
      return { label: '—', className: 'text-neutral-500' };
  }
}
