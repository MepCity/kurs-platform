package org.mepcity.kursplatform.iam.application;

import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OperationScope;

/** Persistent, cross-instance IAM-006 quota. Implementations must use database time. */
@FunctionalInterface
public interface IamDeviceRateLimiter {
    void consume(UUID actorUserId, OperationScope scope, UUID contextId, OperationCode operationCode);
}
