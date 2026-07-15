import { createHash, randomBytes, randomUUID } from 'node:crypto';

const ACTIVE = 'ACTIVE';
const REVOKED = 'REVOKED';

function digest(token) {
  return createHash('sha256').update(token).digest('hex');
}

function opaqueToken() {
  return randomBytes(32).toString('base64url');
}

function membershipKey(userId, organizationId) {
  return `${userId}:${organizationId}`;
}

function providerIdentityKey(provider, realm, subject) {
  if (![provider, realm, subject].every((value) => typeof value === 'string' && value.length > 0)) {
    throw new SessionRejectedError('PROVIDER_SCOPE_INVALID');
  }
  return JSON.stringify([provider, realm, subject]);
}

function providerEventKey(provider, realm, eventId) {
  if (![provider, realm, eventId].every((value) => typeof value === 'string' && value.length > 0)) {
    throw new SessionRejectedError('PROVIDER_SCOPE_INVALID');
  }
  return JSON.stringify([provider, realm, eventId]);
}

export class SessionRejectedError extends Error {
  constructor(code) {
    super(code);
    this.name = 'SessionRejectedError';
    this.code = code;
  }
}

export class PlatformSessionStore {
  #users = new Map();
  #memberships = new Map();
  #devices = new Map();
  #families = new Map();
  #refreshTokens = new Map();
  #accessTokens = new Map();
  #providerSubjects = new Map();
  #processedEvents = new Set();

  registerUser({ userId, provider, realm, providerSubject, organizationIds, deviceIds }) {
    this.#users.set(userId, {
      disabled: false,
      providerState: ACTIVE,
      sessionGeneration: 0,
    });
    this.#providerSubjects.set(providerIdentityKey(provider, realm, providerSubject), userId);
    for (const organizationId of organizationIds) {
      this.#memberships.set(membershipKey(userId, organizationId), {
        active: true,
        reauthenticationRequired: false,
        sessionGeneration: 0,
      });
    }
    for (const deviceId of deviceIds) {
      this.#devices.set(deviceId, {
        userId,
        reauthenticationRequired: false,
        sessionGeneration: 0,
      });
    }
  }

  exchangeProviderAssertion({
    provider,
    realm,
    providerSubject,
    providerCheck,
    organizationId,
    deviceId,
  }) {
    if (!providerCheck || providerCheck.available !== true) {
      throw new SessionRejectedError('PROVIDER_STATUS_UNAVAILABLE');
    }
    if (
      providerCheck.provider !== provider ||
      providerCheck.realm !== realm ||
      providerCheck.subject !== providerSubject ||
      providerCheck.active !== true
    ) {
      throw new SessionRejectedError('PROVIDER_ASSERTION_REVOKED');
    }

    const userId = this.#providerSubjects.get(
      providerIdentityKey(provider, realm, providerSubject),
    );
    const user = this.#users.get(userId);
    const membership = this.#memberships.get(membershipKey(userId, organizationId));
    const device = this.#devices.get(deviceId);
    if (!user || user.disabled || user.providerState !== ACTIVE) {
      throw new SessionRejectedError('USER_SESSION_REVOKED');
    }
    if (!membership?.active || membership.reauthenticationRequired) {
      throw new SessionRejectedError('MEMBERSHIP_SESSION_REVOKED');
    }
    if (!device || device.userId !== userId || device.reauthenticationRequired) {
      throw new SessionRejectedError('DEVICE_SESSION_REVOKED');
    }

    return this.#issueFamily({ userId, organizationId, deviceId });
  }

  refresh(refreshToken) {
    const tokenRecord = this.#refreshTokens.get(digest(refreshToken));
    if (!tokenRecord) {
      throw new SessionRejectedError('REFRESH_TOKEN_INVALID');
    }
    const family = this.#families.get(tokenRecord.familyId);
    if (!family || family.status !== ACTIVE) {
      throw new SessionRejectedError('REFRESH_FAMILY_REVOKED');
    }
    if (tokenRecord.consumed) {
      this.#revokeFamilies([family], 'REFRESH_TOKEN_REUSE');
      throw new SessionRejectedError('REFRESH_TOKEN_REUSE');
    }
    this.#assertCurrentGenerations(family);
    tokenRecord.consumed = true;
    return this.#issueTokens(family);
  }

  authorize(accessToken) {
    const tokenRecord = this.#accessTokens.get(digest(accessToken));
    const family = tokenRecord && this.#families.get(tokenRecord.familyId);
    if (!family || family.status !== ACTIVE) {
      throw new SessionRejectedError('ACCESS_TOKEN_REVOKED');
    }
    this.#assertCurrentGenerations(family);
    return {
      userId: family.userId,
      organizationId: family.organizationId,
      deviceId: family.deviceId,
    };
  }

  revokeDevice({ userId, deviceId, reason = 'DEVICE_SESSION_REVOKE' }) {
    const device = this.#devices.get(deviceId);
    if (!device || device.userId !== userId) {
      return 0;
    }
    if (!device.reauthenticationRequired) {
      device.reauthenticationRequired = true;
      device.sessionGeneration += 1;
    }
    return this.#revokeFamilies(
      [...this.#families.values()].filter(
        (family) => family.userId === userId && family.deviceId === deviceId,
      ),
      reason,
    );
  }

  revokeOrganization({ userId, organizationId, reason = 'ORGANIZATION_SESSION_REVOKE' }) {
    const membership = this.#memberships.get(membershipKey(userId, organizationId));
    if (!membership) {
      return 0;
    }
    if (!membership.reauthenticationRequired) {
      membership.reauthenticationRequired = true;
      membership.sessionGeneration += 1;
    }
    return this.#revokeFamilies(
      [...this.#families.values()].filter(
        (family) => family.userId === userId && family.organizationId === organizationId,
      ),
      reason,
    );
  }

  applyProviderSecurityEvent({ provider, realm, eventId, providerSubject, type }) {
    const completionKey = providerEventKey(provider, realm, eventId);
    if (this.#processedEvents.has(completionKey)) {
      return { completion: 'DUPLICATE', revokedFamilies: 0, state: REVOKED };
    }
    const userId = this.#providerSubjects.get(
      providerIdentityKey(provider, realm, providerSubject),
    );
    if (!userId) {
      return {
        completion: 'PENDING_MAPPING',
        revokedFamilies: 0,
        state: 'UNKNOWN_SUBJECT',
      };
    }
    const revokedFamilies = this.#revokeUser(userId, `PROVIDER_${type}`);
    this.#processedEvents.add(completionKey);
    return { completion: 'COMPLETED', revokedFamilies, state: REVOKED };
  }

  reconcileProviderState({ provider, realm, providerSubject, providerSnapshot }) {
    const userId = this.#providerSubjects.get(
      providerIdentityKey(provider, realm, providerSubject),
    );
    const user = this.#users.get(userId);
    if (!user) {
      return { revokedFamilies: 0, state: 'UNKNOWN_SUBJECT' };
    }
    if (!providerSnapshot || providerSnapshot.available !== true) {
      return {
        revokedFamilies: this.#revokeUser(userId, 'PROVIDER_RECONCILIATION_UNAVAILABLE'),
        state: 'FAIL_CLOSED',
      };
    }
    if (
      providerSnapshot.provider !== provider ||
      providerSnapshot.realm !== realm ||
      providerSnapshot.subject !== providerSubject ||
      providerSnapshot.active !== true
    ) {
      return {
        revokedFamilies: this.#revokeUser(userId, 'PROVIDER_RECONCILIATION_REVOKED'),
        state: REVOKED,
      };
    }
    user.providerState = ACTIVE;
    return { revokedFamilies: 0, state: ACTIVE };
  }

  familyStatus(familyId) {
    const family = this.#families.get(familyId);
    return family && { status: family.status, revokeReason: family.revokeReason };
  }

  userGeneration(userId) {
    return this.#users.get(userId)?.sessionGeneration;
  }

  #issueFamily({ userId, organizationId, deviceId }) {
    const user = this.#users.get(userId);
    const membership = this.#memberships.get(membershipKey(userId, organizationId));
    const device = this.#devices.get(deviceId);
    const family = {
      id: randomUUID(),
      userId,
      organizationId,
      deviceId,
      status: ACTIVE,
      userGeneration: user.sessionGeneration,
      membershipGeneration: membership.sessionGeneration,
      deviceGeneration: device.sessionGeneration,
    };
    this.#families.set(family.id, family);
    return { familyId: family.id, ...this.#issueTokens(family) };
  }

  #issueTokens(family) {
    const accessToken = opaqueToken();
    const refreshToken = opaqueToken();
    this.#accessTokens.set(digest(accessToken), { familyId: family.id });
    this.#refreshTokens.set(digest(refreshToken), {
      familyId: family.id,
      consumed: false,
    });
    return { accessToken, refreshToken };
  }

  #assertCurrentGenerations(family) {
    const user = this.#users.get(family.userId);
    const membership = this.#memberships.get(
      membershipKey(family.userId, family.organizationId),
    );
    const device = this.#devices.get(family.deviceId);
    if (
      !user ||
      user.disabled ||
      user.providerState !== ACTIVE ||
      user.sessionGeneration !== family.userGeneration
    ) {
      this.#revokeFamilies([family], 'USER_GENERATION_CHANGED');
      throw new SessionRejectedError('USER_SESSION_REVOKED');
    }
    if (
      !membership?.active ||
      membership.reauthenticationRequired ||
      membership.sessionGeneration !== family.membershipGeneration
    ) {
      this.#revokeFamilies([family], 'MEMBERSHIP_GENERATION_CHANGED');
      throw new SessionRejectedError('MEMBERSHIP_SESSION_REVOKED');
    }
    if (
      !device ||
      device.reauthenticationRequired ||
      device.sessionGeneration !== family.deviceGeneration
    ) {
      this.#revokeFamilies([family], 'DEVICE_GENERATION_CHANGED');
      throw new SessionRejectedError('DEVICE_SESSION_REVOKED');
    }
  }

  #revokeUser(userId, reason) {
    const user = this.#users.get(userId);
    if (!user) {
      return 0;
    }
    if (user.providerState === ACTIVE) {
      user.sessionGeneration += 1;
    }
    user.disabled = true;
    user.providerState = REVOKED;
    return this.#revokeFamilies(
      [...this.#families.values()].filter((family) => family.userId === userId),
      reason,
    );
  }

  #revokeFamilies(families, reason) {
    let revoked = 0;
    for (const family of families) {
      if (family.status === ACTIVE) {
        family.status = REVOKED;
        family.revokeReason = reason;
        revoked += 1;
      }
    }
    return revoked;
  }
}
