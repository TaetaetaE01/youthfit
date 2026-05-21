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
    'callback FAILED',
]) {
    assert.ok(nodes.includes(required), `force-enrich.json 에 노드 "${required}" 가 있어야 함`);
}

const connStr = JSON.stringify(workflow.connections);
assert.ok(connStr.includes('callback FAILED'),
    'callback FAILED 노드가 connections 에 연결돼야 함');

console.log('OK: force-enrich workflow has all required nodes');
