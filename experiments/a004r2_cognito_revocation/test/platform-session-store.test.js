import assert from 'node:assert/strict';
import test from 'node:test';

import { PlatformSessionStore, SessionRejectedError } from '../src/platform-session-store.js';

const subject = 'synthetic-cognito-subject';
const provider = 'COGNITO';
const realm = 'eu-central-1_pool-a';
const activeProvider = { available: true, active: true, provider, realm, subject };

function storeWithUser() {
  const store = new PlatformSessionStore();
  store.registerUser({
    userId: 'user-1',
    provider,
    realm,
    providerSubject: subject,
    organizationIds: ['org-a', 'org-b'],
    deviceIds: ['device-1', 'device-2'],
  });
  return store;
}

function exchange(store, organizationId = 'org-a', deviceId = 'device-1') {
  return store.exchangeProviderAssertion({
    provider,
    realm,
    providerSubject: subject,
    providerCheck: activeProvider,
    organizationId,
    deviceId,
  });
}

function rejectsCode(fn, code) {
  assert.throws(fn, (error) => error instanceof SessionRejectedError && error.code === code);
}

test('refresh token rotates once and reuse revokes the complete family', () => {
  const store = storeWithUser();
  const initial = exchange(store);
  const rotated = store.refresh(initial.refreshToken);
  assert.notEqual(rotated.refreshToken, initial.refreshToken);
  rejectsCode(() => store.refresh(initial.refreshToken), 'REFRESH_TOKEN_REUSE');
  rejectsCode(() => store.refresh(rotated.refreshToken), 'REFRESH_FAMILY_REVOKED');
  rejectsCode(() => store.authorize(rotated.accessToken), 'ACCESS_TOKEN_REVOKED');
});

test('provider outage fails closed before a platform family is issued', () => {
  const store = storeWithUser();
  rejectsCode(
    () =>
      store.exchangeProviderAssertion({
        provider,
        realm,
        providerSubject: subject,
        providerCheck: { available: false },
        organizationId: 'org-a',
        deviceId: 'device-1',
      }),
    'PROVIDER_STATUS_UNAVAILABLE',
  );
});

test('disabled or revoked provider assertion cannot create a platform session', () => {
  const store = storeWithUser();
  rejectsCode(
    () =>
      store.exchangeProviderAssertion({
        provider,
        realm,
        providerSubject: subject,
        providerCheck: { available: true, active: false, provider, realm, subject },
        organizationId: 'org-a',
        deviceId: 'device-1',
      }),
    'PROVIDER_ASSERTION_REVOKED',
  );
});

test('global provider event revokes every organization and is idempotent', () => {
  const store = storeWithUser();
  const orgA = exchange(store, 'org-a');
  const orgB = exchange(store, 'org-b');
  assert.deepEqual(
    store.applyProviderSecurityEvent({
      provider,
      realm,
      eventId: 'event-1',
      providerSubject: subject,
      type: 'DISABLE',
    }),
    { completion: 'COMPLETED', revokedFamilies: 2, state: 'REVOKED' },
  );
  assert.deepEqual(
    store.applyProviderSecurityEvent({
      provider,
      realm,
      eventId: 'event-1',
      providerSubject: subject,
      type: 'DISABLE',
    }),
    { completion: 'DUPLICATE', revokedFamilies: 0, state: 'REVOKED' },
  );
  rejectsCode(() => store.authorize(orgA.accessToken), 'ACCESS_TOKEN_REVOKED');
  rejectsCode(() => store.authorize(orgB.accessToken), 'ACCESS_TOKEN_REVOKED');
});

test('single-device revoke leaves the other device active', () => {
  const store = storeWithUser();
  const device1 = exchange(store, 'org-a', 'device-1');
  const device2 = exchange(store, 'org-a', 'device-2');
  assert.equal(store.revokeDevice({ userId: 'user-1', deviceId: 'device-1' }), 1);
  assert.equal(store.revokeDevice({ userId: 'user-1', deviceId: 'device-1' }), 0);
  rejectsCode(() => store.authorize(device1.accessToken), 'ACCESS_TOKEN_REVOKED');
  rejectsCode(() => exchange(store, 'org-a', 'device-1'), 'DEVICE_SESSION_REVOKED');
  assert.deepEqual(store.authorize(device2.accessToken), {
    userId: 'user-1',
    organizationId: 'org-a',
    deviceId: 'device-2',
  });
});

test('organization revoke cannot affect another organization', () => {
  const store = storeWithUser();
  const orgA = exchange(store, 'org-a');
  const orgB = exchange(store, 'org-b');
  assert.equal(store.revokeOrganization({ userId: 'user-1', organizationId: 'org-a' }), 1);
  assert.equal(store.revokeOrganization({ userId: 'user-1', organizationId: 'org-a' }), 0);
  rejectsCode(() => store.authorize(orgA.accessToken), 'ACCESS_TOKEN_REVOKED');
  rejectsCode(() => exchange(store, 'org-a'), 'MEMBERSHIP_SESSION_REVOKED');
  assert.equal(store.authorize(orgB.accessToken).organizationId, 'org-b');
});

test('reconciliation catches a lost disable event and revokes all families', () => {
  const store = storeWithUser();
  const orgA = exchange(store, 'org-a');
  const result = store.reconcileProviderState({
    provider,
    realm,
    providerSubject: subject,
    providerSnapshot: { available: true, active: false, provider, realm, subject },
  });
  assert.deepEqual(result, { revokedFamilies: 1, state: 'REVOKED' });
  rejectsCode(() => store.authorize(orgA.accessToken), 'ACCESS_TOKEN_REVOKED');
});

test('unavailable reconciliation revokes fail-closed and increments user generation once', () => {
  const store = storeWithUser();
  const family = exchange(store);
  assert.equal(store.userGeneration('user-1'), 0);
  const firstResult = store.reconcileProviderState({
    provider,
    realm,
    providerSubject: subject,
    providerSnapshot: { available: false },
  });
  const secondResult = store.reconcileProviderState({
    provider,
    realm,
    providerSubject: subject,
    providerSnapshot: { available: false },
  });
  assert.deepEqual(firstResult, { revokedFamilies: 1, state: 'FAIL_CLOSED' });
  assert.deepEqual(secondResult, { revokedFamilies: 0, state: 'FAIL_CLOSED' });
  assert.equal(store.userGeneration('user-1'), 1);
  rejectsCode(() => store.authorize(family.accessToken), 'ACCESS_TOKEN_REVOKED');
});

test('unknown subject event remains pending and applies after the mapping appears', () => {
  const store = new PlatformSessionStore();
  const event = {
    provider,
    realm,
    eventId: 'event-before-mapping',
    providerSubject: subject,
    type: 'DISABLE',
  };

  assert.deepEqual(store.applyProviderSecurityEvent(event), {
    completion: 'PENDING_MAPPING',
    revokedFamilies: 0,
    state: 'UNKNOWN_SUBJECT',
  });

  store.registerUser({
    userId: 'user-1',
    provider,
    realm,
    providerSubject: subject,
    organizationIds: ['org-a'],
    deviceIds: ['device-1'],
  });
  const activeFamily = exchange(store);

  assert.deepEqual(store.applyProviderSecurityEvent(event), {
    completion: 'COMPLETED',
    revokedFamilies: 1,
    state: 'REVOKED',
  });
  assert.equal(store.userGeneration('user-1'), 1);
  rejectsCode(() => store.authorize(activeFamily.accessToken), 'ACCESS_TOKEN_REVOKED');
});

test('event dedupe key is isolated by provider and realm', () => {
  const store = storeWithUser();
  const wrongProvider = store.applyProviderSecurityEvent({
    provider: 'KEYCLOAK',
    realm,
    eventId: 'shared-event-id',
    providerSubject: subject,
    type: 'DISABLE',
  });
  const wrongRealm = store.applyProviderSecurityEvent({
    provider,
    realm: 'eu-central-1_unknown-realm',
    eventId: 'shared-event-id',
    providerSubject: subject,
    type: 'DISABLE',
  });
  const correctScope = store.applyProviderSecurityEvent({
    provider,
    realm,
    eventId: 'shared-event-id',
    providerSubject: subject,
    type: 'DISABLE',
  });
  assert.equal(wrongProvider.completion, 'PENDING_MAPPING');
  assert.equal(wrongRealm.completion, 'PENDING_MAPPING');
  assert.equal(correctScope.completion, 'COMPLETED');
});

test('provider event without provider, realm, or event ID fails closed', () => {
  const store = storeWithUser();
  rejectsCode(
    () =>
      store.applyProviderSecurityEvent({
        provider: '',
        realm,
        eventId: 'event-1',
        providerSubject: subject,
        type: 'DISABLE',
      }),
    'PROVIDER_SCOPE_INVALID',
  );
});
