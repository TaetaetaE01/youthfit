import { ExternalLink, Bot } from 'lucide-react';
import type { PolicyEnrichment, PolicyEnrichmentSections } from '@/types/policy';

type Props = { enrichment: PolicyEnrichment | null };

const SECTION_LABELS: Array<[keyof PolicyEnrichmentSections, string]> = [
  ['supportTarget', '지원대상'],
  ['supportContent', '지원내용'],
  ['applyMethod', '신청방법'],
  ['requiredDocuments', '제출서류'],
  ['deadlineNote', '마감안내'],
];

export function PolicyEnrichmentSection({ enrichment }: Props) {
  if (!enrichment) return null;

  const visibleSections = SECTION_LABELS.filter(([key]) => {
    const v = enrichment.sections?.[key];
    return v != null && v.trim() !== '';
  });

  if (visibleSections.length === 0 && enrichment.extraAttachments.length === 0) {
    return null;
  }

  return (
    <section
      aria-label="정책 안내 페이지에서 자동 수집한 정보"
      className="mb-6 rounded-2xl border border-violet-200 bg-violet-50/40 p-6"
    >
      {/* Header */}
      <header className="mb-4 flex flex-wrap items-center gap-3">
        <span
          className="inline-flex items-center gap-1.5 rounded-full bg-violet-100 px-2.5 py-1 text-xs font-semibold text-violet-700"
          title="AI 가 외부 페이지에서 자동으로 정리한 정보입니다"
        >
          <Bot className="h-3.5 w-3.5" />
          AI 자동 수집
        </span>
        <a
          href={enrichment.sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 text-xs text-violet-600 underline-offset-2 hover:underline"
        >
          원문 보기
          <ExternalLink className="h-3 w-3" />
        </a>
        <time
          dateTime={enrichment.fetchedAt}
          className="ml-auto text-xs text-neutral-400"
        >
          수집: {new Date(enrichment.fetchedAt).toLocaleString('ko-KR')}
        </time>
      </header>

      {/* Structured sections */}
      {visibleSections.length > 0 && (
        <dl className="space-y-3">
          {visibleSections.map(([key, label]) => (
            <div key={key} className="flex flex-col gap-1">
              <dt className="text-xs font-semibold text-violet-700">{label}</dt>
              <dd className="whitespace-pre-wrap text-sm text-neutral-700">
                {enrichment.sections?.[key]}
              </dd>
            </div>
          ))}
        </dl>
      )}

      {/* Extra attachments */}
      {enrichment.extraAttachments.length > 0 && (
        <div className={visibleSections.length > 0 ? 'mt-4 border-t border-violet-200 pt-4' : ''}>
          <h4 className="mb-2 text-xs font-semibold text-violet-700">첨부 파일 (자동 발견)</h4>
          <ul className="space-y-1.5">
            {enrichment.extraAttachments.map((a) => (
              <li key={a.url}>
                <a
                  href={a.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 text-sm text-violet-600 underline-offset-2 hover:underline"
                >
                  <ExternalLink className="h-3.5 w-3.5 shrink-0" />
                  {a.name}
                </a>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
