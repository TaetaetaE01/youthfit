// 동기화 책임: 단일 원본은 n8n/workflows/node-src/link-fetch-merge.js 다.
// 이 파일은 그 순수 함수들의 미러이며 verify.mjs 로 검증한다. README.md 참고.

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const PER_PAGE_CAP = 8000;
const TEXT_SEPARATOR = '\n\n---\n\n';

// cheerio 는 host 에서 항상 설치돼 있지 않을 수 있어 dynamic import + 가용성 플래그로 처리한다.
// host 에서는 `import('cheerio')`, n8n container 에서는 pnpm 경로 fallback 으로 시도한다.
// verify.mjs 는 HTML 케이스를 cheerio 가 없으면 skip 하고 helper 케이스만 돌린다.
let cheerioModule = null;
async function getCheerio() {
  if (cheerioModule) return cheerioModule;
  try {
    cheerioModule = await import('cheerio');
    return cheerioModule;
  } catch (_) {
    // try n8n container's pnpm-resolved cheerio
    try {
      cheerioModule = await import('/usr/local/lib/node_modules/n8n/node_modules/cheerio/dist/commonjs/index.js');
      return cheerioModule;
    } catch (_e) {
      cheerioModule = null;
    }
  }
  return cheerioModule;
}
export async function cheerioAvailable() {
  return (await getCheerio()) != null;
}

function normalizeUrlKey(u) {
  return u.toLowerCase().replace(/\/+$/, '');
}

// URL 정규화: 스킴 없는 도메인(`www.kofpi.or.kr`)에 https 를 부여한다 (#157).
// URL 로 볼 수 없는 문자열은 null — 호출부가 INVALID_URL 로 기록한다.
export function normalizeCandidateUrl(raw) {
  if (typeof raw !== 'string') return null;
  const u = raw.trim();
  if (!u) return null;
  if (/^https?:\/\//i.test(u)) return u;
  if (u.startsWith('//')) return 'https:' + u;
  if (/^[a-z0-9-]+(\.[a-z0-9-]+)+([/:?#]|$)/i.test(u)) return 'https://' + u;
  return null;
}

// 자기 포털(youth.seoul.go.kr)은 fetch 하지 않는다.
// 메인은 인덱스 shell, content.do 는 WebGate JS 챌린지, view.do 는 타 정책 교차 오염원.
export function isSelfPortalUrl(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  if (!m) return false;
  return /(^|\.)youth\.seoul\.go\.kr$/i.test(m[1]);
}

// SSRF 가드: 내부 대역(사설/루프백/링크로컬/메타데이터/docker 서비스명)으로의 요청을 차단한다.
// n8n 은 docker 네트워크 안에서 돌고 prod 는 EC2(IMDS)라, 크롤 URL·리다이렉트가
// 내부 리소스로 향하면 안 된다. 리터럴 IP·알려진 내부 호스트명·단일 라벨 호스트를 막는다.
// (공개 호스트명이 내부 IP 로 resolve 되는 DNS rebinding 은 이 순수 가드 범위 밖 —
//  완전 방어는 dns.lookup 후 연결 IP 고정이 필요하며 별도 과제다.)
export function isInternalHost(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  if (!m) return false;
  let host = m[1].toLowerCase();
  if (host.startsWith('[') && host.endsWith(']')) host = host.slice(1, -1);
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local')) return true;
  if (host === 'metadata.google.internal') return true;
  if (host === '::1' || host === '::') return true;
  if (/^f[cd][0-9a-f]{2}:/.test(host)) return true;
  if (/^fe[89ab][0-9a-f]:/.test(host)) return true;
  const mapped = host.match(/^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/);
  const v4 = mapped ? mapped[1] : host;
  const oct = v4.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (oct) {
    const a = +oct[1], b = +oct[2];
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 192 && b === 168) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    return false;
  }
  if (/^(0x[0-9a-f]+|\d+)$/.test(host)) return true;
  if (!host.includes('.')) return true;
  return false;
}

// selectUrls 가 모은 후보를 정규화·필터링해 fetch 대상과 진단을 분리한다.
export function prepareUrls(candidates) {
  const urls = [];
  const diagnostics = [];
  const seen = new Set();
  for (const raw of Array.isArray(candidates) ? candidates : []) {
    const normalized = normalizeCandidateUrl(raw);
    if (!normalized) {
      diagnostics.push({ url: String(raw).slice(0, 500), outcome: 'INVALID_URL' });
      continue;
    }
    const key = normalizeUrlKey(normalized);
    if (seen.has(key)) continue;
    seen.add(key);
    if (isSelfPortalUrl(normalized)) {
      diagnostics.push({ url: normalized, outcome: 'SELF_PORTAL' });
      continue;
    }
    if (isInternalHost(normalized)) {
      diagnostics.push({ url: normalized, outcome: 'INVALID_URL' });
      continue;
    }
    urls.push(normalized);
  }
  return { urls, diagnostics };
}

export function selectUrls(policy) {
  // 1) 명시 refUrls[] 가 있으면 우선 사용 (youth-seoul-crawl)
  if (policy && Array.isArray(policy.refUrls)) {
    const seen = new Set();
    const out = [];
    for (const u of policy.refUrls) {
      if (typeof u !== 'string') continue;
      const trimmed = u.trim();
      if (!trimmed) continue;
      const key = normalizeUrlKey(trimmed);
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(trimmed);
      if (out.length >= MAX_URLS) break;
    }
    return out;
  }
  // 2) fallback: 온통청년 키 기반
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

export function absUrl(href, pageUrl) {
  if (!href) return href;
  if (/^https?:\/\//i.test(href)) return href;
  const m = pageUrl.match(/^(https?:\/\/[^/]+)/);
  const origin = m ? m[1] : '';
  if (href.startsWith('//')) {
    const proto = (pageUrl.match(/^(https?:)/) || ['', 'https:'])[1];
    return proto + href;
  }
  if (href.startsWith('/')) return origin + href;
  return origin + '/' + href.replace(/^\.?\//, '');
}

// onclick 기반 다운로드 사이트의 진짜 GET URL 재구성 룰.
// 같은 페이지의 모든 첨부 a 태그가 href="#" 인 경우, absUrl fallback 으로는
// 모두 동일 URL 이 되어 seen-dedup 으로 1개만 살아남는다.
// host 별 onclick 함수 호출 패턴을 인식해 인자로부터 GET URL 을 재구성한다.
const ONCLICK_DOWNLOAD_RULES = [
  {
    host: /(^|\.)kofpi\.or\.kr$/i,
    fnName: 'fnNotiDownload',
    buildUrl: (origin, seq) => `${origin}/noti/download.do?fileSeq=${encodeURIComponent(seq)}`,
  },
];

function isDummyHref(href) {
  if (!href) return true;
  const trimmed = href.trim();
  return trimmed === '' || trimmed === '#' || /^javascript:/i.test(trimmed);
}

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function resolveOnclickUrl(onclick, pageUrl) {
  if (!onclick) return null;
  const m = pageUrl.match(/^(https?:\/\/([^/]+))/);
  if (!m) return null;
  const origin = m[1];
  const host = m[2];
  for (const rule of ONCLICK_DOWNLOAD_RULES) {
    if (!rule.host.test(host)) continue;
    const fnRe = new RegExp('\\b' + escapeRegExp(rule.fnName) + "\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    const am = onclick.match(fnRe);
    if (!am) continue;
    return rule.buildUrl(origin, am[1]);
  }
  return null;
}

export async function extractCleanedAndAttachments(rawHtml, pageUrl) {
  const cheerio = await getCheerio();
  if (!cheerio) {
    throw new Error('cheerio not installed; HTML extraction requires `npm i cheerio` in this directory or running inside the n8n container.');
  }
  const $ = cheerio.load(rawHtml);
  $('script, style, nav, footer, aside, header, noscript').remove();
  const root = $('main').first().length ? $('main').first()
            : $('article').first().length ? $('article').first()
            : $('[role="main"]').first().length ? $('[role="main"]').first()
            : $('#content').first().length ? $('#content').first()
            : $('body').first();
  let cleaned = root.text().replace(/\s+/g, ' ').trim();
  if (cleaned.length > PER_PAGE_CAP) cleaned = cleaned.slice(0, PER_PAGE_CAP);

  const seen = new Set();
  const extras = [];
  $('a[href]').each((_, el) => {
    const $a = $(el);
    const href = $a.attr('href') || '';
    const onclick = $a.attr('onclick') || '';
    const text = $a.text().trim();
    const imgAlt = $a.find('img').first().attr('alt') || '';
    const lowerHref = href.toLowerCase();
    const extPattern = /\.(pdf|hwp|hwpx|docx|xlsx|zip)(\?|$|#)/i;
    const hasExt = extPattern.test(href);
    const textHasExt = /\.(pdf|hwp|hwpx|docx|xlsx|zip)$/i.test(text);
    const imgIsFile = /^(pdf|hwp|hwpx|docx|xlsx|zip)$/i.test(imgAlt);
    const hrefHasDownloadKw = /(download|filedown|attach)/i.test(lowerHref);
    const looksLikeFile = hasExt || textHasExt || imgIsFile || (hrefHasDownloadKw && text.length > 0 && text.length < 200);
    if (!looksLikeFile) return;
    let url = absUrl(href, pageUrl);
    if (isDummyHref(href)) {
      const reconstructed = resolveOnclickUrl(onclick, pageUrl);
      if (reconstructed) url = reconstructed;
    }
    if (seen.has(url)) return;
    seen.add(url);
    let name = text;
    if (!name || name.length < 2) {
      name = imgAlt ? `attachment.${imgAlt}` : url.split('/').pop().slice(0, 200);
    }
    name = name.replace(/\s*미리보기\s*$/, '').trim().slice(0, 200);
    extras.push({ name, url });
  });
  return { cleaned, extras };
}

// 리다이렉트 체인 한정 cookie jar (#158).
// Set-Cookie 의 name=value 만 취하고 속성(Path/Domain/Expires)은 무시한다 —
// 체인 밖으로 쿠키를 유지하지 않으므로 만료·스코프 관리가 불필요하다.
export function applySetCookies(jar, host, setCookieHeaders) {
  if (!Array.isArray(setCookieHeaders) || setCookieHeaders.length === 0) return jar;
  const next = { ...jar, [host]: { ...(jar[host] || {}) } };
  for (const line of setCookieHeaders) {
    if (typeof line !== 'string') continue;
    const pair = line.split(';', 1)[0];
    const eq = pair.indexOf('=');
    if (eq <= 0) continue;
    const name = pair.slice(0, eq).trim();
    if (!name) continue;
    next[host][name] = pair.slice(eq + 1).trim();
  }
  return next;
}

export function cookieHeaderFor(jar, host) {
  const cookies = jar && jar[host];
  if (!cookies) return null;
  const entries = Object.entries(cookies);
  if (entries.length === 0) return null;
  return entries.map(([k, v]) => `${k}=${v}`).join('; ');
}
