import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { EffectiveConfig, HybridOverride } from '@/types/ragPreview';

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

  return (
    <form className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-sm">
      <label className="text-neutral-500">hybridEnabled</label>
      <input type="checkbox" {...register('hybridEnabled')} />

      <label className="text-neutral-500">topNPerSearch</label>
      <input type="number" {...register('topNPerSearch')} className="w-20 rounded border px-1" />
      {errors.topNPerSearch && <span className="col-span-2 text-xs text-red-600">1~100</span>}

      <label className="text-neutral-500">rrfK</label>
      <input type="number" {...register('rrfK')} className="w-20 rounded border px-1" />
      {errors.rrfK && <span className="col-span-2 text-xs text-red-600">1~500</span>}

      <label className="text-neutral-500">trigramThreshold</label>
      <input type="number" step="0.01" {...register('trigramThreshold')}
             className="w-24 rounded border px-1" />
      {errors.trigramThreshold && <span className="col-span-2 text-xs text-red-600">0.0~1.0</span>}

      <label className="text-neutral-500">keywordBoostEnabled</label>
      <input type="checkbox" {...register('keywordBoostEnabled')} />

      <label className="text-neutral-500">maxKeywords</label>
      <input type="number" {...register('maxKeywords')} className="w-20 rounded border px-1" />
      {errors.maxKeywords && <span className="col-span-2 text-xs text-red-600">0~20</span>}
    </form>
  );
}
