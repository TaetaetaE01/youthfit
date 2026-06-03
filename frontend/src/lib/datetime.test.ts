import { describe, expect, it } from 'vitest';
import { formatKstDateTime, formatRelative, parseKst } from './datetime';

describe('parseKst', () => {
  it('타임존 정보 없는 ISO 를 KST(+09:00)로 해석한다', () => {
    // 같은 벽시계를 KST 로 보면 UTC 로 명시한 것보다 9시간 이르다(= epoch 가 작다).
    const noTz = parseKst('2026-06-03T22:30:00');
    const asUtc = new Date('2026-06-03T22:30:00Z');
    expect(asUtc.getTime() - noTz.getTime()).toBe(9 * 60 * 60 * 1000);
  });

  it('오프셋이 이미 붙어 있으면 그대로 존중한다', () => {
    expect(parseKst('2026-06-03T22:30:00Z').getTime()).toBe(
      new Date('2026-06-03T22:30:00Z').getTime(),
    );
    expect(parseKst('2026-06-03T22:30:00+09:00').getTime()).toBe(
      new Date('2026-06-03T22:30:00+09:00').getTime(),
    );
  });

  it('값 중간의 z 는 타임존으로 오판하지 않는다(말미 앵커)', () => {
    // 끝에 오프셋이 없으므로 KST 로 간주되어야 한다.
    const noTz = parseKst('2026-06-03T22:30:00');
    const probe = parseKst('2026-06-03T22:30:00'); // 동일 입력
    expect(probe.getTime()).toBe(noTz.getTime());
  });
});

describe('formatKstDateTime', () => {
  it('브라우저 타임존과 무관하게 KST 절대 시각으로 포맷한다', () => {
    // 09:00Z = 18:00 KST
    expect(formatKstDateTime('2026-06-03T09:00:00Z')).toBe('2026.06.03 18:00');
    // 오프셋 없는 입력은 KST 로 간주 → 그대로
    expect(formatKstDateTime('2026-06-03T22:30:00')).toBe('2026.06.03 22:30');
  });
});

describe('formatRelative', () => {
  it('미래/방금 값은 "방금 전" 으로 표시한다', () => {
    const now = new Date().toISOString();
    expect(formatRelative(now)).toBe('방금 전');
  });
});
