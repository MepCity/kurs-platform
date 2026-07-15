import assert from 'node:assert/strict';
import test from 'node:test';

import { buildExperimentResult, experimentExitCode } from '../src/experiment-result.js';

test('experiment exits non-zero when refresh rotation does not produce a new token', () => {
  const result = buildExperimentResult({
    initialRefreshToken: 'same-token',
    rotatedRefreshToken: 'same-token',
    reuseDetected: true,
    familyRevoked: true,
  });

  assert.equal(result.refreshRotation, 'FAIL');
  assert.equal(experimentExitCode(result), 1);
});

test('experiment exits zero only when every measured assertion passes', () => {
  const result = buildExperimentResult({
    initialRefreshToken: 'initial-token',
    rotatedRefreshToken: 'rotated-token',
    reuseDetected: true,
    familyRevoked: true,
  });

  assert.deepEqual(result, {
    opaqueValuesPrinted: false,
    refreshRotation: 'PASS',
    refreshReuseDetection: 'PASS',
    completeFamilyRevocation: 'PASS',
  });
  assert.equal(experimentExitCode(result), 0);
});
