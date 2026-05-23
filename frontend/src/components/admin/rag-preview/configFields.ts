export interface ConfigFieldMeta {
  key: 'hybridEnabled' | 'topNPerSearch' | 'rrfK' | 'trigramThreshold' | 'keywordBoostEnabled' | 'maxKeywords';
  label: string;
  hint: string;
}

export const CONFIG_FIELDS: ConfigFieldMeta[] = [
  { key: 'hybridEnabled',       label: '하이브리드 검색',   hint: '벡터+키워드 검색 결과를 RRF 로 융합합니다. 끄면 벡터 검색만 사용.' },
  { key: 'topNPerSearch',       label: '검색기별 top-K',    hint: '각 검색기(벡터·키워드)에서 가져올 후보 수. 1~100.' },
  { key: 'rrfK',                label: 'RRF 상수 (k)',     hint: 'Reciprocal Rank Fusion 상수. 작을수록 상위 결과의 가중치가 커집니다. 1~500.' },
  { key: 'trigramThreshold',    label: '키워드 유사도 임계',  hint: 'PostgreSQL pg_trgm 의 similarity 임계치. 높이면 더 정확한 매칭만. 0.0~1.0.' },
  { key: 'keywordBoostEnabled', label: '키워드 부스트',     hint: '쿼리에서 추출한 키워드를 검색 가중치에 반영합니다.' },
  { key: 'maxKeywords',         label: '최대 키워드 수',    hint: '부스트에 사용할 추출 키워드 상한. 0~20.' },
];
