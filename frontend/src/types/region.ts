export interface RegionSido {
  code: string;       // 2자리 행정코드
  name: string;       // 시·도명
}

export interface RegionSigungu {
  code: string;       // 5자리 행정코드
  sidoCode: string;
  sidoName: string;
  name: string;       // 시·군·구명
}

export interface RegionListResponse {
  sidos: RegionSido[];
  sigungus: RegionSigungu[];
}
