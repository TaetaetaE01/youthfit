import { describe, it, expect } from 'vitest';
import {
  parseRegionsParam,
  toRegionsParam,
  classifyRegionCodes,
  NATIONWIDE_TOKEN,
} from './regionFilter';

describe('parseRegionsParam', () => {
  it('null/빈 문자열은 빈 배열', () => {
    expect(parseRegionsParam(null)).toEqual([]);
    expect(parseRegionsParam('')).toEqual([]);
  });

  it('공백을 trim 하고 빈 항목을 제외한다', () => {
    expect(parseRegionsParam(' 11 , ,11680 ')).toEqual(['11', '11680']);
  });
});

describe('toRegionsParam', () => {
  it('배열을 CSV 로 합친다', () => {
    expect(toRegionsParam(['11', '11680'])).toBe('11,11680');
  });
});

describe('classifyRegionCodes', () => {
  it('2자리는 시·도, 5자리는 시·군·구로 분류', () => {
    const r = classifyRegionCodes(['11', '11680', '26']);
    expect(r.sidoCodes).toEqual(['11', '26']);
    expect(r.sigunguCodes).toEqual(['11680']);
    expect(r.isNationwideOnly).toBe(false);
  });

  it('NATIONWIDE 단독은 전국만 모드', () => {
    const r = classifyRegionCodes([NATIONWIDE_TOKEN]);
    expect(r.isNationwideOnly).toBe(true);
  });

  it("'전국' 한글 별칭도 NATIONWIDE 로 인식", () => {
    expect(classifyRegionCodes(['전국']).isNationwideOnly).toBe(true);
  });

  it('NATIONWIDE 가 다른 코드와 함께 오면 일반 필터', () => {
    const r = classifyRegionCodes(['NATIONWIDE', '11']);
    expect(r.isNationwideOnly).toBe(false);
    expect(r.sidoCodes).toEqual(['11']);
  });

  it('중복 코드 제거 + 공백 trim', () => {
    const r = classifyRegionCodes(['11', '11', ' 11680 ', '11680']);
    expect(r.sidoCodes).toEqual(['11']);
    expect(r.sigunguCodes).toEqual(['11680']);
  });

  it('알 수 없는 길이/문자는 무시', () => {
    const r = classifyRegionCodes(['1', '123', 'ABC', '11']);
    expect(r.sidoCodes).toEqual(['11']);
    expect(r.sigunguCodes).toEqual([]);
  });
});
