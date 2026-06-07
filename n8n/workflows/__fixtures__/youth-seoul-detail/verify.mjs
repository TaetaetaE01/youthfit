import { parsePlcyInfoDetail } from './parse-plcyinfo.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const HTML_CASES_DIR = new URL('./cases-html/', import.meta.url);

function deepEqual(a, b) {
  try {
    deepStrictEqual(a, b);
    return true;
  } catch {
    return false;
  }
}

const entries = await readdir(HTML_CASES_DIR);
const inputs = entries.filter((e) => e.endsWith('.input.html')).sort();

if (inputs.length === 0) {
  console.error(`no fixtures found in ${HTML_CASES_DIR.pathname}`);
  process.exit(1);
}

let failed = 0;
for (const inputFile of inputs) {
  const name = inputFile.replace('.input.html', '');
  const metaFile = `${name}.meta.json`;
  const expectedFile = `${name}.expected.json`;
  const html = await readFile(new URL(inputFile, HTML_CASES_DIR), 'utf8');
  const meta = JSON.parse(await readFile(new URL(metaFile, HTML_CASES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(expectedFile, HTML_CASES_DIR), 'utf8'));
  const actual = parsePlcyInfoDetail(html, meta);
  if (deepEqual(actual, expected)) {
    console.log(`PASS  ${name}`);
  } else {
    failed++;
    console.log(`FAIL  ${name}`);
    console.log(`  expected: ${JSON.stringify(expected)}`);
    console.log(`  actual:   ${JSON.stringify(actual)}`);
  }
}

if (failed > 0) {
  console.error(`\n${failed} case(s) failed`);
  process.exit(1);
}
console.log(`\nAll ${inputs.length} case(s) passed`);
