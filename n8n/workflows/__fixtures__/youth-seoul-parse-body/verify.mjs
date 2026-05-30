import { extractByTh, extractRefUrls, extractSelfAttachments, extractApplyUrl, extractReferenceSites } from './parse-body.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const ROOT = new URL('./', import.meta.url);

async function loadCases(dir) {
  const url = new URL(`${dir}/`, ROOT);
  const entries = await readdir(url);
  return entries.filter(e => e.endsWith('.input.html')).sort()
    .map(input => ({
      name: input.replace('.input.html', ''),
      input,
      expected: input.replace('.input.html', '.expected.json'),
      meta: input.replace('.input.html', '.meta.json'),
      url,
    }));
}

function eq(a, b) {
  try { deepStrictEqual(a, b); return true; } catch { return false; }
}

async function readJson(url) {
  try { return JSON.parse(await readFile(url, 'utf8')); } catch { return null; }
}

let failed = 0;
let passed = 0;

async function runDir(dir, fn) {
  const cases = await loadCases(dir);
  for (const c of cases) {
    const html = await readFile(new URL(c.input, c.url), 'utf8');
    const expected = JSON.parse(await readFile(new URL(c.expected, c.url), 'utf8'));
    const meta = await readJson(new URL(c.meta, c.url));
    const actual = fn(html, meta);
    if (eq(actual, expected)) {
      console.log(`PASS  ${dir}/${c.name}`);
      passed++;
    } else {
      console.log(`FAIL  ${dir}/${c.name}`);
      console.log(`  expected: ${JSON.stringify(expected)}`);
      console.log(`  actual:   ${JSON.stringify(actual)}`);
      failed++;
    }
  }
}

await runDir('cases-extract-by-th', (html, meta) => extractByTh(html, meta?.thText || ''));
await runDir('cases-ref-urls', html => extractRefUrls(html));
await runDir('cases-self-attachments', html => extractSelfAttachments(html));
await runDir('cases-apply-url', html => extractApplyUrl(html));
await runDir('cases-reference-sites', html => extractReferenceSites(html));

if (failed > 0) {
  console.error(`\n${failed} case(s) failed (passed ${passed})`);
  process.exit(1);
}
console.log(`\nAll ${passed} case(s) passed`);
