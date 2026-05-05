import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAdminEmailAttempt } from '@/hooks/queries/useAdminEmailAttempt';
import { useAdminEmailPreview } from '@/hooks/queries/useAdminEmailPreview';
import { useRedispatchEmail } from '@/hooks/mutations/useRedispatchEmail';

export default function AdminEmailDetailPage() {
  const { attemptId } = useParams();
  const id = Number(attemptId);
  const { data, isLoading } = useAdminEmailAttempt(id);
  const [previewOn, setPreviewOn] = useState(false);
  const [previewMode, setPreviewMode] = useState<'html' | 'text'>('html');
  const preview = useAdminEmailPreview(id, previewOn);
  const redispatch = useRedispatchEmail();

  if (isLoading || !data) return <div className="h-64 animate-pulse rounded-lg bg-slate-100" />;

  return (
    <div className="space-y-6">
      <Link to="/admin/email" className="text-sm text-indigo-600 hover:underline">← 목록으로</Link>
      <div className="rounded-lg bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold">발송 #{data.id}</h1>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-sm">{data.status}</span>
        </div>

        <h2 className="mt-6 mb-2 text-sm font-medium text-slate-500">메타</h2>
        <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
          <div><dt className="text-slate-500">수신자</dt><dd>{data.recipient}{data.recipientUserId && ` (userId: ${data.recipientUserId})`}</dd></div>
          <div><dt className="text-slate-500">타입</dt><dd>{data.emailType}</dd></div>
          <div className="sm:col-span-2"><dt className="text-slate-500">제목</dt><dd>{data.subject}</dd></div>
          <div className="sm:col-span-2"><dt className="text-slate-500">SES Message ID</dt><dd className="font-mono text-xs">{data.sesMessageId ?? '—'}</dd></div>
          <div><dt className="text-slate-500">발송 시각</dt><dd>{data.sentAt.replace('T',' ').slice(0,19)}</dd></div>
          <div><dt className="text-slate-500">상태 변경 시각</dt><dd>{data.updatedAt.replace('T',' ').slice(0,19)}</dd></div>
        </dl>

        <h2 className="mt-6 mb-2 text-sm font-medium text-slate-500">입력 데이터</h2>
        <pre className="rounded-md bg-slate-900 p-3 text-xs text-slate-100 overflow-auto">
          {JSON.stringify(JSON.parse(data.inputPayloadJson), null, 2)}
        </pre>

        <h2 className="mt-6 mb-2 text-sm font-medium text-slate-500">본문 미리보기</h2>
        {!previewOn ? (
          <button onClick={() => setPreviewOn(true)}
            className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white">
            본문 미리보기 불러오기
          </button>
        ) : preview.isLoading ? (
          <div className="h-32 animate-pulse rounded-md bg-slate-100" />
        ) : preview.data ? (
          <>
            <div className="mb-2 flex gap-2">
              <button className={`rounded px-2 py-1 text-xs ${previewMode==='html'?'bg-slate-900 text-white':'bg-slate-100'}`}
                onClick={() => setPreviewMode('html')}>HTML</button>
              <button className={`rounded px-2 py-1 text-xs ${previewMode==='text'?'bg-slate-900 text-white':'bg-slate-100'}`}
                onClick={() => setPreviewMode('text')}>Text</button>
            </div>
            {previewMode === 'html' ? (
              <iframe className="w-full h-96 rounded border border-slate-200"
                srcDoc={preview.data.htmlBody} sandbox="" title="email preview" />
            ) : (
              <pre className="rounded border border-slate-200 p-3 text-xs whitespace-pre-wrap">
                {preview.data.textBody}
              </pre>
            )}
          </>
        ) : null}

        {(data.status === 'FAILED' || data.status === 'BOUNCED') && (data.errorMessage || data.errorCode) && (
          <>
            <h2 className="mt-6 mb-2 text-sm font-medium text-slate-500">에러 정보</h2>
            <div className="rounded-md bg-red-50 p-3 text-sm text-red-800">
              <div><b>code:</b> {data.errorCode ?? '—'}</div>
              <div><b>message:</b> {data.errorMessage ?? '—'}</div>
              {data.bounceType && <div><b>bounce type:</b> {data.bounceType}</div>}
            </div>
          </>
        )}

        {data.status === 'FAILED' && (
          <div className="mt-6">
            <button onClick={() => redispatch.mutate(id)}
              disabled={redispatch.isPending}
              className="rounded-md bg-amber-600 px-4 py-2 text-sm text-white disabled:opacity-50">
              {redispatch.isPending ? '재발송 중...' : '재발송'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
