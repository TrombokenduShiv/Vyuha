import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { actionCopy, formatRisk, riskTone, scenarioCopy } from '../apps/judge-dashboard/src/presentation.js';
const { scenarios } = JSON.parse(readFileSync(new URL('../apps/judge-dashboard/src/data/evaluation.json', import.meta.url)));

test('every saved scenario has readable copy and its existing action template', () => {
  assert.equal(scenarios.length, 7);
  for (const item of scenarios) {
    assert.ok(scenarioCopy[item.case]?.title);
    assert.ok(actionCopy[item.template_id]?.message);
  }
});
test('unknown recipient evidence stays unknown, with a verification action', () => {
  assert.equal(formatRisk(null), 'Unknown');
  assert.equal(riskTone(null), 'unknown');
  for (const item of scenarios.filter(item => item.counterparty_risk === null)) {
    assert.equal(actionCopy[item.template_id].tone, 'unknown');
    assert.notEqual(item.template_id, 'PASS');
    assert.notEqual(item.template_id, 'ISOLATION_BREAK');
  }
});
test('recipient risk is not presented as payer coercion', () => {
  const item = scenarios.find(item => item.template_id === 'COUNTERPARTY_WARNING');
  assert.equal(formatRisk(item.agency_risk), '0.2%');
  assert.equal(formatRisk(item.counterparty_risk), '92.0%');
  assert.equal(actionCopy[item.template_id].title, 'Verify the recipient before paying');
});
test('isolation wording preserves the call boundary and bank authority', () => {
  for (const item of scenarios.filter(item => item.template_id === 'ISOLATION_BREAK')) {
    assert.ok(item.context.communication_active || item.context.capture_risk);
    assert.ok(item.agency_risk > .9);
    assert.equal(formatRisk(item.agency_risk), '99.7%');
  }
  assert.match(actionCopy.ISOLATION_BREAK.message, /cannot end another app/);
  assert.match(actionCopy.PASS.message, /bank still authorizes/);
});
