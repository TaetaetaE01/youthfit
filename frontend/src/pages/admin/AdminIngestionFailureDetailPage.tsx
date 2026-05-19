import { useEffect, useReducer } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  fetchIngestionFailureDetail,
  retryIngestionFailure,
  type IngestionFailureDetailResponse,
} from '@/apis/admin.ingestion.api';
import { FailureReasonBadge } from '@/components/admin/ingestion/FailureReasonBadge';
import { FailureReasonGuide } from '@/components/admin/ingestion/FailureReasonGuide';

type State =
  | { status: 'loading' }
  | { status: 'done'; detail: IngestionFailureDetailResponse }
  | { status: 'error'; error: Error };

type Action =
  | { type: 'LOAD' }
  | { type: 'OK'; detail: IngestionFailureDetailResponse }
  | { type: 'ERR'; error: Error };

function formatJson(raw: string | null): string {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

const N8N_BASE_URL =
  (import.meta.env.VITE_N8N_BASE_URL as string | undefined) ?? 'http://localhost:5678';

function n8nExecutionUrl(executionId: string): string {
  return `${N8N_BASE_URL.replace(/\/$/, '')}/executions/${encodeURIComponent(executionId)}`;
}

function reducer(_: State, action: Action): State {
  switch (action.type) {
    case 'LOAD':
      return { status: 'loading' };
    case 'OK':
      return { status: 'done', detail: action.detail };
    case 'ERR':
      return { status: 'error', error: action.error };
  }
}

export default function AdminIngestionFailureDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [state, dispatch] = useReducer(reducer, { status: 'loading' });

  useEffect(() => {
    if (!id) return;
    dispatch({ type: 'LOAD' });
    fetchIngestionFailureDetail(Number(id))
      .then((detail) => dispatch({ type: 'OK', detail }))
      .catch((e: unknown) =>
        dispatch({ type: 'ERR', error: e instanceof Error ? e : new Error(String(e)) }),
      );
  }, [id]);

  const handleRetry = async () => {
    if (state.status !== 'done') return;
    const r = await retryIngestionFailure(state.detail.id);
    alert(`재처리 결과: ${r.status} — ${r.message}`);
    if (!id) return;
    dispatch({ type: 'LOAD' });
    fetchIngestionFailureDetail(Number(id))
      .then((detail) => dispatch({ type: 'OK', detail }))
      .catch((e: unknown) =>
        dispatch({ type: 'ERR', error: e instanceof Error ? e : new Error(String(e)) }),
      );
  };

  if (state.status === 'loading') {
    return <div className="p-6 text-sm text-slate-500">로딩 중…</div>;
  }
  if (state.status === 'error') {
    return <div className="p-6 text-sm text-red-700">에러: {state.error.message}</div>;
  }

  const { detail } = state;

  return (
    <div className="flex flex-col gap-6 p-6 lg:flex-row lg:items-start">
      <div className="min-w-0 flex-1 space-y-4">
      <header className="flex items-center gap-2">
        <Link to="/admin/ingestion" className="text-sm text-indigo-600">
          ← 목록
        </Link>
        <h1 className="text-xl font-semibold">실패 항목 #{detail.id}</h1>
        <FailureReasonBadge reason={detail.failureReason} />
      </header>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <dt className="text-slate-500">source</dt>
        <dd className="font-mono">{detail.source}</dd>
        <dt className="text-slate-500">externalId</dt>
        <dd className="font-mono text-xs">{detail.sourceItemId ?? '-'}</dd>
        <dt className="text-slate-500">발생 시각</dt>
        <dd>{new Date(detail.createdAt).toLocaleString('ko-KR')}</dd>
        <dt className="text-slate-500">재시도 횟수</dt>
        <dd>{detail.retryCount}</dd>
        <dt className="text-slate-500">마지막 재시도</dt>
        <dd>
          {detail.lastRetriedAt
            ? new Date(detail.lastRetriedAt).toLocaleString('ko-KR')
            : '-'}
        </dd>
      </dl>

      {(detail.n8nWorkflowName || detail.n8nExecutionId || detail.n8nNodeName) && (
        <section className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <h2 className="mb-2 text-sm font-medium text-slate-700">n8n 파이프라인</h2>
          <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-1 text-xs">
            <dt className="text-slate-500">workflow</dt>
            <dd className="font-mono">{detail.n8nWorkflowName ?? '-'}</dd>
            <dt className="text-slate-500">node</dt>
            <dd className="font-mono">{detail.n8nNodeName ?? '-'}</dd>
            <dt className="text-slate-500">execution</dt>
            <dd className="font-mono">
              {detail.n8nExecutionId ? (
                <a
                  href={n8nExecutionUrl(detail.n8nExecutionId)}
                  target="_blank"
                  rel="noreferrer"
                  className="text-indigo-600 hover:underline"
                >
                  {detail.n8nExecutionId} ↗
                </a>
              ) : (
                '-'
              )}
            </dd>
          </dl>
        </section>
      )}

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">에러 메시지</h2>
        <pre className="overflow-x-auto rounded bg-slate-100 p-3 text-xs">{detail.errorMessage}</pre>
        {detail.errorStack && (
          <details className="mt-2">
            <summary className="cursor-pointer text-xs text-slate-500 hover:text-slate-700">
              stack trace 보기
            </summary>
            <pre className="mt-1 max-h-96 overflow-auto rounded bg-slate-900 p-3 text-[11px] leading-relaxed text-slate-100">
              {detail.errorStack}
            </pre>
          </details>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">raw_payload</h2>
        {detail.payloadAvailable ? (
          <pre className="max-h-96 overflow-auto rounded bg-slate-100 p-3 font-mono text-xs leading-relaxed">
            {formatJson(detail.rawPayload)}
          </pre>
        ) : (
          <div className="rounded border border-slate-200 bg-slate-50 p-3 text-xs text-slate-500">
            7일 경과로 redact 됨. hash:{' '}
            <span className="font-mono">{detail.rawPayloadHash ?? '(없음)'}</span>
          </div>
        )}
      </section>

      <div className="flex gap-2">
        <button
          onClick={handleRetry}
          disabled={!detail.payloadAvailable}
          className="rounded bg-indigo-600 px-4 py-2 text-sm text-white disabled:cursor-not-allowed disabled:opacity-60"
          title={detail.payloadAvailable ? '재처리 가능' : '7일 경과로 재처리 불가'}
        >
          재처리
        </button>
      </div>
      </div>
      <aside className="w-full lg:w-80 lg:shrink-0">
        <FailureReasonGuide currentReason={detail.failureReason} />
      </aside>
    </div>
  );
}
