package org.mepcity.kursplatform.org.application;

/**
 * Provider-independent port that persists {@link AuditEvent} rows.
 *
 * <p>Implementations participate in the caller's transaction (Spring-managed or manual). The
 * implementation must NOT swallow {@link AuditEvent} write failures: an audit INSERT failure is
 * the terminating signal that rolls back the entire ORG transaction (organization status,
 * membership barrier, token revocations, idempotency result and partial audit rows). The audit
 * row is append-only; no update or delete path exists.
 */
public interface AuditWriter {

    /**
     * Inserts the audit row. Throws on any failure so the surrounding transaction rolls back.
     *
     * @param event typed, validated audit event; never {@code null}
     */
    void write(AuditEvent event);
}
