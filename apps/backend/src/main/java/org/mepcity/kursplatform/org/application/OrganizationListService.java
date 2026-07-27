package org.mepcity.kursplatform.org.application;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Read transaction which makes PostgreSQL RLS and access audit inseparable. */
public final class OrganizationListService {
    private static final Duration CURSOR_TTL = Duration.ofMinutes(15);
    private final OrganizationRepository organizations; private final ActiveSessionResolver sessions;
    private final AuditWriter audits; private final DataSource dataSource; private final TransactionTemplate tx;
    private final Clock clock; private final byte[] cursorKey;
    public OrganizationListService(OrganizationRepository organizations, ActiveSessionResolver sessions, AuditWriter audits,
            DataSource dataSource, PlatformTransactionManager manager, Clock clock) {
        this.organizations=organizations; this.sessions=sessions; this.audits=audits; this.dataSource=dataSource;
        this.tx=new TransactionTemplate(manager); this.clock=clock; this.cursorKey=new byte[32]; new SecureRandom().nextBytes(cursorKey);
    }
    public Result list(String token, Query query, String requestId) {
        CredentialResolution credential;
        try { credential=sessions.resolveCredential(token); } catch (CredentialAuthenticationException ex) { throw new OrganizationAuthenticationException(); }
        if (credential.kind()==CredentialResolution.Kind.CONTEXT_SELECTION) throw new OrganizationContextRequiredException();
        ActiveSession actor=credential.activeSession();
        if (!actor.isGlobalPlatformAdmin() && query.supplied()) throw new OrganizationListValidationException();
        return tx.execute(s -> actor.isGlobalPlatformAdmin() ? global(actor, query, requestId) : organization(actor, requestId));
    }
    private Result global(ActiveSession actor, Query query, String requestId) {
        Cursor decoded = query.cursor == null ? null : decode(query.cursor, actor.userId(), query);
        context(actor.userId(), null, "GLOBAL");
        List<Organization> rows=organizations.list(new OrganizationListQuery(query.status, query.search, query.sort, query.order,
                query.limit + 1, decoded == null ? null : decoded.lastValue(), decoded == null ? null : decoded.lastId()));
        boolean hasNext=rows.size()>query.limit; List<Organization> items=hasNext ? rows.subList(0, query.limit) : rows;
        for (Organization organization: items) audits.write(new AuditEvent.Factory(requestId).platformAdminOrgAccess(UUID.randomUUID(),
                organization.id(), actor.userId(), organization.id(), "ORG_LIST", "ALLOWED", null));
        String next=null;
        if (hasNext) { Organization last=items.getLast(); next=encode(actor.userId(), query, last); }
        return new Result(List.copyOf(items), next, hasNext);
    }
    private Result organization(ActiveSession actor, String requestId) {
        context(actor.userId(), actor.organizationId(), "ORGANIZATION");
        Organization organization=organizations.findById(actor.organizationId()).orElseThrow(ForbiddenException::new);
        if (!organization.isActive()) throw new ForbiddenException();
        return new Result(List.of(organization), null, false);
    }
    private void context(UUID actor, UUID organization, String scope) {
        try (var st=DataSourceUtils.getConnection(dataSource).createStatement()) {
            st.execute("SET LOCAL ROLE org_runtime"); set("app.iam_operation_scope",scope); set("app.iam_actor_user_id",actor.toString());
            if (organization!=null) set("app.organization_id",organization.toString()); set("app.iam_operation_code","ORG_LIST");
        } catch (Exception e) { throw new OrganizationPersistenceStateException("Listeleme RLS bağlamı kurulamadı",e); }
    }
    private void set(String k,String v) throws Exception { try (var p=DataSourceUtils.getConnection(dataSource).prepareStatement("SELECT set_config(?, ?, true)")) { p.setString(1,k);p.setString(2,v);p.execute(); } }
    private String encode(UUID actor, Query q, Organization last) {
        String value=q.sort==OrganizationListQuery.Sort.NAME ? last.name() : last.createdAt().toString();
        String raw=String.join("|", actor.toString(), q.status==null?"":q.status.name(), q.search==null?"":q.search, q.sort.name(),q.order.name(),Integer.toString(q.limit),value,last.id().toString(),Long.toString(clock.instant().plus(CURSOR_TTL).getEpochSecond()));
        String body=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8)); return body+"."+signature(body);
    }
    private Cursor decode(String token, UUID actor, Query q) {
        String[] parts=token.split("\\.",-1); if(parts.length!=2 || !constant(parts[1],signature(parts[0]))) throw new InvalidCursorException();
        try { String[] a=new String(java.util.Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8).split("\\|",-1);
            if(a.length!=9 || !a[0].equals(actor.toString()) || !a[1].equals(q.status==null?"":q.status.name()) || !a[2].equals(q.search==null?"":q.search) || !a[3].equals(q.sort.name()) || !a[4].equals(q.order.name()) || Integer.parseInt(a[5])!=q.limit || Instant.ofEpochSecond(Long.parseLong(a[8])).isBefore(clock.instant())) throw new InvalidCursorException();
            return new Cursor(q.sort==OrganizationListQuery.Sort.NAME?a[6]:Instant.parse(a[6]),UUID.fromString(a[7]));
        } catch (RuntimeException e) { throw new InvalidCursorException(); }
    }
    private String signature(String value) { try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(cursorKey,"HmacSHA256"));return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII))); } catch(Exception e){throw new IllegalStateException(e);} }
    private static boolean constant(String a,String b){return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));}
    private record Cursor(Object lastValue, UUID lastId) {}
    public record Query(OrganizationStatus status,String search,OrganizationListQuery.Sort sort,OrganizationListQuery.Order order,int limit,String cursor,boolean supplied) {}
    public record Result(List<Organization> items,String nextCursor,boolean hasNextPage) {}
}
