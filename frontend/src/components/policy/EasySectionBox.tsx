interface Props {
  title: string;
  items: string[];
}

export function EasySectionBox({ title, items }: Props) {
  return (
    <div className="rounded-t-2xl border border-b-0 border-indigo-100 bg-indigo-50/40 p-5">
      <span className="mb-2 inline-block rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-indigo-600">
        쉬운 해석
      </span>
      <h3 className="mb-3 text-base font-semibold text-neutral-900">{title}</h3>
      <ul className="space-y-1.5 text-sm text-neutral-800">
        {items.map((item, i) => (
          <li key={i} className="flex gap-2">
            <span className="text-indigo-500">•</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
