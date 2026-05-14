import { describe, it, expect } from 'vitest';
import { pickWithFallback, isMeaningful } from '../policyEnrichment';

describe('pickWithFallback', () => {
  it('원본이 있으면 fromAi=false 로 원본을 반환한다', () => {
    expect(pickWithFallback('원본값', 'AI값')).toEqual({ value: '원본값', fromAi: false });
  });

  it('원본이 null 이고 AI 값이 있으면 fromAi=true 로 AI 값을 반환한다', () => {
    expect(pickWithFallback(null, 'AI값')).toEqual({ value: 'AI값', fromAi: true });
  });

  it('둘 다 비어있으면 null 을 반환한다', () => {
    expect(pickWithFallback(null, null)).toBeNull();
    expect(pickWithFallback('', '')).toBeNull();
    expect(pickWithFallback(undefined, undefined)).toBeNull();
  });

  it('문자열 "null" / "NULL" / 공백은 빈 값으로 취급한다', () => {
    expect(pickWithFallback('null', 'AI값')).toEqual({ value: 'AI값', fromAi: true });
    expect(pickWithFallback('NULL', 'AI값')).toEqual({ value: 'AI값', fromAi: true });
    expect(pickWithFallback('  null  ', 'AI값')).toEqual({ value: 'AI값', fromAi: true });
    expect(pickWithFallback('   ', 'AI값')).toEqual({ value: 'AI값', fromAi: true });
    expect(pickWithFallback('원본', 'null')).toEqual({ value: '원본', fromAi: false });
    expect(pickWithFallback('null', 'null')).toBeNull();
  });

  it('원본에 공백이 있으면 trim 후 반환한다', () => {
    expect(pickWithFallback('  원본  ', null)).toEqual({ value: '원본', fromAi: false });
  });
});

describe('isMeaningful', () => {
  it('의미있는 값이면 true', () => {
    expect(isMeaningful('내용')).toBe(true);
  });
  it('null/빈/"null" 문자열은 false', () => {
    expect(isMeaningful(null)).toBe(false);
    expect(isMeaningful('')).toBe(false);
    expect(isMeaningful('null')).toBe(false);
    expect(isMeaningful('NULL')).toBe(false);
    expect(isMeaningful('  ')).toBe(false);
  });
});
