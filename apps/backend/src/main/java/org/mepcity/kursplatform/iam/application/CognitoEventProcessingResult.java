package org.mepcity.kursplatform.iam.application;

/**
 * Queue-independent durable hand-off result. Every value permits the transport adapter to ACK:
 * processing either completed or a canonical database row owns the remaining responsibility.
 */
public enum CognitoEventProcessingResult {
    COMPLETED,
    PERSISTED_PENDING,
    ALREADY_PERSISTED
}
