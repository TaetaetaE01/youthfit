import { cn } from '@/lib/cn';

export function RankDeltaBadge({ delta }: { delta: string }) {
  if (delta === '0') return null;
  const isNew = delta === 'NEW';
  const isDropped = delta === 'DROPPED';
  const isUp = delta.startsWith('-');   // -2 = 순위 상승 (작을수록 위)
  const isDown = delta.startsWith('+');

  const color = cn(
    isNew && 'bg-emerald-100 text-emerald-700',
    isDropped && 'bg-red-100 text-red-700',
    isUp && 'bg-blue-100 text-blue-700',
    isDown && 'bg-amber-100 text-amber-700',
  );

  const label = isUp ? `↑${delta.slice(1)}` : isDown ? `↓${delta.slice(1)}` : delta;
  return (
    <span className={cn('rounded px-1.5 py-0.5 text-xs font-medium', color)}>
      {label}
    </span>
  );
}
