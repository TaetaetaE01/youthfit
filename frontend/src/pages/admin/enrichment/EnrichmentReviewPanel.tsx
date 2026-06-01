import { useEffect, useRef, useState } from 'react';
import { usePolicyDetail, useUpdateReferenceSites, useCreateJob } from '@/hooks/useAdminEnrichment';
import { EnrichmentReferenceSiteEditor } from './EnrichmentReferenceSiteEditor';
import { EnrichmentJobBadge } from '@/components/admin/enrichment/EnrichmentJobBadge';
import { AttachmentStatusBadge } from '@/components/admin/enrichment/AttachmentStatusBadge';
import type { ReferenceSite } from '@/types/adminEnrichment';

interface Props {
  policyId: number;
}

/**
 * 관리자 enrichment 검토 콘솔 우측 패널.
 *
 * 책임:
 *  - 정책 enrichment 현황 (status / confidence / fetchedAt / extractor) 표시
 *  - reference URL 편집 (EnrichmentReferenceSiteEditor 위임)
 *  - 최근 잡 이력 표시 (최대 5)
 *  - 재크롤 잡 트리거 (referenceSites 비어있거나 활성 잡 존재 시 비활성)
 *
 * 폴링은 usePolicyDetail 훅이 활성 잡 상태에 따라 자동 처리한다.
 */
export function EnrichmentReviewPanel({ policyId }: Props) {
  const { data, isLoading } = usePolicyDetail(policyId);
  const updateSites = useUpdateReferenceSites(policyId);
  const createJob = useCreateJob(policyId);
  const [confirmOpen, setConfirmOpen] = useState(false);

  // 최신 잡 상태가 SUCCESS / FAILED 로 전이될 때 사용자에게 알린다.
  // 표준 토스트 도입 전까지 다른 어드민 탭(IngestionHealthTab)과 동일하게 alert 를 stopgap 으로 사용한다.
  const lastStatusRef = useRef<string | null>(null);
  const latestJobForEffect = data?.recentJobs[0];
  const curStatus = latestJobForEffect?.status ?? null;
  useEffect(() => {
    if (
      lastStatusRef.current &&
      (curStatus === 'SUCCESS' || curStatus === 'FAILED') &&
      curStatus !== lastStatusRef.current
    ) {
      const title = data?.title ?? '';
      if (curStatus === 'SUCCESS') {
        alert(`재크롤 완료: ${title}`);
      } else {
        const err = latestJobForEffect?.errorMessage ?? '';
        alert(`재크롤 실패: ${title}${err ? ` — ${err}` : ''}`);
      }
    }
    lastStatusRef.current = curStatus;
  }, [curStatus, data?.title, latestJobForEffect?.errorMessage]);

  if (isLoading || !data) {
    return <div className="p-4 text-sm">로딩 중...</div>;
  }

  const latestJob = data.recentJobs[0];
  const hasActiveJob = !!latestJob && (latestJob.status === 'PENDING' || latestJob.status === 'RUNNING');
  const disabled = data.referenceSites.length === 0 || hasActiveJob;
  const attachments = data.attachments ?? [];

  return (
    <aside className="p-4 space-y-4 border-l w-[420px]">
      <h2 className="font-semibold">
        정책 #{data.policyId} — {data.title}
      </h2>

      <section>
        <h3 className="text-sm font-semibold mb-1">Enrichment 현황</h3>
        {data.enrichment ? (
          <ul className="text-xs space-y-0.5">
            <li>status: {data.enrichment.status ?? '-'}</li>
            <li>confidence: {data.enrichment.confidence ?? '-'}</li>
            <li>fetched: {data.enrichment.fetchedAt ?? '-'}</li>
            <li>extractor: {data.enrichment.extractor ?? '-'}</li>
          </ul>
        ) : (
          <p className="text-xs text-gray-500">아직 enrich 되지 않음</p>
        )}
      </section>

      <section>
        <h3 className="text-sm font-semibold mb-1">Reference URLs</h3>
        <EnrichmentReferenceSiteEditor
          initialSites={data.referenceSites}
          onSave={(sites: ReferenceSite[]) => updateSites.mutate(sites)}
        />
      </section>

      <section>
        <h3 className="text-sm font-semibold mb-1">첨부파일 ({attachments.length})</h3>
        <ul className="text-xs space-y-1">
          {attachments.map((a) => (
            <li key={a.id} className="space-y-0.5">
              <div className="flex items-center gap-2">
                <AttachmentStatusBadge status={a.status} />
                <span className="truncate" title={a.name}>
                  {a.name}
                </span>
              </div>
              {a.status === 'FAILED' && a.error && (
                <p className="text-rose-600 break-all">
                  실패(재시도 {a.retryCount}): {a.error}
                </p>
              )}
              {a.status === 'SKIPPED' && a.skipReason && (
                <p className="text-amber-600">건너뜀: {a.skipReason}</p>
              )}
            </li>
          ))}
          {attachments.length === 0 && <li className="text-gray-500">첨부 없음</li>}
        </ul>
      </section>

      <section>
        <h3 className="text-sm font-semibold mb-1">잡 이력 (최근 5)</h3>
        <ul className="text-xs space-y-0.5">
          {data.recentJobs.slice(0, 5).map((j) => (
            <li key={j.id} className="flex items-center gap-2">
              <EnrichmentJobBadge status={j.status} />
              <span>
                #{j.id} · {j.requestedAt}
              </span>
              {j.errorMessage && <span className="text-red-600">— {j.errorMessage}</span>}
            </li>
          ))}
          {data.recentJobs.length === 0 && <li className="text-gray-500">잡 없음</li>}
        </ul>
      </section>

      <button
        type="button"
        disabled={disabled || createJob.isPending}
        onClick={() => setConfirmOpen(true)}
        className="w-full py-2 bg-blue-600 text-white text-sm disabled:bg-gray-300"
      >
        재크롤 실행
      </button>

      {confirmOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="enrichment-rerun-confirm-title"
          className="fixed inset-0 bg-black/30 flex items-center justify-center"
        >
          <div className="bg-white p-4 space-y-2 w-[360px]">
            <p id="enrichment-rerun-confirm-title" className="font-semibold">"{data.title}" 재크롤을 실행할까요?</p>
            <ul className="text-xs">
              {data.referenceSites.map((s) => (
                <li key={s.url}>· {s.url}</li>
              ))}
            </ul>
            <div className="flex justify-end gap-2 text-sm">
              <button type="button" onClick={() => setConfirmOpen(false)}>
                취소
              </button>
              <button
                type="button"
                onClick={() => {
                  setConfirmOpen(false);
                  createJob.mutate(undefined);
                }}
                className="bg-blue-600 text-white px-3 py-1"
              >
                실행
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}
