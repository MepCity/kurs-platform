package org.mepcity.kursplatform.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real MockMvc → production ORG list service/repository/RLS/audit chain on PostgreSQL. */
@ActiveProfiles("local-stub")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "KURS_PLATFORM_ENVIRONMENT=development",
        "KURS_PLATFORM_PUBLIC_API_BASE_URL=https://api-development.example.invalid", "KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE", "KURS_PLATFORM_COGNITO_CLIENT_ID=examplepublicclientid", "KURS_PLATFORM_DATABASE_URL_SECRET_REF=development/platform/database-url", "KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=development/platform/iam-token-pepper", "KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF=development/platform/iam-secret-delivery-key", "KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF=development/platform/cognito-admin-role"})
@AutoConfigureMockMvc @Import(OrganizationListHttpPostgresIntegrationTests.Auth.class) @Execution(ExecutionMode.SAME_THREAD)
class OrganizationListHttpPostgresIntegrationTests {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final AtomicReference<UUID> ADMIN = new AtomicReference<>();
    static final AtomicReference<UUID> ORG = new AtomicReference<>();
    static boolean migrated;
    @Autowired MockMvc mvc; @Autowired DataSource dataSource;
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { start(); r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", () -> "iam_runtime"); r.add("spring.datasource.password", () -> "list-runtime-password"); }
    @BeforeEach void seed() throws Exception { UUID admin=UUID.randomUUID(), org=UUID.randomUUID(); ADMIN.set(admin); ORG.set(org); try(Connection c=owner(); var s=c.createStatement()) { s.execute("INSERT INTO users(id,status) VALUES ('"+admin+"','ACTIVE'),('"+org+"','ACTIVE')"); s.execute("INSERT INTO platform_administrators(id,user_id,granted_at) VALUES ('"+UUID.randomUUID()+"','"+admin+"',transaction_timestamp())"); insert(s, org, "İstanbul", "IST"); insert(s, UUID.randomUUID(), "Iğdır", "IGD"); insert(s, UUID.randomUUID(), "Arşiv", "ARS"); s.execute("UPDATE organizations SET status='ARCHIVED' WHERE name='Arşiv'"); c.commit(); } }
    @Test void globalFiltersSearchSortAuditsAndUsesIamRuntimeToOrgRuntime() throws Exception { try(Connection c=dataSource.getConnection(); var rs=c.createStatement().executeQuery("SELECT session_user")){rs.next(); assertThat(rs.getString(1)).isEqualTo("iam_runtime");} mvc.perform(get("/api/v1/organizations").header("Authorization","Bearer global").param("search","istanbul").param("status","ACTIVE").header("X-Request-Id","list-42")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("İstanbul")); assertThat(count("audit_logs WHERE action_type='PLATFORM_ADMIN_ORG_ACCESS' AND request_id='list-42'")).isEqualTo(1); }
    @Test void organizationScopeReturnsOnlyOwnActiveAndRejectsParamsOrContext() throws Exception { mvc.perform(get("/api/v1/organizations").header("Authorization","Bearer organization")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page.hasNextPage").value(false)); mvc.perform(get("/api/v1/organizations").header("Authorization","Bearer organization").param("limit","1")).andExpect(status().isUnprocessableEntity()); mvc.perform(get("/api/v1/organizations").header("Authorization","Bearer context")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORGANIZATION_CONTEXT_REQUIRED")); }
    private static void insert(java.sql.Statement s, UUID id,String name,String shortName)throws Exception{s.execute("INSERT INTO organizations(id,name,short_name,status,default_timezone,created_at,updated_at,row_version,created_by_user_id,updated_by_user_id) VALUES ('"+id+"','"+name+"','"+shortName+"','ACTIVE','Europe/Istanbul',transaction_timestamp(),transaction_timestamp(),1,'"+ADMIN.get()+"','"+ADMIN.get()+"')");}
    static synchronized void start(){if(migrated)return;POSTGRES.start();Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).locations("classpath:db/migration").load().migrate();try(Connection c=owner()){c.createStatement().execute("ALTER ROLE iam_runtime PASSWORD 'list-runtime-password'");c.commit();}catch(Exception e){throw new IllegalStateException(e);}migrated=true;}
    static Connection owner()throws Exception{Connection c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());c.setAutoCommit(false);return c;}
    static long count(String from)throws Exception{try(Connection c=owner();var rs=c.createStatement().executeQuery("SELECT count(*) FROM "+from)){rs.next();return rs.getLong(1);}}
    @TestConfiguration static class Auth {@Bean @Primary ActiveSessionResolver resolver(){return t->switch(t){case "global"->CredentialResolution.platformAccess(ActiveSession.globalPlatformAdmin(ADMIN.get()));case "organization"->CredentialResolution.platformAccess(ActiveSession.organization(ORG.get(),ORG.get()));case "context"->CredentialResolution.contextSelection();default->throw new org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException("UNAUTHENTICATED");};}}
}
