import type { EffectiveConfig } from '@/types/ragPreview';

export function BaselineConfigPanel({ config }: { config?: EffectiveConfig }) {
  if (!config) return <div className="text-sm text-neutral-400">아직 비교 안 됨</div>;
  const rows: [string, string | number | boolean][] = [
    ['hybridEnabled', config.hybridEnabled],
    ['topNPerSearch', config.topNPerSearch],
    ['rrfK', config.rrfK],
    ['trigramThreshold', config.trigramThreshold],
    ['keywordBoostEnabled', config.keywordBoostEnabled],
    ['maxKeywords', config.maxKeywords],
  ];
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
      {rows.map(([k, v]) => (
        <span key={k} className="contents">
          <dt className="text-neutral-500">{k}</dt>
          <dd className="font-mono text-neutral-800">{String(v)}</dd>
        </span>
      ))}
    </dl>
  );
}
