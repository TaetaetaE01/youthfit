// 동기화 책임: 이 파일과 youth-seoul-crawl.json 의 "상세 데이터 파싱" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

export function extractByTh(html, thText) {
  if (!html || !thText) return null;
  const escapedTh = thText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(
    '<th[^>]*>' + escapedTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>',
    'i'
  );
  const match = html.match(regex);
  if (!match) return '';
  return match[1]
    .replace(/<!--[\s\S]*?-->/g, '')              // 멀티라인 HTML 주석 제거
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/?[a-zA-Z][^>]*>/g, '')           // 영문 시작 태그만 — 본문 <민원인...> 보존
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\t/g, '')
    .replace(/^[  ]*-{3,}[  ]*$/gm, '') // dash 만 있는 줄 제거
    .replace(/-{4,}>/g, '')                       // dash 화살표 잔재
    .replace(/\n\s*\n/g, '\n')
    .trim();
}

const REF_SECTIONS = ['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];

export function extractRefUrls(html) {
  if (!html) return [];
  const urls = [];
  const seen = new Set();
  for (const th of REF_SECTIONS) {
    const escTh = th.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const re = new RegExp('<th[^>]*>' + escTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>', 'i');
    const m = html.match(re);
    if (!m) continue;
    const hrefRe = /href="(https?:\/\/[^"]+)"/gi;
    let hm;
    while ((hm = hrefRe.exec(m[1])) !== null) {
      const u = hm[1];
      if (seen.has(u)) continue;
      seen.add(u);
      urls.push(u);
    }
  }
  return urls.slice(0, 3);
}

const ATTACHMENT_EXT_PATTERN = /\.(pdf|hwp|hwpx|docx?|xlsx?|zip)(\?|$|#)/i;
const BASE_ORIGIN = 'https://youth.seoul.go.kr';

export function extractSelfAttachments(html) {
  if (!html) return [];
  const out = [];
  const seen = new Set();
  const linkRe = /<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let lm;
  while ((lm = linkRe.exec(html)) !== null) {
    const href = lm[1];
    if (!ATTACHMENT_EXT_PATTERN.test(href)) continue;
    const abs = /^https?:\/\//i.test(href)
      ? href
      : BASE_ORIGIN + (href.startsWith('/') ? href : '/' + href);
    if (seen.has(abs)) continue;
    seen.add(abs);
    const text = lm[2].replace(/<[^>]+>/g, '').trim().slice(0, 200);
    out.push({ name: text || abs.split('/').pop(), url: abs });
  }
  return out;
}
