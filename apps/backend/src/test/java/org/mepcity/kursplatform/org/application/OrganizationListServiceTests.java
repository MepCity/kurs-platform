package org.mepcity.kursplatform.org.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;

class OrganizationListServiceTests {
    @Test
    void organizationScopeRejectsAnyListParameterBeforeReadingAnotherTenant() {
        UUID actor = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        OrganizationRepository repository = new OrganizationRepository() {
            @Override public Organization create(Organization value) { throw new UnsupportedOperationException(); }
            @Override public Optional<Organization> findById(UUID id) { return Optional.empty(); }
            @Override public Optional<Organization> findByIdForUpdate(UUID id) { return Optional.empty(); }
            @Override public Optional<Organization> updateIdentity(Organization value) { return Optional.empty(); }
            @Override public Optional<Organization> transitionStatus(UUID id, OrganizationStatus expected, OrganizationStatus next, int version, UUID user) { return Optional.empty(); }
            @Override public Optional<Organization> updateBrand(UUID id, String primary, String secondary, int version, UUID user) { return Optional.empty(); }
            @Override public List<Organization> list(OrganizationListQuery query) { throw new AssertionError("tenant list must not run"); }
        };
        ActiveSessionResolver sessions = token -> CredentialResolution.platformAccess(ActiveSession.organization(actor, organization));
        OrganizationListService service = new OrganizationListService(repository, sessions, event -> { }, directTransaction(), cursorCodec(), user -> { }, clock());
        var query = new OrganizationListService.Query(null, null, OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 50, null, true);

        assertThatThrownBy(() -> service.list("token", query, "request")).isInstanceOf(OrganizationListValidationException.class);
    }

    @Test
    void globalPageUsesTypedNamePositionAndProducesBoundCursor() {
        UUID actor = UUID.randomUUID();
        Organization first = organization("A", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Organization second = organization("B", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        java.util.concurrent.atomic.AtomicReference<OrganizationListQuery> seen = new java.util.concurrent.atomic.AtomicReference<>();
        OrganizationRepository repository = new StubRepository() {
            @Override public List<Organization> list(OrganizationListQuery query) { seen.set(query); return List.of(first, second); }
        };
        ActiveSessionResolver sessions = token -> CredentialResolution.platformAccess(ActiveSession.globalPlatformAdmin(actor));
        OrganizationListService service = new OrganizationListService(repository, sessions, event -> { }, directTransaction(), cursorCodec(), user -> { }, clock());
        var result = service.list("token", new OrganizationListService.Query(null, null, OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 1, null, false), "request");

        assertThat(seen.get().position()).isNull();
        assertThat(result.items()).containsExactly(first);
        assertThat(result.hasNextPage()).isTrue();
        assertThat(result.nextCursor()).isNotBlank();
    }

    private static OrganizationListTransaction directTransaction() { return new OrganizationListTransaction() {
        @Override public <T> T execute(UUID actor, Scope scope, UUID organization, java.util.function.Supplier<T> action) { return action.get(); }
    }; }
    private static OrganizationListCursorCodec cursorCodec() { return new org.mepcity.kursplatform.org.infrastructure.persistence.AesGcmOrganizationListCursorCodec("test", "test-list-cursor-secret-with-at-least-32", null, null, clock(), new java.security.SecureRandom()); }
    private static Clock clock() { return Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC); }
    private static Organization organization(String name, UUID id) { Instant now = Instant.parse("2026-07-27T12:00:00Z"); return new Organization(id, name, null, null, null, OrganizationStatus.ACTIVE, "Europe/Istanbul", now, now, 1, UUID.randomUUID(), UUID.randomUUID()); }
    private abstract static class StubRepository implements OrganizationRepository {
        @Override public Organization create(Organization value) { throw new UnsupportedOperationException(); }
        @Override public Optional<Organization> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<Organization> findByIdForUpdate(UUID id) { return Optional.empty(); }
        @Override public Optional<Organization> updateIdentity(Organization value) { return Optional.empty(); }
        @Override public Optional<Organization> transitionStatus(UUID id, OrganizationStatus expected, OrganizationStatus next, int version, UUID user) { return Optional.empty(); }
        @Override public Optional<Organization> updateBrand(UUID id, String primary, String secondary, int version, UUID user) { return Optional.empty(); }
    }
}
