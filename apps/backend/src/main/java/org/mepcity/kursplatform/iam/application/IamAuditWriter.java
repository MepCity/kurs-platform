package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.IamAuditEvent;

/**
 * Provider-independent port that persists {@link IamAuditEvent} rows. Implementations must
 * participate in the caller's existing transaction and must NOT swallow write failures — an audit
 * INSERT failure is the signal that rolls back the entire IAM_AUTH/GLOBAL mutation (context-token
 * consumption, family/token issuance, idempotency terminal write) alongside it. Mirrors the ORG
 * module's equivalent audit-writer contract.
 */
public interface IamAuditWriter {

    void write(IamAuditEvent event);
}
