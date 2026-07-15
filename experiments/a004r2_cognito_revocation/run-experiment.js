import { PlatformSessionStore, SessionRejectedError } from './src/platform-session-store.js';
import { buildExperimentResult, experimentExitCode } from './src/experiment-result.js';

const store = new PlatformSessionStore();
const provider = 'COGNITO';
const realm = 'eu-central-1_synthetic';
const providerSubject = 'synthetic-cognito-subject';
store.registerUser({
  userId: 'synthetic-user',
  provider,
  realm,
  providerSubject,
  organizationIds: ['synthetic-org'],
  deviceIds: ['synthetic-device'],
});

const family = store.exchangeProviderAssertion({
  provider,
  realm,
  providerSubject,
  providerCheck: { available: true, active: true, provider, realm, subject: providerSubject },
  organizationId: 'synthetic-org',
  deviceId: 'synthetic-device',
});
const rotated = store.refresh(family.refreshToken);

let reuseDetected = false;
try {
  store.refresh(family.refreshToken);
} catch (error) {
  if (error instanceof SessionRejectedError && error.code === 'REFRESH_TOKEN_REUSE') {
    reuseDetected = true;
  }
}

let familyRevoked = false;
try {
  store.authorize(rotated.accessToken);
} catch (error) {
  if (error instanceof SessionRejectedError && error.code === 'ACCESS_TOKEN_REVOKED') {
    familyRevoked = true;
  }
}

const result = buildExperimentResult({
  initialRefreshToken: family.refreshToken,
  rotatedRefreshToken: rotated.refreshToken,
  reuseDetected,
  familyRevoked,
});

console.log(JSON.stringify(result, null, 2));
process.exitCode = experimentExitCode(result);
