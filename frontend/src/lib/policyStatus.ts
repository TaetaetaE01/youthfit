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

export function getEffectiveStatus(
  policy: { applyStart: string | null; applyEnd: string | null; status: PolicyStatus },
  now: Date = new Date(),
): PolicyStatus {
  const today = toStartOfDay(now);
  const start = parseDate(policy.applyStart);
  const end = parseDate(policy.applyEnd);

  if (end !== null && today > end) return 'CLOSED';
  if (start !== null && today < start) return 'UPCOMING';
  if (start !== null && end !== null) return 'OPEN';

  return policy.status;
}

export function isExpired(
  policy: { applyStart: string | null; applyEnd: string | null; status: PolicyStatus },
  now: Date = new Date(),
): boolean {
  return getEffectiveStatus(policy, now) === 'CLOSED';
}
