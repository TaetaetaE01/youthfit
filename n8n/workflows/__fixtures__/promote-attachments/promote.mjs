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

export function promote(input) {
  const enrichment = input?.rawData?.enrichment;
  const extras = enrichment?.extraAttachments;
  if (!Array.isArray(extras) || extras.length === 0) {
    return input;
  }
  const attachments = Array.isArray(input.rawData.attachments)
    ? input.rawData.attachments
    : [];
  const merged = [...attachments];
  for (const ex of extras) {
    const dotIdx = ex.url.lastIndexOf('.');
    if (dotIdx === -1) continue;
    const ext = ex.url.slice(dotIdx + 1);
    const mediaType = EXT_TO_MEDIA_TYPE[ext];
    if (!mediaType) continue;
    merged.push({ name: ex.name, url: ex.url, mediaType });
  }
  return { ...input, rawData: { ...input.rawData, attachments: merged } };
}
