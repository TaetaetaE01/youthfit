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

const APPLY_SECTION = '신청 사이트';
const REFERENCE_SECTIONS = ['관련 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];

function firstHref(td) {
  const m = td.match(/href="(https?:\/\/[^"]+)"/i);
  return m ? m[1] : null;
}

function tdByTh(html, thText) {
  const escTh = thText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp('<th[^>]*>' + escTh + '</th>\\s*<td[^>]*>([\\s\\S]*?)</td>', 'i');
  const m = html.match(re);
  return m ? m[1] : null;
}

export function extractApplyUrl(html) {
  if (!html) return null;
  const td = tdByTh(html, APPLY_SECTION);
  return td ? firstHref(td) : null;
}

export function extractReferenceSites(html) {
  if (!html) return [];
  const out = [];
  const seen = new Set();
  for (const th of REFERENCE_SECTIONS) {
    const td = tdByTh(html, th);
    if (!td) continue;
    const hrefRe = /href="(https?:\/\/[^"]+)"/gi;
    let hm;
    while ((hm = hrefRe.exec(td)) !== null) {
      const u = hm[1];
      if (seen.has(u)) continue;
      seen.add(u);
      out.push({ name: th.replace(/\s*[ⅠⅡ]\s*$/, '').trim(), url: u });
    }
  }
  return out;
}

// 본문 구성 섹션: th 라벨 → 본문 라벨. 청년몽땅정보통 상세의 의미있는 텍스트 칸을
// 페이지 자연 순서대로 담는다. 값이 빈 칸은 buildBody 에서 스킵한다.
// (정책 유형·사업신청기간·각종 사이트 URL 은 별도 필드로 처리하므로 제외)
const BODY_SECTIONS = [
  { th: '정책 소개', label: '사업개요' },
  { th: '주관 기관', label: '주관기관' },
  { th: '운영기관', label: '운영기관' },
  { th: '지원 내용', label: '지원내용' },
  { th: '지원규모', label: '지원규모' },
  { th: '사업운영기간', label: '사업운영기간' },
  { th: '연령', label: '지원대상' },
  { th: '참여요건', label: '참여요건' },
  { th: '학력', label: '학력' },
  { th: '전공요건', label: '전공요건' },
  { th: '취업상태', label: '취업상태' },
  { th: '특화분야 요건', label: '특화분야요건' },
  { th: '추가단서 사항', label: '추가단서' },
  { th: '참여제한 대상', label: '참여제한' },
  { th: '신청절차', label: '신청방법' },
  { th: '심사 및 발표', label: '심사·발표' },
  { th: '제출서류', label: '제출서류' },
  { th: '기타사항', label: '기타사항' },
];

export function buildBody(html) {
  const parts = [];
  for (const s of BODY_SECTIONS) {
    const v = extractByTh(html, s.th);
    if (v) parts.push(s.label + ': ' + v);
  }
  return parts.join('\n');
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
