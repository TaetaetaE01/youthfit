// 동기화 책임: 이 파일과 youth-center-seoul.json 의 "attachments 승격" 노드 jsCode 는
// 동일 알고리즘이어야 한다. README.md 참고.

const EXT_TO_MEDIA_TYPE = {
  pdf: 'application/pdf',
  hwp: 'application/x-hwp',
  hwpx: 'application/x-hwp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

function extractExt(url) {
  if (typeof url !== 'string') return null;
  const cleaned = url.split('#')[0].split('?')[0].toLowerCase();
  const dotIdx = cleaned.lastIndexOf('.');
  if (dotIdx === -1) return null;
  return cleaned.slice(dotIdx + 1);
}

const TEXT_EXT_PATTERN = /\.(pdf|hwpx?|docx?|xlsx?)\b/i;
const PAREN_EXT_PATTERN = /[\(\[]\s*(pdf|hwpx?|docx?|xlsx?)\s*[\)\]]/i;
const PATH_EXT_PATTERN = /(?:^|[^a-zA-Z])(pdf|hwpx?|docx?|xlsx?)(?:$|[^a-zA-Z])/i;
// path-pattern fallback 은 다운로드 의도 키워드가 동반될 때만 활성화한다.
// (예: /board/pdf/list 같은 카테고리 슬러그가 첨부로 잘못 승격되는 false positive 방지)
// 'attach' 가 atchFile/atchFileNo 의 'attach' 변형을 사실상 커버한다.
const PATH_DOWNLOAD_KEYWORD_PATTERN = /(?:download|filedown|attach|getfile|board)/i;

function extractExtFromText(text) {
  if (typeof text !== 'string' || text.length === 0) return null;
  const m1 = text.match(TEXT_EXT_PATTERN);
  if (m1) return m1[1].toLowerCase();
  const m2 = text.match(PAREN_EXT_PATTERN);
  if (m2) return m2[1].toLowerCase();
  return null;
}

function extractExtFromPath(url) {
  if (typeof url !== 'string') return null;
  const path = url.split('?')[0].split('#')[0]
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .toLowerCase();
  if (!PATH_DOWNLOAD_KEYWORD_PATTERN.test(path)) return null;
  const m = path.match(PATH_EXT_PATTERN);
  return m ? m[1].toLowerCase() : null;
}

function mapExt(ext) {
  if (!ext) return null;
  return EXT_TO_MEDIA_TYPE[ext.toLowerCase()] || null;
}

function inferMediaType(item) {
  const fromUrl = mapExt(extractExt(item.url));
  if (fromUrl) return fromUrl;
  const fromName = mapExt(extractExtFromText(item.name));
  if (fromName) return fromName;
  const fromPath = mapExt(extractExtFromPath(item.url));
  if (fromPath) return fromPath;
  return null;
}

const NAME_WHITELIST_PATTERN = /(공고|안내|모집|요강|신청서|계획서|FAQ|Q&A|가이드|설명|일정|참가|운영|평가|선정|채용|지원|보고서|양식|서식|자료|다운로드|붙임|별첨|결과|명단|목록)/i;
const NAME_BLACKLIST_PATTERN = /(로고|배너|아이콘|썸네일|포스터|광고|favicon)/i;

function isInformationalName(name) {
  if (typeof name !== 'string' || !name) return true;     // 이름이 없으면 통과 (확장자 매핑이 이미 OK)
  if (NAME_BLACKLIST_PATTERN.test(name)) return false;
  if (NAME_WHITELIST_PATTERN.test(name)) return true;
  return name.length >= 5;                                 // 보수적 fallback
}

export function promote(input) {
  const enrichment = input?.rawData?.enrichment;
  const extras = enrichment?.extraAttachments;
  if (!Array.isArray(extras) || extras.length === 0) {
    return input;
  }
  const attachments = Array.isArray(input.rawData.attachments)
    ? input.rawData.attachments
    : [];
  const existingUrls = new Set(
    attachments
      .map(a => (typeof a.url === 'string' ? a.url.toLowerCase() : null))
      .filter(Boolean)
  );
  const merged = [...attachments];
  for (const ex of extras) {
    if (!ex || typeof ex.url !== 'string') continue;
    const mediaType = inferMediaType(ex);
    if (!mediaType) continue;
    if (!isInformationalName(ex.name)) continue;
    const key = ex.url.toLowerCase();
    if (existingUrls.has(key)) continue;
    merged.push({ name: ex.name, url: ex.url, mediaType });
    existingUrls.add(key);
  }
  return { ...input, rawData: { ...input.rawData, attachments: merged } };
}
