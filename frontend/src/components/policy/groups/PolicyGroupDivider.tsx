interface Props {
  nextLabel: string;
}

export function PolicyGroupDivider({ nextLabel }: Props) {
  return (
    <div
      aria-hidden
      className="my-12 flex items-center gap-4 text-sm font-medium text-neutral-400"
    >
      <div className="h-px flex-1 bg-neutral-200" />
      <span>다음 · {nextLabel}</span>
      <div className="h-px flex-1 bg-neutral-200" />
    </div>
  );
}
