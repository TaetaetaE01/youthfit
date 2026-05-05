import { useEffect, useReducer } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getQnaCacheLookup, type QnaCacheLookupDetail } from '@/apis/admin.qnaCache.api';
import { QnaCacheResultBadge } from '@/components/admin/qnaCache/QnaCacheResultBadge';

type State =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ok'; data: QnaCacheLookupDetail };

type Action =
  | { type: 'loaded'; data: QnaCacheLookupDetail }
  | { type: 'failed' };

function reducer(_: State, action: Action): State {
  if (action.type === 'loaded') return { status: 'ok', data: action.data };
  return { status: 'error', message: '데이터를 불러오지 못했습니다.' };
}

export default function AdminQnaCacheDetailPage() {
  const { lookupId } = useParams<{ lookupId: string }>();
  const [state, dispatch] = useReducer(reducer, { status: 'loading' });

  useEffect(() => {
    if (!lookupId) return;
    let cancelled = false;
    getQnaCacheLookup(Number(lookupId))
      .then((data) => { if (!cancelled) dispatch({ type: 'loaded', data }); })
      .catch(() => { if (!cancelled) dispatch({ type: 'failed' }); });
    return () => { cancelled = true; };
  }, [lookupId]);

  if (state.status === 'loading') {
    return <div className="h-64 animate-pulse rounded-lg bg-slate-100" />;
  }

  if (state.status === 'error') {
    return (
      <div className="space-y-4">
        <Link to="/admin/qna-cache" className="text-sm text-blue-600 hover:underline">← 목록</Link>
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-800">
          {state.message}
        </div>
      </div>
    );
  }

  const { data } = state;
  const sim = data.similarityScore ?? null;
  const thr = data.thresholdApplied;

  return (
    <div className="space-y-4">
      <Link to="/admin/qna-cache" className="text-sm text-blue-600 hover:underline">← 목록</Link>

      <div className="rounded-lg bg-white p-6 shadow-sm space-y-4">
        {/* Header: badge + meta */}
        <div className="flex flex-wrap items-center gap-3">
          <QnaCacheResultBadge result={data.result} />
          <span className="text-sm text-gray-500">
            {new Date(data.lookedUpAt).toLocaleString('ko-KR')} · 정책 #{data.policyId}
          </span>
          <span className="ml-auto font-mono text-xs text-gray-400">#{data.id}</span>
        </div>

        {/* Raw question */}
        <section>
          <h3 className="mb-1 text-xs font-medium text-gray-500">질문 (raw)</h3>
          <p className="whitespace-pre-wrap text-sm text-gray-900">{data.questionText}</p>
        </section>

        {/* Normalized question */}
        <section>
          <h3 className="mb-1 text-xs font-medium text-gray-500">정규화</h3>
          <p className="whitespace-pre-wrap text-sm text-gray-700">{data.normalizedText}</p>
        </section>

        {/* HIT: matched cache entry */}
        {data.result === 'HIT' && data.matchedCachedQuestion && (
          <section className="space-y-2 rounded-md border border-emerald-200 bg-emerald-50 p-4">
            <h3 className="text-xs font-medium text-emerald-900">
              매칭된 캐시 질문 (#{data.matchedCachedId})
            </h3>
            <p className="text-sm text-emerald-900">{data.matchedCachedQuestion}</p>
            <div className="text-xs text-emerald-800">
              유사도 {sim?.toFixed(3) ?? '—'} · 임계값(거리) {thr.toFixed(3)}
            </div>
            {data.matchedCachedAnswerExcerpt && (
              <details className="mt-1">
                <summary className="cursor-pointer text-xs text-emerald-700 hover:underline">
                  답변 미리보기
                </summary>
                <p className="mt-2 whitespace-pre-wrap text-sm text-emerald-900">
                  {data.matchedCachedAnswerExcerpt}
                </p>
              </details>
            )}
          </section>
        )}

        {/* BELOW_THRESHOLD: closest candidate */}
        {data.result === 'BELOW_THRESHOLD' && data.matchedCachedQuestion && (
          <section className="space-y-2 rounded-md border border-amber-200 bg-amber-50 p-4">
            <h3 className="text-xs font-medium text-amber-900">
              가장 가까운 후보 (#{data.matchedCachedId})
            </h3>
            <p className="text-sm text-amber-900">{data.matchedCachedQuestion}</p>
            <div className="text-xs text-amber-800">
              유사도 {sim?.toFixed(3) ?? '—'} · 임계값(거리) {thr.toFixed(3)}
            </div>
          </section>
        )}

        {/* MISS: no candidate found */}
        {data.result === 'MISS' && (
          <section className="rounded-md border border-red-200 bg-red-50 p-4">
            <p className="text-sm text-red-900">
              매칭 후보 없음. LLM 호출됨:{' '}
              <span className="font-medium">{data.llmCallMade ? '예' : '아니오'}</span>
            </p>
          </section>
        )}
      </div>
    </div>
  );
}
