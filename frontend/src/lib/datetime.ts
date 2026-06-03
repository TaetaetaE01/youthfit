/**
 * 백엔드의 `LocalDateTime` 컬럼은 KST 시각을 타임존 정보 없이 ISO 로 직렬화한다
 * (예: `2026-06-03T22:30:00`). 타임존 정보(`Z` 또는 `±hh:mm`)가 없으면 KST(+09:00)로
 * 간주해, 브라우저 타임존과 무관하게 항상 한국시로 해석한다. 오프셋이 이미 붙어 오면 존중한다.
 */
export function parseKst(iso: string): Date {
  const hasTz = /(?:[zZ]|[+-]\d{2}:?\d{2})$/.test(iso);
  return new Date(hasTz ? iso : `${iso}+09:00`);
}

/** KST 기준 절대 날짜+시각을 `2026.06.03 22:30` 형태로 표시한다. */
export function formatKstDateTime(iso: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(parseKst(iso));
  const p = Object.fromEntries(parts.map((x) => [x.type, x.value]));
  return `${p.year}.${p.month}.${p.day} ${p.hour}:${p.minute}`;
}

/** 현재 시각 기준 상대 시간(예: `3시간 전`). hover 툴팁 등 보조 표시용. */
export function formatRelative(iso: string): string {
  const diffMs = Date.now() - parseKst(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return '방금 전';
  if (min < 60) return `${min}분 전`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}
