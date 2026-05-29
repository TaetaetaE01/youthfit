import type { PolicyProcessingStats } from '@/types/adminPolicyProcessing';

interface Props {
  stats: PolicyProcessingStats;
}

type Tone = 'ok' | 'warn' | 'fail' | 'neutral';

export function PolicyProcessingKpiCards({ stats }: Props) {
  const completePercent =
    stats.totalCount > 0
      ? Math.round((stats.completeCount / stats.totalCount) * 100)
      : 0;

  return (
    <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
      <KpiCard
        label="완전"
        value={stats.completeCount}
        suffix={`/ ${stats.totalCount} (${completePercent}%)`}
        tone="ok"
      />
      <KpiCard
        label="부분"
        value={stats.partialCount}
        suffix={`/ ${stats.totalCount}`}
        tone="warn"
      />
      <KpiCard
        label="미흡"
        value={stats.incompleteCount}
        suffix={`/ ${stats.totalCount}`}
        tone="fail"
      />
      <KpiCard
        label="최근 24h 적재"
        value={stats.recent24hCount}
        suffix="건"
        tone="neutral"
      />
    </div>
  );
}

interface KpiCardProps {
  label: string;
  value: number;
  suffix: string;
  tone: Tone;
}

const VALUE_COLOR: Record<Tone, string> = {
  ok: 'text-green-600',
  warn: 'text-amber-600',
  fail: 'text-red-600',
  neutral: 'text-neutral-900',
};

function KpiCard({ label, value, suffix, tone }: KpiCardProps) {
  return (
    <div className="rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm">
      <div className="text-xs uppercase tracking-wider text-indigo-600">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${VALUE_COLOR[tone]}`}>
        {value}{' '}
        <span className="text-sm font-normal text-neutral-500">{suffix}</span>
      </div>
    </div>
  );
}
