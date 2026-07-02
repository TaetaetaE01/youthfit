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
const tls = require('tls');

// TLS 중간 인증서 보강 (#160). n8n 2.16 task runner 는 NODE_EXTRA_CA_CERTS 를
// 상속하지 않아 컨테이너 env 로 주입한 번들이 Code 노드 fetch 에 닿지 않는다.
// 그래서 누락된 중간 인증서(GlobalSign RSA OV SSL CA 2018 — kinfa.or.kr 등)를
// 노드 코드에 인라인해 기본 root 목록과 합쳐 request 의 ca 로 직접 넘긴다.
// 원본은 n8n/certs/extra-ca.pem. 만료 2028-11-21 (OPS.md 갱신 절차 참고).
const EXTRA_CA_PEM = `-----BEGIN CERTIFICATE-----
MIIETjCCAzagAwIBAgINAe5fIh38YjvUMzqFVzANBgkqhkiG9w0BAQsFADBMMSAw
HgYDVQQLExdHbG9iYWxTaWduIFJvb3QgQ0EgLSBSMzETMBEGA1UEChMKR2xvYmFs
U2lnbjETMBEGA1UEAxMKR2xvYmFsU2lnbjAeFw0xODExMjEwMDAwMDBaFw0yODEx
MjEwMDAwMDBaMFAxCzAJBgNVBAYTAkJFMRkwFwYDVQQKExBHbG9iYWxTaWduIG52
LXNhMSYwJAYDVQQDEx1HbG9iYWxTaWduIFJTQSBPViBTU0wgQ0EgMjAxODCCASIw
DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKdaydUMGCEAI9WXD+uu3Vxoa2uP
UGATeoHLl+6OimGUSyZ59gSnKvuk2la77qCk8HuKf1UfR5NhDW5xUTolJAgvjOH3
idaSz6+zpz8w7bXfIa7+9UQX/dhj2S/TgVprX9NHsKzyqzskeU8fxy7quRU6fBhM
abO1IFkJXinDY+YuRluqlJBJDrnw9UqhCS98NE3QvADFBlV5Bs6i0BDxSEPouVq1
lVW9MdIbPYa+oewNEtssmSStR8JvA+Z6cLVwzM0nLKWMjsIYPJLJLnNvBhBWk0Cq
o8VS++XFBdZpaFwGue5RieGKDkFNm5KQConpFmvv73W+eka440eKHRwup08CAwEA
AaOCASkwggElMA4GA1UdDwEB/wQEAwIBhjASBgNVHRMBAf8ECDAGAQH/AgEAMB0G
A1UdDgQWBBT473/yzXhnqN5vjySNiPGHAwKz6zAfBgNVHSMEGDAWgBSP8Et/qC5F
JK5NUPpjmove4t0bvDA+BggrBgEFBQcBAQQyMDAwLgYIKwYBBQUHMAGGImh0dHA6
Ly9vY3NwMi5nbG9iYWxzaWduLmNvbS9yb290cjMwNgYDVR0fBC8wLTAroCmgJ4Yl
aHR0cDovL2NybC5nbG9iYWxzaWduLmNvbS9yb290LXIzLmNybDBHBgNVHSAEQDA+
MDwGBFUdIAAwNDAyBggrBgEFBQcCARYmaHR0cHM6Ly93d3cuZ2xvYmFsc2lnbi5j
b20vcmVwb3NpdG9yeS8wDQYJKoZIhvcNAQELBQADggEBAJmQyC1fQorUC2bbmANz
EdSIhlIoU4r7rd/9c446ZwTbw1MUcBQJfMPg+NccmBqixD7b6QDjynCy8SIwIVbb
0615XoFYC20UgDX1b10d65pHBf9ZjQCxQNqQmJYaumxtf4z1s4DfjGRzNpZ5eWl0
6r/4ngGPoJVpjemEuunl1Ig423g7mNA2eymw0lIYkN5SQwCuaifIFJ6GlazhgDEw
fpolu4usBCOmmQDo8dIm7A9+O4orkjgTHY+GzYZSR+Y0fFukAj6KYXwidlNalFMz
hriSqHKvoflShx8xpfywgVcvzfTO3PYkz6fiNJBonf6q8amaEsybwMbDqKWwIX7e
SPY=
-----END CERTIFICATE-----`;
// ca 를 지정하면 기본 신뢰 목록이 대체되므로, Node 기본 root 에 중간 인증서를 더한다.
const CA_BUNDLE = [...tls.rootCertificates, EXTRA_CA_PEM];

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

// SSRF 가드: 내부 대역(사설/루프백/링크로컬/메타데이터/docker 서비스명)으로의 요청을 차단한다.
// n8n 은 docker 네트워크 안에서 돌고 prod 는 EC2(IMDS)라, 크롤 URL·리다이렉트가
// 내부 리소스로 향하면 안 된다. 리터럴 IP·알려진 내부 호스트명·단일 라벨 호스트를 막는다.
// (공개 호스트명이 내부 IP 로 resolve 되는 DNS rebinding 은 이 순수 가드 범위 밖 —
//  완전 방어는 dns.lookup 후 연결 IP 고정이 필요하며 별도 과제다.)
function isInternalHost(url) {
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
    if (isInternalHost(normalized)) {
      diagnostics.push({ url: normalized, outcome: 'INVALID_URL' });
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
      req = lib.request(url, { method: 'GET', headers, timeout: FETCH_TIMEOUT_MS, ca: CA_BUNDLE }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          const nextUrl = absUrl(res.headers.location, url);
          if (isInternalHost(nextUrl)) {
            res.resume();
            return resolve({ ok: false, outcome: 'INVALID_URL' });
          }
          const jar = applySetCookies(state.jar, host, res.headers['set-cookie'] || []);
          res.resume();
          return httpGetText(nextUrl, { hops: state.hops + 1, jar, visited: state.visited }).then(resolve);
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          res.resume();
          return resolve({ ok: false, outcome: 'HTTP_' + res.statusCode });
        }
        res.on('error', () => resolve({ ok: false, outcome: 'NETWORK' }));
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
