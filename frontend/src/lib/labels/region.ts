export type RegionSidoCode =
  | 'SEOUL'
  | 'BUSAN'
  | 'DAEGU'
  | 'INCHEON'
  | 'GWANGJU'
  | 'DAEJEON'
  | 'ULSAN'
  | 'SEJONG'
  | 'GYEONGGI'
  | 'GANGWON'
  | 'CHUNGBUK'
  | 'CHUNGNAM'
  | 'JEONBUK'
  | 'JEONNAM'
  | 'GYEONGBUK'
  | 'GYEONGNAM'
  | 'JEJU';

export const REGION_LABELS: Record<RegionSidoCode, string> = {
  SEOUL: '서울',
  BUSAN: '부산',
  DAEGU: '대구',
  INCHEON: '인천',
  GWANGJU: '광주',
  DAEJEON: '대전',
  ULSAN: '울산',
  SEJONG: '세종',
  GYEONGGI: '경기',
  GANGWON: '강원',
  CHUNGBUK: '충북',
  CHUNGNAM: '충남',
  JEONBUK: '전북',
  JEONNAM: '전남',
  GYEONGBUK: '경북',
  GYEONGNAM: '경남',
  JEJU: '제주',
};

export const REGION_SIDO_OPTIONS: RegionSidoCode[] = [
  'SEOUL',
  'BUSAN',
  'DAEGU',
  'INCHEON',
  'GWANGJU',
  'DAEJEON',
  'ULSAN',
  'SEJONG',
  'GYEONGGI',
  'GANGWON',
  'CHUNGBUK',
  'CHUNGNAM',
  'JEONBUK',
  'JEONNAM',
  'GYEONGBUK',
  'GYEONGNAM',
  'JEJU',
];

/**
 * RegionSidoCode (enum) ↔ 행정구역 시·도 코드(2자리).
 * 행정안전부 행정코드 기준.
 */
export const SIDO_CODE_BY_ENUM: Record<RegionSidoCode, string> = {
  SEOUL: '11',
  BUSAN: '26',
  DAEGU: '27',
  INCHEON: '28',
  GWANGJU: '29',
  DAEJEON: '30',
  ULSAN: '31',
  SEJONG: '36',
  GYEONGGI: '41',
  GANGWON: '42',
  CHUNGBUK: '43',
  CHUNGNAM: '44',
  JEONBUK: '45',
  JEONNAM: '46',
  GYEONGBUK: '47',
  GYEONGNAM: '48',
  JEJU: '50',
};

export function sidoCodeOf(enumCode: RegionSidoCode): string {
  return SIDO_CODE_BY_ENUM[enumCode];
}
