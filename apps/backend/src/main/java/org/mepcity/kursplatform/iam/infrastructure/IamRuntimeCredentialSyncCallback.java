package org.mepcity.kursplatform.iam.infrastructure;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Keeps the iam_runtime login password in sync with the secret the application's runtime
 * datasource actually authenticates with (spring.datasource.password), without coupling that
 * secret to a versioned Flyway migration.
 *
 * <p>V1__iam_tables.sql creates iam_runtime with LOGIN but no password, and the migration-owner
 * connection (spring.flyway.*) is intentionally a different, more privileged role than the
 * application's runtime connection (spring.datasource.*, which now authenticates as iam_runtime
 * itself — see application.properties). Something has to bridge the two: this runs as a Flyway
 * callback, using Flyway's OWN connection (the migration owner, which has the CREATEROLE grant
 * needed for ALTER ROLE), immediately after every migrate. Being a callback rather than a
 * schema_history-tracked migration means secret rotation never produces a Flyway checksum
 * mismatch — re-running this with a new password is exactly the point, every deploy.
 */
@Component
public class IamRuntimeCredentialSyncCallback implements Callback {

    private final String runtimePassword;

    public IamRuntimeCredentialSyncCallback(@Value("${spring.datasource.password:}") String runtimePassword) {
        this.runtimePassword = runtimePassword;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        if (runtimePassword == null || runtimePassword.isBlank()) {
            // local-stub / test profiles run migration owner and runtime as the same
            // trusted-network credential and don't set spring.datasource.password separately.
            return;
        }
        String tag = "iam_rt_pwd_" + UUID.randomUUID().toString().replace("-", "");
        String sql = "ALTER ROLE iam_runtime WITH PASSWORD $" + tag + "$" + runtimePassword + "$" + tag + "$";
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("iam_runtime parolası eşitlenemedi", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "iamRuntimeCredentialSync";
    }
}
