// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "링크 fetch + 머지" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';

export function selectUrls(p) {
  const candidates = [p?.aplyUrlAddr, p?.refUrlAddr1, p?.refUrlAddr2]
    .map(s => (typeof s === 'string' ? s.trim() : ''))
    .filter(Boolean);
  return candidates.slice(0, MAX_URLS);
}

export function mergeFetchResults(results) {
  if (!Array.isArray(results) || results.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  const ok = results.filter(r => r && !r.status);
  if (ok.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  let cleanedText = ok.map(r => r.cleanedText || '').join(TEXT_SEPARATOR);
  if (cleanedText.length > MAX_CLEANED_LEN) cleanedText = cleanedText.slice(0, MAX_CLEANED_LEN);
  const extraAttachments = ok.flatMap(r => Array.isArray(r.extraAttachments) ? r.extraAttachments : []);
  return { cleanedText, extraAttachments, status: null };
}
