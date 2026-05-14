export interface EnrichedValue {
  value: string;
  fromAi: boolean;
}

function normalize(input: string | null | undefined): string | null {
  if (input == null) return null;
  const trimmed = input.trim();
  if (trimmed === '') return null;
  if (trimmed.toLowerCase() === 'null') return null;
  return trimmed;
}

export function pickWithFallback(
  original: string | null | undefined,
  enriched: string | null | undefined,
): EnrichedValue | null {
  const o = normalize(original);
  if (o) return { value: o, fromAi: false };
  const e = normalize(enriched);
  if (e) return { value: e, fromAi: true };
  return null;
}

export function isMeaningful(input: string | null | undefined): boolean {
  return normalize(input) !== null;
}
