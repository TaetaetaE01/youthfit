type Range = '24h' | '7d' | '30d';

interface Props {
  value: Range;
  onChange: (r: Range) => void;
}

const OPTIONS: { value: Range; label: string }[] = [
  { value: '24h', label: '24시간' },
  { value: '7d', label: '7일' },
  { value: '30d', label: '30일' },
];

export function LlmCostRangeToggle({ value, onChange }: Props) {
  return (
    <div className="inline-flex rounded-md border border-slate-200">
      {OPTIONS.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={`px-3 py-1.5 text-sm ${
            value === o.value
              ? 'bg-indigo-600 text-white'
              : 'bg-white text-slate-700 hover:bg-slate-50'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
