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
  ok: 'text-green-500',
  warn: 'text-amber-500',
  fail: 'text-red-500',
  neutral: 'text-white',
};

function KpiCard({ label, value, suffix, tone }: KpiCardProps) {
  return (
    <div className="rounded border border-slate-700 bg-slate-900 p-3">
      <div className="text-xs uppercase tracking-wider text-blue-300">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${VALUE_COLOR[tone]}`}>
        {value}{' '}
        <span className="text-sm font-normal text-slate-500">{suffix}</span>
      </div>
    </div>
  );
}
