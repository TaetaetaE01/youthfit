/**
 * URL ?regions= CSV 와 string[] 간 변환 + 시·도/시·군·구 분류 헬퍼.
 *
 * NATIONWIDE 토큰은 "전국만 보기" 모드를 의미한다.
 */
export const NATIONWIDE_TOKEN = 'NATIONWIDE';

export interface RegionSelection {
  isNationwideOnly: boolean;
  sidoCodes: string[];     // 2자리
  sigunguCodes: string[];  // 5자리
}

export function parseRegionsParam(csv: string | null): string[] {
  if (!csv) return [];
  return csv
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export function toRegionsParam(codes: string[]): string {
  return codes.join(',');
}

export function classifyRegionCodes(codes: string[]): RegionSelection {
  const seen = new Set<string>();
  let hasNationwide = false;
  const sidos: string[] = [];
  const sigungus: string[] = [];

  for (const raw of codes) {
    const code = raw.trim();
    if (!code || seen.has(code)) continue;
    seen.add(code);

    if (code === NATIONWIDE_TOKEN || code === '전국') {
      hasNationwide = true;
      continue;
    }
    if (!/^\d+$/.test(code)) continue;
    if (code.length === 2) sidos.push(code);
    else if (code.length === 5) sigungus.push(code);
  }

  return {
    isNationwideOnly: hasNationwide && sidos.length === 0 && sigungus.length === 0,
    sidoCodes: sidos,
    sigunguCodes: sigungus,
  };
}
