import { selectUrls, mergeFetchResults } from './enrich.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { deepStrictEqual } from 'node:assert/strict';

const CASES_DIR = new URL('./cases/', import.meta.url);

function deepEqual(a, b) {
  try { deepStrictEqual(a, b); return true; } catch { return false; }
}

const entries = await readdir(CASES_DIR);
const inputs = entries.filter(e => e.endsWith('.input.json')).sort();

if (inputs.length === 0) {
  console.error(`no fixtures found in ${CASES_DIR.pathname}`);
  process.exit(1);
}

let failed = 0;
for (const inputFile of inputs) {
  const name = inputFile.replace('.input.json', '');
  const expectedFile = `${name}.expected.json`;
  const input = JSON.parse(await readFile(new URL(inputFile, CASES_DIR), 'utf8'));
  const expected = JSON.parse(await readFile(new URL(expectedFile, CASES_DIR), 'utf8'));
  const actual = {
    selectedUrls: selectUrls(input.policy),
    merged: mergeFetchResults(input.fetchResults)
  };
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
