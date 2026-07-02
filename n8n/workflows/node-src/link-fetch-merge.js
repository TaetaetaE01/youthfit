// ⚠ 단일 원본: 이 파일이 4개 워크플로우 노드 jsCode 의 원본이다.
//   - youth-seoul-city/district/external.json :: "참고사이트 fetch + 머지"
//   - youth-center-seoul.json :: "링크 fetch + 머지"
//   워크플로우 JSON 을 손으로 수정하지 말고 sync-link-fetch-merge.mjs 를 실행한다.
// ⚠ 알고리즘 미러: __fixtures__/enrichment-merge/enrich.mjs (verify.mjs 로 검증).
//   순수 함수(selectUrls/prepareUrls/mergeFetchResults/cookie jar/추출)는
//   미러와 동일 본문이어야 한다.
const cheerio = require('cheerio');
const https = require('https');
const http = require('http');

const MAX_URLS = 3;
const MAX_CLEANED_LEN = 16000;
const TEXT_SEPARATOR = '\n\n---\n\n';
const FETCH_TIMEOUT_MS = 10000;
const MAX_RESPONSE_BYTES = 2000000;
const PER_PAGE_CAP = 8000;

function normalizeUrlKey(u) {
  return u.toLowerCase().replace(/\/+$/, '');
}

function selectUrls(policy) {
  // 1) 명시 refUrls[] 가 있으면 우선 사용 (youth-seoul 몽땅 크롤)
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

// URL 정규화: 스킴 없는 도메인(`www.kofpi.or.kr`)에 https 를 부여한다 (#157).
// URL 로 볼 수 없는 문자열은 null — 호출부가 INVALID_URL 로 기록한다.
function normalizeCandidateUrl(raw) {
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
function isSelfPortalUrl(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  if (!m) return false;
  return /(^|\.)youth\.seoul\.go\.kr$/i.test(m[1]);
}

// selectUrls 가 모은 후보를 정규화·필터링해 fetch 대상과 진단을 분리한다.
function prepareUrls(candidates) {
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
    urls.push(normalized);
  }
  return { urls, diagnostics };
}

// 리다이렉트 체인 한정 cookie jar (#158).
// Set-Cookie 의 name=value 만 취하고 속성(Path/Domain/Expires)은 무시한다 —
// 체인 밖으로 쿠키를 유지하지 않으므로 만료·스코프 관리가 불필요하다.
function applySetCookies(jar, host, setCookieHeaders) {
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

function cookieHeaderFor(jar, host) {
  const cookies = jar && jar[host];
  if (!cookies) return null;
  const entries = Object.entries(cookies);
  if (entries.length === 0) return null;
  return entries.map(([k, v]) => `${k}=${v}`).join('; ');
}

function mergeFetchResults(results) {
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

function absUrl(href, pageUrl) {
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

function resolveOnclickUrl(onclick, pageUrl) {
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

function extractCleanedAndAttachments(rawHtml, pageUrl) {
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

// TLS 계열 에러코드 → TLS_ERROR 로 분류 (그 외 소켓 에러는 NETWORK)
const TLS_ERROR_CODES = /^(UNABLE_TO_VERIFY_LEAF_SIGNATURE|SELF_SIGNED_CERT_IN_CHAIN|DEPTH_ZERO_SELF_SIGNED_CERT|CERT_|ERR_TLS_)/;

function hostOf(url) {
  const m = String(url).match(/^https?:\/\/([^/:?#]+)/i);
  return m ? m[1].toLowerCase() : '';
}

// #157: lib.request 동기 throw(ERR_INVALID_URL 등)까지 전부 resolve 로 흡수한다.
// 반환: { ok:true, body } | { ok:false, outcome }
function httpGetText(url, state) {
  state = state || { hops: 0, jar: {}, visited: [] };
  if (state.hops >= 5) return Promise.resolve({ ok: false, outcome: 'REDIRECT_LOOP' });
  return new Promise((resolve) => {
    let req;
    try {
      const lib = /^https:\/\//i.test(url) ? https : http;
      const host = hostOf(url);
      const cookie = cookieHeaderFor(state.jar, host);
      // 같은 URL 을 같은 쿠키로 재방문 = 쿠키를 줘도 안 풀리는 루프 → 중단
      const visitKey = url + '|' + (cookie || '');
      if (state.visited.includes(visitKey)) {
        return resolve({ ok: false, outcome: 'REDIRECT_LOOP' });
      }
      state.visited.push(visitKey);
      const headers = {
        'User-Agent': 'YouthFit-Bot/1.0 (+https://youthfit.kr/bot)',
        'Accept': 'text/html,application/xhtml+xml',
        'Accept-Encoding': 'identity'
      };
      if (cookie) headers['Cookie'] = cookie;
      req = lib.request(url, { method: 'GET', headers, timeout: FETCH_TIMEOUT_MS }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          const jar = applySetCookies(state.jar, host, res.headers['set-cookie'] || []);
          res.resume();
          return httpGetText(absUrl(res.headers.location, url), { hops: state.hops + 1, jar, visited: state.visited }).then(resolve);
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          res.resume();
          return resolve({ ok: false, outcome: 'HTTP_' + res.statusCode });
        }
        const chunks = [];
        let total = 0;
        res.on('data', c => {
          total += c.length;
          if (total > MAX_RESPONSE_BYTES) {
            req.destroy();
            resolve({ ok: false, outcome: 'OVERSIZE' });
            return;
          }
          chunks.push(c);
        });
        res.on('end', () => resolve({ ok: true, body: Buffer.concat(chunks).toString('utf8') }));
      });
      req.on('error', (e) => {
        const code = (e && e.code) || '';
        resolve({ ok: false, outcome: TLS_ERROR_CODES.test(code) ? 'TLS_ERROR' : 'NETWORK' });
      });
      req.on('timeout', () => { req.destroy(); resolve({ ok: false, outcome: 'TIMEOUT' }); });
      req.end();
    } catch (e) {
      resolve({ ok: false, outcome: 'INVALID_URL' });
    }
  });
}

// #157: URL 하나의 실패가 다른 URL·다른 정책으로 번지지 않게 await 도 격리한다.
async function fetchAndExtract(url) {
  let res;
  try {
    res = await httpGetText(url);
  } catch (e) {
    res = { ok: false, outcome: 'NETWORK' };
  }
  if (!res.ok || !res.body) {
    return { url, status: 'FETCH_FAILED', outcome: res.outcome || 'NETWORK', cleanedText: '', extraAttachments: [] };
  }
  try {
    const { cleaned, extras } = extractCleanedAndAttachments(res.body, url);
    const tooShort = cleaned.length < 200;
    return {
      url,
      status: tooShort ? 'TOO_SHORT' : null,
      outcome: tooShort ? 'TOO_SHORT' : 'OK',
      cleanedText: cleaned,
      extraAttachments: extras
    };
  } catch (e) {
    return { url, status: 'FETCH_FAILED', outcome: 'NETWORK', cleanedText: '', extraAttachments: [] };
  }
}

// 호출부 — 몽땅(rawData._refUrls)과 온통청년(aplyUrlAddr 계열) 모두 처리하는 통합 tail.
const p = $input.first().json;
const refUrls = p && p.rawData && Array.isArray(p.rawData._refUrls) ? p.rawData._refUrls : null;
const candidates = refUrls ? selectUrls({ refUrls }) : selectUrls(p);
const prep = prepareUrls(candidates);

if (prep.urls.length === 0) {
  return [{
    json: {
      ...p,
      _enrichUrl: null,
      _enrichUrls: [],
      _cleanedText: '',
      _extraAttachments: [],
      _enrichmentStatus: 'NO_LINK',
      _fetchDiagnostics: prep.diagnostics
    }
  }];
}

const results = await Promise.all(prep.urls.map(u => fetchAndExtract(u)));
const merged = mergeFetchResults(results);
const diagnostics = prep.diagnostics.concat(results.map(r => ({ url: r.url, outcome: r.outcome })));

return [{
  json: {
    ...p,
    _enrichUrl: prep.urls[0],
    _enrichUrls: prep.urls,
    _cleanedText: merged.cleanedText,
    _extraAttachments: merged.extraAttachments,
    _enrichmentStatus: merged.status,
    _fetchDiagnostics: diagnostics
  }
}];
