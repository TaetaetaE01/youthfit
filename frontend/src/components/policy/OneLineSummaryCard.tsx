import { Sparkles } from 'lucide-react';

interface Props {
  oneLineSummary: string;
}

export function OneLineSummaryCard({ oneLineSummary }: Props) {
  return (
    <section className="mb-6 rounded-2xl border border-brand-100 bg-gradient-to-br from-brand-50 to-white p-6">
      <div className="flex items-start gap-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-800 text-white">
          <Sparkles className="h-5 w-5" />
        </div>
        <div>
          <div className="mb-1 text-sm font-semibold text-brand-800">AI 한 줄 요약</div>
          <p className="text-[15px] leading-relaxed text-neutral-800">{oneLineSummary}</p>
          <p className="mt-2 text-xs text-neutral-500">
            AI가 정리한 해석이에요. 정확한 조건은 아래 원문과 공식 공고에서 확인해주세요.
          </p>
        </div>
      </div>
    </section>
  );
}
