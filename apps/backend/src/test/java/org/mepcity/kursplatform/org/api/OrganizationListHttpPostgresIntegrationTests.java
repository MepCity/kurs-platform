package org.mepcity.kursplatform.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.org.application.OrganizationListTransaction;
import org.mepcity.kursplatform.org.infrastructure.OrganizationRateLimitProperties;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcOrganizationListRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real HTTP → production controller → service → transaction → repository/rate-limit/audit →
 * {@code iam_runtime} DataSource → {@code SET LOCAL ROLE org_runtime} → PostgreSQL acceptance
 * tests for ORG_LIST.
 */
@ActiveProfiles("local-stub")
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "KURS_PLATFORM_ENVIRONMENT=development",
            "KURS_PLATFORM_PUBLIC_API_BASE_URL=https://api-development.example.invalid",
            "KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
            "KURS_PLATFORM_COGNITO_CLIENT_ID=examplepublicclientid",
            "KURS_PLATFORM_DATABASE_URL_SECRET_REF=development/platform/database-url",
            "KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=development/platform/iam-token-pepper",
            "KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF=development/platform/iam-secret-delivery-key",
            "KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF=development/platform/cognito-admin-role",
            "org.create.rate-limit.list-limit=20",
            "org.create.rate-limit.list-window=PT1H"
        })
@AutoConfigureMockMvc
@Import(OrganizationListHttpPostgresIntegrationTests.TestAuthenticationConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationListHttpPostgresIntegrationTests {

    private static final String RUNTIME_PASSWORD = "list-runtime-password";
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection owner = ownerConnection()) {
            owner.createStatement()
                    .execute("ALTER ROLE iam_runtime PASSWORD 'list-runtime-password'");
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;
    @Autowired private OrganizationListTransaction listTransaction;
    @Autowired private OrganizationRateLimitProperties rateLimitProperties;
    @Autowired private TestCredentialResolver credentials;

    @Autowired
    @Qualifier("organizationListOwnerJdbc")
    private JdbcTemplate owner;

    private UUID admin;
    private UUID otherAdmin;
    private UUID organizationActor;
    private UUID ownOrganization;
    private UUID otherOrganization;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "iam_runtime");
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
    }

    @BeforeEach
    void resetDatabaseAndSeedActors() {
        owner.execute("TRUNCATE TABLE users CASCADE");
        createRoleProbe();
        owner.execute("TRUNCATE TABLE org_list_test_role_probe");
        owner.execute("GRANT INSERT ON org_list_test_role_probe TO org_runtime");
        owner.execute("GRANT INSERT ON audit_logs TO org_runtime");

        admin = insertUser();
        otherAdmin = insertUser();
        organizationActor = insertUser();
        ownOrganization = insertOrganization("Kendi Etkin Kurum", "KEK", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));
        otherOrganization = insertOrganization("Başka Kurum", "BK", "ACTIVE", Instant.parse("2026-01-02T00:00:00Z"));
        insertPlatformAdministrator(admin, false);
        insertPlatformAdministrator(otherAdmin, false);
        insertMembershipWithRole(organizationActor, ownOrganization, "ACTIVE", false);

        credentials.clear();
        credentials.platformAdmin("global", admin);
        credentials.platformAdmin("other-global", otherAdmin);
        credentials.organization("organization", organizationActor, ownOrganization);
        credentials.contextSelection("context-selection");
    }

    @Test
    void activeGlobalAdminListsThroughIamRuntimeAndOrgRuntimeAndAuditsEveryResult()
            throws Exception {
        assertThat(owner.queryForObject("SELECT session_user", String.class))
                .isEqualTo(POSTGRES.getUsername());
        try (Connection runtime = dataSource.getConnection()) {
            assertThat(queryString(runtime, "SELECT session_user")).isEqualTo("iam_runtime");
        }

        MvcResult response =
                        mvc.perform(
                                get("/api/v1/organizations")
                                        .header("Authorization", bearer("global"))
                                        .header("X-Request-Id", "global-list-request"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(2))
                        .andReturn();

        List<UUID> returned = itemIds(response);
        assertThat(returned).containsExactlyInAnyOrder(ownOrganization, otherOrganization);
        assertThat(
                        owner.queryForList(
                                """
                                SELECT organization_id, actor_user_id, target_entity_id,
                                       request_id, event_metadata->>'operationCode' AS operation_code
                                FROM audit_logs
                                WHERE action_type = 'PLATFORM_ADMIN_ORG_ACCESS'
                                ORDER BY organization_id
                                """))
                .allSatisfy(
                        row -> {
                            assertThat(row.get("actor_user_id")).isEqualTo(admin);
                            assertThat(row.get("organization_id")).isIn(returned);
                            assertThat(row.get("target_entity_id")).isEqualTo(row.get("organization_id"));
                            assertThat(row.get("request_id")).isEqualTo("global-list-request");
                            assertThat(row.get("operation_code")).isEqualTo("ORG_LIST");
                        })
                .hasSize(2);
        assertThat(
                        owner.queryForMap(
                                """
                                SELECT role_name, session_name
                                FROM org_list_test_role_probe
                                WHERE request_id = 'global-list-request'
                                LIMIT 1
                                """))
                .containsEntry("role_name", "org_runtime")
                .containsEntry("session_name", "iam_runtime");
    }

    @Test
    void revokedGlobalAdminAndInvalidCredentialsFailClosed() throws Exception {
        UUID revokedAdmin = insertUser();
        insertPlatformAdministrator(revokedAdmin, true);
        credentials.platformAdmin("revoked-global", revokedAdmin);

        mvc.perform(get("/api/v1/organizations").header("Authorization", bearer("revoked-global")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("context-selection")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_CONTEXT_REQUIRED"));
        mvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/organizations").header("Authorization", "Basic value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/organizations").header("Authorization", bearer("unknown")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void organizationScopeReturnsOnlyItsOwnActiveOrganizationWithoutAudit() throws Exception {
        mvc.perform(get("/api/v1/organizations").header("Authorization", bearer("organization")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ownOrganization.toString()))
                .andExpect(jsonPath("$.page.nextCursor").isEmpty())
                .andExpect(jsonPath("$.page.hasNextPage").value(false));

        assertThat(
                        owner.queryForObject(
                                "SELECT count(*) FROM audit_logs WHERE action_type='PLATFORM_ADMIN_ORG_ACCESS'",
                                Long.class))
                .isZero();
    }

    @ParameterizedTest(name = "organization scope rejects {0}")
    @MethodSource("organizationScopeParameters")
    void organizationScopeRejectsEveryListParameter(String parameter, String value) throws Exception {
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("organization"))
                                .param(parameter, value))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    Stream<Arguments> organizationScopeParameters() {
        return Stream.of(
                Arguments.of("status", "ACTIVE"),
                Arguments.of("search", "kendi"),
                Arguments.of("sort", "createdAt"),
                Arguments.of("order", "DESC"),
                Arguments.of("limit", "1"),
                Arguments.of("cursor", "anything"));
    }

    @Test
    void suspendedArchivedAndRevokedMembershipOrRoleOrganizationActorsAreRejected()
            throws Exception {
        UUID suspended = insertOrganization("Askıdaki", "ASK", "SUSPENDED", Instant.now());
        UUID archived = insertOrganization("Arşivde", "ARS", "ARCHIVED", Instant.now());
        UUID suspendedActor = insertUser();
        UUID archivedActor = insertUser();
        UUID passiveActor = insertUser();
        UUID revokedRoleActor = insertUser();
        insertMembershipWithRole(suspendedActor, suspended, "ACTIVE", false);
        insertMembershipWithRole(archivedActor, archived, "ACTIVE", false);
        insertMembershipWithRole(passiveActor, ownOrganization, "SUSPENDED", false);
        insertMembershipWithRole(revokedRoleActor, otherOrganization, "ACTIVE", true);
        credentials.organization("suspended-org", suspendedActor, suspended);
        credentials.organization("archived-org", archivedActor, archived);
        credentials.organization("passive-membership", passiveActor, ownOrganization);
        credentials.organization("revoked-role", revokedRoleActor, otherOrganization);

        for (String token :
                List.of("suspended-org", "archived-org", "passive-membership", "revoked-role")) {
            mvc.perform(get("/api/v1/organizations").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void postgresStatusTurkishSearchShortNameAndLikeEscapesMatchJavaNormalization()
            throws Exception {
        insertOrganization("İstanbul Eğitim", "İST", "ACTIVE", Instant.parse("2026-02-01T00:00:00Z"));
        insertOrganization("Iğdır Eğitim", "IGD", "SUSPENDED", Instant.parse("2026-02-02T00:00:00Z"));
        insertOrganization("Arşiv Eğitim", "KISA-ARANAN", "ARCHIVED", Instant.parse("2026-02-03T00:00:00Z"));
        insertOrganization("Yüzde % Kursu", "YUZ", "ACTIVE", Instant.parse("2026-02-04T00:00:00Z"));
        insertOrganization("Alt_Çizgi", "ALT", "ACTIVE", Instant.parse("2026-02-05T00:00:00Z"));
        insertOrganization("Ters\\Slash", "TERS", "ACTIVE", Instant.parse("2026-02-06T00:00:00Z"));

        for (String search : List.of("İstanbul", "istanbul", "İSTANBUL")) {
            assertSearchReturns(search, "İstanbul Eğitim");
        }
        for (String search : List.of("Iğdır", "IĞDIR", "ığdır")) {
            assertSearchReturns(search, "Iğdır Eğitim");
        }
        assertSearchReturns("kısa-aranan", "Arşiv Eğitim");
        assertSearchReturns("%", "Yüzde % Kursu");
        assertSearchReturns("_", "Alt_Çizgi");
        assertSearchReturns("\\", "Ters\\Slash");

        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("global"))
                                .param("search", " \t "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(8));
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("global"))
                                .param("search", "a".repeat(200)))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("global"))
                                .param("search", "a".repeat(201)))
                .andExpect(status().isUnprocessableEntity());

        assertStatusResults("ACTIVE", 6);
        assertStatusResults("SUSPENDED", 1);
        assertStatusResults("ARCHIVED", 1);
    }

    @ParameterizedTest(name = "{0} {1} keyset with limit={2}")
    @MethodSource("keysetCases")
    void nameAndCreatedAtKeysetsConsumeEveryRowWithoutDuplicateOrSkip(
            String sort, String order, int limit) throws Exception {
        seedKeysetTies();

        List<UUID> expected = expectedIds(sort, order, null);
        PageWalk walk = consumeAllPages(sort, order, limit, null);

        assertThat(walk.ids()).containsExactlyElementsOf(expected);
        assertThat(new HashSet<>(walk.ids())).hasSize(expected.size());
        assertThat(walk.intermediateCursorCount()).isPositive();
        assertThat(walk.lastCursor()).isNull();
        assertThat(walk.lastHasNext()).isFalse();
    }

    Stream<Arguments> keysetCases() {
        return Stream.of("name", "createdAt")
                .flatMap(
                        sort ->
                                Stream.of("ASC", "DESC")
                                        .flatMap(
                                                order ->
                                                        Stream.of(1, 2)
                                                                .map(limit -> Arguments.of(sort, order, limit))));
    }

    @ParameterizedTest(name = "{0} {1} keyset tolerates insertion and archive")
    @MethodSource("sortAndOrderCases")
    void keysetsRemainStableWhenRowsAreInsertedAndArchivedBetweenPages(
            String sort, String order) throws Exception {
        seedKeysetTies();
        List<UUID> initial = expectedIds(sort, order, "ACTIVE");
        Page first = page(sort, order, 2, null, "ACTIVE");
        UUID archived = initial.get(initial.size() - 1);
        owner.update("UPDATE organizations SET status='ARCHIVED' WHERE id=?", archived);
        UUID inserted = insertAfterCursor(sort, order);

        List<UUID> collected = new ArrayList<>(first.ids());
        String cursor = first.nextCursor();
        boolean hasNext = first.hasNext();
        while (hasNext) {
            Page next = page(sort, order, 2, cursor, "ACTIVE");
            collected.addAll(next.ids());
            cursor = next.nextCursor();
            hasNext = next.hasNext();
        }

        List<UUID> expected = expectedIds(sort, order, "ACTIVE");
        assertThat(expected).contains(inserted).doesNotContain(archived);
        assertThat(collected).containsExactlyElementsOf(expected);
        assertThat(new HashSet<>(collected)).hasSize(collected.size());
        assertThat(cursor).isNull();
    }

    Stream<Arguments> sortAndOrderCases() {
        return Stream.of(
                Arguments.of("name", "ASC"),
                Arguments.of("name", "DESC"),
                Arguments.of("createdAt", "ASC"),
                Arguments.of("createdAt", "DESC"));
    }

    @Test
    void httpCursorIsBoundToActorStatusSearchSortOrderAndLimitAndRejectsMalformedTokens()
            throws Exception {
        seedKeysetTies();
        Page first = page("name", "ASC", 1, null, "ACTIVE", "tie");
        String cursor = first.nextCursor();
        assertThat(cursor).isNotBlank();

        assertInvalidCursor("other-global", "ACTIVE", "tie", "name", "ASC", "1", cursor);
        assertInvalidCursor("global", "SUSPENDED", "tie", "name", "ASC", "1", cursor);
        assertInvalidCursor("global", "ACTIVE", "other", "name", "ASC", "1", cursor);
        assertInvalidCursor("global", "ACTIVE", "tie", "createdAt", "ASC", "1", cursor);
        assertInvalidCursor("global", "ACTIVE", "tie", "name", "DESC", "1", cursor);
        assertInvalidCursor("global", "ACTIVE", "tie", "name", "ASC", "2", cursor);

        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("organization"))
                                .param("cursor", cursor))
                .andExpect(status().isUnprocessableEntity());
        for (String invalid :
                List.of(
                        cursor.substring(0, cursor.length() - 1),
                        "v1.%%%",
                        "unknown." + cursor.substring(cursor.indexOf('.') + 1),
                        "x".repeat(4097))) {
            assertInvalidCursor("global", "ACTIVE", "tie", "name", "ASC", "1", invalid);
        }
    }

    @Test
    void postgresRateLimitReturnsSafe429AndTwoInstancesShareActorQuota() throws Exception {
        assertThat(rateLimitProperties.getListLimit()).isEqualTo(20);
        for (int request = 1; request <= 20; request++) {
            mvc.perform(
                            get("/api/v1/organizations")
                                    .header("Authorization", bearer("global"))
                                    .header("X-Request-Id", "quota-" + request))
                    .andExpect(status().isOk());
        }
        assertThat(
                        owner.queryForObject(
                                "SELECT request_count FROM organization_list_rate_limits WHERE actor_user_id=?",
                                Integer.class,
                                admin))
                .isEqualTo(20);
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("global"))
                                .header("X-Request-Id", "quota-blocked"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.requestId").value("quota-blocked"));

        mvc.perform(get("/api/v1/organizations").header("Authorization", bearer("other-global")))
                .andExpect(status().isOk());

        clearRateLimits();
        JdbcOrganizationListRateLimiter first =
                new JdbcOrganizationListRateLimiter(dataSource, 1, java.time.Duration.ofMinutes(1));
        JdbcOrganizationListRateLimiter second =
                new JdbcOrganizationListRateLimiter(dataSource, 1, java.time.Duration.ofMinutes(1));
        listTransaction.execute(
                admin,
                OrganizationListTransaction.Scope.GLOBAL,
                null,
                () -> {
                    first.check(admin);
                    return null;
                });
        assertThatThrownBy(
                        () ->
                                listTransaction.execute(
                                        admin,
                                        OrganizationListTransaction.Scope.GLOBAL,
                                        null,
                                        () -> {
                                            second.check(admin);
                                            return null;
                                        }))
                .isInstanceOf(org.mepcity.kursplatform.org.application.RateLimitExceededException.class);
    }

    @Test
    void quotaRlsRejectsWrongGucsOtherActorsAndDirectRuntimeRoles() throws Exception {
        clearRateLimits();
        mvc.perform(get("/api/v1/organizations").header("Authorization", bearer("global")))
                .andExpect(status().isOk());

        assertQuotaRowsHidden("GLOBAL", "WRONG", admin);
        assertQuotaRowsHidden("IAM_AUTH", "ORG_LIST", admin);
        assertQuotaRowsHidden("GLOBAL", "ORG_LIST", otherAdmin);
        assertRuntimeSqlDenied("SELECT * FROM organizations");
        assertRuntimeSqlDenied("SELECT * FROM organization_list_rate_limits");

        try (Connection ownerConnection = ownerConnection()) {
            ownerConnection.setAutoCommit(false);
            ownerConnection.createStatement().execute("SET LOCAL ROLE app_runtime");
            assertThatThrownBy(
                            () ->
                                    ownerConnection
                                            .createStatement()
                                            .executeQuery("SELECT * FROM organization_list_rate_limits"))
                    .isInstanceOf(SQLException.class);
            ownerConnection.rollback();
        }

        Map<String, Object> policy =
                owner.queryForMap(
                        """
                        SELECT relrowsecurity, relforcerowsecurity,
                               has_table_privilege('org_runtime',
                                   'organization_list_rate_limits','SELECT') AS org_select,
                               has_table_privilege('iam_runtime',
                                   'organization_list_rate_limits','SELECT') AS iam_select,
                               has_table_privilege('app_runtime',
                                   'organization_list_rate_limits','SELECT') AS app_select
                        FROM pg_class
                        WHERE relname='organization_list_rate_limits'
                        """);
        assertThat(policy)
                .containsEntry("relrowsecurity", true)
                .containsEntry("relforcerowsecurity", true)
                .containsEntry("org_select", true)
                .containsEntry("iam_select", false)
                .containsEntry("app_select", false);
    }

    @Test
    void auditFailureReturns500AndRollsBackAuditAndRateLimitAtomically() throws Exception {
        owner.execute(
                """
                CREATE OR REPLACE FUNCTION fail_org_list_audit_for_test()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF NEW.request_id = 'audit-fail' THEN
                        RAISE EXCEPTION 'forced audit failure';
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        owner.execute("DROP TRIGGER IF EXISTS fail_org_list_audit_for_test ON audit_logs");
        owner.execute(
                """
                CREATE TRIGGER fail_org_list_audit_for_test
                BEFORE INSERT ON audit_logs
                FOR EACH ROW EXECUTE FUNCTION fail_org_list_audit_for_test()
                """);

        try {
            mvc.perform(
                            get("/api/v1/organizations")
                                    .header("Authorization", bearer("global"))
                                    .header("X-Request-Id", "audit-fail"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
        } finally {
            owner.execute("DROP TRIGGER IF EXISTS fail_org_list_audit_for_test ON audit_logs");
        }

        assertThat(
                        owner.queryForObject(
                                "SELECT count(*) FROM audit_logs WHERE request_id='audit-fail'",
                                Long.class))
                .isZero();
        assertThat(
                        owner.queryForObject(
                                "SELECT count(*) FROM organization_list_rate_limits WHERE actor_user_id=?",
                                Long.class,
                                admin))
                .isZero();
    }

    private void assertSearchReturns(String search, String expectedName) throws Exception {
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("global"))
                                .param("search", search))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value(expectedName));
    }

    private void assertStatusResults(String statusValue, int expectedCount) throws Exception {
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer("global"))
                                .param("status", statusValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(expectedCount));
    }

    private void assertInvalidCursor(
            String token,
            String statusValue,
            String search,
            String sort,
            String order,
            String limit,
            String cursor)
            throws Exception {
        mvc.perform(
                        get("/api/v1/organizations")
                                .header("Authorization", bearer(token))
                                .param("status", statusValue)
                                .param("search", search)
                                .param("sort", sort)
                                .param("order", order)
                                .param("limit", limit)
                                .param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
    }

    private void seedKeysetTies() {
        Instant tie = Instant.parse("2026-03-01T12:00:00Z");
        insertOrganization("Tie Kurum", "T1", "ACTIVE", tie);
        insertOrganization("Tie Kurum", "T2", "ACTIVE", tie);
        insertOrganization("Tie Kurum", "T3", "ACTIVE", tie);
        insertOrganization("Önce Kurum", "ON", "ACTIVE", Instant.parse("2026-02-01T12:00:00Z"));
        insertOrganization("Sonra Kurum", "SO", "ACTIVE", Instant.parse("2026-04-01T12:00:00Z"));
    }

    private List<UUID> expectedIds(String sort, String order, String status) {
        String column = sort.equals("name") ? "name" : "created_at";
        String where = status == null ? "" : " WHERE status='" + status + "'";
        return owner.query(
                "SELECT id FROM organizations"
                        + where
                        + " ORDER BY "
                        + column
                        + " "
                        + order
                        + ", id ASC",
                (result, row) -> result.getObject(1, UUID.class));
    }

    private PageWalk consumeAllPages(String sort, String order, int limit, String status)
            throws Exception {
        List<UUID> ids = new ArrayList<>();
        String cursor = null;
        int intermediate = 0;
        boolean hasNext;
        do {
            Page page = page(sort, order, limit, cursor, status);
            ids.addAll(page.ids());
            cursor = page.nextCursor();
            hasNext = page.hasNext();
            if (hasNext) {
                intermediate++;
                assertThat(cursor).isNotBlank();
            }
        } while (hasNext);
        return new PageWalk(ids, intermediate, cursor, false);
    }

    private Page page(String sort, String order, int limit, String cursor, String status)
            throws Exception {
        return page(sort, order, limit, cursor, status, null);
    }

    private Page page(
            String sort, String order, int limit, String cursor, String status, String search)
            throws Exception {
        var request =
                get("/api/v1/organizations")
                        .header("Authorization", bearer("global"))
                        .param("sort", sort)
                        .param("order", order)
                        .param("limit", Integer.toString(limit));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        if (status != null) {
            request.param("status", status);
        }
        if (search != null) {
            request.param("search", search);
        }
        MvcResult result = mvc.perform(request).andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError(
                    "ORG_LIST page failed with " + result.getResponse().getContentAsString(),
                    result.getResolvedException());
        }
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<UUID> ids = new ArrayList<>();
        body.path("items").forEach(item -> ids.add(UUID.fromString(item.path("id").asText())));
        JsonNode cursorNode = body.path("page").path("nextCursor");
        return new Page(
                ids,
                cursorNode.isNull() ? null : cursorNode.asText(),
                body.path("page").path("hasNextPage").asBoolean());
    }

    private UUID insertAfterCursor(String sort, String order) {
        if (sort.equals("name")) {
            String name = order.equals("ASC") ? "ZZZ Yeni Kurum" : "000 Yeni Kurum";
            return insertOrganization(name, "NEW", "ACTIVE", Instant.parse("2026-03-15T00:00:00Z"));
        }
        Instant createdAt =
                order.equals("ASC")
                        ? Instant.parse("2027-01-01T00:00:00Z")
                        : Instant.parse("2025-01-01T00:00:00Z");
        return insertOrganization("Yeni Tarihli", "NEW", "ACTIVE", createdAt);
    }

    private List<UUID> itemIds(MvcResult result) throws Exception {
        List<UUID> ids = new ArrayList<>();
        objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("items")
                .forEach(item -> ids.add(UUID.fromString(item.path("id").asText())));
        return ids;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO users(id,status) VALUES (?,'ACTIVE')", id);
        return id;
    }

    private void insertPlatformAdministrator(UUID actor, boolean revoked) {
        owner.update(
                """
                INSERT INTO platform_administrators(id,user_id,granted_at,revoked_at)
                VALUES (?,?,transaction_timestamp(),
                        CASE WHEN ? THEN transaction_timestamp() ELSE NULL END)
                """,
                UUID.randomUUID(),
                actor,
                revoked);
    }

    private UUID insertOrganization(
            String name, String shortName, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        owner.update(
                """
                INSERT INTO organizations(
                    id,name,short_name,status,default_timezone,created_at,updated_at,row_version,
                    created_by_user_id,updated_by_user_id)
                VALUES (?,?,?,?::organization_status_enum,'Europe/Istanbul',?,?,1,?,?)
                """,
                id,
                name,
                shortName,
                status,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                admin,
                admin);
        return id;
    }

    private void insertMembershipWithRole(
            UUID actor, UUID organization, String membershipStatus, boolean revokedRole) {
        UUID person = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        owner.update(
                """
                INSERT INTO people(id,organization_id,first_name,last_name,phone)
                VALUES (?,?,'Test','Actor','000')
                """,
                person,
                organization);
        owner.update(
                """
                INSERT INTO organization_memberships(
                    id,organization_id,user_id,person_id,status,granted_at)
                VALUES (?,?,?,?,?::user_status_enum,transaction_timestamp())
                """,
                membership,
                organization,
                actor,
                person,
                membershipStatus);
        owner.update(
                """
                INSERT INTO organization_membership_roles(
                    id,organization_membership_id,organization_id,role,granted_at,revoked_at)
                VALUES (?,?,?,'ORG_ADMIN',transaction_timestamp(),
                        CASE WHEN ? THEN transaction_timestamp() ELSE NULL END)
                """,
                UUID.randomUUID(),
                membership,
                organization,
                revokedRole);
    }

    private void clearRateLimits() {
        owner.execute("TRUNCATE TABLE organization_list_rate_limits");
    }

    private void createRoleProbe() {
        owner.execute(
                """
                CREATE TABLE IF NOT EXISTS org_list_test_role_probe(
                    request_id TEXT NOT NULL,
                    role_name TEXT NOT NULL,
                    session_name TEXT NOT NULL
                )
                """);
        owner.execute(
                """
                CREATE OR REPLACE FUNCTION capture_org_list_role_for_test()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    INSERT INTO org_list_test_role_probe(request_id,role_name,session_name)
                    VALUES (NEW.request_id,current_user,session_user);
                    RETURN NEW;
                END
                $$
                """);
        owner.execute("DROP TRIGGER IF EXISTS capture_org_list_role_for_test ON audit_logs");
        owner.execute(
                """
                CREATE TRIGGER capture_org_list_role_for_test
                AFTER INSERT ON audit_logs
                FOR EACH ROW
                WHEN (NEW.action_type = 'PLATFORM_ADMIN_ORG_ACCESS')
                EXECUTE FUNCTION capture_org_list_role_for_test()
                """);
    }

    private void assertRuntimeSqlDenied(String sql) throws Exception {
        try (Connection runtime = dataSource.getConnection()) {
            runtime.setAutoCommit(false);
            assertThatThrownBy(() -> runtime.createStatement().execute(sql))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }
    }

    private void assertQuotaRowsHidden(String scope, String operation, UUID actor) throws Exception {
        try (Connection runtime = dataSource.getConnection()) {
            runtime.setAutoCommit(false);
            runtime.createStatement().execute("SET LOCAL ROLE org_runtime");
            setLocal(runtime, "app.iam_operation_scope", scope);
            setLocal(runtime, "app.iam_operation_code", operation);
            setLocal(runtime, "app.iam_actor_user_id", actor.toString());
            assertThat(queryLong(runtime, "SELECT count(*) FROM organization_list_rate_limits"))
                    .isZero();
            try (var update =
                    runtime.prepareStatement(
                            """
                            UPDATE organization_list_rate_limits
                            SET request_count=request_count+1
                            WHERE actor_user_id=?
                            """)) {
                update.setObject(1, admin);
                assertThat(update.executeUpdate()).isZero();
            }
            runtime.rollback();
        }
    }

    private static void setLocal(Connection connection, String key, String value)
            throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config(?,?,true)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record Page(List<UUID> ids, String nextCursor, boolean hasNext) {}

    private record PageWalk(
            List<UUID> ids,
            int intermediateCursorCount,
            String lastCursor,
            boolean lastHasNext) {}

    @TestConfiguration
    static class TestAuthenticationConfiguration {

        @Bean
        @Primary
        TestCredentialResolver organizationListTestCredentialResolver() {
            return new TestCredentialResolver();
        }

        @Bean("organizationListOwnerJdbc")
        JdbcTemplate organizationListOwnerJdbc() {
            DriverManagerDataSource ownerDataSource =
                    new DriverManagerDataSource(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            return new JdbcTemplate(ownerDataSource);
        }
    }

    static final class TestCredentialResolver implements ActiveSessionResolver {
        private final Map<String, CredentialResolution> resolutions = new HashMap<>();

        void clear() {
            resolutions.clear();
        }

        void platformAdmin(String token, UUID actor) {
            resolutions.put(
                    token,
                    CredentialResolution.platformAccess(ActiveSession.globalPlatformAdmin(actor)));
        }

        void organization(String token, UUID actor, UUID organization) {
            resolutions.put(
                    token,
                    CredentialResolution.platformAccess(
                            ActiveSession.organization(actor, organization)));
        }

        void contextSelection(String token) {
            resolutions.put(token, CredentialResolution.contextSelection());
        }

        @Override
        public CredentialResolution resolveCredential(String token) {
            CredentialResolution resolution = resolutions.get(token);
            if (resolution == null) {
                throw new CredentialAuthenticationException("UNAUTHENTICATED");
            }
            return resolution;
        }
    }
}
