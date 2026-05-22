interface SparklineProps {
  values: number[];
  label?: string;
}

export default function Sparkline({ values, label }: SparklineProps) {
  if (values.length === 0) return null;
  const max = Math.max(...values, 1);
  const points = values
    .map((v, i) => {
      const x = values.length === 1 ? 40 : (i / (values.length - 1)) * 80;
      const y = 24 - (v / max) * 24;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
  const a11yProps = label
    ? { role: 'img' as const }
    : { 'aria-hidden': true as const };
  return (
    <svg viewBox="0 0 80 24" className="h-6 w-20 text-slate-400" {...a11yProps}>
      {label && <title>{label}</title>}
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}
