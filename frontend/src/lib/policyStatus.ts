import type { PolicyStatus } from '@/types/policy';

function toStartOfDay(date: Date): number {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy.getTime();
}

function parseDate(dateStr: string | null | undefined): number | null {
  if (!dateStr) return null;
  const parsed = new Date(dateStr);
  if (Number.isNaN(parsed.getTime())) return null;
  return toStartOfDay(parsed);
}

type PolicyLike = {
  applyStart: string | null;
  applyEnd: string | null;
  referenceYear?: number | null;
  status: PolicyStatus;
};

export function getEffectiveStatus(
  policy: PolicyLike,
  now: Date = new Date(),
): PolicyStatus {
  const today = toStartOfDay(now);
  const start = parseDate(policy.applyStart);
  const end = parseDate(policy.applyEnd);

  if (end !== null && today > end) return 'CLOSED';
  if (start !== null && today < start) return 'UPCOMING';
  if (start !== null && end !== null) return 'OPEN';

  const currentYear = now.getFullYear();
  if (policy.referenceYear != null && policy.referenceYear < currentYear) {
    return 'CLOSED';
  }
  if (policy.referenceYear === currentYear) {
    return 'OPEN';
  }

  return policy.status;
}

export function isExpired(
  policy: PolicyLike,
  now: Date = new Date(),
): boolean {
  return getEffectiveStatus(policy, now) === 'CLOSED';
}

export function formatPolicyPeriod(
  policy: PolicyLike,
  now: Date = new Date(),
): string {
  const { applyStart, applyEnd } = policy;
  const fmt = (d: string) => d.slice(0, 10).replace(/-/g, '.');
  if (!applyStart && !applyEnd) {
    const status = getEffectiveStatus(policy, now);
    return status === 'CLOSED' ? '마감' : '상시';
  }
  if (!applyStart) return `~${fmt(applyEnd!)}`;
  if (!applyEnd) return `${fmt(applyStart)}~`;
  return `${fmt(applyStart)}~${fmt(applyEnd)}`;
}
