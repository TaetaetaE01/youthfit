import type { EffectiveConfig } from '@/types/ragPreview';
import { CONFIG_FIELDS } from './configFields';

export function BaselineConfigPanel({ config }: { config?: EffectiveConfig }) {
  if (!config) return <div className="text-sm text-neutral-400">아직 비교 안 됨</div>;
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-sm">
      {CONFIG_FIELDS.map(({ key, label, hint }) => (
        <span key={key} className="contents">
          <dt className="flex flex-col">
            <span className="text-neutral-700">
              {label}{' '}
              <span title={hint} className="cursor-help text-neutral-400">ⓘ</span>
            </span>
            <span className="text-xs font-mono text-neutral-400">{key}</span>
          </dt>
          <dd className="self-center font-mono text-neutral-800">{String(config[key])}</dd>
        </span>
      ))}
    </dl>
  );
}
