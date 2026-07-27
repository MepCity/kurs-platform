package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEventClaim;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

/** Transactional core; queue receipt handles never cross this application boundary. */
public final class CognitoSecurityEventService {
    private static final Duration LEASE_TTL = Duration.ofMinutes(2);
    private static final Duration INITIAL_MAPPING_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_MAPPING_BACKOFF = Duration.ofMinutes(5);

    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactions;
    private final IamAuditWriter audit;
    private final SecurityAlertSink alerts;
    private final Clock clock;
    private final String issuer;
    private final String userPoolId;

    public CognitoSecurityEventService(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            IamAuditWriter audit,
            SecurityAlertSink alerts,
            Clock clock,
            String issuer,
            String userPoolId) {
        this.repository = repository;
        this.transactions = transactions;
        this.audit = audit;
        this.alerts = alerts;
        this.clock = clock;
        this.issuer = issuer;
        this.userPoolId = userPoolId;
    }

    public CognitoEventProcessingResult ingest(CognitoSecurityEvent event, String workerId) {
        boolean inserted = transactions.executeInGlobalScope(
                OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> repository.recordCognitoSecurityEvent(event));
        var claim = claimExact(event, workerId);
        if (claim.isEmpty()) {
            return inserted
                    ? CognitoEventProcessingResult.PERSISTED_PENDING
                    : CognitoEventProcessingResult.ALREADY_PERSISTED;
        }
        return processClaim(claim.orElseThrow());
    }

    public boolean processNextPending(String workerId) {
        Instant now = clock.instant();
        var claim = transactions.executeInGlobalScope(
                OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> repository.claimNextCognitoSecurityEvent(
                        userPoolId, workerId, now, now.plus(LEASE_TTL)));
        claim.ifPresent(this::processClaim);
        return claim.isPresent();
    }

    private java.util.Optional<CognitoSecurityEventClaim> claimExact(
            CognitoSecurityEvent event, String workerId) {
        Instant now = clock.instant();
        return transactions.executeInGlobalScope(
                OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> repository.claimCognitoSecurityEvent(
                        event, workerId, now, now.plus(LEASE_TTL)));
    }

    private CognitoEventProcessingResult processClaim(CognitoSecurityEventClaim claim) {
        CognitoSecurityEvent event = claim.event();
        var identity = transactions.executeInAuthenticationScope(
                UUID.randomUUID(),
                issuer,
                event.subject(),
                () -> repository.findUserIdentityByIssuerAndSubject(issuer, event.subject()));
        if (identity.isEmpty()) {
            Instant nextAttemptAt = clock.instant().plus(mappingBackoff(claim.fencingToken()));
            transactions.executeInGlobalScope(
                    OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                    IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                    () -> {
                        repository.releaseCognitoSecurityEvent(claim, nextAttemptAt);
                        return null;
                    });
            emitBestEffort(new SecurityAlertSink.SecurityAlert(
                    SecurityAlertSink.Type.UNKNOWN_SUBJECT,
                    SecurityAlertSink.Severity.WARNING,
                    clock.instant(),
                    Map.of("eventId", event.eventId())));
            return CognitoEventProcessingResult.PERSISTED_PENDING;
        }
        completeMapped(claim, identity.orElseThrow());
        return CognitoEventProcessingResult.COMPLETED;
    }

    private void completeMapped(CognitoSecurityEventClaim claim, UserIdentity mapped) {
        transactions.executeInGlobalScope(
                OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(mapped.userId())
                        .withTargetUser(mapped.userId()),
                () -> {
                    UserIdentity revalidated = repository.revalidateCognitoEventSubject(
                                    issuer,
                                    claim.event().subject(),
                                    claim.event().userPoolId())
                            .filter(identity -> identity.userId().equals(mapped.userId()))
                            .orElseThrow(() -> new IllegalStateException(
                                    "Cognito subject mapping completion sırasında değişti."));
                    repository.revokeAllActorFamilies(
                            revalidated.userId(),
                            OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                            clock.instant());
                    repository.elevateUserReauthenticationRequiredAfter(
                            revalidated.userId(), clock.instant());
                    audit.write(new IamAuditEvent(
                            UUID.randomUUID(),
                            null,
                            mapped.userId(),
                            null,
                            "IAM_PROVIDER_SESSION_REVOKED",
                            IamAuditEvent.EventScope.GLOBAL,
                            "USER",
                            IamAuditEvent.EventKind.SECURITY,
                            mapped.userId(),
                            Map.of("providerStatus", "REVOKED"),
                            Map.of("operationCode",
                                    OperationCode.COGNITO_SECURITY_EVENT_PROCESS.name())));
                    repository.completeCognitoSecurityEvent(claim, clock.instant());
                    return null;
                });
    }

    private static Duration mappingBackoff(long attempt) {
        int exponent = (int) Math.min(Math.max(0, attempt - 1), 4);
        Duration calculated = INITIAL_MAPPING_BACKOFF.multipliedBy(1L << exponent);
        return calculated.compareTo(MAX_MAPPING_BACKOFF) > 0
                ? MAX_MAPPING_BACKOFF
                : calculated;
    }

    private void emitBestEffort(SecurityAlertSink.SecurityAlert alert) {
        try {
            alerts.emit(alert);
        } catch (RuntimeException ignored) {
            // Observability must not revoke durable queue ownership or cause a duplicate side effect.
        }
    }
}
