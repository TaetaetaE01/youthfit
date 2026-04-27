interface Props {
  oneLineSummary: string;
}

export function OneLineSummaryCard({ oneLineSummary }: Props) {
  return (
    <section className="mb-6 rounded-2xl border border-indigo-100 bg-indigo-50/50 p-6">
      <span className="mb-3 inline-block rounded-full bg-brand-100 px-3 py-1 text-xs font-bold uppercase tracking-wide text-indigo-600">
        이 정책 한눈에
      </span>
      <p className="text-base leading-relaxed text-neutral-800">{oneLineSummary}</p>
      <p className="mt-3 text-xs text-neutral-500">
        AI가 정리한 해석이에요. 정확한 조건은 아래 원문과 공식 공고에서 확인해주세요.
      </p>
    </section>
  );
}
