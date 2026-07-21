package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OperationScope;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Applies {@code SET LOCAL app.*} RLS session variables to the connection that Spring's
 * transaction infrastructure actually binds to the current transaction (via
 * {@link DataSourceUtils#getConnection(DataSource)}), NOT a second unrelated connection pulled
 * straight from the pool. Pulling a fresh connection here would (a) never reach the connection
 * {@link org.springframework.jdbc.core.JdbcTemplate} uses for the repository calls inside the
 * same transaction, silently defeating RLS, and (b) leak a pooled connection per call.
 */
public class SpringIamTransactionExecutor implements IamTransactionExecutor {

    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final ThreadLocal<Boolean> mutationScopeActive = ThreadLocal.withInitial(() -> false);

    public SpringIamTransactionExecutor(PlatformTransactionManager transactionManager, DataSource dataSource) {
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
    }

    @Override
    public <T> T executeInAuthenticationScope(UUID actorUserId, String issuer, String subject,
                                               Supplier<T> action) {
        Map<String, String> vars = new HashMap<>();
        vars.put("app.iam_operation_scope", OperationScope.AUTHENTICATION.name());
        vars.put("app.iam_provider_issuer", issuer);
        vars.put("app.iam_provider_subject", subject);
        return executeReadOnlyWithVars(vars, action);
    }

    @Override
    public <T> T executeInIamAuthScope(OperationCode operationCode, IamAuthScopeContext context,
                                       Supplier<T> action) {
        Map<String, String> vars = new HashMap<>();
        vars.put("app.iam_operation_scope", OperationScope.IAM_AUTH.name());
        vars.put("app.iam_operation_code", operationCode.name());
        putContextVars(vars, context);
        return executeMutationWithVars(vars, action);
    }

    @Override
    public <T> T executeInGlobalScope(OperationCode operationCode, IamAuthScopeContext context,
                                      Supplier<T> action) {
        Map<String, String> vars = new HashMap<>();
        vars.put("app.iam_operation_scope", OperationScope.GLOBAL.name());
        vars.put("app.iam_operation_code", operationCode.name());
        putContextVars(vars, context);
        return executeMutationWithVars(vars, action);
    }

    @Override
    public <T> T executeInProvisioningScope(OperationCode operationCode, IamAuthScopeContext context,
                                             Supplier<T> action) {
        Map<String, String> vars = new HashMap<>();
        vars.put("app.iam_operation_scope", OperationScope.IAM_PROVISIONING.name());
        vars.put("app.iam_operation_code", operationCode.name());
        putContextVars(vars, context);
        return executeMutationWithVars(vars, action);
    }

    @Override
    public void refreshIamAuthScope(IamAuthScopeContext context) {
        if (!Boolean.TRUE.equals(mutationScopeActive.get()) || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "refreshIamAuthScope yalnız aktif bir executeInIamAuthScope/executeInGlobalScope transaction'ı içinde çağrılabilir.");
        }
        Map<String, String> vars = new HashMap<>();
        putContextVars(vars, context);
        if (vars.isEmpty()) {
            return;
        }
        applySessionVarsToBoundConnection(vars);
    }

    private void putContextVars(Map<String, String> vars, IamAuthScopeContext context) {
        if (context.actorUserId() != null) {
            vars.put("app.iam_actor_user_id", context.actorUserId().toString());
        }
        if (context.currentTrustedDeviceId() != null) {
            vars.put("app.iam_current_trusted_device_id", context.currentTrustedDeviceId().toString());
        }
        if (context.currentFamilyId() != null) {
            vars.put("app.iam_current_family_id", context.currentFamilyId().toString());
        }
        if (context.targetMembershipId() != null) {
            vars.put("app.iam_target_membership_id", context.targetMembershipId().toString());
        }
        if (context.targetOrganizationId() != null) {
            vars.put("app.iam_target_organization_id", context.targetOrganizationId().toString());
            // IAM_PROVISIONING/ORGANIZATION-scope RLS policies (V1) key off app.organization_id,
            // not app.iam_target_organization_id — both are set from the same value so provider-
            // command provisioning policies (iam_provider_commands_insert_provisioning, ipc_complete)
            // see what they actually check.
            vars.put("app.organization_id", context.targetOrganizationId().toString());
        }
        if (context.targetDeviceId() != null) {
            vars.put("app.iam_target_device_id", context.targetDeviceId().toString());
        }
        if (context.targetUserId() != null) {
            vars.put("app.iam_target_user_id", context.targetUserId().toString());
        }
        if (context.targetIdentityId() != null) {
            vars.put("app.iam_target_identity_id", context.targetIdentityId().toString());
        }
        if (context.providerDeviceIdentifier() != null) {
            vars.put("app.iam_provider_device_identifier", context.providerDeviceIdentifier().toString());
        }
        if (context.verifiedAuthTime() != null) {
            vars.put("app.iam_verified_auth_time", context.verifiedAuthTime().toString());
        }
        if (context.contextTokenHash() != null) {
            vars.put("app.iam_context_token_hash", context.contextTokenHash());
        }
        if (context.accessTokenHash() != null) {
            vars.put("app.iam_access_token_hash", context.accessTokenHash());
        }
    }

    private <T> T executeReadOnlyWithVars(Map<String, String> vars, Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(10);
        return executeWithVars(template, vars, action, false);
    }

    private <T> T executeMutationWithVars(Map<String, String> vars, Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(false);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(15);
        return executeWithVars(template, vars, action, true);
    }

    private <T> T executeWithVars(TransactionTemplate template, Map<String, String> vars, Supplier<T> action,
                                   boolean isMutationScope) {
        return template.execute(status -> {
            applySessionVarsToBoundConnection(vars);
            if (isMutationScope) {
                mutationScopeActive.set(true);
            }
            try {
                return action.get();
            } finally {
                if (isMutationScope) {
                    mutationScopeActive.remove();
                }
            }
        });
    }

    /**
     * Applies SET LOCAL statements to the connection Spring has already bound to the current
     * transaction on this thread. Uses {@link DataSourceUtils} so this resolves to the SAME
     * physical connection {@link org.springframework.jdbc.core.JdbcTemplate} will use for the
     * repository calls made inside the transactional callback — never a second connection.
     */
    private void applySessionVarsToBoundConnection(Map<String, String> vars) {
        if (vars.isEmpty()) {
            return;
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (var stmt = connection.createStatement()) {
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    String key = entry.getKey().replace("'", "''");
                    String value = entry.getValue().replace("'", "''");
                    stmt.execute("SET LOCAL " + key + " = '" + value + "'");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("RLS session değişkenleri ayarlanamadı", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
