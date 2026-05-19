// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "링크 fetch + 머지" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';

function normalizeUrlKey(u) {
  return u.toLowerCase().replace(/\/+$/, '');
}

export function selectUrls(policy) {
  const candidates = [policy?.aplyUrlAddr, policy?.refUrlAddr1, policy?.refUrlAddr2]
    .map(s => (typeof s === 'string' ? s.trim() : ''))
    .filter(Boolean);
  const seen = new Set();
  const out = [];
  for (const u of candidates) {
    const key = normalizeUrlKey(u);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(u);
    if (out.length >= MAX_URLS) break;
  }
  return out;
}

export function mergeFetchResults(results) {
  if (!Array.isArray(results) || results.length === 0) {
    return { cleanedText: '', extraAttachments: [], status: 'FETCH_FAILED' };
  }
  const ok = results.filter(r => r && r.status == null);
  if (ok.length === 0) {
    const allTooShort = results.length > 0 && results.every(r => r && r.status === 'TOO_SHORT');
    return {
      cleanedText: '',
      extraAttachments: [],
      status: allTooShort ? 'TOO_SHORT' : 'FETCH_FAILED'
    };
  }
  let cleanedText = ok.map(r => r.cleanedText || '').join(TEXT_SEPARATOR);
  if (cleanedText.length > MAX_CLEANED_LEN) cleanedText = cleanedText.slice(0, MAX_CLEANED_LEN);
  const seenAttachments = new Set();
  const extraAttachments = [];
  for (const r of ok) {
    const items = Array.isArray(r.extraAttachments) ? r.extraAttachments : [];
    for (const a of items) {
      if (!a || typeof a.url !== 'string') continue;
      const key = a.url.toLowerCase();
      if (seenAttachments.has(key)) continue;
      seenAttachments.add(key);
      extraAttachments.push(a);
    }
  }
  return { cleanedText, extraAttachments, status: null };
}
