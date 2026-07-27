package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.OrganizationListTransaction;
import org.mepcity.kursplatform.org.application.OrganizationPersistenceStateException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Establishes the only RLS context accepted by the ORG_LIST database policies. */
public final class SpringOrganizationListTransaction implements OrganizationListTransaction {
    private static final String ACTIVE_ADMIN =
            "SELECT user_id FROM platform_administrators WHERE user_id = ? AND revoked_at IS NULL";
    private final DataSource dataSource;
    private final TransactionTemplate transactions;

    public SpringOrganizationListTransaction(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(UUID actorUserId, Scope scope, UUID organizationId, Supplier<T> action) {
        return transactions.execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            establish(connection, actorUserId, scope, organizationId);
            if (scope == Scope.GLOBAL && !activeAdminExists(connection, actorUserId)) {
                throw new ForbiddenException();
            }
            return action.get();
        });
    }

    private static void establish(Connection connection, UUID actor, Scope scope, UUID organizationId) {
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCAL ROLE org_runtime");
            set(connection, "app.iam_operation_scope", scope.name());
            set(connection, "app.iam_actor_user_id", actor.toString());
            set(connection, "app.iam_operation_code", "ORG_LIST");
            if (scope == Scope.ORGANIZATION && organizationId != null) {
                set(connection, "app.organization_id", organizationId.toString());
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("ORG_LIST RLS bağlamı kurulamadı", exception);
        }
    }

    private static void set(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        }
    }

    private static boolean activeAdminExists(Connection connection, UUID actor) {
        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_ADMIN)) {
            statement.setObject(1, actor);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Platform yöneticisi doğrulanamadı", exception);
        }
    }
}
