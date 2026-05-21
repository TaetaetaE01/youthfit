import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dir = path.dirname(fileURLToPath(import.meta.url));
const workflow = JSON.parse(
    readFileSync(path.join(__dir, '..', '..', 'force-enrich.json'), 'utf8'));

const nodes = workflow.nodes.map(n => n.name);
for (const required of [
    'Webhook',
    'callback RUNNING',
    'enrich',
    'ingestion update',
    'callback SUCCESS',
]) {
    assert.ok(nodes.includes(required), `force-enrich.json 에 노드 "${required}" 가 있어야 함`);
}

console.log('OK: force-enrich workflow has all required nodes');
