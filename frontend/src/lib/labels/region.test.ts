import { describe, it, expect } from 'vitest';
import {
  REGION_SIDO_OPTIONS,
  SIDO_CODE_BY_ENUM,
  sidoCodeOf,
} from './region';

describe('SIDO_CODE_BY_ENUM', () => {
  it('17개 광역시도 모두를 행정코드와 매핑한다', () => {
    expect(Object.keys(SIDO_CODE_BY_ENUM)).toHaveLength(17);
    REGION_SIDO_OPTIONS.forEach((code) => {
      expect(SIDO_CODE_BY_ENUM[code]).toMatch(/^\d{2}$/);
    });
  });

  it('주요 시·도 행정코드가 올바르다', () => {
    expect(SIDO_CODE_BY_ENUM.SEOUL).toBe('11');
    expect(SIDO_CODE_BY_ENUM.BUSAN).toBe('26');
    expect(SIDO_CODE_BY_ENUM.GYEONGGI).toBe('41');
    expect(SIDO_CODE_BY_ENUM.JEJU).toBe('50');
  });

  it('sidoCodeOf 는 동일한 매핑을 함수로 노출한다', () => {
    expect(sidoCodeOf('SEOUL')).toBe('11');
  });
});
