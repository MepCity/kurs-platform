import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scriptPath = fileURLToPath(new URL('../scripts/run-cognito-experiment.sh', import.meta.url));

test('real Cognito procedure is fail-closed and never enables shell tracing', () => {
  const script = readFileSync(scriptPath, 'utf8');
  assert.match(script, /^set -Eeuo pipefail$/m);
  assert.match(script, /^trap cleanup EXIT$/m);
  assert.doesNotMatch(script, /set\s+-x/);

  const result = spawnSync('bash', [scriptPath], {
    encoding: 'utf8',
    env: { PATH: process.env.PATH ?? '' },
  });
  assert.notEqual(result.status, 0);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /eyJ[A-Za-z0-9_-]{20,}\./);
});
