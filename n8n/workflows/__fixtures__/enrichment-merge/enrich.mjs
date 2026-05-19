// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "링크 fetch + 머지" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';

export function selectUrls(p) {
  return [];
}

export function mergeFetchResults(results) {
  return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
}
