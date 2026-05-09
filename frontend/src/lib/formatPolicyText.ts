export type ListItem = {
  text: string;
  note?: string[];
  sub?: ListItem[];
};

export type FormattedBlock =
  | { type: 'heading'; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; items: ListItem[] };

const BULLET_REGEX = /^(?:[-•·∙◦]|\d+[).]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮]|[가-힣]\.)\s+/;
const NOTE_REGEX = /^(?:※|\*)\s+/;
const BRACKET_HEADING_REGEX = /^\[([^\]]{1,30})\]\s*$/;
const LABELED_PAREN_REGEX = /^\([^)\n]{2,20}\)(?:\s+\S|\s*$)/;
const STAGE_REGEX = /^(?:\d+\s*라운드|아이디어\s*심사|최종\s*심사|예선|본선|결선)\s*[:：]\s*\S/;
const FIELD_REGEX = /^(?:제공방식|지원규모|지원기간|지원금액|지원범위|기준연도|지원주기|제공유형|사업기간|신청기간|문의처|운영기관|주관기관)\s*[:：]/;

function normalize(raw: string): string {
  return raw
    .replace(/\r\n/g, '\n')
    .replace(/(\S)\s*(※)\s+/g, '$1\n$2 ')
    .replace(/([.!?)가-힣%])\s*(\*)\s+/g, '$1\n$2 ')
    .replace(/([가-힣.!?:)%])\s*-\s+(\S)/g, '$1\n- $2')
    .replace(/([.!?])\s*(\d+[).])\s+/g, '$1\n$2 ')
    .replace(/([.!?:)])\s+(\([^)\n]{2,20}\))\s+(\S)/g, '$1\n$2 $3')
    .replace(/([.)])\s*(\[[^\]]+\])/g, '$1\n\n$2')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function isIntro(line: string): boolean {
  if (/같(?:습니다|음)\s*[.。:]?\s*$/.test(line)) return true;
  if (/:\s*$/.test(line)) return true;
  if (/(?:아래|다음|이하)[^\n]*(?:합니다|됩니다|적용|해당|포함|제외|충족|만족)[^\n]*\.\s*$/.test(line)) return true;
  if (LABELED_PAREN_REGEX.test(line)) return true;
  return false;
}

function stripBullet(line: string): string {
  return line.replace(BULLET_REGEX, '').trim();
}

function bulletDepth(line: string): number {
  if (/^\d+\.\s+/.test(line)) return 0;
  if (/^[가-힣]\.\s+/.test(line)) return 0;
  if (/^[-•·∙◦]\s+/.test(line)) return 0;
  if (/^\d+\)\s+/.test(line)) return 1;
  if (/^[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮]\s+/.test(line)) return 2;
  return -1;
}

type LineClass =
  | { kind: 'note'; text: string }
  | { kind: 'bullet'; text: string; depth: number }
  | { kind: 'field'; text: string }
  | { kind: 'intro'; text: string }
  | { kind: 'standalone'; text: string };

function classifyLines(lines: string[]): LineClass[] {
  return lines.map((line, i): LineClass => {
    if (NOTE_REGEX.test(line)) return { kind: 'note', text: line };
    const d = bulletDepth(line);
    if (d >= 0) return { kind: 'bullet', text: stripBullet(line), depth: d };
    if (FIELD_REGEX.test(line)) return { kind: 'field', text: line };
    if (STAGE_REGEX.test(line)) return { kind: 'bullet', text: line, depth: 0 };

    const explicit = isIntro(line);
    let nextIsListItem = false;
    for (let j = i + 1; j < lines.length; j++) {
      if (NOTE_REGEX.test(lines[j])) continue;
      const nd = bulletDepth(lines[j]);
      if (nd >= 0) { nextIsListItem = true; break; }
      if (STAGE_REGEX.test(lines[j])) { nextIsListItem = true; break; }
      break;
    }
    if (explicit || nextIsListItem) return { kind: 'intro', text: line };
    return { kind: 'standalone', text: line };
  });
}

function cleanupEmptySubs(items: ListItem[]): void {
  for (const it of items) {
    if (it.sub) {
      if (it.sub.length === 0) {
        delete it.sub;
      } else {
        cleanupEmptySubs(it.sub);
      }
    }
  }
}

function convertToItems(lines: string[]): ListItem[] {
  const root: ListItem[] = [];
  type Container = { depth: number; arr: ListItem[] };
  const stack: Container[] = [{ depth: -2, arr: root }];
  let lastItem: ListItem | null = null;

  for (const c of classifyLines(lines)) {
    if (c.kind === 'note') {
      if (lastItem) (lastItem.note ??= []).push(c.text);
      continue;
    }

    if (c.kind === 'field' || c.kind === 'standalone') {
      while (stack.length > 1) stack.pop();
      const it: ListItem = { text: c.text };
      stack[0].arr.push(it);
      lastItem = it;
      continue;
    }

    if (c.kind === 'intro') {
      while (stack.length > 1) stack.pop();
      const it: ListItem = { text: c.text, sub: [] };
      stack[0].arr.push(it);
      stack.push({ depth: -1, arr: it.sub! });
      lastItem = it;
      continue;
    }

    while (stack.length > 1 && stack[stack.length - 1].depth >= c.depth) {
      stack.pop();
    }
    const it: ListItem = { text: c.text, sub: [] };
    stack[stack.length - 1].arr.push(it);
    stack.push({ depth: c.depth, arr: it.sub! });
    lastItem = it;
  }

  cleanupEmptySubs(root);
  return root;
}

function processGroup(lines: string[], blocks: FormattedBlock[]) {
  if (lines.length === 0) return;
  if (
    lines.length === 1 &&
    !NOTE_REGEX.test(lines[0]) &&
    !BULLET_REGEX.test(lines[0])
  ) {
    blocks.push({ type: 'paragraph', text: lines[0] });
    return;
  }
  blocks.push({ type: 'list', items: convertToItems(lines) });
}

function appendSegment(lines: string[], blocks: FormattedBlock[]) {
  let buffer: string[] = [];
  const flush = () => {
    if (buffer.length) {
      processGroup(buffer, blocks);
      buffer = [];
    }
  };
  for (const line of lines) {
    const bm = line.match(BRACKET_HEADING_REGEX);
    if (bm) {
      flush();
      blocks.push({ type: 'heading', text: bm[1].trim() });
    } else {
      buffer.push(line);
    }
  }
  flush();
}

export function formatPolicyText(raw: string): FormattedBlock[] {
  if (!raw?.trim()) return [];
  const normalized = normalize(raw);
  const segments = normalized.split(/\n{2,}/);
  const blocks: FormattedBlock[] = [];

  for (const seg of segments) {
    const lines = seg
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean);
    if (lines.length === 0) continue;
    appendSegment(lines, blocks);
  }

  return blocks;
}
