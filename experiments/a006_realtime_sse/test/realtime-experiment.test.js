import test from 'node:test';
import assert from 'node:assert/strict';
import { runScenario } from '../lib/scenario.js';

test('iki istemci olayi alir, kopan istemci kanonik duruma uzlasir ve kapsam korunur', async () => {
  const result = await runScenario();

  assert.ok(result.firstPropagationClientAMs >= 0);
  assert.ok(result.firstPropagationClientBMs >= 0);
  assert.ok(result.reconnectAndReconcileMs >= 0);
  assert.deepEqual(result.recoveredSequences, [2]);
  assert.equal(result.canonicalStatus, 'ABSENT');
  assert.equal(result.canonicalRowVersion, 2);
  assert.equal(result.crossOrganizationSubscriptionStatus, 403);
  assert.equal(result.unauthorizedClassSubscriptionStatus, 403);
  assert.equal(result.foreignUserSyncTokenStatus, 409);
  assert.equal(result.unauthenticatedSubscriptionStatus, 401);
});
