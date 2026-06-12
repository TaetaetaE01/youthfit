/**
 * 청년몽땅정보통(youth.seoul.go.kr) 상세 페이지 파서 — 3카테고리 공유.
 *
 * 서울시(plcyInfo/view.do tabKind=002)·자치구(tabKind=003)·중앙/타지역
 * (youthPlcyInfo/view.do) 세 상세 페이지는 동일한
 * `<th scope="row">라벨</th><td>값</td>` 테이블 구조를 갖는다(실증: N8N-1 spike).
 * 파서 1개로 셋 다 처리한다.
 *
 * 이 환경엔 cheerio 가 미설치이고, th/td 구조가 규칙적이므로 정규식 기반으로 작성한다.
 * (verify 가 항상 실행되도록 — cheerio dynamic import 로 인한 skip 회피)
 *
 * ⚠ 이 파일은 youth-seoul-city/district/external 워크플로우의 "상세 데이터 파싱"
 *   노드 jsCode 와 동기화 미러다. 한쪽 변경 시 양쪽을 함께 갱신한다(README 참조).
 */

const REF_LABELS = ['관련 사이트', '신청 사이트', '참고 사이트 Ⅰ', '참고 사이트 Ⅱ'];
const QUAL_LABELS = [
  '연령',
  '참여요건',
  '학력',
  '전공요건',
  '취업상태',
  '특화분야 요건',
  '추가단서 사항',
  '참여제한 대상',
];

/**
 * plcyInfo/view.do 상세 HTML → rawData.
 * @param {string} html  상세 페이지 HTML 원문
 * @param {{plcyBizId: string, region: string, sourceUrl: string}} ctx
 *   - plcyBizId: 외부 정책 ID (externalId 로 그대로 적재 — external-hash dedup 전제)
 *   - region: 'city'→'서울특별시', 'district'→호출자가 추출한 구명, 'external'→'타지역' 등.
 *             하드코딩 금지, 호출자가 주입한다.
 *   - sourceUrl: 상세 페이지 절대 URL (첨부/상대링크 절대경로화 기준)
 * @returns {object} rawData
 */
export function parsePlcyInfoDetail(html, ctx) {
  // 사이트 HTML 에는 비활성 영역이 `<!-- ... -->` 로 광범위하게 주석 처리돼 있고,
  // 주석 안에도 동일한 `<th scope="row">` 라벨이 존재한다. 실제 값만 추출하려면
  // 먼저 주석을 제거한다.
  const doc = stripComments(html);

  const title = extractTitle(doc);
  const body = joinNonEmpty(
    [thValue(doc, '정책 소개'), thValue(doc, '지원 내용')],
    '\n\n'
  );

  const additionalQualification = QUAL_LABELS.map((label) => {
    const value = thValue(doc, label);
    return value ? `${label}: ${value}` : '';
  })
    .filter(Boolean)
    .join('\n');

  const applyPeriod = thValueRaw(doc, '사업신청기간'); // "사업운영기간"은 신청기간 아님 → 사용 안 함
  const { start: applyStart, end: applyEnd } = parsePeriod(applyPeriod);

  const applyUrl = thLink(doc, '신청 사이트');

  const refUrls = [];
  for (const label of REF_LABELS) {
    const url = thLink(doc, label);
    if (url && !refUrls.includes(url)) refUrls.push(url);
    if (refUrls.length >= 3) break;
  }

  const selfAttachments = extractAttachments(doc, ctx.sourceUrl);

  return {
    externalId: ctx.plcyBizId,
    title,
    body,
    category: '복지', // 백엔드 mapCategory 가 본문/태그로 재분류
    region: ctx.region, // 호출자 주입
    additionalQualification, // support_target → 적합도 룰 생성
    applyStart,
    applyEnd,
    applyUrl,
    _refUrls: refUrls,
    _selfAttachments: selfAttachments,
  };
}

// ---------------------------------------------------------------------------
// region (자치구 전용)
// ---------------------------------------------------------------------------

/** 서울 25개 자치구. */
const SEOUL_GU = ['종로구','중구','용산구','성동구','광진구','동대문구','중랑구','성북구','강북구','도봉구','노원구','은평구','서대문구','마포구','양천구','강서구','구로구','금천구','영등포구','동작구','관악구','서초구','강남구','송파구','강동구'];

/**
 * 자치구 정책의 region 을 정책 제목에서 추출.
 *  1) 제목 끝 괄호 `(○○구)` 최우선. 예: "…응시료 지원사업(중랑구)" → "중랑구".
 *  2) 괄호가 없으면 제목 토큰 중 자치구명 정확매칭. 예: "2026년 중랑구 청년 재테크 교육" → "중랑구".
 * 제목에 구명이 없으면(본청/전체 정책) → "서울특별시".
 * ⚠ footer 주소(예 "중구 세종대로 124")가 섞인 제목은 우편번호(\d{5})·"(우)" 시그널로 감지해
 *   토큰 탐색을 생략한다(주소 구명 오매칭 방지). 반드시 파서가 추출한 title 을 넘겨 호출한다.
 * ⚠ 토큰은 split + 정확매칭이라 "진로"·"중구역" 같은 부분문자열 오인이 없다.
 * ⚠ 한계: 공백 없이 붙은 구명("강남구청년센터")은 토큰 분리가 안 돼 서울특별시로 폴백한다.
 *   (오분류보다 시 단위 폴백이 안전하다는 의도적 선택)
 */
export function regionFromDistrictTitle(title) {
  const s = String(title || '');
  const paren = s.match(/\(([가-힣]+구)\)/);
  if (paren && SEOUL_GU.includes(paren[1])) return paren[1];
  if (/\(우\)|\d{5}/.test(s)) return '서울특별시'; // footer 주소 섞임 → 토큰 탐색 생략
  for (const tok of s.split(/\s+/)) {
    if (SEOUL_GU.includes(tok)) return tok;
  }
  return '서울특별시';
}

// ---------------------------------------------------------------------------
// 헬퍼
// ---------------------------------------------------------------------------

/** HTML 주석 제거. */
export function stripComments(html) {
  return String(html).replace(/<!--[\s\S]*?-->/g, '');
}

/** 정규식 escape. */
function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * `<th scope="row">label</th>` 바로 뒤 `<td ...>...</td>` 의 inner HTML 을 반환.
 * caption 등 다른 위치의 동일 단어 오매칭을 피하려고 `<th scope="row">` 컨텍스트로 한정한다.
 * 매칭 없으면 빈 문자열.
 */
export function thValueRawHtml(html, label) {
  const re = new RegExp(
    '<th\\s+scope="row">\\s*' + escapeRegExp(label) + '\\s*</th>\\s*<td[^>]*>([\\s\\S]*?)</td>'
  );
  const m = html.match(re);
  return m ? m[1] : '';
}

/** thValueRawHtml 의 텍스트화(정제 전, 공백 보존) — 날짜 등 원문 패턴 매칭용. */
export function thValueRaw(html, label) {
  return decodeAndStrip(thValueRawHtml(html, label));
}

/** 라벨의 td 값을 텍스트로 정제해 반환(태그 제거·엔티티 디코드·공백 정규화). */
export function thValue(html, label) {
  return normalizeWhitespace(thValueRaw(html, label));
}

/** 라벨의 td 안 첫 번째 `<a href>` 절대 URL. 없으면 빈 문자열. */
export function thLink(html, label) {
  const inner = thValueRawHtml(html, label);
  const m = inner.match(/href="([^"]*)"/);
  if (!m) return '';
  const href = decodeEntities(m[1]).trim();
  if (!href || href.startsWith('javascript:') || href === '#') return '';
  return href;
}

/** 상세 상단 제목: `<strong class="title">...</strong>`. */
export function extractTitle(html) {
  const m = html.match(/<strong\s+class="title">([\s\S]*?)<\/strong>/);
  return m ? normalizeWhitespace(decodeAndStrip(m[1])) : '';
}

/**
 * 첨부 테이블("파일 명" 헤더) 안의 정적 `<a href>` 링크를 [{name, url}] 로 추출.
 * youth.seoul.go.kr 첨부 목록은 JS(htmlStr += ...)로 동적 렌더되므로 정적 HTML 에는
 * 보통 비어 있다(실측: youth-seoul-attachment-limit). 정적 링크가 있을 때만 채운다.
 */
export function extractAttachments(html, sourceUrl) {
  // "파일 명" col 헤더를 가진 table 영역만 좁혀서 본다.
  const headIdx = html.indexOf('<th scope="col">파일 명</th>');
  if (headIdx < 0) return [];
  const tableEnd = html.indexOf('</table>', headIdx);
  const region = tableEnd < 0 ? html.slice(headIdx) : html.slice(headIdx, tableEnd);

  const out = [];
  const re = /<a[^>]*href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/g;
  let m;
  while ((m = re.exec(region)) !== null) {
    const href = decodeEntities(m[1]).trim();
    if (!href || href.startsWith('javascript:') || href === '#') continue;
    const name = normalizeWhitespace(decodeAndStrip(m[2]));
    out.push({ name, url: absolutize(href, sourceUrl) });
  }
  return out;
}

/**
 * 신청기간 문자열 → {start, end} (YYYY-MM-DD | null).
 * 지원 포맷:
 *   - "2026.4.1.(수) 10:00 ~ 2026.4.14.(화) 18:00" (점 구분, 요일/시간 포함)
 *   - "20260520 ~ 20260529" (YYYYMMDD 압축)
 *   - "2026-04-01" 등
 * "~" 로 분리하고 각 측에서 첫 날짜를 추출한다.
 */
export function parsePeriod(raw) {
  const text = String(raw || '').trim();
  if (!text) return { start: null, end: null };
  const parts = text.split('~');
  const start = extractDate(parts[0]);
  const end = parts.length > 1 ? extractDate(parts[1]) : null;
  return { start, end };
}

/** 문자열에서 첫 날짜를 YYYY-MM-DD 로. 없으면 null. */
function extractDate(s) {
  if (!s) return null;
  const str = String(s);
  // YYYY.MM.DD / YYYY-MM-DD / YYYY/MM/DD
  let m = str.match(/(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})/);
  if (m) return `${m[1]}-${pad2(m[2])}-${pad2(m[3])}`;
  // YYYYMMDD
  m = str.match(/(\d{4})(\d{2})(\d{2})/);
  if (m) return `${m[1]}-${m[2]}-${m[3]}`;
  return null;
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

/** 상대/절대 href 를 baseUrl 기준 절대 URL 로. */
export function absolutize(href, baseUrl) {
  if (!href) return '';
  try {
    return new URL(href, baseUrl).href;
  } catch {
    return href;
  }
}

// 의미 있는 줄바꿈(`<br>`)만 보존하기 위한 sentinel. 소스 들여쓰기 개행과 구분한다.
const BR_SENTINEL = ' BR ';

/**
 * HTML 태그 제거 + 엔티티 디코드 + `<br>` → 개행.
 * `<br>` 만 의미 있는 줄바꿈으로 보존하고, 소스 들여쓰기의 개행은 normalizeWhitespace 에서
 * 공백으로 흡수된다(BR_SENTINEL 로 표시 후 복원).
 */
export function decodeAndStrip(htmlFragment) {
  let s = String(htmlFragment);
  s = s.replace(/<br\s*\/?>/gi, BR_SENTINEL);
  s = s.replace(/<[^>]+>/g, '');
  s = decodeEntities(s);
  return s;
}

/**
 * 텍스트 공백 정규화: 모든 공백(개행 포함)을 단일 공백으로, BR_SENTINEL 은 개행으로 복원,
 * 줄 단위 trim, 빈 줄 제거.
 * → `<br>` 가 만든 줄바꿈만 남고, 소스 들여쓰기 개행은 공백으로 흡수된다.
 */
export function normalizeWhitespace(text) {
  return String(text)
    .replace(/\s+/g, ' ')
    .split(BR_SENTINEL)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join('\n')
    .trim();
}

/** 비어있지 않은 값들만 separator 로 결합. */
function joinNonEmpty(values, separator) {
  return values.filter(Boolean).join(separator);
}

/** 기본 HTML 엔티티 디코드. */
export function decodeEntities(text) {
  return String(text)
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&#x27;/gi, "'")
    .replace(/&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&'); // amp 는 마지막에 (이중 디코드 방지)
}
