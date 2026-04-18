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

type InternalItem = ListItem & { _hasBullet?: boolean };

function clean(item: InternalItem): ListItem {
  const { _hasBullet, ...rest } = item;
  void _hasBullet;
  return rest;
}

function isExplicitIntro(it: InternalItem): boolean {
  return !it._hasBullet && isIntro(it.text);
}

function isImplicitIntro(items: InternalItem[], idx: number): boolean {
  const it = items[idx];
  if (it._hasBullet) return false;
  if (isExplicitIntro(it)) return false;
  if (idx + 1 >= items.length) return false;
  return !!items[idx + 1]._hasBullet;
}

function groupItems(items: InternalItem[]): ListItem[] {
  const hasExplicit = items.some(isExplicitIntro);
  const isIntroAt = hasExplicit
    ? (idx: number) => isExplicitIntro(items[idx])
    : (idx: number) => isImplicitIntro(items, idx);

  const result: ListItem[] = [];
  let i = 0;
  while (i < items.length) {
    if (isIntroAt(i)) {
      let j = i + 1;
      while (j < items.length && !isIntroAt(j)) j++;
      const children = items.slice(i + 1, j);
      const parent: ListItem = { ...clean(items[i]) };
      if (children.length > 0) {
        parent.sub = groupItems(children);
      }
      result.push(parent);
      i = j;
    } else {
      result.push(clean(items[i]));
      i++;
    }
  }
  return result;
}

function convertToItems(lines: string[]): ListItem[] {
  const items: InternalItem[] = [];
  let current: InternalItem | null = null;

  for (const line of lines) {
    if (NOTE_REGEX.test(line)) {
      if (current) {
        (current.note ??= []).push(line);
      } else {
        current = { text: line };
        items.push(current);
      }
    } else {
      const hasBullet = BULLET_REGEX.test(line);
      const newItem: InternalItem = {
        text: stripBullet(line),
        _hasBullet: hasBullet,
      };
      items.push(newItem);
      current = newItem;
    }
  }

  return groupItems(items);
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
