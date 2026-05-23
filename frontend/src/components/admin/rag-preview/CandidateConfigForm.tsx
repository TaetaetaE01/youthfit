import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { EffectiveConfig, HybridOverride } from '@/types/ragPreview';
import { CONFIG_FIELDS } from './configFields';

const schema = z.object({
  hybridEnabled: z.boolean(),
  topNPerSearch: z.coerce.number().int().min(1).max(100),
  rrfK: z.coerce.number().int().min(1).max(500),
  trigramThreshold: z.coerce.number().min(0).max(1),
  keywordBoostEnabled: z.boolean(),
  maxKeywords: z.coerce.number().int().min(0).max(20),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  baseline?: EffectiveConfig;
  onChange: (overrides: HybridOverride) => void;
}

const ERROR_HINTS: Record<keyof FormValues, string | null> = {
  hybridEnabled: null,
  topNPerSearch: '1~100',
  rrfK: '1~500',
  trigramThreshold: '0.0~1.0',
  keywordBoostEnabled: null,
  maxKeywords: '0~20',
};

function formatBaseline(key: keyof FormValues, v: boolean | number): string {
  if (key === 'hybridEnabled' || key === 'keywordBoostEnabled') {
    return v ? 'on' : 'off';
  }
  return String(v);
}

export function CandidateConfigForm({ baseline, onChange }: Props) {
  const { register, watch, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: baseline ?? undefined,
  });

  useEffect(() => {
    if (baseline) reset(baseline);
  }, [baseline, reset]);

  const values = watch();
  useEffect(() => {
    if (!baseline) return;
    // Zod coerce.number() parses at validation time; watch() still returns raw strings.
    // Coerce number fields manually so the diff carries proper types.
    const coerced: FormValues = {
      hybridEnabled: Boolean(values.hybridEnabled),
      topNPerSearch: Number(values.topNPerSearch),
      rrfK: Number(values.rrfK),
      trigramThreshold: Number(values.trigramThreshold),
      keywordBoostEnabled: Boolean(values.keywordBoostEnabled),
      maxKeywords: Number(values.maxKeywords),
    };
    // baseline 과 다른 필드만 overrides 로 전달
    const diff: HybridOverride = {};
    (Object.keys(coerced) as (keyof FormValues)[]).forEach((k) => {
      if (coerced[k] !== baseline[k]) (diff as any)[k] = coerced[k];
    });
    onChange(diff);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(values), baseline]);

  if (!baseline) return <div className="text-sm text-neutral-400">baseline 로딩 대기</div>;

  // 현재 coerced 값을 diff 표시용으로 계산
  const coerced: FormValues = {
    hybridEnabled: Boolean(values.hybridEnabled),
    topNPerSearch: Number(values.topNPerSearch),
    rrfK: Number(values.rrfK),
    trigramThreshold: Number(values.trigramThreshold),
    keywordBoostEnabled: Boolean(values.keywordBoostEnabled),
    maxKeywords: Number(values.maxKeywords),
  };

  return (
    <form className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-3 text-sm">
      {CONFIG_FIELDS.map(({ key, label, hint }) => {
        const isBoolean = key === 'hybridEnabled' || key === 'keywordBoostEnabled';
        const isFloat = key === 'trigramThreshold';
        const errorHint = ERROR_HINTS[key];
        const hasError = errors[key];
        const differs = !Number.isNaN(coerced[key] as number)
          && coerced[key] !== baseline[key];

        return (
          <span key={key} className="contents">
            <label className="flex flex-col" htmlFor={`cand-${key}`}>
              <span className="text-neutral-700">
                {label}{' '}
                <span title={hint} className="cursor-help text-neutral-400">ⓘ</span>
              </span>
              <span className="text-xs font-mono text-neutral-400">{key}</span>
            </label>
            <div className="flex items-center gap-2">
              {isBoolean ? (
                <input
                  id={`cand-${key}`}
                  type="checkbox"
                  {...register(key)}
                />
              ) : (
                <input
                  id={`cand-${key}`}
                  type="number"
                  step={isFloat ? '0.01' : undefined}
                  {...register(key)}
                  className={isFloat ? 'w-24 rounded border px-1' : 'w-20 rounded border px-1'}
                />
              )}
              {differs && (
                <span className="text-xs text-amber-600 font-mono">
                  ← baseline: {formatBaseline(key, baseline[key])}
                </span>
              )}
              {hasError && errorHint && (
                <span className="text-xs text-red-600">{errorHint}</span>
              )}
            </div>
          </span>
        );
      })}
    </form>
  );
}
