package org.mepcity.kursplatform.org.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;

/** Framework-free list use case. JDBC, transactions, RLS and crypto stay behind ports. */
public final class OrganizationListService {
    private static final Duration CURSOR_TTL = Duration.ofMinutes(15);
    private final OrganizationRepository organizations;
    private final ActiveSessionResolver sessions;
    private final AuditWriter audits;
    private final OrganizationListTransaction transactions;
    private final OrganizationListCursorCodec cursors;
    private final OrganizationListRateLimiter rateLimiter;
    private final Clock clock;
    public OrganizationListService(OrganizationRepository organizations, ActiveSessionResolver sessions, AuditWriter audits,
            OrganizationListTransaction transactions, OrganizationListCursorCodec cursors, OrganizationListRateLimiter rateLimiter, Clock clock) {
        this.organizations = organizations;
        this.sessions = sessions;
        this.audits = audits;
        this.transactions = transactions;
        this.cursors = cursors;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }
    public Result list(String token, Query query, String requestId) {
        CredentialResolution credential;
        try { credential = sessions.resolveCredential(token); } catch (CredentialAuthenticationException e) { throw new OrganizationAuthenticationException(); }
        if (credential.kind() == CredentialResolution.Kind.CONTEXT_SELECTION) throw new OrganizationContextRequiredException();
        ActiveSession actor = credential.activeSession();
        if (!actor.isGlobalPlatformAdmin() && query.supplied()) throw new OrganizationListValidationException();
        return actor.isGlobalPlatformAdmin() ? global(actor, query, requestId) : organization(actor, query);
    }
    private Result global(ActiveSession actor, Query query, String requestId) {
        OrganizationListCursor.Context context = query.context(actor.userId(), OrganizationListTransaction.Scope.GLOBAL);
        OrganizationListCursor decoded = query.cursor() == null ? null : cursors.decode(query.cursor(), context);
        return transactions.execute(actor.userId(), OrganizationListTransaction.Scope.GLOBAL, null, () -> {
            rateLimiter.check(actor.userId());
            List<Organization> rows = organizations.list(query.repositoryQuery(decoded, query.limit() + 1));
            boolean hasNext = rows.size() > query.limit();
            List<Organization> items = hasNext ? rows.subList(0, query.limit()) : rows;
            for (Organization item : items) {
                audits.write(new AuditEvent.Factory(requestId).platformAdminOrgAccess(UUID.randomUUID(), item.id(), actor.userId(), item.id(), "ORG_LIST", "ALLOWED", null));
            }
            String next = hasNext ? cursors.encode(new OrganizationListCursor(context, query.lastValue(items.getLast()), items.getLast().id(), clock.instant().plus(CURSOR_TTL))) : null;
            return new Result(List.copyOf(items), next, hasNext);
        });
    }
    private Result organization(ActiveSession actor, Query query) {
        return transactions.execute(actor.userId(), OrganizationListTransaction.Scope.ORGANIZATION, actor.organizationId(), () -> {
            rateLimiter.check(actor.userId());
            Organization value = organizations.findById(actor.organizationId()).orElseThrow(ForbiddenException::new);
            if (!value.isActive()) throw new ForbiddenException();
            return new Result(List.of(value), null, false);
        });
    }
    public record Query(org.mepcity.kursplatform.org.domain.OrganizationStatus status,String search,OrganizationListQuery.Sort sort,OrganizationListQuery.Order order,int limit,String cursor,boolean supplied) {
        OrganizationListCursor.Context context(UUID actor, OrganizationListTransaction.Scope scope){return new OrganizationListCursor.Context(actor,scope,status,search,sort,order,limit);}
        OrganizationListQuery repositoryQuery(OrganizationListCursor cursor, int size) {
            OrganizationListQuery.Position position = cursor == null ? null : sort == OrganizationListQuery.Sort.NAME
                    ? new OrganizationListQuery.NamePosition(((OrganizationListCursor.Name) cursor.lastValue()).value(), cursor.lastOrganizationId())
                    : new OrganizationListQuery.CreatedAtPosition(((OrganizationListCursor.CreatedAt) cursor.lastValue()).value(), cursor.lastOrganizationId());
            return new OrganizationListQuery(status, search, sort, order, size, position);
        }
        OrganizationListCursor.LastValue lastValue(Organization organization){ return sort==OrganizationListQuery.Sort.NAME ? new OrganizationListCursor.Name(organization.name()) : new OrganizationListCursor.CreatedAt(organization.createdAt()); }
    }
    public record Result(List<Organization> items,String nextCursor,boolean hasNextPage) { }
}
