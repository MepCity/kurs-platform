package org.mepcity.kursplatform.iam.application;

/** Versioned, typed boundary for the safe idempotency snapshots used by IAM-006. */
public interface DeviceSessionSnapshotSerializer {
    String serialize(DeviceSessionService.DeviceRevokeResult value);
    String serialize(DeviceSessionService.MembershipRevokeResult value);
    DeviceSessionService.DeviceRevokeResult readDeviceRevoke(String payload);
    DeviceSessionService.MembershipRevokeResult readMembershipRevoke(String payload);
}
